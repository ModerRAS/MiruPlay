package com.miruplay.tv.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiSubjectMatcherTest {
    @Test
    fun `rank prefers subject with requested season evidence over generic series title`() {
        val result = BangumiSubjectMatcher.rank(
            context = BangumiMatchContext.fromQueries(
                listOf("Dr STONE 新石纪 第四季", "Dr STONE 新石纪")
            ),
            candidates = listOf(
                BangumiSubjectMatchCandidate(
                    id = "266794",
                    title = "Dr.STONE",
                    titleCn = "石纪元",
                    aliases = listOf("Dr.STONE", "Dr STONE 新石纪"),
                    score = 7.5f,
                    serverIndex = 0,
                ),
                BangumiSubjectMatchCandidate(
                    id = "471578",
                    title = "Dr.STONE SCIENCE FUTURE",
                    titleCn = "石纪元 科学与未来",
                    aliases = listOf("Dr.STONE SCIENCE FUTURE", "Dr STONE 新石纪 第四季", "新石纪 第四季"),
                    score = 7.2f,
                    serverIndex = 1,
                ),
            )
        )

        assertEquals("471578", result.first().candidate.id)
        assertTrue(result.first().confidence > result.last().confidence)
        assertTrue("Generic seasonless result should stay below auto-scrape threshold", result.last().confidence < 0.62f)
    }

    @Test
    fun `rank does not penalize generic title when request has no season evidence`() {
        val result = BangumiSubjectMatcher.rank(
            query = "Dr STONE 新石纪",
            candidates = listOf(
                BangumiSubjectMatchCandidate(
                    id = "266794",
                    title = "Dr.STONE",
                    titleCn = "石纪元",
                    aliases = listOf("Dr.STONE", "Dr STONE 新石纪"),
                    score = 7.5f,
                    serverIndex = 0,
                )
            )
        )

        assertEquals("266794", result.single().candidate.id)
        assertTrue(result.single().confidence >= 0.9f)
    }
}
