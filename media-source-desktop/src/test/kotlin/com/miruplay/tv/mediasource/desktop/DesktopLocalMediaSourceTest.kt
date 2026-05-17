package com.miruplay.tv.mediasource.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DesktopLocalMediaSourceTest {
    @Test
    fun `listFiles returns directories before files and hides ignored names`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-desktop-source")
        try {
            Files.createDirectory(root.resolve("Season 01"))
            Files.writeString(root.resolve("Episode 01.mkv"), "video")
            Files.writeString(root.resolve("Thumbs.db"), "hidden")

            val source = sourceFor(root.toString())

            val result = source.listFiles()

            assertTrue(result is Result.Success)
            val entries = (result as Result.Success).data
            assertEquals(listOf("Season 01", "Episode 01.mkv"), entries.map { it.name })
            assertTrue(entries.first().isDirectory)
            assertEquals("video/x-matroska", entries.last().mimeType)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `openStream reads files inside root`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-desktop-source")
        try {
            val file = root.resolve("Episode 01.mkv")
            Files.writeString(file, "payload")

            val result = sourceFor(root.toString()).openStream(file.toString())

            assertTrue(result is Result.Success)
            (result as Result.Success).data.use { stream ->
                assertEquals("payload", stream.readBytes().decodeToString())
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `openStream with range reads requested bytes`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-desktop-source")
        try {
            val file = root.resolve("Episode 01.mkv")
            Files.writeString(file, "0123456789")

            val result = sourceFor(root.toString()).openStream(file.toString(), DesktopStreamRange(2, 5))

            assertTrue(result is Result.Success)
            (result as Result.Success).data.use { stream ->
                assertEquals("2345", stream.readBytes().decodeToString())
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `path outside root is rejected`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-desktop-source")
        val outside = Files.createTempFile("miruplay-outside", ".mkv")
        try {
            val result = sourceFor(root.toString()).getMetadata(outside.toString())

            assertTrue(result is Result.Error)
            assertTrue((result as Result.Error).error is AppError.MediaSourceError.NotFound)
        } finally {
            root.toFile().deleteRecursively()
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun `testConnection reports readable directory`() = runBlocking {
        val root = Files.createTempDirectory("miruplay-desktop-source")
        try {
            val result = sourceFor(root.toString()).testConnection()

            assertEquals(true, (result as Result.Success).data)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun sourceFor(path: String): DesktopLocalMediaSource =
        DesktopLocalMediaSource(
            MediaSourceInfo(
                name = "Local",
                type = MediaSourceType.LOCAL,
                connectionInfo = mapOf("path" to path),
                isConnected = true,
            )
        )
}
