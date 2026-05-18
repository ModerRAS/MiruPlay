package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopStreamRange
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.player.mpv.RifeBackend
import com.miruplay.tv.player.mpv.RifeInterpolationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

class DesktopPlaybackPresentersTest {
    @Test
    fun `build playback source trims media path parses seconds and subtitles`() {
        val source = buildPlaybackSource(
            mediaPath = " D:/Anime/Episode 01.mkv ",
            subtitlePath = "D:/Anime/Episode 01.ass",
            startSeconds = "12.345",
            mediaSourceId = "library",
            episodeId = "episode-1",
        )

        assertEquals("D:/Anime/Episode 01.mkv", source.uri)
        assertEquals("library", source.mediaSourceId)
        assertEquals("episode-1", source.episodeId)
        assertEquals(12_345L, source.startPosition)
        assertEquals(listOf("D:/Anime/Episode 01.ass"), source.subtitleTracks.map { it.path })
    }

    @Test
    fun `build playback source clamps invalid or negative starts to zero`() {
        assertEquals(0L, buildPlaybackSource("video.mkv", "", "abc").startPosition)
        assertEquals(0L, buildPlaybackSource("video.mkv", "", "-3").startPosition)
    }

    @Test
    fun `build playback source requires media path`() {
        val error = runCatching { buildPlaybackSource("   ", "", "") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Choose a media URI or file path before launching mpv.", error?.message)
    }

    @Test
    fun `runtime config enables rife only when requested`() {
        val withoutRife = buildRuntimeConfig(
            mpvPath = "D:/MiruPlay/runtime/mpv/mpv.exe",
            configDir = "",
            fullscreen = true,
            keepOpen = false,
            rifeEnabled = false,
            rifeBackend = RifeBackend.DIRECTML,
        )
        val withRife = buildRuntimeConfig(
            mpvPath = "D:/MiruPlay/runtime/mpv/mpv.exe",
            configDir = "D:/MiruPlay/runtime/mpv/portable_config",
            fullscreen = false,
            keepOpen = true,
            rifeEnabled = true,
            rifeBackend = RifeBackend.NVIDIA,
        )

        assertEquals(Paths.get("D:/MiruPlay/runtime/mpv/mpv.exe"), withoutRife.mpvExecutable)
        assertNull(withoutRife.configDirectory)
        assertTrue(withoutRife.startFullscreen)
        assertNull(withoutRife.rife)
        assertEquals(Paths.get("D:/MiruPlay/runtime/mpv/portable_config"), withRife.configDirectory)
        assertEquals(RifeBackend.NVIDIA, withRife.rife?.backend)
        assertTrue(withRife.keepOpen)
    }

    @Test
    fun `validate runtime for launch surfaces missing selected rife script`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        try {
            val configDirectory = tempDir.resolve("portable_config")
            val scriptDirectory = configDirectory.resolve("vs")
            Files.createDirectories(scriptDirectory)
            val mpv = tempDir.resolve("mpv.exe")
            Files.createFile(mpv)
            val config = buildRuntimeConfig(
                mpvPath = mpv.toString(),
                configDir = configDirectory.toString(),
                fullscreen = false,
                keepOpen = false,
                rifeEnabled = true,
                rifeBackend = RifeBackend.DIRECTML,
            ).copy(rife = RifeInterpolationConfig(backend = RifeBackend.DIRECTML))

            val result = validateRuntimeForLaunch(config)

            assertTrue(result is Result.Error)
            assertEquals(
                "播放出错：RIFE script not found: ${scriptDirectory.resolve(RifeBackend.DIRECTML.scriptName)}",
                (result as Result.Error).error.toUserMessage(),
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `command preview quotes arguments with spaces`() {
        val preview = buildCommandPreview(
            mpvPath = "D:/MiruPlay/runtime/mpv/mpv player.exe",
            configDir = "",
            mediaPath = "D:/Anime/Episode 01.mkv",
            subtitlePath = "",
            startSeconds = "",
            fullscreen = false,
            keepOpen = true,
            rifeEnabled = false,
            rifeBackend = RifeBackend.DIRECTML,
        )

        val normalized = preview.replace('\\', '/')
        assertTrue(normalized.startsWith("\"D:/MiruPlay/runtime/mpv/mpv player.exe\""))
        assertTrue(normalized.endsWith("\"D:/Anime/Episode 01.mkv\""))
    }

    @Test
    fun `playable uri keeps direct and local paths without bridge`() {
        val bridge = FakePlaybackBridge()

        assertEquals("https://example.test/video.mkv", playableUriFor(null, bridge, " https://example.test/video.mkv "))
        assertEquals("D:/Anime/video.mkv", playableUriFor(localSource(), bridge, "D:/Anime/video.mkv"))
        assertEquals(emptyList<String>(), bridge.requests)
    }

    @Test
    fun `playable uri bridges credentialed remote paths`() {
        val bridge = FakePlaybackBridge()

        assertEquals("bridge:///Anime/Episode 01.mkv", playableUriFor(webDavSource(), bridge, "/Anime/Episode 01.mkv"))
        assertEquals("bridge://smb://nas/anime/Episode 01.mkv", playableUriFor(smbSource(), bridge, "smb://nas/anime/Episode 01.mkv"))
        assertEquals(listOf("/Anime/Episode 01.mkv", "smb://nas/anime/Episode 01.mkv"), bridge.requests)
    }

    @Test
    fun `playable uri leaves non absolute remote paths unchanged`() {
        val bridge = FakePlaybackBridge()

        assertEquals("Anime/Episode 01.mkv", playableUriFor(webDavSource(), bridge, "Anime/Episode 01.mkv"))
        assertEquals("/nas/anime/Episode 01.mkv", playableUriFor(smbSource(), bridge, "/nas/anime/Episode 01.mkv"))
        assertEquals(emptyList<String>(), bridge.requests)
    }

    private class FakePlaybackBridge : DesktopPlaybackUriBridge {
        val requests = mutableListOf<String>()

        override fun playableUri(source: DesktopMediaSource, path: String): String {
            requests += path
            return "bridge://$path"
        }
    }

    private fun localSource(): DesktopMediaSource = FakeMediaSource(MediaSourceType.LOCAL)

    private fun webDavSource(): DesktopMediaSource = FakeMediaSource(MediaSourceType.WEBDAV)

    private fun smbSource(): DesktopMediaSource = FakeMediaSource(MediaSourceType.SMB)

    private class FakeMediaSource(type: MediaSourceType) : DesktopMediaSource {
        override val id: String = type.name
        override val info: MediaSourceInfo = MediaSourceInfo(name = type.name, type = type)
        override val capabilities: MediaCapabilities = MediaCapabilities()

        override suspend fun listFiles(path: String): Result<List<FileEntry>> = Result.success(emptyList())
        override suspend fun openStream(path: String): Result<InputStream> = Result.success(ByteArrayInputStream(ByteArray(0)))
        override suspend fun openStream(path: String, range: DesktopStreamRange): Result<InputStream> = openStream(path)
        override suspend fun getMetadata(path: String): Result<FileMetadata> =
            Result.success(FileMetadata(name = path, path = path, isDirectory = false))
        override suspend fun testConnection(): Result<Boolean> = Result.success(true)
        override suspend fun close() = Unit
    }
}
