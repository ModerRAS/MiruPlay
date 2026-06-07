package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
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

    override suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> = withContext(Dispatchers.IO) {
        val token = credentials.tmdbAccessToken?.trim().orEmpty()
        if (token.isBlank()) {
            return@withContext Result.success(null)
        }

        runCatching {
            val apiBaseUrl = credentials.tmdbApiBaseUrlOverride
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: API_BASE
            val searchUrl = apiBaseUrl.toHttpUrl().newBuilder()
                .addPathSegments("3/search/tv")
                .addQueryParameter("query", title)
                .addQueryParameter("language", "zh-CN")
                .build()
            val searchRoot = executeJson(searchUrl.toString(), token).jsonObject
            val firstResult = searchRoot["results"]
                ?.jsonArray
                ?.firstUsefulTvResult(seasonHint)
                ?: return@runCatching null

            val tmdbId = firstResult["id"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: return@runCatching null
            val detailUrl = apiBaseUrl.toHttpUrl().newBuilder()
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
                val seasonDetailUrl = apiBaseUrl.toHttpUrl().newBuilder()
                    .addPathSegments("3/tv/$tmdbId/season/$seasonNumber")
                    .addQueryParameter("language", "zh-CN")
                    .build()
                val seasonDetail = executeJson(seasonDetailUrl.toString(), token).jsonObject
                seasonDetail.toDramaSeasonMetadata()
            }

            DramaSeriesMetadata(
                series = DramaSeries(
                    id = "tmdb:$tmdbId",
                    title = detail.string("name").ifBlank { firstResult.string("name") },
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
        }.fold(
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
        mapNotNull { it as? JsonObject }
            .sortedByDescending { candidateScore(it, seasonHint) }
            .firstOrNull()

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
