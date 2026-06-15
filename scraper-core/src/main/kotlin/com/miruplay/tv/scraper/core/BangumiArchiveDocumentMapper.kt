package com.miruplay.tv.scraper.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.lucene.document.Document
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.document.Field.Store

internal class BangumiArchiveDocumentMapper(
    private val normalizeText: (String) -> String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseSubject(line: String): BangumiArchiveSubject? {
        if (line.isBlank()) return null
        val record = runCatching {
            json.decodeFromString(BangumiArchiveSubjectRecord.serializer(), line)
        }.getOrNull() ?: return null
        if (record.type != SUBJECT_TYPE_ANIME || record.name.isBlank()) return null

        val aliases = buildList {
            add(record.name)
            record.nameCn?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(record.infobox.extractInfoboxAliases())
            addAll(record.metaTags)
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        return BangumiArchiveSubject(
            id = record.id,
            name = record.name,
            nameCn = record.nameCn?.ifBlank { null },
            summary = record.summary?.ifBlank { null },
            aliases = aliases,
            platform = record.platform,
            date = record.date,
            episodeCount = record.eps ?: record.totalEpisodes ?: 0,
            score = record.score,
            rank = record.rank,
        )
    }

    fun toDocument(subject: BangumiArchiveSubject): Document =
        Document().apply {
            add(StringField(BangumiArchiveLuceneFields.ID, subject.id.toString(), Store.NO))
            add(StoredField(BangumiArchiveLuceneFields.STORED_ID, subject.id))
            add(StoredField(BangumiArchiveLuceneFields.STORED_TITLE, subject.name))
            subject.nameCn?.let { add(StoredField(BangumiArchiveLuceneFields.STORED_TITLE_CN, it)) }
            subject.summary?.let { add(StoredField(BangumiArchiveLuceneFields.STORED_SUMMARY, it)) }
            subject.aliases.forEach { add(StoredField(BangumiArchiveLuceneFields.STORED_ALIAS, it)) }
            subject.platform?.let { add(StoredField(BangumiArchiveLuceneFields.STORED_PLATFORM, it)) }
            add(StoredField(BangumiArchiveLuceneFields.STORED_EPISODE_COUNT, subject.episodeCount))
            subject.rank?.let { add(StoredField(BangumiArchiveLuceneFields.STORED_RANK, it)) }
            subject.score?.let { add(StoredField(BangumiArchiveLuceneFields.STORED_SCORE, it)) }
            subject.date?.takeIf { it.isNotBlank() }?.let {
                add(StoredField(BangumiArchiveLuceneFields.STORED_DATE, it))
            }

            indexTextField(this, BangumiArchiveLuceneFields.TITLE, subject.name)
            subject.nameCn?.let { indexTextField(this, BangumiArchiveLuceneFields.TITLE_CN, it) }

            subject.aliases
                .asSequence()
                .filterNot { it == subject.name || it == subject.nameCn }
                .distinct()
                .forEach { alias ->
                    indexTextField(this, BangumiArchiveLuceneFields.ALIASES, alias)
                }

            titleVariants(subject).forEach { title ->
                val normalized = normalizeArchiveIndexedText(title, normalizeText)
                if (normalized.isBlank()) return@forEach
                add(TextField(BangumiArchiveLuceneFields.ALL_TITLES, normalized, Store.NO))
                add(StringField(BangumiArchiveLuceneFields.ALL_TITLES_EXACT, normalized, Store.NO))

                val seasonless = normalized.toSeasonlessArchiveText()
                if (seasonless.isNotBlank()) {
                    add(StringField(BangumiArchiveLuceneFields.ALL_TITLES_SEASONLESS, seasonless, Store.NO))
                }
            }
        }

    fun toSubject(document: Document): BangumiArchiveSubject =
        BangumiArchiveSubject(
            id = document.getField(BangumiArchiveLuceneFields.STORED_ID).numericValue().toInt(),
            name = document.get(BangumiArchiveLuceneFields.STORED_TITLE),
            nameCn = document.get(BangumiArchiveLuceneFields.STORED_TITLE_CN),
            summary = document.get(BangumiArchiveLuceneFields.STORED_SUMMARY),
            aliases = document.getValues(BangumiArchiveLuceneFields.STORED_ALIAS).toList().distinct(),
            platform = document.get(BangumiArchiveLuceneFields.STORED_PLATFORM),
            date = document.get(BangumiArchiveLuceneFields.STORED_DATE),
            episodeCount = document.getField(BangumiArchiveLuceneFields.STORED_EPISODE_COUNT)?.numericValue()?.toInt() ?: 0,
            score = document.getField(BangumiArchiveLuceneFields.STORED_SCORE)?.numericValue()?.toFloat(),
            rank = document.getField(BangumiArchiveLuceneFields.STORED_RANK)?.numericValue()?.toInt(),
        )

    fun matchSubject(subject: BangumiArchiveSubject, query: String): BangumiArchiveTitleMatch {
        val normalizedQuery = normalizeArchiveIndexedText(query, normalizeText)
        if (normalizedQuery.isBlank()) {
            return BangumiArchiveTitleMatch(
                title = subject.nameCn ?: subject.name,
                confidence = 0f,
            )
        }

        val requestedSeason = extractArchiveSeasonNumber(query) ?: extractArchiveSeasonNumber(normalizedQuery)
        return titleVariants(subject)
            .distinct()
            .map { candidate ->
                BangumiArchiveTitleMatch(
                    title = candidate,
                    confidence = candidate.matchScoreForQuery(
                        normalizedQuery = normalizedQuery,
                        requestedSeason = requestedSeason,
                        normalizeText = normalizeText,
                    ),
                )
            }
            .maxByOrNull { it.confidence }
            ?: BangumiArchiveTitleMatch(
                title = subject.nameCn ?: subject.name,
                confidence = 0f,
            )
    }

    fun matchedTitle(subject: BangumiArchiveSubject, query: String): String =
        matchSubject(subject, query).title

    private fun indexTextField(document: Document, fieldName: String, value: String) {
        val normalized = normalizeArchiveIndexedText(value, normalizeText)
        if (normalized.isBlank()) return
        document.add(TextField(fieldName, normalized, Store.NO))
    }

    private fun titleVariants(subject: BangumiArchiveSubject): List<String> =
        buildList {
            add(subject.name)
            subject.nameCn?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(subject.aliases)
        }.map(String::trim).filter(String::isNotBlank).distinct()

    companion object {
        private const val SUBJECT_TYPE_ANIME = 2
    }
}

internal data class BangumiArchiveTitleMatch(
    val title: String,
    val confidence: Float,
)

internal object BangumiArchiveLuceneFields {
    const val ID = "id"
    const val TITLE = "title"
    const val TITLE_CN = "titleCn"
    const val ALIASES = "aliases"
    const val ALL_TITLES = "allTitles"
    const val ALL_TITLES_EXACT = "allTitlesExact"
    const val ALL_TITLES_SEASONLESS = "allTitlesSeasonless"
    const val STORED_ID = "storedId"
    const val STORED_TITLE = "storedTitle"
    const val STORED_TITLE_CN = "storedTitleCn"
    const val STORED_SUMMARY = "storedSummary"
    const val STORED_ALIAS = "storedAlias"
    const val STORED_PLATFORM = "storedPlatform"
    const val STORED_EPISODE_COUNT = "storedEpisodeCount"
    const val STORED_RANK = "storedRank"
    const val STORED_SCORE = "storedScore"
    const val STORED_DATE = "storedDate"
}

internal fun normalizeArchiveIndexedText(
    text: String,
    normalizeText: (String) -> String,
): String =
    normalizeText(text)
        .lowercase()
        .replace(Regex("\\[[^\\]]*]"), " ")
        .replace(Regex("[_\\-:：/／\\\\()（）【】「」『』.,，!！?？~～＊*+'\"`]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun String.toSeasonlessArchiveText(): String =
    replace(archiveSeasonMarkerRegex, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun String.matchScoreForQuery(
    normalizedQuery: String,
    requestedSeason: Int? = null,
    normalizeText: (String) -> String,
): Float {
    val normalizedCandidate = normalizeArchiveIndexedText(this, normalizeText)
    if (normalizedCandidate.isBlank()) return 0f
    val seasonlessCandidate = normalizedCandidate.toSeasonlessArchiveText()
    val seasonlessQuery = normalizedQuery.toSeasonlessArchiveText()
    val baseScore = when {
        normalizedCandidate == normalizedQuery -> 1.0f
        normalizedCandidate.contains(normalizedQuery) -> 0.9f
        normalizedQuery.contains(normalizedCandidate) &&
            normalizedCandidate.archiveCoverageOf(normalizedQuery) >= 0.72f -> 0.9f
        seasonlessCandidate == seasonlessQuery && seasonlessQuery.isNotBlank() -> 0.88f
        normalizedCandidate.archiveCjkSimilarity(normalizedQuery) >= 0.72f -> 0.82f
        normalizedCandidate.archiveTokenOverlap(normalizedQuery) >= 0.5f -> 0.7f
        normalizedCandidate.archiveCjkSimilarity(normalizedQuery) >= 0.56f -> 0.66f
        else -> 0.2f
    }
    val candidateSeason = extractArchiveSeasonNumber(this) ?: extractArchiveSeasonNumber(normalizedCandidate)
    return adjustArchiveScoreForSeason(baseScore, requestedSeason, candidateSeason).coerceIn(0f, 1f)
}

private fun String.archiveTokenOverlap(other: String): Float {
    val left = archiveTitleTokens()
    val right = other.archiveTitleTokens()
    if (left.isEmpty() || right.isEmpty()) return 0f
    val overlap = left.intersect(right).size
    if (overlap < 2 && left != right) return 0f
    if (overlap == 2 && left.size > overlap && right.size > overlap) {
        val leftOnly = left - right
        val rightOnly = right - left
        if (!leftOnly.hasRelatedArchiveToken(rightOnly)) return 0f
    }
    return overlap.toFloat() / minOf(left.size, right.size).toFloat()
}

private fun String.archiveCjkSimilarity(other: String): Float {
    val left = archiveCjkChars()
    val right = other.archiveCjkChars()
    if (left.isEmpty() || right.isEmpty()) return 0f
    return left.intersect(right).size.toFloat() / maxOf(left.size, right.size).toFloat()
}

private fun String.archiveCoverageOf(other: String): Float {
    val leftCjk = archiveCjkChars()
    val rightCjk = other.archiveCjkChars()
    if (leftCjk.isNotEmpty() && rightCjk.isNotEmpty()) {
        return leftCjk.intersect(rightCjk).size.toFloat() / rightCjk.size.toFloat()
    }

    val leftLength = archiveComparableLength()
    val rightLength = other.archiveComparableLength()
    if (leftLength == 0 || rightLength == 0) return 0f
    return leftLength.toFloat() / rightLength.toFloat()
}

private fun String.archiveTitleTokens(): Set<String> =
    split(' ')
        .map { it.trim() }
        .filter { it.length > 1 }
        .filterNot { it.all(Char::isDigit) }
        .filterNot { it in archiveTokenOverlapStopTokens }
        .toSet()

private fun Set<String>.hasRelatedArchiveToken(other: Set<String>): Boolean {
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

private fun String.archiveCjkChars(): Set<Char> =
    filter { it in '\u4e00'..'\u9fff' }.toSet()

private fun String.archiveComparableLength(): Int =
    count { it.isLetterOrDigit() }

private fun adjustArchiveScoreForSeason(
    baseScore: Float,
    requestedSeason: Int?,
    candidateSeason: Int?,
): Float {
    val season = requestedSeason ?: return baseScore
    return when {
        candidateSeason == season -> maxOf(baseScore, 0.94f)
        candidateSeason != null && candidateSeason != season -> minOf(baseScore, 0.48f)
        baseScore >= 0.9f -> 0.66f
        baseScore >= 0.7f -> 0.58f
        else -> baseScore
    }
}

internal fun extractArchiveSeasonNumber(text: String): Int? {
    val trimmed = text.trim()
    archiveSeasonPatterns.forEach { pattern ->
        val match = pattern.find(trimmed) ?: return@forEach
        val raw = match.groups["num"]?.value ?: return@forEach
        return parseArchiveSeasonNumber(raw)
    }
    return null
}

private fun parseArchiveSeasonNumber(raw: String): Int? {
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
        else -> parseArchiveCjkNumber(raw)
    }
}

private fun parseArchiveCjkNumber(raw: String): Int? {
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

@Serializable
private data class BangumiArchiveSubjectRecord(
    val id: Int,
    val type: Int,
    val name: String,
    @SerialName("name_cn") val nameCn: String? = null,
    val summary: String? = null,
    val infobox: JsonElement? = null,
    val platform: String? = null,
    val date: String? = null,
    val eps: Int? = null,
    @SerialName("total_episodes") val totalEpisodes: Int? = null,
    val score: Float? = null,
    val rank: Int? = null,
    @SerialName("meta_tags") val metaTags: List<String> = emptyList(),
)

private fun JsonElement?.extractInfoboxAliases(): List<String> =
    when (this) {
        null -> emptyList()
        is JsonArray -> jsonArray.flatMap { it.extractInfoboxAliases() }
        is JsonObject -> extractStructuredInfoboxAliases()
        else -> jsonPrimitive.contentOrNull?.extractWikiAliases().orEmpty()
    }

private fun JsonObject.extractStructuredInfoboxAliases(): List<String> {
    val itemKey = stringValue("key")
    val itemValue = jsonObject["value"]
    if (itemKey != null && itemValue != null) {
        return if (itemKey in infoboxAliasKeys) itemValue.extractInfoboxValueAliases() else emptyList()
    }

    return jsonObject.flatMap { (key, value) ->
        if (key in infoboxAliasKeys) {
            value.extractInfoboxValueAliases()
        } else {
            value.extractInfoboxAliases()
        }
    }
}

private fun JsonElement?.extractInfoboxValueAliases(): List<String> =
    when (this) {
        null -> emptyList()
        is JsonArray -> jsonArray.flatMap { it.extractInfoboxValueAliases() }
        is JsonObject -> stringValue("v")?.extractStructuredAliases()
            ?: jsonObject.values.flatMap { it.extractInfoboxValueAliases() }
        else -> jsonPrimitive.contentOrNull?.extractStructuredAliases().orEmpty()
    }

private fun JsonObject.stringValue(key: String): String? =
    jsonObject[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

private fun String.extractWikiAliases(): List<String> =
    lines().flatMap { line ->
        val trimmed = line.trim().trimStart('|').trim()
        val key = trimmed.substringBefore('=', "").trim()
        if (key !in infoboxAliasKeys) return@flatMap emptyList()
        trimmed.substringAfter('=', "")
            .replace(Regex("""\{\{[^{}]*}}"""), " ")
            .replace(Regex("""\[\[([^]|]+)(?:\|[^]]+)?]]"""), "$1")
            .split('\n', ';', '；', '、')
            .map { it.trimWikiValue() }
            .filter { it.isNotBlank() }
    }

private fun String.extractStructuredAliases(): List<String> =
    split('\n', ';', '；', '、')
        .map { it.trimWikiValue() }
        .filter { it.isNotBlank() }

private fun String.trimWikiValue(): String =
    replace(Regex("""<[^>]*>"""), " ")
        .replace(Regex("""'{2,}"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '"', '\'', '[', ']', '{', '}')

private val archiveSeasonMarkerRegex = Regex(
    """(?i)(\bseason\s*\d+\b|\bs\s*\d+\b|\b\d{1,2}(?:st|nd|rd|th)\s+season\b|第\s*[一二三四五六七八九十\d]+\s*[季期]|第\s*\d+\s*季|[ivx]{2,4}\b)"""
)

private val archiveSeasonPatterns = listOf(
    Regex("""(?i)\bs\s*(?<num>\d{1,2})\b"""),
    Regex("""(?i)\bseason\s*(?<num>\d{1,2})\b"""),
    Regex("""(?i)\b(?<num>\d{1,2})(?:st|nd|rd|th)?\s+season\b"""),
    Regex("""第\s*(?<num>\d{1,2}|[一二三四五六七八九十]+)\s*[季期]"""),
    Regex("""(?i)\b(?<num>II|III|IV|V|VI|VII|VIII|IX|X)\b"""),
)

private val infoboxAliasKeys = setOf(
    "中文名",
    "别名",
    "其他名称",
    "英文名",
    "日文名",
)

private val archiveTokenOverlapStopTokens = setOf(
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
