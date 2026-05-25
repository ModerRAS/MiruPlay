package com.miruplay.tv.sync

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.repository.BangumiEpisodeMetadata
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.scraper.EpisodeMetadata
import com.miruplay.tv.scraper.MetadataScraper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiMetadataRefreshCoreTest {
    @Test
    fun `refresh searches candidates and caches merged Bangumi metadata`() = runBlocking {
        val metadata = FakeMetadataRepository()
        val scraper = FakeMetadataScraper(
            directResults = listOf(result(animeId = "direct", confidence = 0.2f)),
            aliasResult = result(animeId = "431767", confidence = 0.95f),
            details = Anime(id = "431767", title = "葬送のフリーレン", titleCn = "葬送的芙莉莲", episodeCount = 28, bangumiId = 431767),
            episodes = listOf(
                episodeMetadata(episodeNumber = 1, title = "旅立ち", bangumiEpisodeId = 1001, durationMs = 1_500_000L),
            ),
        )
        val core = BangumiMetadataRefreshCore(metadata, scraper)

        val refreshed = core.refresh(
            cacheAnimeId = "local-frieren",
            query = "local-frieren",
            candidates = listOf("葬送的芙莉莲"),
            localEpisodes = listOf(episode(id = "ep1", animeId = "local-frieren", episodeNumber = 1, title = "旧标题")),
        )

        assertTrue(refreshed is Result.Success)
        val data = (refreshed as Result.Success).data
        assertEquals("431767", data.match.animeId)
        assertEquals(listOf("local-frieren"), scraper.searchQueries)
        assertEquals(listOf(listOf("葬送的芙莉莲")), scraper.aliasCandidates)
        assertEquals(431767, metadata.anime?.bangumiId)
        assertEquals("local-frieren", metadata.anime?.id)
        assertEquals("旅立ち", metadata.episodes.single().title)
        assertEquals(1001, metadata.episodes.single().bangumiEpisodeId)
        assertEquals(1_500_000L, metadata.episodes.single().duration)
    }

    @Test
    fun `cacheResolvedMetadata preserves local episode when remote episode is absent`() = runBlocking {
        val metadata = FakeMetadataRepository()
        val core = BangumiMetadataRefreshCore(metadata, FakeMetadataScraper())

        val cached = core.cacheResolvedMetadata(
            cacheAnimeId = "local",
            details = Anime(id = "remote", title = "Remote", episodeCount = 0),
            localEpisodes = listOf(episode(id = "ep2", animeId = "old", episodeNumber = 2, title = "Local")),
            remoteEpisodes = emptyList(),
        )

        assertTrue(cached is Result.Success)
        assertEquals("local", metadata.anime?.id)
        assertEquals(1, metadata.anime?.episodeCount)
        assertEquals("local", metadata.episodes.single().animeId)
        assertEquals("Local", metadata.episodes.single().title)
    }

    @Test
    fun `media index entries map to Bangumi cache ids and local episodes`() {
        val metadataEntry = MediaIndexEntry(
            sourceId = 1L,
            path = "D:/Anime/Frieren/Frieren - S01E02.mkv",
            animeName = "Frieren",
            metadataId = "431767",
            metadataTitle = "葬送的芙莉莲",
            seasonNumber = 1,
            episodeNumber = 2,
            episodeTitle = "旅途",
        )
        val plainEntry = MediaIndexEntry(
            sourceId = 1L,
            path = "D:/Anime/Frieren/Frieren - S01E03.mkv",
        )

        val episode = metadataEntry.toBangumiLocalEpisode(metadataEntry.bangumiMetadataCacheId())

        assertEquals("431767", metadataEntry.bangumiMetadataCacheId())
        assertEquals("Frieren - S01E03", plainEntry.bangumiMetadataCacheId())
        assertEquals("431767", episode.animeId)
        assertEquals(1, episode.seasonNumber)
        assertEquals(2, episode.episodeNumber)
        assertEquals("旅途", episode.title)
        assertEquals("Frieren - S01E02.mkv", episode.fileName)
    }

    @Test
    fun `cacheMatchedIndexMetadata maps related index entries into cached local episodes`() = runBlocking {
        val metadata = FakeMetadataRepository()
        val core = BangumiMetadataRefreshCore(
            metadata,
            FakeMetadataScraper(
                details = Anime(id = "431767", title = "Frieren", episodeCount = 28, bangumiId = 431767),
                episodes = listOf(
                    episodeMetadata(episodeNumber = 1, title = "Start", bangumiEpisodeId = 1001, durationMs = 1_500_000L),
                    episodeMetadata(episodeNumber = 2, title = "Magic", bangumiEpisodeId = 1002, durationMs = 1_400_000L),
                ),
            ),
        )
        val entry = mediaEntry(path = "D:/Anime/Frieren/01.mkv", metadataId = "431767", episodeNumber = 1)
        val related = listOf(entry, mediaEntry(path = "D:/Anime/Frieren/02.mkv", metadataId = "431767", episodeNumber = 2))

        val cached = core.cacheMatchedIndexMetadata(
            entry = entry,
            relatedEntries = related,
            match = result(animeId = "431767", confidence = 0.98f),
        )

        assertTrue(cached is Result.Success)
        assertEquals("431767", metadata.anime?.id)
        assertEquals(listOf("Start", "Magic"), metadata.episodes.map { it.title })
        assertEquals(listOf("01.mkv", "02.mkv"), metadata.episodes.map { it.fileName })
        assertEquals(listOf(1001, 1002), metadata.episodes.map { it.bangumiEpisodeId })
    }

    @Test
    fun `ensureCachedIndexMetadata returns cached anime id when episode metadata is already present`() = runBlocking {
        val metadata = FakeMetadataRepository().apply {
            anime = Anime(id = "431767", title = "Frieren", bangumiId = 431767)
            episodes = listOf(episode(id = "ep1", animeId = "431767", episodeNumber = 1, title = "Cached", bangumiEpisodeId = 1001))
        }
        val scraper = FakeMetadataScraper()
        val core = BangumiMetadataRefreshCore(metadata, scraper)

        val animeId = core.ensureCachedIndexMetadata(
            entry = mediaEntry(path = "D:/Anime/Frieren/01.mkv", metadataId = "431767"),
            relatedEntries = emptyList(),
        )

        assertEquals(Result.success("431767"), animeId)
        assertTrue(scraper.detailRequests.isEmpty())
    }

    @Test
    fun `ensureCachedIndexMetadata requires a Bangumi metadata id`() = runBlocking {
        val core = BangumiMetadataRefreshCore(FakeMetadataRepository(), FakeMetadataScraper())

        val animeId = core.ensureCachedIndexMetadata(
            entry = mediaEntry(path = "D:/Anime/Frieren/01.mkv"),
            relatedEntries = emptyList(),
        )

        assertTrue(animeId is Result.Error)
        assertTrue((animeId as Result.Error).error.toUserMessage().contains("请先应用 Bangumi 匹配"))
    }

    @Test
    fun `cacheMatchedMetadata fetches selected Bangumi result and caches merged metadata`() = runBlocking {
        val metadata = FakeMetadataRepository()
        val core = BangumiMetadataRefreshCore(
            metadata,
            FakeMetadataScraper(
                details = Anime(id = "431767", title = "Frieren", episodeCount = 28, bangumiId = 431767),
                episodes = listOf(episodeMetadata(episodeNumber = 2, title = "Magic", bangumiEpisodeId = 1002, durationMs = 1_400_000L)),
            ),
        )

        val cached = core.cacheMatchedMetadata(
            cacheAnimeId = "local-frieren",
            match = result(animeId = "431767", confidence = 0.98f),
            localEpisodes = listOf(episode(id = "ep2", animeId = "old", episodeNumber = 2, title = "Local 2")),
        )

        assertTrue(cached is Result.Success)
        val data = (cached as Result.Success).data
        assertEquals("431767", data.match.animeId)
        assertEquals("local-frieren", data.details.id)
        assertEquals("local-frieren", metadata.anime?.id)
        assertEquals(431767, metadata.anime?.bangumiId)
        assertEquals("Magic", metadata.episodes.single().title)
        assertEquals(1002, metadata.episodes.single().bangumiEpisodeId)
    }

    @Test
    fun `cacheBangumiMetadata returns scraper detail error for existing metadata id`() = runBlocking {
        val scraperError = AppError.ScrapingError.ApiError("Bangumi", "detail unavailable")
        val core = BangumiMetadataRefreshCore(
            FakeMetadataRepository(),
            FakeMetadataScraper(detailsResult = Result.failure(scraperError)),
        )

        val cached = core.cacheBangumiMetadata(
            cacheAnimeId = "local",
            bangumiAnimeId = "431767",
            localEpisodes = emptyList(),
        )

        assertTrue(cached is Result.Error)
        assertEquals(scraperError, (cached as Result.Error).error)
    }

    @Test
    fun `refresh reports no reliable match when preferred search is weak`() = runBlocking {
        val core = BangumiMetadataRefreshCore(
            FakeMetadataRepository(),
            FakeMetadataScraper(directResults = listOf(result(animeId = "weak", confidence = 0.2f))),
        )

        val refreshed = core.refresh(
            cacheAnimeId = "local",
            query = "local",
            candidates = emptyList(),
            localEpisodes = emptyList(),
        )

        assertTrue(refreshed is Result.Error)
        assertTrue((refreshed as Result.Error).error.toUserMessage().contains("没有找到可靠的 Bangumi 匹配"))
    }

    private class FakeMetadataScraper(
        private val directResults: List<ScraperResult> = emptyList(),
        private val aliasResult: ScraperResult? = null,
        private val details: Anime = Anime(id = "remote", title = "Remote", bangumiId = 1),
        private val detailsResult: Result<Anime> = Result.success(details),
        private val episodes: List<EpisodeMetadata> = emptyList(),
    ) : MetadataScraper {
        val searchQueries = mutableListOf<String>()
        val aliasCandidates = mutableListOf<List<String>>()
        val detailRequests = mutableListOf<String>()

        override val sourceName: String = "Bangumi"

        override suspend fun searchAnime(query: String): Result<List<ScraperResult>> {
            searchQueries += query
            return Result.success(directResults)
        }

        override suspend fun getAnimeDetails(animeId: String): Result<Anime> {
            detailRequests += animeId
            return detailsResult
        }

        override suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>> =
            Result.success(episodes)

        override suspend fun searchByAlias(
            normalizedName: String,
            candidates: List<String>,
        ): Result<ScraperResult?> {
            aliasCandidates += candidates
            return Result.success(aliasResult)
        }
    }

    private class FakeMetadataRepository : MetadataRepository {
        var anime: Anime? = null
        var episodes: List<Episode> = emptyList()

        override suspend fun cacheMetadata(anime: Anime): Result<Unit> {
            this.anime = anime
            return Result.success(Unit)
        }

        override suspend fun getCachedMetadata(animeId: String): Result<Anime?> =
            Result.success(anime)

        override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> =
            Result.success(anime?.takeIf { animeIds.contains(it.id) }?.let(::listOf).orEmpty())

        override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> =
            Result.success(episodes.firstOrNull { it.id == episodeId })

        override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> =
            Result.success(episodes)

        override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> {
            this.episodes = episodes
            return Result.success(Unit)
        }

        override suspend fun invalidateCache(animeId: String): Result<Unit> {
            anime = null
            episodes = emptyList()
            return Result.success(Unit)
        }
    }

    private fun result(
        animeId: String,
        confidence: Float,
    ): ScraperResult =
        ScraperResult(
            animeId = animeId,
            title = "葬送のフリーレン",
            titleCn = "葬送的芙莉莲",
            matchedTitle = "葬送的芙莉莲",
            confidence = confidence,
            source = ScraperSource.BANGUMI,
        )

    private fun episode(
        id: String,
        animeId: String,
        episodeNumber: Int,
        title: String,
        bangumiEpisodeId: Int? = null,
    ): Episode =
        Episode(
            id = id,
            animeId = animeId,
            seasonNumber = 1,
            episodeNumber = episodeNumber,
            title = title,
            filePath = "D:/Anime/$id.mkv",
            fileName = "$id.mkv",
            bangumiEpisodeId = bangumiEpisodeId,
        )

    private fun mediaEntry(
        path: String,
        metadataId: String? = null,
        episodeNumber: Int? = 1,
    ): MediaIndexEntry =
        MediaIndexEntry(
            sourceId = 1L,
            path = path,
            animeName = "Frieren",
            metadataId = metadataId,
            metadataTitle = metadataId?.let { "葬送的芙莉莲" },
            seasonNumber = 1,
            episodeNumber = episodeNumber,
        )

    private fun episodeMetadata(
        episodeNumber: Int,
        title: String,
        bangumiEpisodeId: Int,
        durationMs: Long,
    ): BangumiEpisodeMetadata =
        BangumiEpisodeMetadata(
            episodeNumber = episodeNumber,
            title = title,
            bangumiEpisodeId = bangumiEpisodeId,
            durationMs = durationMs,
        )
}
