package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PosterWallArrangement
import com.miruplay.tv.repository.WebControlAccessManager
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebControlSettingsRouteTest {
    @Test
    fun `scan settings round trip parses request and serializes response`() {
        val service = SettingsStubService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val put = server.serve(
            session(
                method = NanoHTTPD.Method.PUT,
                uri = "/api/settings/scan",
                body = """{"autoScanEnabled":true,"autoScanIntervalHours":12,"mergeSameAnimeEnabled":true,"posterWallArrangement":"RELEASE_SEASON","currentAppMode":"drama"}""",
            )
        )
        assertEquals(NanoHTTPD.Response.Status.OK, put.status)
        assertEquals(true, service.capturedScan?.autoScanEnabled)
        assertEquals(12, service.capturedScan?.autoScanIntervalHours)
        assertEquals(PosterWallArrangement.RELEASE_SEASON, service.capturedScan?.posterWallArrangement)
        assertEquals("drama", service.capturedScan?.currentAppMode)

        val get = server.serve(session(method = NanoHTTPD.Method.GET, uri = "/api/settings/scan", body = ""))
        assertEquals(NanoHTTPD.Response.Status.OK, get.status)
        val body = get.bodyText()
        assertTrue(body.contains("\"autoScanEnabled\":true"))
        assertTrue(body.contains("\"currentAppMode\":\"drama\""))
    }

    @Test
    fun `playback settings route serializes end action and tone mapping`() {
        val service = SettingsStubService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val put = server.serve(
            session(
                method = NanoHTTPD.Method.PUT,
                uri = "/api/settings/playback",
                body = """{"episodeVersionSelectionPolicy":"manual","preferredSubtitleLanguage":"zh_hans","subtitleBackgroundTransparent":true}""",
            ),
        )
        assertEquals(NanoHTTPD.Response.Status.OK, put.status)
        assertEquals("manual", service.capturedPlayback?.episodeVersionSelectionPolicy)
        assertEquals("zh_hans", service.capturedPlayback?.preferredSubtitleLanguage)
        assertEquals(true, service.capturedPlayback?.subtitleBackgroundTransparent)

        val get = server.serve(session(method = NanoHTTPD.Method.GET, uri = "/api/settings/playback", body = ""))
        assertEquals(NanoHTTPD.Response.Status.OK, get.status)
        val body = get.bodyText()
        assertTrue(body.contains("\"endAction\":\"return_to_detail\""))
        assertTrue(body.contains("\"episodeVersionSelectionPolicy\":\"auto_nearest\""))
        assertTrue(body.contains("\"episodeVersionSelectionPolicyOptions\""))
        assertTrue(body.contains("\"preferredSubtitleLanguage\":\"zh_hans\""))
        assertTrue(body.contains("\"subtitleBackgroundTransparent\":true"))
        assertTrue(body.contains("\"preferredSubtitleLanguageOptions\""))
        assertTrue(body.contains("\"formatAwareToneMapping\""))
        assertTrue(body.contains("\"backendOptions\""))
        assertTrue(body.contains("\"value\":\"EXPERIMENTAL_IJKPLAYER\""))
    }

    @Test
    fun `web control access rotate token route returns refreshed dto`() {
        val service = SettingsStubService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            session(method = NanoHTTPD.Method.POST, uri = "/api/web-control/access/rotate-token", body = "")
        )
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertTrue(response.bodyText().contains("\"accessToken\":\"rotated-token\""))
        assertTrue(service.rotateCalled)
    }

    @Test
    fun `tmdb token routes save and clear`() {
        val service = SettingsStubService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val save = server.serve(
            session(method = NanoHTTPD.Method.POST, uri = "/api/metadata/tmdb-token", body = """{"token":"tmdb-abc"}""")
        )
        assertEquals(NanoHTTPD.Response.Status.OK, save.status)
        assertEquals("tmdb-abc", service.capturedTmdbToken)
        assertTrue(save.bodyText().contains("\"tmdbTokenConfigured\":true"))

        val clear = server.serve(session(method = NanoHTTPD.Method.DELETE, uri = "/api/metadata/tmdb-token", body = ""))
        assertEquals(NanoHTTPD.Response.Status.OK, clear.status)
        assertTrue(service.tmdbCleared)
    }

    @Test
    fun `app update check route returns dto`() {
        val service = SettingsStubService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(session(method = NanoHTTPD.Method.POST, uri = "/api/app-update/check", body = ""))
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        val body = response.bodyText()
        assertTrue(body.contains("\"currentVersionName\":\"1.0.0\""))
        assertTrue(body.contains("\"updateAvailable\":true"))
    }

    @Test
    fun `app control route parses restart action`() {
        val service = SettingsStubService()
        val server = NanoHttpWebControlServer(
            webControlService = service,
            webControlAccess = EnabledAccess,
            staticAssets = WebControlStaticAssets { null },
        )

        val response = server.serve(
            session(method = NanoHTTPD.Method.POST, uri = "/api/app-control", body = """{"action":"restart"}""")
        )
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals("restart", service.capturedAppControlAction)
        assertTrue(response.bodyText().contains("\"accepted\":true"))
    }

    private object EnabledAccess : WebControlAccessManager {
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

    private fun session(method: NanoHTTPD.Method, uri: String, body: String): NanoHTTPD.IHTTPSession =
        FakeSettingsSession(method, uri, body)

    private class FakeSettingsSession(
        private val method: NanoHTTPD.Method,
        private val uri: String,
        body: String,
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

    private class SettingsStubService : EmptyWebControlEndpointService() {
        var capturedScan: ScanSettingsRequest? = null
        var capturedPlayback: PlaybackSettingsRequest? = null
        var capturedTmdbToken: String? = null
        var capturedAppControlAction: String? = null
        var tmdbCleared = false
        var rotateCalled = false

        override suspend fun getScanSettings(): ScanSettingsDto = ScanSettingsDto(
            autoScanEnabled = true,
            autoScanIntervalHours = 12,
            lastScanAt = 0L,
            mergeSameAnimeEnabled = true,
            posterWallArrangement = PosterWallArrangement.RELEASE_SEASON,
            currentAppMode = "drama",
        )

        override suspend fun saveScanSettings(request: ScanSettingsRequest): ScanSettingsDto {
            capturedScan = request
            return runBlocking { getScanSettings() }
        }

        override suspend fun getPlaybackSettings(): PlaybackSettingsDto = PlaybackSettingsDto(
            endAction = "return_to_detail",
            preferredSubtitleLanguage = "zh_hans",
            subtitleBackgroundTransparent = true,
            formatAwareToneMapping = FormatAwareToneMappingPreferences(),
            backendOptions = listOf(
                PlaybackBackendOptionDto(
                    value = "EXPERIMENTAL_IJKPLAYER",
                    label = "实验 ijkplayer",
                ),
            ),
        )

        override suspend fun savePlaybackSettings(request: PlaybackSettingsRequest): PlaybackSettingsDto {
            capturedPlayback = request
            return getPlaybackSettings()
        }

        override suspend fun getMetadataSettings(): MetadataSettingsDto =
            MetadataSettingsDto(bangumiTokenConfigured = false, tmdbTokenConfigured = true)

        override suspend fun saveTmdbToken(request: TmdbTokenRequest): MetadataSettingsDto {
            capturedTmdbToken = request.token
            return getMetadataSettings()
        }

        override suspend fun clearTmdbToken(): MetadataSettingsDto {
            tmdbCleared = true
            return MetadataSettingsDto(bangumiTokenConfigured = false, tmdbTokenConfigured = false)
        }

        override suspend fun rotateWebControlAccessToken(): WebControlAccessDto {
            rotateCalled = true
            return WebControlAccessDto(enabled = true, accessToken = "rotated-token", urls = emptyList())
        }

        override suspend fun checkAppUpdate(): AppUpdateDto = AppUpdateDto(
            currentVersionName = "1.0.0",
            currentVersionCode = 100L,
            latest = AppUpdateInfoDto(
                versionName = "1.1.0",
                versionCode = 110L,
                releaseName = "v1.1.0",
                tagName = "v1.1.0",
                publishedAt = "2026-01-01",
                releaseUrl = "https://example.test/release",
                assetName = "app.apk",
                assetSizeBytes = 1L,
                downloadUrl = "https://example.test/app.apk",
            ),
            updateAvailable = true,
            lastCheckedAt = 1L,
            canRequestPackageInstalls = true,
        )

        override suspend fun appControl(request: AppControlRequest): AppControlDto {
            capturedAppControlAction = request.action
            return AppControlDto(action = request.action, accepted = true, message = "ok")
        }
    }
}
