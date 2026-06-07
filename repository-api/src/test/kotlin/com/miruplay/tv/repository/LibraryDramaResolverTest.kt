package com.miruplay.tv.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.DramaEpisodeMetadata
import com.miruplay.tv.model.DramaSeasonMetadata
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDramaResolverTest {
    @Test
    fun `loadSeries only returns drama sources`() = runBlocking {
        val dramaSource = MediaSourceInfoConventions.local(
            rootPath = "D:/Drama",
            name = "Drama",
        ).copy(id = 1L, contentMode = MediaContentMode.DRAMA)
        val animeSource = MediaSourceInfoConventions.local(
            rootPath = "D:/Anime",
            name = "Anime",
        ).copy(id = 2L, contentMode = MediaContentMode.ANIME)
        val resolver = resolver(
            sources = listOf(dramaSource, animeSource),
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "D:/Drama/Show/S01E01.mkv",
                    animeName = "Show",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    plot = "Drama plot",
                ),
                MediaIndexEntry(
                    sourceId = 2L,
                    path = "D:/Anime/Anime/01.mkv",
                    animeName = "Anime",
                    episodeNumber = 1,
                    plot = "Anime plot",
                ),
            ),
        )

        val series = resolver.loadSeries()

        assertEquals(listOf("Show"), series.map { it.id })
        assertEquals("Drama plot", series.single().summary)
        assertEquals(1, series.single().seasonCount)
        assertEquals(1, series.single().episodeCount)
    }

    @Test
    fun `loadSeriesDetail groups episodes by season and keeps order`() = runBlocking {
        val dramaSource = MediaSourceInfoConventions.webDav(
            url = "https://dav.example/drama",
            name = "Drama DAV",
        ).copy(id = 1L, contentMode = MediaContentMode.DRAMA)
        val resolver = resolver(
            sources = listOf(dramaSource),
            entries = listOf(
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "/Show/S02E01.mkv",
                    animeName = "Show",
                    seasonNumber = 2,
                    episodeNumber = 1,
                ),
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "/Show/S01E02.mkv",
                    animeName = "Show",
                    seasonNumber = 1,
                    episodeNumber = 2,
                ),
                MediaIndexEntry(
                    sourceId = 1L,
                    path = "/Show/S01E01.mkv",
                    animeName = "Show",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    plot = "Pilot",
                ),
            ),
        )

        val detail = resolver.loadSeriesDetail("Show")

        assertEquals("Show", detail?.series?.id)
        assertEquals(2, detail?.series?.seasonCount)
        assertEquals(3, detail?.series?.episodeCount)
        assertEquals(
            listOf(
                "1:/Show/S01E01.mkv",
                "1:/Show/S01E02.mkv",
                "1:/Show/S02E01.mkv",
            ),
            detail?.episodes?.map { it.id },
        )
        assertEquals(
            listOf(
                "https://dav.example/drama/Show/S01E01.mkv",
                "https://dav.example/drama/Show/S01E02.mkv",
                "https://dav.example/drama/Show/S02E01.mkv",
            ),
            detail?.episodes?.map { it.filePath },
        )
        assertEquals(
            listOf(1, 1, 2),
            detail?.episodes?.map { it.seasonNumber },
        )
        assertEquals(
            listOf(1, 2, 1),
            detail?.episodes?.map { it.episodeNumber },
        )
    }

    @Test
    fun `loadSeriesDetail returns null when series is missing`() = runBlocking {
        val resolver = resolver()

        assertNull(resolver.loadSeriesDetail("missing"))
    }

    @Test
    fun `toDramaSeasons counts episodes per season`() {
        val seasons = listOf(
            com.miruplay.tv.model.DramaEpisode(
                id = "a",
                seriesId = "show",
                seasonNumber = 2,
                episodeNumber = 1,
                filePath = "/show/s02e01.mkv",
                fileName = "s02e01.mkv",
            ),
            com.miruplay.tv.model.DramaEpisode(
                id = "b",
                seriesId = "show",
                seasonNumber = 1,
                episodeNumber = 2,
                filePath = "/show/s01e02.mkv",
                fileName = "s01e02.mkv",
            ),
            com.miruplay.tv.model.DramaEpisode(
                id = "c",
                seriesId = "show",
                seasonNumber = 1,
                episodeNumber = 1,
                filePath = "/show/s01e01.mkv",
                fileName = "s01e01.mkv",
            ),
        ).toDramaSeasons()

        assertEquals(listOf(1, 2), seasons.map { it.seasonNumber })
        assertEquals(listOf(2, 1), seasons.map { it.episodeCount })
    }

    @Test
    fun `loadSeriesDetail merges online drama metadata when available`() = runBlocking {
        val dramaSource = MediaSourceInfoConventions.local(
            rootPath = "D:/Drama",
            name = "Drama",
        ).copy(id = 1L, contentMode = MediaContentMode.DRAMA)
        val resolver = LibraryDramaResolver(
            mediaSources = FakeMediaSourceRepository(listOf(dramaSource)),
            index = FakeMediaIndexRepository(
                listOf(
                    MediaIndexEntry(
                        sourceId = 1L,
                        path = "D:/Drama/Show/S01E01.mkv",
                        animeName = "Show",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        plot = "Local plot",
                    ),
                ),
            ),
            metadata = object : DramaMetadataRepository {
                override suspend fun fetchSeriesMetadata(
                    title: String,
                    seasonHint: Int?,
                    seasonNumbers: List<Int>,
                ) =
                    Result.success(
                        DramaSeriesMetadata(
                            series = DramaSeries(
                                id = "tmdb:42",
                                title = "Show CN",
                                originalTitle = "Show Original",
                                summary = "Online plot",
                                posterUrl = "poster",
                                fanartUrl = "fanart",
                                firstAirDate = "2024-01-01",
                                tmdbId = 42,
                            ),
                            seasons = listOf(
                                DramaSeasonMetadata(
                                    seasonNumber = 1,
                                    episodes = listOf(
                                        DramaEpisodeMetadata(
                                            seasonNumber = 1,
                                            episodeNumber = 1,
                                            title = "Pilot Online",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    )
            },
        )

        val detail = resolver.loadSeriesDetail("Show")

        assertEquals("Show CN", detail?.series?.title)
        assertEquals("Show Original", detail?.series?.originalTitle)
        assertEquals("Online plot", detail?.series?.summary)
        assertEquals("poster", detail?.series?.posterUrl)
        assertEquals("fanart", detail?.series?.fanartUrl)
        assertEquals("2024-01-01", detail?.series?.firstAirDate)
        assertEquals(42, detail?.series?.tmdbId)
        assertEquals("Pilot Online", detail?.episodes?.single()?.title)
    }

    @Test
    fun `loadSeriesDetail prefers stored tmdb metadata id over title lookup`() = runBlocking {
        val dramaSource = MediaSourceInfoConventions.local(
            rootPath = "D:/Drama",
            name = "Drama",
        ).copy(id = 1L, contentMode = MediaContentMode.DRAMA)
        val metadataRepository = TrackingDramaMetadataRepository(
            seriesTitle = "庆余年 第一季",
            seriesOriginalTitle = "庆余年",
        )
        val resolver = LibraryDramaResolver(
            mediaSources = FakeMediaSourceRepository(listOf(dramaSource)),
            index = FakeMediaIndexRepository(
                listOf(
                    MediaIndexEntry(
                        sourceId = 1L,
                        path = "D:/Drama/庆余年/S01E01.mkv",
                        animeName = "庆余年",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        metadataSource = "TMDB",
                        metadataId = "321",
                        metadataTitle = "庆余年 第一季",
                    ),
                ),
            ),
            metadata = metadataRepository,
        )

        val detail = resolver.loadSeriesDetail("庆余年")

        assertEquals(listOf(321), metadataRepository.requestedIds)
        assertTrue(metadataRepository.requestedTitles.isEmpty())
        assertEquals("庆余年 第一季", detail?.series?.title)
        assertEquals(321, detail?.series?.tmdbId)
    }

    @Test
    fun `loadLocalSeriesDetails merges cached drama series metadata`() = runBlocking {
        val dramaSource = MediaSourceInfoConventions.local(
            rootPath = "D:/Drama",
            name = "Drama",
        ).copy(id = 1L, contentMode = MediaContentMode.DRAMA)
        val resolver = LibraryDramaResolver(
            mediaSources = FakeMediaSourceRepository(listOf(dramaSource)),
            index = FakeMediaIndexRepository(
                listOf(
                    MediaIndexEntry(
                        sourceId = 1L,
                        path = "D:/Drama/庆余年/S01E01.mkv",
                        animeName = "庆余年",
                        seasonNumber = 1,
                        episodeNumber = 1,
                    ),
                ),
            ),
            metadataCache = FakeMetadataRepository(
                metadataById = mapOf(
                    dramaSeriesCacheKey("庆余年") to Anime(
                        id = dramaSeriesCacheKey("庆余年"),
                        title = "庆余年 第一季",
                        titleCn = "Joy of Life",
                        summary = "缓存简介",
                        posterUrl = "poster-url",
                        fanartUrl = "fanart-url",
                        airDate = "2024-01-01",
                        tmdbId = 88,
                    ),
                ),
            ),
        )

        val detail = resolver.loadLocalSeriesDetails().single()

        assertEquals("庆余年 第一季", detail.series.title)
        assertEquals("Joy of Life", detail.series.originalTitle)
        assertEquals("缓存简介", detail.series.summary)
        assertEquals("poster-url", detail.series.posterUrl)
        assertEquals("fanart-url", detail.series.fanartUrl)
        assertEquals("2024-01-01", detail.series.firstAirDate)
        assertEquals(88, detail.series.tmdbId)
    }

    @Test
    fun `loadSeriesDetail ignores unreasonable stored tmdb binding and falls back to local title lookup`() = runBlocking {
        val dramaSource = MediaSourceInfoConventions.local(
            rootPath = "D:/Drama",
            name = "Drama",
        ).copy(id = 1L, contentMode = MediaContentMode.DRAMA)
        val metadataRepository = TrackingDramaMetadataRepository()
        val resolver = LibraryDramaResolver(
            mediaSources = FakeMediaSourceRepository(listOf(dramaSource)),
            index = FakeMediaIndexRepository(
                listOf(
                    MediaIndexEntry(
                        sourceId = 1L,
                        path = "D:/Drama/金庸武侠世界/S01E01.mkv",
                        animeName = "金庸武侠世界",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        metadataSource = "TMDB",
                        metadataId = "176599",
                        metadataTitle = "WWW.迷糊餐厅",
                    ),
                ),
            ),
            metadata = metadataRepository,
        )

        val detail = resolver.loadSeriesDetail("金庸武侠世界")

        assertTrue(metadataRepository.requestedIds.isEmpty())
        assertEquals(listOf("金庸武侠世界"), metadataRepository.requestedTitles)
        assertEquals("金庸武侠世界", detail?.series?.title)
        assertNull(detail?.series?.tmdbId)
    }

    @Test
    fun `loadSeriesDetail ignores unreasonable online metadata result`() = runBlocking {
        val dramaSource = MediaSourceInfoConventions.local(
            rootPath = "D:/Drama",
            name = "Drama",
        ).copy(id = 1L, contentMode = MediaContentMode.DRAMA)
        val resolver = LibraryDramaResolver(
            mediaSources = FakeMediaSourceRepository(listOf(dramaSource)),
            index = FakeMediaIndexRepository(
                listOf(
                    MediaIndexEntry(
                        sourceId = 1L,
                        path = "D:/Drama/金庸武侠世界/S01E01.mkv",
                        animeName = "金庸武侠世界",
                        seasonNumber = 1,
                        episodeNumber = 1,
                    ),
                ),
            ),
            metadata = object : DramaMetadataRepository {
                override suspend fun fetchSeriesMetadata(
                    title: String,
                    seasonHint: Int?,
                    seasonNumbers: List<Int>,
                ): Result<DramaSeriesMetadata?> =
                    Result.success(
                        DramaSeriesMetadata(
                            series = DramaSeries(
                                id = "tmdb:176599",
                                title = "WWW.迷糊餐厅",
                                tmdbId = 176599,
                            ),
                        ),
                    )
            },
        )

        val detail = resolver.loadSeriesDetail("金庸武侠世界")

        assertEquals("金庸武侠世界", detail?.series?.title)
        assertNull(detail?.series?.tmdbId)
        assertEquals(
            "TMDB 返回结果和本地剧名差太大，已忽略这次自动匹配。",
            detail?.metadataMessage,
        )
    }

    private fun resolver(
        sources: List<MediaSourceInfo> = emptyList(),
        entries: List<MediaIndexEntry> = emptyList(),
    ): LibraryDramaResolver =
        LibraryDramaResolver(
            mediaSources = FakeMediaSourceRepository(sources),
            index = FakeMediaIndexRepository(entries),
        )

    private class FakeMediaSourceRepository(
        private val sources: List<MediaSourceInfo>,
    ) : MediaSourceRepository {
        override suspend fun addSource(source: MediaSourceInfo): Result<Long> =
            Result.success(source.id)

        override suspend fun removeSource(sourceId: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun getSources(): Result<List<MediaSourceInfo>> =
            Result.success(sources)

        override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> =
            Result.success(Unit)

        override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> =
            sources.firstOrNull { it.id == sourceId }
                ?.let { Result.success(it) }
                ?: Result.failure(AppError.MediaSourceError.NotFound("Source id: $sourceId"))
    }

    private class FakeMediaIndexRepository(
        private val entries: List<MediaIndexEntry>,
    ) : MediaIndexRepository {
        override suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> =
            Result.success(Unit)

        override suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit> =
            Result.success(Unit)

        override suspend fun queryIndex(sourceId: Long, query: String): Result<List<MediaIndexEntry>> =
            Result.success(
                entries.filter { entry ->
                    entry.sourceId == sourceId && (
                        query.isBlank() ||
                            entry.path.contains(query, ignoreCase = true) ||
                            entry.animeName?.contains(query, ignoreCase = true) == true
                    )
                },
            )

        override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> =
            Result.success(entries.filter { it.sourceId == sourceId }.mapNotNull { it.animeName }.distinct())

        override suspend fun clearIndex(sourceId: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun saveLastBatchUndo(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> =
            Result.success(Unit)

        override suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>> =
            Result.success(emptyList())

        override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> =
            Result.success(Unit)
    }

    private class TrackingDramaMetadataRepository : DramaMetadataRepository {
        constructor(
            seriesTitle: String = "示例电视剧",
            seriesOriginalTitle: String = "",
        ) {
            this.seriesTitle = seriesTitle
            this.seriesOriginalTitle = seriesOriginalTitle
        }

        private var seriesTitle: String = "示例电视剧"
        private var seriesOriginalTitle: String = ""
        val requestedIds = mutableListOf<Int>()
        val requestedTitles = mutableListOf<String>()

        override suspend fun fetchSeriesMetadata(
            title: String,
            seasonHint: Int?,
            seasonNumbers: List<Int>,
        ): Result<DramaSeriesMetadata?> {
            requestedTitles += title
            return Result.success(null)
        }

        override suspend fun fetchSeriesMetadataById(
            tmdbId: Int,
            seasonNumbers: List<Int>,
        ): Result<DramaSeriesMetadata?> {
            requestedIds += tmdbId
            return Result.success(
                DramaSeriesMetadata(
                    series = DramaSeries(
                        id = "tmdb:$tmdbId",
                        title = seriesTitle,
                        originalTitle = seriesOriginalTitle,
                        tmdbId = tmdbId,
                    ),
                ),
            )
        }
    }

    private class FakeMetadataRepository(
        metadataById: Map<String, Anime> = emptyMap(),
    ) : MetadataRepository {
        private val storedMetadata = metadataById.toMutableMap()

        override suspend fun cacheMetadata(anime: Anime): Result<Unit> =
            Result.success(Unit).also {
                storedMetadata[anime.id] = anime
            }

        override suspend fun getCachedMetadata(animeId: String): Result<Anime?> =
            Result.success(storedMetadata[animeId])

        override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> =
            Result.success(animeIds.mapNotNull(storedMetadata::get))

        override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> =
            Result.success(null)

        override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> =
            Result.success(emptyList())

        override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> =
            Result.success(Unit)

        override suspend fun invalidateCache(animeId: String): Result<Unit> =
            Result.success(Unit).also {
                storedMetadata.remove(animeId)
            }
    }
}
