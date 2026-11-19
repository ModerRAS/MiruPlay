package com.miruplay.tv.webcontrol

import com.miruplay.tv.repository.WebControlAccessManager
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Route tests for the 新番时间表 data-source settings endpoints. */
class WebControlScheduleSettingsRouteTest {

    @Test
    fun `GET schedule settings returns unconfigured by default`() {
        val service = ScheduleSettingsStubService()
        val server = serverFor(service)

        val response = server.serve(FakeSession(method = NanoHTTPD.Method.GET, uri = "/api/settings/schedule"))

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        val body = response.bodyText()
        assertTrue(body.contains("\"dataSourceUrl\":\"\""))
        assertTrue(body.contains("\"configured\":false"))
    }

    @Test
    fun `PUT schedule settings normalizes trailing slash and reports configured`() {
        val service = ScheduleSettingsStubService()
        val server = serverFor(service)

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.PUT,
                uri = "/api/settings/schedule",
                body = """{"dataSourceUrl":"https://example.com/"}""",
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals("https://example.com/", service.captured?.dataSourceUrl)
        val body = response.bodyText()
        assertTrue(body.contains("\"dataSourceUrl\":\"https://example.com\""))
        assertTrue(body.contains("\"configured\":true"))
        assertEquals("https://example.com", service.dataSourceUrl)
    }

    @Test
    fun `PUT invalid schedule settings returns 400 and keeps previous url`() {
        val service = ScheduleSettingsStubService()
        val server = serverFor(service)

        val saved = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.PUT,
                uri = "/api/settings/schedule",
                body = """{"dataSourceUrl":"https://example.com"}""",
            )
        )
        assertEquals(NanoHTTPD.Response.Status.OK, saved.status)

        val invalid = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.PUT,
                uri = "/api/settings/schedule",
                body = """{"dataSourceUrl":"not-a-url"}""",
            )
        )
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, invalid.status)
        assertTrue(invalid.bodyText().contains("地址无效"))
        assertEquals("https://example.com", service.dataSourceUrl)
        assertTrue(service.configured)
    }

    @Test
    fun `PUT empty schedule settings clears the stored url`() {
        val service = ScheduleSettingsStubService()
        val server = serverFor(service)
        server.serve(
            FakeSession(
                method = NanoHTTPD.Method.PUT,
                uri = "/api/settings/schedule",
                body = """{"dataSourceUrl":"https://example.com"}""",
            )
        )

        val cleared = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.PUT,
                uri = "/api/settings/schedule",
                body = """{"dataSourceUrl":""}""",
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, cleared.status)
        assertEquals("", service.dataSourceUrl)
        assertEquals(false, service.configured)
        assertTrue(cleared.bodyText().contains("\"configured\":false"))
    }

    private fun serverFor(service: ScheduleSettingsStubService): NanoHttpWebControlServer =
        NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

    private class ScheduleSettingsStubService : EmptyWebControlEndpointService() {
        var dataSourceUrl: String = ""
        var configured: Boolean = false
        var captured: ScheduleSettingsRequest? = null

        override suspend fun getScheduleSettings(): ScheduleSettingsDto =
            ScheduleSettingsDto(dataSourceUrl = dataSourceUrl, configured = configured)

        override suspend fun saveScheduleSettings(request: ScheduleSettingsRequest): ScheduleSettingsDto {
            captured = request
            val rawUrl = request.dataSourceUrl.trim()
            if (rawUrl.isEmpty()) {
                dataSourceUrl = ""
                configured = false
                return getScheduleSettings()
            }
            val normalized = normalize(rawUrl)
                ?: throw IllegalArgumentException("时间表数据源地址无效，请填写 http:// 或 https:// 开头的完整地址")
            dataSourceUrl = normalized
            configured = true
            return getScheduleSettings()
        }

        private fun normalize(rawUrl: String): String? {
            if (rawUrl.any { it.isWhitespace() }) return null
            val uri = runCatching { java.net.URI(rawUrl) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            if (uri.host.isNullOrBlank()) return null
            if (uri.rawQuery != null || uri.rawFragment != null) return null
            return rawUrl.trimEnd('/').takeIf { it.isNotEmpty() }
        }
    }

    private object EnabledWebControlAccess : WebControlAccessManager {
        override var webControlEnabled: Boolean = true
        override val accessToken: String = "token"
        override fun rotateAccessToken(): String = accessToken
        override fun addEnabledChangeListener(onChanged: (Boolean) -> Unit): Closeable = Closeable { }
    }

    private fun NanoHTTPD.Response.bodyText(): String {
        val size = this.data.available().coerceAtLeast(0)
        val bytes = ByteArray(size)
        this.data.read(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private class FakeSession(
        private val method: NanoHTTPD.Method,
        private val uri: String,
        body: String = "",
    ) : NanoHTTPD.IHTTPSession {
        private val bodyBytes = body.toByteArray(Charsets.UTF_8)
        private val headers = mapOf(
            "content-type" to "application/json",
            "content-length" to bodyBytes.size.toString(),
            "x-miruplay-token" to "token",
        )

        override fun execute() = Unit
        override fun getCookies(): NanoHTTPD.CookieHandler = throw UnsupportedOperationException()
        override fun getHeaders(): Map<String, String> = headers
        override fun getInputStream(): InputStream = ByteArrayInputStream(bodyBytes)
        override fun getMethod(): NanoHTTPD.Method = method
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getParms(): Map<String, String> = emptyMap()
        override fun getParameters(): Map<String, List<String>> = emptyMap()
        override fun getQueryParameterString(): String? = null
        override fun getUri(): String = uri
        @Suppress("OVERRIDE_DEPRECATION")
        override fun parseBody(files: MutableMap<String, String>) = Unit
        override fun getRemoteIpAddress(): String = "127.0.0.1"
        override fun getRemoteHostName(): String = "localhost"
    }
}
