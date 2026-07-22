package com.miruplay.tv.player

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.repository.MediaSourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackHttpRequestResolverTest {
    @Test
    fun `configFor adds WebDAV authorization header using episode source id`() = runBlocking {
        val resolver = PlaybackHttpRequestResolver(
            FakeMediaSourceRepository(
                listOf(
                    MediaSourceInfoConventions.webDav(
                        url = "http://127.0.0.1:19798/dav",
                        username = "alice",
                        password = "secret",
                    ).copy(id = 42),
                ),
            ),
        )

        val config = resolver.configFor(
            PlaybackSource(
                uri = "http://127.0.0.1:19798/dav/Show/Episode%2001.mkv",
                mediaSourceId = "anime",
                episodeId = "42:/Show/Episode 01.mkv",
            ),
        )

        assertEquals(
            "Basic YWxpY2U6c2VjcmV0",
            config.headersFor("http://127.0.0.1:19798/dav/Show/Episode%2001.mkv")["Authorization"],
        )
    }

    @Test
    fun `configFor warms CloudDrive parent directory before playback`() = runBlocking {
        val sourceInfo = MediaSourceInfoConventions.webDav(
            url = "http://127.0.0.1:19798/dav",
        ).copy(id = 42)
        val webDavSource = mockk<MediaSource>()
        coEvery { webDavSource.listFiles(any()) } returns Result.success(emptyList())
        coEvery { webDavSource.close() } returns Unit
        val mediaSourceFactory = mockk<MediaSourceFactory>()
        every { mediaSourceFactory.create(sourceInfo) } returns Result.success(webDavSource)
        val resolver = PlaybackHttpRequestResolver(
            mediaSources = FakeMediaSourceRepository(listOf(sourceInfo)),
            mediaSourceFactory = mediaSourceFactory,
        )

        resolver.configFor(
            PlaybackSource(
                uri = "http://127.0.0.1:19798/dav/115open/%E5%BD%B1%E9%9F%B3/%E5%8A%A8%E6%BC%AB/%E5%BE%9E%200%20%E4%BD%8D%E5%B1%85%E6%B0%91%E9%96%8B%E5%A7%8B%E7%9A%84%E9%82%8A%E5%A2%83%E9%A0%98%E4%B8%BB%E5%A4%A7%E4%BA%BA/Season%201/%5BANi%5D%2003.mp4",
                mediaSourceId = "anime",
                episodeId = "42:/115open/影音/动漫/episode.mp4",
            ),
        )

        coVerify(exactly = 1) {
            webDavSource.listFiles("/115open/影音/动漫/從 0 位居民開始的邊境領主大人/Season 1")
        }
        coVerify(exactly = 1) { webDavSource.close() }
    }

    @Test
    fun `configFor does not warm ordinary WebDAV source`() = runBlocking {
        val sourceInfo = MediaSourceInfoConventions.webDav(
            url = "https://dav.example.test/library",
        ).copy(id = 42)
        val mediaSourceFactory = mockk<MediaSourceFactory>(relaxed = true)
        val resolver = PlaybackHttpRequestResolver(
            mediaSources = FakeMediaSourceRepository(listOf(sourceInfo)),
            mediaSourceFactory = mediaSourceFactory,
        )

        resolver.configFor(
            PlaybackSource(
                uri = "https://dav.example.test/library/Show/Episode%2001.mkv",
                mediaSourceId = "anime",
                episodeId = "42:/Show/Episode 01.mkv",
            ),
        )

        verify(exactly = 0) { mediaSourceFactory.create(any()) }
    }

    @Test
    fun `configFor chooses longest matching WebDAV root`() = runBlocking {
        val resolver = PlaybackHttpRequestResolver(
            FakeMediaSourceRepository(
                listOf(
                    MediaSourceInfoConventions.webDav(
                        url = "https://dav.example.test/root",
                        username = "alice",
                        password = "broad",
                    ).copy(id = 1),
                    MediaSourceInfoConventions.webDav(
                        url = "https://dav.example.test/root/private",
                        username = "bob",
                        password = "private",
                    ).copy(id = 2),
                ),
            ),
        )
        val uri = "https://dav.example.test/root/private/Show/Episode%2001.mkv"

        val config = resolver.configFor(
            PlaybackSource(uri = uri, mediaSourceId = "anime", episodeId = "cached-episode"),
        )

        assertEquals("Basic Ym9iOnByaXZhdGU=", config.headersFor(uri)["Authorization"])
    }

    @Test
    fun `configFor matches absolute WebDAV URL when cached episode id has no source id`() = runBlocking {
        val resolver = PlaybackHttpRequestResolver(
            FakeMediaSourceRepository(
                listOf(
                    MediaSourceInfoConventions.webDav(
                        url = "http://127.0.0.1:19798/dav",
                        username = "alice",
                        password = "secret",
                    ).copy(id = 42),
                ),
            ),
        )

        val config = resolver.configFor(
            PlaybackSource(
                uri = "http://127.0.0.1:19798/dav/Show Name/Episode 01.mkv",
                mediaSourceId = "Show Name",
                episodeId = "http://127.0.0.1:19798/dav/Show Name/Episode 01.mkv",
            ),
        )

        assertEquals(
            "Basic YWxpY2U6c2VjcmV0",
            config.headersFor("http://127.0.0.1:19798/dav/Show%20Name/Episode%2001.mkv")["Authorization"],
        )
    }

    @Test
    fun `configFor does not attach WebDAV authorization to unrelated HTTP URLs`() = runBlocking {
        val resolver = PlaybackHttpRequestResolver(
            FakeMediaSourceRepository(
                listOf(
                    MediaSourceInfoConventions.webDav(
                        url = "http://127.0.0.1:19798/dav",
                        username = "alice",
                        password = "secret",
                    ).copy(id = 42),
                ),
            ),
        )

        val config = resolver.configFor(
            PlaybackSource(
                uri = "http://cdn.example.test/video.mkv",
                mediaSourceId = "anime",
                episodeId = "external-video",
            ),
        )

        assertFalse("Authorization" in config.headersFor("http://cdn.example.test/video.mkv"))
    }

    @Test
    fun `configFor uses anonymous WebDAV authorization when username is blank`() = runBlocking {
        val resolver = PlaybackHttpRequestResolver(
            FakeMediaSourceRepository(
                listOf(
                    MediaSourceInfoConventions.webDav(
                        url = "http://127.0.0.1:19798/dav",
                    ).copy(id = 42),
                ),
            ),
        )

        val config = resolver.configFor(
            PlaybackSource(
                uri = "http://127.0.0.1:19798/dav/Show/Episode%2001.mkv",
                mediaSourceId = "anime",
                episodeId = "42:/Show/Episode 01.mkv",
            ),
        )

        assertEquals(
            "Basic YW5vbnltb3VzOg==",
            config.headersFor("http://127.0.0.1:19798/dav/Show/Episode%2001.mkv")["Authorization"],
        )
    }

    @Test
    fun `configFor falls back to uri matching when direct playback mediaSourceId collides with non-webdav source id`() = runBlocking {
        val resolver = PlaybackHttpRequestResolver(
            FakeMediaSourceRepository(
                listOf(
                    MediaSourceInfoConventions.local(
                        name = "Test Local",
                        rootPath = "/sdcard/Movies/MiruPlayHdrTest",
                    ).copy(id = 1),
                    MediaSourceInfoConventions.webDav(
                        url = "http://127.0.0.1:19798/dav/library",
                        username = "alice",
                        password = "secret",
                    ).copy(id = 2),
                ),
            ),
        )

        val config = resolver.configFor(
            PlaybackSource(
                uri = "http://127.0.0.1:19798/dav/library/1.mp4",
                mediaSourceId = "1",
                episodeId = null,
            ),
        )

        assertEquals(
            "Basic YWxpY2U6c2VjcmV0",
            config.headersFor("http://127.0.0.1:19798/dav/library/1.mp4")["Authorization"],
        )
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
