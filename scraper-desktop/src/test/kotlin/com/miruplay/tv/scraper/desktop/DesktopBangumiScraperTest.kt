package com.miruplay.tv.scraper.desktop

import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBangumiScraperTest {
    @Test
    fun `searchAnime posts Bangumi query and maps ranked results`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "data": [
                        {
                          "id": 431767,
                          "name": "葬送のフリーレン",
                          "name_cn": "葬送的芙莉莲",
                          "rating": { "score": 8.8 },
                          "infobox": [
                            { "key": "别名", "value": [ { "v": "Frieren" } ] }
                          ]
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val scraper = DesktopBangumiScraper(baseUrl = server.url("/"))

            val result = scraper.searchAnime("葬送的芙莉莲")

            assertTrue(result is Result.Success)
            val data = (result as Result.Success).data
            assertEquals("431767", data.single().animeId)
            assertEquals("葬送的芙莉莲", data.single().matchedTitle)
            assertTrue(data.single().confidence > 0.9f)

            val request = server.takeRequest()
            assertEquals("/v0/search/subjects?limit=10&offset=0", request.path)
            assertTrue(request.body.readUtf8().contains("葬送的芙莉莲"))
        }
    }

    @Test
    fun `getAnimeDetails maps subject metadata`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "id": 431767,
                      "name": "葬送のフリーレン",
                      "name_cn": "葬送的芙莉莲",
                      "summary": "<p>旅行仍在继续。</p>",
                      "tags": [ { "name": "奇幻" }, { "name": "冒险" } ],
                      "eps": 28,
                      "date": "2023-09-29",
                      "rating": { "score": 8.8 },
                      "images": { "large": "https://img.example/frieren.jpg" },
                      "collection": { "doing": 1000 }
                    }
                    """.trimIndent()
                )
            )
            val scraper = DesktopBangumiScraper(baseUrl = server.url("/"))

            val result = scraper.getAnimeDetails("431767")

            assertTrue(result is Result.Success)
            val anime = (result as Result.Success).data
            assertEquals("431767", anime.id)
            assertEquals("葬送的芙莉莲", anime.titleCn)
            assertEquals("旅行仍在继续。", anime.summary)
            assertEquals(listOf("奇幻", "冒险"), anime.genres)
            assertEquals(28, anime.episodeCount)
            assertEquals("https://img.example/frieren.jpg", anime.posterUrl)
            assertEquals("/v0/subjects/431767", server.takeRequest().path)
        }
    }

    @Test
    fun `getEpisodes maps and sorts regular episodes`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "total": 2,
                      "data": [
                        { "id": 20, "type": 0, "ep": 2, "name": "Second", "name_cn": "第二集", "duration_seconds": 1440 },
                        { "id": 10, "type": 0, "ep": 1, "name": "First", "name_cn": "第一集", "duration_seconds": 1500 }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val scraper = DesktopBangumiScraper(baseUrl = server.url("/"))

            val result = scraper.getEpisodes("431767")

            assertTrue(result is Result.Success)
            val episodes = (result as Result.Success).data
            assertEquals(listOf(1, 2), episodes.map { it.episodeNumber })
            assertEquals("第一集", episodes.first().title)
            assertEquals(1_500_000L, episodes.first().durationMs)
            assertEquals("/v0/episodes?subject_id=431767&type=0&limit=200&offset=0", server.takeRequest().path)
        }
    }
}
