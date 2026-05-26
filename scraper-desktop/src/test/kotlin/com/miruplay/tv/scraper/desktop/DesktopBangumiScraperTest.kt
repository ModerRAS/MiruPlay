package com.miruplay.tv.scraper.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.scraper.MetadataScraper
import com.miruplay.tv.scraper.searchPreferredResults
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBangumiScraperTest {
    @Test
    fun `desktop wrapper implements shared metadata scraper contract`() {
        val scraper: MetadataScraper = DesktopBangumiScraper()

        assertEquals("Bangumi", scraper.sourceName)
    }

    @Test
    fun `default Bangumi base url can be overridden for desktop behavior tests`() {
        assertEquals(
            "http://127.0.0.1:39000/",
            DesktopBangumiScraper.bangumiBaseUrlFromEnvironment(
                mapOf(DesktopBangumiScraper.BASE_URL_ENV to " http://127.0.0.1:39000/ "),
            ),
        )
        assertEquals(
            "https://api.bgm.tv",
            DesktopBangumiScraper.bangumiBaseUrlFromEnvironment(emptyMap()),
        )
    }

    @Test
    fun `desktop wrapper delegates search to shared Bangumi client`() = runBlocking {
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
                          "rating": { "score": 8.8 }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val scraper = DesktopBangumiScraper(baseUrl = server.url("/"))

            val result = scraper.searchAnime("葬送的芙莉莲")

            assertTrue(result is Result.Success)
            assertEquals("431767", (result as Result.Success).data.single().animeId)
            assertEquals("/v0/search/subjects?limit=10&offset=0", server.takeRequest().path)
        }
    }

    @Test
    fun `desktop wrapper normalizes traditional Chinese Bangumi queries like Android`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "data": [
                        {
                          "id": 1,
                          "name": "Old",
                          "name_cn": "葬送的芙莉莲",
                          "rating": { "score": 8.0 }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val scraper = DesktopBangumiScraper(baseUrl = server.url("/"))

            val result = scraper.searchAnime("葬送的芙莉蓮")

            assertTrue(result is Result.Success)
            assertEquals(1.0f, (result as Result.Success).data.single().confidence)
            assertTrue(server.takeRequest().body.readUtf8().contains("葬送的芙莉莲"))
        }
    }

    @Test
    fun `desktop wrapper delegates alias search to the shared helper`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "data": [
                        {
                          "id": 11,
                          "name": "Completely Different",
                          "name_cn": "完全不同",
                          "rating": { "score": 7.0 }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "data": [
                        {
                          "id": 22,
                          "name": "候选二",
                          "name_cn": "候选二",
                          "rating": { "score": 8.0 }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val scraper = DesktopBangumiScraper(baseUrl = server.url("/"))

            val result = scraper.searchByAlias(
                normalizedName = "候选甲",
                candidates = listOf("候选甲", "候选二"),
            )

            assertTrue(result is Result.Success)
            assertEquals("22", (result as Result.Success).data?.animeId)
            assertTrue(server.takeRequest().body.readUtf8().contains("候选甲"))
            assertTrue(server.takeRequest().body.readUtf8().contains("候选二"))
        }
    }

    @Test
    fun `desktop wrapper prefers alias results when direct search is weak`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "data": [
                        {
                          "id": 11,
                          "name": "Completely Different",
                          "name_cn": "完全不同",
                          "rating": { "score": 7.0 }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "data": [
                        {
                          "id": 22,
                          "name": "候选二",
                          "name_cn": "候选二",
                          "rating": { "score": 8.0 }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val scraper = DesktopBangumiScraper(baseUrl = server.url("/"))

            val result = scraper.searchPreferredResults(
                query = "候选甲",
                candidates = listOf("候选二"),
            )

            assertTrue(result is Result.Success)
            val results = (result as Result.Success).data
            assertEquals("22", results.first().animeId)
            assertEquals("11", results.last().animeId)
            assertTrue(server.takeRequest().body.readUtf8().contains("候选甲"))
            assertTrue(server.takeRequest().body.readUtf8().contains("候选二"))
            assertEquals(2, server.requestCount)
        }
    }
}
