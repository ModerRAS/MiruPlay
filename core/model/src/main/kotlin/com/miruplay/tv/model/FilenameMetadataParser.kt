package com.miruplay.tv.model

data class FilenameParseResult(
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val group: String? = null,
    val resolution: String? = null,
    val source: String? = null,
    val special: String? = null,
)

interface FilenameMetadataParser {
    fun parse(filename: String, maxLength: Int = 128): FilenameParseResult
}

fun FilenameParseResult.sanitizeRecognizedText(): FilenameParseResult =
    copy(
        title = title.cleanRecognizedTitle(),
        group = group.cleanRecognizedField(),
        resolution = resolution.cleanRecognizedField(),
        source = source.cleanRecognizedField(),
        special = special.cleanRecognizedField(),
    )

private fun String?.cleanRecognizedTitle(): String? {
    val cleaned = cleanRecognizedField() ?: return null
    val segments = cleaned
        .split('/', '\\')
        .mapNotNull { it.cleanRecognizedField() }
    if (segments.size <= 1) return cleaned

    val hasPathContext = segments.any { it.isRecognizedPathContextSegment() }
    if (!hasPathContext) return cleaned

    val meaningful = segments.filterNot { it.isRecognizedPathContextSegment() }
    val collapsed = meaningful
        .collapseRepeatedRecognizedSegments()
        .dropLastWhile { it.isRecognizedSupplementSegment() }
        .ifEmpty { meaningful }
    return collapsed
        .joinToString("/")
        .cleanRecognizedField()
}

private fun List<String>.collapseRepeatedRecognizedSegments(): List<String> =
    fold(mutableListOf<String>()) { acc, segment ->
        val previous = acc.lastOrNull()
        if (previous != null && previous.sameRecognizedSegment(segment)) {
            acc
        } else {
            acc += segment
            acc
        }
    }

private fun String.sameRecognizedSegment(other: String): Boolean =
    normalizeRecognizedSegment() == other.normalizeRecognizedSegment()

private fun String.normalizeRecognizedSegment(): String =
    cleanRecognizedField()
        ?.lowercase()
        ?.replace(Regex("""[._\-\[\]【】()（）《》]+"""), " ")
        ?.replace(whitespaceRegex, " ")
        ?.trim()
        .orEmpty()

private fun String?.cleanRecognizedField(): String? =
    this
        ?.replace(whitespaceRegex, " ")
        ?.trim()
        ?.trim(*recognizedBoundaryChars)
        ?.trimRecognizedDecorations()
        ?.trim(*recognizedBoundaryChars)
        ?.replace(whitespaceRegex, " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun String.trimRecognizedDecorations(): String =
    trim('[', ']', '(', ')', '【', '】', '《', '》', '（', '）')

private fun String.isRecognizedPathContextSegment(): Boolean {
    val normalized = lowercase()
        .replace(Regex("""[._\-\[\]【】()（）]+"""), " ")
        .replace(whitespaceRegex, " ")
        .trim()
    if (normalized in recognizedPathContextNames) return true
    return recognizedPathContextPatterns.any { it.matches(normalized) }
}

private fun String.isRecognizedSupplementSegment(): Boolean {
    val normalized = lowercase()
        .replace(Regex("""[._\-\[\]【】()（）《》]+"""), " ")
        .replace(whitespaceRegex, " ")
        .trim()
    if (normalized in recognizedSupplementNames) return true
    return recognizedSupplementPatterns.any { it.matches(normalized) }
}

private val recognizedBoundaryChars = charArrayOf(' ', '\t', '\r', '\n', '-', '_', '.', '/', '\\')
private val whitespaceRegex = Regex("""\s+""")
private val recognizedPathContextNames = setOf(
    "115open",
    "0",
    "ani",
    "anime",
    "anime library",
    "animation",
    "content",
    "content:",
    "dav",
    "download",
    "downloads",
    "emulated",
    "library",
    "media",
    "mnt",
    "movie",
    "movies",
    "raw",
    "raws",
    "root",
    "sdcard",
    "smb",
    "smb:",
    "storage",
    "video",
    "videos",
    "webdav",
    "下载",
    "下載",
    "动漫",
    "動畫",
    "影视",
    "影音",
    "电视剧",
    "劇集",
    "片头尾",
)
private val recognizedPathContextPatterns = listOf(
    Regex("""^[a-z]:$"""),
    Regex("""^miruplaypathparser(?:\s+\d+.*)?$"""),
    Regex("""^(?:season|series|s)\s*\d{1,2}$"""),
    Regex("""^(?:ep|episode|part)\s*[\d一二三四五六七八九十]+(?:[a-z])?$"""),
    Regex("""^\d{1,4}\s+(?:mkv|mp4|avi|mov|wmv|flv|webm|m4v|mpg|mpeg|ts|m2ts)$"""),
)
private val recognizedSupplementNames = setOf(
    "片头",
    "片尾",
    "片头尾",
    "预告",
    "预告片",
    "花絮",
    "opening",
    "ending",
    "trailer",
    "preview",
    "op",
    "ed",
)
private val recognizedSupplementPatterns = listOf(
    Regex("""^(?:片头|片尾|预告(?:片)?|花絮)(?:\s+.*)?$"""),
    Regex("""(?i)^(?:opening|ending|trailer|preview|op|ed)(?:\s+.*)?$"""),
)
