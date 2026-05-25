package com.miruplay.tv.sync.rss

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveRssRunSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun CoroutineScope.launchCloudDriveRssSchedulerLoop(
    dueRunner: CloudDriveRssDueRunner,
    checkIntervalMillis: Long,
    onCheckCompleted: (checkedAtMillis: Long, result: Result<CloudDriveRssRunSummary?>) -> Unit = { _, _ -> },
): Job {
    require(checkIntervalMillis > 0L) { "checkIntervalMillis must be > 0" }
    return launch {
        while (isActive) {
            val result = dueRunner.runIfDue()
            onCheckCompleted(System.currentTimeMillis(), result)
            delay(checkIntervalMillis)
        }
    }
}
