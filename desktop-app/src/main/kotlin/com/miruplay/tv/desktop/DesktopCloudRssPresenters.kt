package com.miruplay.tv.desktop

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssSchedulerState

internal fun schedulerStatus(state: DesktopCloudDriveRssSchedulerState): String {
    val prefix = if (state.running) "Scheduler running." else "Scheduler idle."
    val error = state.lastError
    if (!error.isNullOrBlank()) return "$prefix Last check failed: $error"
    val summary = state.lastSummary
    if (summary != null) {
        return "$prefix Last run: ${summary.submitted} submitted, ${summary.skipped} skipped, ${summary.failed} failed, ${summary.organized} organized."
    }
    return if (state.lastCheckedAt > 0L) {
        "$prefix Last check found no due sync."
    } else {
        "$prefix No checks yet."
    }
}

internal fun linkedSourceLabel(
    sources: List<MediaSourceInfo>,
    sourceId: Long?,
): String {
    if (sourceId == null) return "None"
    val source = sources.firstOrNull { it.id == sourceId }
    return source?.let { "${it.name} (${it.type.name})" } ?: "Missing source #$sourceId"
}
