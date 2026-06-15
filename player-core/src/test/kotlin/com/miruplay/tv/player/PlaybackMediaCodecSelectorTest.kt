package com.miruplay.tv.player

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackMediaCodecSelectorTest {

    @Test
    fun `hevc decoders prefer vendor c2 before software and legacy omx`() {
        val ordered = listOf(
            "OMX.qcom.video.decoder.hevc",
            "c2.android.hevc.decoder",
            "c2.qti.hevc.decoder",
        ).sortedBy { playbackDecoderPriority(MimeTypes.VIDEO_H265, it) }

        assertEquals(
            listOf(
                "c2.qti.hevc.decoder",
                "c2.android.hevc.decoder",
                "OMX.qcom.video.decoder.hevc",
            ),
            ordered,
        )
    }

    @Test
    fun `non hevc mime keeps neutral priority`() {
        assertEquals(
            Int.MAX_VALUE,
            playbackDecoderPriority(MimeTypes.VIDEO_AV1, "c2.android.av1.decoder"),
        )
    }

    @Test
    fun `hdr software preference moves android hevc decoder ahead of vendor decoder`() {
        val ordered = listOf(
            "c2.rk.hevc.decoder",
            "c2.android.hevc.decoder",
            "OMX.google.hevc.decoder",
        ).sortedBy {
            playbackDecoderPriority(
                MimeTypes.VIDEO_H265,
                it,
                PlaybackDecoderPreference.PREFER_SOFTWARE_HEVC_FOR_HDR,
            )
        }

        assertEquals(
            listOf(
                "c2.android.hevc.decoder",
                "OMX.google.hevc.decoder",
                "c2.rk.hevc.decoder",
            ),
            ordered,
        )
    }

    @Test
    fun `software video preference moves android avc decoder ahead of vendor decoder`() {
        val ordered = listOf(
            "c2.rk.avc.decoder",
            "c2.android.avc.decoder",
            "OMX.google.h264.decoder",
        ).sortedBy {
            playbackDecoderPriority(
                MimeTypes.VIDEO_H264,
                it,
                PlaybackDecoderPreference.PREFER_SOFTWARE_VIDEO_FOR_HDR,
            )
        }

        assertEquals(
            listOf(
                "c2.android.avc.decoder",
                "OMX.google.h264.decoder",
                "c2.rk.avc.decoder",
            ),
            ordered,
        )
    }
}
