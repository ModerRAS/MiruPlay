package com.miruplay.tv.webcontrol

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webControlService: WebControlService
) : NanoHTTPD(DEFAULT_PORT) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var running = false

    fun startIfNeeded() {
        if (running) return
        start(SOCKET_READ_TIMEOUT, false)
        running = true
    }

    fun stopIfRunning() {
        if (!running) return
        stop()
        running = false
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            return addCommonHeaders(newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, ""))
        }

        return try {
            if (session.uri.startsWith("/api/")) {
                serveApi(session)
            } else {
                serveStatic(session)
            }
        } catch (e: IllegalArgumentException) {
            errorResponse(Response.Status.BAD_REQUEST, e.message ?: "请求参数不正确")
        } catch (e: Exception) {
            errorResponse(Response.Status.INTERNAL_ERROR, e.message ?: "服务器内部错误")
        }
    }

    private fun serveApi(session: IHTTPSession): Response = runBlocking {
        val route = session.uri.trimEnd('/').ifBlank { "/" }
        val segments = route.split('/').filter { it.isNotBlank() }

        when {
            session.method == Method.GET && route == "/api/info" -> {
                jsonResponse(ServerInfoDto.serializer(), webControlService.getServerInfo(DEFAULT_PORT))
            }
            session.method == Method.GET && route == "/api/sources" -> {
                jsonResponse(ListSerializer(com.miruplay.tv.model.MediaSourceInfo.serializer()), webControlService.listSources())
            }
            session.method == Method.GET && route == "/api/local-directories" -> {
                val path = session.parameters["path"]?.firstOrNull().orEmpty()
                jsonResponse(LocalDirectoryDto.serializer(), webControlService.browseLocalDirectories(path))
            }
            session.method == Method.POST && route == "/api/sources" -> {
                val request = parseBody(session, SourceRequest.serializer())
                jsonResponse(com.miruplay.tv.model.MediaSourceInfo.serializer(), webControlService.addSource(request))
            }
            session.method == Method.PUT && segments.size == 3 && segments[0] == "api" && segments[1] == "sources" -> {
                val sourceId = segments[2].toLongOrNull() ?: throw IllegalArgumentException("媒体源 ID 不正确")
                val request = parseBody(session, SourceRequest.serializer())
                jsonResponse(com.miruplay.tv.model.MediaSourceInfo.serializer(), webControlService.updateSource(sourceId, request))
            }
            session.method == Method.DELETE && segments.size == 3 && segments[0] == "api" && segments[1] == "sources" -> {
                val sourceId = segments[2].toLongOrNull() ?: throw IllegalArgumentException("媒体源 ID 不正确")
                webControlService.removeSource(sourceId)
                jsonResponse(Unit.serializer(), Unit)
            }
            session.method == Method.POST && route == "/api/sources/test" -> {
                val request = parseBody(session, SourceTestRequest.serializer())
                jsonResponse(SourceTestResponse.serializer(), webControlService.testSource(request))
            }
            session.method == Method.POST && segments.size == 4 && segments[0] == "api" && segments[1] == "sources" && segments[3] == "scan" -> {
                val sourceId = segments[2].toLongOrNull() ?: throw IllegalArgumentException("媒体源 ID 不正确")
                jsonResponse(SourceScanResponse.serializer(), webControlService.scanSource(sourceId))
            }
            session.method == Method.POST && route == "/api/sources/scan-all" -> {
                jsonResponse(ListSerializer(SourceScanResponse.serializer()), webControlService.scanAllSources())
            }
            session.method == Method.GET && route == "/api/library" -> {
                val query = session.parameters["query"]?.firstOrNull().orEmpty()
                jsonResponse(LibraryDto.serializer(), webControlService.searchLibrary(query))
            }
            session.method == Method.GET && segments.size == 3 && segments[0] == "api" && segments[1] == "anime" -> {
                val animeId = decodeSegment(segments[2])
                jsonResponse(AnimeDetailDto.serializer(), webControlService.getAnimeDetail(animeId))
            }
            session.method == Method.POST && route == "/api/playback/play" -> {
                val request = parseBody(session, PlayEpisodeRequest.serializer())
                jsonResponse(PlaybackStatusDto.serializer(), webControlService.playEpisode(request))
            }
            session.method == Method.POST && route == "/api/playback/command" -> {
                val request = parseBody(session, PlaybackCommandRequest.serializer())
                jsonResponse(PlaybackStatusDto.serializer(), webControlService.playbackCommand(request))
            }
            session.method == Method.GET && route == "/api/playback/status" -> {
                jsonResponse(PlaybackStatusDto.serializer(), webControlService.playbackStatus())
            }
            else -> errorResponse(Response.Status.NOT_FOUND, "接口不存在")
        }
    }

    private fun serveStatic(session: IHTTPSession): Response {
        val rawPath = session.uri.substringBefore('?')
        val path = when (rawPath) {
            "/", "" -> "web/index.html"
            else -> "web/" + rawPath.trimStart('/')
        }
        val safePath = path.replace("\\", "/")
        if (!safePath.startsWith("web/") || safePath.contains("../")) {
            return errorResponse(Response.Status.FORBIDDEN, "禁止访问")
        }

        return try {
            val bytes = context.assets.open(safePath).use { it.readBytes() }
            val response = newFixedLengthResponse(
                Response.Status.OK,
                mimeTypeFor(safePath),
                bytes.inputStream(),
                bytes.size.toLong()
            )
            addCommonHeaders(response)
        } catch (_: FileNotFoundException) {
            val bytes = context.assets.open("web/index.html").use { it.readBytes() }
            addCommonHeaders(
                newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html; charset=utf-8",
                    bytes.inputStream(),
                    bytes.size.toLong()
                )
            )
        }
    }

    private fun <T> jsonResponse(serializer: KSerializer<T>, data: T): Response {
        val body = json.encodeToString(
            ApiEnvelope.serializer(serializer),
            ApiEnvelope(ok = true, data = data)
        )
        return addCommonHeaders(newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body))
    }

    private fun errorResponse(status: Response.Status, message: String): Response {
        val body = json.encodeToString(
            ApiEnvelope.serializer(Unit.serializer()),
            ApiEnvelope<Unit>(ok = false, error = message)
        )
        return addCommonHeaders(newFixedLengthResponse(status, "application/json; charset=utf-8", body))
    }

    private fun <T> parseBody(session: IHTTPSession, serializer: KSerializer<T>): T {
        val files = mutableMapOf<String, String>()
        try {
            session.parseBody(files)
        } catch (e: Exception) {
            throw IllegalArgumentException("请求体读取失败: ${e.message}")
        }

        val body = files["postData"].orEmpty()
        if (body.isBlank()) {
            throw IllegalArgumentException("请求体不能为空")
        }

        return try {
            json.decodeFromString(serializer, body)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("JSON 格式不正确: ${e.message}")
        }
    }

    private fun addCommonHeaders(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js" -> "application/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        else -> "application/octet-stream"
    }

    private fun decodeSegment(segment: String): String =
        URLDecoder.decode(segment, Charsets.UTF_8.name())

    companion object {
        const val DEFAULT_PORT = 9978
    }
}
