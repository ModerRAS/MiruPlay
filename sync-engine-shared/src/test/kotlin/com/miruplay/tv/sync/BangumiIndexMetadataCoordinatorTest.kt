package com.miruplay.tv.sync

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MetadataBatchMatch
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.scraper.EpisodeMetadata
import com.miruplay.tv.scraper.MetadataScraper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiIndexMetadataCoordinatorTest {
    @Test
    fun `previewBatch searches source entries and returns shared status`() = runBlocking {
        val index = FakeMediaIndexRepository(
            entries = mutableListOf(
                mediaEntry(path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren"),
                mediaEntry(path = "D:/Anime/Frieren/02.mkv", animeName = "Frieren"),
            )
        )
        val scraper = FakeMetadataScraper(
            searchResults = mapOf("Frieren" to listOf(result(confidence = 0.95f))),
        )
        val coordinator = coordinator(index = index, scraper = scraper)
        val startedStatuses = mutableListOf<String>()

        val preview = coordinator.previewBatch(sourceId = 1L) { startedStatuses += it } as Result.Success

        assertEquals(listOf("正在用 Bangumi 搜索 1 个索引标题..."), startedStatuses)
        assertEquals(1, preview.data.matches.size)
        assertEquals(2, preview.data.plan?.readyUpdates?.size)
        assertEquals("2 个可应用，0 个需复核，0 个冲突", preview.data.status)
        assertEquals(listOf("Frieren"), scraper.searchQueries)
    }

    @Test
    fun `applyBatch writes ready updates and reports missing preview`() = runBlocking {
        val original = mediaEntry(path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren")
        val index = FakeMediaIndexRepository(entries = mutableListOf(original))
        val coordinator = coordinator(index = index)

        val missingPreview = coordinator.applyBatch(sourceId = 1L, matches = emptyList()) as Result.Success
        val applied = coordinator.applyBatch(
            sourceId = 1L,
            matches = listOf(MetadataBatchMatch(query = "Frieren", result = result(confidence = 0.95f))),
        ) as Result.Success

        assertEquals("请先运行批量预览；当前没有可直接应用的高置信匹配。", missingPreview.data.status)
        assertEquals("已将 Bangumi 批量元数据应用到 1 个索引条目，跳过 0 个冲突。", applied.data.status)
        assertEquals("431767", index.entries.single().metadataId)
        assertEquals(listOf(original.copy(sourceId = 1L)), applied.data.write.rollbackEntries)
        assertEquals(applied.data.write.rollbackEntries, index.lastUndo)
    }

    @Test
    fun `undoBatch restores rollback entries and reports empty undo`() = runBlocking {
        val original = mediaEntry(path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren")
        val updated = original.copy(metadataId = "431767", metadataTitle = "葬送的芙莉莲")
        val index = FakeMediaIndexRepository(entries = mutableListOf(updated))
        val coordinator = coordinator(index = index)

        val restored = coordinator.undoBatch(sourceId = 1L, rollbackEntries = listOf(original)) as Result.Success
        val empty = coordinator.undoBatch(sourceId = 1L, rollbackEntries = emptyList()) as Result.Success

        assertEquals("已从上一次 Bangumi 批量更改中恢复 1 个索引条目。", restored.data.status)
        assertEquals(original.copy(sourceId = 1L), index.entries.single())
        assertEquals("没有可撤销的 Bangumi 批量更改。", empty.data.status)
    }

    @Test
    fun `selectBatchCandidate replans and acceptBatchReview applies reviewed match`() = runBlocking {
        val entry = mediaEntry(path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren")
        val index = FakeMediaIndexRepository(entries = mutableListOf(entry))
        val coordinator = coordinator(index = index)
        val lowConfidence = result(confidence = 0.7f)
        val highConfidence = result(confidence = 1f)
        val match = MetadataBatchMatch(
            query = "Frieren",
            result = lowConfidence,
            candidates = listOf(lowConfidence, highConfidence),
        )

        val selection = coordinator.selectBatchCandidate(
            sourceId = 1L,
            matches = listOf(match),
            match = match,
            candidate = highConfidence,
        ) as Result.Success
        val accepted = coordinator.acceptBatchReview(
            sourceId = 1L,
            match = match,
        ) as Result.Success

        assertEquals(highConfidence, selection.data.selectedResult)
        assertEquals(1, selection.data.plan?.readyUpdates?.size)
        assertEquals("已选择批量候选：Frieren -> 葬送的芙莉莲", selection.data.status)
        assertEquals("已接受复核的 Bangumi 匹配，更新 1 个索引条目。", accepted.data.status)
        assertEquals("431767", index.entries.single().metadataId)
    }

    @Test
    fun `search uses selected entry candidates and reports blank query`() = runBlocking {
        val scraper = FakeMetadataScraper(searchResults = mapOf("Frieren" to listOf(result(confidence = 0.95f))))
        val coordinator = coordinator(scraper = scraper)
        val selected = mediaEntry(
            path = "D:/Anime/Frieren/01.mkv",
            animeName = "Frieren",
            metadataTitle = "葬送的芙莉莲",
            metadataId = "431767",
        )
        val startedStatuses = mutableListOf<String>()

        val blank = coordinator.search(query = "", selectedEntry = null) as Result.Success
        val search = coordinator.search(query = "", selectedEntry = selected) { startedStatuses += it } as Result.Success

        assertEquals("请输入 Bangumi 搜索词，或先选择索引视频。", blank.data.status)
        assertEquals("Frieren", search.data.query)
        assertEquals(result(confidence = 0.95f), search.data.selectedResult)
        assertEquals(listOf("正在搜索 Bangumi：\"Frieren\"..."), startedStatuses)
        assertEquals(listOf("Frieren"), scraper.searchQueries)
    }

    @Test
    fun `applyEntryMetadata updates index and caches Bangumi details`() = runBlocking {
        val entry = mediaEntry(path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren", episodeNumber = 1)
        val related = listOf(
            entry,
            mediaEntry(path = "D:/Anime/Frieren/02.mkv", animeName = "Frieren", episodeNumber = 2),
        )
        val index = FakeMediaIndexRepository(entries = mutableListOf(entry))
        val metadata = FakeMetadataRepository()
        val scraper = FakeMetadataScraper(
            details = Anime(id = "431767", title = "Frieren", bangumiId = 431767, episodeCount = 28),
            episodes = listOf(
                episodeMetadata(episodeNumber = 1, title = "Start", bangumiEpisodeId = 1001),
                episodeMetadata(episodeNumber = 2, title = "Magic", bangumiEpisodeId = 1002),
            ),
        )
        val coordinator = coordinator(index = index, metadata = metadata, scraper = scraper)

        val applied = coordinator.applyEntryMetadata(
            sourceId = 1L,
            entry = entry,
            match = result(confidence = 0.98f),
            relatedEntries = related,
        ) as Result.Success

        assertNotNull(applied.data.updatedEntry)
        assertEquals("已将 Bangumi 元数据应用到 D:/Anime/Frieren/01.mkv。", applied.data.status)
        assertEquals("431767", index.entries.single().metadataId)
        assertEquals("431767", metadata.anime?.id)
        assertEquals(listOf("Start", "Magic"), metadata.episodes.map { it.title })
    }

    @Test
    fun `clearEntryMetadata updates index and invalidates old cache`() = runBlocking {
        val entry = mediaEntry(
            path = "D:/Anime/Frieren/01.mkv",
            animeName = "Frieren",
            metadataId = "431767",
            metadataTitle = "葬送的芙莉莲",
        )
        val index = FakeMediaIndexRepository(entries = mutableListOf(entry))
        val metadata = FakeMetadataRepository()
        val coordinator = coordinator(index = index, metadata = metadata)

        val cleared = coordinator.clearEntryMetadata(sourceId = 1L, entry = entry) as Result.Success

        assertEquals("已清除 D:/Anime/Frieren/01.mkv 的外部元数据。", cleared.data.status)
        assertEquals(null, index.entries.single().metadataId)
        assertEquals(listOf("431767"), metadata.invalidatedAnimeIds)
    }

    @Test
    fun `actions return shared required-state messages`() = runBlocking {
        val coordinator = coordinator()

        val preview = coordinator.previewBatch(sourceId = null) as Result.Success
        val apply = coordinator.applyEntryMetadata(
            sourceId = 1L,
            entry = null,
            match = result(confidence = 0.95f),
            relatedEntries = emptyList(),
        ) as Result.Success
        val clear = coordinator.clearEntryMetadata(sourceId = 1L, entry = null) as Result.Success
        val accept = coordinator.acceptBatchReview(sourceId = 1L, match = null) as Result.Success

        assertEquals("请先打开或扫描媒体源。", preview.data.status)
        assertEquals("请先选择索引视频，再应用 Bangumi 元数据。", apply.data.status)
        assertEquals("请先选择索引视频，再清除元数据。", clear.data.status)
        assertEquals("请先选择带 Bangumi 结果的批量匹配。", accept.data.status)
    }

    private fun coordinator(
        index: FakeMediaIndexRepository = FakeMediaIndexRepository(),
        metadata: FakeMetadataRepository = FakeMetadataRepository(),
        scraper: FakeMetadataScraper = FakeMetadataScraper(),
    ): BangumiIndexMetadataCoordinator =
        BangumiIndexMetadataCoordinator(
            indexRepository = index,
            metadataRepository = metadata,
            bangumiScraper = scraper,
        )

    private class FakeMediaIndexRepository(
        val entries: MutableList<MediaIndexEntry> = mutableListOf(),
    ) : MediaIndexRepository {
        var lastUndo = emptyList<MediaIndexEntry>()

        override suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> {
            this.entries.removeAll { it.sourceId == sourceId }
            this.entries += entries.map { it.copy(sourceId = sourceId) }
            return Result.success(Unit)
        }

        override suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit> {
            val normalized = entry.copy(sourceId = sourceId)
            entries.removeAll { it.sourceId == sourceId && it.path == normalized.path }
            entries += normalized
            return Result.success(Unit)
        }

        override suspend fun queryIndex(sourceId: Long, query: String): Result<List<MediaIndexEntry>> =
            Result.success(entries.filter { it.sourceId == sourceId })

        override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> =
            Result.success(emptyList())

        override suspend fun clearIndex(sourceId: Long): Result<Unit> {
            entries.removeAll { it.sourceId == sourceId }
            return Result.success(Unit)
        }

        override suspend fun saveLastBatchUndo(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> {
            lastUndo = entries.map { it.copy(sourceId = sourceId) }
            return Result.success(Unit)
        }

        override suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>> =
            Result.success(lastUndo)

        override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> {
            lastUndo = emptyList()
            return Result.success(Unit)
        }
    }

    private class FakeMetadataRepository : MetadataRepository {
        var anime: Anime? = null
        var episodes: List<Episode> = emptyList()
        val invalidatedAnimeIds = mutableListOf<String>()

        override suspend fun cacheMetadata(anime: Anime): Result<Unit> {
            this.anime = anime
            return Result.success(Unit)
        }

        override suspend fun getCachedMetadata(animeId: String): Result<Anime?> =
            Result.success(anime)

        override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> =
            Result.success(episodes.firstOrNull { it.id == episodeId })

        override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> =
            Result.success(episodes)

        override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> {
            this.episodes = episodes
            return Result.success(Unit)
        }

        override suspend fun invalidateCache(animeId: String): Result<Unit> {
            invalidatedAnimeIds += animeId
            anime = null
            episodes = emptyList()
            return Result.success(Unit)
        }
    }

    private class FakeMetadataScraper(
        private val searchResults: Map<String, List<ScraperResult>> = emptyMap(),
        private val details: Anime = Anime(id = "431767", title = "Frieren", bangumiId = 431767),
        private val episodes: List<EpisodeMetadata> = emptyList(),
    ) : MetadataScraper {
        val searchQueries = mutableListOf<String>()
        val aliasCandidates = mutableListOf<List<String>>()

        override val sourceName: String = "Bangumi"

        override suspend fun searchAnime(query: String): Result<List<ScraperResult>> {
            searchQueries += query
            return Result.success(searchResults[query].orEmpty())
        }

        override suspend fun getAnimeDetails(animeId: String): Result<Anime> =
            Result.success(details)

        override suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>> =
            Result.success(episodes)

        override suspend fun searchByAlias(
            normalizedName: String,
            candidates: List<String>,
        ): Result<ScraperResult?> {
            aliasCandidates += candidates
            return Result.success(null)
        }
    }

    private fun mediaEntry(
        path: String,
        animeName: String? = null,
        metadataTitle: String? = null,
        metadataId: String? = null,
        episodeNumber: Int? = null,
    ): MediaIndexEntry =
        MediaIndexEntry(
            sourceId = 1L,
            path = path,
            animeName = animeName,
            metadataTitle = metadataTitle,
            metadataId = metadataId,
            seasonNumber = 1,
            episodeNumber = episodeNumber,
        )

    private fun result(
        animeId: String = "431767",
        confidence: Float,
    ): ScraperResult =
        ScraperResult(
            animeId = animeId,
            title = "Frieren",
            titleCn = "葬送的芙莉莲",
            matchedTitle = "葬送的芙莉莲",
            confidence = confidence,
            source = ScraperSource.BANGUMI,
        )

    private fun episodeMetadata(
        episodeNumber: Int,
        title: String,
        bangumiEpisodeId: Int,
    ): EpisodeMetadata =
        EpisodeMetadata(
            episodeNumber = episodeNumber,
            title = title,
            bangumiEpisodeId = bangumiEpisodeId,
            durationMs = 1_500_000L,
        )
}
