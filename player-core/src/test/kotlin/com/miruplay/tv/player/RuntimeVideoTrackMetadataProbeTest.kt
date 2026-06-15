package com.miruplay.tv.player

import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.miruplay.tv.model.VideoColorPrimaries
import com.miruplay.tv.model.VideoTransferCharacteristic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeVideoTrackMetadataProbeTest {
    @Test
    fun `extractor snapshot infers hdr10 plus metadata and 10 bit hevc`() {
        val metadata = runtimeVideoTrackMetadataFromExtractorTrackFormat(
            ExtractorVideoTrackFormat(
                sampleMimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
                colorStandard = MediaFormat.COLOR_STANDARD_BT2020,
                colorTransfer = MediaFormat.COLOR_TRANSFER_ST2084,
                codecProfile = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus,
                hdrStaticInfoPresent = true,
                hdr10PlusInfoPresent = true,
            )
        )

        assertEquals(VideoTransferCharacteristic.PQ, metadata?.transfer)
        assertEquals(VideoColorPrimaries.BT2020, metadata?.colorPrimaries)
        assertEquals(10, metadata?.bitDepth)
        assertTrue(metadata?.hasHdr10PlusMetadata == true)
    }

    @Test
    fun `extractor snapshot infers avc high10 bit depth from codec profile`() {
        val metadata = runtimeVideoTrackMetadataFromExtractorTrackFormat(
            ExtractorVideoTrackFormat(
                sampleMimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
                colorStandard = MediaFormat.COLOR_STANDARD_BT2020,
                colorTransfer = MediaFormat.COLOR_TRANSFER_ST2084,
                codecProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10,
            )
        )

        assertEquals(VideoTransferCharacteristic.PQ, metadata?.transfer)
        assertEquals(VideoColorPrimaries.BT2020, metadata?.colorPrimaries)
        assertEquals(10, metadata?.bitDepth)
        assertFalse(metadata?.hasHdr10PlusMetadata == true)
    }

    @Test
    fun `extractor snapshot keeps sdr defaults for bt709 avc`() {
        val metadata = runtimeVideoTrackMetadataFromExtractorTrackFormat(
            ExtractorVideoTrackFormat(
                sampleMimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
                colorStandard = MediaFormat.COLOR_STANDARD_BT709,
                colorTransfer = MediaFormat.COLOR_TRANSFER_SDR_VIDEO,
                codecProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
            )
        )

        assertEquals(VideoTransferCharacteristic.SDR, metadata?.transfer)
        assertEquals(VideoColorPrimaries.BT709, metadata?.colorPrimaries)
        assertEquals(8, metadata?.bitDepth)
        assertFalse(metadata?.hasHdrStaticMetadata == true)
        assertFalse(metadata?.hasHdr10PlusMetadata == true)
    }
}
