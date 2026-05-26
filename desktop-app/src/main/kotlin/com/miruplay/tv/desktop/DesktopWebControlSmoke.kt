package com.miruplay.tv.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.desktop.DesktopRepositories
import com.miruplay.tv.webcontrol.PlaybackStatusDto
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal const val DESKTOP_WEB_CONTROL_SMOKE_ARG = "--miruplay-desktop-webui-smoke"
internal const val DESKTOP_WEB_CONTROL_SMOKE_REPORT_ARG_PREFIX = "--miruplay-desktop-webui-smoke-report="

internal fun shouldRunDesktopWebControlSmoke(args: Array<String>): Boolean =
    args.any { it == DESKTOP_WEB_CONTROL_SMOKE_ARG }

internal fun desktopWebControlSmokeReportPath(args: Array<String>): Path? =
    args.firstOrNull { it.startsWith(DESKTOP_WEB_CONTROL_SMOKE_REPORT_ARG_PREFIX) }
        ?.removePrefix(DESKTOP_WEB_CONTROL_SMOKE_REPORT_ARG_PREFIX)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(Paths::get)

internal fun runDesktopWebControlSmoke(args: Array<String>): Boolean {
    if (!shouldRunDesktopWebControlSmoke(args)) return false

    val evidence = DesktopWebControlSmokeEvidence()
    val result = runCatching {
        runBlocking {
            executeDesktopWebControlSmoke(evidence)
        }
    }
    val report = evidence.toReport(
        status = if (result.isSuccess) "passed" else "failed",
        error = result.exceptionOrNull()?.message,
    ).toJson()
    desktopWebControlSmokeReportPath(args)?.let { reportPath ->
        reportPath.parent?.let(Files::createDirectories)
        Files.writeString(reportPath, report)
    }
    println(report)
    result.getOrThrow()
    return true
}

private suspend fun executeDesktopWebControlSmoke(evidence: DesktopWebControlSmokeEvidence) {
    val storeDirectory = Files.createTempDirectory("miruplay-webui-smoke-store")
    val storePath = storeDirectory.resolve("desktop-store.json")
    val mediaRoot = Files.createTempDirectory("miruplay-webui-smoke-media")
    val port = freeDesktopWebControlSmokePort()
    evidence.port = port

    try {
        val showDirectory = Files.createDirectory(mediaRoot.resolve("Frieren"))
        val firstEpisode = showDirectory.resolve("Frieren - 01.mkv")
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
                    "password" to WEB_CONTROL_SMOKE_SECRET,
                ),
            ),
        ) as Result.Success).data
        evidence.sourceId = sourceId
        val episodeId = "$sourceId:$firstEpisode"
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
                titleCn = "Frieren",
            ),
        )
        repositories.metadata.cacheEpisodes(
            animeId = "Frieren",
            episodes = listOf(
                Episode(
                    id = episodeId,
                    animeId = "Frieren",
                    episodeNumber = 1,
                    filePath = firstEpisode.toString(),
                    fileName = firstEpisode.fileName.toString(),
                    duration = 120_000L,
                ),
            ),
        )
        repositories.progress.saveProgress(
            episodeId = episodeId,
            positionMs = 12_000L,
            lastWatched = 24L,
            incrementPlayCount = true,
        )
        repositories.webControlAccess.webControlEnabled = true
        val token = repositories.webControlAccess.accessToken
        val played = mutableListOf<String>()
        val commands = mutableListOf<String>()
        val service = DesktopWebControlService(
            repositories = repositories,
            deviceName = "Windows WebUI Smoke",
            playEpisodeHandler = { request, episode ->
                played += request.episodeId
                PlaybackStatusDto(
                    state = "Playing",
                    uri = episode.filePath,
                    mediaSourceId = episode.id.substringBefore(':'),
                    positionMs = request.startPositionMs ?: 0L,
                    durationMs = episode.duration,
                    isPlaying = true,
                )
            },
            playbackCommandHandler = { command ->
                commands += command.command
                PlaybackStatusDto(
                    state = "Playing",
                    positionMs = command.positionMs ?: 0L,
                    durationMs = 120_000L,
                    isPlaying = true,
                )
            },
        )
        val server = DesktopWebControlServer(
            webControlService = service,
            webControlAccess = repositories.webControlAccess,
            port = port,
        )
        server.startIfNeeded()
        try {
            val baseUrl = "http://127.0.0.1:$port"
            evidence.check("api_requires_token") {
                val unauthorized = webControlSmokeRequest("$baseUrl/api/info")
                unauthorized.code == 401 && unauthorized.body.contains("WebUI")
            }
            val index = webControlSmokeRequest("$baseUrl/?token=${token.urlEncodedForSmoke()}")
            evidence.check("static_shell_served") {
                index.code == 200 && index.body.contains("MiruPlay Web Control") && index.header("Set-Cookie") != null
            }
            evidence.staticAssetBytes = index.body.toByteArray(Charsets.UTF_8).size
            val cookie = index.header("Set-Cookie")?.substringBefore(';').orEmpty()
            evidence.check("cookie_authorizes_api") {
                val info = webControlSmokeRequest(
                    url = "$baseUrl/api/info",
                    headers = mapOf("Cookie" to cookie),
                )
                info.code == 200 && info.body.contains("\"deviceName\":\"Windows WebUI Smoke\"")
            }
            evidence.check("sources_redact_secrets") {
                val sources = webControlSmokeRequest("$baseUrl/api/sources?token=${token.urlEncodedForSmoke()}")
                sources.code == 200 &&
                    sources.body.contains("Local Anime") &&
                    !sources.body.contains(WEB_CONTROL_SMOKE_SECRET)
            }
            evidence.check("library_exposes_progress") {
                val library = webControlSmokeRequest("$baseUrl/api/library?token=${token.urlEncodedForSmoke()}")
                library.code == 200 && library.body.contains("Sousou no Frieren") && library.body.contains("12000")
            }
            evidence.check("detail_exposes_episode") {
                val detail = webControlSmokeRequest("$baseUrl/api/anime/Frieren?token=${token.urlEncodedForSmoke()}")
                detail.code == 200 && detail.body.contains("Frieren - 01.mkv")
            }
            evidence.check("cloud_drive_summary_served") {
                val cloud = webControlSmokeRequest("$baseUrl/api/cloud-drive?token=${token.urlEncodedForSmoke()}")
                cloud.code == 200 && cloud.body.contains("\"ok\":true")
            }
            evidence.check("playback_play_api") {
                val play = webControlSmokeRequest(
                    url = "$baseUrl/api/playback/play?token=${token.urlEncodedForSmoke()}",
                    method = "POST",
                    body = """{"episodeId":"${episodeId.jsonEscapedForSmoke()}","startPositionMs":12000}""",
                )
                play.code == 200 &&
                    play.body.contains("\"state\":\"Playing\"") &&
                    play.body.contains("\"positionMs\":12000") &&
                    played.singleOrNull() == episodeId
            }
            evidence.check("playback_command_api") {
                val command = webControlSmokeRequest(
                    url = "$baseUrl/api/playback/command?token=${token.urlEncodedForSmoke()}",
                    method = "POST",
                    body = """{"command":"seek","positionMs":45000}""",
                )
                command.code == 200 &&
                    command.body.contains("\"positionMs\":45000") &&
                    commands.singleOrNull() == "seek"
            }
        } finally {
            server.stopIfRunning()
        }
    } finally {
        mediaRoot.toFile().deleteRecursively()
        storeDirectory.toFile().deleteRecursively()
    }
}

