package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MetadataProviderRef
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TvMazeDramaMetadataRepositoryTest {
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
    fun `searchSeriesCandidates maps tvmaze search response`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {
                    "score": 19.2,
                    "show": {
                      "id": 321,
                      "name": "金庸武侠世界",
                      "summary": "<p>在线简介</p>",
                      "premiered": "2024-06-17",
                      "image": {
                        "medium": "https://static.tvmaze.com/uploads/images/medium_portrait/1/1.jpg",
                        "original": "https://static.tvmaze.com/uploads/images/original_untouched/1/1.jpg"
                      }
                    }
                  },
                  {
                    "score": 7.1,
                    "show": {
                      "id": 123,
                      "name": "Legend Show",
                      "summary": "<p>Second result</p>",
                      "premiered": "2018-01-01"
                    }
                  }
                ]
                """.trimIndent(),
            ),
        )
        val repository = TvMazeDramaMetadataRepository(
            okHttpClient = OkHttpClient(),
            testApiBaseUrl = server.url("/").toString(),
        )

        val result = repository.searchSeriesCandidates(query = "金庸武侠世界", maxResults = 5)

        val data = (result as Result.Success).data
        assertEquals(2, data.size)
        assertEquals("TVMaze", data.first().providerRef.source)
        assertEquals("321", data.first().providerRef.id)
        assertEquals("金庸武侠世界", data.first().title)
        assertEquals("在线简介", data.first().summary)
        assertEquals("2024-06-17", data.first().firstAirDate)
        assertEquals("https://static.tvmaze.com/uploads/images/medium_portrait/1/1.jpg", data.first().posterUrl)
        assertEquals("https://static.tvmaze.com/uploads/images/original_untouched/1/1.jpg", data.first().fanartUrl)
        server.takeRequest().also { request ->
            assertEquals("/search/shows?q=%E9%87%91%E5%BA%B8%E6%AD%A6%E4%BE%A0%E4%B8%96%E7%95%8C", request.path)
        }
        Unit
    }

    @Test
    fun `fetchSeriesMetadataByProviderRef maps tvmaze detail and episodes`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": 321,
                  "name": "金庸武侠世界",
                  "summary": "<p>在线简介</p>",
                  "premiered": "2024-06-17",
                  "image": {
                    "medium": "https://static.tvmaze.com/uploads/images/medium_portrait/1/1.jpg",
                    "original": "https://static.tvmaze.com/uploads/images/original_untouched/1/1.jpg"
                  }
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {
                    "id": 1,
                    "season": 1,
                    "number": 1,
                    "name": "第一集",
                    "summary": "<p>第一集简介</p>"
                  },
                  {
                    "id": 2,
                    "season": 1,
                    "number": 2,
                    "name": "第二集",
                    "summary": "<p>第二集简介</p>"
                  }
                ]
                """.trimIndent(),
            ),
        )
        val repository = TvMazeDramaMetadataRepository(
            okHttpClient = OkHttpClient(),
            testApiBaseUrl = server.url("/").toString(),
        )

        val result = repository.fetchSeriesMetadataByProviderRef(
            providerRef = MetadataProviderRef(source = "TVMaze", id = "321"),
            seasonNumbers = listOf(1),
        )

        val data = (result as Result.Success).data!!
        assertEquals("tvmaze:321", data.series.id)
        assertEquals("金庸武侠世界", data.series.title)
        assertEquals("在线简介", data.series.summary)
        assertEquals("2024-06-17", data.series.firstAirDate)
        assertEquals("TVMaze", data.series.metadataProviderRef?.source)
        assertEquals("321", data.series.metadataProviderRef?.id)
        assertEquals(1, data.series.seasonCount)
        assertEquals(2, data.series.episodeCount)
        assertEquals(1, data.seasons.size)
        assertEquals(1, data.seasons.single().seasonNumber)
        assertEquals("Season 1", data.seasons.single().title)
        assertEquals(listOf("第一集", "第二集"), data.seasons.single().episodes.map { it.title })
        assertEquals(listOf("第一集简介", "第二集简介"), data.seasons.single().episodes.map { it.summary })
        server.takeRequest().also { request ->
            assertEquals("/shows/321", request.path)
        }
        server.takeRequest().also { request ->
            assertEquals("/shows/321/episodes", request.path)
        }
        Unit
    }

    @Test
    fun `fetchSeriesMetadataByProviderRef returns api error on http failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val repository = TvMazeDramaMetadataRepository(
            okHttpClient = OkHttpClient(),
            testApiBaseUrl = server.url("/").toString(),
        )

        val result = repository.fetchSeriesMetadataByProviderRef(
            providerRef = MetadataProviderRef(source = "TVMaze", id = "321"),
            seasonNumbers = listOf(1),
        )

        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is AppError.ScrapingError.ApiError)
        error as AppError.ScrapingError.ApiError
        assertEquals("TVMaze", error.source)
        assertTrue(error.message.contains("HTTP 500"))
    }
}
