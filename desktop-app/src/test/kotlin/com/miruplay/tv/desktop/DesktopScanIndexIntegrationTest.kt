package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.repository.desktop.DesktopRepositories
import com.miruplay.tv.scanner.desktop.DesktopMediaLibraryScanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DesktopScanIndexIntegrationTest {
    @Test
    fun `desktop scanner results can be persisted and queried from repository index`() = runBlocking {
        val mediaRoot = Files.createTempDirectory("miruplay-desktop-library")
        val storePath = Files.createTempDirectory("miruplay-desktop-store").resolve("store.json")
        try {
            val show = Files.createDirectory(mediaRoot.resolve("Bocchi the Rock"))
            Files.writeString(show.resolve("Bocchi the Rock - 03.mkv"), "video")

            val repositories = DesktopRepositories.fileBacked(storePath)
            val source = DesktopLocalMediaSource.create("Local", mediaRoot)
            val sourceId = 1L

            val scan = DesktopMediaLibraryScanner().scan(sourceId, source) as Result.Success
            repositories.index.rebuildIndex(sourceId, scan.data.entries)

            val query = repositories.index.queryIndex(sourceId, "Bocchi") as Result.Success
            val videos = query.data.filterNot { it.isDirectory }

            assertEquals(1, videos.size)
            assertEquals("Bocchi the Rock", videos.single().animeName)
            assertEquals(3, videos.single().episodeNumber)
            assertTrue(videos.single().path.endsWith("Bocchi the Rock - 03.mkv"))
        } finally {
            mediaRoot.toFile().deleteRecursively()
            storePath.parent.toFile().deleteRecursively()
        }
    }
}
