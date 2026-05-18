package com.miruplay.tv.sync.rss

import com.miruplay.tv.core.common.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssSubmissionPlannerTest {
    @Test
    fun `plan classifies filter skips missing submissions and submission types`() {
        val result = RssSubmissionPlanner.plan(
            feedItems = listOf(
                RssFeedItem(
                    title = "Episode 01",
                    guid = "guid-1",
                    link = "magnet:?xt=urn:btih:abc",
                    enclosureUrl = null,
                ),
                RssFeedItem(
                    title = "Preview",
                    guid = "guid-2",
                    link = "magnet:?xt=urn:btih:def",
                    enclosureUrl = null,
                ),
                RssFeedItem(
                    title = "Episode 02",
                    guid = "guid-3",
                    link = null,
                    enclosureUrl = "https://example.test/episode-02.torrent?token=abc",
                ),
                RssFeedItem(
                    title = "Episode 03",
                    guid = null,
                    link = null,
                    enclosureUrl = null,
                ),
            ),
            filterRegex = "Episode",
        )

        assertTrue(result is Result.Success)
        val decisions = (result as Result.Success).data
        assertEquals(RssSubmissionDecisionStatus.WOULD_SUBMIT, decisions[0].status)
        assertEquals(RssSubmissionUrlType.MAGNET, decisions[0].submissionType)
        assertEquals("guid-1", decisions[0].itemKey)
        assertEquals(RssSubmissionDecisionStatus.SKIPPED_FILTER, decisions[1].status)
        assertEquals(RssSubmissionDecisionStatus.WOULD_SUBMIT, decisions[2].status)
        assertEquals(RssSubmissionUrlType.TORRENT, decisions[2].submissionType)
        assertEquals(RssSubmissionDecisionStatus.MISSING_SUBMISSION, decisions[3].status)
        assertEquals(RssSubmissionUrlType.NONE, decisions[3].submissionType)
    }

    @Test
    fun `stable item key falls back to sha1 of title and submission url`() {
        val item = RssFeedItem(
            title = "Episode 01",
            guid = null,
            link = "magnet:?xt=urn:btih:abc",
            enclosureUrl = null,
        )

        assertEquals(
            "08428a959c1a8e628ae7ebe187d9a1ae7247a7fe",
            RssSubmissionPlanner.stableItemKey(item, "magnet:?xt=urn:btih:abc"),
        )
    }

    @Test
    fun `plan rejects invalid filter regex`() {
        val result = RssSubmissionPlanner.plan(
            feedItems = listOf(RssFeedItem("Episode", null, "magnet:?xt=urn:btih:abc", null)),
            filterRegex = "[",
        )

        assertTrue(result is Result.Error)
    }
}
