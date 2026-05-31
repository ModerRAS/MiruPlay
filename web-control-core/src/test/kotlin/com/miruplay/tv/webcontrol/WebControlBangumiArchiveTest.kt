package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.WebControlAccessManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream

class WebControlBangumiArchiveTest {
    @Test
    fun `default Bangumi Archive status is unavailable for runtimes without archive store`() = runBlocking {
        val service = ArchiveOnlyWebControlEndpointService()

        val status = service.getBangumiArchive()
        val download = service.downloadBangumiArchive()
        val upload = service.uploadBangumiArchive(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            originalName = "manual.zip",
            contentLength = 3L,
        )

        assertFalse(status.available)
        assertFalse(status.hasSubjectData)
        assertEquals("Bangumi Archive 下载在当前运行环境不可用", status.lastError)
        assertEquals(status, download)
        assertEquals(status, upload)
    }

    @Test
    fun `archive upload route passes raw request stream to service`() {
        val body = byteArrayOf(1, 2, 3, 4)
        val service = CapturingArchiveUploadService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )
        val session = RawUploadSession(
            uri = "/api/metadata/bangumi-archive/upload",
            query = "filename=%E6%89%8B%E5%8A%A8.zip",
            body = body,
        )

        val response = server.serve(session)

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals("手动.zip", service.originalName)
        assertEquals(body.size.toLong(), service.contentLength)
        assertEquals(body.toList(), service.uploadedBytes?.toList())
        assertFalse(session.parseBodyCalled)
    }
}

private object EnabledWebControlAccess : WebControlAccessManager {
    override var webControlEnabled: Boolean = true
    override val accessToken: String = "token"
    override fun rotateAccessToken(): String = accessToken
    override fun addEnabledChangeListener(onChanged: (Boolean) -> Unit): Closeable =
        Closeable { }
}

private class RawUploadSession(
    private val uri: String,
    private val query: String,
    body: ByteArray,
) : NanoHTTPD.IHTTPSession {
    private val bodyBytes = body
    private val headers = mapOf(
        "content-type" to "application/octet-stream",
        "content-length" to bodyBytes.size.toString(),
        "x-miruplay-token" to "token",
    )
    var parseBodyCalled: Boolean = false
        private set

    override fun execute() = Unit
    override fun getCookies(): NanoHTTPD.CookieHandler =
        throw UnsupportedOperationException("Cookies are not used in this test")
    override fun getHeaders(): Map<String, String> = headers
    override fun getInputStream(): InputStream = ByteArrayInputStream(bodyBytes)
    override fun getMethod(): NanoHTTPD.Method = NanoHTTPD.Method.POST
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getParms(): Map<String, String> = emptyMap()
    override fun getParameters(): Map<String, List<String>> = emptyMap()
    override fun getQueryParameterString(): String = query
    override fun getUri(): String = uri
    @Suppress("OVERRIDE_DEPRECATION")
    override fun parseBody(files: MutableMap<String, String>) {
        parseBodyCalled = true
    }
    override fun getRemoteIpAddress(): String = "127.0.0.1"
    override fun getRemoteHostName(): String = "localhost"
}

private class CapturingArchiveUploadService : ArchiveOnlyWebControlEndpointService() {
    var originalName: String? = null
    var contentLength: Long = 0L
    var uploadedBytes: ByteArray? = null

    override suspend fun uploadBangumiArchive(
        input: InputStream,
        originalName: String,
        contentLength: Long,
    ): BangumiArchiveDto {
        this.originalName = originalName
        this.contentLength = contentLength
        uploadedBytes = input.readBytes()
        return BangumiArchiveDto(available = true, hasSubjectData = true, latestName = originalName)
    }
}

private open class ArchiveOnlyWebControlEndpointService : WebControlEndpointService {
    override suspend fun getServerInfo(port: Int): ServerInfoDto = unused()
    override suspend fun listSources(): List<MediaSourceInfo> = unused()
    override suspend fun browseLocalDirectories(path: String): LocalDirectoryDto = unused()
    override suspend fun browseCloudDriveDirectories(endpointUrl: String, path: String): CloudDriveDirectoryDto = unused()
    override suspend fun addSource(request: SourceRequest): MediaSourceInfo = unused()
    override suspend fun updateSource(sourceId: Long, request: SourceRequest): MediaSourceInfo = unused()
    override suspend fun removeSource(sourceId: Long): Unit = unused()
    override suspend fun testSource(request: SourceTestRequest): SourceTestResponse = unused()
    override suspend fun scanSource(sourceId: Long): SourceScanResponse = unused()
    override suspend fun scanAllSources(): List<SourceScanResponse> = unused()
    override suspend fun getCloudDriveAutomation(): CloudDriveAutomationDto = unused()
    override suspend fun saveCloudDriveConfig(request: CloudDriveConfigRequest): CloudDriveAutomationDto = unused()
    override suspend fun loginCloudDrive(request: CloudDriveLoginRequest): CloudDriveAutomationDto = unused()
    override suspend fun saveCloudDriveToken(request: CloudDriveTokenRequest): CloudDriveTokenResponse = unused()
    override suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse = unused()
    override suspend fun saveRssSubscription(request: RssSubscriptionRequest): RssSubscriptionInfo = unused()
    override suspend fun updateRssSubscription(id: Long, request: RssSubscriptionRequest): RssSubscriptionInfo = unused()
    override suspend fun deleteRssSubscription(id: Long): Unit = unused()
    override suspend fun getLogUpload(): LogUploadDto = unused()
    override suspend fun saveLogUploadConfig(request: LogUploadConfigRequest): LogUploadDto = unused()
    override suspend fun saveLogUploadToken(request: LogUploadTokenRequest): LogUploadDto = unused()
    override suspend fun clearLogUploadToken(): LogUploadDto = unused()
    override suspend fun uploadPendingLogs(): LogUploadDto = unused()
    override suspend fun getMetadataSettings(): MetadataSettingsDto = unused()
    override suspend fun saveBangumiToken(request: BangumiTokenRequest): MetadataSettingsDto = unused()
    override suspend fun clearBangumiToken(): MetadataSettingsDto = unused()
    override suspend fun searchLibrary(query: String): LibraryDto = unused()
    override suspend fun getAnimeDetail(animeId: String): AnimeDetailDto = unused()
    override suspend fun playEpisode(request: PlayEpisodeRequest): PlaybackStatusDto = unused()
    override suspend fun playbackCommand(request: PlaybackCommandRequest): PlaybackStatusDto = unused()
    override suspend fun playbackStatus(): PlaybackStatusDto = unused()

    private fun unused(): Nothing = error("unused")
}
