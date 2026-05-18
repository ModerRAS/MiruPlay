package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopStreamRange
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class DesktopPlaybackPresentersTest {
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
