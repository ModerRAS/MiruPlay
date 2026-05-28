package com.miruplay.tv.repository

import kotlin.math.max

data class BangumiMatchContext(
    val queries: List<String>,
    val seasonNumber: Int? = null,
) {
    companion object {
        fun fromQueries(queries: Collection<String>): BangumiMatchContext {
            val cleaned = queries
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            val seasonSpecific = cleaned.filter { BangumiSubjectMatcher.extractSeasonNumber(it) != null }
            val effectiveQueries = seasonSpecific.ifEmpty { cleaned }
            return BangumiMatchContext(
                queries = effectiveQueries,
                seasonNumber = seasonSpecific.asSequence()
                    .mapNotNull(BangumiSubjectMatcher::extractSeasonNumber)
                    .firstOrNull() ?: cleaned.asSequence()
                    .mapNotNull(BangumiSubjectMatcher::extractSeasonNumber)
                    .firstOrNull(),
            )
        }
    }
}

data class BangumiSubjectMatchCandidate(
    val id: String,
    val title: String,
    val titleCn: String?,
    val aliases: List<String>,
    val score: Float,
    val serverIndex: Int,
    val rank: Int? = null,
    val date: String? = null,
)

data class BangumiSubjectMatch(
    val candidate: BangumiSubjectMatchCandidate,
    val matchedTitle: String,
    val confidence: Float,
)

object BangumiSubjectMatcher {
    fun rank(
        query: String,
        candidates: List<BangumiSubjectMatchCandidate>,
    ): List<BangumiSubjectMatch> =
        rank(BangumiMatchContext.fromQueries(listOf(query)), candidates)

    fun rank(
        context: BangumiMatchContext,
        candidates: List<BangumiSubjectMatchCandidate>,
    ): List<BangumiSubjectMatch> {
        if (context.queries.isEmpty() || candidates.isEmpty()) return emptyList()
        return candidates.mapNotNull { candidate ->
            val aliases = candidate.aliases
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            val scored = context.queries
                .mapNotNull { query -> scoreQuery(query, aliases, candidate.score, candidate.serverIndex, context.seasonNumber) }
                .maxByOrNull { it.confidence }
                ?: return@mapNotNull null
            BangumiSubjectMatch(
                candidate = candidate.copy(aliases = aliases),
                matchedTitle = scored.matchedTitle,
                confidence = scored.confidence,
            )
        }.sortedWith(
            compareByDescending<BangumiSubjectMatch> { it.confidence }
                .thenBy { it.candidate.rank ?: Int.MAX_VALUE }
                .thenByDescending { it.candidate.score }
                .thenByDescending { it.candidate.date.orEmpty() }
        )
    }

    fun calculateConfidence(query: String, candidates: List<String>, score: Float, serverIndex: Int): Float =
        scoreQuery(query, candidates, score, serverIndex, requestedSeason = null)?.confidence ?: 0.2f

    internal fun extractSeasonNumber(text: String): Int? {
        val trimmed = text.trim()
        seasonPatterns.forEach { pattern ->
            val match = pattern.find(trimmed) ?: return@forEach
            val raw = match.groups["num"]?.value ?: return@forEach
            return parseSeasonNumber(raw)
        }
        return null
    }

    private fun scoreQuery(
        query: String,
        candidates: List<String>,
        score: Float,
        serverIndex: Int,
        requestedSeason: Int?,
    ): QueryScore? {
        val normalizedQuery = query.normalizedTitle()
        if (normalizedQuery.isBlank()) return null
        val normalizedCandidates = candidates
            .map { CandidateText(original = it, normalized = it.normalizedTitle(), season = extractSeasonNumber(it)) }
            .filter { it.normalized.isNotBlank() }
        if (normalizedCandidates.isEmpty()) return null

        val best = normalizedCandidates
            .map { candidate ->
                val base = titleSimilarity(normalizedQuery, candidate.normalized)
                val seasonAdjusted = adjustForSeason(base, requestedSeason, candidate)
                QueryScore(
                    matchedTitle = candidate.original,
                    confidence = seasonAdjusted,
                )
            }
            .maxByOrNull { it.confidence }
            ?: return null

        val scoreBoost = if (best.confidence >= 0.62f) (score / 200f).coerceAtMost(0.04f) else 0f
        val serverOrderBoost = if (serverIndex == 0 && best.confidence >= 0.62f) 0.03f else 0f
        return best.copy(confidence = (best.confidence + scoreBoost + serverOrderBoost).coerceIn(0f, 1f))
    }

