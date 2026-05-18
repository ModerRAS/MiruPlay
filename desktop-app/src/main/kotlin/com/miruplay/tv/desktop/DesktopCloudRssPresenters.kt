package com.miruplay.tv.desktop

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.sync.rss.CloudDriveRssRunSummary
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssSchedulerState
import com.miruplay.tv.sync.rss.cloudDriveCredentialsClearedStatus as sharedCloudDriveCredentialsClearedStatus
import com.miruplay.tv.sync.rss.cloudDriveCredentialsSavedStatus as sharedCloudDriveCredentialsSavedStatus
import com.miruplay.tv.sync.rss.cloudDriveLoginRequiredStatus as sharedCloudDriveLoginRequiredStatus
import com.miruplay.tv.sync.rss.cloudDriveLoginStartedStatus as sharedCloudDriveLoginStartedStatus
import com.miruplay.tv.sync.rss.cloudDriveLoginSucceededStatus as sharedCloudDriveLoginSucceededStatus
import com.miruplay.tv.sync.rss.cloudDriveTokenRequiredStatus as sharedCloudDriveTokenRequiredStatus
import com.miruplay.tv.sync.rss.cloudDriveTokenValidationStartedStatus as sharedCloudDriveTokenValidationStartedStatus
import com.miruplay.tv.sync.rss.cloudRssConfigSavedStatus as sharedCloudRssConfigSavedStatus
import com.miruplay.tv.sync.rss.cloudRssRunStartedStatus as sharedCloudRssRunStartedStatus
import com.miruplay.tv.sync.rss.cloudRssScanSourceClearedStatus as sharedCloudRssScanSourceClearedStatus
import com.miruplay.tv.sync.rss.cloudRssScanSourceRequiredStatus as sharedCloudRssScanSourceRequiredStatus
import com.miruplay.tv.sync.rss.cloudRssSchedulerDisabledStatus as sharedCloudRssSchedulerDisabledStatus
import com.miruplay.tv.sync.rss.cloudRssSchedulerStartStatus as sharedCloudRssSchedulerStartStatus
import com.miruplay.tv.sync.rss.cloudRssSchedulerStoppedStatus as sharedCloudRssSchedulerStoppedStatus
import com.miruplay.tv.sync.rss.completeStatus
import com.miruplay.tv.sync.rss.linkedCloudRssScanSourceStatus
import com.miruplay.tv.sync.rss.linkedCloudDriveSourceLabel
import com.miruplay.tv.sync.rss.rssSubscriptionDeletedStatus as sharedRssSubscriptionDeletedStatus
import com.miruplay.tv.sync.rss.rssSubscriptionRequiredStatus as sharedRssSubscriptionRequiredStatus
import com.miruplay.tv.sync.rss.rssUrlRequiredStatus as sharedRssUrlRequiredStatus
import com.miruplay.tv.sync.rss.savedStatus
import com.miruplay.tv.sync.rss.schedulerStatus as sharedSchedulerStatus
import com.miruplay.tv.sync.rss.selectedStatus
import com.miruplay.tv.sync.rss.verifiedStatus

internal fun schedulerStatus(state: DesktopCloudDriveRssSchedulerState): String =
    state.sharedSchedulerStatus()

internal fun linkedSourceLabel(
    sources: List<MediaSourceInfo>,
    sourceId: Long?,
): String =
    linkedCloudDriveSourceLabel(sources, sourceId)

internal fun cloudRssConfigSavedMessage(): String =
    sharedCloudRssConfigSavedStatus()

internal fun cloudDriveCredentialsSavedMessage(): String =
    sharedCloudDriveCredentialsSavedStatus()

internal fun cloudDriveCredentialsClearedMessage(): String =
    sharedCloudDriveCredentialsClearedStatus()

internal fun cloudDriveLoginRequiredMessage(): String =
    sharedCloudDriveLoginRequiredStatus()

internal fun cloudDriveLoginStartedMessage(): String =
    sharedCloudDriveLoginStartedStatus()

internal fun cloudDriveLoginSucceededMessage(): String =
    sharedCloudDriveLoginSucceededStatus()

internal fun cloudDriveTokenRequiredMessage(): String =
    sharedCloudDriveTokenRequiredStatus()

internal fun cloudDriveTokenValidationStartedMessage(): String =
    sharedCloudDriveTokenValidationStartedStatus()

internal fun cloudDriveTokenVerifiedMessage(info: CloudDriveTokenInfo): String =
    info.verifiedStatus()

internal fun cloudRssRunStartedMessage(): String =
    sharedCloudRssRunStartedStatus()

internal fun cloudRssRunCompleteMessage(summary: CloudDriveRssRunSummary): String =
    summary.completeStatus()

internal fun cloudRssSchedulerDisabledMessage(): String =
    sharedCloudRssSchedulerDisabledStatus()

internal fun cloudRssSchedulerStartMessage(started: Boolean): String =
    sharedCloudRssSchedulerStartStatus(started)

internal fun cloudRssSchedulerStoppedMessage(): String =
    sharedCloudRssSchedulerStoppedStatus()

internal fun cloudRssScanSourceRequiredMessage(): String =
    sharedCloudRssScanSourceRequiredStatus()

internal fun linkedCloudRssScanSourceMessage(source: MediaSourceInfo): String =
    source.linkedCloudRssScanSourceStatus()

internal fun cloudRssScanSourceClearedMessage(): String =
    sharedCloudRssScanSourceClearedStatus()

internal fun rssUrlRequiredMessage(): String =
    sharedRssUrlRequiredStatus()

internal fun rssSubscriptionSavedMessage(subscription: RssSubscriptionInfo): String =
    subscription.savedStatus()

internal fun rssSubscriptionSelectedMessage(subscription: RssSubscriptionInfo): String =
    subscription.selectedStatus()

internal fun rssSubscriptionRequiredMessage(): String =
    sharedRssSubscriptionRequiredStatus()

internal fun rssSubscriptionDeletedMessage(): String =
    sharedRssSubscriptionDeletedStatus()
