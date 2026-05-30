package com.miruplay.tv.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build

data class BackgroundTaskProgress(
    val current: Int,
    val max: Int,
    val indeterminate: Boolean,
) {
    companion object {
        fun indeterminate(): BackgroundTaskProgress = BackgroundTaskProgress(
            current = 0,
            max = 0,
            indeterminate = true,
        )

        fun determinate(current: Int, max: Int): BackgroundTaskProgress = BackgroundTaskProgress(
            current = current.coerceAtLeast(0),
            max = max.coerceAtLeast(0),
            indeterminate = false,
        )
    }
}

object BackgroundTaskIds {
    const val LIBRARY_SCAN = "library-scan"
    const val CLOUD_DRIVE_RSS = "cloud-drive-rss"
    const val BANGUMI_ARCHIVE = "bangumi-archive"
    const val APP_UPDATE = "app-update"
}

object BackgroundTaskNotificationIds {
    const val FOREGROUND_SERVICE = 4401
    const val CLOUD_DRIVE_RSS_WORKER = 4402
    const val BANGUMI_ARCHIVE_WORKER = 4403
}

object BackgroundTaskNotifications {
    private const val CHANNEL_ID = "miruplay_background_tasks"
    private const val CHANNEL_NAME = "后台任务"
    private const val CHANNEL_DESCRIPTION = "媒体库扫描、同步、刮削和下载进度"

    fun build(
        context: Context,
        title: String,
        text: String,
        progress: BackgroundTaskProgress? = BackgroundTaskProgress.indeterminate(),
        ongoing: Boolean = true,
    ): Notification {
        ensureChannel(context)
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)

        launchPendingIntent(context)?.let(builder::setContentIntent)
        progress?.let {
            builder.setProgress(it.max, it.current.coerceAtMost(it.max), it.indeterminate)
        }
        return builder.build()
    }

    fun dataSyncForegroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

    fun startForeground(service: Service, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(
                BackgroundTaskNotificationIds.FOREGROUND_SERVICE,
                notification,
                dataSyncForegroundServiceType(),
            )
        } else {
            service.startForeground(BackgroundTaskNotificationIds.FOREGROUND_SERVICE, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
        )
    }

    private fun launchPendingIntent(context: Context): PendingIntent? {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
