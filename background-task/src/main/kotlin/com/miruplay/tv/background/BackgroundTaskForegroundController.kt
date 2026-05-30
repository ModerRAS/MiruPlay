package com.miruplay.tv.background

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundTaskForegroundController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun start(
        taskId: String,
        title: String,
        text: String,
        progress: BackgroundTaskProgress? = BackgroundTaskProgress.indeterminate(),
    ) {
        send(
            MiruBackgroundTaskForegroundService.startIntent(
                context = context,
                taskId = taskId,
                title = title,
                text = text,
                progress = progress,
            ),
            foreground = true,
        )
    }

    fun update(
        taskId: String,
        title: String,
        text: String,
        progress: BackgroundTaskProgress? = BackgroundTaskProgress.indeterminate(),
    ) {
        send(
            MiruBackgroundTaskForegroundService.updateIntent(
                context = context,
                taskId = taskId,
                title = title,
                text = text,
                progress = progress,
            ),
            foreground = false,
        )
    }

    fun finish(taskId: String) {
        send(
            MiruBackgroundTaskForegroundService.finishIntent(
                context = context,
                taskId = taskId,
            ),
            foreground = false,
        )
    }

    private fun send(intent: Intent, foreground: Boolean) {
        try {
            if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Unable to start background task foreground service", error)
        } catch (error: SecurityException) {
            Log.w(TAG, "Missing permission for background task foreground service", error)
        }
    }

    private companion object {
        private const val TAG = "BackgroundTaskForeground"
    }
}
