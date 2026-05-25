package com.miruplay.tv.repository.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.RssDownloadStatus
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.BangumiCollectionService
import com.miruplay.tv.repository.BangumiEpisodeCollection
import com.miruplay.tv.repository.BangumiEpisodeCollectionType
import com.miruplay.tv.repository.BangumiSubjectCollection
import com.miruplay.tv.repository.BangumiSubjectCollectionType
import com.miruplay.tv.repository.BangumiUser
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.sync.BangumiSyncCore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DesktopRepositoriesTest {
    @Test
    fun `default store path honors system property override`() {
        val storePath = tempStorePath()
        val previous = System.getProperty(DesktopRepositoryPaths.STORE_PATH_PROPERTY)
        try {
            System.setProperty(DesktopRepositoryPaths.STORE_PATH_PROPERTY, storePath.toString())

            assertEquals(storePath, DesktopRepositoryPaths.defaultStorePath())
        } finally {
            if (previous == null) {
                System.clearProperty(DesktopRepositoryPaths.STORE_PATH_PROPERTY)
            } else {
                System.setProperty(DesktopRepositoryPaths.STORE_PATH_PROPERTY, previous)
            }
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `media sources are persisted and deduplicated by location`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            val source = MediaSourceInfo(
                name = "Anime",
                type = MediaSourceType.LOCAL,
                connectionInfo = mapOf("path" to "D:/Anime"),
                isConnected = true,
            )

            val firstId = repositories.mediaSources.addSource(source)
            val secondId = repositories.mediaSources.addSource(source.copy(name = "Anime Copy"))

            assertEquals((firstId as Result.Success).data, (secondId as Result.Success).data)

            val reopened = DesktopRepositories.fileBacked(storePath)
            val sources = reopened.mediaSources.getSources() as Result.Success
            assertEquals(1, sources.data.size)
            assertEquals("Anime Copy", sources.data.single().name)
        } finally {
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `adding source with same location updates stored configuration`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            val first = MediaSourceInfo(
                name = "Old WebDAV",
                type = MediaSourceType.WEBDAV,
                connectionInfo = mapOf("url" to "https://dav.example/anime", "username" to "old"),
                isConnected = false,
                lastScanned = 99L,
            )
            val second = first.copy(
                name = "New WebDAV",
                connectionInfo = mapOf("url" to "https://dav.example/anime", "username" to "new", "password" to "secret"),
                isConnected = true,
            )

            val firstId = (repositories.mediaSources.addSource(first) as Result.Success).data
            val secondId = (repositories.mediaSources.addSource(second) as Result.Success).data

            assertEquals(firstId, secondId)
            val source = (repositories.mediaSources.getSourceById(firstId) as Result.Success).data
            assertEquals("New WebDAV", source.name)
            assertEquals("new", source.connectionInfo["username"])
            assertEquals("secret", source.connectionInfo["password"])
            assertEquals(99L, source.lastScanned)
            assertTrue(source.isConnected)
        } finally {
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `progress is persisted and sorted for continue watching`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)

            repositories.progress.saveProgress("episode-1", positionMs = 1_000L, lastWatched = 10L)
            repositories.progress.saveProgress("episode-2", positionMs = 2_000L, lastWatched = 20L)
            repositories.progress.saveProgress("episode-1", positionMs = 3_000L, lastWatched = 30L, incrementPlayCount = true)

            val reopened = DesktopRepositories.fileBacked(storePath)
            val recent = reopened.progress.getContinueWatching(limit = 2) as Result.Success

            assertEquals(listOf("episode-1", "episode-2"), recent.data.map { it.episodeId })
            assertEquals(3_000L, recent.data.first().positionMs)
            assertEquals(1, recent.data.first().playCount)
        } finally {
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `playback end action preference is persisted`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)

            assertEquals(PlaybackEndAction.RETURN_TO_DETAIL, repositories.playbackPreferences.getEndAction())

            repositories.playbackPreferences.setEndAction(PlaybackEndAction.PLAY_NEXT_EPISODE)

            val reopened = DesktopRepositories.fileBacked(storePath)
            assertEquals(PlaybackEndAction.PLAY_NEXT_EPISODE, reopened.playbackPreferences.getEndAction())
        } finally {
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `metadata cache is persisted for Bangumi progress sync`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            val anime = Anime(
                id = "431767",
                title = "Sousou no Frieren",
                titleCn = "葬送的芙莉莲",
                bangumiId = 431767,
            )
            val episodes = listOf(
                Episode(
                    id = "D:/Anime/Frieren/01.mkv",
                    animeId = anime.id,
                    episodeNumber = 1,
                    filePath = "D:/Anime/Frieren/01.mkv",
                    fileName = "01.mkv",
                    bangumiEpisodeId = 1001,
                ),
            )

            repositories.metadata.cacheMetadata(anime)
            repositories.metadata.cacheEpisodes(anime.id, episodes)

            val reopened = DesktopRepositories.fileBacked(storePath)
            val cachedAnime = reopened.metadata.getCachedMetadata(anime.id) as Result.Success
            val cachedEpisodes = reopened.metadata.getCachedEpisodes(anime.id) as Result.Success

            assertEquals(431767, cachedAnime.data?.bangumiId)
            assertEquals(1, cachedAnime.data?.episodeCount)
            assertEquals(1001, cachedEpisodes.data.single().bangumiEpisodeId)

            reopened.metadata.invalidateCache(anime.id)
            assertEquals(null, (reopened.metadata.getCachedMetadata(anime.id) as Result.Success).data)
            assertTrue((reopened.metadata.getCachedEpisodes(anime.id) as Result.Success).data.isEmpty())
        } finally {
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `shared Bangumi sync core uses desktop metadata and progress stores`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            val anime = Anime(
                id = "431767",
                title = "Sousou no Frieren",
                titleCn = "葬送的芙莉莲",
                bangumiId = 431767,
            )
            val episodes = listOf(
                Episode(
                    id = "D:/Anime/Frieren/01.mkv",
                    animeId = anime.id,
                    episodeNumber = 1,
                    filePath = "D:/Anime/Frieren/01.mkv",
                    fileName = "01.mkv",
                    duration = 1_000L,
                    bangumiEpisodeId = 1001,
                ),
                Episode(
                    id = "D:/Anime/Frieren/02.mkv",
                    animeId = anime.id,
                    episodeNumber = 2,
                    filePath = "D:/Anime/Frieren/02.mkv",
                    fileName = "02.mkv",
                    duration = 2_000L,
                    bangumiEpisodeId = 1002,
                ),
            )
            val bangumiService = FakeBangumiCollectionService(
                episodeCollections = listOf(
                    BangumiEpisodeCollection(
                        episodeId = 1002,
                        episodeNumber = 2,
                        type = BangumiEpisodeCollectionType.DONE.value,
                    ),
                ),
            )
            val syncCore = BangumiSyncCore(
                bangumiService = bangumiService,
                metadataRepository = repositories.metadata,
                progressRepository = repositories.progress,
            )

            repositories.metadata.cacheMetadata(anime)
            repositories.metadata.cacheEpisodes(anime.id, episodes)
            repositories.progress.saveProgress(
                episodeId = episodes.first().id,
                positionMs = 1_000L,
                lastWatched = 10L,
            )

            val summary = syncCore.syncAnime(anime.id) as Result.Success

            assertEquals(1, summary.data.pushedEpisodes)
            assertEquals(1, summary.data.pulledEpisodes)
            assertEquals(listOf(1001), bangumiService.pushedEpisodeIds)
            assertEquals(listOf(BangumiEpisodeCollectionType.DONE), bangumiService.pushedEpisodeTypes)
            assertEquals(
                listOf(BangumiSubjectCollectionType.DOING, BangumiSubjectCollectionType.DONE),
                bangumiService.subjectCollectionTypes,
            )

            val pulledProgress = repositories.progress.getProgress(episodes[1].id) as Result.Success
            assertEquals(2_000L, pulledProgress.data?.positionMs)

            val cachedAnime = repositories.metadata.getCachedMetadata(anime.id) as Result.Success
            val cachedEpisodes = repositories.metadata.getCachedEpisodes(anime.id) as Result.Success
            assertEquals(BangumiSubjectCollectionType.DONE.value, cachedAnime.data?.bangumiCollectionType)
            assertEquals(2, cachedAnime.data?.bangumiEpStatus)
            assertEquals(
                listOf(BangumiEpisodeCollectionType.DONE.value, BangumiEpisodeCollectionType.DONE.value),
                cachedEpisodes.data.map { it.bangumiCollectionType },
            )
        } finally {
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `removing media source clears its index entries`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            val sourceId = (repositories.mediaSources.addSource(
                MediaSourceInfo(
                    name = "Anime",
                    type = MediaSourceType.LOCAL,
                    connectionInfo = mapOf("path" to "D:/Anime"),
                    isConnected = true,
                )
            ) as Result.Success).data
            repositories.index.rebuildIndex(
                sourceId = sourceId,
                entries = listOf(MediaIndexEntry(sourceId = 0L, path = "D:/Anime/A/01.mkv", animeName = "Show A")),
            )

            repositories.mediaSources.removeSource(sourceId)

            val sources = repositories.mediaSources.getSources() as Result.Success
            val entries = repositories.index.queryIndex(sourceId, "") as Result.Success
            assertTrue(sources.data.isEmpty())
            assertTrue(entries.data.isEmpty())
        } finally {
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `index can be rebuilt queried and cleared`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            repositories.index.rebuildIndex(
                sourceId = 7L,
                entries = listOf(
                    MediaIndexEntry(sourceId = 0L, path = "D:/Anime/A/01.mkv", animeName = "Show A"),
                    MediaIndexEntry(
                        sourceId = 0L,
                        path = "D:/Anime/B/01.mkv",
                        animeName = "Show B",
                        episodeTitle = "A Good Episode",
                        plot = "The indexed plot",
                    ),
                ),
            )

            val query = repositories.index.queryIndex(7L, "Show B") as Result.Success
            assertEquals("D:/Anime/B/01.mkv", query.data.single().path)

            val titleQuery = repositories.index.queryIndex(7L, "good episode") as Result.Success
            assertEquals("D:/Anime/B/01.mkv", titleQuery.data.single().path)

            val plotQuery = repositories.index.queryIndex(7L, "indexed plot") as Result.Success
            assertEquals("D:/Anime/B/01.mkv", plotQuery.data.single().path)

            val names = repositories.index.getAnimeInIndex(7L) as Result.Success
            assertEquals(listOf("Show A", "Show B"), names.data)

            repositories.index.clearIndex(7L)
            val cleared = repositories.index.queryIndex(7L, "") as Result.Success
            assertTrue(cleared.data.isEmpty())
        } finally {
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `index entry can be upserted with external metadata`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            repositories.index.rebuildIndex(
                sourceId = 7L,
                entries = listOf(
                    MediaIndexEntry(
                        sourceId = 0L,
                        path = "D:/Anime/Frieren/01.mkv",
                        animeName = "Frieren",
                        episodeNumber = 1,
                    )
                ),
            )

            repositories.index.upsertEntry(
                sourceId = 7L,
                entry = MediaIndexEntry(
                    sourceId = 0L,
                    path = "D:/Anime/Frieren/01.mkv",
                    animeName = "葬送的芙莉莲",
                    episodeNumber = 1,
                    metadataSource = "BANGUMI",
                    metadataId = "431767",
                    metadataTitle = "葬送的芙莉莲",
                ),
            )

            val byMetadataId = repositories.index.queryIndex(7L, "431767") as Result.Success
            assertEquals("葬送的芙莉莲", byMetadataId.data.single().animeName)
            assertEquals("BANGUMI", byMetadataId.data.single().metadataSource)
            assertEquals("431767", byMetadataId.data.single().metadataId)

            val all = repositories.index.queryIndex(7L, "") as Result.Success
            assertEquals(1, all.data.size)
        } finally {
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `last batch undo is persisted and cleared per source`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            val original = MediaIndexEntry(
                sourceId = 0L,
                path = "D:/Anime/Frieren/01.mkv",
                animeName = "Frieren",
                episodeNumber = 1,
            )

            repositories.index.saveLastBatchUndo(sourceId = 7L, entries = listOf(original))

            val reopened = DesktopRepositories.fileBacked(storePath)
            val undo = reopened.index.getLastBatchUndo(7L) as Result.Success
            assertEquals(1, undo.data.size)
            assertEquals(7L, undo.data.single().sourceId)
            assertEquals("Frieren", undo.data.single().animeName)

            reopened.index.clearLastBatchUndo(7L)
            val cleared = reopened.index.getLastBatchUndo(7L) as Result.Success
            assertTrue(cleared.data.isEmpty())
        } finally {
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `cloud drive config is persisted and last run can be updated`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            repositories.cloudDriveAutomation.saveConfig(
                CloudDriveAutomationConfig(
                    endpointUrl = "http://127.0.0.1:19798",
                    username = "miru",
                    inboxPath = "/downloads",
                    libraryPath = "/library",
                    enabled = true,
                ),
            )
            repositories.cloudDriveAutomation.updateLastRunAt(123L)

            val reopened = DesktopRepositories.fileBacked(storePath)
            val config = reopened.cloudDriveAutomation.getConfig() as Result.Success

            assertEquals("http://127.0.0.1:19798", config.data.endpointUrl)
            assertEquals("/downloads", config.data.inboxPath)
            assertTrue(config.data.enabled)
            assertEquals(123L, config.data.lastRunAt)
        } finally {
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `rss subscriptions processed items and tasks are persisted`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            val firstId = (repositories.cloudDriveAutomation.saveSubscription(
                RssSubscriptionInfo(name = "Feed", url = "https://example.test/rss.xml"),
            ) as Result.Success).data
            val secondId = (repositories.cloudDriveAutomation.saveSubscription(
                RssSubscriptionInfo(name = "Feed Updated", url = "https://example.test/rss.xml", enabled = true),
            ) as Result.Success).data

            assertEquals(firstId, secondId)

            repositories.cloudDriveAutomation.markSubscriptionChecked(firstId, 88L)
            repositories.cloudDriveAutomation.markItemProcessed(
                RssProcessedItemInfo(
                    subscriptionId = firstId,
                    itemKey = "item-1",
                    title = "Episode 01",
                    url = "magnet:?xt=urn:btih:abc",
                    processedAt = 90L,
                ),
            )
            val taskId = (repositories.cloudDriveAutomation.saveDownloadTask(
                RssDownloadTaskInfo(
                    subscriptionId = firstId,
                    itemKey = "item-1",
                    title = "Episode 01",
                    url = "magnet:?xt=urn:btih:abc",
                    status = RssDownloadStatus.SUBMITTED,
                    createdAt = 91L,
                    updatedAt = 91L,
                ),
            ) as Result.Success).data

            val reopened = DesktopRepositories.fileBacked(storePath)
            val enabled = reopened.cloudDriveAutomation.listEnabledSubscriptions() as Result.Success
            val processed = reopened.cloudDriveAutomation.isItemProcessed(firstId, "item-1") as Result.Success

            assertEquals(listOf(firstId), enabled.data.map { it.id })
            assertEquals("Feed Updated", enabled.data.single().name)
            assertEquals(88L, enabled.data.single().lastCheckedAt)
            assertTrue(processed.data)
            assertTrue(taskId > 0L)

            reopened.cloudDriveAutomation.deleteSubscription(firstId)
            val afterDelete = reopened.cloudDriveAutomation.listEnabledSubscriptions() as Result.Success
            val processedAfterDelete = reopened.cloudDriveAutomation.isItemProcessed(firstId, "item-1") as Result.Success
            assertTrue(afterDelete.data.isEmpty())
            assertEquals(false, processedAfterDelete.data)
        } finally {
            deleteTempStore(storePath)
        }
    }

    @Test
    fun `desktop credentials are persisted and can be cleared`() = runBlocking {
        val storePath = tempStorePath()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            repositories.credentials.cloudDriveToken = "cloud-token"
            repositories.credentials.cloudDrivePassword = "cloud-password"
            repositories.credentials.bangumiAccessToken = "bangumi-token"
            repositories.credentials.otlpAccessToken = "otlp-token"

            val reopened = DesktopRepositories.fileBacked(storePath)
            assertEquals("cloud-token", reopened.credentials.cloudDriveToken)
            assertEquals("cloud-password", reopened.credentials.cloudDrivePassword)
            assertEquals("bangumi-token", reopened.credentials.bangumiAccessToken)
            assertEquals("otlp-token", reopened.credentials.otlpAccessToken)

            reopened.credentials.clearCloudDriveCredentials()
            reopened.credentials.clearBangumiToken()
            reopened.credentials.clearOtlpAccessToken()

            val cleared = DesktopRepositories.fileBacked(storePath)
            assertEquals(null, cleared.credentials.cloudDriveToken)
            assertEquals(null, cleared.credentials.cloudDrivePassword)
            assertEquals(null, cleared.credentials.bangumiAccessToken)
            assertEquals(null, cleared.credentials.otlpAccessToken)
        } finally {
            deleteTempStore(storePath)
        }
    }

    private fun tempStorePath() =
        Files.createTempDirectory("miruplay-repository").resolve("store.json")

    private fun deleteTempStore(storePath: Path) {
        storePath.parent.toFile().deleteRecursively()
    }
}

