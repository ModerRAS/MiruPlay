package com.miruplay.tv.player

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.miruplay.tv.core.common.logging.MiruLog
import java.io.File

internal data class ExtractorVideoTrackFormat(
    val sampleMimeType: String = "",
    val codecId: String = "",
    val colorStandard: Int? = null,
    val colorTransfer: Int? = null,
    val codecProfile: Int? = null,
    val codecLevel: Int? = null,
    val hdrStaticInfoPresent: Boolean = false,
    val hdr10PlusInfoPresent: Boolean = false,
)

internal suspend fun probeRuntimeVideoTrackMetadata(
    context: Context,
    uri: String,
    httpConfig: PlaybackHttpRequestConfig,
): RuntimeVideoTrackMetadata? {
    if (httpConfig.isWebDav(uri)) return null
    return runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setPlaybackDataSource(context, uri, httpConfig)
            (0 until extractor.trackCount)
                .asSequence()
                .map(extractor::getTrackFormat)
                .firstOrNull { format ->
                    format.getString(MediaFormat.KEY_MIME)
                        ?.startsWith("video/", ignoreCase = true) == true
                }
                ?.toExtractorVideoTrackFormat()
                ?.let(::runtimeVideoTrackMetadataFromExtractorTrackFormat)
        } finally {
            extractor.release()
        }
    }.onFailure { error ->
        MiruLog.w(
            "RuntimeVideoTrackProbe",
            "Runtime video track probe failed",
            error,
            mapOf("source_uri" to uri),
        )
    }.getOrNull()
}

internal fun runtimeVideoTrackMetadataFromExtractorTrackFormat(
    format: ExtractorVideoTrackFormat,
): RuntimeVideoTrackMetadata {
    val sampleMimeType = format.sampleMimeType
    val codecId = format.codecId
    val transfer = resolveExtractorTransfer(format.colorTransfer)
    val colorPrimaries = resolveExtractorColorPrimaries(format.colorStandard)
    val bitDepth = inferBitDepth(
        sampleMimeType = sampleMimeType,
        codecId = codecId,
        codecProfile = format.codecProfile,
    )
    val hasHdr10PlusMetadata = format.hdr10PlusInfoPresent ||
        codecId.contains("hdr10+", ignoreCase = true) ||
        codecId.contains("hdr10plus", ignoreCase = true) ||
        isHdr10PlusProfile(
            sampleMimeType = sampleMimeType,
            codecProfile = format.codecProfile,
        )

    return RuntimeVideoTrackMetadata(
        sampleMimeType = sampleMimeType,
        codecId = codecId,
        transfer = transfer,
        colorPrimaries = colorPrimaries,
        bitDepth = bitDepth,
        hasHdrStaticMetadata = format.hdrStaticInfoPresent,
        hasHdr10PlusMetadata = hasHdr10PlusMetadata,
    )
}

private fun MediaExtractor.setPlaybackDataSource(
    context: Context,
    uri: String,
    httpConfig: PlaybackHttpRequestConfig,
) {
    when {
        uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) -> {
            setDataSource(uri, httpConfig.headersFor(uri))
        }
        uri.startsWith("content://", ignoreCase = true) -> {
            setDataSource(context, Uri.parse(uri), emptyMap())
        }
        uri.startsWith("file://", ignoreCase = true) -> {
            setDataSource(File(Uri.parse(uri).path.orEmpty()).absolutePath)
        }
        else -> {
            setDataSource(File(uri).absolutePath)
        }
    }
}

