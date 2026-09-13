package com.miruplay.tv.webcontrol

import com.miruplay.tv.repository.WebControlAccessManager
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.Closeable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Route tests for the Topping DAC control endpoints. */
class WebControlToppingRouteTest {

    @Test
    fun `GET topping status returns the controller state`() {
        val service = CapturingToppingService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(method = NanoHTTPD.Method.GET, uri = "/api/topping/status")
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        val body = response.bodyText()
        assertTrue(body.contains("\"attached\":true"))
        assertTrue(body.contains("DX5 II"))
    }

    @Test
    fun `POST topping volume clamps loud levels without confirmation flag`() {
        val service = CapturingToppingService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/api/topping/volume",
                body = """{"db": -5.0}""",
            )
        )

        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, response.status)
        assertTrue(response.bodyText().contains("二次确认"))
    }

    @Test
    fun `POST topping preset applies bands from text`() {
        val service = CapturingToppingService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/api/topping/preset",
                body = """{"text": "Preamp: -6.7 dB\nFilter 1: ON PK Fc 1200 Hz Gain -3.2 dB Q 1.41"}""",
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(1, service.appliedBandCount)
        val body = response.bodyText()
        assertTrue(body.contains("\"appliedBandCount\":1"))
    }

    private object EnabledWebControlAccess : WebControlAccessManager {
        override var webControlEnabled: Boolean = true
        override val accessToken: String = "token"
        override fun rotateAccessToken(): String = accessToken
        override fun addEnabledChangeListener(onChanged: (Boolean) -> Unit): java.io.Closeable = java.io.Closeable { }
    }

    private class FakeSession(
        private val method: NanoHTTPD.Method,
        private val uri: String,
        body: String = "",
    ) : NanoHTTPD.IHTTPSession {
        private val bodyBytes = body.toByteArray(Charsets.UTF_8)
        private val headers = buildMap {
            put("x-miruplay-token", "token")
            if (bodyBytes.isNotEmpty()) {
                put("content-type", "application/json")
                put("content-length", bodyBytes.size.toString())
            }
        }

        override fun execute() = Unit
        override fun getCookies(): NanoHTTPD.CookieHandler =
            throw UnsupportedOperationException("Cookies are not used in this test")
        override fun getHeaders(): Map<String, String> = headers
        override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(bodyBytes)
        override fun getMethod(): NanoHTTPD.Method = method
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getParms(): Map<String, String> = emptyMap()
        override fun getParameters(): Map<String, List<String>> = emptyMap()
        override fun getQueryParameterString(): String? = null
        override fun getUri(): String = uri
        @Suppress("OVERRIDE_DEPRECATION")
        override fun parseBody(files: MutableMap<String, String>) {
            files["postData"] = bodyBytes.toString(Charsets.UTF_8)
        }
        override fun getRemoteIpAddress(): String = "127.0.0.1"
        override fun getRemoteHostName(): String = "localhost"
    }

    private fun NanoHTTPD.Response.bodyText(): String {
        val size = this.data.available().coerceAtLeast(0)
        val bytes = ByteArray(size)
        this.data.read(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private class CapturingToppingService : EmptyWebControlEndpointService() {
        var appliedBandCount: Int = 0

        override suspend fun getToppingStatus(): ToppingStatusDto = ToppingStatusDto(
            attached = true, model = "Topping DX5 II", confirmed = true,
            hidVolume = true, usbPermission = true,
        )

        override suspend fun setToppingVolume(request: ToppingVolumeRequest): ToppingStatusDto {
            require(request.confirmed || request.db <= -10.0) { request.db.toString() + " dB 音量过大，请二次确认后再执行" }
            return getToppingStatus().copy(volumeDb = request.db)
        }

        override suspend fun applyToppingPreset(request: ToppingPresetApplyRequest): ToppingPresetApplyDto {
            val text = request.text.orEmpty()
            val bandCount = Regex("Filter \\d+:").findAll(text).count()
            appliedBandCount = bandCount
            return ToppingPresetApplyDto(status = getToppingStatus(), appliedBandCount = bandCount)
        }
    }
}