private class FakeBangumiCollectionService(
    private val episodeCollections: List<BangumiEpisodeCollection>,
    private val subjectCollection: BangumiSubjectCollection? = null,
) : BangumiCollectionService {
    override val hasToken: Boolean = true
    val pushedEpisodeIds = mutableListOf<Int>()
    val pushedEpisodeTypes = mutableListOf<BangumiEpisodeCollectionType>()
    val subjectCollectionTypes = mutableListOf<BangumiSubjectCollectionType>()
    val singleEpisodeUpdates = mutableListOf<Int>()

    override suspend fun getCurrentUser(): Result<BangumiUser> =
        Result.success(BangumiUser(id = 1, username = "tester", nickname = "Tester"))

    override suspend fun getSubjectCollection(subjectId: Int): Result<BangumiSubjectCollection?> =
        Result.success(subjectCollection)

    override suspend fun upsertSubjectCollection(
        subjectId: Int,
        type: BangumiSubjectCollectionType
    ): Result<Unit> {
        subjectCollectionTypes += type
        return Result.success(Unit)
    }

    override suspend fun getEpisodeCollections(subjectId: Int): Result<List<BangumiEpisodeCollection>> =
        Result.success(episodeCollections)

    override suspend fun updateEpisodeCollections(
        subjectId: Int,
        episodeIds: List<Int>,
        type: BangumiEpisodeCollectionType
    ): Result<Unit> {
        pushedEpisodeIds += episodeIds
        pushedEpisodeTypes += type
        return Result.success(Unit)
    }

    override suspend fun updateEpisodeCollection(episodeId: Int, type: BangumiEpisodeCollectionType): Result<Unit> {
        singleEpisodeUpdates += episodeId
        return Result.success(Unit)
    }
}
