package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.repository.AppCredentialStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TmdbDramaMetadataRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchSeriesMetadata returns null when tmdb token is blank`() = runBlocking {
        val repository = createRepository(token = " ")

        val result = repository.fetchSeriesMetadata(title = "Show")

        assertTrue(result is Result.Success)
        assertNull(result.getOrNull())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `fetchSeriesMetadata maps tmdb search and detail response`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "results": [
                    {
                      "id": 100,
                      "name": "Show Low",
                      "overview": "",
                      "poster_path": null,
                      "first_air_date": ""
                    },
                    {
                      "id": 321,
                      "name": "Show CN",
                      "overview": "Has overview",
                      "poster_path": "/poster.jpg",
                      "first_air_date": "2024-01-01"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": 321,
                  "name": "Show CN",
                  "original_name": "Show Original",
                  "overview": "Online plot",
                  "number_of_seasons": 3,
                  "number_of_episodes": 24,
                  "poster_path": "/poster.jpg",
                  "backdrop_path": "/backdrop.jpg",
                  "first_air_date": "2024-01-01"
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "season_number": 1,
                  "name": "Season 1",
                  "episodes": [
                    {
                      "episode_number": 1,
                      "name": "Pilot Online",
                      "overview": "Episode plot"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val repository = createRepository(token = "token-123")

        val result = repository.fetchSeriesMetadata(
            title = "Show",
            seasonNumbers = listOf(1),
        )

        val data = (result as Result.Success).data
        requireNotNull(data)
        assertSeries(data)
        assertEquals(1, data.seasons.size)
        assertEquals(1, data.seasons.single().seasonNumber)
        assertEquals("Season 1", data.seasons.single().title)
        assertEquals(1, data.seasons.single().episodes.size)
        assertEquals("Pilot Online", data.seasons.single().episodes.single().title)
        assertEquals("Episode plot", data.seasons.single().episodes.single().summary)

        val searchRequest = server.takeRequest()
        assertEquals("/3/search/tv?query=Show&language=zh-CN", searchRequest.path)
        assertEquals("Bearer token-123", searchRequest.getHeader("Authorization"))

        val detailRequest = server.takeRequest()
        assertEquals("/3/tv/321?language=zh-CN", detailRequest.path)

        val seasonRequest = server.takeRequest()
        assertEquals("/3/tv/321/season/1?language=zh-CN", seasonRequest.path)
    }

    @Test
    fun `fetchSeriesMetadata falls back to season hint when season list is empty`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "results": [
                    { "id": 321, "name": "Show CN", "overview": "Has overview", "poster_path": "/poster.jpg", "first_air_date": "2024-01-01" }
                  ]
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": 321,
                  "name": "Show CN",
                  "original_name": "Show Original",
                  "overview": "Online plot",
                  "number_of_seasons": 3,
                  "number_of_episodes": 24,
                  "poster_path": "/poster.jpg",
                  "backdrop_path": "/backdrop.jpg",
                  "first_air_date": "2024-01-01"
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "season_number": 2,
                  "name": "Season 2",
                  "episodes": []
                }
                """.trimIndent(),
            ),
        )
        val repository = createRepository(token = "token-123")

        val result = repository.fetchSeriesMetadata(
            title = "Show",
            seasonHint = 2,
            seasonNumbers = emptyList(),
        )

        val data = (result as Result.Success).data
        requireNotNull(data)
        assertSeries(data)
        assertEquals(1, data.seasons.size)
        assertEquals(2, data.seasons.single().seasonNumber)

        server.takeRequest()
        server.takeRequest()
        val seasonRequest = server.takeRequest()
        assertEquals("/3/tv/321/season/2?language=zh-CN", seasonRequest.path)
    }

    @Test
    fun `fetchSeriesMetadata returns api error when tmdb responds with http error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"status_message":"boom"}"""))
        val repository = createRepository(token = "token-123")

        val result = repository.fetchSeriesMetadata(title = "Show")

        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is AppError.ScrapingError.ApiError)
        error as AppError.ScrapingError.ApiError
        assertEquals("TMDB", error.source)
        assertTrue(error.message.contains("HTTP 500"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `fetchSeriesMetadata uses overridden tmdb base url when configured`() {
        runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "results": [
                    { "id": 321, "name": "Show CN", "overview": "Has overview", "poster_path": "/poster.jpg", "first_air_date": "2024-01-01" }
                  ]
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": 321,
                  "name": "Show CN",
                  "original_name": "Show Original",
                  "overview": "Online plot",
                  "number_of_seasons": 3,
                  "number_of_episodes": 24,
                  "poster_path": "/poster.jpg",
                  "backdrop_path": "/backdrop.jpg",
                  "first_air_date": "2024-01-01"
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "season_number": 1,
                  "name": "Season 1",
                  "episodes": []
                }
                """.trimIndent(),
            ),
        )
        val repository = TmdbDramaMetadataRepository(
            credentials = FakeCredentialStore(
                tmdbAccessToken = "token-123",
                tmdbApiBaseUrlOverride = server.url("/debug-api/").toString(),
            ),
            okHttpClient = OkHttpClient(),
        )

        val result = repository.fetchSeriesMetadata(
            title = "Show",
            seasonNumbers = listOf(1),
        )

        assertTrue(result is Result.Success)
        server.takeRequest().also { request ->
            assertEquals("/debug-api/3/search/tv?query=Show&language=zh-CN", request.path)
        }
        server.takeRequest().also { request ->
            assertEquals("/debug-api/3/tv/321?language=zh-CN", request.path)
        }
        server.takeRequest().also { request ->
            assertEquals("/debug-api/3/tv/321/season/1?language=zh-CN", request.path)
        }
        }
    }

    private fun createRepository(token: String): TmdbDramaMetadataRepository {
        val baseUrl = server.url("/")
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val redirected = original.newBuilder()
                    .url(
                        original.url.newBuilder()
                            .scheme(baseUrl.scheme)
                            .host(baseUrl.host)
                            .port(baseUrl.port)
                            .build(),
                    )
                    .build()
                chain.proceed(redirected)
            }
            .build()
        return TmdbDramaMetadataRepository(
            credentials = FakeCredentialStore(token),
            okHttpClient = client,
        )
    }

    private class FakeCredentialStore(
        override var tmdbAccessToken: String?,
        override var tmdbApiBaseUrlOverride: String? = null,
    ) : AppCredentialStore {
        override var cloudDriveToken: String? = null
        override var cloudDrivePassword: String? = null
        override var bangumiAccessToken: String? = null
        override var otlpAccessToken: String? = null

        override fun clearCloudDriveCredentials() {
            cloudDriveToken = null
            cloudDrivePassword = null
        }

        override fun clearBangumiToken() {
            bangumiAccessToken = null
        }

        override fun clearTmdbToken() {
            tmdbAccessToken = null
        }

        override fun clearOtlpAccessToken() {
            otlpAccessToken = null
        }
    }

    private fun assertSeries(data: DramaSeriesMetadata) {
        assertEquals("tmdb:321", data.series.id)
        assertEquals("Show CN", data.series.title)
        assertEquals("Show Original", data.series.originalTitle)
        assertEquals("Online plot", data.series.summary)
        assertEquals(3, data.series.seasonCount)
        assertEquals(24, data.series.episodeCount)
        assertEquals("https://image.tmdb.org/t/p/w780/poster.jpg", data.series.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w780/backdrop.jpg", data.series.fanartUrl)
        assertEquals("2024-01-01", data.series.firstAirDate)
        assertEquals(321, data.series.tmdbId)
    }
}
