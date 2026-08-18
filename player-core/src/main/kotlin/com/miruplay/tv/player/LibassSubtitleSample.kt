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
    // Media3 1.8 MatroskaExtractor contract (SSA_DIALOGUE_FORMAT):
    //   "Dialogue: <Start>,<End>,<ReadOrder>,<Layer>,<Style>,<Name>,<MarginL>,<MarginR>,<MarginV>,<Effect>,<Text>"
    // Text may contain commas (kept intact by the split limit). mkvmerge blocks carry the ReadOrder
    // column (11 fields); SSA v4 "Marked" / legacy blocks omit it (10 fields, body starts at the
    // Marked-or-Layer value). Both layouts are decoded here.
    if (fields.size !in MINIMUM_ASS_FIELD_COUNT..MEDIA3_ASS_FIELD_COUNT) return null

    val relativeStartMs = parseAssTimeMs(fields[0]) ?: return null
    val relativeEndMs = parseAssTimeMs(fields[1]) ?: return null
    val durationMs = relativeEndMs - relativeStartMs
    if (durationMs <= 0L) return null

    val bodyStart = fields.size - 8 // 3 with ReadOrder, 2 for Marked/legacy
    val startMs = sampleTimeUs.coerceAtLeast(0L) / 1_000L
    val eventFields = listOf(
        fields[bodyStart], // Layer (or Marked=0)
        formatAssTime(startMs),
        formatAssTime(startMs + durationMs),
        fields[bodyStart + 1], // Style
        fields[bodyStart + 2], // Name
        fields[bodyStart + 3], // MarginL
        fields[bodyStart + 4], // MarginR
        fields[bodyStart + 5], // MarginV
        fields[bodyStart + 6], // Effect
        fields[bodyStart + 7], // Text
    )
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

internal fun formatAssTime(timeMs: Long): String {
    val totalCentiseconds = timeMs.coerceAtLeast(0L) / 10L
    val centiseconds = totalCentiseconds % 100L
    val totalSeconds = totalCentiseconds / 100L
    val seconds = totalSeconds % 60L
    val totalMinutes = totalSeconds / 60L
    val minutes = totalMinutes % 60L
    val hours = totalMinutes / 60L
    return String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds)
}

/**
 * Parse the start/end bounds (microseconds) of a Dialogue event line that uses the
 * canonical libass-session layout produced by [decodeLibassPayload] / SRT conversion:
 *   "Dialogue: <Layer>,<Start>,<End>,<Style>,<Name>,..."
 * Start/End are already absolute in the playback timeline. Returns null when unparseable.
 */
internal fun dialogueCueBoundsUs(dialogueLine: String): Pair<Long, Long>? {
    val line = dialogueLine.trimEnd('\u0000', '\r', '\n')
    if (!line.startsWith("Dialogue:", ignoreCase = true)) return null
    val fields = line.substringAfter(':').trimStart().split(',', limit = MEDIA3_ASS_FIELD_COUNT)
    if (fields.size < 10) return null
    val startMs = parseAssTimeMs(fields[1]) ?: return null
    val endMs = parseAssTimeMs(fields[2]) ?: return null
    if (endMs <= startMs) return null
    return (startMs * 1_000L) to (endMs * 1_000L)
}

/**
 * Extract cue start/end bounds from an ASS document's [Events] block. Only the existing
 * Dialogue lines are parsed (fixed upper bound); this is deliberately not a general
 * indexer. Column order is resolved from the Format line when present, otherwise the
 * standard layout (Layer, Start, End, ...) is assumed.
 */
internal fun assDocumentCueBoundsUs(document: ByteArray): List<Pair<Long, Long>> {
    if (document.isEmpty()) return emptyList()
    val text = runCatching { document.toString(Charsets.UTF_8) }.getOrNull() ?: return emptyList()
    val eventsMarker = text.indexOf("[Events]", ignoreCase = true)
    if (eventsMarker < 0) return emptyList()
    val eventsSection = text.substring(eventsMarker)
    var startIndex = 1
    var endIndex = 2
    for (line in eventsSection.lineSequence()) {
        if (!line.startsWith("Format:", ignoreCase = true)) continue
        val columns = line.substringAfter(':').trim().split(',').map { it.trim() }
        val start = columns.indexOfFirst { it.equals("Start", ignoreCase = true) }
        val end = columns.indexOfFirst { it.equals("End", ignoreCase = true) }
        if (start >= 0 && end >= 0) {
            startIndex = start
            endIndex = end
        }
        break
    }
    val result = mutableListOf<Pair<Long, Long>>()
    for (line in eventsSection.lineSequence()) {
        if (result.size >= LibassSubtitleMonitor.MAX_CUE_TIMELINE) break
        if (!line.startsWith("Dialogue:", ignoreCase = true)) continue
        val fields = line.substringAfter(':').trimStart().split(',', limit = MEDIA3_ASS_FIELD_COUNT)
        val start = fields.getOrNull(startIndex)?.let(::parseAssTimeMs) ?: continue
        val end = fields.getOrNull(endIndex)?.let(::parseAssTimeMs) ?: continue
        if (end <= start) continue
        result += (start * 1_000L) to (end * 1_000L)
    }
    return result
}

private const val MEDIA3_ASS_FIELD_COUNT = 11
private const val MINIMUM_ASS_FIELD_COUNT = 10
private const val MAX_LIBASS_SAMPLE_BYTES = 1024 * 1024
private val ASS_TIME = Regex("^(\\d+):(\\d{1,2}):(\\d{1,2})[:.](\\d{1,2})$")
