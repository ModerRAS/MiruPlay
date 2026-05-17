package com.miruplay.tv.scraper.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class DesktopBangumiScraper internal constructor(
    private val baseUrl: HttpUrl,
    private val client: OkHttpClient,
) {
    constructor() : this(DEFAULT_BASE_URL, defaultClient())
    internal constructor(baseUrl: HttpUrl) : this(baseUrl, defaultClient())

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun searchAnime(query: String): Result<List<ScraperResult>> = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("keyword", query.trim())
                put("sort", "match")
                put("filter", buildJsonObject {
                    put("type", buildJsonArray { add(SUBJECT_TYPE_ANIME) })
                })
            }
            val request = buildRequest(
                apiUrl("/v0/search/subjects")
                    .addQueryParameter("limit", "10")
                    .addQueryParameter("offset", "0")
                    .build()
            ).post(body.toString().toRequestBody(mediaType)).build()

            val root = executeJson(request).jsonObject
            val items = root["data"]?.jsonArray ?: return@withContext Result.success(emptyList())
            val results = items.mapIndexedNotNull { index, item ->
                val obj = item.jsonObject
                val id = obj.int("id") ?: return@mapIndexedNotNull null
                val title = obj.string("name").orEmpty()
                val titleCn = obj.string("name_cn")
                val score = obj.float("score") ?: obj.obj("rating")?.float("score") ?: 0f
                val aliases = obj.extractSubjectAliases()
                ScraperResult(
                    animeId = id.toString(),
                    title = title,
                    titleCn = titleCn?.ifBlank { null },
                    matchedTitle = titleCn?.ifBlank { title } ?: title,
                    confidence = calculateConfidence(query, aliases, score, index),
                    source = ScraperSource.BANGUMI,
                )
            }.sortedByDescending { it.confidence }

            Result.success(results)
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Unknown error"))
        }
    }

    suspend fun getAnimeDetails(animeId: String): Result<Anime> = withContext(Dispatchers.IO) {
        try {
            val id = animeId.toInt()
            val request = buildRequest(apiUrl("/v0/subjects/$animeId").build()).get().build()
            Result.success(parseSubject(executeJson(request).jsonObject, id))
        } catch (error: NumberFormatException) {
            Result.failure(AppError.ScrapingError.NoMatchFound(animeId))
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Unknown error"))
        }
    }

    suspend fun getEpisodes(animeId: String): Result<List<DesktopEpisodeMetadata>> = withContext(Dispatchers.IO) {
        try {
            val result = mutableListOf<DesktopEpisodeMetadata>()
            var offset = 0
            var total = 0
            do {
                val request = buildRequest(
                    apiUrl("/v0/episodes")
                        .addQueryParameter("subject_id", animeId)
                        .addQueryParameter("type", "0")
                        .addQueryParameter("limit", "200")
                        .addQueryParameter("offset", offset.toString())
                        .build()
                ).get().build()
                val root = executeJson(request).jsonObject
                val page = root["data"]?.jsonArray ?: JsonArray(emptyList())
                result += page.mapNotNull { parseEpisodeMetadata(it.jsonObject) }
                offset += page.size
                total = root.int("total") ?: result.size
            } while (offset < total && page.isNotEmpty())

            Result.success(result.sortedBy { it.episodeNumber })
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Unknown error"))
        }
    }

    private fun apiUrl(path: String): HttpUrl.Builder =
        baseUrl.newBuilder().encodedPath(path)

    private fun buildRequest(url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")

    private fun executeJson(request: Request): JsonElement {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${body.ifBlank { response.message }}")
            }
            if (body.isBlank()) throw IllegalStateException("Empty response")
            return json.parseToJsonElement(body)
        }
    }

    private fun parseSubject(obj: JsonObject, id: Int): Anime {
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
            bangumiEpStatus = collection?.int("doing") ?: 0,
        )
    }

    private fun parseEpisodeMetadata(obj: JsonObject): DesktopEpisodeMetadata? {
        val type = obj.int("type") ?: 0
        val episodeNumber = obj.float("ep")?.toInt()
            ?: obj.float("sort")?.toInt()
            ?: return null

        return DesktopEpisodeMetadata(
            episodeNumber = episodeNumber,
            title = obj.string("name_cn")?.ifBlank { null } ?: obj.string("name")?.ifBlank { null },
            airDate = obj.string("airdate")?.ifBlank { null },
            summary = obj.string("desc")?.ifBlank { null },
            isSpecial = type != 0,
            bangumiEpisodeId = obj.int("id"),
            durationMs = (obj.int("duration_seconds") ?: 0).toLong() * 1_000L,
        )
    }

    private fun calculateConfidence(query: String, candidates: List<String>, score: Float, serverIndex: Int): Float {
        val normalizedQuery = query.normalizedTitle()
        val normalizedCandidates = candidates.map { it.normalizedTitle() }.filter { it.isNotBlank() }
        val base = when {
            normalizedCandidates.any { it == normalizedQuery } -> 1.0f
            normalizedCandidates.any { it.contains(normalizedQuery) || normalizedQuery.contains(it) } -> 0.9f
            normalizedCandidates.any { it.cjkSimilarity(normalizedQuery) >= 0.72f } -> 0.82f
            normalizedCandidates.any { it.tokenOverlap(normalizedQuery) >= 0.5f } -> 0.7f
            normalizedCandidates.any { it.cjkSimilarity(normalizedQuery) >= 0.56f } -> 0.66f
            else -> 0.2f
        }
        val scoreBoost = if (base >= 0.62f) (score / 200f).coerceAtMost(0.04f) else 0f
        val serverOrderBoost = if (serverIndex == 0 && base >= 0.62f) 0.03f else 0f
        return (base + scoreBoost + serverOrderBoost).coerceIn(0f, 1f)
    }

    private companion object {
        val DEFAULT_BASE_URL: HttpUrl = "https://api.bgm.tv".toHttpUrl()
        const val SOURCE_NAME = "Bangumi"
        const val SUBJECT_TYPE_ANIME = 2
        const val USER_AGENT = "MiruPlay/1.0 (Windows Desktop; https://github.com/hooke007/mpv_PlayKit)"

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}

data class DesktopEpisodeMetadata(
    val episodeNumber: Int,
    val title: String? = null,
    val airDate: String? = null,
    val summary: String? = null,
    val thumbnailUrl: String? = null,
    val isSpecial: Boolean = false,
    val bangumiEpisodeId: Int? = null,
    val durationMs: Long = 0L,
)

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
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
        .replace(Regex("[_\\-:：/\\\\()（）【】「」『』.,，!！?？]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun String.tokenOverlap(other: String): Float {
    val left = split(' ').filter { it.isNotBlank() }.toSet()
    val right = other.split(' ').filter { it.isNotBlank() }.toSet()
    if (left.isEmpty() || right.isEmpty()) return 0f
    return left.intersect(right).size.toFloat() / minOf(left.size, right.size).toFloat()
}

private fun String.cjkSimilarity(other: String): Float {
    val left = cjkChars()
    val right = other.cjkChars()
    if (left.isEmpty() || right.isEmpty()) return 0f
    return left.intersect(right).size.toFloat() / maxOf(left.size, right.size).toFloat()
}

private fun String.cjkChars(): Set<Char> =
    filter { it in '\u4e00'..'\u9fff' }.toSet()
