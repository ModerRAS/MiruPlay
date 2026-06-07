package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.DramaEpisodeMetadata
import com.miruplay.tv.model.DramaMetadataSearchResult
import com.miruplay.tv.model.DramaSeasonMetadata
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.MetadataProviderRef
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
class TvMazeDramaMetadataRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : DramaMetadataRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private var apiBaseUrlOverride: String? = null

    override fun canFetchSeriesMetadataByTitle(): Boolean = true

    override fun canFetchMetadataByProviderRef(
        providerRef: MetadataProviderRef,
    ): Boolean =
        providerRef.source.equals(SOURCE_NAME, ignoreCase = true) && providerRef.id.isNotBlank()

    internal constructor(
        okHttpClient: OkHttpClient,
        testApiBaseUrl: String,
    ) : this(okHttpClient) {
        apiBaseUrlOverride = testApiBaseUrl
    }

    override suspend fun fetchSeriesMetadata(
        title: String,
        seasonHint: Int?,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> = withContext(Dispatchers.IO) {
        runCatching {
            val candidate = executeSearch(title)
                .rankedTvMazeResults(seasonHint)
                .firstOrNull()
                ?: return@runCatching null
            fetchSeriesMetadataInternal(
                providerRef = MetadataProviderRef(source = SOURCE_NAME, id = candidate.id),
                seasonNumbers = seasonNumbers,
                fallbackCandidate = candidate,
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

    override suspend fun fetchSeriesMetadataByProviderRef(
        providerRef: MetadataProviderRef,
        seasonNumbers: List<Int>,
    ): Result<DramaSeriesMetadata?> = withContext(Dispatchers.IO) {
        if (!providerRef.source.equals(SOURCE_NAME, ignoreCase = true)) {
            return@withContext Result.success(null)
        }
        runCatching {
            fetchSeriesMetadataInternal(
                providerRef = providerRef,
                seasonNumbers = seasonNumbers,
                fallbackCandidate = null,
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

    override suspend fun searchSeriesCandidates(
        query: String,
        seasonHint: Int?,
        maxResults: Int,
    ): Result<List<DramaMetadataSearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            executeSearch(query)
                .rankedTvMazeResults(seasonHint)
                .take(maxResults.coerceAtLeast(1))
                .map { candidate -> candidate.toDramaSearchResult() }
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

    private fun fetchSeriesMetadataInternal(
        providerRef: MetadataProviderRef,
        seasonNumbers: List<Int>,
        fallbackCandidate: TvMazeShowCandidate?,
    ): DramaSeriesMetadata? {
        val detail = executeShow(providerRef.id) ?: return fallbackCandidate?.toDramaSeriesMetadata()
        val episodes = executeEpisodes(providerRef.id)
        val requestedSeasonNumbers = seasonNumbers
            .filter { it > 0 }
            .distinct()
            .sorted()
        val filteredEpisodes = if (requestedSeasonNumbers.isEmpty()) {
            episodes
        } else {
            episodes.filter { it.seasonNumber in requestedSeasonNumbers }
        }
        val groupedSeasons = filteredEpisodes
            .groupBy(TvMazeEpisodeCandidate::seasonNumber)
            .toSortedMap()
            .map { (seasonNumber, seasonEpisodes) ->
                DramaSeasonMetadata(
                    seasonNumber = seasonNumber,
                    title = if (seasonNumber > 0) "Season $seasonNumber" else "",
                    episodes = seasonEpisodes
                        .sortedBy(TvMazeEpisodeCandidate::episodeNumber)
                        .map { episode ->
                            DramaEpisodeMetadata(
                                seasonNumber = episode.seasonNumber,
                                episodeNumber = episode.episodeNumber,
                                title = episode.title,
                                summary = episode.summary,
                            )
                        },
                )
            }
        val seasonCount = episodes.map(TvMazeEpisodeCandidate::seasonNumber).distinct().size
        return DramaSeriesMetadata(
            series = DramaSeries(
                id = "tvmaze:${providerRef.id}",
                title = detail.title.ifBlank { fallbackCandidate?.title.orEmpty() },
                originalTitle = detail.originalTitle,
                summary = detail.summary,
                seasonCount = seasonCount,
                episodeCount = episodes.size,
                posterUrl = detail.posterUrl,
                fanartUrl = detail.fanartUrl,
                firstAirDate = detail.firstAirDate,
                metadataProviderRef = providerRef,
            ),
            seasons = groupedSeasons,
        )
    }

    private fun executeSearch(query: String): List<TvMazeShowCandidate> {
        val searchUrl = apiBaseUrl().toHttpUrl().newBuilder()
            .addPathSegments("search/shows")
            .addQueryParameter("q", query)
            .build()
        val request = Request.Builder()
            .url(searchUrl)
            .addHeader("Accept", "application/json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            return json.parseToJsonElement(body)
                .jsonArray
                .mapNotNull { element -> (element as? JsonObject)?.toSearchCandidate() }
        }
    }

    private fun executeShow(id: String): TvMazeShowCandidate? {
        val detailUrl = apiBaseUrl().toHttpUrl().newBuilder()
            .addPathSegments("shows/$id")
            .build()
        val request = Request.Builder()
            .url(detailUrl)
            .addHeader("Accept", "application/json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return (json.parseToJsonElement(body) as? JsonObject)?.toShowCandidate()
        }
    }

    private fun executeEpisodes(id: String): List<TvMazeEpisodeCandidate> {
        val episodesUrl = apiBaseUrl().toHttpUrl().newBuilder()
            .addPathSegments("shows/$id/episodes")
            .build()
        val request = Request.Builder()
            .url(episodesUrl)
            .addHeader("Accept", "application/json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            return json.parseToJsonElement(body)
                .jsonArray
                .mapNotNull { element -> (element as? JsonObject)?.toEpisodeCandidate() }
        }
    }

    private fun apiBaseUrl(): String =
        apiBaseUrlOverride
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: TVMAZE_API_BASE

    private fun JsonObject.toSearchCandidate(): TvMazeShowCandidate? {
        val show = this["show"]?.jsonObject ?: return null
        return show.toShowCandidate(rawScore = float("score"))
    }

    private fun JsonObject.toShowCandidate(
        rawScore: Float? = null,
    ): TvMazeShowCandidate? {
        val id = int("id")?.toString() ?: return null
        val title = string("name").ifBlank { return null }
        val image = this["image"] as? JsonObject
        val originalImage = image?.string("original")?.takeIf { it.isNotBlank() }
        val mediumImage = image?.string("medium")?.takeIf { it.isNotBlank() }
        return TvMazeShowCandidate(
            id = id,
            title = title,
            originalTitle = title.takeUnless(::containsCjk).orEmpty(),
            summary = string("summary").stripHtmlTags(),
            firstAirDate = string("premiered").ifBlank { null }.orEmpty(),
            posterUrl = mediumImage ?: originalImage,
            fanartUrl = originalImage ?: mediumImage,
            rawScore = rawScore,
        )
    }

    private fun JsonObject.toEpisodeCandidate(): TvMazeEpisodeCandidate? {
        val seasonNumber = int("season") ?: return null
        val episodeNumber = int("number") ?: return null
        return TvMazeEpisodeCandidate(
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            title = string("name"),
            summary = string("summary").stripHtmlTags(),
        )
    }

    private fun List<TvMazeShowCandidate>.rankedTvMazeResults(seasonHint: Int?): List<TvMazeShowCandidate> =
        sortedByDescending { candidate ->
            var score = candidate.rawScore ?: 0f
            if (candidate.posterUrl != null) score += 0.15f
            if (candidate.summary.isNotBlank()) score += 0.1f
            if (candidate.firstAirDate != null) score += 0.05f
            if (seasonHint != null && seasonHint > 1 && candidate.title.contains(seasonHint.toString())) {
                score += 0.05f
            }
            score
        }

    private fun JsonObject.string(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull().orEmpty()

    private fun JsonObject.int(key: String): Int? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.toIntOrNull()

    private fun JsonObject.float(key: String): Float? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.toFloatOrNull()

    private fun String.stripHtmlTags(): String =
        replace(htmlTagRegex, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(whitespaceRegex, " ")
            .trim()

    private data class TvMazeShowCandidate(
        val id: String,
        val title: String,
        val originalTitle: String = "",
        val summary: String,
        val firstAirDate: String? = null,
        val posterUrl: String? = null,
        val fanartUrl: String? = null,
        val rawScore: Float? = null,
    ) {
        fun toDramaSearchResult(): DramaMetadataSearchResult = DramaMetadataSearchResult(
            title = title,
            originalTitle = originalTitle,
            summary = summary,
            firstAirDate = firstAirDate,
            posterUrl = posterUrl,
            fanartUrl = fanartUrl,
            providerRef = MetadataProviderRef(source = SOURCE_NAME, id = id),
        )

        fun toDramaSeriesMetadata(): DramaSeriesMetadata = DramaSeriesMetadata(
            series = DramaSeries(
                id = "tvmaze:$id",
                title = title,
                originalTitle = originalTitle,
                summary = summary,
                posterUrl = posterUrl,
                fanartUrl = fanartUrl,
                firstAirDate = firstAirDate,
                metadataProviderRef = MetadataProviderRef(source = SOURCE_NAME, id = id),
            ),
        )
    }

    private data class TvMazeEpisodeCandidate(
        val seasonNumber: Int,
        val episodeNumber: Int,
        val title: String,
        val summary: String,
    )

    companion object {
        private const val SOURCE_NAME = "TVMaze"
        private const val TVMAZE_API_BASE = "https://api.tvmaze.com/"
        private val htmlTagRegex = Regex("<[^>]+>")
        private val whitespaceRegex = Regex("\\s+")
    }
}

private fun containsCjk(text: String): Boolean =
    text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
