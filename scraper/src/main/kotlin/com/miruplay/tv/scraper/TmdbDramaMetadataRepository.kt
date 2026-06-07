package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.DramaMetadataSearchResult
import com.miruplay.tv.model.DramaEpisodeMetadata
import com.miruplay.tv.model.DramaSeasonMetadata
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.DramaMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbDramaMetadataRepository @Inject constructor(
    private val credentials: AppCredentialStore,
    private val okHttpClient: OkHttpClient,
) : DramaMetadataRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun canFetchSeriesMetadataByTitle(): Boolean =
        credentials.tmdbAccessToken?.trim().isNullOrBlank().not()

    override fun canFetchMetadataByProviderRef(
        providerRef: com.miruplay.tv.model.MetadataProviderRef,
    ): Boolean =
        providerRef.source.equals(SOURCE_NAME, ignoreCase = true) &&
            providerRef.id.toIntOrNull() != null &&
            canFetchSeriesMetadataByTitle()

    override suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> = withContext(Dispatchers.IO) {
        withTmdbResult(onMissingToken = { null }) {
            val searchUrl = apiBaseUrl().toHttpUrl().newBuilder()
                .addPathSegments("3/search/tv")
                .addQueryParameter("query", title)
                .addQueryParameter("language", "zh-CN")
                .build()
            val searchRoot = executeJson(searchUrl.toString(), it).jsonObject
            val firstResult = searchRoot["results"]
                ?.jsonArray
                ?.firstUsefulTvResult(seasonHint)
                ?: return@withTmdbResult null
            val tmdbId = firstResult["id"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: return@withTmdbResult null
            fetchSeriesMetadataInternal(
                token = it,
                tmdbId = tmdbId,
                seasonHint = seasonHint,
                seasonNumbers = seasonNumbers,
                fallbackSearchResult = firstResult,
            )
        }
    }

    override suspend fun searchSeriesCandidates(
        query: String,
        seasonHint: Int?,
        maxResults: Int,
    ): Result<List<DramaMetadataSearchResult>> = withContext(Dispatchers.IO) {
        withTmdbResult(onMissingToken = { emptyList() }) {
            val searchUrl = apiBaseUrl().toHttpUrl().newBuilder()
                .addPathSegments("3/search/tv")
                .addQueryParameter("query", query)
                .addQueryParameter("language", "zh-CN")
                .build()
            val searchRoot = executeJson(searchUrl.toString(), it).jsonObject
            searchRoot["results"]
                ?.jsonArray
                ?.rankedTvResults(seasonHint)
                ?.take(maxResults.coerceAtLeast(1))
                ?.mapNotNull { result -> result.toDramaSearchResult() }
                .orEmpty()
        }
    }

    override suspend fun fetchSeriesMetadataByProviderRef(
        providerRef: com.miruplay.tv.model.MetadataProviderRef,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> {
        if (!providerRef.source.equals(SOURCE_NAME, ignoreCase = true)) {
            return Result.success(null)
        }
        val tmdbId = providerRef.id.toIntOrNull() ?: return Result.success(null)
        return fetchSeriesMetadataById(
            tmdbId = tmdbId,
            seasonNumbers = seasonNumbers,
        )
    }

    suspend fun fetchSeriesMetadataById(
        tmdbId: Int,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> = withContext(Dispatchers.IO) {
        withTmdbResult(onMissingToken = { null }) {
            fetchSeriesMetadataInternal(
                token = it,
                tmdbId = tmdbId,
                seasonHint = seasonNumbers.minOrNull(),
                seasonNumbers = seasonNumbers,
                fallbackSearchResult = null,
            )
        }
    }

    private fun fetchSeriesMetadataInternal(
        token: String,
        tmdbId: Int,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
        fallbackSearchResult: JsonObject?,
    ): DramaSeriesMetadata {
        val detailUrl = apiBaseUrl().toHttpUrl().newBuilder()
            .addPathSegments("3/tv/$tmdbId")
            .addQueryParameter("language", "zh-CN")
            .build()
        val detail = executeJson(detailUrl.toString(), token).jsonObject
        val requestedSeasonNumbers = seasonNumbers
            .filter { it > 0 }
            .distinct()
            .sorted()
            .ifEmpty {
                listOfNotNull(seasonHint?.takeIf { it > 0 })
            }
        val seasonMetadata = requestedSeasonNumbers.mapNotNull { seasonNumber ->
            val seasonDetailUrl = apiBaseUrl().toHttpUrl().newBuilder()
                .addPathSegments("3/tv/$tmdbId/season/$seasonNumber")
                .addQueryParameter("language", "zh-CN")
                .build()
            val seasonDetail = executeJson(seasonDetailUrl.toString(), token).jsonObject
            seasonDetail.toDramaSeasonMetadata()
        }

        return DramaSeriesMetadata(
            series = DramaSeries(
                id = "tmdb:$tmdbId",
                title = detail.string("name").ifBlank { fallbackSearchResult?.string("name").orEmpty() },
                originalTitle = detail.string("original_name"),
                summary = detail.string("overview"),
                seasonCount = detail.int("number_of_seasons") ?: 0,
                episodeCount = detail.int("number_of_episodes") ?: 0,
                posterUrl = detail.imageUrl("poster_path"),
                fanartUrl = detail.imageUrl("backdrop_path"),
                firstAirDate = detail.string("first_air_date").ifBlank { null }.orEmpty(),
                tmdbId = tmdbId,
            ),
            seasons = seasonMetadata,
        )
    }

    private inline fun <T> withTmdbResult(
        onMissingToken: () -> T,
        block: (token: String) -> T,
    ): Result<T> {
        val token = credentials.tmdbAccessToken?.trim().orEmpty()
        if (token.isBlank()) {
            return Result.success(onMissingToken())
        }
        return runCatching { block(token) }.fold(
            onSuccess = { Result.success(it) },
            onFailure = {
                Result.failure(
                    AppError.ScrapingError.ApiError(
                        source = SOURCE_NAME,
                        message = it.message ?: "Unknown error",
                    ),
                )
            },
        )
    }

    private fun apiBaseUrl(): String =
        credentials.tmdbApiBaseUrlOverride
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: API_BASE

    private fun executeJson(url: String, token: String): JsonElement {
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                throw IllegalStateException("Empty response")
            }
            return json.parseToJsonElement(body)
        }
    }

    private fun JsonArray.firstUsefulTvResult(seasonHint: Int?): JsonObject? =
        rankedTvResults(seasonHint).firstOrNull()

    private fun JsonArray.rankedTvResults(seasonHint: Int?): List<JsonObject> =
        mapNotNull { it as? JsonObject }
            .sortedByDescending { candidateScore(it, seasonHint) }

    private fun candidateScore(item: JsonObject, seasonHint: Int?): Int {
        var score = 0
        if (item.string("poster_path").isNotBlank()) score += 2
        if (item.string("overview").isNotBlank()) score += 2
        if (item.string("first_air_date").isNotBlank()) score += 1
        if (seasonHint != null && seasonHint > 1 && item.string("name").contains(seasonHint.toString())) {
            score += 1
        }
        return score
    }

    private fun JsonObject.string(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull().orEmpty()

    private fun JsonObject.int(key: String): Int? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.toIntOrNull()

    private fun JsonObject.imageUrl(key: String): String? =
        string(key).takeIf { it.isNotBlank() }?.let { "$IMAGE_BASE$it" }

    private fun JsonObject.toDramaSearchResult(): DramaMetadataSearchResult? {
        val tmdbId = int("id") ?: return null
        return DramaMetadataSearchResult(
            tmdbId = tmdbId,
            title = string("name"),
            originalTitle = string("original_name"),
            summary = string("overview"),
            firstAirDate = string("first_air_date").ifBlank { null }.orEmpty(),
            posterUrl = imageUrl("poster_path"),
            fanartUrl = imageUrl("backdrop_path"),
        )
    }

    private fun JsonObject.toDramaSeasonMetadata(): DramaSeasonMetadata? {
        val seasonNumber = int("season_number") ?: return null
        val episodes = this["episodes"]
            ?.jsonArray
            ?.mapNotNull { episode ->
                (episode as? JsonObject)?.toDramaEpisodeMetadata(seasonNumber)
            }
            .orEmpty()
        return DramaSeasonMetadata(
            seasonNumber = seasonNumber,
            title = string("name"),
            episodes = episodes,
        )
    }

    private fun JsonObject.toDramaEpisodeMetadata(
        seasonNumber: Int,
    ): DramaEpisodeMetadata? {
        val episodeNumber = int("episode_number") ?: return null
        return DramaEpisodeMetadata(
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            title = string("name"),
            summary = string("overview"),
        )
    }

    companion object {
        private const val SOURCE_NAME = "TMDB"
        private const val API_BASE = "https://api.themoviedb.org/"
        private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w780"
    }
}
