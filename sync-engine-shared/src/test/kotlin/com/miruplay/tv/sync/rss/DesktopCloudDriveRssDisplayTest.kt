package com.miruplay.tv.sync.rss

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
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
}
