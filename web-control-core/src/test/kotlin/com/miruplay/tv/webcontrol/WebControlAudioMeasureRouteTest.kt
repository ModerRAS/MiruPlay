package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.AudioDspBand
import com.miruplay.tv.model.AudioDspChannelTarget
import com.miruplay.tv.repository.WebControlAccessManager
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.Closeable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Route tests for the sweep-measurement endpoints (Phase 2–4 web surface).
 */
class WebControlAudioMeasureRouteTest {

    @Test
    fun `GET measure capabilities returns the host probe result`() {
        val service = CapturingMeasureService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(method = NanoHTTPD.Method.GET, uri = "/api/audio-dsp/measure/capabilities")
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        val body = response.bodyText()
        assertTrue(body.contains("\"available\":true"))
        assertTrue(body.contains("USB mic"))
    }

    @Test
    fun `POST measure import-wav rejects malformed base64 with a 400`() {
        val service = CapturingMeasureService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/api/audio-dsp/measure/import-wav",
                body = """{"wavBase64": "!!!not-base64!!!"}""",
            )
        )

        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `POST measure apply persists the measured bands as a preset`() {
        val service = CapturingMeasureService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/api/audio-dsp/measure/apply",
                body = """
                    {
                      "bands": [
                        {"type": "PEAKING", "frequencyHz": 41.8, "gainDb": -8.75, "q": 5.6}
                      ],
                      "target": "ALL",
                      "presetName": "Room EQ"
                    }
                """.trimIndent(),
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(1, service.appliedBands?.size)
        assertEquals(AudioDspChannelTarget.ALL, service.appliedTarget)
        val body = response.bodyText()
        assertTrue(body.contains("Room EQ"))
        assertTrue(body.contains("measured-"))
    }

    private class CapturingMeasureService : EmptyWebControlEndpointService() {
        var appliedBands: List<AudioDspBand>? = null
        var appliedTarget: AudioDspChannelTarget? = null

        override suspend fun getAudioDspMeasureCapabilities(): AudioDspMeasureCapabilitiesDto =
            AudioDspMeasureCapabilitiesDto(available = true, reason = null, inputDeviceName = "USB mic")

        override suspend fun importAudioDspMeasureWav(request: AudioDspMeasureImportRequest): AudioDspMeasureResultDto =
            throw IllegalArgumentException("WAV base64 payload is invalid")

        override suspend fun applyAudioDspMeasure(request: AudioDspMeasureApplyRequest): AudioDspDto {
            appliedBands = request.bands
            appliedTarget = request.target
            val preset = com.miruplay.tv.model.AudioDspPreset(
                id = "measured-1000",
                name = request.presetName ?: "measured",
                rules = listOf(com.miruplay.tv.model.AudioDspChannelRule(target = request.target, bands = request.bands)),
            )
            return AudioDspDto(config = com.miruplay.tv.model.AudioDspConfig(
                presets = listOf(preset),
                selectedPresetId = preset.id,
            ))
        }
    }

    private object EnabledWebControlAccess : WebControlAccessManager {
        override var webControlEnabled: Boolean = true
        override val accessToken: String = "token"
        override fun rotateAccessToken(): String = accessToken
        override fun addEnabledChangeListener(onChanged: (Boolean) -> Unit): Closeable = Closeable { }
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
}
