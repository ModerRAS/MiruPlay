package com.miruplay.tv.data.logging

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import com.miruplay.tv.core.common.logging.MiruLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

@Singleton
class AppCrashDiagnostics @Inject constructor(
    @ApplicationContext context: Context,
    private val localLogStore: LocalLogStore
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val installed = AtomicBoolean(false)
    private val handlingCrash = AtomicBoolean(false)
    private var previousExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private var startedActivityCount = 0
    private var resumedActivityCount = 0

    val activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                recordActivityLifecycle(
                    activity = activity,
                    state = "activity_created",
                    isImportant = true,
                    attributes = mapOf(
                        "has_saved_state" to (savedInstanceState != null).toString(),
                        "intent_action" to activity.intent?.action.orEmpty(),
                        "intent_data_scheme" to activity.intent?.data?.scheme.orEmpty(),
                        "intent_data_host" to activity.intent?.data?.host.orEmpty(),
                    )
                )
            }

            override fun onActivityStarted(activity: Activity) {
                val wasBackground = startedActivityCount == 0
                startedActivityCount += 1
                recordActivityLifecycle(activity, "activity_started")
                if (wasBackground) {
                    recordState(
                        state = "application_foreground",
                        activity = activity,
                        message = "Application entered foreground",
                        isImportant = true
                    )
                }
            }

            override fun onActivityResumed(activity: Activity) {
                resumedActivityCount += 1
                recordActivityLifecycle(activity, "activity_resumed", isImportant = true)
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivityCount = (resumedActivityCount - 1).coerceAtLeast(0)
                recordActivityLifecycle(activity, "activity_paused")
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                recordActivityLifecycle(activity, "activity_stopped")
                if (startedActivityCount == 0) {
                    recordState(
                        state = "application_background",
                        activity = activity,
                        message = "Application entered background",
                        isImportant = true
                    )
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                recordActivityLifecycle(activity, "activity_save_instance_state")
            }

            override fun onActivityDestroyed(activity: Activity) {
                recordActivityLifecycle(activity, "activity_destroyed")
            }
        }

    fun install(
        previousHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
    ) {
        if (!installed.compareAndSet(false, true)) return

        MiruLog.setSink(localLogStore)
        reportPreviousSessionIfNeeded()
        beginSession()

        previousExceptionHandler = previousHandler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
        }
        MiruLog.i(
            tag = TAG,
            message = "Crash diagnostics installed",
            attributes = sessionAttributes() + runtimeAttributes()
        )
    }

    fun markStartupCheckpoint(checkpoint: String, attributes: Map<String, String> = emptyMap()) {
        recordState(
            state = checkpoint,
            message = "Startup checkpoint: $checkpoint",
            isImportant = true,
            attributes = attributes
        )
    }

    fun markCleanShutdown(reason: String) {
        preferences.edit()
            .putBoolean(KEY_ACTIVE, false)
            .putBoolean(KEY_CLEAN_SHUTDOWN, true)
            .putString(KEY_LAST_STATE, reason)
            .putLong(KEY_LAST_HEARTBEAT_AT, System.currentTimeMillis())
            .commit()
        MiruLog.i(
            tag = TAG,
            message = "Application session ended cleanly",
            attributes = sessionAttributes() + mapOf("shutdown_reason" to reason)
        )
    }

    fun sessionAttributes(): Map<String, String> =
        mapOf(
            "session_id" to preferences.getString(KEY_SESSION_ID, "").orEmpty(),
            "process_id" to Process.myPid().toString(),
            "process_uptime_ms" to SystemClock.elapsedRealtime().toString(),
            "last_lifecycle_state" to preferences.getString(KEY_LAST_STATE, "").orEmpty(),
        )

    private fun beginSession() {
        val now = System.currentTimeMillis()
        preferences.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putBoolean(KEY_CLEAN_SHUTDOWN, false)
            .putBoolean(KEY_CRASH_RECORDED, false)
            .putString(KEY_SESSION_ID, "${now}-${UUID.randomUUID()}")
            .putLong(KEY_SESSION_STARTED_AT, now)
            .putLong(KEY_LAST_HEARTBEAT_AT, now)
            .putString(KEY_LAST_STATE, "application_on_create")
            .putString(KEY_LAST_ACTIVITY, "")
            .commit()
    }

    private fun reportPreviousSessionIfNeeded() {
        if (!preferences.getBoolean(KEY_ACTIVE, false)) return

        val crashRecorded = preferences.getBoolean(KEY_CRASH_RECORDED, false)
        val message = if (crashRecorded) {
            "Previous app session crashed before restart"
        } else {
            "Previous app session ended without a clean shutdown callback"
        }
        MiruLog.w(
            tag = TAG,
            message = message,
            attributes = previousSessionAttributes(crashRecorded)
        )
    }

    private fun previousSessionAttributes(crashRecorded: Boolean): Map<String, String> {
        val startedAt = preferences.getLong(KEY_SESSION_STARTED_AT, 0L)
        val heartbeatAt = preferences.getLong(KEY_LAST_HEARTBEAT_AT, 0L)
        val now = System.currentTimeMillis()
        return mapOf(
            "previous_session_id" to preferences.getString(KEY_SESSION_ID, "").orEmpty(),
            "previous_started_at_ms" to startedAt.toString(),
            "previous_last_heartbeat_at_ms" to heartbeatAt.toString(),
            "previous_last_heartbeat_age_ms" to if (heartbeatAt > 0L) (now - heartbeatAt).toString() else "",
            "previous_last_state" to preferences.getString(KEY_LAST_STATE, "").orEmpty(),
            "previous_last_activity" to preferences.getString(KEY_LAST_ACTIVITY, "").orEmpty(),
            "previous_clean_shutdown" to preferences.getBoolean(KEY_CLEAN_SHUTDOWN, false).toString(),
            "previous_crash_recorded" to crashRecorded.toString(),
            "previous_crash_at_ms" to preferences.getLong(KEY_LAST_CRASH_AT, 0L).toString(),
        ) + historicalExitReasonAttributes()
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        if (handlingCrash.compareAndSet(false, true)) {
            runCatching {
                val now = System.currentTimeMillis()
                preferences.edit()
                    .putBoolean(KEY_ACTIVE, true)
                    .putBoolean(KEY_CLEAN_SHUTDOWN, false)
                    .putBoolean(KEY_CRASH_RECORDED, true)
                    .putLong(KEY_LAST_CRASH_AT, now)
                    .putLong(KEY_LAST_HEARTBEAT_AT, now)
                    .putString(KEY_LAST_STATE, "uncaught_exception")
                    .commit()

                MiruLog.e(
                    tag = TAG,
                    message = "Unhandled exception crashed the app",
                    throwable = throwable,
                    attributes = sessionAttributes() + runtimeAttributes() + mapOf(
                        "thread_name" to thread.name,
                        "thread_id" to thread.id.toString(),
                        "thread_state" to thread.state.name,
                        "thread_priority" to thread.priority.toString(),
                        "is_main_thread" to (thread.name == "main").toString(),
                    )
                )
            }
        }
        previousExceptionHandler?.uncaughtException(thread, throwable)
            ?: run {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
    }

    private fun recordActivityLifecycle(
        activity: Activity,
        state: String,
        isImportant: Boolean = false,
        attributes: Map<String, String> = emptyMap()
    ) {
        recordState(
            state = state,
            activity = activity,
            message = "Activity lifecycle: $state",
            isImportant = isImportant,
            attributes = attributes
        )
    }

    private fun recordState(
        state: String,
        activity: Activity? = null,
        message: String,
        isImportant: Boolean = false,
        attributes: Map<String, String> = emptyMap()
    ) {
        val now = System.currentTimeMillis()
        val activityName = activity?.javaClass?.name.orEmpty()
        preferences.edit()
            .putLong(KEY_LAST_HEARTBEAT_AT, now)
            .putString(KEY_LAST_STATE, state)
            .putString(KEY_LAST_ACTIVITY, activityName)
            .apply()

        val logAttributes = sessionAttributes() + mapOf(
            "lifecycle_state" to state,
            "activity" to activityName,
            "started_activity_count" to startedActivityCount.toString(),
            "resumed_activity_count" to resumedActivityCount.toString(),
        ) + attributes.filterValues { it.isNotBlank() }
        if (isImportant) {
            MiruLog.i(TAG, message, logAttributes)
        } else {
            MiruLog.d(TAG, message, logAttributes)
        }
    }

    private fun runtimeAttributes(): Map<String, String> {
        val packageInfo = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }.getOrNull()
        return mapOf(
            "package_name" to appContext.packageName,
            "version_name" to packageInfo?.versionName.orEmpty(),
            "version_code" to (packageInfo?.longVersionCode ?: 0L).toString(),
            "android_sdk" to Build.VERSION.SDK_INT.toString(),
            "android_release" to Build.VERSION.RELEASE.orEmpty(),
            "device_manufacturer" to Build.MANUFACTURER.orEmpty(),
            "device_model" to Build.MODEL.orEmpty(),
            "device_product" to Build.PRODUCT.orEmpty(),
            "available_processors" to Runtime.getRuntime().availableProcessors().toString(),
            "max_memory_bytes" to Runtime.getRuntime().maxMemory().toString(),
        )
    }

    private fun historicalExitReasonAttributes(): Map<String, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return mapOf("historical_exit_reason_available" to "false")
        }
        val activityManager = appContext.getSystemService(ActivityManager::class.java)
            ?: return mapOf(
                "historical_exit_reason_available" to "true",
                "historical_exit_reason_present" to "false",
            )
        val exitInfo = runCatching {
            activityManager.getHistoricalProcessExitReasons(appContext.packageName, 0, 5).firstOrNull()
        }.getOrNull()
            ?: return mapOf(
                "historical_exit_reason_available" to "true",
                "historical_exit_reason_present" to "false",
            )

        return mapOf(
            "historical_exit_reason_available" to "true",
            "historical_exit_reason_present" to "true",
            "historical_exit_reason" to exitInfo.reason.toString(),
            "historical_exit_reason_label" to exitInfo.reason.label(),
            "historical_exit_status" to exitInfo.status.toString(),
            "historical_exit_importance" to exitInfo.importance.toString(),
            "historical_exit_timestamp_ms" to exitInfo.timestamp.toString(),
            "historical_exit_pid" to exitInfo.pid.toString(),
            "historical_exit_process_name" to exitInfo.processName.orEmpty(),
            "historical_exit_pss_kb" to exitInfo.pss.toString(),
            "historical_exit_rss_kb" to exitInfo.rss.toString(),
            "historical_exit_description" to exitInfo.description.orEmpty(),
        )
    }

    private fun Int.label(): String = when (this) {
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource_usage"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit_self"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
        ApplicationExitInfo.REASON_OTHER -> "other"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission_change"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_UNKNOWN -> "unknown"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
        else -> "reason_$this"
    }

    companion object {
        private const val TAG = "AppCrashDiagnostics"
        private const val PREFERENCES_NAME = "miruplay_crash_diagnostics"
        private const val KEY_ACTIVE = "active"
        private const val KEY_CLEAN_SHUTDOWN = "clean_shutdown"
        private const val KEY_CRASH_RECORDED = "crash_recorded"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_SESSION_STARTED_AT = "session_started_at"
        private const val KEY_LAST_HEARTBEAT_AT = "last_heartbeat_at"
        private const val KEY_LAST_STATE = "last_state"
        private const val KEY_LAST_ACTIVITY = "last_activity"
        private const val KEY_LAST_CRASH_AT = "last_crash_at"
    }
}
