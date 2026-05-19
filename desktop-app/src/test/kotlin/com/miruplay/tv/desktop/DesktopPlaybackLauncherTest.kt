package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopPlaybackUriBridge
import com.miruplay.tv.mediasource.desktop.DesktopStreamRange
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.player.mpv.MpvRuntimeConfig
import com.miruplay.tv.player.mpv.MpvProcessLauncher
import com.miruplay.tv.player.mpv.MpvProcessPlayer
import com.miruplay.tv.player.mpv.RifeBackend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class DesktopPlaybackLauncherTest {
    @Test
    fun `command preview wrapper returns blank media message`() {
        val preview = desktopMpvCommandPreviewFromInputs(
            mpvPath = "C:/MiruPlay/mpv/mpv.exe",
            configDir = "C:/MiruPlay/mpv/portable_config",
            mediaPath = " ",
            subtitlePath = "",
            startSeconds = "",
            fullscreen = false,
            keepOpen = false,
            rifeEnabled = false,
            rifeBackend = RifeBackend.NVIDIA,
            blankMediaMessage = "Choose media first.",
            errorMessage = "Unable to build mpv command.",
        )

        assertEquals("Choose media first.", preview)
    }

    @Test
    fun `prepare bridges WebDAV playback and keeps original path as session id`() {
        val bridge = RecordingBridge()
        val remoteSource = FakeRemoteSource(
            MediaSourceInfoConventions.webDav(
                name = "Private WebDAV",
                url = "https://media.example.test/dav",
                username = "alice",
                password = "secret",
                isConnected = true,
            )
        )
        val launcher = DesktopPlaybackLauncher(
            bridge = bridge,
            runtimeValidator = { Result.success(null) },
        )

        val prepared = launcher.prepare(
            request(
                mediaPath = "  /Anime/Episode 01.mkv  ",
                activeSource = remoteSource,
                activeSourceId = 7L,
                startSeconds = "12",
            )
        )

        assertTrue(prepared is Result.Success)
        val data = (prepared as Result.Success).data
        assertEquals("http://127.0.0.1:19090/stream/1", data.source.uri)
        assertEquals("7", data.source.mediaSourceId)
        assertEquals(12_000L, data.source.startPosition)
        assertEquals("/Anime/Episode 01.mkv", data.source.episodeId)
        assertEquals("/Anime/Episode 01.mkv", data.session.episodeId)
        assertEquals("/Anime/Episode 01.mkv", bridge.paths.single())
    }

    @Test
    fun `prepare returns runtime validation error before building playback source`() {
        val launcher = DesktopPlaybackLauncher(
            bridge = RecordingBridge(),
            runtimeValidator = {
                Result.failure(AppError.PlaybackError.StreamError("mpv executable not found: C:/missing/mpv.exe"))
            },
        )

        val prepared = launcher.prepare(request(mediaPath = "D:/Anime/Episode.mkv"))

        assertTrue(prepared is Result.Error)
        assertEquals(
            "播放出错：mpv executable not found: C:/missing/mpv.exe",
            (prepared as Result.Error).error.toUserMessage(),
        )
    }

    @Test
    fun `prepare keeps local path unbridged and falls back to desktop media source id`() {
        val bridge = RecordingBridge()
        val launcher = DesktopPlaybackLauncher(
            bridge = bridge,
            runtimeValidator = { Result.success(null) },
        )

        val prepared = launcher.prepare(request(mediaPath = "D:/Anime/Episode.mkv"))

        assertTrue(prepared is Result.Success)
        val data = (prepared as Result.Success).data
        assertEquals("D:/Anime/Episode.mkv", data.source.uri)
        assertEquals("desktop-compose", data.source.mediaSourceId)
        assertTrue(bridge.paths.isEmpty())
    }

    @Test
    fun `launch returns player session and launch status`() = runBlocking {
        val mpv = Files.createTempFile("miruplay-launcher-test", ".exe")
        val process = FakeProcess(pidValue = 1234L)
        val launcher = DesktopPlaybackLauncher(
            bridge = RecordingBridge(),
            runtimeValidator = { Result.success(null) },
            playerFactory = { config ->
                MpvProcessPlayer(
                    config = config.copy(mpvExecutable = mpv),
                    processLauncher = MpvProcessLauncher { process },
                )
            },
        )

        val launched = launcher.launch(
            request(
                mediaPath = "D:/Anime/Episode.mkv",
                startSeconds = "3",
            )
        )

        assertTrue(launched is Result.Success)
        val data = (launched as Result.Success).data
        assertEquals("D:/Anime/Episode.mkv", data.session.episodeId)
        assertEquals(3_000L, data.source.startPosition)
        assertEquals("mpv launched: pid 1234", data.status)
        assertTrue(data.launch.command.any { it.contains("Episode.mkv") })
    }

    private fun request(
        mediaPath: String,
        activeSource: DesktopMediaSource? = null,
        activeSourceId: Long? = null,
        startSeconds: String = "",
    ): DesktopPlaybackLaunchRequest =
        DesktopPlaybackLaunchRequest(
            mpvPath = "C:/MiruPlay/mpv/mpv.exe",
            configDir = "C:/MiruPlay/mpv/portable_config",
            mediaPath = mediaPath,
            subtitlePath = "",
            startSeconds = startSeconds,
            fullscreen = false,
            keepOpen = false,
            rifeEnabled = false,
            rifeBackend = RifeBackend.NVIDIA,
            activeSource = activeSource,
            activeSourceId = activeSourceId,
            blankMediaMessage = "Choose media first.",
            fallbackMediaSourceId = "desktop-compose",
        )

    private class RecordingBridge : DesktopPlaybackUriBridge {
        val paths = mutableListOf<String>()

        override fun playableUri(source: DesktopMediaSource, path: String): String {
            paths += path
            return "http://127.0.0.1:19090/stream/${paths.size}"
        }
    }

    private class FakeRemoteSource(
        override val info: MediaSourceInfo,
    ) : DesktopMediaSource {
        override val id: String = info.id.toString()
        override val capabilities: MediaCapabilities = MediaCapabilities(supportsRange = true, supportsList = true)

        override suspend fun listFiles(path: String): Result<List<FileEntry>> =
            Result.success(emptyList())

        override suspend fun openStream(path: String): Result<InputStream> =
            Result.success(ByteArrayInputStream("payload".toByteArray()))

        override suspend fun openStream(path: String, range: DesktopStreamRange): Result<InputStream> =
            openStream(path)

        override suspend fun getMetadata(path: String): Result<FileMetadata> =
            Result.success(
                FileMetadata(
                    name = path.substringAfterLast('/'),
                    path = path,
                    isDirectory = false,
                    size = 7L,
                )
            )

        override suspend fun testConnection(): Result<Boolean> =
            Result.success(true)

        override suspend fun close() = Unit
    }

    private class FakeProcess(
        private val pidValue: Long,
    ) : Process() {
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = 0

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = true

        override fun exitValue(): Int = 0

        override fun destroy() = Unit

        override fun destroyForcibly(): Process = this

        override fun isAlive(): Boolean = false

        override fun pid(): Long = pidValue
    }
}
