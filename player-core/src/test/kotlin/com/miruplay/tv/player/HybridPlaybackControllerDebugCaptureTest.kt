package com.miruplay.tv.player

import android.content.Context
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.defaultToneMappingRuleSet
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HybridPlaybackControllerDebugCaptureTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `libvlc backend exposes and clears pending debug capture labels`() {
        val debugOverrides = PlaybackDebugOverrides().apply {
            pendingGlFrameCaptureLabel = "hdr-proof"
        }
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockk<ExoPlaybackController>(relaxed = true),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = debugOverrides,
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        assertEquals("hdr-proof", controller.pendingGlFrameCaptureLabel())

        controller.clearPendingGlFrameCaptureLabel("hdr-proof")

        assertNull(controller.pendingGlFrameCaptureLabel())
    }

    @Test
    fun `vmem same frame verification can request native snapshot after gl capture`() {
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockk<ExoPlaybackController>(relaxed = true),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides(),
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        controller.requestLibVlcNativeSnapshot("same-frame")

        assertEquals("same-frame", controller.pendingLibVlcNativeSnapshotLabel())
    }

    @Test
    fun `requesting libvlc backend prevents exo controller state from reclaiming the session`() {
        val exoState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
        val exoRequestedBackend = MutableStateFlow(PlaybackRenderBackend.STANDARD_EXO)
        val exoActiveBackend = MutableStateFlow(PlaybackRenderBackend.STANDARD_EXO)
        val exoController = mockk<ExoPlaybackController>(relaxed = true).also { controller ->
            every { controller.state } returns exoState
            every { controller.requestedRenderBackend } returns exoRequestedBackend
            every { controller.activeRenderBackend } returns exoActiveBackend
            every { controller.currentVideoSignalDescriptor } returns MutableStateFlow(null)
            every { controller.currentRenderRuleKey } returns MutableStateFlow(VideoRenderRuleKey.SDR)
            every { controller.currentToneMappingRuleSet } returns
                MutableStateFlow(defaultToneMappingRuleSet(VideoRenderRuleKey.SDR))
            every { controller.sessionRuleOverrides } returns MutableStateFlow(emptyMap())
            every { controller.fallbackReason } returns MutableStateFlow(null)
        }
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = exoController,
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides(),
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        runBlocking {
            controller.setRequestedRenderBackend(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC)
        }
        exoRequestedBackend.value = PlaybackRenderBackend.STANDARD_EXO
        exoActiveBackend.value = PlaybackRenderBackend.STANDARD_EXO
        exoState.value = PlaybackState.Loading(
            source = com.miruplay.tv.model.PlaybackSource(
                uri = "file:///tmp.mp4",
                mediaSourceId = "tmp",
                startPosition = 0L,
                subtitleTracks = emptyList(),
                episodeId = null,
            )
        )

        assertEquals(
            PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
            controller.requestedRenderBackend.value,
        )
        assertEquals(
            PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
            controller.activeRenderBackend.value,
        )
        assertEquals(PlaybackState.Idle, controller.state.value)
    }

    @Test
    fun `android display snapshot remains eligible once playback is active even before displayed pictures are reported`() {
        val evidence = LibVlcRenderEvidence(
            decodedVideo = 12,
            displayedPictures = 0,
            lostPictures = 0,
            hasDisplayedFrames = false,
        )

        assertTrue(
            shouldAttemptLibVlcNativeSnapshotForTest(
                evidence = evidence,
                voutMode = LibVlcVoutMode.ANDROID_DISPLAY,
                isPlaying = true,
                currentTimeMs = 0L,
            ),
        )
        assertFalse(
            shouldAttemptLibVlcNativeSnapshotForTest(
                evidence = evidence,
                voutMode = LibVlcVoutMode.DEFAULT,
                isPlaying = true,
                currentTimeMs = 0L,
            ),
        )
        assertFalse(
            shouldAttemptLibVlcNativeSnapshotForTest(
                evidence = evidence.copy(decodedVideo = 0),
                voutMode = LibVlcVoutMode.ANDROID_DISPLAY,
                isPlaying = true,
                currentTimeMs = 0L,
            ),
        )
    }

    @Test
    fun `vmem stream snapshot waits until the vmem path has produced a frame`() {
        val evidence = LibVlcRenderEvidence(
            decodedVideo = 12,
            displayedPictures = 1,
            lostPictures = 0,
            hasDisplayedFrames = true,
        )

        assertFalse(
            shouldAttemptLibVlcNativeSnapshotForTest(
                evidence = evidence,
                voutMode = LibVlcVoutMode.VMEM_STREAM,
                isPlaying = true,
                currentTimeMs = 4_404L,
                vmemFrameReady = false,
            ),
        )
        assertTrue(
            shouldAttemptLibVlcNativeSnapshotForTest(
                evidence = evidence,
                voutMode = LibVlcVoutMode.VMEM_STREAM,
                isPlaying = true,
                currentTimeMs = 15_700L,
                vmemFrameReady = true,
            ),
        )
    }
}
