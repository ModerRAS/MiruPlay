package com.miruplay.tv.translation

/**
 * 纯 Kotlin 字幕解析/重写。SRT / ASS / VTT 一律解析为统一的 cue（时间轴已规范化为
 * SRT 的 "HH:MM:SS,mmm" 格式），重写时输出 SRT。
 *
 * ASS 只取 Dialogue 行正文，去掉 {\...} 覆盖标签、\N/\n 换行转成真实换行；
 * 其他行（header、Format、Comment、Style 等）丢弃。VTT 的头部/注释/标识行自然跳过。
 */
data class SubtitleCue(
    val start: String,
    val end: String,
    val text: String,
)

object SubtitleFileParser {

    fun parse(text: String): List<SubtitleCue> =
        if (isAss(text)) parseAss(text) else parseGeneric(text)

    fun toSrt(cues: List<SubtitleCue>): String =
        buildString {
            cues.forEachIndexed { index, cue ->
                append(index + 1).append('\n')
                append(cue.start).append(" --> ").append(cue.end).append('\n')
                append(cue.text).append('\n')
                if (index != cues.lastIndex) append('\n')
            }
        }

    private fun isAss(text: String): Boolean =
        text.lines().any { it.trimStart().startsWith("Dialogue:") }

    /** SRT / VTT 通用解析：找 " --> " 时间行，其后非空行作为文本，直到空行或下一条时间行。 */
    private fun parseGeneric(text: String): List<SubtitleCue> {
        val lines = text.split("\r\n", "\n")
        val cues = mutableListOf<SubtitleCue>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.contains("-->")) {
                val (start, end) = parseTimeLine(line)
                val textLines = mutableListOf<String>()
                i++
                while (i < lines.size) {
                    val content = lines[i].trim()
                    if (content.isEmpty() || content.contains("-->")) break
                    textLines += content
                    i++
                }
                val cueText = textLines.joinToString("\n")
                if (cueText.isNotBlank()) cues += SubtitleCue(start, end, cueText)
                continue
            }
            i++
        }
        return cues
    }

    /** ASS：只解析 Dialogue 行。字段按规范用前 9 个逗号分隔，Text 是第 10 段（含逗号）。 */
    private fun parseAss(text: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        text.split("\r\n", "\n").forEach { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("Dialogue:")) return@forEach
            val fields = line.split(",", limit = 10)
            if (fields.size < 10) return@forEach
            val cueText = cleanAssText(fields[9])
            if (cueText.isBlank()) return@forEach
            cues += SubtitleCue(
                start = canonicalizeTimestamp(fields[1].trim()),
                end = canonicalizeTimestamp(fields[2].trim()),
                text = cueText,
            )
        }
        return cues
    }

    private fun cleanAssText(raw: String): String =
        raw.replace(ASS_OVERRIDE_TAG, "")
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .trim()

    private fun parseTimeLine(line: String): Pair<String, String> {
        val parts = line.split("-->")
        val start = parts[0].trim().substringBefore(' ')
        val end = parts.getOrElse(1) { "" }.trim().substringBefore(' ')
        return canonicalizeTimestamp(start) to canonicalizeTimestamp(end)
    }

    /** 支持 SRT(hh:mm:ss,mmm) / VTT(hh:mm:ss.mmm 或 mm:ss.mmm) / ASS(H:MM:SS.cc)。 */
    private fun canonicalizeTimestamp(raw: String): String {
        FULL_TIMESTAMP.find(raw)?.let { match ->
            val (h, m, s, frac) = match.destructured
            val millis = h.toInt() * 3_600_000 + m.toInt() * 60_000 + s.toInt() * 1_000 + fractionMillis(frac)
            return formatTimestamp(millis)
        }
        SHORT_TIMESTAMP.find(raw)?.let { match ->
            val (m, s, frac) = match.destructured
            val millis = m.toInt() * 60_000 + s.toInt() * 1_000 + fractionMillis(frac)
            return formatTimestamp(millis)
        }
        return raw
    }

    private fun fractionMillis(frac: String): Int =
        when (frac.length) {
            1 -> frac.toInt() * 100
            2 -> frac.toInt() * 10
            else -> frac.toInt()
        }

    private fun formatTimestamp(totalMillis: Int): String {
        val h = totalMillis / 3_600_000
        val m = (totalMillis % 3_600_000) / 60_000
        val s = (totalMillis % 60_000) / 1_000
        val millis = totalMillis % 1_000
        return "%02d:%02d:%02d,%03d".format(h, m, s, millis)
    }

    private val FULL_TIMESTAMP = Regex("""(\d{1,2}):(\d{1,2}):(\d{1,2})[.,](\d{1,3})""")
    private val SHORT_TIMESTAMP = Regex("""(\d{1,2}):(\d{1,2})[.,](\d{1,3})""")
    private val ASS_OVERRIDE_TAG = Regex("""\{[^}]*\}""")
}
