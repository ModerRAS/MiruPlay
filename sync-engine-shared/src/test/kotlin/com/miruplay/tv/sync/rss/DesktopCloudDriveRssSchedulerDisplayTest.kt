package com.miruplay.tv.sync.rss

import com.miruplay.tv.model.CloudDriveRssRunSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopCloudDriveRssSchedulerDisplayTest {
    @Test
    fun `scheduler status delegates desktop state to shared TV status copy`() {
        assertEquals("调度器待命，尚未检查。", DesktopCloudDriveRssSchedulerState().schedulerStatus())
        assertEquals(
            "调度器待命，上次检查没有待同步内容。",
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

        assertEquals("调度器运行中，上次检查失败：network down", state.schedulerStatus())
    }

    @Test
    fun `scheduler status summarizes last successful run`() {
        val state = DesktopCloudDriveRssSchedulerState(
            running = false,
            lastSummary = CloudDriveRssRunSummary(submitted = 2, skipped = 1, failed = 1, organized = 4),
        )

        assertEquals("调度器待命，上次运行：提交 2 个，跳过 1 个，失败 1 个，整理 4 个。", state.schedulerStatus())
    }
}
