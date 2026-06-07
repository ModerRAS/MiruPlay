package com.miruplay.tv.repository

import com.miruplay.tv.model.FilenameParseResult
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.model.sanitizeRecognizedText
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.max

fun metadataExtractSeasonNumber(text: String): Int? {
    val trimmed = text.trim()
    metadataSeasonPatterns.forEach { pattern ->
        val match = pattern.find(trimmed) ?: return@forEach
        val raw = match.groups["num"]?.value ?: return@forEach
        return metadataParseSeasonNumber(raw)
    }
    return null
}

fun metadataExtractYear(text: String): Int? =
    Regex("""\b(19|20)\d{2}\b""")
        .find(text)
        ?.value
        ?.toIntOrNull()

fun metadataNormalizeTitle(text: String): String =
    text.lowercase()
        .replace(Regex("\\[[^\\]]*]"), " ")
        .replace(Regex("[_\\-:：/／\\\\()（）【】「」『』.,，!！?？~～＊*·]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

fun metadataNormalizeSeasonlessTitle(text: String): String =
    metadataNormalizeTitle(text)
        .replace(metadataSeasonSuffixRegex, "")
        .replace(Regex("\\s+"), " ")
        .trim()

fun metadataTokenOverlap(left: String, right: String): Float {
    val leftTokens = metadataTitleTokens(left)
    val rightTokens = metadataTitleTokens(right)
    if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0f
    val overlap = leftTokens.intersect(rightTokens).size
    if (overlap == 0) return 0f
    return overlap.toFloat() / minOf(leftTokens.size, rightTokens.size).toFloat()
}

fun metadataCjkSimilarity(left: String, right: String): Float {
    val leftChars = left.filter { it in '\u4e00'..'\u9fff' }.toSet()
    val rightChars = right.filter { it in '\u4e00'..'\u9fff' }.toSet()
    if (leftChars.isEmpty() || rightChars.isEmpty()) return 0f
    return leftChars.intersect(rightChars).size.toFloat() / maxOf(leftChars.size, rightChars.size).toFloat()
}

fun metadataTitleSimilarity(left: String, right: String): Float {
    val normalizedLeft = metadataNormalizeSeasonlessTitle(left)
    val normalizedRight = metadataNormalizeSeasonlessTitle(right)
    if (normalizedLeft.isBlank() || normalizedRight.isBlank()) return 0f
    return when {
        normalizedLeft == normalizedRight -> 1.0f
        normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft) -> 0.9f
        metadataCjkSimilarity(normalizedLeft, normalizedRight) >= 0.72f -> 0.82f
        metadataTokenOverlap(normalizedLeft, normalizedRight) >= 0.6f -> 0.72f
        metadataCjkSimilarity(normalizedLeft, normalizedRight) >= 0.56f -> 0.66f
        metadataLongestCommonSubstringLength(normalizedLeft, normalizedRight) >= 4 -> 0.62f
        else -> 0.2f
    }
}

fun metadataDerivePathQueries(path: String): List<String> {
    val decoded = runCatching { URLDecoder.decode(path, StandardCharsets.UTF_8.name()) }.getOrDefault(path)
    val segments = decoded
        .split('/', '\\')
        .map { it.substringBeforeLast('.') }
        .mapNotNull(::metadataSanitizeQueryCandidate)
        .distinct()
    return buildList {
        segments.lastOrNull()?.let(::add)
        segments.asReversed().drop(1).take(2).forEach(::add)
    }.distinct()
}

fun metadataComparableYear(date: String?): Int? =
    date
        ?.takeIf { it.isNotBlank() }
        ?.substringBefore('-')
        ?.toIntOrNull()

fun metadataProviderRefHintText(providerRef: MetadataProviderRef): String =
    "${providerRef.source}:${providerRef.id}"

fun metadataParseProviderRefHint(text: String?): MetadataProviderRef? {
    val trimmed = text?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    val separatorIndex = trimmed.indexOf(':')
    if (separatorIndex <= 0 || separatorIndex >= trimmed.lastIndex) return null
    val rawSource = trimmed.substring(0, separatorIndex).trim()
    val rawId = trimmed.substring(separatorIndex + 1).trim()
    if (rawSource.isBlank() || rawId.isBlank()) return null
    val normalizedSource = when (rawSource.lowercase()) {
        "bangumi", "bgm" -> "Bangumi"
        "anilist", "ani" -> "AniList"
        "tmdb" -> "TMDB"
        "tvmaze", "tv-maze" -> "TVMaze"
        else -> return null
    }
    return MetadataProviderRef(source = normalizedSource, id = rawId)
}

private fun metadataTitleTokens(text: String): Set<String> =
    metadataNormalizeSeasonlessTitle(text)
        .split(' ')
        .map { it.trim() }
        .filter { it.length > 1 }
        .filterNot { it.all(Char::isDigit) }
        .filterNot { it in metadataTokenOverlapStopTokens }
        .toSet()

private fun metadataLongestCommonSubstringLength(left: String, right: String): Int {
    if (left.isEmpty() || right.isEmpty()) return 0
    val dp = IntArray(right.length + 1)
    var longest = 0
    for (i in left.indices) {
        for (j in right.length - 1 downTo 0) {
            dp[j + 1] = if (left[i] == right[j]) dp[j] + 1 else 0
            longest = max(longest, dp[j + 1])
        }
    }
    return longest
}

private fun metadataParseSeasonNumber(raw: String): Int? {
    raw.trim().toIntOrNull()?.let { return it }
    return when (raw.trim().uppercase()) {
        "II" -> 2
        "III" -> 3
        "IV" -> 4
        "V" -> 5
        "VI" -> 6
        "VII" -> 7
        "VIII" -> 8
        "IX" -> 9
        "X" -> 10
        else -> metadataParseCjkNumber(raw)
    }
}

private fun metadataParseCjkNumber(raw: String): Int? {
    var total = 0
    var current = 0
    for (char in raw.trim()) {
        when (char) {
            '一' -> current += 1
            '二' -> current += 2
            '三' -> current += 3
            '四' -> current += 4
            '五' -> current += 5
            '六' -> current += 6
            '七' -> current += 7
            '八' -> current += 8
            '九' -> current += 9
            '十' -> {
                total += if (current == 0) 10 else current * 10
                current = 0
            }
            else -> return null
        }
    }
    return (total + current).takeIf { it > 0 }
}

private fun metadataSanitizeQueryCandidate(raw: String): String? {
    val stripped = raw
        .replace(metadataEpisodeNoiseRegex, " ")
        .replace(metadataBracketNoiseRegex, " ")
        .replace(metadataYearSuffixRegex, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return FilenameParseResult(title = stripped)
        .sanitizeRecognizedText()
        .title
        ?.replace(metadataPathNoiseRegex, " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim('-', '_', '.', '/', '\\', ' ')
        ?.takeIf { it.isNotBlank() }
}

private val metadataSeasonPatterns = listOf(
    Regex("""(?i)\bs\s*(?<num>\d{1,2})\b"""),
    Regex("""(?i)\bseason\s*(?<num>\d{1,2})\b"""),
    Regex("""(?i)\b(?<num>\d{1,2})(?:st|nd|rd|th)?\s+season\b"""),
    Regex("""第\s*(?<num>\d{1,2}|[一二三四五六七八九十]+)\s*[季期]"""),
    Regex("""(?i)\b(?<num>II|III|IV|V|VI|VII|VIII|IX|X)\b"""),
)
private val metadataSeasonSuffixRegex = Regex("""(?i)(?:\bseason\s*\d+\b|\bs\s*\d+\b|第\s*\d+\s*[季期])""")
private val metadataEpisodeNoiseRegex = Regex("""(?i)(?:\bs\d{1,2}e\d{1,3}\b|\bep?\s*\d{1,3}\b|第\s*\d{1,3}\s*[集话])""")
private val metadataBracketNoiseRegex = Regex("""[\[\]【】()（）]""")
private val metadataYearSuffixRegex = Regex("""\b(19|20)\d{2}\b""")
private val metadataPathNoiseRegex = Regex("""(?i)\b(?:mkv|mp4|avi|mov|wmv|flv|webm|m4v|mpeg|ts|m2ts|4k|1080p|720p|2160p)\b""")
private val metadataTokenOverlapStopTokens = setOf(
    "a",
    "an",
    "and",
    "ep",
    "episode",
    "for",
    "from",
    "in",
    "movie",
    "no",
    "of",
    "on",
    "ona",
    "or",
    "ova",
    "part",
    "season",
    "series",
    "special",
    "the",
    "to",
    "tv",
    "with",
)