private fun DesktopWebControlSmokeEvidence.check(name: String, predicate: () -> Boolean) {
    if (predicate()) {
        checks += DesktopWebControlSmokeCheck(name, "passed")
    } else {
        checks += DesktopWebControlSmokeCheck(name, "failed")
        throw IllegalStateException("Desktop WebUI smoke check failed: $name")
    }
}

private fun freeDesktopWebControlSmokePort(): Int =
    ServerSocket(0).use { socket -> socket.localPort }

private fun webControlSmokeRequest(
    url: String,
    method: String = "GET",
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
): DesktopWebControlSmokeHttpResult {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        connection.requestMethod = method
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
        }
        val code = connection.responseCode
        val stream = if (code >= 400) connection.errorStream else connection.inputStream
        DesktopWebControlSmokeHttpResult(
            code = code,
            body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(),
            headers = connection.headerFields.mapKeys { it.key.orEmpty() },
        )
    } finally {
        connection.disconnect()
    }
}

private data class DesktopWebControlSmokeHttpResult(
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

private data class DesktopWebControlSmokeCheck(
    val name: String,
    val status: String,
) {
    fun toJson(): String =
        """{"name":${name.smokeJsonValue()},"status":${status.smokeJsonValue()}}"""
}

private data class DesktopWebControlSmokeReport(
    val status: String,
    val port: Int,
    val sourceId: Long,
    val staticAssetBytes: Int,
    val checks: List<DesktopWebControlSmokeCheck>,
    val error: String?,
) {
    fun toJson(): String =
        """
        {
          "schemaVersion": 1,
          "name": "desktop-web-control-smoke",
          "status": ${status.smokeJsonValue()},
          "port": $port,
          "sourceId": $sourceId,
          "staticAssetBytes": $staticAssetBytes,
          "checks": [${checks.joinToString(",") { it.toJson() }}],
          "error": ${error?.smokeJsonValue() ?: "null"}
        }
        """.trimIndent()
}

private class DesktopWebControlSmokeEvidence {
    var port: Int = 0
    var sourceId: Long = 0
    var staticAssetBytes: Int = 0
    val checks = mutableListOf<DesktopWebControlSmokeCheck>()

    fun toReport(status: String, error: String?): DesktopWebControlSmokeReport =
        DesktopWebControlSmokeReport(
            status = status,
            port = port,
            sourceId = sourceId,
            staticAssetBytes = staticAssetBytes,
            checks = checks.toList(),
            error = error,
        )
}

private fun String.urlEncodedForSmoke(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")

private fun String.jsonEscapedForSmoke(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

private fun String.smokeJsonValue(): String =
    buildString {
        append('"')
        this@smokeJsonValue.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

private const val WEB_CONTROL_SMOKE_SECRET = "webui-smoke-password"
