@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.miruplay.tv.model.SubtitleFormat
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * External subtitle support for the IJK backend.
 *
 * The pinned IJK jar can decode text tracks but has no subtitle rendering path, so
 * external subtitle files are rendered by the existing libass stack
 * ([LibassSubtitleSession] + [LibassSubtitleSurfaceView]) driven by the IJK playback
 * clock instead. ASS/SSA files are handed to libass as-is (ass_read_memory parses the
 * full document); SRT/VTT files are converted to a minimal ASS document plus
 * Dialogue events before being fed to the session.
 */

/**
 * True when the IJK backend must fall back to standard Exo because external subtitles
 * cannot be rendered (libass native library unavailable). External subtitles no longer
 * force an Exo fallback on their own.
 */
internal fun ijkExternalSubtitleFallbackReason(
    hasExternalSubtitles: Boolean,
    libassAvailable: Boolean,
): String? {
    if (!hasExternalSubtitles) return null
    if (libassAvailable) return null
    return "IJKPlayer 缺少 libass 渲染库，外挂字幕需使用标准 Exo"
}

/**
 * Selection contract for IJK subtitle tracks. Only renderable (externally loaded)
 * tracks can be selected; selecting anything else — including embedded tracks IJK
 * cannot render — resolves to null and the playback stays closed.
 */
internal fun resolveIjkSubtitleTrackSelection(
    trackIndex: Int?,
    renderableTrackCount: Int,
): Int? {
    if (trackIndex == null) return null
    if (trackIndex !in 0 until renderableTrackCount) return null
    return trackIndex
}

/**
 * Convert an external subtitle file into libass payloads:
 *  - ASS/SSA: the full file is the libass document (ass_read_memory parses all events).
 *  - SRT/VTT: a minimal ASS header document plus one Dialogue event per cue.
 * Returns an empty list when the file contains no parseable cues.
 */
internal fun parseExternalSubtitlePayloads(
    bytes: ByteArray,
    format: SubtitleFormat,
): List<LibassPayload> {
    if (bytes.isEmpty()) return emptyList()
    return when (format) {
        SubtitleFormat.ASS, SubtitleFormat.SSA -> listOf(LibassPayload.Document(bytes))
        SubtitleFormat.SRT -> srtToAssPayloads(textOf(bytes))
        SubtitleFormat.VTT -> vttToAssPayloads(textOf(bytes))
    }
}

/**
 * Read the full subtitle file for the IJK backend. WebDAV URIs are fetched with a
 * direct connection so the read never queues behind the video stream's WebDAV lease.
 */
@UnstableApi
internal suspend fun readIjkExternalSubtitleBytes(
    dataSourceFactory: DataSource.Factory,
    isWebDavUri: Boolean,
    requestHeaders: Map<String, String>,
    path: String,
): ByteArray? = withContext(Dispatchers.IO) {
    val bytes = if (isWebDavUri) {
        readWebDavBytes(path, requestHeaders)
    } else {
        readDataSourceBytes(dataSourceFactory, path)
    }
    bytes?.takeIf { it.isNotEmpty() && it.size <= MAX_EXTERNAL_SUBTITLE_BYTES }
}

private fun textOf(bytes: ByteArray): String =
    bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")

private val CUE_TIMING = Regex(
    """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""",
)

private data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

private fun srtToAssPayloads(text: String): List<LibassPayload> {
    val cues = mutableListOf<SubtitleCue>()
    val lines = normalizedLines(text)
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trim()
        if (line.isBlank() || line.all(Char::isDigit)) {
            index++
            continue
        }
        val match = CUE_TIMING.find(line)
        if (match == null) {
            index++
            continue
        }
        val startMs = timingMillis(match, 1)
        val endMs = timingMillis(match, 5)
        index++
        val textLines = mutableListOf<String>()
        while (index < lines.size && lines[index].isNotBlank()) {
            textLines += lines[index].trim()
            index++
        }
        if (textLines.isNotEmpty()) {
            cues += SubtitleCue(startMs, endMs, textLines.joinToString("\n"))
        }
    }
    return assPayloadsFromCues(cues)
}

