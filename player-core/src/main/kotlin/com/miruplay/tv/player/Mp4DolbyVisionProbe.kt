package com.miruplay.tv.player

import android.content.Context
import android.net.Uri
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.mediasource.WebDavHttpStatusException
import com.miruplay.tv.mediasource.WebDavRequest
import com.miruplay.tv.mediasource.WebDavRequestCoordinator
import com.miruplay.tv.mediasource.WebDavRequestKind
import com.miruplay.tv.mediasource.WebDavTransportResult
import com.miruplay.tv.model.DolbyVisionProfile
import com.miruplay.tv.model.VideoColorPrimaries
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.model.VideoTransferCharacteristic
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

internal suspend fun probeContainerVideoSignalDescriptor(
    context: Context,
    uri: String,
    httpConfig: PlaybackHttpRequestConfig,
    maxProbeBytes: Int = DEFAULT_PROBE_BYTES,
): VideoSignalDescriptor? {
    if (!uri.trim().substringBefore('?').endsWith(".mp4", ignoreCase = true)) {
        return null
    }

    return runCatching {
        val initialProbe = readProbeBytes(
            context = context,
            uri = uri,
            httpConfig = httpConfig,
            maxProbeBytes = maxProbeBytes,
            startOffset = 0L,
        ) ?: return@runCatching null

        parseMp4DolbyVisionDescriptor(initialProbe)
            ?: findAdditionalMp4ProbeOffsets(
                bytes = initialProbe,
                probeStartOffset = 0L,
            ).asSequence()
                .take(MAX_ADDITIONAL_PROBE_WINDOWS)
                .mapNotNull { startOffset ->
                    readProbeBytes(
                        context = context,
                        uri = uri,
                        httpConfig = httpConfig,
                        maxProbeBytes = maxProbeBytes,
                        startOffset = startOffset,
                    )
                }
                .mapNotNull(::parseMp4DolbyVisionDescriptor)
                .firstOrNull()
    }.onFailure { error ->
        MiruLog.w(
            "Mp4DolbyVisionProbe",
            "Container Dolby Vision probe failed",
            error,
            mapOf("source_uri" to uri),
        )
    }.getOrNull()
}

internal fun mergeVideoSignalDescriptor(
    runtimeDescriptor: VideoSignalDescriptor,
    containerHint: VideoSignalDescriptor?,
): VideoSignalDescriptor {
    val hint = containerHint ?: return runtimeDescriptor
    val useDolbyVisionHint = hint.signalKind == VideoSignalKind.DOLBY_VISION &&
        runtimeDescriptor.signalKind != VideoSignalKind.DOLBY_VISION
    val useHdrHint = runtimeDescriptor.signalKind == VideoSignalKind.SDR &&
        hint.signalKind != VideoSignalKind.SDR

    if (!useDolbyVisionHint && !useHdrHint) {
        return runtimeDescriptor
    }

    return runtimeDescriptor.copy(
        signalKind = hint.signalKind,
        transfer = runtimeDescriptor.transfer.takeIfMeaningfulTransfer() ?: hint.transfer,
        colorPrimaries = runtimeDescriptor.colorPrimaries.takeIfMeaningfulPrimaries() ?: hint.colorPrimaries,
        bitDepth = maxOf(runtimeDescriptor.bitDepth, hint.bitDepth),
        codecId = hint.codecId.ifBlank { runtimeDescriptor.codecId },
        dolbyVisionProfile = hint.dolbyVisionProfile ?: runtimeDescriptor.dolbyVisionProfile,
        dolbyVisionLevel = hint.dolbyVisionLevel ?: runtimeDescriptor.dolbyVisionLevel,
        hasHdrStaticMetadata = runtimeDescriptor.hasHdrStaticMetadata || hint.hasHdrStaticMetadata,
        hasHdr10PlusMetadata = runtimeDescriptor.hasHdr10PlusMetadata || hint.hasHdr10PlusMetadata,
    )
}

internal fun findAdditionalMp4ProbeOffsets(
    bytes: ByteArray,
    probeStartOffset: Long,
): List<Long> {
    if (probeStartOffset != 0L || bytes.size < BOX_HEADER_BYTES) {
        return emptyList()
    }

    val probeEndOffset = probeStartOffset + bytes.size
    var offset = 0
    while (offset + BOX_HEADER_BYTES <= bytes.size) {
        val header = readMp4BoxHeader(bytes, offset)
        if (header == null) {
            offset += 1
            continue
        }
        val absoluteBoxOffset = probeStartOffset + offset
        val nextAbsoluteOffset = absoluteBoxOffset + header.boxSize
        if (nextAbsoluteOffset > probeEndOffset) {
            return listOf(nextAbsoluteOffset)
        }
        offset += header.headerSize + header.payloadSize.toInt()
    }
    return emptyList()
}

