package com.miruplay.tv.scraper.core

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScraperResult
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
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override val hasToken: Boolean
        get() = !tokenProvider().isNullOrBlank()

    fun configureProxy(proxyConfig: BangumiHttpProxyConfig) {
        client.configureProxy(proxyConfig)
    }

    suspend fun searchAnime(query: String): Result<List<ScraperResult>> = withContext(Dispatchers.IO) {
        try {
            archiveSearch?.search(query)?.takeIf { it.isNotEmpty() }?.let { localResults ->
                return@withContext Result.success(localResults)
            }

            val searchKeyword = normalizeQuery(query.trim())
            val request = buildRequest(
                apiUrl("/v0/search/subjects")
                    .addQueryParameter("limit", "10")
                    .addQueryParameter("offset", "0")
                    .build()
            ).post(BangumiApiPayloads.searchSubjects(searchKeyword, SUBJECT_TYPE_ANIME).toString().toRequestBody(mediaType)).build()

            val root = executeJson(request).jsonObject
            Result.success(BangumiJsonMapper.parseSearchResults(root, query, normalizeQuery))
        } catch (error: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(SOURCE_NAME, error.message ?: "Unknown error"))
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
                .post(BangumiApiPayloads.subjectCollection(type).toString().toRequestBody(mediaType))
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
                .patch(BangumiApiPayloads.episodeCollections(episodeIds, type).toString().toRequestBody(mediaType))
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
                .put(BangumiApiPayloads.episodeCollection(type).toString().toRequestBody(mediaType))
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

    companion object {
        const val DEFAULT_BASE_URL = "https://api.bgm.tv"
        const val DEFAULT_USER_AGENT = "MiruPlay/1.0"
        const val SOURCE_NAME = "Bangumi"

        private const val SUBJECT_TYPE_ANIME = 2

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}

private fun kotlinx.serialization.json.JsonObject.jsonString(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private fun kotlinx.serialization.json.JsonObject.jsonInt(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull
