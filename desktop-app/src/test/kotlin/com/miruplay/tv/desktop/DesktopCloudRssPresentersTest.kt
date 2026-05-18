package com.miruplay.tv.desktop

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.sync.rss.CloudDriveRssRunSummary
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssSchedulerState
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopCloudRssPresentersTest {
    @Test
    fun `scheduler status describes idle and first run states`() {
        assertEquals("Scheduler idle. No checks yet.", schedulerStatus(DesktopCloudDriveRssSchedulerState()))
        assertEquals(
            "Scheduler idle. Last check found no due sync.",
            schedulerStatus(DesktopCloudDriveRssSchedulerState(lastCheckedAt = 123L))
        )
    }

    @Test
    fun `scheduler status includes last error before summary`() {
        val state = DesktopCloudDriveRssSchedulerState(
            running = true,
            lastError = "network down",
            lastSummary = CloudDriveRssRunSummary(submitted = 1, skipped = 2, failed = 0, organized = 3),
        )

        assertEquals("Scheduler running. Last check failed: network down", schedulerStatus(state))
    }

    @Test
    fun `scheduler status summarizes last successful run`() {
        val state = DesktopCloudDriveRssSchedulerState(
            running = false,
            lastSummary = CloudDriveRssRunSummary(submitted = 2, skipped = 1, failed = 1, organized = 4),
        )

        assertEquals("Scheduler idle. Last run: 2 submitted, 1 skipped, 1 failed, 4 organized.", schedulerStatus(state))
    }

    @Test
    fun `linked source label handles none missing and existing source`() {
        val sources = listOf(
            MediaSourceInfo(id = 7L, name = "Cloud WebDAV", type = MediaSourceType.WEBDAV),
        )

        assertEquals("None", linkedSourceLabel(sources, null))
        assertEquals("Missing source #8", linkedSourceLabel(sources, 8L))
        assertEquals("Cloud WebDAV (WEBDAV)", linkedSourceLabel(sources, 7L))
    }

    @Test
    fun `subscription list messages are shared with sync display`() {
        val subscription = RssSubscriptionInfo(name = "Anime", url = "https://example.test/rss.xml")

        assertEquals("Load or save Cloud/RSS automation settings.", cloudRssInitialMessage())
        assertEquals("No RSS subscriptions configured.", rssSubscriptionsLoadedMessage(emptyList()))
        assertEquals("Loaded 1 RSS subscription(s).", rssSubscriptionsLoadedMessage(listOf(subscription)))
        assertEquals("No RSS subscriptions configured.", rssSubscriptionsShowingMessage(emptyList()))
        assertEquals("Showing 1 RSS subscription(s).", rssSubscriptionsShowingMessage(listOf(subscription)))
        assertEquals("Failed to load RSS subscriptions.", rssSubscriptionsLoadFailedMessage(null))
        assertEquals("load failed", rssSubscriptionsLoadFailedMessage("load failed"))
        assertEquals("Failed to refresh RSS subscriptions.", rssSubscriptionsRefreshFailedMessage(null))
        assertEquals("refresh failed", rssSubscriptionsRefreshFailedMessage("refresh failed"))
    }

    @Test
    fun `linked scan messages are shared with sync display`() {
        val source = MediaSourceInfo(id = 7L, name = "Cloud WebDAV", type = MediaSourceType.WEBDAV)

        assertEquals(
            "Linked scan source was not found. Clear or relink the Cloud/RSS scan source.",
            cloudRssScanSourceMissingMessage(),
        )
        assertEquals(
            "Scheduled sync complete. Rescanning Cloud WebDAV...",
            cloudRssRescanStartedMessage(source, "Scheduled sync complete."),
        )
    }
}
