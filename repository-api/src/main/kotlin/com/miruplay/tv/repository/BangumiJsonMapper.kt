package com.miruplay.tv.repository

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class BangumiEpisodeMetadata(
    val episodeNumber: Int,
    val title: String? = null,
    val airDate: String? = null,
    val summary: String? = null,
    val thumbnailUrl: String? = null,
    val isSpecial: Boolean = false,
    val bangumiEpisodeId: Int? = null,
    val durationMs: Long = 0L,
    val collectionType: Int? = null,
)

object BangumiJsonMapper {
    fun parseSearchResults(
        root: JsonObject,
        query: String,
        normalizeQuery: (String) -> String = { it },
    ): List<ScraperResult> {
        val searchQuery = normalizeQuery(query)
        val items = root["data"]?.jsonArray ?: return emptyList()
        return items.mapIndexedNotNull { index, item ->
            val obj = item.jsonObject
            val id = obj.int("id") ?: return@mapIndexedNotNull null
            val title = obj.string("name").orEmpty()
            val titleCn = obj.string("name_cn")
            val score = obj.float("score") ?: obj.obj("rating")?.float("score") ?: 0f
            val aliases = obj.extractSubjectAliases().map(normalizeQuery)
            ScraperResult(
                animeId = id.toString(),
                title = title,
                titleCn = titleCn?.ifBlank { null },
                matchedTitle = titleCn?.ifBlank { title } ?: title,
                confidence = calculateConfidence(searchQuery, aliases, score, index),
                source = ScraperSource.BANGUMI,
            )
        }.sortedByDescending { it.confidence }
    }

    fun parseSubject(obj: JsonObject, id: Int): Anime {
        val images = obj.obj("images")
        val rating = obj.obj("rating")
        val collection = obj.obj("collection")
        val genres = obj["tags"]?.jsonArray?.mapNotNull { tag ->
            tag.jsonObject.string("name")
        }?.take(12).orEmpty().ifEmpty {
            obj["meta_tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        }

        return Anime(
            id = id.toString(),
            title = obj.string("name").orEmpty(),
            titleCn = obj.string("name_cn")?.ifBlank { null },
            summary = obj.string("summary").orEmpty().stripHtml(),
            genres = genres,
            episodeCount = obj.int("eps") ?: obj.int("total_episodes") ?: 0,
            airDate = obj.string("date")?.ifBlank { null },
            rating = rating?.float("score") ?: 0f,
            bangumiId = id,
            posterUrl = images?.string("large") ?: images?.string("common") ?: images?.string("grid"),
            fanartUrl = null,
            bangumiEpStatus = collection?.int("doing") ?: 0,
        )
    }

    fun parseEpisodeMetadata(obj: JsonObject): BangumiEpisodeMetadata? {
        val type = obj.int("type") ?: 0
        val episodeNumber = obj.float("ep")?.toInt()
            ?: obj.float("sort")?.toInt()
            ?: return null

        return BangumiEpisodeMetadata(
            episodeNumber = episodeNumber,
            title = obj.string("name_cn")?.ifBlank { null } ?: obj.string("name")?.ifBlank { null },
            airDate = obj.string("airdate")?.ifBlank { null },
            summary = obj.string("desc")?.ifBlank { null },
            isSpecial = type != 0,
            bangumiEpisodeId = obj.int("id"),
            durationMs = (obj.int("duration_seconds") ?: 0).toLong() * 1_000L,
        )
    }

    fun parseSubjectCollection(obj: JsonObject): BangumiSubjectCollection =
        BangumiSubjectCollection(
            subjectId = obj.int("subject_id") ?: 0,
            type = obj.int("type") ?: 0,
            rate = obj.int("rate") ?: 0,
            epStatus = obj.int("ep_status") ?: 0,
            updatedAt = obj.string("updated_at"),
        )

    fun parseEpisodeCollection(obj: JsonObject): BangumiEpisodeCollection? {
        val episode = obj.obj("episode") ?: return null
        val episodeNumber = episode.float("ep")?.toInt()
            ?: episode.float("sort")?.toInt()
            ?: return null

        return BangumiEpisodeCollection(
            episodeId = episode.int("id") ?: return null,
            episodeNumber = episodeNumber,
            type = obj.int("type") ?: 0,
            updatedAt = obj.long("updated_at") ?: 0L,
        )
    }

    fun calculateConfidence(query: String, candidates: List<String>, score: Float, serverIndex: Int): Float {
        val normalizedQuery = query.normalizedTitle()
        val normalizedCandidates = candidates.map { it.normalizedTitle() }.filter { it.isNotBlank() }
        val base = when {
            normalizedCandidates.any { it == normalizedQuery } -> 1.0f
            normalizedCandidates.any { it.contains(normalizedQuery) } -> 0.9f
            normalizedCandidates.any { normalizedQuery.contains(it) && it.coverageOf(normalizedQuery) >= 0.72f } -> 0.9f
            normalizedCandidates.any { it.cjkSimilarity(normalizedQuery) >= 0.72f } -> 0.82f
            normalizedCandidates.any { it.tokenOverlap(normalizedQuery) >= 0.5f } -> 0.7f
            normalizedCandidates.any { it.cjkSimilarity(normalizedQuery) >= 0.56f } -> 0.66f
            else -> 0.2f
        }
        val scoreBoost = if (base >= 0.62f) (score / 200f).coerceAtMost(0.04f) else 0f
        val serverOrderBoost = if (serverIndex == 0 && base >= 0.62f) 0.03f else 0f
        return (base + scoreBoost + serverOrderBoost).coerceIn(0f, 1f)
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
private fun JsonObject.float(key: String): Float? = this[key]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
private fun JsonObject.obj(key: String): JsonObject? = runCatching { this[key]?.jsonObject }.getOrNull()
private fun String.stripHtml(): String = replace(Regex("<[^>]*>"), "").trim()

private fun JsonObject.extractSubjectAliases(): List<String> = buildList {
    string("name")?.let(::add)
    string("name_cn")?.let(::add)
    this@extractSubjectAliases["infobox"]?.jsonArray?.forEach { item ->
        val obj = runCatching { item.jsonObject }.getOrNull() ?: return@forEach
        val key = obj.string("key").orEmpty()
        if (key == "中文名" || key == "别名") {
            addInfoboxValue(obj["value"])
        }
    }
}.map { it.trim() }.filter { it.isNotBlank() }.distinct()

private fun MutableList<String>.addInfoboxValue(value: JsonElement?) {
    when (value) {
        null -> Unit
        is JsonArray -> value.forEach { child ->
            val obj = runCatching { child.jsonObject }.getOrNull()
            val text = obj?.string("v") ?: child.jsonPrimitive.contentOrNull
            if (!text.isNullOrBlank()) add(text)
        }
        is JsonObject -> value.string("v")?.takeIf { it.isNotBlank() }?.let(::add)
        else -> value.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }?.let(::add)
    }
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
