package com.miruplay.tv.desktop

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.clouddrive.CloudDriveLoginResult
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.desktop.DesktopRepositories
import com.miruplay.tv.sync.rss.CloudDriveLibraryOrganizer
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.RssFeedItem
import com.miruplay.tv.sync.rss.RssFeedReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files

class DesktopWebControlServerTest {
    @Test
    fun `desktop web control serves static assets and requires token for api`() {
        val storePath = Files.createTempDirectory("miruplay-web-control-store").resolve("store.json")
        val mediaRoot = Files.createTempDirectory("miruplay-web-control-media")
        val port = freePort()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            repositories.webControlAccess.webControlEnabled = true
            val token = repositories.webControlAccess.accessToken
            val service = DesktopWebControlService(repositories, deviceName = "Windows Test")
            val server = DesktopWebControlServer(
                webControlService = service,
                webControlAccess = repositories.webControlAccess,
                port = port,
            )
            server.startIfNeeded()
            try {
                val unauthorized = request("http://127.0.0.1:$port/api/info")
                assertEquals(401, unauthorized.code)

                val info = request("http://127.0.0.1:$port/api/info?token=$token")
                assertEquals(200, info.code)
                assertTrue(info.body.contains("\"deviceName\":\"Windows Test\""))

                val index = request("http://127.0.0.1:$port/?token=$token")
                assertEquals(200, index.code)
                assertTrue(index.body.contains("MiruPlay Web Control"))
                assertNotNull(index.header("Set-Cookie"))
            } finally {
                server.stopIfRunning()
            }
        } finally {
            mediaRoot.toFile().deleteRecursively()
            storePath.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `desktop web control exposes repository library and sanitized sources`() = runBlocking {
        val storePath = Files.createTempDirectory("miruplay-web-control-store").resolve("store.json")
        val mediaRoot = Files.createTempDirectory("miruplay-web-control-media")
        val port = freePort()
        try {
            val showDir = Files.createDirectory(mediaRoot.resolve("Frieren"))
            val firstEpisode = showDir.resolve("Frieren - 01.mkv")
            Files.writeString(firstEpisode, "video")
            val repositories = DesktopRepositories.fileBacked(storePath)
            val sourceId = (repositories.mediaSources.addSource(
                MediaSourceInfoConventions.local(
                    name = "Local Anime",
                    rootPath = mediaRoot.toString(),
                    isConnected = true,
                ).copy(
                    connectionInfo = mapOf(
                        "path" to mediaRoot.toString(),
                        "password" to "secret",
                    ),
                )
            ) as Result.Success).data
            repositories.index.rebuildIndex(
                sourceId = sourceId,
                entries = listOf(
                    MediaIndexEntry(
                        sourceId = sourceId,
                        path = firstEpisode.toString(),
                        animeName = "Frieren",
                        episodeNumber = 1,
                    ),
                ),
            )
            repositories.metadata.cacheMetadata(
                Anime(
                    id = "Frieren",
                    title = "Sousou no Frieren",
                    titleCn = "葬送的芙莉莲",
                ),
            )
            repositories.metadata.cacheEpisodes(
                animeId = "Frieren",
                episodes = listOf(
                    Episode(
                        id = "$sourceId:${firstEpisode}",
                        animeId = "Frieren",
                        episodeNumber = 1,
                        filePath = firstEpisode.toString(),
                        fileName = firstEpisode.fileName.toString(),
                    ),
                ),
            )
            repositories.progress.saveProgress(
                episodeId = "$sourceId:${firstEpisode}",
                positionMs = 12_000L,
                lastWatched = 24L,
                incrementPlayCount = true,
            )
            repositories.webControlAccess.webControlEnabled = true
            val token = repositories.webControlAccess.accessToken
            val service = DesktopWebControlService(repositories, deviceName = "Windows Test")
            val server = DesktopWebControlServer(
                webControlService = service,
                webControlAccess = repositories.webControlAccess,
                port = port,
            )
            server.startIfNeeded()
            try {
                val sources = request("http://127.0.0.1:$port/api/sources?token=$token")
                assertEquals(200, sources.code)
                assertTrue(sources.body.contains("Local Anime"))
                assertTrue(!sources.body.contains("secret"))

                val library = request("http://127.0.0.1:$port/api/library?token=$token")
                assertEquals(200, library.code)
                assertTrue(library.body.contains("Sousou no Frieren"))
                assertTrue(library.body.contains("12000"))

                val detail = request("http://127.0.0.1:$port/api/anime/Frieren?token=$token")
                assertEquals(200, detail.code)
                assertTrue(detail.body.contains("Frieren - 01.mkv"))
            } finally {
                server.stopIfRunning()
            }
        } finally {
            mediaRoot.toFile().deleteRecursively()
            storePath.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `desktop web control scans sources through shared desktop scanner`() = runBlocking {
        val storePath = Files.createTempDirectory("miruplay-web-control-store").resolve("store.json")
        val mediaRoot = Files.createTempDirectory("miruplay-web-control-media")
        val secondMediaRoot = Files.createTempDirectory("miruplay-web-control-media-second")
        val port = freePort()
        try {
            val firstShowDir = Files.createDirectory(mediaRoot.resolve("Bocchi"))
            Files.writeString(firstShowDir.resolve("Bocchi - 01.mkv"), "video")
            val secondShowDir = Files.createDirectory(secondMediaRoot.resolve("K-On"))
            Files.writeString(secondShowDir.resolve("K-On - 01.mkv"), "video")

            val repositories = DesktopRepositories.fileBacked(storePath)
            val firstSourceId = (repositories.mediaSources.addSource(
                MediaSourceInfoConventions.local(
                    name = "Local Anime",
                    rootPath = mediaRoot.toString(),
                    isConnected = true,
                )
            ) as Result.Success).data
            val secondSourceId = (repositories.mediaSources.addSource(
                MediaSourceInfoConventions.local(
                    name = "Second Anime",
                    rootPath = secondMediaRoot.toString(),
                    isConnected = true,
                )
            ) as Result.Success).data
            repositories.webControlAccess.webControlEnabled = true
            val token = repositories.webControlAccess.accessToken
            val service = DesktopWebControlService(repositories, deviceName = "Windows Test")
            val server = DesktopWebControlServer(
                webControlService = service,
                webControlAccess = repositories.webControlAccess,
                port = port,
            )
            server.startIfNeeded()
            try {
                val initialLibrary = request("http://127.0.0.1:$port/api/library?token=$token")
                assertEquals(200, initialLibrary.code)
                assertTrue(!initialLibrary.body.contains("Bocchi"))

                val scanOne = request(
                    url = "http://127.0.0.1:$port/api/sources/$firstSourceId/scan?token=$token",
                    method = "POST",
                )
                assertEquals(200, scanOne.code)
                assertTrue(scanOne.body.contains("\"sourceId\":$firstSourceId"))
                assertTrue(scanOne.body.contains("\"animeName\":\"Local Anime\""))
                assertTrue(scanOne.body.contains("\"episodesFound\":1"))
                assertTrue(scanOne.body.contains("\"newEpisodes\":1"))

                val scannedLibrary = request("http://127.0.0.1:$port/api/library?token=$token")
                assertEquals(200, scannedLibrary.code)
                assertTrue(scannedLibrary.body.contains("Bocchi"))

                val scanAll = request(
                    url = "http://127.0.0.1:$port/api/sources/scan-all?token=$token",
                    method = "POST",
                )
                assertEquals(200, scanAll.code)
                assertTrue(scanAll.body.contains("\"sourceId\":$firstSourceId"))
                assertTrue(scanAll.body.contains("\"sourceId\":$secondSourceId"))

                val fullLibrary = request("http://127.0.0.1:$port/api/library?token=$token")
                assertEquals(200, fullLibrary.code)
                assertTrue(fullLibrary.body.contains("Bocchi"))
                assertTrue(fullLibrary.body.contains("K-On"))
            } finally {
                server.stopIfRunning()
            }
        } finally {
            secondMediaRoot.toFile().deleteRecursively()
            mediaRoot.toFile().deleteRecursively()
            storePath.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `desktop web control uses shared CloudDrive engine and directory browser`() = runBlocking {
        val storePath = Files.createTempDirectory("miruplay-web-control-store").resolve("store.json")
        val port = freePort()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            val cloudDrive = FakeCloudDriveClient(
                rootDir = "/CloudRoot",
                files = listOf(
                    CloudDriveFileInfo("Episode 01.mkv", "/CloudRoot/Anime/Episode 01.mkv", isDirectory = false),
                    CloudDriveFileInfo(".cache", "/CloudRoot/Anime/.cache", isDirectory = true),
                    CloudDriveFileInfo("Season 02", "/CloudRoot/Anime/Season 02", isDirectory = true),
                    CloudDriveFileInfo("season 01", "/CloudRoot/Anime/season 01", isDirectory = true),
                ),
            )
            val cloudRssEngine = DesktopCloudDriveRssAutomationEngine(
                repository = repositories.cloudDriveAutomation,
                credentials = repositories.credentials,
                cloudDriveClient = cloudDrive,
                feedFetcher = FakeFeedReader(),
                organizer = CloudDriveLibraryOrganizer(cloudDrive),
            )
            repositories.cloudDriveAutomation.saveConfig(
                CloudDriveAutomationConfig(
                    endpointUrl = "http://cloud.test",
                    username = "miru",
                    inboxPath = "/Downloads",
                    libraryPath = "/Library",
                )
            )
            repositories.cloudDriveAutomation.saveSubscription(
                RssSubscriptionInfo(
                    name = "Anime",
                    url = "https://example.test/rss.xml",
                    enabled = true,
                )
            )
            repositories.webControlAccess.webControlEnabled = true
            val token = repositories.webControlAccess.accessToken
            val service = DesktopWebControlService(
                repositories = repositories,
                cloudDriveClient = cloudDrive,
                cloudRssEngine = cloudRssEngine,
                deviceName = "Windows Test",
            )
            val server = DesktopWebControlServer(
                webControlService = service,
                webControlAccess = repositories.webControlAccess,
                port = port,
            )
            server.startIfNeeded()
            try {
                val login = request(
                    url = "http://127.0.0.1:$port/api/cloud-drive/login?token=$token",
                    method = "POST",
                    body = """{"endpointUrl":"http://cloud.test","username":"miru","password":"secret"}""",
                )
                assertEquals(200, login.code)
                assertTrue(login.body.contains("\"tokenConfigured\":true"))
                assertEquals("login-token", repositories.credentials.cloudDriveToken)
                assertEquals("secret", repositories.credentials.cloudDrivePassword)

                val verified = request(
                    url = "http://127.0.0.1:$port/api/cloud-drive/token?token=$token",
                    method = "POST",
                    body = """{"endpointUrl":"http://cloud.test","token":"api-token"}""",
                )
                assertEquals(200, verified.code)
                assertTrue(verified.body.contains("\"rootDir\":\"/CloudRoot\""))
                assertTrue(verified.body.contains("\"friendlyName\":\"desktop-token\""))
                assertTrue(verified.body.contains("\"allowList\":true"))
                assertEquals("api-token", repositories.credentials.cloudDriveToken)

                val directories = request(
                    "http://127.0.0.1:$port/api/cloud-drive/directories?token=$token&endpointUrl=http%3A%2F%2Fcloud.test&path=%2FOutside%2FAnime",
                )
                assertEquals(200, directories.code)
                assertTrue(directories.body.contains("\"path\":\"/CloudRoot\""))
                assertTrue(!directories.body.contains("Episode 01.mkv"))

                val animeDirectories = request(
                    "http://127.0.0.1:$port/api/cloud-drive/directories?token=$token&endpointUrl=http%3A%2F%2Fcloud.test&path=%2FCloudRoot%2FAnime",
                )
                assertEquals(200, animeDirectories.code)
                assertTrue(animeDirectories.body.contains("season 01"))
                assertTrue(animeDirectories.body.contains("Season 02"))
                assertTrue(!animeDirectories.body.contains(".cache"))

                val run = request(
                    url = "http://127.0.0.1:$port/api/cloud-drive/run?token=$token",
                    method = "POST",
                )
                assertEquals(200, run.code)
                assertTrue(run.body.contains("\"submitted\":1"))
                assertTrue(run.body.contains("\"organized\":0"))
                assertEquals(listOf("magnet:?xt=urn:btih:abc"), cloudDrive.offlineUrls)
                assertEquals("/Downloads", cloudDrive.offlineTargetFolder)
            } finally {
                server.stopIfRunning()
            }
        } finally {
            storePath.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `desktop web control stops serving api when disabled`() {
        val storePath = Files.createTempDirectory("miruplay-web-control-store").resolve("store.json")
        val port = freePort()
        try {
            val repositories = DesktopRepositories.fileBacked(storePath)
            repositories.webControlAccess.webControlEnabled = true
            val token = repositories.webControlAccess.accessToken
            val service = DesktopWebControlService(repositories, deviceName = "Windows Test")
            val server = DesktopWebControlServer(
                webControlService = service,
                webControlAccess = repositories.webControlAccess,
                port = port,
            )
            server.startIfNeeded()
            try {
                repositories.webControlAccess.webControlEnabled = false
                val disabled = request("http://127.0.0.1:$port/api/info?token=$token")
                assertEquals(403, disabled.code)
                assertTrue(disabled.body.contains("WebUI"))
            } finally {
                server.stopIfRunning()
            }
        } finally {
            storePath.parent.toFile().deleteRecursively()
        }
    }

    private fun freePort(): Int =
        ServerSocket(0).use { it.localPort }

    private fun request(url: String, method: String = "GET", body: String? = null): HttpResult {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        connection.requestMethod = method
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
        }
        val code = connection.responseCode
        val stream = if (code >= 400) connection.errorStream else connection.inputStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        return HttpResult(
            code = code,
            body = body,
            headers = connection.headerFields.mapKeys { it.key.orEmpty() },
        )
    }

    private data class HttpResult(
        val code: Int,
        val body: String,
        val headers: Map<String, List<String>>,
    ) {
        fun header(name: String): String? =
            headers.entries
                .firstOrNull { it.key.equals(name, ignoreCase = true) }
                ?.value
                ?.firstOrNull()
    }

    private class FakeFeedReader : RssFeedReader {
        override fun configureProxy(enabled: Boolean, host: String, port: Int) = Unit

        override suspend fun fetch(url: String): Result<List<RssFeedItem>> =
            Result.success(
                listOf(
                    RssFeedItem(
                        title = "Episode 01",
                        guid = "guid-1",
                        link = "magnet:?xt=urn:btih:abc",
                        enclosureUrl = null,
                    )
                )
            )
    }

    private class FakeCloudDriveClient(
        private val rootDir: String = "/",
        private val files: List<CloudDriveFileInfo> = emptyList(),
    ) : CloudDriveClient {
        val offlineUrls = mutableListOf<String>()
        var offlineTargetFolder: String = ""

        override suspend fun login(endpointUrl: String, username: String, password: String): Result<CloudDriveLoginResult> =
            Result.success(CloudDriveLoginResult("login-token"))

        override suspend fun getApiTokenInfo(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> =
            Result.success(
                CloudDriveTokenInfo(
                    rootDir = rootDir,
                    friendlyName = "desktop-token",
                    allowList = true,
                    allowCreateFolder = true,
                    allowCreateFile = true,
                    allowWrite = true,
                    allowMove = true,
                    allowAddOfflineDownload = true,
                )
            )

        override suspend fun addOfflineFiles(
            endpoint: CloudDriveEndpoint,
            urls: List<String>,
            targetFolder: String,
        ): Result<Unit> {
            offlineUrls += urls
            offlineTargetFolder = targetFolder
            return Result.success(Unit)
        }

        override suspend fun uploadFile(
            endpoint: CloudDriveEndpoint,
            localFile: File,
            parentPath: String,
            remoteFileName: String,
        ): Result<String> =
            Result.success("$parentPath/$remoteFileName")

        override suspend fun listFolder(
            endpoint: CloudDriveEndpoint,
            path: String,
            forceRefresh: Boolean,
        ): Result<List<CloudDriveFileInfo>> =
            Result.success(files.filter { it.path.parentCloudPath() == path.trimEnd('/') })

        override suspend fun createFolder(endpoint: CloudDriveEndpoint, parentPath: String, folderName: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun moveFiles(endpoint: CloudDriveEndpoint, paths: List<String>, destinationPath: String): Result<Unit> =
            Result.success(Unit)

        private fun String.parentCloudPath(): String =
            trimEnd('/').substringBeforeLast('/', "").ifBlank { "/" }
    }
}
