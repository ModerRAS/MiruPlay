package com.miruplay.tv.sync

import android.content.Context
import androidx.work.ForegroundInfo
import com.miruplay.tv.background.BackgroundTaskNotificationIds
import com.miruplay.tv.background.BackgroundTaskNotifications
import com.miruplay.tv.background.BackgroundTaskProgress

internal fun cloudDriveRssForegroundInfo(
    context: Context,
    text: String,
): ForegroundInfo =
    backgroundTaskForegroundInfo(
        context = context,
        notificationId = BackgroundTaskNotificationIds.CLOUD_DRIVE_RSS_WORKER,
        title = "CloudDrive/RSS 同步",
        text = text,
    )

internal fun bangumiArchiveForegroundInfo(
    context: Context,
    text: String,
): ForegroundInfo =
    backgroundTaskForegroundInfo(
        context = context,
        notificationId = BackgroundTaskNotificationIds.BANGUMI_ARCHIVE_WORKER,
        title = "Bangumi Archive 更新",
        text = text,
    )

private fun backgroundTaskForegroundInfo(
    context: Context,
    notificationId: Int,
    title: String,
    text: String,
): ForegroundInfo =
    ForegroundInfo(
        notificationId,
        BackgroundTaskNotifications.build(
            context = context,
            title = title,
            text = text,
            progress = BackgroundTaskProgress.indeterminate(),
        ),
        BackgroundTaskNotifications.dataSyncForegroundServiceType(),
    )
