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
        val candidates = items.mapIndexedNotNull { index, item ->
            val obj = item.jsonObject
            val id = obj.int("id") ?: return@mapIndexedNotNull null
            val title = obj.string("name").orEmpty()
            val titleCn = obj.string("name_cn")
            val score = obj.float("score") ?: obj.obj("rating")?.float("score") ?: 0f
            val aliases = obj.extractSubjectAliases().map(normalizeQuery)
            BangumiSubjectMatchCandidate(
                id = id.toString(),
                title = title,
                titleCn = titleCn?.ifBlank { null },
                aliases = aliases,
                score = score,
                serverIndex = index,
            )
        }
        return BangumiSubjectMatcher.rank(searchQuery, candidates).map { match ->
            ScraperResult(
                animeId = match.candidate.id,
                title = match.candidate.title,
                titleCn = match.candidate.titleCn,
                matchedTitle = match.candidate.titleCn ?: match.candidate.title,
                confidence = match.confidence,
                source = ScraperSource.BANGUMI,
            )
        }
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
        return BangumiSubjectMatcher.calculateConfidence(query, candidates, score, serverIndex)
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
