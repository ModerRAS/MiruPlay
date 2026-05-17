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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

class DesktopPlaybackBridgeTest {
    @Test
    fun `playableUri serves source stream through loopback url`() {
        val source = FakeMediaSource("payload")
        DesktopPlaybackBridge().use { bridge ->
            val url = bridge.playableUri(source, "smb://nas.local/anime/Episode 01.mkv")

            val connection = URI.create(url).toURL().openConnection() as HttpURLConnection

            assertTrue(url.startsWith("http://127.0.0.1:${bridge.port}/stream/"))
            assertFalse(url.contains("nas.local"))
            assertEquals(200, connection.responseCode)
            assertEquals("payload", connection.inputStream.use { it.readBytes().decodeToString() })
            assertEquals("smb://nas.local/anime/Episode 01.mkv", source.lastOpenedPath)
        }
    }

    @Test
    fun `playableUri supports byte range requests`() {
        val source = FakeMediaSource("0123456789")
        DesktopPlaybackBridge().use { bridge ->
            val url = bridge.playableUri(source, "smb://nas.local/anime/Episode 01.mkv")
            val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.setRequestProperty("Range", "bytes=2-5")

            assertEquals(206, connection.responseCode)
            assertEquals("bytes 2-5/10", connection.getHeaderField("Content-Range"))
            assertEquals("bytes", connection.getHeaderField("Accept-Ranges"))
            assertEquals("2345", connection.inputStream.use { it.readBytes().decodeToString() })
            assertEquals(DesktopStreamRange(2, 5), source.lastRange)
        }
    }

    @Test
    fun `playableUri rejects unsatisfiable byte ranges`() {
        val source = FakeMediaSource("0123456789")
        DesktopPlaybackBridge().use { bridge ->
            val url = bridge.playableUri(source, "smb://nas.local/anime/Episode 01.mkv")
            val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.setRequestProperty("Range", "bytes=20-30")

            assertEquals(416, connection.responseCode)
            assertEquals("bytes */10", connection.getHeaderField("Content-Range"))
        }
    }

    private class FakeMediaSource(
        private val payload: String,
    ) : DesktopMediaSource {
        var lastOpenedPath: String = ""
        var lastRange: DesktopStreamRange? = null

        override val id: String = "fake"
        override val info: MediaSourceInfo = MediaSourceInfo(
            name = "Fake SMB",
            type = MediaSourceType.SMB,
            connectionInfo = mapOf("url" to "smb://nas.local/anime"),
        )
        override val capabilities: MediaCapabilities = MediaCapabilities()

        override suspend fun listFiles(path: String): Result<List<FileEntry>> =
            Result.success(emptyList())

        override suspend fun openStream(path: String): Result<InputStream> {
            lastOpenedPath = path
            lastRange = null
            return Result.success(ByteArrayInputStream(payload.toByteArray()))
        }

        override suspend fun openStream(path: String, range: DesktopStreamRange): Result<InputStream> {
            lastOpenedPath = path
            lastRange = range
            val bytes = payload.toByteArray()
            val start = range.start.toInt().coerceAtMost(bytes.size)
            val endExclusive = ((range.endInclusive ?: (bytes.size - 1L)) + 1L)
                .toInt()
                .coerceAtMost(bytes.size)
            return Result.success(ByteArrayInputStream(bytes.copyOfRange(start, endExclusive)))
        }

        override suspend fun getMetadata(path: String): Result<FileMetadata> =
            Result.success(
                FileMetadata(
                    name = path.substringAfterLast('/'),
                    path = path,
                    isDirectory = false,
                    size = payload.toByteArray().size.toLong(),
                )
            )

        override suspend fun testConnection(): Result<Boolean> =
            Result.success(true)

        override suspend fun close() {
            // No resources in this fake.
        }
    }
}
