package com.miruplay.tv.player

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import com.miruplay.tv.model.DolbyVisionProfile
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.model.VideoTransferCharacteristic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSignalFormatResolverTest {
    @Test
    fun `resolver classifies hdr10 from st2084 bt2020 format`() {
        val descriptor = resolveVideoSignalDescriptor(
            Format.Builder()
                .setSampleMimeType("video/hevc")
                .setCodecs("hev1.2.4.L153")
                .setColorInfo(
                    ColorInfo.Builder()
                        .setColorSpace(C.COLOR_SPACE_BT2020)
                        .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                        .setLumaBitdepth(10)
                        .setChromaBitdepth(10)
                        .build()
                )
                .build()
        )

        assertEquals(VideoSignalKind.HDR10, descriptor.signalKind)
        assertEquals(VideoTransferCharacteristic.PQ, descriptor.transfer)
        assertNull(descriptor.dolbyVisionProfile)
    }

    @Test
    fun `resolver classifies hdr10 plus from metadata marker`() {
        val descriptor = resolveVideoSignalDescriptor(
            Format.Builder()
                .setSampleMimeType("video/hevc")
                .setCodecs("hev1.2.4.L153+hdr10plus")
                .setColorInfo(
                    ColorInfo.Builder()
                        .setColorSpace(C.COLOR_SPACE_BT2020)
                        .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                        .setLumaBitdepth(10)
                        .setChromaBitdepth(10)
                        .build()
                )
                .build()
        )

        assertEquals(VideoSignalKind.HDR10_PLUS, descriptor.signalKind)
    }

    @Test
    fun `resolver classifies dolby vision and profile from codec string`() {
        val descriptor = resolveVideoSignalDescriptor(
            Format.Builder()
                .setSampleMimeType("video/dolby-vision")
                .setCodecs("dvhe.08.04")
                .setColorInfo(
                    ColorInfo.Builder()
                        .setColorSpace(C.COLOR_SPACE_BT2020)
                        .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                        .setLumaBitdepth(10)
                        .setChromaBitdepth(10)
                        .build()
                )
                .build()
        )

        assertEquals(VideoSignalKind.DOLBY_VISION, descriptor.signalKind)
        assertEquals(DolbyVisionProfile.PROFILE_8_1, descriptor.dolbyVisionProfile)
        assertEquals("04", descriptor.dolbyVisionLevel)
    }

    @Test
    fun `resolver keeps unknown hdr when transfer is hdr but format family is unclear`() {
        val descriptor = resolveVideoSignalDescriptor(
            Format.Builder()
                .setSampleMimeType("video/x-custom")
                .setCodecs("customhdr")
                .setColorInfo(
                    ColorInfo.Builder()
                        .setColorSpace(C.COLOR_SPACE_BT2020)
                        .setColorTransfer(C.COLOR_TRANSFER_HLG)
                        .setLumaBitdepth(10)
                        .setChromaBitdepth(10)
                        .build()
                )
                .build()
        )

        assertEquals(VideoSignalKind.UNKNOWN_HDR, descriptor.signalKind)
        assertEquals(VideoTransferCharacteristic.HLG, descriptor.transfer)
    }

    @Test
    fun `resolver classifies pq h264 high10 stream as hdr10`() {
        val descriptor = resolveVideoSignalDescriptor(
            Format.Builder()
                .setSampleMimeType("video/avc")
                .setCodecs("avc1.6e0028")
                .setColorInfo(
                    ColorInfo.Builder()
                        .setColorSpace(C.COLOR_SPACE_BT2020)
                        .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                        .setLumaBitdepth(10)
                        .setChromaBitdepth(10)
                        .build()
                )
                .build()
        )

        assertEquals(VideoSignalKind.HDR10, descriptor.signalKind)
        assertEquals(VideoTransferCharacteristic.PQ, descriptor.transfer)
    }

    @Test
    fun `resolver treats bt709 sdr as sdr`() {
        val descriptor = resolveVideoSignalDescriptor(
            Format.Builder()
                .setSampleMimeType("video/avc")
                .setCodecs("avc1.640028")
                .setColorInfo(
                    ColorInfo.Builder()
                        .setColorSpace(C.COLOR_SPACE_BT709)
                        .setColorTransfer(C.COLOR_TRANSFER_SDR)
                        .setLumaBitdepth(8)
                        .setChromaBitdepth(8)
                        .build()
                )
                .build()
        )

        assertEquals(VideoSignalKind.SDR, descriptor.signalKind)
        assertEquals(8, descriptor.bitDepth)
    }

    @Test
    fun `merge prefers dolby vision hint when runtime format is empty`() {
        val merged = mergeVideoSignalDescriptor(
            runtimeDescriptor = VideoSignalDescriptor(),
            containerHint = VideoSignalDescriptor(
                signalKind = VideoSignalKind.DOLBY_VISION,
                codecId = "dvhe.05.06",
                dolbyVisionProfile = DolbyVisionProfile.PROFILE_5,
                dolbyVisionLevel = "06",
            ),
        )

        assertEquals(VideoSignalKind.DOLBY_VISION, merged.signalKind)
        assertEquals(DolbyVisionProfile.PROFILE_5, merged.dolbyVisionProfile)
        assertEquals("06", merged.dolbyVisionLevel)
        assertEquals("dvhe.05.06", merged.codecId)
    }
}
