package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.distinctSeasonEpisodeCount
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaIndexMetadataCacheTest {
    @Test
    fun `cache writes anime and episodes from indexed entries`() = runBlocking {
        val metadata = FakeMetadataRepository()
        val source = MediaSourceInfoConventions.webDav(url = "https://dav.example/anime", name = "DAV").copy(id = 2L)
        val entries = listOf(
            MediaIndexEntry(sourceId = 2L, path = "/Frieren/Episode 02.mkv", animeName = "Frieren", episodeNumber = 2),
            MediaIndexEntry(sourceId = 2L, path = "/Frieren/Episode 01.mkv", animeName = "Frieren", episodeNumber = 1),
            MediaIndexEntry(
                sourceId = 2L,
                path = "/Frieren/NCOP01.mkv",
                animeName = "Frieren",
                extraKind = MediaExtraKind.NCOP,
                extraOrdinal = 1,
            ),
        )

        val result = MediaIndexMetadataCache(metadata).cache(source, entries)

        assertEquals(MediaIndexMetadataCacheResult(animeCached = 1, episodesCached = 2), result)
        assertEquals("Frieren", metadata.cachedAnime.getValue("Frieren").title)
        assertEquals(
            listOf("https://dav.example/anime/Frieren/Episode%2001.mkv", "https://dav.example/anime/Frieren/Episode%2002.mkv"),
            metadata.cachedEpisodes.getValue("Frieren").map { it.filePath },
        )
    }

    @Test
    fun `cache updates existing anime episode count`() = runBlocking {
        val metadata = FakeMetadataRepository(
            cachedAnime = mutableMapOf(
                "Frieren" to Anime(id = "Frieren", title = "Sousou no Frieren", episodeCount = 1),
            ),
        )
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val entries = listOf(
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren", episodeNumber = 1),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/02.mkv", animeName = "Frieren", episodeNumber = 2),
        )

        MediaIndexMetadataCache(metadata).cache(source, entries)

        assertEquals("Sousou no Frieren", metadata.cachedAnime.getValue("Frieren").title)
        assertEquals(2, metadata.cachedAnime.getValue("Frieren").episodeCount)
    }

    @Test
    fun `cache counts duplicate indexed files as one logical episode`() = runBlocking {
        val metadata = FakeMetadataRepository()
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val entries = listOf(
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/WEB/S01E01.mkv", animeName = "Frieren", seasonNumber = 1, episodeNumber = 1),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/BD/S01E01.mkv", animeName = "Frieren", seasonNumber = 1, episodeNumber = 1),
        )

        MediaIndexMetadataCache(metadata).cache(source, entries)

        assertEquals(2, metadata.cachedEpisodes.getValue("Frieren").size)
        assertEquals(1, metadata.cachedAnime.getValue("Frieren").episodeCount)
    }

    @Test
    fun `cache assigns deterministic non-colliding identities around explicit episodes`() = runBlocking {
        val metadata = FakeMetadataRepository()
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val entries = listOf(
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Show/WEB/S01E02.mkv", animeName = "Show", seasonNumber = 1, episodeNumber = 2),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Show/Unknown A.mkv", animeName = "Show", seasonNumber = 1),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Show/Legacy.mkv", animeName = "Show"),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Show/BD/S01E02.mkv", animeName = "Show", seasonNumber = 1, episodeNumber = 2),
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Show/Unknown B.mkv", animeName = "Show", seasonNumber = 1),
            MediaIndexEntry(
                sourceId = 1L,
                path = "D:/Anime/Show/NCOP01.mkv",
                animeName = "Show",
                extraKind = MediaExtraKind.NCOP,
                extraOrdinal = 1,
            ),
        )

        val result = MediaIndexMetadataCache(metadata).cache(source, entries)
        val cached = metadata.cachedEpisodes.getValue("Show")
        val identitiesById = cached.associate { it.id to (it.seasonNumber to it.episodeNumber) }

        assertEquals(MediaIndexMetadataCacheResult(animeCached = 1, episodesCached = 5), result)
        assertEquals(
            mapOf(
                "1:D:/Anime/Show/WEB/S01E02.mkv" to (1 to 2),
                "1:D:/Anime/Show/BD/S01E02.mkv" to (1 to 2),
                "1:D:/Anime/Show/Unknown A.mkv" to (1 to 1),
                "1:D:/Anime/Show/Unknown B.mkv" to (1 to 3),
                "1:D:/Anime/Show/Legacy.mkv" to (1 to 4),
            ),
            identitiesById,
        )
        assertEquals(4, metadata.cachedAnime.getValue("Show").episodeCount)
        assertEquals(
            cached.map { Triple(it.id, it.seasonNumber, it.episodeNumber) },
            entries.toCachedIndexedEpisodes(source, "Show").map { Triple(it.id, it.seasonNumber, it.episodeNumber) },
        )
    }

    @Test
    fun `cache lets caller enrich anime and episodes`() = runBlocking {
        val metadata = FakeMetadataRepository()
        val source = MediaSourceInfoConventions.local(rootPath = "D:/Anime", name = "Local").copy(id = 1L)
        val entries = listOf(
            MediaIndexEntry(sourceId = 1L, path = "D:/Anime/Frieren/01.mkv", animeName = "Frieren"),
        )

        MediaIndexMetadataCache(metadata).cache(
            source = source,
            entries = entries,
            animeTransform = { animeId, episodes ->
                Anime(id = animeId, title = "Enriched $animeId", episodeCount = episodes.distinctSeasonEpisodeCount())
            },
            episodeTransform = { _, episodes ->
                episodes.map { it.copy(title = "Enriched Episode") }
            },
        )

        assertEquals("Enriched Frieren", metadata.cachedAnime.getValue("Frieren").title)
        assertEquals("Enriched Episode", metadata.cachedEpisodes.getValue("Frieren").single().title)
        assertEquals(1, metadata.cachedEpisodes.getValue("Frieren").single().episodeNumber)
    }

    private class FakeMetadataRepository(
        val cachedAnime: MutableMap<String, Anime> = mutableMapOf(),
        val cachedEpisodes: MutableMap<String, List<Episode>> = mutableMapOf(),
    ) : MetadataRepository {
        override suspend fun cacheMetadata(anime: Anime): Result<Unit> {
            cachedAnime[anime.id] = anime
            return Result.success(Unit)
        }

        override suspend fun getCachedMetadata(animeId: String): Result<Anime?> =
            Result.success(cachedAnime[animeId])

        override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> =
            Result.success(animeIds.mapNotNull(cachedAnime::get))

        override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> =
            Result.success(cachedEpisodes.values.flatten().firstOrNull { it.id == episodeId })

        override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> =
            Result.success(cachedEpisodes[animeId].orEmpty())

        override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> {
            cachedEpisodes[animeId] = episodes
            return Result.success(Unit)
        }

        override suspend fun invalidateCache(animeId: String): Result<Unit> {
            cachedAnime.remove(animeId)
            cachedEpisodes.remove(animeId)
            return Result.success(Unit)
        }
    }
}
