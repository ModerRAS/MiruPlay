package com.miruplay.tv

import com.miruplay.tv.player.LibVlcVoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchPlaybackDebugOverridesVmemProbeTest {
    @Test
    fun `playback debug override parser resolves vmem probe vout mode`() {
        val overrides = resolveLaunchPlaybackDebugOverrides(
            rawForcedSignalKind = null,
            rawCaptureGlFrameLabel = "hdr-proof",
            rawLibVlcHardwareMode = "disabled",
            rawLibVlcVoutMode = "vmem_probe",
            rawLibVlcDisplayChroma = null,
        )

        assertEquals(LibVlcVoutMode.VMEM_PROBE, overrides.libVlcVoutMode)
    }

    @Test
    fun `playback debug override parser resolves direct texture vout mode`() {
        val overrides = resolveLaunchPlaybackDebugOverrides(
            rawForcedSignalKind = null,
            rawCaptureGlFrameLabel = "hdr-proof",
            rawLibVlcHardwareMode = "decoding_only",
            rawLibVlcVoutMode = "direct_texture",
            rawLibVlcDisplayChroma = null,
        )

        assertEquals("DIRECT_TEXTURE", overrides.libVlcVoutMode?.name)
    }

    @Test
    fun `playback debug override parser resolves gl surface vout mode`() {
        val overrides = resolveLaunchPlaybackDebugOverrides(
            rawForcedSignalKind = null,
            rawCaptureGlFrameLabel = "hdr-proof",
            rawLibVlcHardwareMode = "full",
            rawLibVlcVoutMode = "gl_surface",
            rawLibVlcDisplayChroma = null,
        )

        assertEquals(LibVlcVoutMode.GL_SURFACE, overrides.libVlcVoutMode)
    }

    @Test
    fun `playback debug override parser resolves output callbacks vout mode`() {
        val overrides = resolveLaunchPlaybackDebugOverrides(
            rawForcedSignalKind = null,
            rawCaptureGlFrameLabel = "hdr-proof",
            rawLibVlcHardwareMode = "full",
            rawLibVlcVoutMode = "output_callbacks",
            rawLibVlcDisplayChroma = null,
        )

        assertEquals(LibVlcVoutMode.OUTPUT_CALLBACKS, overrides.libVlcVoutMode)
    }

    @Test
    fun `playback debug override parser resolves vmem stream vout mode`() {
        val overrides = resolveLaunchPlaybackDebugOverrides(
            rawForcedSignalKind = null,
            rawCaptureGlFrameLabel = "hdr-proof",
            rawLibVlcHardwareMode = "disabled",
            rawLibVlcVoutMode = "vmem_stream",
            rawLibVlcDisplayChroma = null,
        )

        assertEquals(LibVlcVoutMode.VMEM_STREAM, overrides.libVlcVoutMode)
    }

    @Test
    fun `vmem stream launch defers native snapshot until after gl capture`() {
        val overrides = resolveLaunchPlaybackDebugOverrides(
            rawForcedSignalKind = null,
            rawCaptureGlFrameLabel = "hdr-proof",
            rawLibVlcHardwareMode = "disabled",
            rawLibVlcVoutMode = "vmem_stream",
            rawLibVlcDisplayChroma = null,
        )

        assertNull(initialPendingLibVlcNativeSnapshotLabelFor(overrides))
    }

    @Test
    fun `non vmem launch still seeds native snapshot immediately`() {
        val overrides = resolveLaunchPlaybackDebugOverrides(
            rawForcedSignalKind = null,
            rawCaptureGlFrameLabel = "hdr-proof",
            rawLibVlcHardwareMode = "full",
            rawLibVlcVoutMode = "android_display",
            rawLibVlcDisplayChroma = null,
        )

        assertEquals("hdr-proof", initialPendingLibVlcNativeSnapshotLabelFor(overrides))
    }

    @Test
    fun `playback debug override parser normalizes explicit libvlc display chroma`() {
        val overrides = resolveLaunchPlaybackDebugOverrides(
            rawForcedSignalKind = null,
            rawCaptureGlFrameLabel = "hdr-proof",
            rawLibVlcHardwareMode = "full",
            rawLibVlcVoutMode = "default",
            rawLibVlcDisplayChroma = "rv32",
        )

        assertEquals("RV32", overrides.libVlcDisplayChroma)
    }
}
