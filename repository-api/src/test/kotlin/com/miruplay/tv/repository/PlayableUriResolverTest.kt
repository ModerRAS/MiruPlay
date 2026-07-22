package com.miruplay.tv.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayableUriResolverTest {
    @Test
    fun `resolvePlayableUri leaves already playable uri unchanged`() = runBlocking {
        val repository = FakeMediaSourceRepository(
            listOf(MediaSourceInfoConventions.webDav(url = "https://dav.example/anime", name = "DAV")),
        )

        val uri = resolvePlayableUri(
            path = "https://cdn.example/video.mkv",
            episodeId = "1:episode",
            mediaRepository = repository,
        )

        assertEquals("https://cdn.example/video.mkv", uri)
    }

    @Test
    fun `resolvePlayableUri encodes raw unicode bracketed http paths`() = runBlocking {
        val repository = FakeMediaSourceRepository(emptyList())
        val path = "http://127.0.0.1:19798/dav/115open/影音/动漫/無職轉生～到了異世界就拿出真本事～/Season 3/[ANi] 無職轉生～到了異世界就拿出真本事～第三季 - 01 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4"

        val uri = resolvePlayableUri(
            path = path,
            episodeId = "1:episode",
            mediaRepository = repository,
        )

        assertEquals(
            "http://127.0.0.1:19798/dav/115open/%E5%BD%B1%E9%9F%B3/%E5%8A%A8%E6%BC%AB/%E7%84%A1%E8%81%B7%E8%BD%89%E7%94%9F%EF%BD%9E%E5%88%B0%E4%BA%86%E7%95%B0%E4%B8%96%E7%95%8C%E5%B0%B1%E6%8B%BF%E5%87%BA%E7%9C%9F%E6%9C%AC%E4%BA%8B%EF%BD%9E/Season%203/%5BANi%5D%20%E7%84%A1%E8%81%B7%E8%BD%89%E7%94%9F%EF%BD%9E%E5%88%B0%E4%BA%86%E7%95%B0%E4%B8%96%E7%95%8C%E5%B0%B1%E6%8B%BF%E5%87%BA%E7%9C%9F%E6%9C%AC%E4%BA%8B%EF%BD%9E%E7%AC%AC%E4%B8%89%E5%AD%A3%20-%2001%20%5B1080P%5D%5BBaha%5D%5BWEB-DL%5D%5BAAC%20AVC%5D%5BCHT%5D.mp4",
            uri,
        )
    }

    @Test
    fun `resolvePlayableUri preserves existing escapes in partially encoded paths`() = runBlocking {
        val uri = resolvePlayableUri(
            path = "http://127.0.0.1:19798/dav/%E5%BD%B1%E9%9F%B3/动漫/Season%203/[ANi] 01.mp4",
            episodeId = "1:episode",
            mediaRepository = FakeMediaSourceRepository(emptyList()),
        )

        assertEquals(
            "http://127.0.0.1:19798/dav/%E5%BD%B1%E9%9F%B3/%E5%8A%A8%E6%BC%AB/Season%203/%5BANi%5D%2001.mp4",
            uri,
        )
    }

    @Test
    fun `resolvePlayableUri joins WebDAV source URL using encoded path segments`() = runBlocking {
        val repository = FakeMediaSourceRepository(
            listOf(MediaSourceInfoConventions.webDav(url = "https://dav.example/anime/", name = "DAV").copy(id = 42)),
        )

        val uri = resolvePlayableUri(
            path = "/孤独摇滚/Season 01/Episode 01.mkv",
            episodeId = "42:episode",
            mediaRepository = repository,
        )

        assertEquals(
            "https://dav.example/anime/%E5%AD%A4%E7%8B%AC%E6%91%87%E6%BB%9A/Season%2001/Episode%2001.mkv",
            uri,
        )
    }

    @Test
    fun `resolvePlayableUri joins SMB source URL when episode id carries source id`() = runBlocking {
        val repository = FakeMediaSourceRepository(
            listOf(MediaSourceInfoConventions.smb(url = "smb://nas/anime/", name = "NAS").copy(id = 9)),
        )

        val uri = resolvePlayableUri(
            path = "Season 01/Episode 01.mkv",
            episodeId = "9:episode",
            mediaRepository = repository,
        )

        assertEquals("smb://nas/anime/Season%2001/Episode%2001.mkv", uri)
    }

    @Test
    fun `playable uri helper joins WebDAV and SMB indexed paths consistently`() {
        val webDav = MediaSourceInfoConventions.webDav(url = "https://dav.example/anime/", name = "DAV")
        val smb = MediaSourceInfoConventions.smb(url = "smb://nas/anime/", name = "NAS")

        assertEquals(
            "https://dav.example/anime/%E5%AD%A4%E7%8B%AC%E6%91%87%E6%BB%9A/Season%2001/Episode%2001.mkv",
            webDav.playableUriForIndexedPath("/孤独摇滚/Season 01/Episode 01.mkv"),
        )
        assertEquals(
            "smb://nas/anime/%E5%AD%A4%E7%8B%AC%E6%91%87%E6%BB%9A/Season%2001/Episode%2001.mkv",
            smb.playableUriForIndexedPath("孤独摇滚/Season 01/Episode 01.mkv"),
        )
        assertEquals(
            "smb://nas/anime/Season 01/Episode 01.mkv",
            smb.playableUriForIndexedPath("smb://nas/anime/Season 01/Episode 01.mkv"),
        )
        assertEquals(
            "https://cdn.example/episode.mkv",
            smb.playableUriForIndexedPath("https://cdn.example/episode.mkv"),
        )
    }

    @Test
    fun `indexed entry maps to episode with shared playable path and ordering`() {
        val source = MediaSourceInfoConventions.webDav(url = "https://dav.example/anime", name = "DAV").copy(id = 7L)
        val entries = listOf(
            MediaIndexEntry(sourceId = 7L, path = "/Show/Episode 02.mkv", episodeNumber = 2, episodeTitle = "Second"),
            MediaIndexEntry(sourceId = 7L, path = "/Show/Episode 01.mkv", episodeNumber = 1, episodeTitle = "First"),
        )

        val episodes = entries.toIndexedEpisodes(source, animeId = "show")

        assertEquals(listOf("7:/Show/Episode 01.mkv", "7:/Show/Episode 02.mkv"), episodes.map { it.id })
        assertEquals("show", episodes.first().animeId)
        assertEquals("First", episodes.first().title)
        assertEquals(
            "https://dav.example/anime/Show/Episode%2001.mkv",
            episodes.first().filePath,
        )
        assertEquals("Episode 01.mkv", episodes.first().fileName)
    }

    @Test
    fun `extras are excluded from episodes and projected separately`() {
        val source = MediaSourceInfoConventions.webDav(url = "https://dav.example/anime", name = "DAV").copy(id = 7L)
        val entries = listOf(
            MediaIndexEntry(sourceId = 7L, path = "/Show/01.mkv", episodeNumber = 1),
            MediaIndexEntry(
                sourceId = 7L,
                path = "/Show/NCOP02.mkv",
                episodeTitle = "NCOP 02",
                extraKind = MediaExtraKind.NCOP,
                extraOrdinal = 2,
                extraSortOrder = 2,
                duration = 90_000L,
            ),
            MediaIndexEntry(
                sourceId = 7L,
                path = "/Show/OVA.mkv",
                episodeTitle = "OVA",
                extraKind = MediaExtraKind.OVA,
                extraOrdinal = 1,
                extraSortOrder = 1,
            ),
        )

        assertEquals(listOf("7:/Show/01.mkv"), entries.toIndexedEpisodes(source, "show").map { it.id })
        val extras = entries.toIndexedExtras(source, "show")
        assertEquals(listOf("OVA", "NCOP 02"), extras.map { it.title })
        assertEquals(listOf(0, 0), extras.map { it.seasonNumber })
        assertEquals(90_000L, extras.last().duration)
    }

    @Test
    fun `resolvePlayableUri returns local path when no matching WebDAV source exists`() = runBlocking {
        val repository = FakeMediaSourceRepository(
            listOf(
                MediaSourceInfo(
                    id = 1,
                    name = "Local",
                    type = MediaSourceType.LOCAL,
                    connectionInfo = mapOf("path" to "D:/Anime"),
                ),
            ),
        )

        val uri = resolvePlayableUri(
            path = "D:/Anime/Show/Episode 01.mkv",
            episodeId = "1:episode",
            mediaRepository = repository,
        )

        assertEquals("D:/Anime/Show/Episode 01.mkv", uri)
    }

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
}
