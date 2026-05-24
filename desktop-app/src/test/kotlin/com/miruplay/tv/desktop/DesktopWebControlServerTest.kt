package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.desktop.DesktopRepositories
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
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

    private fun request(url: String): HttpResult {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        val code = connection.responseCode
        val stream = if (code >= 400) connection.errorStream else connection.inputStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        return HttpResult(
            code = code,
            body = body,
            headers = connection.headerFields.filterKeys { it != null },
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
}
