package com.miruplay.tv.sync.rss

import com.miruplay.tv.model.MediaSourceInfo

fun DesktopCloudDriveRssSchedulerState.schedulerStatus(): String {
    val prefix = if (running) "Scheduler running." else "Scheduler idle."
    val error = lastError
    if (!error.isNullOrBlank()) return "$prefix Last check failed: $error"
    val summary = lastSummary
    if (summary != null) {
        return "$prefix Last run: ${summary.submitted} submitted, ${summary.skipped} skipped, ${summary.failed} failed, ${summary.organized} organized."
    }
    return if (lastCheckedAt > 0L) {
        "$prefix Last check found no due sync."
    } else {
        "$prefix No checks yet."
    }
}

fun linkedCloudDriveSourceLabel(
    sources: List<MediaSourceInfo>,
    sourceId: Long?,
): String {
    if (sourceId == null) return "None"
    val source = sources.firstOrNull { it.id == sourceId }
    return source?.let { "${it.name} (${it.type.name})" } ?: "Missing source #$sourceId"
}
