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
    fun `parseSearchResults does not promote short parent titles above specific matches`() {
        val root = json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": 501796,
                  "name": "魔法の姉妹ルルットリリィ",
                  "name_cn": "魔法姐妹露露特莉莉",
                  "rating": { "score": 6.5 },
                  "infobox": [
                    { "key": "别名", "value": [ { "v": "魔法姊妹露露特莉莉" } ] }
                  ]
                },
                {
                  "id": 331725,
                  "name": "魔法 (Official Video)",
                  "name_cn": "魔法",
                  "rating": { "score": 5.6 }
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val result = BangumiJsonMapper.parseSearchResults(root, "魔法姐妹露露莉莉 Mahou no Shimai Rurutto Riryi")

        assertEquals("501796", result.first().animeId)
        assertTrue(result.first().confidence > result.last().confidence)
        assertTrue("Short parent title should not be a reliable match", result.last().confidence < 0.62f)
    }

    @Test
    fun `parseSearchResults keeps sequel title above short series parent`() {
        val root = json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": 408897,
                  "name": "龙族Ⅱ 悼亡者之瞳",
                  "rating": { "score": 4.3 },
                  "infobox": [
                    { "key": "别名", "value": [ { "v": "龙族 第2季" } ] }
                  ]
                },
                {
                  "id": 312297,
                  "name": "龙族",
                  "rating": { "score": 5.2 }
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val result = BangumiJsonMapper.parseSearchResults(root, "龙族II 悼亡者之瞳")

        assertEquals("408897", result.first().animeId)
        assertTrue(result.first().confidence > result.last().confidence)
        assertTrue("Short series title should not be a reliable match", result.last().confidence < 0.62f)
    }

    @Test
    fun `parseSearchResults prefers requested Dr Stone season over generic first season`() {
        val root = json.parseToJsonElement(
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
                },
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
        ).jsonObject

        val result = BangumiJsonMapper.parseSearchResults(root, "Dr STONE 新石纪 第四季")

        assertEquals("471578", result.first().animeId)
        assertTrue(result.first().confidence > result.last().confidence)
        assertTrue(result.last().confidence < 0.62f)
    }

    @Test
    fun `parseSearchResults does not accept one shared franchise token as reliable`() {
        val root = json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": 120791,
                  "name": "銀魂°",
                  "name_cn": "银魂°",
                  "rating": { "score": 8.2 },
                  "infobox": [
                    { "key": "别名", "value": [ { "v": "银魂 第3期" } ] }
                  ]
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val result = BangumiJsonMapper.parseSearchResults(root, "银魂 3 年 Z 班银八老师")

        assertTrue("One shared franchise token should not pass the scrape threshold", result.single().confidence < 0.62f)
    }

    @Test
    fun `parseSearchResults ignores generic numeric token overlap`() {
        val root = json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": 53773,
                  "name": "アタックNo.1",
                  "name_cn": "排球甜心",
                  "rating": { "score": 6.4 },
                  "infobox": [
                    { "key": "别名", "value": [ { "v": "Attack No.1" } ] }
                  ]
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val result = BangumiJsonMapper.parseSearchResults(root, "No 1 Sentai Gozyuger")

        assertTrue("Generic no/1 overlap should not pass the scrape threshold", result.single().confidence < 0.62f)
    }

    @Test
    fun `parseSearchResults rejects shared prefix tokens with unrelated suffixes`() {
        val root = json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": 48352,
                  "name": "仮面ライダーSD 怪奇!?クモ男",
                  "name_cn": "假面骑士SD",
                  "rating": { "score": 6.0 },
                  "infobox": [
                    { "key": "别名", "value": [ { "v": "Kamen Rider SD" } ] }
                  ]
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val result = BangumiJsonMapper.parseSearchResults(root, "Kamen Rider Zeztz")

        assertTrue("Shared franchise prefix should not pass without the subtitle", result.single().confidence < 0.62f)
    }

    @Test
    fun `parseSearchResults keeps related romanized tokens reliable`() {
        val root = json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": 501796,
                  "name": "魔法の姉妹ルルットリリィ",
                  "name_cn": "魔法姐妹露露特莉莉",
                  "rating": { "score": 6.5 },
                  "infobox": [
                    { "key": "别名", "value": [ { "v": "Magical Sisters Lulutto Lilly" } ] }
                  ]
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val result = BangumiJsonMapper.parseSearchResults(root, "Magical Sisters LuluttoLilly")

        assertTrue(result.single().confidence >= 0.62f)
    }

    @Test
    fun `parseSearchResults normalizes full width slash in romanized titles`() {
        val root = json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": 443831,
                  "name": "Fate/strange Fake",
                  "name_cn": "Fate/strange Fake",
                  "rating": { "score": 7.3 }
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val result = BangumiJsonMapper.parseSearchResults(root, "Fate／strange Fake")

        assertEquals("443831", result.single().animeId)
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
