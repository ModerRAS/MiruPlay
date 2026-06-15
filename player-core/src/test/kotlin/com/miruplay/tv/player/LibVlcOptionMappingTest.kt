package com.miruplay.tv.player

import com.miruplay.tv.model.PeakDetectionStrategy
import com.miruplay.tv.model.ToneMappingCurvePreset
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.model.VideoTransferCharacteristic
import com.miruplay.tv.model.defaultToneMappingRuleSet
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.libvlc.Media

class LibVlcOptionMappingTest {
    @Test
    fun `hdr10 rule emits vlc tone mapping options`() {
        val ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10).copy(
            curvePreset = ToneMappingCurvePreset.MOBIUS,
            peakDetectionStrategy = PeakDetectionStrategy.DYNAMIC,
        )

        val options = buildLibVlcOptionsForTest(
            ruleSet = ruleSet,
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10),
        )

        assertTrue(options.none { it.startsWith("--vout=") })
        assertTrue(options.contains("--target-prim=3"))
        assertTrue(options.contains("--target-trc=1"))
        assertTrue(options.contains("--gl-tone-mapping-function=4"))
        assertTrue(options.any { it.startsWith("--gl-tone-mapping-param=") })
        assertTrue(options.none { it.startsWith("--tone-mapping=") })
        assertTrue(options.none { it.startsWith("--tone-mapping-desat=") })
        assertTrue(options.none { it.startsWith("--android-display-chroma") })
        assertTrue(options.none { it == "RV32" })
        assertTrue(options.none { it == "RV16" })
    }

    @Test
    fun `hdr10 plus rule keeps tone mapping active without forcing hdr output target`() {
        val options = buildLibVlcOptionsForTest(
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10_PLUS),
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10_PLUS),
        )

        assertTrue(options.contains("--target-prim=3"))
        assertTrue(options.contains("--target-trc=1"))
        assertTrue(options.contains("--gl-tone-mapping-function=4"))
        assertTrue(options.any { it.startsWith("--gl-tone-mapping-param=") })
        assertTrue(options.none { it.startsWith("--tone-mapping-desat=") })
    }

    @Test
    fun `sdr rule keeps tone mapping disabled`() {
        val ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.SDR)

        val options = buildLibVlcOptionsForTest(
            ruleSet = ruleSet,
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.SDR),
        )

        assertTrue(options.contains("--target-trc=0"))
        assertTrue(options.contains("--target-prim=0"))
        assertTrue(options.none { it.startsWith("--gl-tone-mapping-function=") })
        assertTrue(options.none { it.startsWith("--gl-tone-mapping-param=") })
        assertTrue(options.none { it.startsWith("--vout=") })
        assertTrue(options.none { it.startsWith("--android-display-chroma") })
        assertTrue(options.none { it == "RV32" })
        assertTrue(options.none { it == "RV16" })
    }

    @Test
    fun `hdr bypass rule does not force hdr target output options`() {
        val ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10).copy(
            enabled = false,
            curvePreset = ToneMappingCurvePreset.PASSTHROUGH,
            peakDetectionStrategy = PeakDetectionStrategy.DISABLED,
            saturationRecovery = 0,
            contrastRecovery = 0,
            highlightCompression = 0,
        )

        val options = buildLibVlcOptionsForTest(
            ruleSet = ruleSet,
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10),
        )

        assertTrue(options.contains("--target-trc=0"))
        assertTrue(options.contains("--target-prim=0"))
        assertTrue(options.none { it.startsWith("--gl-tone-mapping-function=") })
        assertTrue(options.none { it.startsWith("--gl-tone-mapping-param=") })
        assertTrue(options.none { it.startsWith("--tone-mapping-desat=") })
    }

    @Test
    fun `android display debug override emits explicit vout option`() {
        val ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10)

        val options = buildLibVlcOptionsForTest(
            ruleSet = ruleSet,
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10),
            debugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.ANDROID_DISPLAY),
        )

        assertTrue(options.contains("--vout=android_display,none"))
    }

    @Test
    fun `android display debug override keeps startup options aligned with stock vlc while restoring baseline playback`() {
        val ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10)

        val options = buildLibVlcOptionsForTest(
            ruleSet = ruleSet,
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10),
            debugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.ANDROID_DISPLAY),
        )

        assertTrue(options.contains("--vout=android_display,none"))
        assertTrue(options.none { it.startsWith("--target-prim=") })
        assertTrue(options.none { it.startsWith("--target-trc=") })
        assertTrue(options.none { it.startsWith("--gl-tone-mapping-function=") })
        assertTrue(options.none { it.startsWith("--gl-tone-mapping-param=") })
    }

    @Test
    fun `direct texture debug override emits explicit gles2 vout option`() {
        val ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10)

        val options = buildLibVlcOptionsForTest(
            ruleSet = ruleSet,
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10),
            debugConfig = LibVlcDebugConfig(
                voutMode = enumValueOf<LibVlcVoutMode>("DIRECT_TEXTURE"),
            ),
        )

        assertTrue(options.contains("--vout=gles2,none"))
    }

    @Test
    fun `gl surface debug override keeps libvlc vout selection automatic and requests hdr passthrough target`() {
        val ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10)

        val options = buildLibVlcOptionsForTest(
            ruleSet = ruleSet,
            signalDescriptor = VideoSignalDescriptor(
                signalKind = VideoSignalKind.HDR10,
                transfer = VideoTransferCharacteristic.PQ,
            ),
            debugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.GL_SURFACE),
        )

        assertTrue(options.none { it.startsWith("--vout=") })
        assertTrue(options.contains("--target-trc=8"))
        assertTrue(options.contains("--target-prim=5"))
        assertTrue(options.none { it.startsWith("--gl-tone-mapping-function=") })
        assertTrue(options.none { it.startsWith("--gl-tone-mapping-param=") })
    }

    @Test
    fun `output callbacks debug override forces android display vout while keeping hdr passthrough target`() {
        val ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10)

        val options = buildLibVlcOptionsForTest(
            ruleSet = ruleSet,
            signalDescriptor = VideoSignalDescriptor(
                signalKind = VideoSignalKind.HDR10,
                transfer = VideoTransferCharacteristic.PQ,
            ),
            debugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.OUTPUT_CALLBACKS),
        )

        assertTrue(options.contains("--vout=android_display,none"))
        assertTrue(options.contains("--target-trc=8"))
        assertTrue(options.contains("--target-prim=5"))
        assertTrue(options.none { it.startsWith("--gl-tone-mapping-function=") })
        assertTrue(options.none { it.startsWith("--gl-tone-mapping-param=") })
    }

    @Test
    fun `output callbacks debug override preserves hlg target transfer for self managed hdr path`() {
        val ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.UNKNOWN_HDR)

        val options = buildLibVlcOptionsForTest(
            ruleSet = ruleSet,
            signalDescriptor = VideoSignalDescriptor(
                signalKind = VideoSignalKind.UNKNOWN_HDR,
                transfer = VideoTransferCharacteristic.HLG,
            ),
            debugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.OUTPUT_CALLBACKS),
        )

        assertTrue(options.contains("--target-trc=9"))
        assertTrue(options.contains("--target-prim=5"))
        assertTrue(options.none { it.startsWith("--gl-tone-mapping-function=") })
        assertTrue(options.none { it.startsWith("--gl-tone-mapping-param=") })
    }

    @Test
    fun `explicit display chroma debug override emits android display chroma option`() {
        val ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10)

        val options = buildLibVlcOptionsForTest(
            ruleSet = ruleSet,
            signalDescriptor = VideoSignalDescriptor(signalKind = VideoSignalKind.HDR10),
            debugConfig = LibVlcDebugConfig(displayChroma = "RV32"),
        )

        assertTrue(options.contains("--android-display-chroma=RV32"))
    }

    @Test
    fun `media options keep hardware decode enabled without forcing decode only rendering`() {
        val media = mockk<Media>(relaxed = true)

        applyLibVlcMediaOptionsForTest(
            media = media,
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10),
        )

        verify { media.setHWDecoderEnabled(true, true) }
        verify(exactly = 0) { media.addOption(":no-mediacodec-dr") }
        verify(exactly = 0) { media.addOption(":no-omxil-dr") }
    }

    @Test
    fun `decoding only debug override disables direct rendering`() {
        val media = mockk<Media>(relaxed = true)

        applyLibVlcMediaOptionsForTest(
            media = media,
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10),
            debugConfig = LibVlcDebugConfig(hwMode = LibVlcHardwareAccelerationMode.DECODING_ONLY),
        )

        verifyOrder {
            media.setHWDecoderEnabled(true, true)
            media.addOption(":no-mediacodec-dr")
            media.addOption(":no-omxil-dr")
        }
    }

    @Test
    fun `disabled hardware mode forces avcodec software decoding`() {
        val media = mockk<Media>(relaxed = true)

        applyLibVlcMediaOptionsForTest(
            media = media,
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10),
            debugConfig = LibVlcDebugConfig(hwMode = LibVlcHardwareAccelerationMode.DISABLED),
        )

        verifyOrder {
            media.setHWDecoderEnabled(false, false)
            media.addOption(":codec=avcodec")
            media.addOption(":avcodec-hw=none")
        }
    }

    @Test
    fun `output callbacks debug override keeps media vout selection on the libvlc instance options`() {
        val media = mockk<Media>(relaxed = true)

        applyLibVlcMediaOptionsForTest(
            media = media,
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10),
            debugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.OUTPUT_CALLBACKS),
        )

        verify(exactly = 0) { media.addOption(":vout=android_display") }
    }

    @Test
    fun `vmem probe debug override explicitly selects callback vout without forcing a window module`() {
        val media = mockk<Media>(relaxed = true)

        applyLibVlcMediaOptionsForTest(
            media = media,
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10),
            debugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.VMEM_PROBE),
        )

        verifyOrder {
            media.addOption(":vout=vmem")
            media.addOption(":dec-dev=none")
        }
        verify(exactly = 0) { media.addOption(match { it.startsWith(":window=") }) }
    }

    @Test
    fun `vmem stream debug override explicitly selects callback vout without forcing a window module`() {
        val media = mockk<Media>(relaxed = true)

        applyLibVlcMediaOptionsForTest(
            media = media,
            ruleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10),
            debugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.VMEM_STREAM),
        )

        verifyOrder {
            media.addOption(":vout=vmem")
            media.addOption(":dec-dev=none")
            media.setHWDecoderEnabled(false, false)
            media.addOption(":codec=avcodec")
            media.addOption(":avcodec-hw=none")
        }
        verify(exactly = 0) { media.addOption(match { it.startsWith(":window=") }) }
    }
}
