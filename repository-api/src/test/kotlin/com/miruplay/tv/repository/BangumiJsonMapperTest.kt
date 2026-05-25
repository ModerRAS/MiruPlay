package com.miruplay.tv.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiJsonMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parseSearchResults maps aliases and confidence`() {
        val root = json.parseToJsonElement(
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
        ).jsonObject

        val result = BangumiJsonMapper.parseSearchResults(root, "葬送的芙莉莲")

        assertEquals("431767", result.single().animeId)
        assertEquals("葬送的芙莉莲", result.single().matchedTitle)
        assertTrue(result.single().confidence > 0.9f)
    }

    @Test
    fun `parseSubject maps Bangumi subject metadata`() {
        val subject = json.parseToJsonElement(
            """
            {
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
        ).jsonObject

        val anime = BangumiJsonMapper.parseSubject(subject, 431767)

        assertEquals("431767", anime.id)
        assertEquals("葬送的芙莉莲", anime.titleCn)
        assertEquals("旅行仍在继续。", anime.summary)
        assertEquals(listOf("奇幻", "冒险"), anime.genres)
        assertEquals(28, anime.episodeCount)
        assertEquals("https://img.example/frieren.jpg", anime.posterUrl)
        assertEquals(1000, anime.bangumiEpStatus)
    }

    @Test
    fun `parseEpisodeMetadata maps regular episode fields`() {
        val episode = json.parseToJsonElement(
            """
            {
              "id": 10,
              "type": 0,
              "ep": 1,
              "name": "First",
              "name_cn": "第一集",
              "airdate": "2023-09-29",
              "desc": "旅途开始",
              "duration_seconds": 1500
            }
            """.trimIndent()
        ).jsonObject

        val metadata = BangumiJsonMapper.parseEpisodeMetadata(episode)

        assertEquals(1, metadata?.episodeNumber)
        assertEquals("第一集", metadata?.title)
        assertEquals("2023-09-29", metadata?.airDate)
        assertEquals("旅途开始", metadata?.summary)
        assertEquals(false, metadata?.isSpecial)
        assertEquals(10, metadata?.bangumiEpisodeId)
        assertEquals(1_500_000L, metadata?.durationMs)
    }

    @Test
    fun `parseCollections maps subject and episode collection payloads`() {
        val subject = json.parseToJsonElement(
            """
            {
              "subject_id": 431767,
              "type": 3,
              "rate": 0,
              "ep_status": 7,
              "updated_at": "2026-05-22T12:00:00Z"
            }
            """.trimIndent()
        ).jsonObject
        val episode = json.parseToJsonElement(
            """
            {
              "type": 2,
              "updated_at": 1710000000,
              "episode": { "id": 10, "ep": 1 }
            }
            """.trimIndent()
        ).jsonObject

        val subjectCollection = BangumiJsonMapper.parseSubjectCollection(subject)
        val episodeCollection = BangumiJsonMapper.parseEpisodeCollection(episode)

        assertEquals(431767, subjectCollection.subjectId)
        assertEquals(BangumiSubjectCollectionType.DOING.value, subjectCollection.type)
        assertEquals(7, subjectCollection.epStatus)
        assertEquals(10, episodeCollection?.episodeId)
        assertEquals(1, episodeCollection?.episodeNumber)
        assertEquals(BangumiEpisodeCollectionType.DONE.value, episodeCollection?.type)
        assertEquals(1710000000L, episodeCollection?.updatedAt)
    }

    @Test
    fun `api payloads build shared Bangumi request bodies`() {
        val search = BangumiApiPayloads.searchSubjects("葬送的芙莉莲").toString()
        val subject = BangumiApiPayloads.subjectCollection(BangumiSubjectCollectionType.DOING).toString()
        val episodeBatch = BangumiApiPayloads
            .episodeCollections(listOf(10, 20, 10), BangumiEpisodeCollectionType.DONE)
            .toString()
        val episode = BangumiApiPayloads.episodeCollection(BangumiEpisodeCollectionType.DONE).toString()

        assertTrue(search.contains(""""keyword":"葬送的芙莉莲""""))
        assertTrue(search.contains(""""type":[2]"""))
        assertEquals("""{"type":3}""", subject)
        assertTrue(episodeBatch.contains(""""episode_id":[10,20]"""))
        assertTrue(episodeBatch.contains(""""type":2"""))
        assertEquals("""{"type":2}""", episode)
    }
}
