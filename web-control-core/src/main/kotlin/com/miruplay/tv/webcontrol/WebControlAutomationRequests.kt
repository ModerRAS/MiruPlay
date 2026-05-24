package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.buildRssSubscriptionFromForm
import com.miruplay.tv.model.withAutomationFormValues

fun CloudDriveConfigRequest.toAutomationConfig(current: CloudDriveAutomationConfig): CloudDriveAutomationConfig =
    current.withAutomationFormValues(
        endpointUrl = endpointUrl,
        username = username,
        webDavSourceId = webDavSourceId?.takeIf { it > 0L },
        inboxPath = inboxPath,
        libraryPath = libraryPath,
        intervalMinutes = intervalMinutes,
        enabled = enabled,
        rssProxyEnabled = rssProxyEnabled,
        rssProxyHost = rssProxyHost,
        rssProxyPort = rssProxyPort,
    )

fun RssSubscriptionRequest.toSubscription(existingLastCheckedAt: Long = 0L): RssSubscriptionInfo? =
    buildRssSubscriptionFromForm(
        name = name,
        url = url,
        filterRegex = filterRegex.orEmpty(),
        enabled = enabled,
        existingId = id,
        existingLastCheckedAt = existingLastCheckedAt,
    )

fun RssSubscriptionInfo.withSavedId(savedId: Long): RssSubscriptionInfo =
    copy(id = if (id > 0L) id else savedId)
