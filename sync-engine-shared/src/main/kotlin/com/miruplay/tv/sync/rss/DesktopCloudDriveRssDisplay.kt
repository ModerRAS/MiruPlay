package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.RssSubscriptionInfo

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

fun cloudRssConfigSavedStatus(): String =
    "Cloud/RSS automation settings saved."

fun cloudRssInitialStatus(): String =
    "Load or save Cloud/RSS automation settings."

fun cloudDriveCredentialsSavedStatus(): String =
    "CloudDrive credentials saved."

fun cloudDriveCredentialsClearedStatus(): String =
    "CloudDrive credentials cleared."

fun cloudDriveLoginRequiredStatus(): String =
    "Enter CloudDrive2 endpoint, username, and password first."

fun cloudDriveLoginStartedStatus(): String =
    "Logging into CloudDrive2..."

fun cloudDriveLoginSucceededStatus(): String =
    "CloudDrive2 login succeeded; token saved."

fun cloudDriveTokenRequiredStatus(): String =
    "Enter CloudDrive2 endpoint and API token first."

fun cloudDriveTokenValidationStartedStatus(): String =
    "Validating CloudDrive2 API token..."

fun CloudDriveTokenInfo.verifiedStatus(): String {
    val label = friendlyName.takeIf { it.isNotBlank() }
        ?: rootDir.ifBlank { "CloudDrive2" }
    return "CloudDrive2 API token verified and saved: $label."
}

fun cloudRssRunStartedStatus(): String =
    "Running Cloud/RSS sync..."

fun CloudDriveRssRunSummary.completeStatus(): String =
    "Sync complete: $submitted submitted, $skipped skipped, $failed failed, $organized organized."

fun cloudRssSchedulerDisabledStatus(): String =
    "Enable and save Cloud/RSS sync before starting the scheduler."

fun cloudRssSchedulerStartStatus(started: Boolean): String =
    if (started) {
        "Cloud/RSS scheduler started."
    } else {
        "Cloud/RSS scheduler is already running."
    }

fun cloudRssSchedulerStoppedStatus(): String =
    "Cloud/RSS scheduler stopped."

fun cloudRssScanSourceRequiredStatus(): String =
    "Open a saved media source before linking Cloud/RSS scanning."

fun cloudRssScanSourceMissingStatus(): String =
    "Linked scan source was not found. Clear or relink the Cloud/RSS scan source."

fun MediaSourceInfo.linkedCloudRssScanSourceStatus(): String =
    "Linked Cloud/RSS post-sync scan source: $name. Save sync config to persist it."

fun MediaSourceInfo.cloudRssRescanStartedStatus(reason: String): String =
    "$reason Rescanning $name..."

fun cloudRssScanSourceClearedStatus(): String =
    "Cloud/RSS post-sync scan source cleared. Save sync config to persist it."

fun rssUrlRequiredStatus(): String =
    "Enter an RSS URL first."

fun List<RssSubscriptionInfo>.loadedStatus(): String =
    if (isEmpty()) {
        "No RSS subscriptions configured."
    } else {
        "Loaded $size RSS subscription(s)."
    }

fun List<RssSubscriptionInfo>.showingStatus(): String =
    if (isEmpty()) {
        "No RSS subscriptions configured."
    } else {
        "Showing $size RSS subscription(s)."
    }

fun rssSubscriptionsLoadFailedStatus(errorMessage: String?): String =
    errorMessage ?: "Failed to load RSS subscriptions."

fun rssSubscriptionsRefreshFailedStatus(errorMessage: String?): String =
    errorMessage ?: "Failed to refresh RSS subscriptions."

fun RssSubscriptionInfo.savedStatus(): String =
    "RSS subscription saved: $name"

fun RssSubscriptionInfo.selectedStatus(): String =
    "Selected RSS subscription: $name"

fun rssSubscriptionRequiredStatus(): String =
    "Select an RSS subscription first."

fun rssSubscriptionDeletedStatus(): String =
    "RSS subscription deleted."
