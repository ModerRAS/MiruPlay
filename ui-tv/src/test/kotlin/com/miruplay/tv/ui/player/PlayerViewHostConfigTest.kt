package com.miruplay.tv.ui.player

import com.miruplay.tv.model.PlaybackRenderBackend
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerViewHostConfigTest {
    @Test
    fun `experimental gl backend prefers the texture player view host`() {
        assertEquals(
            PlayerViewHost.SurfaceTextureView,
            resolvePlayerViewHost(
                activeBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
                requestedBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
            ),
        )
    }

    @Test
    fun `standard backend keeps surface player view host`() {
        assertEquals(
            PlayerViewHost.SurfaceView,
            resolvePlayerViewHost(
                activeBackend = PlaybackRenderBackend.STANDARD_EXO,
                requestedBackend = PlaybackRenderBackend.STANDARD_EXO,
            ),
        )
    }

    @Test
    fun `debug capture prefers texture player view host`() {
        assertEquals(
            PlayerViewHost.SurfaceTextureView,
            resolvePlayerViewHost(
                activeBackend = PlaybackRenderBackend.STANDARD_EXO,
                requestedBackend = PlaybackRenderBackend.STANDARD_EXO,
                preferCapturableTextureView = true,
            ),
        )
    }

    @Test
    fun `dedicated experimental surface takes priority over texture host`() {
        assertEquals(
            PlayerViewHost.DedicatedGlSurface,
            resolvePlayerViewHost(
                activeBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
                requestedBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
                preferCapturableTextureView = true,
                preferDedicatedGlSurface = true,
            ),
        )
    }

    @Test
    fun `experimental gl host can prewarm from the default backend before playback starts`() {
        assertEquals(
            PlayerViewHost.DedicatedGlSurface,
            resolvePlayerViewHost(
                activeBackend = PlaybackRenderBackend.STANDARD_EXO,
                requestedBackend = PlaybackRenderBackend.STANDARD_EXO,
                hasStartedPlayback = false,
                defaultBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
            ),
        )
    }

    @Test
    fun `capture texture preference stays latched for the playback session once requested`() {
        assertEquals(
            true,
            latchCapturableTextureViewForPlaybackSession(
                wasAlreadyLatched = false,
                pendingLabel = "hdr-proof",
            ),
        )
        assertEquals(
            true,
            latchCapturableTextureViewForPlaybackSession(
                wasAlreadyLatched = true,
                pendingLabel = null,
            ),
        )
    }

    @Test
    fun `dedicated experimental surface preference stays latched for the playback session once requested`() {
        assertEquals(
            true,
            latchDedicatedExperimentalSurfaceForPlaybackSession(
                wasAlreadyLatched = false,
                deviceGlEsMajorVersion = 2,
                activeBackend = PlaybackRenderBackend.STANDARD_EXO,
                requestedBackend = PlaybackRenderBackend.STANDARD_EXO,
                defaultBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
            ),
        )
        assertEquals(
            true,
            latchDedicatedExperimentalSurfaceForPlaybackSession(
                wasAlreadyLatched = true,
                deviceGlEsMajorVersion = 2,
                activeBackend = PlaybackRenderBackend.STANDARD_EXO,
                requestedBackend = PlaybackRenderBackend.STANDARD_EXO,
                defaultBackend = PlaybackRenderBackend.STANDARD_EXO,
            ),
        )
    }

    @Test
    fun `libvlc host stays visible while backend activation is still in flight`() {
        assertEquals(
            true,
            shouldShowLibVlcVideoLayout(
                activeBackend = PlaybackRenderBackend.STANDARD_EXO,
                requestedBackend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
                hasStartedPlayback = true,
                defaultBackend = PlaybackRenderBackend.STANDARD_EXO,
            ),
        )
    }

    @Test
    fun `libvlc host can prewarm from the default backend before playback starts`() {
        assertEquals(
            true,
            shouldShowLibVlcVideoLayout(
                activeBackend = PlaybackRenderBackend.STANDARD_EXO,
                requestedBackend = PlaybackRenderBackend.STANDARD_EXO,
                hasStartedPlayback = false,
                defaultBackend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
            ),
        )
    }

    @Test
    fun `standard playback does not keep the libvlc host visible`() {
        assertEquals(
            false,
            shouldShowLibVlcVideoLayout(
                activeBackend = PlaybackRenderBackend.STANDARD_EXO,
                requestedBackend = PlaybackRenderBackend.STANDARD_EXO,
                hasStartedPlayback = true,
                defaultBackend = PlaybackRenderBackend.STANDARD_EXO,
            ),
        )
    }

    @Test
    fun `libvlc default backend keeps libvlc host visible during startup before controller state catches up`() {
        assertEquals(
            true,
            shouldPreferLibVlcHostDuringStartup(
                hasStartedPlayback = true,
                requestedBackend = PlaybackRenderBackend.STANDARD_EXO,
                activeBackend = PlaybackRenderBackend.STANDARD_EXO,
                defaultBackend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
            ),
        )
    }
}
