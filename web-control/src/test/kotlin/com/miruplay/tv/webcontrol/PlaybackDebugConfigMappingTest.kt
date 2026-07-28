package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.model.PeakDetectionStrategy
import com.miruplay.tv.model.ToneMappingProfilePreset
import com.miruplay.tv.player.LibVlcHardwareAccelerationMode
import com.miruplay.tv.player.LibVlcVoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDebugConfigMappingTest {
    @Test
    fun `backend parser accepts WebAPI friendly aliases`() {
        assertEquals(PlaybackRenderBackend.STANDARD_EXO, playbackRenderBackendFromDebugValue("exo"))
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC, playbackRenderBackendFromDebugValue("lib-vlc"))
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_GL, playbackRenderBackendFromDebugValue("experimental_gl"))
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED, playbackRenderBackendFromDebugValue("mpvandroid"))
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED, playbackRenderBackendFromDebugValue("libmpv"))
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED, playbackRenderBackendFromDebugValue("mpv"))
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_IJKPLAYER, playbackRenderBackendFromDebugValue("ijk"))
        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_IJKPLAYER, playbackRenderBackendFromDebugValue("ijkplayer"))
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

    @Test
    fun `native profile downloads only allow generated perf data names`() {
        assertEquals(
            "miruplay-native-profile-1782115219139.data",
            sanitizeNativeProfileDownloadFileName("miruplay-native-profile-1782115219139.data"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            sanitizeNativeProfileDownloadFileName("../miruplay-native-profile-1782115219139.data.log")
        }
        assertThrows(IllegalArgumentException::class.java) {
            sanitizeNativeProfileDownloadFileName("miruplay-logs.jsonl")
        }
    }

    @Test
    fun `tone mapping debug parsers accept preset and peak detection aliases`() {
        assertEquals(ToneMappingProfilePreset.BYPASS, toneMappingPresetFromDebugValue("passthrough"))
        assertEquals(ToneMappingProfilePreset.BALANCED, toneMappingPresetFromDebugValue("mobius"))
        assertEquals(PeakDetectionStrategy.STATIC_METADATA, peakDetectionStrategyFromDebugValue("static"))
        assertEquals(PeakDetectionStrategy.DYNAMIC_AGGRESSIVE, peakDetectionStrategyFromDebugValue("aggressive"))
        assertEquals("clip", gamutMappingModeFromDebugValue("clip"))
        assertEquals("relative", gamutMappingModeFromDebugValue("relative"))
        assertEquals("gpu-next", embeddedMpvVoFromDebugValue("gpu-next"))
        assertEquals("gpu-hq", embeddedMpvVoFromDebugValue("gpu_hq"))
        assertEquals("mediacodec-copy", embeddedMpvHwdecFromDebugValue("copy"))
        assertEquals("mediacodec", embeddedMpvHwdecFromDebugValue("mediacodec"))
        assertEquals("mediacodec,mediacodec-copy", embeddedMpvHwdecFromDebugValue("default"))
        assertEquals("no", embeddedMpvHwdecFromDebugValue("off"))
        assertNull(toneMappingPresetFromDebugValue("unknown"))
        assertNull(peakDetectionStrategyFromDebugValue("weird"))
        assertNull(gamutMappingModeFromDebugValue("bogus"))
        assertNull(embeddedMpvVoFromDebugValue("bogus"))
        assertNull(embeddedMpvHwdecFromDebugValue("bogus"))
    }
}
