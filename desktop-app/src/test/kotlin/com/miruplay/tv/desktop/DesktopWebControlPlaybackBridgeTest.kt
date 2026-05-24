package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopStreamRange
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.webcontrol.PlayEpisodeRequest
import com.miruplay.tv.webcontrol.PlaybackCommandRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class DesktopWebControlPlaybackBridgeTest {
    @Test
    fun `command status maps WebUI playback commands to shared mpv status text`() {
        assertEquals(
            "mpv seeked back 4s.",
            webControlPlaybackCommandStatus(PlaybackCommandRequest(command = "seek_relative", deltaMs = -4_000L)),
        )
        assertEquals(
            "mpv seeked forward 5s.",
            webControlPlaybackCommandStatus(PlaybackCommandRequest(command = "seek_relative", deltaMs = 5_000L)),
        )
        assertEquals(
            "mpv seeked forward 30s.",
            webControlPlaybackCommandStatus(PlaybackCommandRequest(command = "skip_forward")),
        )
        assertEquals(
            "mpv seeked back 10s.",
            webControlPlaybackCommandStatus(PlaybackCommandRequest(command = "skip_backward")),
        )
        assertEquals(
            "mpv position synced at 00:45.",
            webControlPlaybackCommandStatus(PlaybackCommandRequest(command = "seek", positionMs = 45_000L)),
        )
    }

    @Test
    fun `source selection reuses active local source and does not own it`() = runBlocking {
        val sourceInfo = localSource(id = 7L, rootPath = "D:/Anime")
        val activeLocalSource = DesktopLocalMediaSource(sourceInfo)

        val selection = desktopWebControlPlaybackSourceSelection(
            episode = episode(id = "7:D:/Anime/Episode.mkv"),
            savedSources = listOf(sourceInfo),
            activeSourceId = 7L,
            activeSource = activeLocalSource,
            activeLocalSource = activeLocalSource,
            loadSourceById = { null },
        )

        assertEquals(7L, selection.sourceId)
        assertSame(activeLocalSource, selection.source)
        assertFalse(selection.ownsSource)
    }

    @Test
    fun `source selection reuses active remote source and does not own it`() = runBlocking {
        val sourceInfo = webDavSource(id = 8L)
        val activeRemoteSource = FakeDesktopMediaSource(sourceInfo)

        val selection = desktopWebControlPlaybackSourceSelection(
            episode = episode(id = "8:/Anime/Episode.mkv"),
            savedSources = listOf(sourceInfo),
            activeSourceId = 8L,
            activeSource = activeRemoteSource,
            activeLocalSource = null,
            loadSourceById = { null },
            sourceFactory = { error("Factory should not be called for the active source") },
        )

        assertEquals(8L, selection.sourceId)
        assertSame(activeRemoteSource, selection.source)
        assertFalse(selection.ownsSource)
    }

    @Test
    fun `source selection loads inactive source from repository and owns it`() = runBlocking {
        val sourceInfo = webDavSource(id = 9L)
        val createdSource = FakeDesktopMediaSource(sourceInfo)

        val selection = desktopWebControlPlaybackSourceSelection(
            episode = episode(id = "9:/Anime/Episode.mkv"),
            savedSources = emptyList(),
            activeSourceId = 8L,
            activeSource = FakeDesktopMediaSource(webDavSource(id = 8L)),
            activeLocalSource = null,
            loadSourceById = { id -> sourceInfo.takeIf { it.id == id } },
            sourceFactory = { createdSource },
        )

        assertEquals(9L, selection.sourceId)
        assertSame(createdSource, selection.source)
        assertTrue(selection.ownsSource)
    }

    @Test
    fun `source selection returns no source for episode ids without source prefix`() = runBlocking {
        val selection = desktopWebControlPlaybackSourceSelection(
            episode = episode(id = "standalone-episode-id"),
            savedSources = emptyList(),
            activeSourceId = null,
            activeSource = null,
            activeLocalSource = null,
            loadSourceById = { error("Repository should not be queried without a source id") },
        )

        assertNull(selection.sourceId)
        assertNull(selection.source)
        assertFalse(selection.ownsSource)
    }

    @Test
    fun `playback source prefers WebUI request over saved progress`() {
        val episode = episode(duration = 120_000L)
        val progress = ProgressRecord(
            episodeId = episode.id,
            positionMs = 30_000L,
            lastWatched = 1L,
        )

        val source = desktopWebControlPlaybackSource(
            request = PlayEpisodeRequest(episodeId = episode.id, startPositionMs = 5_000L),
            episode = episode,
            progress = progress,
        )

        assertEquals(episode.filePath, source.uri)
        assertEquals(episode.animeId, source.mediaSourceId)
        assertEquals(5_000L, source.startPosition)
        assertEquals(episode.id, source.episodeId)
    }

    @Test
    fun `playback source falls back to resumable saved progress`() {
        val episode = episode(duration = 120_000L)
        val progress = ProgressRecord(
            episodeId = episode.id,
            positionMs = 30_000L,
            lastWatched = 1L,
        )

        assertEquals(
            30_000L,
            desktopWebControlPlaybackSource(
                request = PlayEpisodeRequest(episodeId = episode.id),
                episode = episode,
                progress = progress,
            ).startPosition,
        )
    }

    @Test
    fun `playback source resets completed progress`() {
        val episode = episode(duration = 100_000L)
        val progress = ProgressRecord(
            episodeId = episode.id,
            positionMs = 95_000L,
            lastWatched = 1L,
        )

        assertEquals(
            0L,
            desktopWebControlPlaybackSource(
                request = PlayEpisodeRequest(episodeId = episode.id),
                episode = episode,
                progress = progress,
            ).startPosition,
        )
    }

    @Test
    fun `next playback source preserves WebUI owned source and parses source id`() {
        val currentSource = FakeDesktopMediaSource(webDavSource(id = 7L))
        val nextTarget = PlaybackSource(
            uri = "/Anime/Episode 02.mkv",
            mediaSourceId = "Anime",
            startPosition = 12_000L,
            episodeId = "7:/Anime/Episode 02.mkv",
        )

        val nextPlayback = desktopWebControlNextPlaybackSource(nextTarget, currentSource)

        assertSame(currentSource, nextPlayback.source)
        assertEquals(7L, nextPlayback.sourceId)
        assertEquals("7:/Anime/Episode 02.mkv", nextPlayback.episodeId)
    }

    @Test
    fun `next playback source is empty without a next target`() {
        val nextPlayback = desktopWebControlNextPlaybackSource(
            nextTarget = null,
            currentWebControlPlaybackSource = FakeDesktopMediaSource(webDavSource(id = 7L)),
        )

        assertNull(nextPlayback.source)
        assertNull(nextPlayback.sourceId)
        assertNull(nextPlayback.episodeId)
    }

    private fun episode(
        id: String = "7:/Anime/Episode 01.mkv",
        duration: Long = 0L,
    ): Episode =
        Episode(
            id = id,
            animeId = "anime-1",
            episodeNumber = 1,
            filePath = id.substringAfter(':', id),
            fileName = id.substringAfterLast('/'),
            duration = duration,
        )

    private fun localSource(id: Long, rootPath: String): MediaSourceInfo =
        MediaSourceInfoConventions.local(
            name = "Local",
            rootPath = rootPath,
            isConnected = true,
        ).copy(id = id)

    private fun webDavSource(id: Long): MediaSourceInfo =
        MediaSourceInfoConventions.webDav(
            name = "Remote",
            url = "https://media.example.test/dav/$id",
            isConnected = true,
        ).copy(id = id)

    private class FakeDesktopMediaSource(
        override val info: MediaSourceInfo,
    ) : DesktopMediaSource {
        override val id: String = info.id.toString()
        override val capabilities: MediaCapabilities = MediaCapabilities(supportsRange = true, supportsList = true)

        override suspend fun listFiles(path: String): Result<List<FileEntry>> =
            Result.success(emptyList())

        override suspend fun openStream(path: String): Result<InputStream> =
            Result.success(ByteArrayInputStream(ByteArray(0)))

        override suspend fun openStream(path: String, range: DesktopStreamRange): Result<InputStream> =
            openStream(path)

        override suspend fun getMetadata(path: String): Result<FileMetadata> =
            Result.success(
                FileMetadata(
                    name = path.substringAfterLast('/'),
                    path = path,
                    isDirectory = false,
                    size = 0L,
                )
            )

        override suspend fun testConnection(): Result<Boolean> =
            Result.success(true)

        override suspend fun close() = Unit
    }
}