private fun MediaFormat.toExtractorVideoTrackFormat(): ExtractorVideoTrackFormat =
    ExtractorVideoTrackFormat(
        sampleMimeType = getString(MediaFormat.KEY_MIME).orEmpty(),
        codecId = getString(MediaFormat.KEY_CODECS_STRING).orEmpty(),
        colorStandard = getOptionalInteger(MediaFormat.KEY_COLOR_STANDARD),
        colorTransfer = getOptionalInteger(MediaFormat.KEY_COLOR_TRANSFER),
        codecProfile = getOptionalInteger(MediaFormat.KEY_PROFILE),
        codecLevel = getOptionalInteger(MediaFormat.KEY_LEVEL),
        hdrStaticInfoPresent = getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO) != null,
        hdr10PlusInfoPresent = getByteBuffer(MediaFormat.KEY_HDR10_PLUS_INFO) != null,
    )

private fun MediaFormat.getOptionalInteger(key: String): Int? =
    if (containsKey(key)) getInteger(key) else null

private fun resolveExtractorTransfer(colorTransfer: Int?): com.miruplay.tv.model.VideoTransferCharacteristic =
    when (colorTransfer) {
        MediaFormat.COLOR_TRANSFER_ST2084 -> com.miruplay.tv.model.VideoTransferCharacteristic.PQ
        MediaFormat.COLOR_TRANSFER_HLG -> com.miruplay.tv.model.VideoTransferCharacteristic.HLG
        MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> com.miruplay.tv.model.VideoTransferCharacteristic.SDR
        else -> com.miruplay.tv.model.VideoTransferCharacteristic.UNKNOWN
    }

private fun resolveExtractorColorPrimaries(colorStandard: Int?): com.miruplay.tv.model.VideoColorPrimaries =
    when (colorStandard) {
        MediaFormat.COLOR_STANDARD_BT2020 -> com.miruplay.tv.model.VideoColorPrimaries.BT2020
        MediaFormat.COLOR_STANDARD_BT709 -> com.miruplay.tv.model.VideoColorPrimaries.BT709
        else -> com.miruplay.tv.model.VideoColorPrimaries.UNKNOWN
    }

private fun inferBitDepth(
    sampleMimeType: String,
    codecId: String,
    codecProfile: Int?,
): Int {
    if (codecId.hasTenBitMarker()) {
        return 10
    }
    if (codecProfile == null) {
        return 8
    }
    val isTenBitProfile = when {
        sampleMimeType.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) ->
            codecProfile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10
        sampleMimeType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) ->
            codecProfile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                codecProfile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                codecProfile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
        sampleMimeType.equals(MediaFormat.MIMETYPE_VIDEO_AV1, ignoreCase = true) ->
            codecProfile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10 ||
                codecProfile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10 ||
                codecProfile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus
        sampleMimeType.equals(MediaFormat.MIMETYPE_VIDEO_VP9, ignoreCase = true) ->
            codecProfile == MediaCodecInfo.CodecProfileLevel.VP9Profile2 ||
                codecProfile == MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR ||
                codecProfile == MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus ||
                codecProfile == MediaCodecInfo.CodecProfileLevel.VP9Profile3 ||
                codecProfile == MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR ||
                codecProfile == MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR10Plus
        sampleMimeType.equals(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION, ignoreCase = true) -> true
        else -> false
    }
    return if (isTenBitProfile) 10 else 8
}

private fun isHdr10PlusProfile(
    sampleMimeType: String,
    codecProfile: Int?,
): Boolean =
    when {
        codecProfile == null -> false
        sampleMimeType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) ->
            codecProfile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
        sampleMimeType.equals(MediaFormat.MIMETYPE_VIDEO_AV1, ignoreCase = true) ->
            codecProfile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus
        sampleMimeType.equals(MediaFormat.MIMETYPE_VIDEO_VP9, ignoreCase = true) ->
            codecProfile == MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus ||
                codecProfile == MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR10Plus
        else -> false
    }

private fun String.hasTenBitMarker(): Boolean {
    val normalized = lowercase()
    return "main10" in normalized ||
        "high10" in normalized ||
        "profile2" in normalized ||
        "profile3" in normalized ||
        Regex("""(?<!\d)10(?!\d)""").containsMatchIn(normalized)
}