internal fun parseMp4DolbyVisionDescriptor(bytes: ByteArray): VideoSignalDescriptor? {
    parseMp4DolbyVisionBoxes(
        bytes = bytes,
        startOffset = 0,
        endOffsetExclusive = bytes.size,
    )?.let { return it }

    var offset = 0
    while (offset + BOX_HEADER_BYTES <= bytes.size) {
        val header = readMp4BoxHeader(bytes, offset)
        if (header == null) {
            offset += 1
            continue
        }
        val boxSize = header.boxSize
        val boxType = bytes.decodeToString(
            startIndex = offset + BOX_SIZE_BYTES,
            endIndex = offset + BOX_SIZE_BYTES + BOX_TYPE_BYTES,
        )
        if (boxType in DOLBY_VISION_BOX_TYPES && boxSize >= (header.headerSize + DV_MIN_PAYLOAD_BYTES).toLong()) {
            val payloadOffset = offset + header.headerSize
            if (payloadOffset + DV_MIN_PAYLOAD_BYTES > bytes.size) {
                return null
            }
            return parseDvConfigurationRecord(bytes, payloadOffset)
        }

        val nextOffset = offset.toLong() + boxSize
        offset += when {
            boxSize < header.headerSize.toLong() -> 1
            nextOffset > bytes.size.toLong() -> 1
            else -> boxSize.toInt()
        }
    }
    return null
}

private fun parseMp4DolbyVisionBoxes(
    bytes: ByteArray,
    startOffset: Int,
    endOffsetExclusive: Int,
): VideoSignalDescriptor? {
    var offset = startOffset
    while (offset + BOX_HEADER_BYTES <= endOffsetExclusive) {
        val header = readMp4BoxHeader(bytes, offset)
        if (header == null) {
            offset += 1
            continue
        }

        val boxType = bytes.decodeToString(
            startIndex = offset + BOX_SIZE_BYTES,
            endIndex = offset + BOX_SIZE_BYTES + BOX_TYPE_BYTES,
        )
        if (boxType in DOLBY_VISION_BOX_TYPES && header.boxSize >= (header.headerSize + DV_MIN_PAYLOAD_BYTES).toLong()) {
            val payloadOffset = offset + header.headerSize
            if (payloadOffset + DV_MIN_PAYLOAD_BYTES > bytes.size) {
                return null
            }
            return parseDvConfigurationRecord(bytes, payloadOffset)
        }

        val availableBoxEndExclusive = minOf(
            endOffsetExclusive.toLong(),
            bytes.size.toLong(),
            offset.toLong() + header.boxSize,
        ).toInt()
        childScanRange(
            boxType = boxType,
            payloadOffset = offset + header.headerSize,
            availableBoxEndExclusive = availableBoxEndExclusive,
        )?.let { (childStart, childEndExclusive) ->
            parseMp4DolbyVisionBoxes(
                bytes = bytes,
                startOffset = childStart,
                endOffsetExclusive = childEndExclusive,
            )?.let { return it }
        }

        val nextOffset = offset.toLong() + header.boxSize
        offset += when {
            header.boxSize < header.headerSize.toLong() -> 1
            nextOffset > endOffsetExclusive.toLong() -> break
            else -> header.boxSize.toInt()
        }
    }

    return null
}

private fun childScanRange(
    boxType: String,
    payloadOffset: Int,
    availableBoxEndExclusive: Int,
): Pair<Int, Int>? {
    val childStart = when (boxType) {
        in DIRECT_CHILD_CONTAINER_BOX_TYPES -> payloadOffset
        MP4_STSD_BOX_TYPE -> payloadOffset + STSD_PREFIX_BYTES
        in VIDEO_SAMPLE_ENTRY_BOX_TYPES -> payloadOffset + VISUAL_SAMPLE_ENTRY_PREFIX_BYTES
        else -> return null
    }
    return if (childStart + BOX_HEADER_BYTES <= availableBoxEndExclusive) {
        childStart to availableBoxEndExclusive
    } else {
        null
    }
}

