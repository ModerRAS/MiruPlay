package com.miruplay.tv

import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.repository.AppMode
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
        )

        assertFalse(snapshot.hasTestSourceIntent())
        assertTrue(snapshot.hasAnyLaunchTestData())
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
}
