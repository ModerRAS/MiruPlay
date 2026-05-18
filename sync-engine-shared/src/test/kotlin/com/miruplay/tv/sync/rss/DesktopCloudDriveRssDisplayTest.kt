package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.RssSubscriptionInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopCloudDriveRssDisplayTest {
    @Test
    fun `scheduler status describes idle and first run states`() {
        assertEquals("Scheduler idle. No checks yet.", DesktopCloudDriveRssSchedulerState().schedulerStatus())
        assertEquals(
            "Scheduler idle. Last check found no due sync.",
            DesktopCloudDriveRssSchedulerState(lastCheckedAt = 123L).schedulerStatus(),
        )
    }

    @Test
    fun `scheduler status includes last error before summary`() {
        val state = DesktopCloudDriveRssSchedulerState(
            running = true,
            lastError = "network down",
            lastSummary = CloudDriveRssRunSummary(submitted = 1, skipped = 2, failed = 0, organized = 3),
        )

        assertEquals("Scheduler running. Last check failed: network down", state.schedulerStatus())
    }

    @Test
    fun `scheduler status summarizes last successful run`() {
        val state = DesktopCloudDriveRssSchedulerState(
            running = false,
            lastSummary = CloudDriveRssRunSummary(submitted = 2, skipped = 1, failed = 1, organized = 4),
        )

        assertEquals("Scheduler idle. Last run: 2 submitted, 1 skipped, 1 failed, 4 organized.", state.schedulerStatus())
    }

    @Test
    fun `linked source label handles none missing and existing source`() {
        val sources = listOf(
            MediaSourceInfo(id = 7L, name = "Cloud WebDAV", type = MediaSourceType.WEBDAV),
        )

        assertEquals("None", linkedCloudDriveSourceLabel(sources, null))
        assertEquals("Missing source #8", linkedCloudDriveSourceLabel(sources, 8L))
        assertEquals("Cloud WebDAV (WEBDAV)", linkedCloudDriveSourceLabel(sources, 7L))
    }

    @Test
    fun `cloud drive credential statuses share desktop wording`() {
        assertEquals("Cloud/RSS automation settings saved.", cloudRssConfigSavedStatus())
        assertEquals("CloudDrive credentials saved.", cloudDriveCredentialsSavedStatus())
        assertEquals("CloudDrive credentials cleared.", cloudDriveCredentialsClearedStatus())
        assertEquals(
            "Enter CloudDrive2 endpoint, username, and password first.",
            cloudDriveLoginRequiredStatus(),
        )
        assertEquals("Logging into CloudDrive2...", cloudDriveLoginStartedStatus())
        assertEquals("CloudDrive2 login succeeded; token saved.", cloudDriveLoginSucceededStatus())
        assertEquals(
            "Enter CloudDrive2 endpoint and API token first.",
            cloudDriveTokenRequiredStatus(),
        )
        assertEquals("Validating CloudDrive2 API token...", cloudDriveTokenValidationStartedStatus())
    }

    @Test
    fun `token verification status uses friendly name then root fallback`() {
        val named = tokenInfo(friendlyName = "MiruPlay")
        val rooted = tokenInfo(friendlyName = "", rootDir = "/Anime")
        val fallback = tokenInfo(friendlyName = "", rootDir = "")

        assertEquals("CloudDrive2 API token verified and saved: MiruPlay.", named.verifiedStatus())
        assertEquals("CloudDrive2 API token verified and saved: /Anime.", rooted.verifiedStatus())
        assertEquals("CloudDrive2 API token verified and saved: CloudDrive2.", fallback.verifiedStatus())
    }

    @Test
    fun `subscription list statuses share desktop wording`() {
        val subscription = RssSubscriptionInfo(name = "Anime", url = "https://example.test/rss.xml")

        assertEquals("Load or save Cloud/RSS automation settings.", cloudRssInitialStatus())
        assertEquals("No RSS subscriptions configured.", emptyList<RssSubscriptionInfo>().loadedStatus())
        assertEquals("Loaded 1 RSS subscription(s).", listOf(subscription).loadedStatus())
        assertEquals("No RSS subscriptions configured.", emptyList<RssSubscriptionInfo>().showingStatus())
        assertEquals("Showing 1 RSS subscription(s).", listOf(subscription).showingStatus())
        assertEquals("Failed to load RSS subscriptions.", rssSubscriptionsLoadFailedStatus(null))
        assertEquals("load failed", rssSubscriptionsLoadFailedStatus("load failed"))
        assertEquals("Failed to refresh RSS subscriptions.", rssSubscriptionsRefreshFailedStatus(null))
        assertEquals("refresh failed", rssSubscriptionsRefreshFailedStatus("refresh failed"))
    }

    @Test
    fun `run scheduler scan source and rss statuses share desktop wording`() {
        val summary = CloudDriveRssRunSummary(submitted = 3, skipped = 2, failed = 1, organized = 4)
        val source = MediaSourceInfo(id = 7L, name = "Cloud WebDAV", type = MediaSourceType.WEBDAV)
        val subscription = RssSubscriptionInfo(name = "Anime", url = "https://example.test/rss.xml")

        assertEquals("Running Cloud/RSS sync...", cloudRssRunStartedStatus())
        assertEquals(
            "Sync complete: 3 submitted, 2 skipped, 1 failed, 4 organized.",
            summary.completeStatus(),
        )
        assertEquals(
            "Enable and save Cloud/RSS sync before starting the scheduler.",
            cloudRssSchedulerDisabledStatus(),
        )
        assertEquals("Cloud/RSS scheduler started.", cloudRssSchedulerStartStatus(started = true))
        assertEquals(
            "Cloud/RSS scheduler is already running.",
            cloudRssSchedulerStartStatus(started = false),
        )
        assertEquals("Cloud/RSS scheduler stopped.", cloudRssSchedulerStoppedStatus())
        assertEquals(
            "Open a saved media source before linking Cloud/RSS scanning.",
            cloudRssScanSourceRequiredStatus(),
        )
        assertEquals(
            "Linked scan source was not found. Clear or relink the Cloud/RSS scan source.",
            cloudRssScanSourceMissingStatus(),
        )
        assertEquals(
            "Linked Cloud/RSS post-sync scan source: Cloud WebDAV. Save sync config to persist it.",
            source.linkedCloudRssScanSourceStatus(),
        )
        assertEquals(
            "Scheduled sync complete. Rescanning Cloud WebDAV...",
            source.cloudRssRescanStartedStatus("Scheduled sync complete."),
        )
        assertEquals(
            "Cloud/RSS post-sync scan source cleared. Save sync config to persist it.",
            cloudRssScanSourceClearedStatus(),
        )
        assertEquals("Enter an RSS URL first.", rssUrlRequiredStatus())
        assertEquals("RSS subscription saved: Anime", subscription.savedStatus())
        assertEquals("Selected RSS subscription: Anime", subscription.selectedStatus())
        assertEquals("Select an RSS subscription first.", rssSubscriptionRequiredStatus())
        assertEquals("RSS subscription deleted.", rssSubscriptionDeletedStatus())
    }

    private fun tokenInfo(
        friendlyName: String,
        rootDir: String = "/",
    ): CloudDriveTokenInfo =
        CloudDriveTokenInfo(
            rootDir = rootDir,
            friendlyName = friendlyName,
            allowList = true,
            allowCreateFolder = true,
            allowCreateFile = true,
            allowWrite = true,
            allowMove = true,
            allowAddOfflineDownload = true,
        )
}
