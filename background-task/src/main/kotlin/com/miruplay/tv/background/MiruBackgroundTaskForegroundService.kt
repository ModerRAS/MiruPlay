package com.miruplay.tv.background

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class MiruBackgroundTaskForegroundService : Service() {
    private val activeTasks = linkedMapOf<String, TaskSpec>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START,
            ACTION_UPDATE -> upsertTask(intent)
            ACTION_FINISH -> finishTask(intent.getStringExtra(EXTRA_TASK_ID).orEmpty())
        }
        return START_NOT_STICKY
    }

    private fun upsertTask(intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)?.takeIf { it.isNotBlank() } ?: return
        val spec = TaskSpec(
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            progress = intent.progress(),
        )
        activeTasks[taskId] = spec
        publish(spec)
    }

    private fun finishTask(taskId: String) {
        activeTasks.remove(taskId)
        val next = activeTasks.values.lastOrNull()
        if (next == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            publish(next)
        }
    }

    private fun publish(spec: TaskSpec) {
        val notification = BackgroundTaskNotifications.build(
            context = this,
            title = spec.title,
            text = spec.text,
            progress = spec.progress,
        )
        BackgroundTaskNotifications.startForeground(this, notification)
        getSystemService(NotificationManager::class.java).notify(
            BackgroundTaskNotificationIds.FOREGROUND_SERVICE,
            notification,
        )
    }

    private fun Intent.progress(): BackgroundTaskProgress? {
        if (!hasExtra(EXTRA_PROGRESS_INDETERMINATE)) return null
        return BackgroundTaskProgress(
            current = getIntExtra(EXTRA_PROGRESS_CURRENT, 0),
            max = getIntExtra(EXTRA_PROGRESS_MAX, 0),
            indeterminate = getBooleanExtra(EXTRA_PROGRESS_INDETERMINATE, true),
        )
    }

    private data class TaskSpec(
        val title: String,
        val text: String,
        val progress: BackgroundTaskProgress?,
    )

    companion object {
        private const val ACTION_START = "com.miruplay.tv.background.action.START"
        private const val ACTION_UPDATE = "com.miruplay.tv.background.action.UPDATE"
        private const val ACTION_FINISH = "com.miruplay.tv.background.action.FINISH"
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_PROGRESS_CURRENT = "progress_current"
        private const val EXTRA_PROGRESS_MAX = "progress_max"
        private const val EXTRA_PROGRESS_INDETERMINATE = "progress_indeterminate"

        fun startIntent(
            context: Context,
            taskId: String,
            title: String,
            text: String,
            progress: BackgroundTaskProgress?,
        ): Intent = taskIntent(context, ACTION_START, taskId, title, text, progress)

        fun updateIntent(
            context: Context,
            taskId: String,
            title: String,
            text: String,
            progress: BackgroundTaskProgress?,
        ): Intent = taskIntent(context, ACTION_UPDATE, taskId, title, text, progress)

        fun finishIntent(context: Context, taskId: String): Intent =
            Intent(context, MiruBackgroundTaskForegroundService::class.java)
                .setAction(ACTION_FINISH)
                .putExtra(EXTRA_TASK_ID, taskId)

        private fun taskIntent(
            context: Context,
            action: String,
            taskId: String,
            title: String,
            text: String,
            progress: BackgroundTaskProgress?,
        ): Intent =
            Intent(context, MiruBackgroundTaskForegroundService::class.java)
                .setAction(action)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
                .apply {
                    progress?.let {
                        putExtra(EXTRA_PROGRESS_CURRENT, it.current)
                        putExtra(EXTRA_PROGRESS_MAX, it.max)
                        putExtra(EXTRA_PROGRESS_INDETERMINATE, it.indeterminate)
                    }
                }
    }
}
