package com.miruplay.tv.core.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDirectoryBrowserTest {
    @Test
    fun `browse lists readable child directories and hides system names`() {
        val root = createTempDir(prefix = "miruplay-local-browser-")
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
}
