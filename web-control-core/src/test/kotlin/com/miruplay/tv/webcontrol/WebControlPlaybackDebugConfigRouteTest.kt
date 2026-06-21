package com.miruplay.tv.webcontrol

import com.miruplay.tv.repository.WebControlAccessManager
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebControlPlaybackDebugConfigRouteTest {
    @Test
    fun `GET playback debug config is token protected and returns current config`() {
        val service = CapturingPlaybackDebugConfigService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/api/playback/debug-config",
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(1, service.getCalls)
        val body = response.bodyText()
        assertTrue(body.contains("\"currentToneMapping\":{"))
        assertTrue(body.contains("\"targetSdrNits\":120"))
        assertTrue(body.contains("\"curvePreset\":\"MOBIUS\""))
    }

    @Test
    fun `PUT playback debug config parses release-safe diagnostic overrides`() {
        val service = CapturingPlaybackDebugConfigService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.PUT,
                uri = "/api/playback/debug-config",
                body = """
                    {
                      "defaultBackend": "EXPERIMENTAL_LIBVLC",
                      "requestedBackend": "EXPERIMENTAL_LIBVLC",
                      "forcedSignalKind": "HDR10",
                      "libVlcHardwareMode": "DECODING_ONLY",
                      "libVlcVoutMode": "ANDROID_DISPLAY",
                      "libVlcDisplayChroma": "RV32",
                      "glFrameCaptureLabel": "hdr10-webapi",
                      "libVlcNativeSnapshotLabel": "hdr10-native"
                    }
                """.trimIndent(),
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        val request = service.capturedSaveRequest
        assertNotNull(request)
        assertEquals("EXPERIMENTAL_LIBVLC", request?.defaultBackend)
        assertEquals("EXPERIMENTAL_LIBVLC", request?.requestedBackend)
        assertEquals("HDR10", request?.forcedSignalKind)
        assertEquals("DECODING_ONLY", request?.libVlcHardwareMode)
        assertEquals("ANDROID_DISPLAY", request?.libVlcVoutMode)
        assertEquals("RV32", request?.libVlcDisplayChroma)
        assertEquals("hdr10-webapi", request?.glFrameCaptureLabel)
        assertEquals("hdr10-native", request?.libVlcNativeSnapshotLabel)
    }

    @Test
    fun `PUT playback debug config can bypass libvlc startup probe`() {
        val service = CapturingPlaybackDebugConfigService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.PUT,
                uri = "/api/playback/debug-config",
                body = """
                    {
                      "skipLibVlcStartupProbe": true,
                      "skipLibVlcStartupOptions": true
                    }
                """.trimIndent(),
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(true, service.capturedSaveRequest?.skipLibVlcStartupProbe)
        assertEquals(true, service.capturedSaveRequest?.skipLibVlcStartupOptions)
    }

    @Test
    fun `GET playback clock samples returns recent samples`() {
        val service = CapturingPlaybackDebugConfigService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/api/playback/clock-samples?limit=2",
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(2, service.capturedClockSampleLimit)
        val body = response.bodyText()
        assertTrue(body.contains("\"activeBackend\":\"EXPERIMENTAL_MPV_EMBEDDED\""))
        assertTrue(body.contains("\"positionMs\":1234"))
        assertTrue(body.contains("\"positionMs\":1734"))
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
        body: String = "",
    ) : NanoHTTPD.IHTTPSession {
        private val bodyBytes = body.toByteArray(Charsets.UTF_8)
        private val queryParameterString = uri.substringAfter('?', "").ifBlank { null }
        private val cleanUri = uri.substringBefore('?')
        private val parameters = queryParameterString
            ?.split('&')
            ?.filter { it.isNotBlank() }
            ?.groupBy(
                keySelector = { it.substringBefore('=').trim() },
                valueTransform = { java.net.URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8.name()) }
            )
            ?: emptyMap()
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
        override fun getInputStream(): InputStream = ByteArrayInputStream(bodyBytes)
        override fun getMethod(): NanoHTTPD.Method = method
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getParms(): Map<String, String> = emptyMap()
        override fun getParameters(): Map<String, List<String>> = parameters
        override fun getQueryParameterString(): String? = queryParameterString
        override fun getUri(): String = cleanUri
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

    @Test
    fun `GET startup diagnostics download returns named startup file`() {
        val service = CapturingPlaybackDebugConfigService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/api/startup-diagnostics/download?name=probe",
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(1, service.downloadStartupDiagnosticsCalls)
        assertEquals("probe", service.capturedStartupDiagnosticsName)
    }

    private class CapturingPlaybackDebugConfigService : EmptyWebControlEndpointService() {
        var getCalls = 0
        var capturedSaveRequest: PlaybackDebugConfigRequest? = null
        var downloadStartupDiagnosticsCalls = 0
        var capturedStartupDiagnosticsName: String? = null
        var capturedClockSampleLimit: Int? = null

        override suspend fun getPlaybackDebugConfig(): PlaybackDebugConfigDto {
            getCalls += 1
            return PlaybackDebugConfigDto(
                defaultBackend = "STANDARD_EXO",
                currentToneMapping = PlaybackDebugCurrentToneMappingDto(
                    enabled = true,
                    curvePreset = "MOBIUS",
                    targetSdrNits = 120,
                    contrastRecovery = 8,
                    saturationRecovery = 10,
                    highlightCompression = 18,
                ),
            )
        }

        override suspend fun savePlaybackDebugConfig(request: PlaybackDebugConfigRequest): PlaybackDebugConfigDto {
            capturedSaveRequest = request
            return PlaybackDebugConfigDto(
                defaultBackend = request.defaultBackend ?: "STANDARD_EXO",
                requestedBackend = request.requestedBackend ?: request.defaultBackend ?: "STANDARD_EXO",
                forcedSignalKind = request.forcedSignalKind,
                libVlcHardwareMode = request.libVlcHardwareMode ?: "FULL",
                libVlcVoutMode = request.libVlcVoutMode ?: "DEFAULT",
                libVlcDisplayChroma = request.libVlcDisplayChroma,
                pendingGlFrameCaptureLabel = request.glFrameCaptureLabel,
                pendingLibVlcNativeSnapshotLabel = request.libVlcNativeSnapshotLabel,
            )
        }

        override suspend fun getPlaybackClockSamples(limit: Int): PlaybackClockSamplesDto {
            capturedClockSampleLimit = limit
            return PlaybackClockSamplesDto(
                activeBackend = "EXPERIMENTAL_MPV_EMBEDDED",
                requestedBackend = "EXPERIMENTAL_MPV_EMBEDDED",
                currentSignalKind = "HDR10",
                currentRuleKey = "HDR10",
                samples = listOf(
                    PlaybackClockSampleDto(
                        monotonicTimestampMs = 1000,
                        positionMs = 1234,
                        durationMs = 9999,
                        paused = false,
                        eofReached = false,
                    ),
                    PlaybackClockSampleDto(
                        monotonicTimestampMs = 1500,
                        positionMs = 1734,
                        durationMs = 9999,
                        paused = false,
                        eofReached = false,
                    ),
                ),
            )
        }

        override suspend fun downloadStartupDiagnostics(name: String): LocalLogDownload {
            downloadStartupDiagnosticsCalls += 1
            capturedStartupDiagnosticsName = name
            return LocalLogDownload(
                fileName = "miruplay-startup-$name.jsonl",
                contentType = "application/x-ndjson; charset=utf-8",
                content = "probe-line\n".toByteArray(),
            )
        }
    }
}
