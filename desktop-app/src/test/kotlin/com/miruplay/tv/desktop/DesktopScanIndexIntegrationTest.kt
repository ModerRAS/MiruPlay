package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.cloudRssScheduledSyncCompleteStatus
import com.miruplay.tv.model.libraryRescanCompleteStatus
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

    @Test
    fun `cloud rss linked source rescan refreshes repository index`() = runBlocking {
        val mediaRoot = Files.createTempDirectory("miruplay-cloud-rss-rescan")
        val storePath = Files.createTempDirectory("miruplay-desktop-store").resolve("store.json")
        try {
            val oldShow = Files.createDirectory(mediaRoot.resolve("Old Show"))
            Files.writeString(oldShow.resolve("Old Show - 01.mkv"), "video")

            val repositories = DesktopRepositories.fileBacked(storePath)
            val sourceInfo = MediaSourceInfoConventions.local(
                name = "Cloud RSS Local",
                rootPath = mediaRoot.toString(),
                isConnected = true,
            ).copy(id = 7L)
            val sourceId = (repositories.mediaSources.addSource(sourceInfo) as Result.Success).data
            val persistedSourceInfo = sourceInfo.copy(id = sourceId)

            val initialScan = DesktopMediaLibraryScanner().scan(sourceId, DesktopLocalMediaSource(persistedSourceInfo)) as Result.Success
            repositories.index.rebuildIndex(sourceId, initialScan.data.entries)

            oldShow.toFile().deleteRecursively()
            val newShow = Files.createDirectory(mediaRoot.resolve("New Show"))
            Files.writeString(newShow.resolve("New Show - S02E03.mkv"), "video")

            val rescan = rescanCloudRssLinkedSource(
                sourceInfo = persistedSourceInfo,
                reason = cloudRssScheduledSyncCompleteStatus(),
                indexRepository = repositories.index,
            ) as Result.Success

            val all = repositories.index.queryIndex(sourceId, "") as Result.Success
            val videos = all.data.filterNot { it.isDirectory }

            assertEquals(DesktopCloudRssRescanTargetStatus.LIBRARY, rescan.data.targetStatus)
            assertEquals("定时同步完成，正在重扫 Cloud RSS Local · 本地...", rescan.data.startedStatus)
            assertEquals(libraryRescanCompleteStatus(1, 2), rescan.data.completedStatus)
            assertEquals(listOf("New Show"), videos.map { it.animeName })
            assertEquals(2, videos.single().seasonNumber)
            assertEquals(3, videos.single().episodeNumber)
            assertTrue(videos.none { it.path.contains("Old Show") })
        } finally {
            mediaRoot.toFile().deleteRecursively()
            storePath.parent.toFile().deleteRecursively()
        }
    }
}
