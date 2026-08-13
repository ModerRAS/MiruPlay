package com.miruplay.tv.scraper.core

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.repository.BangumiEpisodeCollectionType
import com.miruplay.tv.repository.BangumiSubjectCollectionType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class BangumiApiClientTest {
    @Test
    fun `searchAnime uses configured HTTP proxy`() = runBlocking {
        MockWebServer().use { proxy ->
            proxy.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "data": [
                        {
                          "id": 440650,
                          "name": "Dr.STONE SCIENCE FUTURE",
                          "name_cn": "Dr.STONE 新石纪 第四季",
                          "rating": { "score": 8.0 }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val client = BangumiApiClient(baseUrl = "http://api.bgm.tv")
            client.configureProxy(
                BangumiHttpProxyConfig(enabled = true, host = proxy.hostName, port = proxy.port)
            )

            val result = client.searchAnime("Dr.STONE 新石纪 第四季")

            assertTrue(result is Result.Success)
            assertEquals("440650", (result as Result.Success).data.single().animeId)
            val request = proxy.takeRequest()
            assertEquals("api.bgm.tv", request.headers["Host"])
            assertTrue(request.requestLine.contains("/v0/search/subjects"))
        }
    }

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
            val client = BangumiApiClient(baseUrl = server.url("/").toString())

            val result = client.searchAnime("葬送的芙莉莲")

            assertTrue(result is Result.Success)
            val data = (result as Result.Success).data
            assertEquals("431767", data.single().animeId)
            assertEquals("葬送的芙莉莲", data.single().matchedTitle)
            assertTrue(data.single().confidence > 0.9f)

            val request = server.takeRequest()
            assertEquals("/v0/search/subjects?limit=10&offset=0", request.path)
            assertEquals(BangumiApiClient.DEFAULT_USER_AGENT, request.getHeader("User-Agent"))
            assertEquals("application/json", request.getHeader("Accept"))
            assertEquals("application/json", request.getHeader("Content-Type"))
            assertTrue(request.body.readUtf8().contains("葬送的芙莉莲"))
        }
    }

    @Test
    fun `searchAnime can normalize query before posting and scoring`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "data": [
                        {
                          "id": 1,
                          "name": "Old",
                          "name_cn": "简体标题",
                          "rating": { "score": 8.0 }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val client = BangumiApiClient(
                baseUrl = server.url("/").toString(),
                normalizeQuery = { value -> if (value == "繁體標題") "简体标题" else value }
            )

            val result = client.searchAnime("繁體標題")

            assertTrue(result is Result.Success)
            assertEquals(1.0f, (result as Result.Success).data.single().confidence)
            assertTrue(server.takeRequest().body.readUtf8().contains("简体标题"))
        }
    }

    @Test
    fun `searchAnime returns archive results before calling Bangumi api`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[]}"""))
            val tempDir = createTempDirectory(prefix = "bangumi-api-archive-test-").toFile()
            val subjectFile = File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME)
            subjectFile.writeText(
                """{"id":431767,"type":2,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲","score":8.8}"""
            )
            val client = BangumiApiClient(
                baseUrl = server.url("/").toString(),
                archiveSearch = BangumiArchiveSubjectSearch(subjectFile),
            )

            val result = client.searchAnime("葬送的芙莉莲")

            assertTrue(result is Result.Success)
            val match = (result as Result.Success).data.single()
            assertEquals("431767", match.animeId)
            assertTrue(match.fromLocalArchive)
            assertEquals(0, server.requestCount)
            tempDir.deleteRecursively()
            Unit
        }
    }

    @Test
    fun `manual search merges archive and online candidates`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "data": [
                        {
                          "id": 500000,
                          "name": "Sousou no Frieren Special",
                          "name_cn": "葬送的芙莉莲 特别篇",
                          "rating": { "score": 8.0 }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val tempDir = createTempDirectory(prefix = "bangumi-api-manual-merge-test-").toFile()
            val subjectFile = File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME)
            subjectFile.writeText(
                """{"id":431767,"type":2,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲","score":8.8}"""
            )
            val client = BangumiApiClient(
                baseUrl = server.url("/").toString(),
                archiveSearch = BangumiArchiveSubjectSearch(subjectFile),
            )

            val result = client.searchAnimeForManualMatch("葬送的芙莉莲")

            assertTrue(result is Result.Success)
            val matches = (result as Result.Success).data
            assertEquals(listOf("431767", "500000"), matches.map { it.animeId })
            assertTrue(matches.first().fromLocalArchive)
            assertEquals("/v0/search/subjects?limit=20&offset=0", server.takeRequest().path)
            tempDir.deleteRecursively()
            Unit
        }
    }

    @Test
    fun `manual search treats numeric query as Bangumi subject id`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[]}"""))
            val tempDir = createTempDirectory(prefix = "bangumi-api-manual-id-test-").toFile()
            val subjectFile = File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME)
            subjectFile.writeText(
                """{"id":431767,"type":2,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲","score":8.8}"""
            )
            val client = BangumiApiClient(
                baseUrl = server.url("/").toString(),
                archiveSearch = BangumiArchiveSubjectSearch(subjectFile),
            )

            val result = client.searchAnimeForManualMatch("431767")

            assertTrue(result is Result.Success)
            val match = (result as Result.Success).data.first()
            assertEquals("431767", match.animeId)
            assertEquals(1f, match.confidence)
            assertTrue(match.fromLocalArchive)
            tempDir.deleteRecursively()
            Unit
        }
    }

    @Test
    fun `getAnimeDetails returns archive subject without calling Bangumi api`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"id":431767,"name":"should-not-load"}"""))
            val tempDir = createTempDirectory(prefix = "bangumi-api-archive-detail-test-").toFile()
            val subjectFile = File(tempDir, BangumiArchiveStore.SUBJECT_FILE_NAME)
            subjectFile.writeText(
                """{"id":431767,"type":2,"name":"葬送のフリーレン","name_cn":"葬送的芙莉莲","summary":"旅行仍在继续。","eps":28,"date":"2023-09-29","score":8.8}"""
            )
            val client = BangumiApiClient(
                baseUrl = server.url("/").toString(),
                archiveSearch = BangumiArchiveSubjectSearch(subjectFile),
            )

            val result = client.getAnimeDetails("431767")

            assertTrue(result is Result.Success)
            val anime = (result as Result.Success).data
            assertEquals("431767", anime.id)
            assertEquals("葬送的芙莉莲", anime.titleCn)
            assertEquals("旅行仍在继续。", anime.summary)
            assertEquals(28, anime.episodeCount)
            assertEquals(431767, anime.bangumiId)
            assertEquals(0, server.requestCount)
            tempDir.deleteRecursively()
            Unit
        }
    }

    @Test
    fun `searchByAlias chooses strongest confident candidate`() = runBlocking {
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
                          "name": "Candidate Two",
                          "name_cn": "候选二",
                          "rating": { "score": 8.0 }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val client = BangumiApiClient(baseUrl = server.url("/").toString())

            val result = client.searchByAlias(
                normalizedName = "候选甲",
                candidates = listOf("候选甲", "候选二"),
            )

            assertTrue(result is Result.Success)
            assertEquals("22", (result as Result.Success).data?.animeId)

            val firstRequest = server.takeRequest()
            assertTrue(firstRequest.body.readUtf8().contains("候选甲"))
            val secondRequest = server.takeRequest()
            assertTrue(secondRequest.body.readUtf8().contains("候选二"))
        }
    }

    @Test
    fun `searchByAlias keeps evaluating after generic confident candidate when season candidate is stronger`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "data": [
                        {
                          "id": 266794,
                          "name": "Dr.STONE",
                          "name_cn": "石纪元",
                          "rating": { "score": 7.5 },
                          "infobox": [
                            { "key": "别名", "value": [ { "v": "Dr STONE 新石纪" } ] }
                          ]
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
                          "id": 471578,
                          "name": "Dr.STONE SCIENCE FUTURE",
                          "name_cn": "石纪元 科学与未来",
                          "rating": { "score": 7.2 },
                          "infobox": [
                            { "key": "别名", "value": [ { "v": "Dr STONE 新石纪 第四季" } ] }
                          ]
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val client = BangumiApiClient(baseUrl = server.url("/").toString())

            val result = client.searchByAlias(
                normalizedName = "Dr STONE 新石纪",
                candidates = listOf("Dr STONE 新石纪", "Dr STONE 新石纪 第四季"),
            )

            assertTrue(result is Result.Success)
            assertEquals("471578", (result as Result.Success).data?.animeId)
            assertTrue(server.takeRequest().body.readUtf8().contains("Dr STONE 新石纪 第四季"))
            assertTrue(server.takeRequest().body.readUtf8().contains("Dr STONE 新石纪"))
        }
    }

    @Test
    fun `searchByAlias tries season-specific candidates before generic title`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "data": [
                        {
                          "id": 471578,
                          "name": "Dr.STONE SCIENCE FUTURE",
                          "name_cn": "石纪元 科学与未来",
                          "rating": { "score": 7.2 },
                          "infobox": [
                            { "key": "别名", "value": [ { "v": "新石纪 第四季" } ] }
                          ]
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val client = BangumiApiClient(baseUrl = server.url("/").toString())

            val result = client.searchByAlias(
                normalizedName = "Dr STONE 新石紀",
                candidates = listOf(
                    "Dr STONE 新石紀",
                    "[P][Baha][WEB-DL][AAC AVC][CHT]",
                    "Dr STONE 新石紀 第四季",
                ),
            )

            assertTrue(result is Result.Success)
            assertEquals("471578", (result as Result.Success).data?.animeId)
            assertTrue(server.takeRequest().body.readUtf8().contains("Dr STONE 新石紀 第四季"))
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
            val client = BangumiApiClient(baseUrl = server.url("/").toString())

            val result = client.getAnimeDetails("431767")

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
            val client = BangumiApiClient(baseUrl = server.url("/").toString())

            val result = client.getEpisodes("431767")

            assertTrue(result is Result.Success)
            val episodes = (result as Result.Success).data
            assertEquals(listOf(1, 2), episodes.map { it.episodeNumber })
            assertEquals("第一集", episodes.first().title)
            assertEquals(1_500_000L, episodes.first().durationMs)
            assertEquals("/v0/episodes?subject_id=431767&type=0&limit=200&offset=0", server.takeRequest().path)
        }
    }

    @Test
    fun `collection requests include bearer token and map subject collection`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "subject_id": 431767,
                      "type": 3,
                      "rate": 0,
                      "ep_status": 7,
                      "updated_at": "2026-05-22T12:00:00Z"
                    }
                    """.trimIndent()
                )
            )
            val client = BangumiApiClient(baseUrl = server.url("/").toString(), tokenProvider = { "desktop-token" })

            val result = client.getSubjectCollection(431767)

            assertTrue(client.hasToken)
            assertTrue(result is Result.Success)
            val collection = (result as Result.Success).data
            assertEquals(431767, collection?.subjectId)
            assertEquals(BangumiSubjectCollectionType.DOING.value, collection?.type)
            assertEquals(7, collection?.epStatus)

            val request = server.takeRequest()
            assertEquals("/v0/users/-/collections/431767", request.path)
            assertEquals("Bearer desktop-token", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `missing subject collection returns null`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            val client = BangumiApiClient(baseUrl = server.url("/").toString(), tokenProvider = { "desktop-token" })

            val result = client.getSubjectCollection(431767)

            assertTrue(result is Result.Success)
            assertEquals(null, (result as Result.Success).data)
        }
    }

    @Test
    fun `episode collections are paged and parsed`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "total": 1,
                      "data": [
                        {
                          "type": 2,
                          "updated_at": 1710000000,
                          "episode": { "id": 10, "ep": 1 }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val client = BangumiApiClient(baseUrl = server.url("/").toString(), tokenProvider = { "desktop-token" })

            val result = client.getEpisodeCollections(431767)

            assertTrue(result is Result.Success)
            val collection = (result as Result.Success).data.single()
            assertEquals(10, collection.episodeId)
            assertEquals(1, collection.episodeNumber)
            assertEquals(BangumiEpisodeCollectionType.DONE.value, collection.type)
            assertEquals(1710000000L, collection.updatedAt)
            assertEquals("/v0/users/-/collections/431767/episodes?episode_type=0&limit=1000&offset=0", server.takeRequest().path)
        }
    }

    @Test
    fun `updates subject and episode collections`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(204))
            val client = BangumiApiClient(baseUrl = server.url("/").toString(), tokenProvider = { "desktop-token" })

            assertTrue(client.upsertSubjectCollection(431767, BangumiSubjectCollectionType.DOING) is Result.Success)
            assertTrue(client.updateEpisodeCollections(431767, listOf(10, 20, 10), BangumiEpisodeCollectionType.DONE) is Result.Success)
            assertTrue(client.updateEpisodeCollection(30, BangumiEpisodeCollectionType.DONE) is Result.Success)

            val subjectRequest = server.takeRequest()
            assertEquals("POST", subjectRequest.method)
            assertEquals("/v0/users/-/collections/431767", subjectRequest.path)
            assertEquals("application/json", subjectRequest.getHeader("Content-Type"))
            assertTrue(subjectRequest.body.readUtf8().contains(""""type":3"""))

            val episodeBatchRequest = server.takeRequest()
            assertEquals("PATCH", episodeBatchRequest.method)
            assertEquals("/v0/users/-/collections/431767/episodes", episodeBatchRequest.path)
            assertEquals(BangumiApiClient.DEFAULT_USER_AGENT, episodeBatchRequest.getHeader("User-Agent"))
            assertEquals("application/json", episodeBatchRequest.getHeader("Accept"))
            assertEquals("application/json", episodeBatchRequest.getHeader("Content-Type"))
            val batchBody = episodeBatchRequest.body.readUtf8()
            assertTrue(batchBody.contains(""""episode_id":[10,20]"""))
            assertTrue(batchBody.contains(""""type":2"""))

            val episodeRequest = server.takeRequest()
            assertEquals("PUT", episodeRequest.method)
            assertEquals("/v0/users/-/collections/-/episodes/30", episodeRequest.path)
            assertEquals("application/json", episodeRequest.getHeader("Content-Type"))
            assertTrue(episodeRequest.body.readUtf8().contains(""""type":2"""))
        }
    }

    @Test
    fun `getEpisodeComments reads anonymous episode page comment tree`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    <div id="comment_list">
                      <div id="post_10" class="row row_reply" data-item-user="alice">
                        <div class="inner">
                          <strong><a class="l">Alice</a></strong>
                          <div class="reply_content"><div class="message">主评论</div></div>
                        </div>
                        <div class="topic_sub_reply">
                          <div id="post_11" class="sub_reply_bg" data-item-user="bob">
                            <div class="inner">
                              <strong><a class="l">Bob</a></strong>
                              <div class="reply_content"><div class="message">回复</div></div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                    """.trimIndent(),
                ),
            )
            val client = BangumiApiClient(
                baseUrl = server.url("/").toString(),
                websiteBaseUrl = server.url("/").toString(),
                tokenProvider = { "" },
            )

            val result = client.getEpisodeComments(123)

            assertTrue(result is Result.Success)
            val comments = (result as Result.Success).data
            assertEquals(10, comments.single().id)
            assertEquals(11, comments.single().replies.single().id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/ep/123", request.path)
            assertEquals("text/html,application/xhtml+xml", request.getHeader("Accept"))
            assertEquals(null, request.getHeader("Authorization"))
        }
    }

    @Test
    fun `collection service reports missing token`() = runBlocking {
        val client = BangumiApiClient(tokenProvider = { "" })

        assertFalse(client.hasToken)
        assertTrue(client.getCurrentUser() is Result.Error)
    }
}