private fun parseDvConfigurationRecord(
    bytes: ByteArray,
    payloadOffset: Int,
): VideoSignalDescriptor {
    val packed = ((bytes[payloadOffset + 2].toInt() and 0xFF) shl 8) or
        (bytes[payloadOffset + 3].toInt() and 0xFF)
    val profileCode = (packed shr 9) and 0x7F
    val levelCode = (packed shr 3) and 0x3F
    val compatibilityId = (bytes[payloadOffset + 4].toInt() ushr 4) and 0x0F
    val profile = dolbyVisionProfileFromRecord(
        profileCode = profileCode,
        compatibilityId = compatibilityId,
    )

    return VideoSignalDescriptor(
        signalKind = VideoSignalKind.DOLBY_VISION,
        transfer = VideoTransferCharacteristic.PQ,
        colorPrimaries = VideoColorPrimaries.BT2020,
        bitDepth = 10,
        codecId = "dvhe.${profileCode.toTwoDigitCode()}.${levelCode.toTwoDigitCode()}",
        dolbyVisionProfile = profile,
        dolbyVisionLevel = levelCode.toTwoDigitCode(),
        hasHdrStaticMetadata = true,
    )
}

private fun dolbyVisionProfileFromRecord(
    profileCode: Int,
    compatibilityId: Int,
): DolbyVisionProfile =
    when (profileCode) {
        4 -> DolbyVisionProfile.PROFILE_4
        5 -> DolbyVisionProfile.PROFILE_5
        7 -> DolbyVisionProfile.PROFILE_7
        8 -> when (compatibilityId) {
            1 -> DolbyVisionProfile.PROFILE_8_1
            4 -> DolbyVisionProfile.PROFILE_8_4
            else -> DolbyVisionProfile.PROFILE_8
        }
        9 -> DolbyVisionProfile.PROFILE_9
        else -> DolbyVisionProfile.UNKNOWN
    }

private fun readProbeBytes(
    context: Context,
    uri: String,
    httpConfig: PlaybackHttpRequestConfig,
    maxProbeBytes: Int,
    startOffset: Long,
): ByteArray? =
    when {
        uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) -> {
            readHttpProbeBytes(
                uri = uri,
                httpConfig = httpConfig,
                maxProbeBytes = maxProbeBytes,
                startOffset = startOffset,
            )
        }
        uri.startsWith("content://", ignoreCase = true) -> {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { inputStream ->
                    inputStream.channel.position(descriptor.startOffset + startOffset)
                    inputStream.readPrefix(maxProbeBytes)
                }
            }
        }
        uri.startsWith("file://", ignoreCase = true) -> {
            readFileProbeBytes(
                file = File(Uri.parse(uri).path.orEmpty()),
                maxProbeBytes = maxProbeBytes,
                startOffset = startOffset,
            )
        }
        else -> {
            readFileProbeBytes(
                file = File(uri),
                maxProbeBytes = maxProbeBytes,
                startOffset = startOffset,
            )
        }
    }

private fun readHttpProbeBytes(
    uri: String,
    httpConfig: PlaybackHttpRequestConfig,
    maxProbeBytes: Int,
    startOffset: Long,
): ByteArray? {
    val openConnection = {
        (URL(uri).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "GET"
            connectTimeout = HTTP_TIMEOUT_MS
            readTimeout = HTTP_TIMEOUT_MS
            setRequestProperty("Range", "bytes=$startOffset-${startOffset + maxProbeBytes - 1}")
            httpConfig.headersFor(uri).forEach { (key, value) ->
                setRequestProperty(key, value)
            }
        }
    }
    if (!httpConfig.isWebDav(uri)) {
        return openConnection().useConnection { connection ->
            connection.inputStream.use { stream -> stream.readPrefix(maxProbeBytes) }
        }
    }
    val lease = WebDavRequestCoordinator.execute(
        WebDavRequest(
            method = "GET",
            url = uri,
            kind = WebDavRequestKind.RANGE,
            streaming = true,
        ),
    ) {
        val connection = openConnection()
        val statusCode = connection.responseCode
        if (statusCode >= 400 && statusCode != 405) {
            connection.disconnect()
            throw WebDavHttpStatusException(statusCode)
        }
        WebDavTransportResult(connection, statusCode, connection::disconnect)
    }
    return lease.use {
        it.value.inputStream.use { stream -> stream.readPrefix(maxProbeBytes) }
    }
}

private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T =
    try {
        block(this)
    } finally {
        disconnect()
    }

