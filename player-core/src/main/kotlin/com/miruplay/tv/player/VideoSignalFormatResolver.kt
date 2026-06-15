package com.miruplay.tv.player

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import com.miruplay.tv.model.DolbyVisionProfile
import com.miruplay.tv.model.VideoColorPrimaries
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.model.VideoTransferCharacteristic

internal data class RuntimeVideoTrackMetadata(
    val sampleMimeType: String = "",
    val codecId: String = "",
    val transfer: VideoTransferCharacteristic = VideoTransferCharacteristic.UNKNOWN,
    val colorPrimaries: VideoColorPrimaries = VideoColorPrimaries.UNKNOWN,
    val bitDepth: Int = 8,
    val hasHdrStaticMetadata: Boolean = false,
    val hasHdr10PlusMetadata: Boolean = false,
)

fun resolveVideoSignalDescriptor(format: Format?): VideoSignalDescriptor {
    if (format == null) {
        return VideoSignalDescriptor()
    }

    return resolveVideoSignalDescriptor(
        RuntimeVideoTrackMetadata(
            sampleMimeType = format.sampleMimeType.orEmpty(),
            codecId = format.codecs.orEmpty(),
            transfer = resolveTransfer(format.colorInfo?.colorTransfer),
            colorPrimaries = resolveColorPrimaries(format.colorInfo?.colorSpace),
            bitDepth = resolveBitDepth(format.colorInfo),
            hasHdrStaticMetadata = format.colorInfo?.hdrStaticInfo?.isNotEmpty() == true,
            hasHdr10PlusMetadata = format.codecs.orEmpty().contains("hdr10+", ignoreCase = true) ||
                format.codecs.orEmpty().contains("hdr10plus", ignoreCase = true),
        )
    )
}

internal fun resolveVideoSignalDescriptor(metadata: RuntimeVideoTrackMetadata?): VideoSignalDescriptor {
    if (metadata == null) {
        return VideoSignalDescriptor()
    }

    val codecId = metadata.codecId
    val transfer = metadata.transfer
    val colorPrimaries = metadata.colorPrimaries
    val bitDepth = metadata.bitDepth
    val dolbyVisionProfile = DolbyVisionProfile.fromCodecString(codecId)
    val dolbyVisionLevel = DolbyVisionProfile.levelFromCodecString(codecId)
    val sampleMimeType = metadata.sampleMimeType
    val hasHdr10PlusMarker = metadata.hasHdr10PlusMetadata ||
        codecId.contains("hdr10+", ignoreCase = true) ||
        codecId.contains("hdr10plus", ignoreCase = true)
    val signalKind = resolveSignalKind(
        sampleMimeType = sampleMimeType,
        codecId = codecId,
        transfer = transfer,
        dolbyVisionProfile = dolbyVisionProfile,
        hasHdr10PlusMarker = hasHdr10PlusMarker,
    )

    return VideoSignalDescriptor(
        signalKind = signalKind,
        transfer = transfer,
        colorPrimaries = colorPrimaries,
        bitDepth = bitDepth,
        codecId = codecId,
        dolbyVisionProfile = dolbyVisionProfile,
        dolbyVisionLevel = dolbyVisionLevel,
        hasHdrStaticMetadata = metadata.hasHdrStaticMetadata,
        hasHdr10PlusMetadata = hasHdr10PlusMarker,
    )
}

private fun resolveSignalKind(
    sampleMimeType: String,
    codecId: String,
    transfer: VideoTransferCharacteristic,
    dolbyVisionProfile: DolbyVisionProfile?,
    hasHdr10PlusMarker: Boolean,
): VideoSignalKind {
    if (sampleMimeType.equals("video/dolby-vision", ignoreCase = true) || dolbyVisionProfile != null) {
        return VideoSignalKind.DOLBY_VISION
    }
    if (hasHdr10PlusMarker) {
        return VideoSignalKind.HDR10_PLUS
    }
    return when {
        transfer == VideoTransferCharacteristic.PQ &&
            (
                sampleMimeType.contains("hevc", ignoreCase = true) ||
                    sampleMimeType.contains("avc", ignoreCase = true) ||
                    codecId.contains("hev", ignoreCase = true) ||
                    codecId.contains("avc", ignoreCase = true)
                ) -> {
            VideoSignalKind.HDR10
        }
        transfer == VideoTransferCharacteristic.PQ || transfer == VideoTransferCharacteristic.HLG -> {
            VideoSignalKind.UNKNOWN_HDR
        }
        else -> VideoSignalKind.SDR
    }
}

private fun resolveTransfer(colorTransfer: Int?): VideoTransferCharacteristic =
    when (colorTransfer) {
        C.COLOR_TRANSFER_ST2084 -> VideoTransferCharacteristic.PQ
        C.COLOR_TRANSFER_HLG -> VideoTransferCharacteristic.HLG
        C.COLOR_TRANSFER_SRGB -> VideoTransferCharacteristic.SRGB
        C.COLOR_TRANSFER_GAMMA_2_2 -> VideoTransferCharacteristic.GAMMA_22
        C.COLOR_TRANSFER_SDR -> VideoTransferCharacteristic.SDR
        else -> VideoTransferCharacteristic.UNKNOWN
    }

private fun resolveColorPrimaries(colorSpace: Int?): VideoColorPrimaries =
    when (colorSpace) {
        C.COLOR_SPACE_BT2020 -> VideoColorPrimaries.BT2020
        C.COLOR_SPACE_BT709 -> VideoColorPrimaries.BT709
        else -> VideoColorPrimaries.UNKNOWN
    }

private fun resolveBitDepth(colorInfo: ColorInfo?): Int =
    colorInfo?.lumaBitdepth
        ?.takeIf { it > 0 }
        ?: colorInfo?.chromaBitdepth?.takeIf { it > 0 }
        ?: 8
