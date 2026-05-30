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
    return meaningful
        .joinToString("/")
        .cleanRecognizedField()
}

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
)
private val recognizedPathContextPatterns = listOf(
    Regex("""^[a-z]:$"""),
    Regex("""^miruplaypathparser(?:\s+\d+.*)?$"""),
    Regex("""^(?:season|series|s)\s*\d{1,2}$"""),
    Regex("""^(?:ep|episode|part)\s*[\d一二三四五六七八九十]+(?:[a-z])?$"""),
    Regex("""^\d{1,4}\s+(?:mkv|mp4|avi|mov|wmv|flv|webm|m4v|mpg|mpeg|ts|m2ts)$"""),
)
