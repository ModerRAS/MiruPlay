package com.miruplay.tv

import com.miruplay.tv.repository.WebControlAccessManager
import java.io.Closeable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferredAppStartupCoordinatorTest {
    @Test
    fun `start runs deferred startup tasks in expected order`() {
        val events = mutableListOf<String>()
        val webControlAccess = FakeWebControlAccessManager(enabled = false)
        val coordinator = DeferredAppStartupCoordinator(
            webControlAccessManager = webControlAccess,
            syncWebControlServer = { events += "sync_web_control" },
            startCloudDriveRssScheduler = { events += "start_cloud_drive_rss" },
            startBangumiArchiveScheduler = { events += "start_bangumi_archive" },
            startLogUploadScheduler = { events += "start_log_upload" },
            markStartupCheckpoint = { checkpoint, attributes ->
                val enabled = attributes["web_control_enabled"]
                events += if (enabled == null) {
                    "checkpoint:$checkpoint"
                } else {
                    "checkpoint:$checkpoint:$enabled"
                }
            },
            onWebControlPreferenceChanged = { enabled ->
                events += "preference_changed:$enabled"
            },
        )

        coordinator.start()

        assertEquals(
            listOf(
                "checkpoint:web_control_sync:false",
                "sync_web_control",
                "checkpoint:cloud_drive_scheduler_start",
                "start_cloud_drive_rss",
                "checkpoint:bangumi_archive_scheduler_start",
                "start_bangumi_archive",
                "checkpoint:log_upload_scheduler_start",
                "start_log_upload",
            ),
            events,
        )
    }

    @Test
    fun `registered preference listener re-syncs web control when setting changes`() {
        val events = mutableListOf<String>()
        val webControlAccess = FakeWebControlAccessManager(enabled = false)
        val coordinator = DeferredAppStartupCoordinator(
            webControlAccessManager = webControlAccess,
            syncWebControlServer = { events += "sync_web_control" },
            startCloudDriveRssScheduler = {},
            startBangumiArchiveScheduler = {},
            startLogUploadScheduler = {},
            markStartupCheckpoint = { _, _ -> },
            onWebControlPreferenceChanged = { enabled ->
                events += "preference_changed:$enabled"
            },
        )

        coordinator.start()
        events.clear()

        webControlAccess.enabled = true
        webControlAccess.fireEnabledChange()

        assertEquals(
            listOf(
                "preference_changed:true",
                "sync_web_control",
            ),
            events,
        )
    }

    @Test
    fun `deferred startup isolates scheduler failures and continues remaining startup tasks`() {
        val events = mutableListOf<String>()
        val webControlAccess = FakeWebControlAccessManager(enabled = true)
        val coordinator = DeferredAppStartupCoordinator(
            webControlAccessManager = webControlAccess,
            syncWebControlServer = { events += "sync_web_control" },
            startCloudDriveRssScheduler = {
                events += "start_cloud_drive_rss"
                error("cloud drive scheduler boom")
            },
            startBangumiArchiveScheduler = { events += "start_bangumi_archive" },
            startLogUploadScheduler = { events += "start_log_upload" },
            markStartupCheckpoint = { checkpoint, _ ->
                events += "checkpoint:$checkpoint"
            },
            onWebControlPreferenceChanged = {},
        )

        coordinator.start()

        assertEquals(
            listOf(
                "checkpoint:web_control_sync",
                "sync_web_control",
                "checkpoint:cloud_drive_scheduler_start",
                "start_cloud_drive_rss",
                "checkpoint:bangumi_archive_scheduler_start",
                "start_bangumi_archive",
                "checkpoint:log_upload_scheduler_start",
                "start_log_upload",
            ),
            events,
        )
        assertTrue("scheduler failure should not abort remaining startup tasks", "start_log_upload" in events)
    }
}

private class FakeWebControlAccessManager(
    enabled: Boolean,
) : WebControlAccessManager {
    private var listener: ((Boolean) -> Unit)? = null

    var enabled: Boolean = enabled

    override var webControlEnabled: Boolean
        get() = enabled
        set(value) {
            enabled = value
        }

    override val accessToken: String
        get() = "token"

    override fun rotateAccessToken(): String = "token"

    override fun addEnabledChangeListener(onChanged: (Boolean) -> Unit): Closeable {
        listener = onChanged
        return Closeable { listener = null }
    }

    fun fireEnabledChange() {
        listener?.invoke(enabled)
    }
}