    private fun adjustForSeason(base: Float, requestedSeason: Int?, candidate: CandidateText): Float {
        val season = requestedSeason ?: return base
        val candidateSeason = candidate.season
        return when {
            candidateSeason == season -> max(base, 0.94f)
            candidateSeason != null && candidateSeason != season -> minOf(base, 0.48f)
            base >= 0.9f -> 0.66f
            base >= 0.7f -> 0.58f
            else -> base
        }
    }

    private fun titleSimilarity(normalizedQuery: String, normalizedCandidate: String): Float =
        when {
            normalizedCandidate == normalizedQuery -> 1.0f
            normalizedCandidate.contains(normalizedQuery) -> 0.9f
            normalizedQuery.contains(normalizedCandidate) && normalizedCandidate.coverageOf(normalizedQuery) >= 0.72f -> 0.9f
            normalizedCandidate.cjkSimilarity(normalizedQuery) >= 0.72f -> 0.82f
            normalizedCandidate.tokenOverlap(normalizedQuery) >= 0.5f -> 0.7f
            normalizedCandidate.cjkSimilarity(normalizedQuery) >= 0.56f -> 0.66f
            else -> 0.2f
        }

    private data class CandidateText(
        val original: String,
        val normalized: String,
        val season: Int?,
    )

    private data class QueryScore(
        val matchedTitle: String,
        val confidence: Float,
    )

    private val seasonPatterns = listOf(
        Regex("""(?i)\bs\s*(?<num>\d{1,2})\b"""),
        Regex("""(?i)\bseason\s*(?<num>\d{1,2})\b"""),
        Regex("""(?i)\b(?<num>\d{1,2})(?:st|nd|rd|th)?\s+season\b"""),
        Regex("""第\s*(?<num>\d{1,2}|[一二三四五六七八九十]+)\s*[季期]"""),
        Regex("""(?i)\b(?<num>II|III|IV|V|VI|VII|VIII|IX|X)\b"""),
    )
}

private fun parseSeasonNumber(raw: String): Int? {
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
        else -> parseCjkNumber(raw)
    }
}

private fun parseCjkNumber(raw: String): Int? {
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

private fun String.normalizedTitle(): String =
    lowercase()
        .replace(Regex("\\[[^\\]]*]"), " ")
        .replace(Regex("[_\\-:：/／\\\\()（）【】「」『』.,，!！?？~～＊*]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun String.tokenOverlap(other: String): Float {
    val left = titleTokens()
    val right = other.titleTokens()
    if (left.isEmpty() || right.isEmpty()) return 0f
    val overlap = left.intersect(right).size
    if (overlap < 2 && left != right) return 0f
    if (overlap == 2 && left.size > overlap && right.size > overlap) {
        val leftOnly = left - right
        val rightOnly = right - left
        if (!leftOnly.hasRelatedToken(rightOnly)) return 0f
    }
    return overlap.toFloat() / minOf(left.size, right.size).toFloat()
}

private fun String.titleTokens(): Set<String> =
    split(' ')
        .map { it.trim() }
        .filter { it.length > 1 }
        .filterNot { it.all(Char::isDigit) }
        .filterNot { it in tokenOverlapStopTokens }
        .toSet()

private fun Set<String>.hasRelatedToken(other: Set<String>): Boolean {
    val leftJoined = joinToString("")
    val rightJoined = other.joinToString("")
    if (leftJoined.length >= 4 && rightJoined.length >= 4) {
        if (leftJoined.contains(rightJoined) || rightJoined.contains(leftJoined)) return true
    }
    return any { left ->
        other.any { right ->
            left.length >= 4 &&
                right.length >= 4 &&
                (left.contains(right) || right.contains(left))
        }
    }
}

private fun String.cjkSimilarity(other: String): Float {
    val left = cjkChars()
    val right = other.cjkChars()
    if (left.isEmpty() || right.isEmpty()) return 0f
    return left.intersect(right).size.toFloat() / maxOf(left.size, right.size).toFloat()
}

private fun String.cjkChars(): Set<Char> =
    filter { it in '\u4e00'..'\u9fff' }.toSet()

private fun String.coverageOf(other: String): Float {
    val leftCjk = cjkChars()
    val rightCjk = other.cjkChars()
    if (leftCjk.isNotEmpty() && rightCjk.isNotEmpty()) {
        return leftCjk.intersect(rightCjk).size.toFloat() / rightCjk.size.toFloat()
    }

    val leftLength = comparableLength()
    val rightLength = other.comparableLength()
    if (leftLength == 0 || rightLength == 0) return 0f
    return leftLength.toFloat() / rightLength.toFloat()
}

private fun String.comparableLength(): Int =
    count { it.isLetterOrDigit() }

private val tokenOverlapStopTokens = setOf(
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
