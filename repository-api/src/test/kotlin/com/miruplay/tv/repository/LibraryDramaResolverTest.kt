package com.miruplay.tv.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.DramaEpisodeMetadata
import com.miruplay.tv.model.DramaSeasonMetadata
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
