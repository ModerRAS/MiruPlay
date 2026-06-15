package com.miruplay.tv.player

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalHdrSurfaceMediaCodecVideoRendererTest {
    @Test
    fun `dedicated gl pipeline keeps hdr decoder output for hdr color info on api 31 plus`() {
        val hdrFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .setLumaBitdepth(10)
                    .setChromaBitdepth(10)
                    .build(),
            )
            .build()

        assertFalse(
            shouldRequestDecoderToneMapToSdr(
                format = hdrFormat,
                experimentalVideoPipelineMode = ExperimentalVideoPipelineMode.DEDICATED_GL_SURFACE,
                sdkInt = 35,
            )
        )
    }

    @Test
    fun `media3 effects pipeline does not request decoder hdr to sdr`() {
        val hdrFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(C.COLOR_TRANSFER_HLG)
                    .build(),
            )
            .build()

        assertFalse(
            shouldRequestDecoderToneMapToSdr(
                format = hdrFormat,
                experimentalVideoPipelineMode = ExperimentalVideoPipelineMode.MEDIA3_EFFECTS,
                sdkInt = 35,
            )
        )
    }

    @Test
    fun `sdr format does not request decoder hdr to sdr`() {
        val sdrFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setColorInfo(ColorInfo.SDR_BT709_LIMITED)
            .build()

        assertFalse(
            shouldRequestDecoderToneMapToSdr(
                format = sdrFormat,
                experimentalVideoPipelineMode = ExperimentalVideoPipelineMode.DEDICATED_GL_SURFACE,
                sdkInt = 35,
            )
        )
    }

    @Test
    fun `dolby vision samples keep hdr decoder output even when color info is absent`() {
        val dolbyVisionFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .build()

        assertFalse(
            shouldRequestDecoderToneMapToSdr(
                format = dolbyVisionFormat,
                experimentalVideoPipelineMode = ExperimentalVideoPipelineMode.DEDICATED_GL_SURFACE,
                sdkInt = 35,
            )
        )
    }

    @Test
    fun `api 30 and below cannot request decoder hdr to sdr`() {
        val hdrFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setColorInfo(
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .build(),
            )
            .build()

        assertFalse(
            shouldRequestDecoderToneMapToSdr(
                format = hdrFormat,
                experimentalVideoPipelineMode = ExperimentalVideoPipelineMode.DEDICATED_GL_SURFACE,
                sdkInt = 30,
            )
        )
    }
}
