package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.player.LibVlcHardwareAccelerationMode
import com.miruplay.tv.player.LibVlcVoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDebugConfigMappingTest {
    @Test
    fun `backend parser accepts WebAPI friendly aliases`() {
        assertEquals(PlaybackRenderBackend.STANDARD_EXO, playbackRenderBackendFromDebugValue("exo"))
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC, playbackRenderBackendFromDebugValue("lib-vlc"))
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_GL, playbackRenderBackendFromDebugValue("experimental_gl"))
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED, playbackRenderBackendFromDebugValue("libmpv"))
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED, playbackRenderBackendFromDebugValue("mpv"))
        assertNull(playbackRenderBackendFromDebugValue("unknown"))
    }

    @Test
    fun `signal parser accepts HDR aliases`() {
        assertEquals(VideoSignalKind.HDR10_PLUS, videoSignalKindFromDebugValue("HDR10+"))
        assertEquals(VideoSignalKind.DOLBY_VISION, videoSignalKindFromDebugValue("dv"))
        assertEquals(VideoSignalKind.UNKNOWN_HDR, videoSignalKindFromDebugValue("hdr"))
    }

    @Test
    fun `libVLC debug parsers accept short names`() {
        assertEquals(
            LibVlcHardwareAccelerationMode.DECODING_ONLY,
            libVlcHardwareModeFromDebugValue("decoding-only"),
        )
        assertEquals(LibVlcHardwareAccelerationMode.DISABLED, libVlcHardwareModeFromDebugValue("off"))
        assertEquals(LibVlcVoutMode.ANDROID_DISPLAY, libVlcVoutModeFromDebugValue("android-display"))
        assertEquals(LibVlcVoutMode.VMEM_STREAM, libVlcVoutModeFromDebugValue("vmem"))
    }

    @Test
    fun `labels and chroma values normalize for debug requests`() {
        assertEquals("capture-1", debugLabelValue(" capture-1 "))
        assertNull(debugLabelValue("reset"))
        assertEquals("RV32", libVlcDisplayChromaFromDebugValue("rv32"))
        assertNull(libVlcDisplayChromaFromDebugValue("rgb"))
        assertTrue(isDebugClearValue("clear"))
    }
}
