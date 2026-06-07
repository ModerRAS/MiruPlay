package com.miruplay.tv.scraper

import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.model.MetadataQueryPlan
import com.miruplay.tv.model.MetadataSearchContext
import com.miruplay.tv.repository.MetadataQueryPlanner
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TvMazeDramaMetadataSearchProviderTest {
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
    fun `search maps tvmaze results into provider neutral candidates`() = runBlocking {
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
        val provider = TvMazeDramaMetadataSearchProvider(
            repository = TvMazeDramaMetadataRepository(
                okHttpClient = OkHttpClient(),
                testApiBaseUrl = server.url("/").toString(),
            ),
        )
        val context = MetadataSearchContext(
            contentMode = MediaContentMode.DRAMA,
            title = "金庸武侠世界",
        )
        val plan = MetadataQueryPlanner.plan(context)

        val results = provider.search(context, plan)

        assertEquals(2, results.size)
        assertEquals("TVMaze", results.first().providerRef.source)
        assertEquals("321", results.first().providerRef.id)
        assertEquals("金庸武侠世界", results.first().title)
        assertEquals("金庸武侠世界", results.first().localizedTitle)
        assertEquals("", results.first().originalTitle)
        assertEquals("在线简介", results.first().summary)
        assertEquals("2024-06-17", results.first().firstAirDate)
        assertEquals("https://static.tvmaze.com/uploads/images/medium_portrait/1/1.jpg", results.first().posterUrl)
        assertEquals("https://static.tvmaze.com/uploads/images/original_untouched/1/1.jpg", results.first().fanartUrl)
        assertEquals(0, results.first().providerRank)
        assertTrue((results.first().providerScore ?: 0f) >= 0.99f)

        val request = server.takeRequest()
        assertEquals("/search/shows?q=%E9%87%91%E5%BA%B8%E6%AD%A6%E4%BE%A0%E4%B8%96%E7%95%8C", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
        Unit
    }

    @Test
    fun `search tolerates http errors and returns empty list`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val provider = TvMazeDramaMetadataSearchProvider(
            repository = TvMazeDramaMetadataRepository(
                okHttpClient = OkHttpClient(),
                testApiBaseUrl = server.url("/").toString(),
            ),
        )
        val context = MetadataSearchContext(
            contentMode = MediaContentMode.DRAMA,
            manualQuery = "Show",
        )
        val plan = MetadataQueryPlanner.plan(context)

        val results = provider.search(context, plan)

        assertTrue(results.isEmpty())
        val request = server.takeRequest()
        assertEquals("/search/shows?q=Show", request.path)
        Unit
    }

    @Test
    fun `search injects hinted provider ref candidate without query text`() = runBlocking {
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
                  }
                ]
                """.trimIndent(),
            ),
        )
        val provider = TvMazeDramaMetadataSearchProvider(
            repository = TvMazeDramaMetadataRepository(
                okHttpClient = OkHttpClient(),
                testApiBaseUrl = server.url("/").toString(),
            ),
        )
        val context = MetadataSearchContext(contentMode = MediaContentMode.DRAMA)
        val plan = MetadataQueryPlan(
            queries = emptyList(),
            providerRefHints = listOf(MetadataProviderRef(source = "TVMaze", id = "321")),
        )

        val results = provider.search(context, plan)

        assertEquals(1, results.size)
        assertEquals("TVMaze", results.single().providerRef.source)
        assertEquals("321", results.single().providerRef.id)
        assertEquals("TVMaze:321", results.single().matchedQuery)
        assertEquals(0, results.single().providerRank)
        assertEquals(1f, results.single().providerScore)
        server.takeRequest().also { request ->
            assertEquals("/shows/321", request.path)
        }
        server.takeRequest().also { request ->
            assertEquals("/shows/321/episodes", request.path)
        }
        Unit
    }
}