private fun vttToAssPayloads(text: String): List<LibassPayload> {
    val cues = mutableListOf<SubtitleCue>()
    val lines = normalizedLines(text)
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trim()
        if (
            line.isBlank() ||
            line.startsWith("WEBVTT", ignoreCase = true) ||
            line.startsWith("NOTE", ignoreCase = true) ||
            line.startsWith("STYLE", ignoreCase = true)
        ) {
            index++
            continue
        }
        val match = CUE_TIMING.find(line)
        if (match == null) {
            index++
            continue
        }
        val startMs = timingMillis(match, 1)
        val endMs = timingMillis(match, 5)
        index++
        val textLines = mutableListOf<String>()
        while (index < lines.size && lines[index].isNotBlank()) {
            textLines += lines[index].trim()
            index++
        }
        if (textLines.isNotEmpty()) {
            cues += SubtitleCue(startMs, endMs, textLines.joinToString("\n"))
        }
    }
    return assPayloadsFromCues(cues)
}

private fun assPayloadsFromCues(cues: List<SubtitleCue>): List<LibassPayload> {
    if (cues.isEmpty()) return emptyList()
    return listOf(LibassPayload.Document(EXTERNAL_ASS_HEADER)) + cues.map { cue ->
        LibassPayload.Event(
            "Dialogue: 0,${formatAssTime(cue.startMs)},${formatAssTime(cue.endMs)},Default,,0,0,0,,${cue.text.replace("\n", "\\N")}",
        )
    }
}

private fun normalizedLines(text: String): List<String> =
    text.replace("\r\n", "\n").replace('\r', '\n').split('\n')

private fun timingMillis(match: MatchResult, startGroup: Int): Long {
    val hours = match.groupValues[startGroup].toLong()
    val minutes = match.groupValues[startGroup + 1].toLong()
    val seconds = match.groupValues[startGroup + 2].toLong()
    val millis = match.groupValues[startGroup + 3].padStart(3, '0').take(3).toLong()
    return ((hours * 60L + minutes) * 60L + seconds) * 1_000L + millis
}

@UnstableApi
private fun readDataSourceBytes(
    dataSourceFactory: DataSource.Factory,
    path: String,
): ByteArray? = runCatching {
    val uri = subtitleReadUri(path)
    val source = dataSourceFactory.createDataSource()
    try {
        val length = source.open(DataSpec.Builder().setUri(uri).build())
        if (length > MAX_EXTERNAL_SUBTITLE_BYTES) return@runCatching null
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > MAX_EXTERNAL_SUBTITLE_BYTES) return@runCatching null
        }
        output.toByteArray()
    } finally {
        runCatching { source.close() }
    }
}.getOrNull()

private fun readWebDavBytes(
    path: String,
    requestHeaders: Map<String, String>,
): ByteArray? = runCatching {
    val connection = (URL(canonicalPlaybackUri(path)).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = HTTP_TIMEOUT_MILLIS
        readTimeout = HTTP_TIMEOUT_MILLIS
        requestMethod = "GET"
        requestHeaders.forEach(::setRequestProperty)
    }
    try {
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(READ_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                if (output.size() > MAX_EXTERNAL_SUBTITLE_BYTES) return@runCatching null
            }
            output.toByteArray()
        }
    } finally {
        connection.disconnect()
    }
}.getOrNull()

private fun subtitleReadUri(path: String): Uri {
    val parsed = Uri.parse(path)
    return if (parsed.scheme.isNullOrBlank()) Uri.parse("file://$path") else parsed
}

private const val MAX_EXTERNAL_SUBTITLE_BYTES = 8 * 1024 * 1024
private const val READ_BUFFER_BYTES = 32 * 1024
private const val HTTP_TIMEOUT_MILLIS = 20_000

private val EXTERNAL_ASS_HEADER = buildString {
    appendLine("[Script Info]")
    appendLine("ScriptType: v4.00+")
    appendLine("PlayResX: 384")
    appendLine("PlayResY: 288")
    appendLine()
    appendLine("[V4+ Styles]")
    appendLine(
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, " +
            "BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, " +
            "BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
    )
    appendLine(
        "Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0," +
            "100,100,0,0,1,2,2,2,10,10,10,1",
    )
    appendLine()
    appendLine("[Events]")
    appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
}.toByteArray(Charsets.UTF_8)
