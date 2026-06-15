package com.miruplay.tv.ui.player

import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.player.LibVlcVoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibVlcDebugCapturePolicyTest {
    private val source = PlaybackSource(
        uri = "/sdcard/test.mp4",
        mediaSourceId = "test",
        startPosition = 0L,
        subtitleTracks = emptyList(),
    )

    @Test
    fun `capture waits for playback progress before scheduling libvlc debug frame`() {
        val decision = resolveLibVlcDebugCaptureDecision(
            shouldShowVlcVideoLayout = true,
            currentActiveBackend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
            playbackState = PlaybackState.Playing(source, 500L),
            currentPosition = 500L,
            pendingLabel = "hdr-proof",
            hostAvailable = true,
            lastAttempt = null,
        )

        assertEquals(LibVlcDebugCaptureDecision.WAIT_FOR_PROGRESS, decision)
    }

    @Test
    fun `capture schedules once libvlc playback reaches the shorter hdr verification gate`() {
        val decision = resolveLibVlcDebugCaptureDecision(
            shouldShowVlcVideoLayout = true,
            currentActiveBackend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
            playbackState = PlaybackState.Playing(source, 1_300L),
            currentPosition = 1_300L,
            pendingLabel = "hdr-proof",
            hostAvailable = true,
            lastAttempt = null,
        )

        assertEquals(LibVlcDebugCaptureDecision.CAPTURE, decision)
    }

    @Test
    fun `capture uses playback state position when polled position is still stale`() {
        val decision = resolveLibVlcDebugCaptureDecision(
            shouldShowVlcVideoLayout = true,
            currentActiveBackend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
            playbackState = PlaybackState.Playing(source, 1_043L),
            currentPosition = 0L,
            pendingLabel = "hdr-proof",
            hostAvailable = true,
            lastAttempt = null,
        )

        assertEquals(LibVlcDebugCaptureDecision.CAPTURE, decision)
    }

    @Test
    fun `capture schedules once playback passes the minimum progress gate`() {
        val decision = resolveLibVlcDebugCaptureDecision(
            shouldShowVlcVideoLayout = true,
            currentActiveBackend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
            playbackState = PlaybackState.Playing(source, 5_500L),
            currentPosition = 5_500L,
            pendingLabel = "hdr-proof",
            hostAvailable = true,
            lastAttempt = null,
        )

        assertEquals(LibVlcDebugCaptureDecision.CAPTURE, decision)
    }

    @Test
    fun `capture throttles repeated attempts for the same label`() {
        val decision = resolveLibVlcDebugCaptureDecision(
            shouldShowVlcVideoLayout = true,
            currentActiveBackend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
            playbackState = PlaybackState.Playing(source, 6_000L),
            currentPosition = 6_000L,
            pendingLabel = "hdr-proof",
            hostAvailable = true,
            lastAttempt = LibVlcDebugCaptureAttempt(
                label = "hdr-proof",
                positionMs = 5_200L,
            ),
        )

        assertEquals(LibVlcDebugCaptureDecision.THROTTLED, decision)
    }

    @Test
    fun `vmem stream keeps gl capture pending after native snapshot succeeds`() {
        assertFalse(
            shouldClearPendingGlCaptureAfterNativeSnapshot(
                backend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
                voutMode = LibVlcVoutMode.VMEM_STREAM,
            ),
        )
    }

    @Test
    fun `other libvlc vout modes still clear gl capture after native snapshot succeeds`() {
        assertTrue(
            shouldClearPendingGlCaptureAfterNativeSnapshot(
                backend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
                voutMode = LibVlcVoutMode.ANDROID_DISPLAY,
            ),
        )
    }

    @Test
    fun `vmem stream requests native snapshot after gl capture succeeds`() {
        assertTrue(
            shouldRequestLibVlcNativeSnapshotAfterGlCapture(
                backend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
                voutMode = LibVlcVoutMode.VMEM_STREAM,
            ),
        )
    }

    @Test
    fun `vmem stream native snapshot wait no longer depends on the gl label staying pending`() {
        assertFalse(
            shouldRequirePendingGlLabelDuringLibVlcNativeSnapshotWait(
                backend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
                voutMode = LibVlcVoutMode.VMEM_STREAM,
            ),
        )
    }

    @Test
    fun `other libvlc modes still require matching gl label while waiting for native snapshot`() {
        assertTrue(
            shouldRequirePendingGlLabelDuringLibVlcNativeSnapshotWait(
                backend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
                voutMode = LibVlcVoutMode.ANDROID_DISPLAY,
            ),
        )
    }
}
