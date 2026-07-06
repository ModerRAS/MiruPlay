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
        assertTrue(body.contains("\"peakDetectionStrategy\":\"DYNAMIC\""))
        assertTrue(body.contains("\"effectiveEmbeddedMpvVo\":"))
        assertTrue(body.contains("\"effectiveEmbeddedMpvHwdec\":"))
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
                      "sessionToneMappingPreset": "passthrough",
                      "sessionPeakDetectionStrategy": "static",
                      "sessionGamutMappingMode": "clip",
                      "embeddedMpvVo": "gpu-hq",
                      "embeddedMpvHwdec": "off",
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
        assertEquals("passthrough", request?.sessionToneMappingPreset)
        assertEquals("static", request?.sessionPeakDetectionStrategy)
        assertEquals("clip", request?.sessionGamutMappingMode)
        assertEquals("gpu-hq", request?.embeddedMpvVo)
        assertEquals("off", request?.embeddedMpvHwdec)
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

    @Test
    fun `GET playback native diagnostics returns property snapshot and recent native logs`() {
        val service = CapturingPlaybackDebugConfigService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/api/playback/native-diagnostics?limit=3",
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(3, service.capturedNativeDiagnosticsLimit)
        val body = response.bodyText()
        assertTrue(body.contains("\"available\":true"))
        assertTrue(body.contains("\"name\":\"vo\""))
        assertTrue(body.contains("\"value\":\"gpu-hq\""))
        assertTrue(body.contains("\"prefix\":\"cplayer\""))
        assertTrue(body.contains("\"text\":\"restarting audio after underrun\""))
    }

    @Test
    fun `POST playback native profile captures simpleperf request`() {
        val service = CapturingPlaybackDebugConfigService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/api/playback/native-profile",
                body = """
                    {
                      "durationMs": 6000,
                      "sampleFrequency": 1200,
                      "event": "task-clock:u",
                      "callGraph": "fp",
                      "traceOffCpu": true,
                      "sampleTids": [3600, 3604]
                    }
                """.trimIndent(),
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(6000L, service.capturedNativeProfileRequest?.durationMs)
        assertEquals(1200, service.capturedNativeProfileRequest?.sampleFrequency)
        assertEquals("task-clock:u", service.capturedNativeProfileRequest?.event)
        assertEquals("fp", service.capturedNativeProfileRequest?.callGraph)
        assertEquals(true, service.capturedNativeProfileRequest?.traceOffCpu)
        assertEquals(listOf(3600, 3604), service.capturedNativeProfileRequest?.sampleTids)
        val body = response.bodyText()
        assertTrue(body.contains("\"fileName\":\"miruplay-native-profile.data\""))
        assertTrue(body.contains("\"callGraph\":\"fp\""))
    }

    @Test
    fun `GET playback native profile download returns attachment`() {
        val service = CapturingPlaybackDebugConfigService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/api/playback/native-profile/download?name=miruplay-native-profile.data",
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals("miruplay-native-profile.data", service.capturedNativeProfileDownloadName)
    }

    @Test
    fun `POST playback profile captures sampling request`() {
        val service = CapturingPlaybackDebugConfigService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledWebControlAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/api/playback/profile",
                body = """
                    {
                      "durationMs": 1500,
                      "intervalMs": 25,
                      "maxStacks": 20,
                      "includeThreadNames": ["main"]
                    }
                """.trimIndent(),
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(1500L, service.capturedProfileRequest?.durationMs)
        assertEquals(25L, service.capturedProfileRequest?.intervalMs)
        assertEquals(20, service.capturedProfileRequest?.maxStacks)
        assertEquals(listOf("main"), service.capturedProfileRequest?.includeThreadNames)
        val body = response.bodyText()
        assertTrue(body.contains("\"collapsedText\":\"thread:main;top.frame 3\""))
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
        var capturedNativeDiagnosticsLimit: Int? = null
        var capturedNativeProfileRequest: PlaybackNativeProfileRequest? = null
        var capturedNativeProfileDownloadName: String? = null
        var capturedProfileRequest: PlaybackProfileRequest? = null

        override suspend fun getPlaybackDebugConfig(): PlaybackDebugConfigDto {
            getCalls += 1
            return PlaybackDebugConfigDto(
                defaultBackend = "STANDARD_EXO",
                currentToneMapping = PlaybackDebugCurrentToneMappingDto(
                    enabled = true,
                    curvePreset = "MOBIUS",
                    peakDetectionStrategy = "DYNAMIC",
                    gamutMappingMode = "perceptual",
                    targetSdrNits = 120,
                    contrastRecovery = 8,
                    saturationRecovery = 10,
                    highlightCompression = 18,
                ),
                effectiveEmbeddedMpvVo = "gpu-hq",
                effectiveEmbeddedMpvHwdec = "mediacodec,mediacodec-copy",
            )
        }

        override suspend fun savePlaybackDebugConfig(request: PlaybackDebugConfigRequest): PlaybackDebugConfigDto {
            capturedSaveRequest = request
            return PlaybackDebugConfigDto(
                defaultBackend = request.defaultBackend ?: "STANDARD_EXO",
                requestedBackend = request.requestedBackend ?: request.defaultBackend ?: "STANDARD_EXO",
                forcedSignalKind = request.forcedSignalKind,
                currentToneMapping = PlaybackDebugCurrentToneMappingDto(
                    enabled = request.sessionToneMappingPreset != "passthrough",
                    curvePreset = if (request.sessionToneMappingPreset == "passthrough") "PASSTHROUGH" else "MOBIUS",
                    peakDetectionStrategy = when (request.sessionPeakDetectionStrategy) {
                        "static" -> "STATIC_METADATA"
                        else -> "DYNAMIC"
                    },
                    gamutMappingMode = request.sessionGamutMappingMode ?: "perceptual",
                    targetSdrNits = 120,
                    contrastRecovery = 8,
                    saturationRecovery = 10,
                    highlightCompression = 18,
                ),
                embeddedMpvVo = request.embeddedMpvVo,
                embeddedMpvHwdec = request.embeddedMpvHwdec,
                effectiveEmbeddedMpvVo = request.embeddedMpvVo ?: "gpu-hq",
                effectiveEmbeddedMpvHwdec = request.embeddedMpvHwdec ?: "mediacodec,mediacodec-copy",
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

        override suspend fun getPlaybackNativeDiagnostics(logLimit: Int): PlaybackNativeDiagnosticsDto {
            capturedNativeDiagnosticsLimit = logLimit
            return PlaybackNativeDiagnosticsDto(
                activeBackend = "EXPERIMENTAL_MPV_EMBEDDED",
                requestedBackend = "EXPERIMENTAL_MPV_EMBEDDED",
                available = true,
                collectedAtElapsedRealtimeMs = 123456,
                surfaceAttached = true,
                pendingStartPositionMs = 0,
                properties = listOf(
                    PlaybackNativePropertyDto(name = "vo", value = "gpu-hq"),
                    PlaybackNativePropertyDto(name = "hwdec-current", value = "mediacodec-copy"),
                    PlaybackNativePropertyDto(name = "decoder-frame-drop-count", value = "0"),
                ),
                recentLogMessages = listOf(
                    PlaybackNativeLogMessageDto(
                        observedAtElapsedRealtimeMs = 123400,
                        prefix = "cplayer",
                        level = 50,
                        text = "restarting audio after underrun",
                    )
                ),
            )
        }

        override suspend fun capturePlaybackNativeProfile(request: PlaybackNativeProfileRequest): PlaybackNativeProfileCaptureDto {
            capturedNativeProfileRequest = request
            return PlaybackNativeProfileCaptureDto(
                fileName = "miruplay-native-profile.data",
                generatedAtMs = 123456789,
                durationMs = request.durationMs,
                sampleFrequency = request.sampleFrequency,
                event = request.event,
                callGraph = request.callGraph,
                traceOffCpu = request.traceOffCpu,
                fileSizeBytes = 4096,
                notes = listOf("captured"),
            )
        }

        override suspend fun downloadPlaybackNativeProfile(name: String): LocalLogDownload {
            capturedNativeProfileDownloadName = name
            return LocalLogDownload(
                fileName = name,
                contentType = "application/octet-stream",
                content = byteArrayOf(1, 2, 3),
            )
        }

        override suspend fun capturePlaybackProfile(request: PlaybackProfileRequest): PlaybackProfileReportDto {
            capturedProfileRequest = request
            return PlaybackProfileReportDto(
                durationMs = request.durationMs,
                intervalMs = request.intervalMs,
                samplePasses = 3,
                sampledThreadCount = 1,
                totalStackSamples = 3,
                collapsedStacks = listOf(PlaybackProfileStackDto(stack = "thread:main;top.frame", samples = 3)),
                collapsedText = "thread:main;top.frame 3",
                threadSummaries = listOf(
                    PlaybackProfileThreadDto(
                        threadName = "main",
                        samples = 3,
                        runnableSamples = 2,
                        nativeTopFrameSamples = 0,
                        topStack = "thread:main;top.frame",
                    )
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
