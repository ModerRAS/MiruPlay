package com.miruplay.tv.player

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.repository.MediaSourceRepository
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
