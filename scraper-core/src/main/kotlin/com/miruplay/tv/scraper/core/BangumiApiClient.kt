package com.miruplay.tv.scraper.core

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import com.miruplay.tv.repository.BangumiApiPayloads
import com.miruplay.tv.repository.BangumiCollectionService
import com.miruplay.tv.repository.BangumiEpisodeCollection
import com.miruplay.tv.repository.BangumiEpisodeCollectionType
import com.miruplay.tv.repository.BangumiEpisodeMetadata
import com.miruplay.tv.repository.BangumiJsonMapper
import com.miruplay.tv.repository.BangumiSubjectCollection
import com.miruplay.tv.repository.BangumiSubjectCollectionType
import com.miruplay.tv.repository.BangumiUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class BangumiApiClient(
    baseUrl: String = DEFAULT_BASE_URL,
    private val tokenProvider: () -> String? = { null },
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val normalizeQuery: (String) -> String = { it },
    private val archiveSearch: BangumiArchiveSubjectSearch? = null,
) : BangumiCollectionService {
    private val baseHttpUrl: HttpUrl = baseUrl.toHttpUrl()
    private val client = BangumiProxyAwareOkHttpClient(defaultClient())
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()

    override val hasToken: Boolean
        get() = !tokenProvider().isNullOrBlank()

    fun configureProxy(proxyConfig: BangumiHttpProxyConfig) {
        client.configureProxy(proxyConfig)
    }

    suspend fun searchAnime(query: String): Result<List<ScraperResult>> =
        searchAnime(
            query = query,
            archiveMinimumConfidence = ARCHIVE_AUTO_MINIMUM_CONFIDENCE,
            includeOnlineWhenArchiveMatches = false,
            includeDirectIdResult = false,
            onlineLimit = 10,
        )

    suspend fun searchAnimeForManualMatch(query: String): Result<List<ScraperResult>> =
        searchAnime(
            query = query,
            archiveMinimumConfidence = ARCHIVE_MANUAL_MINIMUM_CONFIDENCE,
            includeOnlineWhenArchiveMatches = true,
            includeDirectIdResult = true,
            onlineLimit = 20,
        )

    private suspend fun searchAnime(
        query: String,
        archiveMinimumConfidence: Float,
        includeOnlineWhenArchiveMatches: Boolean,
        includeDirectIdResult: Boolean,
        onlineLimit: Int,
    ): Result<List<ScraperResult>> = withContext(Dispatchers.IO) {
        try {
            val merged = linkedMapOf<String, ScraperResult>()
            if (includeDirectIdResult) {
                directIdSearchResult(query)?.let { merged.addBest(it) }
            }

            val localResults = archiveSearch?.search(
                query = query,
                limit = onlineLimit,
                minimumConfidence = archiveMinimumConfidence,
            ).orEmpty()
            localResults.forEach { merged.addBest(it) }
            if (localResults.isNotEmpty() && !includeOnlineWhenArchiveMatches) {
                return@withContext Result.success(localResults)
            }

            try {
                searchOnlineAnime(query, onlineLimit).forEach { merged.addBest(it) }
            } catch (onlineError: Exception) {
                if (merged.isEmpty()) throw onlineError
            }
            Result.success(merged.values.sortedByDescending { it.confidence })
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Unknown error"))
        }
    }

    private fun searchOnlineAnime(query: String, limit: Int): List<ScraperResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val searchKeyword = normalizeQuery(trimmed)
        val request = buildRequest(
            apiUrl("/v0/search/subjects")
                .addQueryParameter("limit", limit.coerceAtLeast(1).toString())
                .addQueryParameter("offset", "0")
                .build()
        ).post(BangumiApiPayloads.searchSubjects(searchKeyword, SUBJECT_TYPE_ANIME).toRequestBody()).build()

        val root = executeJson(request).jsonObject
        return BangumiJsonMapper.parseSearchResults(root, query, normalizeQuery)
    }

    private suspend fun directIdSearchResult(query: String): ScraperResult? {
        val animeId = query.trim().takeIf { it.isNotBlank() && it.all(Char::isDigit) } ?: return null
        archiveSearch?.findById(animeId)?.let { archived ->
            return archived.toSearchResult(confidence = 1f, fromLocalArchive = true)
        }

        return getOnlineAnimeDetails(animeId).getOrNull()?.toSearchResult(confidence = 1f)
    }

    private fun LinkedHashMap<String, ScraperResult>.addBest(result: ScraperResult) {
        val existing = this[result.animeId]
        if (existing == null || result.confidence > existing.confidence) {
            this[result.animeId] = result
        }
    }

    suspend fun getAnimeDetails(animeId: String): Result<Anime> = withContext(Dispatchers.IO) {
        archiveSearch?.findById(animeId)?.let { archived ->
            return@withContext Result.success(archived.toAnime())
        }
        getOnlineAnimeDetails(animeId)
    }

    suspend fun getOnlineAnimeDetails(animeId: String): Result<Anime> = withContext(Dispatchers.IO) {
        try {
            val id = animeId.toInt()
            val request = buildRequest(apiUrl("/v0/subjects/$animeId").build()).get().build()
            Result.success(BangumiJsonMapper.parseSubject(executeJson(request).jsonObject, id))
        } catch (error: NumberFormatException) {
            Result.failure(AppError.ScrapingError.NoMatchFound(animeId))
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Unknown error"))
        }
    }

    suspend fun getEpisodes(animeId: String): Result<List<BangumiEpisodeMetadata>> = withContext(Dispatchers.IO) {
        try {
            val result = mutableListOf<BangumiEpisodeMetadata>()
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
                result += page.mapNotNull { BangumiJsonMapper.parseEpisodeMetadata(it.jsonObject) }
                offset += page.size
                total = root.jsonInt("total") ?: result.size
            } while (offset < total && page.isNotEmpty())

            Result.success(result.sortedBy { it.episodeNumber })
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Unknown error"))
        }
    }

    override suspend fun getCurrentUser(): Result<BangumiUser> = withContext(Dispatchers.IO) {
        try {
            requireToken()
            val obj = executeJson(buildRequest(apiUrl("/v0/me").build()).get().build()).jsonObject
            Result.success(
                BangumiUser(
                    id = obj.jsonInt("id") ?: 0,
                    username = obj.jsonString("username").orEmpty(),
                    nickname = obj.jsonString("nickname").orEmpty(),
                )
            )
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Bangumi authorization failed"))
        }
    }

    override suspend fun getSubjectCollection(subjectId: Int): Result<BangumiSubjectCollection?> = withContext(Dispatchers.IO) {
        try {
            requireToken()
            val request = buildRequest(apiUrl("/v0/users/-/collections/$subjectId").build()).get().build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext Result.success(null)
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: ${body.ifBlank { response.message }}")
                }
                if (body.isBlank()) throw IllegalStateException("Empty response")
                Result.success(BangumiJsonMapper.parseSubjectCollection(json.parseToJsonElement(body).jsonObject))
            }
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Failed to read collection"))
        }
    }

    override suspend fun upsertSubjectCollection(
        subjectId: Int,
        type: BangumiSubjectCollectionType,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireToken()
            val request = buildRequest(apiUrl("/v0/users/-/collections/$subjectId").build())
                .post(BangumiApiPayloads.subjectCollection(type).toRequestBody())
                .build()
            executeNoContent(request)
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Failed to update collection"))
        }
    }

    override suspend fun getEpisodeCollections(subjectId: Int): Result<List<BangumiEpisodeCollection>> = withContext(Dispatchers.IO) {
        try {
            requireToken()
            val result = mutableListOf<BangumiEpisodeCollection>()
            var offset = 0
            var total = 0
            do {
                val request = buildRequest(
                    apiUrl("/v0/users/-/collections/$subjectId/episodes")
                        .addQueryParameter("episode_type", "0")
                        .addQueryParameter("limit", "1000")
                        .addQueryParameter("offset", offset.toString())
                        .build()
                ).get().build()
                val root = executeJson(request).jsonObject
                val page = root["data"]?.jsonArray ?: JsonArray(emptyList())
                result += page.mapNotNull { BangumiJsonMapper.parseEpisodeCollection(it.jsonObject) }
                offset += page.size
                total = root.jsonInt("total") ?: result.size
            } while (offset < total && page.isNotEmpty())

            Result.success(result)
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Failed to read episode collection"))
        }
    }

    override suspend fun updateEpisodeCollections(
        subjectId: Int,
        episodeIds: List<Int>,
        type: BangumiEpisodeCollectionType,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireToken()
            if (episodeIds.isEmpty()) return@withContext Result.success(Unit)
            val request = buildRequest(apiUrl("/v0/users/-/collections/$subjectId/episodes").build())
                .patch(BangumiApiPayloads.episodeCollections(episodeIds, type).toRequestBody())
                .build()
            executeNoContent(request)
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Failed to update episode collection"))
        }
    }

    override suspend fun updateEpisodeCollection(
        episodeId: Int,
        type: BangumiEpisodeCollectionType,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireToken()
            val request = buildRequest(apiUrl("/v0/users/-/collections/-/episodes/$episodeId").build())
                .put(BangumiApiPayloads.episodeCollection(type).toRequestBody())
                .build()
            executeNoContent(request)
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Failed to update episode"))
        }
    }

    private fun apiUrl(path: String): HttpUrl.Builder =
        baseHttpUrl.newBuilder().encodedPath(path)

    private fun buildRequest(url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .addHeader("User-Agent", userAgent)
            .addHeader("Accept", "application/json")
            .apply {
                val token = tokenProvider()
                if (!token.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $token")
                }
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

    private fun JsonObject.toRequestBody() =
        toString().toByteArray(Charsets.UTF_8).toRequestBody(mediaType)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.bgm.tv"
        const val DEFAULT_USER_AGENT = "ModerRAS/MiruPlay/0.1.0 (https://github.com/ModerRAS/MiruPlay)"
        const val SOURCE_NAME = "Bangumi"

        private const val SUBJECT_TYPE_ANIME = 2
        private const val ARCHIVE_AUTO_MINIMUM_CONFIDENCE = 0.62f
        private const val ARCHIVE_MANUAL_MINIMUM_CONFIDENCE = 0.5f

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}

private fun Anime.toSearchResult(
    confidence: Float,
    fromLocalArchive: Boolean = false,
): ScraperResult =
    ScraperResult(
        animeId = bangumiId?.toString() ?: id,
        title = title,
        titleCn = titleCn,
        matchedTitle = titleCn ?: title,
        confidence = confidence,
        source = ScraperSource.BANGUMI,
        fromLocalArchive = fromLocalArchive,
    )

private fun BangumiArchiveSubject.toSearchResult(
    confidence: Float,
    fromLocalArchive: Boolean = true,
): ScraperResult =
    ScraperResult(
        animeId = id.toString(),
        title = name,
        titleCn = nameCn,
        matchedTitle = nameCn ?: name,
        confidence = confidence,
        source = ScraperSource.BANGUMI,
        fromLocalArchive = fromLocalArchive,
    )

private fun kotlinx.serialization.json.JsonObject.jsonString(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private fun kotlinx.serialization.json.JsonObject.jsonInt(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull
