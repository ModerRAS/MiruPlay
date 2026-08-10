@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import androidx.media3.common.Format
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

internal sealed interface LibassPayload {
    data class Document(val bytes: ByteArray) : LibassPayload
    data class Event(val dialogueLine: String) : LibassPayload
}

internal fun decodeLibassPayload(
    sample: ByteArray,
    sampleTimeUs: Long,
): LibassPayload? {
    val inflated = inflateSubtitleSampleIfNeeded(sample)
    if (inflated.isEmpty() || inflated.size > MAX_LIBASS_SAMPLE_BYTES) return null
    val text = inflated.decodeUtf8Strict() ?: return null
    if (text.isAssDocument()) return LibassPayload.Document(inflated)

    val dialogue = text
        .trimEnd('\u0000', '\r', '\n')
        .takeIf { it.startsWith("Dialogue:", ignoreCase = true) }
        ?: return null
    val fields = dialogue.substringAfter(':').trimStart().split(',', limit = MEDIA3_ASS_FIELD_COUNT)
    if (fields.size != MEDIA3_ASS_FIELD_COUNT) return null

    val relativeStartMs = parseAssTimeMs(fields[0]) ?: return null
    val relativeEndMs = parseAssTimeMs(fields[1]) ?: return null
    val durationMs = relativeEndMs - relativeStartMs
    if (durationMs <= 0L) return null

    val startMs = sampleTimeUs.coerceAtLeast(0L) / 1_000L
    val eventFields = buildList(MEDIA3_ASS_FIELD_COUNT - 1) {
        add(fields[3])
        add(formatAssTime(startMs))
        add(formatAssTime(startMs + durationMs))
        addAll(fields.subList(4, fields.size))
    }
    return LibassPayload.Event("Dialogue: ${eventFields.joinToString(",")}")
}

internal fun relativeLibassSampleTimeUs(
    sampleTimeUs: Long,
    streamOffsetUs: Long,
): Long = sampleTimeUs - streamOffsetUs

internal fun assHeaderFrom(format: Format): ByteArray? {
    val header = format.initializationData
        .firstOrNull { bytes ->
            bytes.decodeUtf8Strict()?.let(String::isAssDocument) == true
        }
        ?: return null
    val text = header.decodeUtf8Strict() ?: return null
    if (text.contains("[Events]", ignoreCase = true)) return header.copyOf()
    val separator = if (text.endsWith('\n') || text.endsWith('\r')) "" else "\n"
    return buildString {
        append(text)
        append(separator)
        appendLine("[Events]")
        appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
    }.toByteArray()
}

private fun ByteArray.decodeUtf8Strict(): String? = runCatching {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(this))
        .toString()
}.getOrNull()

private fun String.isAssDocument(): Boolean {
    val normalized = trimStart()
    return normalized.startsWith("[Script Info]", ignoreCase = true) ||
        normalized.startsWith("[V4 Styles]", ignoreCase = true) ||
        normalized.startsWith("[V4+ Styles]", ignoreCase = true)
}

private fun parseAssTimeMs(value: String): Long? {
    val match = ASS_TIME.matchEntire(value.trim()) ?: return null
    val hours = match.groupValues[1].toLongOrNull() ?: return null
    val minutes = match.groupValues[2].toLongOrNull()?.takeIf { it < 60L } ?: return null
    val seconds = match.groupValues[3].toLongOrNull()?.takeIf { it < 60L } ?: return null
    val centiseconds = match.groupValues[4].toLongOrNull()?.takeIf { it < 100L } ?: return null
    return (((hours * 60L + minutes) * 60L + seconds) * 1_000L) + centiseconds * 10L
}

private fun formatAssTime(timeMs: Long): String {
    val totalCentiseconds = timeMs.coerceAtLeast(0L) / 10L
    val centiseconds = totalCentiseconds % 100L
    val totalSeconds = totalCentiseconds / 100L
    val seconds = totalSeconds % 60L
    val totalMinutes = totalSeconds / 60L
    val minutes = totalMinutes % 60L
    val hours = totalMinutes / 60L
    return String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds)
}

private const val MEDIA3_ASS_FIELD_COUNT = 11
private const val MAX_LIBASS_SAMPLE_BYTES = 1024 * 1024
private val ASS_TIME = Regex("^(\\d+):(\\d{1,2}):(\\d{1,2})[:.](\\d{1,2})$")
