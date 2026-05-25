package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.WebControlConfig
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.WebControlAccessManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

open class NanoHttpWebControlServer(
    private val webControlPort: Int = DEFAULT_PORT,
    private val webControlService: WebControlEndpointService,
    private val webControlAccess: WebControlAccessManager,
    private val staticAssets: WebControlStaticAssets,
) : NanoHTTPD(webControlPort) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var running = false

    open fun startIfNeeded() {
        if (!webControlAccess.webControlEnabled) return
        if (running) return
        webControlAccess.accessToken
        start(SOCKET_READ_TIMEOUT, false)
        running = true
    }

    open fun stopIfRunning() {
        if (!running) return
        stop()
        running = false
    }

    protected fun isRunning(): Boolean = running

    override fun serve(session: IHTTPSession): Response {
        if (!webControlAccess.webControlEnabled) {
            return serviceDisabledResponse()
        }

        if (session.method == Method.OPTIONS) {
            return addCommonHeaders(newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, ""))
        }

        return try {
            if (session.uri.startsWith("/api/")) {
                if (!isAuthorized(session)) {
                    return unauthorizedResponse()
                }
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
                val activePort = listeningPort.takeIf { it > 0 } ?: webControlPort
                jsonResponse(ServerInfoDto.serializer(), webControlService.getServerInfo(activePort))
            }
            session.method == Method.GET && route == "/api/sources" -> {
                jsonResponse(ListSerializer(MediaSourceInfo.serializer()), webControlService.listSources())
            }
            session.method == Method.GET && route == "/api/local-directories" -> {
                val path = session.utf8QueryParameter("path")
                jsonResponse(LocalDirectoryDto.serializer(), webControlService.browseLocalDirectories(path))
            }
            session.method == Method.GET && route == "/api/cloud-drive/directories" -> {
                val endpointUrl = session.utf8QueryParameter("endpointUrl")
                val path = session.utf8QueryParameter("path")
                jsonResponse(CloudDriveDirectoryDto.serializer(), webControlService.browseCloudDriveDirectories(endpointUrl, path))
            }
            session.method == Method.POST && route == "/api/sources" -> {
                val request = parseBody(session, SourceRequest.serializer())
                jsonResponse(MediaSourceInfo.serializer(), webControlService.addSource(request))
            }
            session.method == Method.PUT && segments.size == 3 && segments[0] == "api" && segments[1] == "sources" -> {
                val sourceId = segments[2].toLongOrNull() ?: throw IllegalArgumentException("媒体源 ID 不正确")
                val request = parseBody(session, SourceRequest.serializer())
                jsonResponse(MediaSourceInfo.serializer(), webControlService.updateSource(sourceId, request))
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
            session.method == Method.GET && route == "/api/cloud-drive" -> {
                jsonResponse(CloudDriveAutomationDto.serializer(), webControlService.getCloudDriveAutomation())
            }
            session.method == Method.PUT && route == "/api/cloud-drive/config" -> {
                val request = parseBody(session, CloudDriveConfigRequest.serializer())
                jsonResponse(CloudDriveAutomationDto.serializer(), webControlService.saveCloudDriveConfig(request))
            }
            session.method == Method.POST && route == "/api/cloud-drive/login" -> {
                val request = parseBody(session, CloudDriveLoginRequest.serializer())
                jsonResponse(CloudDriveAutomationDto.serializer(), webControlService.loginCloudDrive(request))
            }
            session.method == Method.POST && route == "/api/cloud-drive/token" -> {
                val request = parseBody(session, CloudDriveTokenRequest.serializer())
                jsonResponse(CloudDriveTokenResponse.serializer(), webControlService.saveCloudDriveToken(request))
            }
            session.method == Method.POST && route == "/api/cloud-drive/run" -> {
                jsonResponse(CloudDriveRunResponse.serializer(), webControlService.runCloudDriveAutomationNow())
            }
            session.method == Method.POST && route == "/api/cloud-drive/rss" -> {
                val request = parseBody(session, RssSubscriptionRequest.serializer())
                jsonResponse(RssSubscriptionInfo.serializer(), webControlService.saveRssSubscription(request))
            }
            session.method == Method.PUT && segments.size == 4 && segments[0] == "api" && segments[1] == "cloud-drive" && segments[2] == "rss" -> {
                val rssId = segments[3].toLongOrNull() ?: throw IllegalArgumentException("RSS 订阅 ID 不正确")
                val request = parseBody(session, RssSubscriptionRequest.serializer())
                jsonResponse(RssSubscriptionInfo.serializer(), webControlService.updateRssSubscription(rssId, request))
            }
            session.method == Method.DELETE && segments.size == 4 && segments[0] == "api" && segments[1] == "cloud-drive" && segments[2] == "rss" -> {
                val rssId = segments[3].toLongOrNull() ?: throw IllegalArgumentException("RSS 订阅 ID 不正确")
                webControlService.deleteRssSubscription(rssId)
                jsonResponse(Unit.serializer(), Unit)
            }
            session.method == Method.GET && route == "/api/log-upload" -> {
                jsonResponse(LogUploadDto.serializer(), webControlService.getLogUpload())
            }
            session.method == Method.PUT && route == "/api/log-upload/config" -> {
                val request = parseBody(session, LogUploadConfigRequest.serializer())
                jsonResponse(LogUploadDto.serializer(), webControlService.saveLogUploadConfig(request))
            }
            session.method == Method.POST && route == "/api/log-upload/token" -> {
                val request = parseBody(session, LogUploadTokenRequest.serializer())
                jsonResponse(LogUploadDto.serializer(), webControlService.saveLogUploadToken(request))
            }
            session.method == Method.DELETE && route == "/api/log-upload/token" -> {
                jsonResponse(LogUploadDto.serializer(), webControlService.clearLogUploadToken())
            }
            session.method == Method.POST && route == "/api/log-upload/run" -> {
                jsonResponse(LogUploadDto.serializer(), webControlService.uploadPendingLogs())
            }
            session.method == Method.GET && route == "/api/metadata" -> {
                jsonResponse(MetadataSettingsDto.serializer(), webControlService.getMetadataSettings())
            }
            session.method == Method.POST && route == "/api/metadata/bangumi-token" -> {
                val request = parseBody(session, BangumiTokenRequest.serializer())
                jsonResponse(MetadataSettingsDto.serializer(), webControlService.saveBangumiToken(request))
            }
            session.method == Method.DELETE && route == "/api/metadata/bangumi-token" -> {
                jsonResponse(MetadataSettingsDto.serializer(), webControlService.clearBangumiToken())
            }
            session.method == Method.GET && route == "/api/library" -> {
                val query = session.utf8QueryParameter("query")
                jsonResponse(LibraryDto.serializer(), webControlService.searchLibrary(query))
            }
            session.method == Method.GET && segments.size == 3 && segments[0] == "api" && segments[1] == "anime" -> {
                val animeId = HttpRequestEncoding.decodeSegment(segments[2])
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

        val requestedBytes = staticAssets.read(safePath)
        val servedPath = if (requestedBytes != null) safePath else "web/index.html"
        val bytes = requestedBytes ?: staticAssets.read(servedPath)
            ?: return errorResponse(Response.Status.NOT_FOUND, "WebUI 静态资源不存在")
        val response = newFixedLengthResponse(
            Response.Status.OK,
            mimeTypeFor(servedPath),
            bytes.inputStream(),
            bytes.size.toLong()
        )
        addAuthCookieIfRequested(session, response)
        return addCommonHeaders(response)
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

    private fun serviceDisabledResponse(): Response =
        errorResponse(Response.Status.FORBIDDEN, "WebUI 未启用")

    private fun unauthorizedResponse(): Response =
        errorResponse(Response.Status.UNAUTHORIZED, "WebUI 访问令牌无效")

    private fun <T> parseBody(session: IHTTPSession, serializer: KSerializer<T>): T {
        val files = mutableMapOf<String, String>()
        try {
            session.parseBody(files)
        } catch (e: Exception) {
            throw IllegalArgumentException("请求体读取失败: ${e.message}")
        }

        val body: String = when {
            files["postData"]?.isNotBlank() == true -> files.getValue("postData")
            files.containsKey("content") -> {
                val path = files.getValue("content")
                val tempFile = File(path)
                if (tempFile.exists()) tempFile.readText(Charsets.UTF_8) else ""
            }
            else -> ""
        }

        if (body.isBlank()) {
            throw IllegalArgumentException("请求体不能为空")
        }

        for (candidate in HttpRequestEncoding.utf8BodyCandidates(body)) {
            try {
                return json.decodeFromString(serializer, candidate)
            } catch (_: SerializationException) {
                // Try the next decoding candidate below.
            }
        }
        throw IllegalArgumentException("JSON 格式不正确")
    }

    private fun addCommonHeaders(response: Response): Response {
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, X-MiruPlay-Token, Authorization")
        response.addHeader("Cache-Control", "no-store")
        response.addHeader("X-Content-Type-Options", "nosniff")
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

    private fun IHTTPSession.utf8QueryParameter(name: String): String =
        HttpRequestEncoding.queryParameter(queryParameterString, parameters, name)

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val expected = webControlAccess.accessToken
        return tokenCandidates(session).any { constantTimeEquals(it, expected) }
    }

    private fun tokenCandidates(session: IHTTPSession): List<String> = buildList {
        headerValue(session, "x-miruplay-token")?.let(::add)
        bearerToken(session)?.let(::add)
        session.parameters["token"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
        cookieValue(session, AUTH_COOKIE_NAME)?.let(::add)
    }

    private fun bearerToken(session: IHTTPSession): String? =
        headerValue(session, "authorization")
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun headerValue(session: IHTTPSession, name: String): String? =
        session.headers.entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun cookieValue(session: IHTTPSession, name: String): String? {
        val cookieHeader = headerValue(session, "cookie") ?: return null
        return cookieHeader
            .split(';')
            .map { it.trim() }
            .firstOrNull { it.substringBefore('=').trim() == name }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotBlank() }
    }

    private fun addAuthCookieIfRequested(session: IHTTPSession, response: Response) {
        val requestedToken = session.parameters["token"]?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
        if (constantTimeEquals(requestedToken, webControlAccess.accessToken)) {
            response.addHeader(
                "Set-Cookie",
                "$AUTH_COOKIE_NAME=$requestedToken; Path=/; SameSite=Strict"
            )
        }
    }

    private fun constantTimeEquals(candidate: String, expected: String): Boolean =
        MessageDigest.isEqual(
            candidate.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )

    companion object {
        const val DEFAULT_PORT = WebControlConfig.DEFAULT_PORT
        private const val AUTH_COOKIE_NAME = "miruplay_web_token"
    }
}