private fun readFileProbeBytes(
    file: File,
    maxProbeBytes: Int,
    startOffset: Long,
): ByteArray? {
    if (!file.exists() || startOffset < 0L) {
        return null
    }
    return RandomAccessFile(file, "r").use { input ->
        if (startOffset >= input.length()) {
            return null
        }
        input.seek(startOffset)
        val bytesToRead = minOf(maxProbeBytes.toLong(), input.length() - startOffset).toInt()
        ByteArray(bytesToRead).also { buffer ->
            input.readFully(buffer)
        }
    }
}

private fun InputStream.readPrefix(maxProbeBytes: Int): ByteArray {
    val buffer = ByteArray(maxProbeBytes)
    var total = 0
    while (total < maxProbeBytes) {
        val read = read(buffer, total, maxProbeBytes - total)
        if (read <= 0) break
        total += read
    }
    return buffer.copyOf(total)
}

private fun readUInt32(bytes: ByteArray, offset: Int): Long =
    ((bytes[offset].toLong() and 0xFF) shl 24) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
        (bytes[offset + 3].toLong() and 0xFF)

private fun readUInt64(bytes: ByteArray, offset: Int): Long =
    ((bytes[offset].toLong() and 0xFF) shl 56) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 48) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 40) or
        ((bytes[offset + 3].toLong() and 0xFF) shl 32) or
        ((bytes[offset + 4].toLong() and 0xFF) shl 24) or
        ((bytes[offset + 5].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 6].toLong() and 0xFF) shl 8) or
        (bytes[offset + 7].toLong() and 0xFF)

private fun readMp4BoxHeader(bytes: ByteArray, offset: Int): Mp4BoxHeader? {
    val rawSize = readUInt32(bytes, offset)
    val headerSize = when (rawSize) {
        MP4_EXTENDED_SIZE_MARKER -> EXTENDED_BOX_HEADER_BYTES
        else -> BOX_HEADER_BYTES
    }
    if (offset + headerSize > bytes.size) {
        return null
    }
    val boxSize = when (rawSize) {
        MP4_EXTENDED_SIZE_MARKER -> readUInt64(bytes, offset + BOX_HEADER_BYTES)
        MP4_BOX_EXTENDS_TO_EOF -> return null
        else -> rawSize
    }
    val payloadSize = boxSize - headerSize
    if (payloadSize < 0) {
        return null
    }
    return Mp4BoxHeader(
        boxSize = boxSize,
        headerSize = headerSize,
        payloadSize = payloadSize,
    )
}

private fun VideoTransferCharacteristic.takeIfMeaningfulTransfer(): VideoTransferCharacteristic? =
    takeIf { it != VideoTransferCharacteristic.SDR && it != VideoTransferCharacteristic.UNKNOWN }

private fun VideoColorPrimaries.takeIfMeaningfulPrimaries(): VideoColorPrimaries? =
    takeIf { it != VideoColorPrimaries.BT709 && it != VideoColorPrimaries.UNKNOWN }

private fun Int.toTwoDigitCode(): String = toString().padStart(2, '0')

private const val DEFAULT_PROBE_BYTES = 256 * 1024
private const val BOX_SIZE_BYTES = 4
private const val BOX_TYPE_BYTES = 4
private const val BOX_HEADER_BYTES = 8
private const val EXTENDED_BOX_HEADER_BYTES = 16
private const val STSD_PREFIX_BYTES = 8
private const val VISUAL_SAMPLE_ENTRY_PREFIX_BYTES = 78
private const val DV_MIN_PAYLOAD_BYTES = 5
private const val HTTP_TIMEOUT_MS = 8_000
private const val MAX_ADDITIONAL_PROBE_WINDOWS = 1
private const val MP4_EXTENDED_SIZE_MARKER = 1L
private const val MP4_BOX_EXTENDS_TO_EOF = 0L
private const val MP4_STSD_BOX_TYPE = "stsd"
private val DOLBY_VISION_BOX_TYPES = setOf("dvcC", "dvvC", "dvwC")
private val DIRECT_CHILD_CONTAINER_BOX_TYPES = setOf(
    "moov",
    "trak",
    "mdia",
    "minf",
    "stbl",
    "edts",
    "dinf",
)
private val VIDEO_SAMPLE_ENTRY_BOX_TYPES = setOf(
    "hev1",
    "hvc1",
    "dvhe",
    "dvh1",
    "encv",
)

private data class Mp4BoxHeader(
    val boxSize: Long,
    val headerSize: Int,
    val payloadSize: Long,
)
