package com.miruplay.tv.core.common

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDirectoryBrowserTest {
    @Test
    fun `browse lists readable child directories and hides system names`() {
        val root = Files.createTempDirectory("miruplay-local-browser-").toFile()
        try {
            File(root, "番剧").mkdirs()
            File(root, "Anime").mkdirs()
            File(root, ".hidden").mkdirs()
            File(root, "proc").mkdirs()
            File(root, "video.mkv").writeText("not a directory")

            val listing = LocalDirectoryBrowser.browse(root.absolutePath)

            assertEquals(root.absolutePath, listing.path)
            assertEquals(root.absolutePath, listing.displayPath)
            assertEquals(root.parentFile?.absolutePath, listing.parentPath)
            assertEquals(listOf("Anime", "番剧"), listing.entries.map { it.name })
            assertTrue(listing.entries.all { it.canRead })
            assertFalse(listing.entries.any { it.name == ".hidden" || it.name == "proc" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `browse blank path uses supplied roots provider`() {
        val first = Files.createTempDirectory("miruplay-root-a-").toFile()
        val second = Files.createTempDirectory("miruplay-root-b-").toFile()
        try {
            val listing = LocalDirectoryBrowser.browse("  ") { listOf(second, first) }

            assertEquals("", listing.path)
            assertEquals("设备存储", listing.displayPath)
            assertEquals(null, listing.parentPath)
            assertEquals(
                listOf(first.absolutePath, second.absolutePath),
                listing.entries.map { it.path },
            )
        } finally {
            first.deleteRecursively()
            second.deleteRecursively()
        }
    }

    @Test
    fun `local root discovery falls back to system roots when storage hints are unavailable`() {
        val first = Files.createTempDirectory("miruplay-system-root-a-").toFile()
        val second = Files.createTempDirectory("miruplay-system-root-b-").toFile()
        try {
            val roots = LocalDirectoryBrowser.localRootCandidates(
                preferredRootPaths = emptyList(),
                discoverRootPaths = emptyList(),
                externalStorage = null,
                secondaryStorage = null,
                systemRoots = arrayOf(second, first),
                listDirectoryChildren = { emptyArray() },
            )

            assertEquals(
                listOf(first.absolutePath, second.absolutePath),
                roots.map { it.absolutePath },
            )
        } finally {
            first.deleteRecursively()
            second.deleteRecursively()
        }
    }
}
