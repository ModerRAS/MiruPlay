package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListScraper @Inject constructor() : MetadataScraper {

    override val sourceName: String = "AniList"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()

    companion object {
        private const val API_URL = "https://graphql.anilist.co"

        private val SEARCH_QUERY = """
        query (${"$"}search: String, ${"$"}page: Int, ${"$"}perPage: Int) {
            Page(page: ${"$"}page, perPage: ${"$"}perPage) {
                media(search: ${"$"}search, type: ANIME) {
                    id
                    title { romaji native english }
                    description
                    genres
                    studios { nodes { name } }
                    episodes
                    startDate { year month day }
                    averageScore
                    externalLinks { site url }
                    coverImage { extraLarge large medium }
                    bannerImage
                }
            }
        }"""

        private val DETAIL_QUERY = """
        query (${"$"}id: Int) {
            Media(id: ${"$"}id, type: ANIME) {
                id
                title { romaji native english }
                description
                genres
                studios { nodes { name } }
                episodes
                duration
                startDate { year month day }
                endDate { year month day }
                averageScore
                season
                seasonYear
                externalLinks { site url id }
                coverImage { extraLarge large medium }
                bannerImage
                relations { edges { node { id title { romaji } type } } }
            }
        }"""
    }

    override suspend fun searchAnime(query: String): Result<List<ScraperResult>> = withContext(Dispatchers.IO) {
        try {
            val variables = buildJsonObject {
                put("search", query)
                put("page", 1)
                put("perPage", 10)
            }

            val response = executeQuery(SEARCH_QUERY, variables)
            val page = response.jsonObject["data"]?.jsonObject?.get("Page")?.jsonObject ?:
                return@withContext Result.failure(AppError.ScrapingError.ApiError(sourceName, "No data"))

            val mediaList = page["media"]?.jsonArray ?:
                return@withContext Result.success(emptyList())

            val results = mediaList.mapNotNull { item ->
                val media = item.jsonObject
                val id = media["id"]?.jsonPrimitive?.int ?: return@mapNotNull null
                val title = parseTitle(media["title"]?.jsonObject)

                ScraperResult(
                    animeId = id.toString(),
                    title = title,
                    matchedTitle = query,
                    confidence = calculateConfidence(title, query),
                    source = ScraperSource.ANILIST
                )
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(sourceName, e.message ?: "Unknown error"))
        }
    }

    override suspend fun getAnimeDetails(animeId: String): Result<Anime> = withContext(Dispatchers.IO) {
        try {
            val idInt = animeId.toInt()
            val variables = buildJsonObject {
                put("id", idInt)
            }

            val response = executeQuery(DETAIL_QUERY, variables)
            val media = response.jsonObject["data"]?.jsonObject?.get("Media")?.jsonObject
                ?: return@withContext Result.failure(AppError.ScrapingError.NoMatchFound(animeId))

            val title = parseTitle(media["title"]?.jsonObject)
            val description = media["description"]?.jsonPrimitive?.contentOrNull
                ?.replace(Regex("<[^>]*>"), "")  // Strip HTML tags
                ?: ""

            val genres = media["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val episodes = media["episodes"]?.jsonPrimitive?.int ?: 0
            val score = media["averageScore"]?.jsonPrimitive?.int?.div(10f) ?: 0f
            val date = parseDate(media["startDate"]?.jsonObject)

            val anime = Anime(
                id = animeId,
                title = title,
                titleCn = null,
                summary = description,
                genres = genres,
                studio = media["studios"]?.jsonObject?.get("nodes")?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull,
                director = null,
                episodeCount = episodes,
                airDate = date,
                rating = score,
                anilistId = idInt,
                posterUrl = media["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull,
                fanartUrl = media["bannerImage"]?.jsonPrimitive?.contentOrNull
            )

            Result.success(anime)
        } catch (e: NumberFormatException) {
            Result.failure(AppError.ScrapingError.NoMatchFound(animeId))
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(sourceName, e.message ?: "Unknown error"))
        }
    }

    override suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>> = withContext(Dispatchers.IO) {
        // AniList GraphQL doesn't return episode-by-episode details via the basic API
        // Return empty list - episode structure comes from scanner
        Result.success(emptyList())
    }

    override suspend fun searchByAlias(normalizedName: String, candidates: List<String>): Result<ScraperResult?> = withContext(Dispatchers.IO) {
        try {
            for (candidate in candidates) {
                val results = searchAnime(candidate)
                val result = results.getOrNull()?.firstOrNull()
                if (result != null) {
                    return@withContext Result.success(result)
                }
            }
            Result.success(null)
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    private suspend fun executeQuery(query: String, variables: JsonObject): JsonElement {
        val requestBody = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString()

        val request = Request.Builder()
            .url(API_URL)
            .post(requestBody.toRequestBody(mediaType))
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        return json.parseToJsonElement(body)
    }

    private fun parseTitle(titleObj: JsonObject?): String {
        if (titleObj == null) return ""
        return titleObj["romaji"]?.jsonPrimitive?.contentOrNull
            ?: titleObj["native"]?.jsonPrimitive?.contentOrNull
            ?: titleObj["english"]?.jsonPrimitive?.contentOrNull
            ?: ""
    }

    private fun parseDate(dateObj: JsonObject?): String? {
        if (dateObj == null) return null
        val year = dateObj["year"]?.jsonPrimitive?.int ?: return null
        val month = dateObj["month"]?.jsonPrimitive?.int ?: 1
        val day = dateObj["day"]?.jsonPrimitive?.int ?: 1
        return String.format("%04d-%02d-%02d", year, month, day)
    }

    private fun calculateConfidence(title: String, query: String): Float {
        val normalizedQuery = query.lowercase().trim()
        val normalizedTitle = title.lowercase().trim()
        return when {
            normalizedTitle == normalizedQuery -> 1.0f
            normalizedTitle.contains(normalizedQuery) -> 0.8f
            normalizedQuery.contains(normalizedTitle) -> 0.6f
            else -> 0.3f
        }
    }
}
