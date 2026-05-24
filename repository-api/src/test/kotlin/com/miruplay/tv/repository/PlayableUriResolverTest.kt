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
