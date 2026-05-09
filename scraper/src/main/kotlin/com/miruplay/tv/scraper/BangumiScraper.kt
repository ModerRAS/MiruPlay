package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.secure.SecurePreferencesManager
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.ScraperSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
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

    companion object {
        private const val API_BASE = "https://api.bgm.tv"
        private const val USER_AGENT = "MiruPlay/1.0 (Android)"
    }

    private fun buildRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        val token = securePrefs.bangumiAccessToken
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }

        return builder.build()
    }

    override suspend fun searchAnime(query: String): Result<List<ScraperResult>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("$API_BASE/search/subject/$query?type=2&responseGroup=small")

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(
                AppError.ScrapingError.ApiError(sourceName, "Empty response")
            )

            val root = json.parseToJsonElement(body).jsonObject
            val items = root["list"]?.jsonArray ?: return@withContext Result.success(emptyList())

            val results = items.mapNotNull { item ->
                val obj = item.jsonObject
                val id = obj["id"]?.jsonPrimitive?.int ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val nameCn = obj["name_cn"]?.jsonPrimitive?.contentOrNull

                ScraperResult(
                    animeId = id.toString(),
                    title = name,
                    titleCn = nameCn,
                    matchedTitle = query,
                    confidence = 0.7f,
                    source = ScraperSource.BANGUMI
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
            val request = buildRequest("$API_BASE/v0/subjects/$animeId")

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(
                AppError.ScrapingError.ApiError(sourceName, "Empty response")
            )

            val obj = json.parseToJsonElement(body).jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
            val nameCn = obj["name_cn"]?.jsonPrimitive?.contentOrNull
            val summary = obj["summary"]?.jsonPrimitive?.contentOrNull?.replace(Regex("<[^>]*>"), "") ?: ""
            val rating = obj["rating"]?.jsonObject?.get("score")?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
            val eps = obj["total_episodes"]?.jsonPrimitive?.int ?: 0
            val images = obj["images"]?.jsonObject

            val anime = Anime(
                id = animeId,
                title = name,
                titleCn = nameCn,
                summary = summary,
                genres = obj["tags"]?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                    ?: emptyList(),
                episodeCount = eps,
                rating = rating,
                bangumiId = idInt,
                posterUrl = images?.get("large")?.jsonPrimitive?.contentOrNull,
                fanartUrl = null
            )

            Result.success(anime)
        } catch (e: NumberFormatException) {
            Result.failure(AppError.ScrapingError.NoMatchFound(animeId))
        } catch (e: Exception) {
            Result.failure(AppError.ScrapingError.ApiError(sourceName, e.message ?: "Unknown error"))
        }
    }

    override suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>> = withContext(Dispatchers.IO) {
        Result.success(emptyList())
    }

    override suspend fun searchByAlias(normalizedName: String, candidates: List<String>): Result<ScraperResult?> = withContext(Dispatchers.IO) {
        try {
            for (candidate in candidates) {
                val result = searchAnime(candidate).getOrNull()?.firstOrNull()
                if (result != null) return@withContext Result.success(result)
            }
            Result.success(null)
        } catch (e: Exception) {
            Result.success(null)
        }
    }
}
