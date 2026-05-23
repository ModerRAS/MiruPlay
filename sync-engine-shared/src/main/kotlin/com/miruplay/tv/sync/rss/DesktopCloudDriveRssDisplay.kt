package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.cloudDriveTokenVerifiedStatus
import com.miruplay.tv.model.cloudDriveRssRunSummaryStatus
import com.miruplay.tv.model.cloudRssLinkedScanSourceStatus
import com.miruplay.tv.model.rssSubscriptionSavedStatus
import com.miruplay.tv.model.rssSubscriptionSelectedStatus
import com.miruplay.tv.model.rssSubscriptionsLoadedStatus
import com.miruplay.tv.model.rssSubscriptionsShowingStatus
import com.miruplay.tv.model.settingsLinkedSourceLabel
import com.miruplay.tv.model.sourcePickerTitle

fun linkedCloudDriveSourceLabel(
    sources: List<MediaSourceInfo>,
    sourceId: Long?,
): String = settingsLinkedSourceLabel(sources, sourceId)

fun cloudRssConfigSavedStatus(): String =
    com.miruplay.tv.model.cloudRssConfigSavedStatus()

fun cloudRssInitialStatus(): String =
    com.miruplay.tv.model.cloudRssInitialStatus()

fun cloudDriveCredentialsSavedStatus(): String =
    com.miruplay.tv.model.cloudDriveCredentialsSavedStatus()

fun cloudDriveCredentialsClearedStatus(): String =
    com.miruplay.tv.model.cloudDriveCredentialsClearedStatus()

fun cloudDriveLoginRequiredStatus(): String =
    com.miruplay.tv.model.cloudDriveLoginRequiredStatus()

fun cloudDriveLoginStartedStatus(): String =
    com.miruplay.tv.model.cloudDriveLoginStartedStatus()

fun cloudDriveLoginSucceededStatus(): String =
    com.miruplay.tv.model.cloudDriveLoginSucceededStatus()

fun cloudDriveTokenRequiredStatus(): String =
    com.miruplay.tv.model.cloudDriveTokenRequiredStatus()

fun cloudDriveTokenValidationStartedStatus(): String =
    com.miruplay.tv.model.cloudDriveTokenValidationStartedStatus()

fun CloudDriveTokenInfo.verifiedStatus(): String {
    return cloudDriveTokenVerifiedStatus(
        friendlyName = friendlyName,
        rootDir = rootDir,
    )
}

fun cloudRssRunStartedStatus(): String =
    com.miruplay.tv.model.cloudRssRunStartedStatus()

fun CloudDriveRssRunSummary.completeStatus(): String =
    cloudDriveRssRunSummaryStatus(this)

fun cloudRssSchedulerDisabledStatus(): String =
    com.miruplay.tv.model.cloudRssSchedulerDisabledStatus()

fun cloudRssSchedulerStartStatus(started: Boolean): String =
    com.miruplay.tv.model.cloudRssSchedulerStartStatus(started)

fun cloudRssSchedulerStoppedStatus(): String =
    com.miruplay.tv.model.cloudRssSchedulerStoppedStatus()

fun cloudRssScanSourceRequiredStatus(): String =
    com.miruplay.tv.model.cloudRssScanSourceRequiredStatus()

fun cloudRssScanSourceMissingStatus(): String =
    com.miruplay.tv.model.cloudRssScanSourceMissingStatus()

fun MediaSourceInfo.linkedCloudRssScanSourceStatus(): String =
    cloudRssLinkedScanSourceStatus(sourcePickerTitle())

fun MediaSourceInfo.cloudRssRescanStartedStatus(reason: String): String =
    com.miruplay.tv.model.cloudRssRescanStartedStatus(reason, sourcePickerTitle())

fun cloudRssScanSourceClearedStatus(): String =
    com.miruplay.tv.model.cloudRssScanSourceClearedStatus()

fun rssUrlRequiredStatus(): String =
    com.miruplay.tv.model.rssUrlRequiredStatus()

fun List<RssSubscriptionInfo>.loadedStatus(): String =
    rssSubscriptionsLoadedStatus(size)

fun List<RssSubscriptionInfo>.showingStatus(): String =
    rssSubscriptionsShowingStatus(size)

fun rssSubscriptionsLoadFailedStatus(errorMessage: String?): String =
    com.miruplay.tv.model.rssSubscriptionsLoadFailedStatus(errorMessage)

fun rssSubscriptionsRefreshFailedStatus(errorMessage: String?): String =
    com.miruplay.tv.model.rssSubscriptionsRefreshFailedStatus(errorMessage)

fun RssSubscriptionInfo.savedStatus(): String =
    rssSubscriptionSavedStatus(name)

fun RssSubscriptionInfo.selectedStatus(): String =
    rssSubscriptionSelectedStatus(name)

fun rssSubscriptionRequiredStatus(): String =
    com.miruplay.tv.model.rssSubscriptionRequiredStatus()

fun rssSubscriptionDeletedStatus(): String =
    com.miruplay.tv.model.rssSubscriptionDeletedStatus()
