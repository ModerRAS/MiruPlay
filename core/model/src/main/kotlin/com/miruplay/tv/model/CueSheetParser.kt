package com.miruplay.tv.model

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

data class CueSheet(
    val file: String? = null,
    val fileType: String? = null,
    val tracks: List<CueTrack> = emptyList()
)

data class CueTrack(
    val index: Int,
    val title: String? = null,
    val performer: String? = null,
    val startMs: Long = 0L,
    val endMs: Long? = null
)

object CueSheetParser {
    fun parse(input: InputStream, charset: Charset = Charsets.UTF_8): CueSheet? {
        val text = try {
            BufferedReader(InputStreamReader(input, charset)).readText()
        } catch (_: Exception) {
            return null
        }
        return parseText(text)
    }

    fun parseText(text: String): CueSheet? {
        var file: String? = null
        var fileType: String? = null
        val tracks = mutableListOf<MutableCueTrack>()
        var current: MutableCueTrack? = null

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("REM", ignoreCase = true)) continue
            when {
                line.startsWith("FILE", ignoreCase = true) -> {
                    // FILE "album.flac" WAVE
                    val m = Regex("""FILE\s+\"([^\"]+)\"(?:\s+(\S+))?""", RegexOption.IGNORE_CASE).find(line)
                        ?: Regex("""FILE\s+(\S+)(?:\s+(\S+))?""", RegexOption.IGNORE_CASE).find(line)
                    if (m != null) {
                        file = m.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                        fileType = m.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
                    }
                }
                line.startsWith("TRACK", ignoreCase = true) -> {
                    // TRACK 01 AUDIO
                    val m = Regex("""TRACK\s+(\d+)\s+\S+""", RegexOption.IGNORE_CASE).find(line)
                    val idx = m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: (tracks.size + 1)
                    current?.let { tracks.add(it) }
                    current = MutableCueTrack(index = idx)
                }
                line.startsWith("TITLE", ignoreCase = true) -> {
                    val title = extractQuoted(line.substringAfter("TITLE", "").trim())
                        ?: line.substringAfter("TITLE", "").trim().trim('"').takeIf { it.isNotBlank() }
                    current?.title = title ?: current?.title
                    // If no current track, could be album title; keep as file-level? ignore for now
                }
                line.startsWith("PERFORMER", ignoreCase = true) -> {
                    val performer = extractQuoted(line.substringAfter("PERFORMER", "").trim())
                        ?: line.substringAfter("PERFORMER", "").trim().trim('"').takeIf { it.isNotBlank() }
                    current?.performer = performer ?: current?.performer
                }
                line.startsWith("INDEX", ignoreCase = true) -> {
                    // INDEX 01 00:00:00  or INDEX 00 00:00:00
                    val m = Regex("""INDEX\s+(0\d|1\d)\s+(\d+):(\d+):(\d+)""", RegexOption.IGNORE_CASE).find(line)
                    if (m != null) {
                        val idxType = m.groupValues[1]
                        val mm = m.groupValues[2].toIntOrNull() ?: 0
                        val ss = m.groupValues[3].toIntOrNull() ?: 0
                        val ff = m.groupValues[4].toIntOrNull() ?: 0
                        val ms = mm * 60 * 1000L + ss * 1000L + ff * 1000L / 75L
                        if (idxType == "01") {
                            current?.startMs = ms
                        } else if (idxType == "00") {
                            // keep as startMs if 01 not yet? but prefer 01
                            if (current?.startMs == 0L) current?.startMs = ms
                        }
                    }
                }
            }
        }
        current?.let { tracks.add(it) }
        if (tracks.isEmpty()) return null
        // Compute endMs as next track start
        val sorted = tracks.sortedBy { it.index }
        val resultTracks = sorted.mapIndexed { idx, t ->
            val end = sorted.getOrNull(idx + 1)?.startMs
            CueTrack(index = t.index, title = t.title, performer = t.performer, startMs = t.startMs, endMs = end)
        }
        return CueSheet(file = file, fileType = fileType, tracks = resultTracks)
    }

    private fun extractQuoted(s: String): String? {
        val t = s.trim()
        if (t.startsWith("\"") && t.endsWith("\"") && t.length >= 2) return t.substring(1, t.length - 1)
        return null
    }

    private data class MutableCueTrack(
        val index: Int,
        var title: String? = null,
        var performer: String? = null,
        var startMs: Long = 0L
    )
}
