package com.miruplay.tv.scraper

import android.icu.text.Transliterator
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.secure.SecurePreferencesManager
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BangumiScraper @Inject constructor(
    private val securePrefs: SecurePreferencesManager
) : MetadataScraper {

    override val sourceName: String = "Bangumi"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val API_BASE = "https://api.bgm.tv"
        private const val USER_AGENT = "MiruPlay/1.0 (Android TV; https://github.com/open-ani/animeko-inspired-local-client)"
        private const val SUBJECT_TYPE_ANIME = 2
    }

    val hasToken: Boolean
        get() = !securePrefs.bangumiAccessToken.isNullOrBlank()

    override suspend fun searchAnime(query: String): Result<List<ScraperResult>> = withContext(Dispatchers.IO) {
        try {
            val searchKeyword = query.toSimplifiedChinese()
            val body = buildJsonObject {
                put("keyword", searchKeyword)
                put("sort", "match")
                put("filter", buildJsonObject {
                    put("type", buildJsonArray { add(SUBJECT_TYPE_ANIME) })
                })
            }

            val request = buildRequest("$API_BASE/v0/search/subjects?limit=10&offset=0")
                .post(body.toString().toRequestBody(mediaType))
                .build()

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
                    source = ScraperSource.BANGUMI
                )
            }.sortedByDescending { it.confidence }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(sourceName, e.message ?: "Unknown error"))
        }
    }

    override suspend fun getAnimeDetails(animeId: String): Result<Anime> = withContext(Dispatchers.IO) {
        try {
            val idInt = animeId.toInt()
            val request = buildRequest("$API_BASE/v0/subjects/$animeId").get().build()
            val obj = executeJson(request).jsonObject

            Result.success(parseSubject(obj, idInt))
        } catch (e: NumberFormatException) {
            Result.failure(AppError.ScrapingError.NoMatchFound(animeId))
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(sourceName, e.message ?: "Unknown error"))
        }
    }

    override suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>> = withContext(Dispatchers.IO) {
        try {
            val result = mutableListOf<EpisodeMetadata>()
            var offset = 0
            var total = 0
            do {
                val url = "$API_BASE/v0/episodes?subject_id=$animeId&type=0&limit=200&offset=$offset"
                val root = executeJson(buildRequest(url).get().build()).jsonObject
                val page = root["data"]?.jsonArray ?: JsonArray(emptyList())
                result += page.mapNotNull { parseEpisodeMetadata(it.jsonObject) }
                offset += page.size
                total = root.int("total") ?: result.size
            } while (offset < total && page.isNotEmpty())

            Result.success(result.sortedBy { it.episodeNumber })
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(sourceName, e.message ?: "Unknown error"))
        }
    }

    override suspend fun searchByAlias(normalizedName: String, candidates: List<String>): Result<ScraperResult?> =
        withContext(Dispatchers.IO) {
            try {
                val uniqueCandidates = (listOf(normalizedName) + candidates)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                for (candidate in uniqueCandidates) {
                    val result = searchAnime(candidate).getOrNull()?.firstOrNull()
                    if (result != null && result.confidence >= 0.62f) {
                        return@withContext Result.success(result)
                    }
                }
                Result.success(null)
            } catch (e: Exception) {
                Result.success(null)
            }
        }

    suspend fun getCurrentUser(): Result<BangumiUser> = withContext(Dispatchers.IO) {
        try {
            requireToken()
            val obj = executeJson(buildRequest("$API_BASE/v0/me").get().build()).jsonObject
            Result.success(
                BangumiUser(
                    id = obj.int("id") ?: 0,
                    username = obj.string("username").orEmpty(),
                    nickname = obj.string("nickname").orEmpty()
                )
            )
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(sourceName, e.message ?: "Bangumi authorization failed"))
        }
    }

    suspend fun getSubjectCollection(subjectId: Int): Result<BangumiSubjectCollection?> = withContext(Dispatchers.IO) {
        try {
            requireToken()
            val request = buildRequest("$API_BASE/v0/users/-/collections/$subjectId").get().build()
            val response = client.newCall(request).execute()
            response.use {
                if (it.code == 404) return@withContext Result.success(null)
                if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}: ${it.message}")
                val body = it.body?.string().orEmpty()
                val obj = json.parseToJsonElement(body).jsonObject
                Result.success(parseSubjectCollection(obj))
            }
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(sourceName, e.message ?: "Failed to read collection"))
        }
    }

    suspend fun upsertSubjectCollection(
        subjectId: Int,
        type: BangumiSubjectCollectionType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireToken()
            val body = buildJsonObject { put("type", type.value) }
            val request = buildRequest("$API_BASE/v0/users/-/collections/$subjectId")
                .post(body.toString().toRequestBody(mediaType))
                .build()
            executeNoContent(request)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(sourceName, e.message ?: "Failed to update collection"))
        }
    }

    suspend fun getEpisodeCollections(subjectId: Int): Result<List<BangumiEpisodeCollection>> = withContext(Dispatchers.IO) {
        try {
            requireToken()
            val result = mutableListOf<BangumiEpisodeCollection>()
            var offset = 0
            var total = 0
            do {
                val url = "$API_BASE/v0/users/-/collections/$subjectId/episodes?episode_type=0&limit=1000&offset=$offset"
                val root = executeJson(buildRequest(url).get().build()).jsonObject
                val page = root["data"]?.jsonArray ?: JsonArray(emptyList())
                result += page.mapNotNull { parseEpisodeCollection(it.jsonObject) }
                offset += page.size
                total = root.int("total") ?: result.size
            } while (offset < total && page.isNotEmpty())

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(sourceName, e.message ?: "Failed to read episode collection"))
        }
    }

    suspend fun updateEpisodeCollections(
        subjectId: Int,
        episodeIds: List<Int>,
        type: BangumiEpisodeCollectionType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireToken()
            if (episodeIds.isEmpty()) return@withContext Result.success(Unit)
            val body = buildJsonObject {
                put("episode_id", buildJsonArray { episodeIds.distinct().forEach { add(it) } })
                put("type", type.value)
            }
            val request = buildRequest("$API_BASE/v0/users/-/collections/$subjectId/episodes")
                .patch(body.toString().toRequestBody(mediaType))
                .build()
            executeNoContent(request)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(sourceName, e.message ?: "Failed to update episode collection"))
        }
    }

    suspend fun updateEpisodeCollection(
        episodeId: Int,
        type: BangumiEpisodeCollectionType
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireToken()
            val body = buildJsonObject { put("type", type.value) }
            val request = buildRequest("$API_BASE/v0/users/-/collections/-/episodes/$episodeId")
                .put(body.toString().toRequestBody(mediaType))
                .build()
            executeNoContent(request)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(sourceName, e.message ?: "Failed to update episode"))
        }
    }

    private fun buildRequest(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")

        val token = securePrefs.bangumiAccessToken
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }

        return builder
    }

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

    private fun executeNoContent(request: Request) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw IllegalStateException("HTTP ${response.code}: ${body.ifBlank { response.message }}")
            }
        }
    }

    private fun requireToken() {
        if (!hasToken) {
            throw IllegalStateException("Bangumi Access Token is required")
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
            fanartUrl = null,
            bangumiEpStatus = collection?.int("doing") ?: 0
        )
    }

    private fun parseEpisodeMetadata(obj: JsonObject): EpisodeMetadata? {
        val type = obj.int("type") ?: 0
        val episodeNumber = obj.float("ep")?.toInt()
            ?: obj.float("sort")?.toInt()
            ?: return null

        return EpisodeMetadata(
            episodeNumber = episodeNumber,
            title = obj.string("name_cn")?.ifBlank { null } ?: obj.string("name")?.ifBlank { null },
            airDate = obj.string("airdate")?.ifBlank { null },
            summary = obj.string("desc")?.ifBlank { null },
            isSpecial = type != 0,
            bangumiEpisodeId = obj.int("id"),
            durationMs = (obj.int("duration_seconds") ?: 0).toLong() * 1000L
        )
    }

    private fun parseSubjectCollection(obj: JsonObject): BangumiSubjectCollection =
        BangumiSubjectCollection(
            subjectId = obj.int("subject_id") ?: 0,
            type = obj.int("type") ?: 0,
            rate = obj.int("rate") ?: 0,
            epStatus = obj.int("ep_status") ?: 0,
            updatedAt = obj.string("updated_at")
        )

    private fun parseEpisodeCollection(obj: JsonObject): BangumiEpisodeCollection? {
        val episode = obj.obj("episode") ?: return null
        val episodeNumber = episode.float("ep")?.toInt()
            ?: episode.float("sort")?.toInt()
            ?: return null

        return BangumiEpisodeCollection(
            episodeId = episode.int("id") ?: return null,
            episodeNumber = episodeNumber,
            type = obj.int("type") ?: 0,
            updatedAt = obj.long("updated_at") ?: 0L
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
        .toSimplifiedChinese()
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

private fun String.toSimplifiedChinese(): String =
    ChineseTransliterator.toSimplified(this)

private object ChineseTransliterator {
    private val traditionalToSimplified = runCatching {
        Transliterator.getInstance("Traditional-Simplified")
    }.getOrNull()

    fun toSimplified(text: String): String =
        traditionalToSimplified?.transliterate(text) ?: text
}
