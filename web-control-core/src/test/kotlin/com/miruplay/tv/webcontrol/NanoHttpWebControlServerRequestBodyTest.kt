package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.WebControlAccessManager
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NanoHttpWebControlServerRequestBodyTest {
    @Test
    fun `json request body defaults to UTF-8 when content type omits charset`() {
        val service = CapturingWebControlService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )
        val session = FakeSession(
            method = NanoHTTPD.Method.POST,
            uri = "/api/sources",
            contentType = "application/json",
            body = """{"name":"ADB WebDAV 中文","type":"WEBDAV","location":"http://dav.test"}""",
        )

        val response = server.serve(session)

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertNotNull(service.capturedSourceRequest)
        assertEquals("ADB WebDAV 中文", service.capturedSourceRequest?.name)
    }

    private object EnabledWebControlAccess : WebControlAccessManager {
        override var webControlEnabled: Boolean = true
        override val accessToken: String = "token"
        override fun rotateAccessToken(): String = accessToken
        override fun addEnabledChangeListener(onChanged: (Boolean) -> Unit): Closeable =
            Closeable { }
    }

    private class FakeSession(
        private val method: NanoHTTPD.Method,
        private val uri: String,
        contentType: String,
        body: String,
    ) : NanoHTTPD.IHTTPSession {
        private val bodyBytes = body.toByteArray(Charsets.UTF_8)
        private val headers = mapOf(
            "content-type" to contentType,
            "content-length" to bodyBytes.size.toString(),
            "x-miruplay-token" to "token",
        )

        override fun execute() = Unit
        override fun getCookies(): NanoHTTPD.CookieHandler =
            throw UnsupportedOperationException("Cookies are not used in this test")
        override fun getHeaders(): Map<String, String> = headers
        override fun getInputStream(): InputStream = ByteArrayInputStream(bodyBytes)
        override fun getMethod(): NanoHTTPD.Method = method
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getParms(): Map<String, String> = emptyMap()
        override fun getParameters(): Map<String, List<String>> = emptyMap()
        override fun getQueryParameterString(): String? = null
        override fun getUri(): String = uri
        @Suppress("OVERRIDE_DEPRECATION")
        override fun parseBody(files: MutableMap<String, String>) {
            throw AssertionError("Raw JSON bodies should be read before NanoHTTPD decodes them")
        }
        override fun getRemoteIpAddress(): String = "127.0.0.1"
        override fun getRemoteHostName(): String = "localhost"
    }

    private class CapturingWebControlService : WebControlEndpointService {
        var capturedSourceRequest: SourceRequest? = null

        override suspend fun addSource(request: SourceRequest): MediaSourceInfo {
            capturedSourceRequest = request
            return MediaSourceInfoConventions.webDav(
                name = request.name,
                url = request.location,
            ).copy(id = 7L)
        }

        override suspend fun getServerInfo(port: Int): ServerInfoDto = error("unused")
        override suspend fun listSources(): List<MediaSourceInfo> = error("unused")
        override suspend fun browseLocalDirectories(path: String): LocalDirectoryDto = error("unused")
        override suspend fun browseCloudDriveDirectories(endpointUrl: String, path: String): CloudDriveDirectoryDto =
            error("unused")
        override suspend fun updateSource(sourceId: Long, request: SourceRequest): MediaSourceInfo = error("unused")
        override suspend fun removeSource(sourceId: Long) = error("unused")
        override suspend fun testSource(request: SourceTestRequest): SourceTestResponse = error("unused")
        override suspend fun scanSource(sourceId: Long): SourceScanResponse = error("unused")
        override suspend fun scanAllSources(): List<SourceScanResponse> = error("unused")
        override suspend fun getCloudDriveAutomation(): CloudDriveAutomationDto = error("unused")
        override suspend fun saveCloudDriveConfig(request: CloudDriveConfigRequest): CloudDriveAutomationDto =
            error("unused")
        override suspend fun loginCloudDrive(request: CloudDriveLoginRequest): CloudDriveAutomationDto = error("unused")
        override suspend fun saveCloudDriveToken(request: CloudDriveTokenRequest): CloudDriveTokenResponse =
            error("unused")
        override suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse = error("unused")
        override suspend fun saveRssSubscription(request: RssSubscriptionRequest): RssSubscriptionInfo =
            error("unused")
        override suspend fun updateRssSubscription(id: Long, request: RssSubscriptionRequest): RssSubscriptionInfo =
            error("unused")
        override suspend fun deleteRssSubscription(id: Long) = error("unused")
        override suspend fun getLogUpload(): LogUploadDto = error("unused")
        override suspend fun saveLogUploadConfig(request: LogUploadConfigRequest): LogUploadDto = error("unused")
        override suspend fun saveLogUploadToken(request: LogUploadTokenRequest): LogUploadDto = error("unused")
        override suspend fun clearLogUploadToken(): LogUploadDto = error("unused")
        override suspend fun uploadPendingLogs(): LogUploadDto = error("unused")
        override suspend fun downloadStartupDiagnostics(name: String): LocalLogDownload = error("unused")
        override suspend fun getMetadataSettings(): MetadataSettingsDto = error("unused")
        override suspend fun saveBangumiToken(request: BangumiTokenRequest): MetadataSettingsDto = error("unused")
        override suspend fun clearBangumiToken(): MetadataSettingsDto = error("unused")
        override suspend fun searchLibrary(query: String): LibraryDto = error("unused")
        override suspend fun getAnimeDetail(animeId: String): AnimeDetailDto = error("unused")
        override suspend fun playEpisode(request: PlayEpisodeRequest): PlaybackStatusDto = error("unused")
        override suspend fun playbackCommand(request: PlaybackCommandRequest): PlaybackStatusDto = error("unused")
        override suspend fun playbackStatus(): PlaybackStatusDto = error("unused")
    }
}
