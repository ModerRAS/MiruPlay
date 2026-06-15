package com.miruplay.tv

import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.ToneMappingProfilePreset
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.player.LibVlcHardwareAccelerationMode
import com.miruplay.tv.player.LibVlcVoutMode
import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.AppModeSelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchTestSourceContentModeTest {
    @Test
    fun `explicit drama content mode wins`() {
        assertEquals(
            MediaContentMode.DRAMA,
            resolveLaunchTestSourceContentMode(
                rawValue = "drama",
                fallbackMode = AppMode.ANIME,
            ),
        )
    }

    @Test
    fun `explicit anime content mode wins`() {
        assertEquals(
            MediaContentMode.ANIME,
            resolveLaunchTestSourceContentMode(
                rawValue = "anime",
                fallbackMode = AppMode.DRAMA,
            ),
        )
    }

    @Test
    fun `fallback mode is used when extra is missing`() {
        assertEquals(
            MediaContentMode.DRAMA,
            resolveLaunchTestSourceContentMode(
                rawValue = null,
                fallbackMode = AppMode.DRAMA,
            ),
        )
    }

    @Test
    fun `anime is default when nothing is provided`() {
        assertEquals(
            MediaContentMode.ANIME,
            resolveLaunchTestSourceContentMode(
                rawValue = null,
                fallbackMode = null,
            ),
        )
    }

    @Test
    fun `tmdb override extra trims whitespace`() {
        assertEquals(
            "http://127.0.0.1:18080/mock",
            normalizeLaunchTmdbOverride("  http://127.0.0.1:18080/mock  "),
        )
    }

    @Test
    fun `blank tmdb override extra becomes null`() {
        assertNull(normalizeLaunchTmdbOverride("   "))
        assertNull(normalizeLaunchTmdbOverride(null))
    }

    @Test
    fun `tmdb token extra trims whitespace`() {
        assertEquals(
            "token-123",
            normalizeLaunchTmdbToken("  token-123  "),
        )
    }

    @Test
    fun `blank tmdb token extra becomes null`() {
        assertNull(normalizeLaunchTmdbToken(" "))
        assertNull(normalizeLaunchTmdbToken(null))
    }

    @Test
    fun `tmdb override snapshot keeps presence flags for blank extras`() {
        val overrides = resolveLaunchTestTmdbOverrides(
            rawToken = "   ",
            rawBaseUrlOverride = null,
            hasTokenExtra = true,
            hasBaseUrlExtra = false,
        )

        assertTrue(overrides.hasTokenExtra)
        assertFalse(overrides.hasBaseUrlExtra)
        assertNull(overrides.token)
        assertNull(overrides.baseUrlOverride)
    }

    @Test
    fun `launch intent snapshot detects tmdb-only test data`() {
        val snapshot = LaunchIntentSnapshot(
            legacyLocalPath = null,
            legacyLocalName = null,
            rawType = null,
            rawLocation = null,
            rawName = null,
            rawDisplayName = null,
            rawUsername = null,
            rawPassword = null,
            rawContentMode = null,
            disableOnlineMetadata = false,
            scanAfterAdd = false,
            tmdbOverrides = LaunchTestTmdbOverrides(
                token = "token-123",
                baseUrlOverride = null,
                hasTokenExtra = true,
                hasBaseUrlExtra = false,
            ),
            playbackOverrides = LaunchPlaybackOverrides(
                backend = null,
                ruleKey = null,
                preset = null,
            ),
            playbackDebugOverrides = LaunchPlaybackDebugOverrides(
                forcedSignalKind = null,
                captureGlFrameLabel = null,
                libVlcHardwareMode = null,
                libVlcVoutMode = null,
                libVlcDisplayChroma = null,
            ),
            directPlaybackRequest = null,
        )

        assertFalse(snapshot.hasTestSourceIntent())
        assertTrue(snapshot.hasAnyLaunchTestData())
    }

    @Test
    fun `launch intent snapshot detects direct playback only test data`() {
        val snapshot = LaunchIntentSnapshot(
            legacyLocalPath = null,
            legacyLocalName = null,
            rawType = null,
            rawLocation = null,
            rawName = null,
            rawDisplayName = null,
            rawUsername = null,
            rawPassword = null,
            rawContentMode = null,
            disableOnlineMetadata = false,
            scanAfterAdd = false,
            tmdbOverrides = LaunchTestTmdbOverrides(
                token = null,
                baseUrlOverride = null,
                hasTokenExtra = false,
                hasBaseUrlExtra = false,
            ),
            playbackOverrides = LaunchPlaybackOverrides(
                backend = null,
                ruleKey = null,
                preset = null,
            ),
            playbackDebugOverrides = LaunchPlaybackDebugOverrides(
                forcedSignalKind = null,
                captureGlFrameLabel = null,
                libVlcHardwareMode = null,
                libVlcVoutMode = null,
                libVlcDisplayChroma = null,
            ),
            directPlaybackRequest = LaunchDirectPlaybackRequest(
                uri = "/sdcard/Movies/HdrTest.mp4",
                mediaSourceId = "HdrTest",
                startPositionMs = 0L,
                episodeId = null,
            ),
        )

        assertFalse(snapshot.hasTestSourceIntent())
        assertTrue(snapshot.hasAnyLaunchTestData())
    }

    @Test
    fun `direct playback bootstrap should switch to compose immediately without waiting for a pre draw`() {
        assertTrue(shouldSwitchDirectPlaybackPlaceholderToComposeImmediately())
    }

    @Test
    fun `launch test source intent detects legacy local path`() {
        assertTrue(hasLaunchTestSourceIntent("/sdcard/Shows", null))
    }

    @Test
    fun `launch test source intent detects explicit source location`() {
        assertTrue(hasLaunchTestSourceIntent(null, "https://dav.example.test/drama"))
    }

    @Test
    fun `launch test source intent is false when both inputs missing`() {
        assertFalse(hasLaunchTestSourceIntent(null, null))
        assertFalse(hasLaunchTestSourceIntent(" ", " "))
    }

    @Test
    fun `launch test source type respects explicit type`() {
        assertEquals(
            MediaSourceType.WEBDAV,
            resolveLaunchTestSourceType("webdav", "/sdcard/ignored"),
        )
    }

    @Test
    fun `launch test source type infers webdav from http location`() {
        assertEquals(
            MediaSourceType.WEBDAV,
            resolveLaunchTestSourceType(null, "https://dav.example.test/drama"),
        )
    }

    @Test
    fun `launch test source type infers smb from unc path`() {
        assertEquals(
            MediaSourceType.SMB,
            resolveLaunchTestSourceType(null, "\\\\NAS\\Drama"),
        )
    }

    @Test
    fun `launch test source type falls back to local`() {
        assertEquals(
            MediaSourceType.LOCAL,
            resolveLaunchTestSourceType(null, "/storage/emulated/0/Drama"),
        )
    }

    @Test
    fun `normalize launch source location trims webdav and local paths`() {
        assertEquals(
            "https://dav.example.test/drama",
            normalizeLaunchTestSourceLocation(
                type = MediaSourceType.WEBDAV,
                location = "  https://dav.example.test/drama  ",
            ),
        )
        assertEquals(
            "/storage/emulated/0/Drama",
            normalizeLaunchTestSourceLocation(
                type = MediaSourceType.LOCAL,
                location = "  /storage/emulated/0/Drama  ",
            ),
        )
    }

    @Test
    fun `normalize launch source location normalizes smb roots`() {
        assertEquals(
            "smb://NAS/Drama",
            normalizeLaunchTestSourceLocation(
                type = MediaSourceType.SMB,
                location = "\\\\NAS\\Drama\\",
            ),
        )
    }

    @Test
    fun `legacy launch source request stays local and forces metadata off`() {
        val request = resolveLaunchTestSourceRequest(
            legacyLocalPath = " /sdcard/Shows ",
            legacyLocalName = " Legacy ",
            rawType = null,
            rawLocation = null,
            rawName = null,
            rawDisplayName = null,
            rawUsername = null,
            rawPassword = null,
            rawContentMode = "drama",
            disableOnlineMetadata = false,
            scanAfterAdd = true,
            fallbackMode = AppMode.ANIME,
        )

        assertNotNull(request)
        assertEquals("Legacy", request!!.name)
        assertEquals(MediaSourceType.LOCAL, request.type)
        assertEquals("/sdcard/Shows", request.location)
        assertEquals(MediaContentMode.DRAMA, request.contentMode)
        assertTrue(request.disableOnlineMetadata)
        assertTrue(request.scanAfterAdd)
    }

    @Test
    fun `explicit launch source request builds webdav drama request`() {
        val request = resolveLaunchTestSourceRequest(
            legacyLocalPath = null,
            legacyLocalName = null,
            rawType = "webdav",
            rawLocation = " https://dav.example.test/drama ",
            rawName = " Drama DAV ",
            rawDisplayName = "Drama Folder",
            rawUsername = " anonymous ",
            rawPassword = " ",
            rawContentMode = "drama",
            disableOnlineMetadata = true,
            scanAfterAdd = true,
            fallbackMode = AppMode.ANIME,
        )

        assertNotNull(request)
        assertEquals("Drama DAV", request!!.name)
        assertEquals(MediaSourceType.WEBDAV, request.type)
        assertEquals("https://dav.example.test/drama", request.location)
        assertEquals("Drama Folder", request.displayName)
        assertEquals("anonymous", request.username)
        assertEquals(" ", request.password)
        assertEquals(MediaContentMode.DRAMA, request.contentMode)
        assertTrue(request.disableOnlineMetadata)
        assertTrue(request.scanAfterAdd)
    }

    @Test
    fun `launch source request returns null when no location exists`() {
        assertNull(
            resolveLaunchTestSourceRequest(
                legacyLocalPath = null,
                legacyLocalName = null,
                rawType = "webdav",
                rawLocation = " ",
                rawName = null,
                rawDisplayName = null,
                rawUsername = null,
                rawPassword = null,
                rawContentMode = null,
                disableOnlineMetadata = false,
                scanAfterAdd = false,
                fallbackMode = null,
            )
        )
    }

    @Test
    fun `playback override parser resolves backend rule and preset`() {
        val overrides = resolveLaunchPlaybackOverrides(
            rawBackend = "experimental_gl",
            rawRuleKey = "hdr10_plus",
            rawPreset = "punchy",
        )

        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_GL, overrides.backend)
        assertEquals(VideoRenderRuleKey.HDR10_PLUS, overrides.ruleKey)
        assertEquals(ToneMappingProfilePreset.PUNCHY, overrides.preset)
    }

    @Test
    fun `playback debug override parser resolves forced signal kind`() {
        val overrides = resolveLaunchPlaybackDebugOverrides(
            rawForcedSignalKind = "hdr10",
            rawCaptureGlFrameLabel = null,
            rawLibVlcHardwareMode = "decoding_only",
            rawLibVlcVoutMode = "android_display",
            rawLibVlcDisplayChroma = null,
        )

        assertEquals(VideoSignalKind.HDR10, overrides.forcedSignalKind)
        assertEquals(LibVlcHardwareAccelerationMode.DECODING_ONLY, overrides.libVlcHardwareMode)
        assertEquals(LibVlcVoutMode.ANDROID_DISPLAY, overrides.libVlcVoutMode)
    }

    @Test
    fun `direct playback request trims values and derives media source id by default`() {
        val request = resolveLaunchDirectPlaybackRequest(
            rawUri = "  /sdcard/Movies/MiruPlayHdrTest/S02E01_10min_1080p_hdr30.mp4  ",
            rawMediaSourceId = " ",
            rawStartPositionMs = "1500",
            rawEpisodeId = " ",
        )

        assertNotNull(request)
        assertEquals(
            "/sdcard/Movies/MiruPlayHdrTest/S02E01_10min_1080p_hdr30.mp4",
            request!!.uri,
        )
        assertEquals("S02E01_10min_1080p_hdr30", request.mediaSourceId)
        assertEquals(1500L, request.startPositionMs)
        assertNull(request.episodeId)
    }

    @Test
    fun `direct playback request keeps explicit metadata and clamps invalid start position`() {
        val request = resolveLaunchDirectPlaybackRequest(
            rawUri = "file:///sdcard/Movies/sample.mp4",
            rawMediaSourceId = " HDR Sample ",
            rawStartPositionMs = "-20",
            rawEpisodeId = " episode-1 ",
        )

        assertNotNull(request)
        assertEquals("HDR Sample", request!!.mediaSourceId)
        assertEquals(0L, request.startPositionMs)
        assertEquals("episode-1", request.episodeId)
    }

    @Test
    fun `direct playback request returns null when uri is blank`() {
        assertNull(
            resolveLaunchDirectPlaybackRequest(
                rawUri = " ",
                rawMediaSourceId = "HdrTest",
                rawStartPositionMs = "0",
                rawEpisodeId = null,
            )
        )
    }

    @Test
    fun `launch bootstrap plan keeps direct playback immediate while source setup stays deferred`() {
        val directPlaybackRequest = LaunchDirectPlaybackRequest(
            uri = "file:///sdcard/Movies/sample_hdr.mp4",
            mediaSourceId = "sample_hdr",
            startPositionMs = 2500L,
            episodeId = null,
        )
        val snapshot = LaunchIntentSnapshot(
            legacyLocalPath = null,
            legacyLocalName = null,
            rawType = "webdav",
            rawLocation = " https://dav.example.test/hdr ",
            rawName = " HDR DAV ",
            rawDisplayName = "HDR DAV",
            rawUsername = "anonymous",
            rawPassword = "",
            rawContentMode = "drama",
            disableOnlineMetadata = true,
            scanAfterAdd = true,
            tmdbOverrides = LaunchTestTmdbOverrides(
                token = null,
                baseUrlOverride = null,
                hasTokenExtra = false,
                hasBaseUrlExtra = false,
            ),
            playbackOverrides = LaunchPlaybackOverrides(
                backend = PlaybackRenderBackend.EXPERIMENTAL_GL,
                ruleKey = null,
                preset = null,
            ),
            playbackDebugOverrides = LaunchPlaybackDebugOverrides(
                forcedSignalKind = VideoSignalKind.HDR10,
                captureGlFrameLabel = "exo_gl_bootstrap",
                libVlcHardwareMode = null,
                libVlcVoutMode = null,
                libVlcDisplayChroma = null,
            ),
            directPlaybackRequest = directPlaybackRequest,
        )

        val plan = buildLaunchBootstrapPlan(
            snapshot = snapshot,
            selectionState = AppModeSelectionState(
                currentAppMode = AppMode.DRAMA,
                hasCompletedModeSelection = true,
            ),
            debugBuild = true,
        )

        assertEquals(directPlaybackRequest, plan.directPlaybackRequest)
        assertNotNull(plan.deferredSourceRequest)
        assertEquals(MediaSourceType.WEBDAV, plan.deferredSourceRequest!!.type)
        assertEquals("https://dav.example.test/hdr", plan.deferredSourceRequest!!.location)
        assertEquals(AppMode.DRAMA, plan.initialSelectionState.currentAppMode)
    }

    @Test
    fun `launch bootstrap plan omits direct playback outside debug builds`() {
        val snapshot = LaunchIntentSnapshot(
            legacyLocalPath = null,
            legacyLocalName = null,
            rawType = null,
            rawLocation = null,
            rawName = null,
            rawDisplayName = null,
            rawUsername = null,
            rawPassword = null,
            rawContentMode = null,
            disableOnlineMetadata = false,
            scanAfterAdd = false,
            tmdbOverrides = LaunchTestTmdbOverrides(
                token = null,
                baseUrlOverride = null,
                hasTokenExtra = false,
                hasBaseUrlExtra = false,
            ),
            playbackOverrides = LaunchPlaybackOverrides(
                backend = PlaybackRenderBackend.EXPERIMENTAL_GL,
                ruleKey = null,
                preset = null,
            ),
            playbackDebugOverrides = LaunchPlaybackDebugOverrides(
                forcedSignalKind = null,
                captureGlFrameLabel = null,
                libVlcHardwareMode = null,
                libVlcVoutMode = null,
                libVlcDisplayChroma = null,
            ),
            directPlaybackRequest = LaunchDirectPlaybackRequest(
                uri = "file:///sdcard/Movies/sample_hdr.mp4",
                mediaSourceId = "sample_hdr",
                startPositionMs = 0L,
                episodeId = null,
            ),
        )

        val plan = buildLaunchBootstrapPlan(
            snapshot = snapshot,
            selectionState = AppModeSelectionState(
                currentAppMode = AppMode.ANIME,
                hasCompletedModeSelection = true,
            ),
            debugBuild = false,
        )

        assertNull(plan.directPlaybackRequest)
        assertNull(plan.deferredSourceRequest)
    }

    @Test
    fun `direct playback source mirrors launch request for player fast path`() {
        val request = LaunchDirectPlaybackRequest(
            uri = "file:///sdcard/Movies/sample_hdr.mp4",
            mediaSourceId = "sample_hdr",
            startPositionMs = 2048L,
            episodeId = "episode-7",
        )

        val source = directPlaybackSourceFor(request)

        assertEquals(request.uri, source.uri)
        assertEquals(request.mediaSourceId, source.mediaSourceId)
        assertEquals(request.startPositionMs, source.startPosition)
        assertEquals(request.episodeId, source.episodeId)
        assertTrue(source.subtitleTracks.isEmpty())
    }
}
