package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopPlaybackBridge
import com.miruplay.tv.mediasource.desktop.DesktopStreamRange
import com.miruplay.tv.mediasource.desktop.playableUriFor
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.FileMetadata
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.playbackSourceFromInputs
import com.miruplay.tv.player.mpv.MpvCommandBuilder
import com.miruplay.tv.player.mpv.MpvRuntimeConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Paths

class DesktopRemotePlaybackSecurityTest {
    @Test
    fun `webdav playback command uses bridge url without remote credentials`() {
        val source = FakeRemoteSource(
            MediaSourceInfoConventions.webDav(
                name = "Private WebDAV",
                url = "https://media.example.test/dav",
                username = "alice",
                password = "webdav-secret",
                isConnected = true,
            ),
        )

        assertRemoteCommandIsSanitized(
            source = source,
            mediaPath = "/Anime/Episode 01.mkv",
            forbiddenFragments = listOf(
                "media.example.test",
                "alice",
                "webdav-secret",
            ),
        )
    }

    @Test
    fun `smb playback command uses bridge url without share credentials`() {
        val source = FakeRemoteSource(
            MediaSourceInfoConventions.smb(
                name = "Private SMB",
                url = "smb://nas.example.test/anime",
                domain = "WORKGROUP",
                username = "bob",
                password = "smb-secret",
                isConnected = true,
            ),
        )

        assertRemoteCommandIsSanitized(
            source = source,
            mediaPath = "smb://nas.example.test/anime/Episode 01.mkv",
            forbiddenFragments = listOf(
                "smb://",
                "nas.example.test",
                "WORKGROUP",
                "bob",
                "smb-secret",
            ),
        )
    }

    private fun assertRemoteCommandIsSanitized(
        source: DesktopMediaSource,
        mediaPath: String,
        forbiddenFragments: List<String>,
    ) {
        DesktopPlaybackBridge().use { bridge ->
            val bridgeUri = playableUriFor(source, bridge, mediaPath)
            val playbackSource = playbackSourceFromInputs(
                mediaPath = bridgeUri,
                subtitlePath = "",
                startSeconds = "",
                mediaSourceId = source.info.id.toString(),
                episodeId = mediaPath,
            )

            val command = MpvCommandBuilder(
                MpvRuntimeConfig(
                    mpvExecutable = Paths.get("C:/MiruPlay/mpv/mpv.exe"),
                    rife = null,
                )
            ).build(playbackSource)
            val commandLine = command.joinToString(" ")

            assertTrue(command.last().startsWith("http://127.0.0.1:${bridge.port}/stream/"))
            forbiddenFragments.forEach { fragment ->
                assertFalse("Command leaked '$fragment': $commandLine", commandLine.contains(fragment))
            }
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
}
