package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.libraryScanCompleteStatus
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
            val sourceInfo = source.info.copy(id = sourceId)

            val scan = scanAndIndexDesktopSource(
                sourceInfo = sourceInfo,
                indexRepository = repositories.index,
                metadataRepository = repositories.metadata,
            ) as Result.Success

            val query = repositories.index.queryIndex(sourceId, "Bocchi") as Result.Success
            val videos = query.data.filterNot { it.isDirectory }
            val cachedAnime = repositories.metadata.getCachedMetadata("Bocchi the Rock").getOrNull()
            val cachedEpisodes = repositories.metadata.getCachedEpisodes("Bocchi the Rock").getOrNull().orEmpty()

            assertEquals(sourceId, scan.data.sourceId)
            assertEquals(mediaRoot.fileName.toString(), scan.data.scanResult.animeName)
            assertEquals(1, scan.data.scanResult.episodesFound)
            assertEquals(1, scan.data.scanResult.newEpisodes)
            assertEquals(0, scan.data.scanResult.updatedEpisodes)
            assertEquals(libraryScanCompleteStatus(1, 2), scan.data.completedStatus)
            assertEquals(1, scan.data.filesIndexed)
            assertEquals(2, scan.data.directoriesVisited)
            assertEquals(videos, scan.data.videoEntries)
            assertEquals(1, videos.size)
            assertEquals("Bocchi the Rock", videos.single().animeName)
            assertEquals(3, videos.single().episodeNumber)
            assertTrue(videos.single().path.endsWith("Bocchi the Rock - 03.mkv"))
            assertEquals("Bocchi the Rock", cachedAnime?.title)
            assertEquals(1, cachedAnime?.episodeCount)
            assertEquals(listOf(3), cachedEpisodes.map { it.episodeNumber })
            assertEquals(videos.single().path, cachedEpisodes.single().filePath)
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
                metadataRepository = repositories.metadata,
            ) as Result.Success

            val all = repositories.index.queryIndex(sourceId, "") as Result.Success
            val videos = all.data.filterNot { it.isDirectory }
            val cachedEpisodes = repositories.metadata.getCachedEpisodes("New Show").getOrNull().orEmpty()

            assertEquals(DesktopCloudRssRescanTargetStatus.LIBRARY, rescan.data.targetStatus)
            assertEquals("定时同步完成，正在重扫 Cloud RSS Local · 本地...", rescan.data.startedStatus)
            assertEquals(libraryRescanCompleteStatus(1, 2), rescan.data.completedStatus)
            assertEquals(listOf("New Show"), videos.map { it.animeName })
            assertEquals(2, videos.single().seasonNumber)
            assertEquals(3, videos.single().episodeNumber)
            assertTrue(videos.none { it.path.contains("Old Show") })
            assertEquals(listOf(3), cachedEpisodes.map { it.episodeNumber })
        } finally {
            mediaRoot.toFile().deleteRecursively()
            storePath.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `cloud rss linked source resolver reports missing link and missing source`() = runBlocking {
        val storePath = Files.createTempDirectory("miruplay-desktop-store").resolve("store.json")
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)

            val missingLink = resolveCloudRssLinkedSource(
                sourceId = null,
                savedSources = emptyList(),
                mediaSources = repositories.mediaSources,
            ) as Result.Success
            assertEquals(DesktopCloudRssLinkedSourceSelection.MissingLink, missingLink.data)

            val missingSource = resolveCloudRssLinkedSource(
                sourceId = 123L,
                savedSources = emptyList(),
                mediaSources = repositories.mediaSources,
            ) as Result.Success
            assertEquals(
                DesktopCloudRssLinkedSourceSelection.MissingSource(123L),
                missingSource.data,
            )
        } finally {
            storePath.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `cloud rss linked source resolver loads repository sources when not cached`() = runBlocking {
        val storePath = Files.createTempDirectory("miruplay-desktop-store").resolve("store.json")
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            val source = MediaSourceInfoConventions.local(
                name = "Cloud RSS Local",
                rootPath = "D:/Anime",
                isConnected = true,
            )
            val sourceId = (repositories.mediaSources.addSource(source) as Result.Success).data
            var loadedSources = emptyList<com.miruplay.tv.model.MediaSourceInfo>()

            val result = resolveCloudRssLinkedSource(
                sourceId = sourceId,
                savedSources = emptyList(),
                mediaSources = repositories.mediaSources,
                onSourcesLoaded = { loaded -> loadedSources = loaded },
            ) as Result.Success

            assertTrue(loadedSources.any { it.id == sourceId })
            val selected = result.data as DesktopCloudRssLinkedSourceSelection.Ready
            assertEquals(sourceId, selected.sourceInfo.id)
            assertEquals("Cloud RSS Local", selected.sourceInfo.name)
        } finally {
            storePath.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `cloud rss resolve and rescan helper reports missing link and missing source`() = runBlocking {
        val storePath = Files.createTempDirectory("miruplay-desktop-store").resolve("store.json")
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)

            val missingLink = resolveAndRescanCloudRssLinkedSource(
                sourceId = null,
                reason = cloudRssScheduledSyncCompleteStatus(),
                savedSources = emptyList(),
                mediaSources = repositories.mediaSources,
                indexRepository = repositories.index,
                metadataRepository = repositories.metadata,
            ) as Result.Success
            assertEquals(DesktopCloudRssLinkedSourceRescanSelection.MissingLink, missingLink.data)

            val missingSource = resolveAndRescanCloudRssLinkedSource(
                sourceId = 999L,
                reason = cloudRssScheduledSyncCompleteStatus(),
                savedSources = emptyList(),
                mediaSources = repositories.mediaSources,
                indexRepository = repositories.index,
                metadataRepository = repositories.metadata,
            ) as Result.Success
            assertEquals(
                DesktopCloudRssLinkedSourceRescanSelection.MissingSource(999L),
                missingSource.data,
            )
        } finally {
            storePath.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `cloud rss resolve and rescan helper rescans selected source`() = runBlocking {
        val mediaRoot = Files.createTempDirectory("miruplay-cloud-rss-rescan-helper")
        val storePath = Files.createTempDirectory("miruplay-desktop-store").resolve("store.json")
        try {
            val oldShow = Files.createDirectory(mediaRoot.resolve("Old Show"))
            Files.writeString(oldShow.resolve("Old Show - 01.mkv"), "video")

            val repositories = DesktopRepositories.fileBacked(storePath)
            val sourceInfo = MediaSourceInfoConventions.local(
                name = "Cloud RSS Local",
                rootPath = mediaRoot.toString(),
                isConnected = true,
            )
            val sourceId = (repositories.mediaSources.addSource(sourceInfo) as Result.Success).data
            val persistedSourceInfo = sourceInfo.copy(id = sourceId)

            val initialScan = DesktopMediaLibraryScanner().scan(sourceId, DesktopLocalMediaSource(persistedSourceInfo)) as Result.Success
            repositories.index.rebuildIndex(sourceId, initialScan.data.entries)

            oldShow.toFile().deleteRecursively()
            val newShow = Files.createDirectory(mediaRoot.resolve("New Show"))
            Files.writeString(newShow.resolve("New Show - S01E05.mkv"), "video")

            val startedSources = mutableListOf<Long>()
            val result = resolveAndRescanCloudRssLinkedSource(
                sourceId = sourceId,
                reason = cloudRssScheduledSyncCompleteStatus(),
                savedSources = emptyList(),
                mediaSources = repositories.mediaSources,
                indexRepository = repositories.index,
                metadataRepository = repositories.metadata,
                onRescanStarting = { source -> startedSources += source.id },
            ) as Result.Success

            val ready = result.data as DesktopCloudRssLinkedSourceRescanSelection.Ready
            assertEquals(sourceId, ready.sourceInfo.id)
            assertEquals(listOf(sourceId), startedSources)
            assertEquals(DesktopCloudRssRescanTargetStatus.LIBRARY, ready.result.targetStatus)
            assertEquals(libraryRescanCompleteStatus(1, 2), ready.result.completedStatus)
            assertEquals("定时同步完成，正在重扫 Cloud RSS Local · 本地...", ready.result.startedStatus)

            val all = repositories.index.queryIndex(sourceId, "") as Result.Success
            val videos = all.data.filterNot { it.isDirectory }
            assertEquals(listOf("New Show"), videos.map { it.animeName })
            assertTrue(videos.none { it.path.contains("Old Show") })
        } finally {
            mediaRoot.toFile().deleteRecursively()
            storePath.parent.toFile().deleteRecursively()
        }
    }
}
