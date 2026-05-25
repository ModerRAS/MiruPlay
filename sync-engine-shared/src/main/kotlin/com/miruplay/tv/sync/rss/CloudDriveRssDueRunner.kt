package com.miruplay.tv.sync.rss

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveRssRunSummary

fun interface CloudDriveRssDueRunner {
    suspend fun runIfDue(): Result<CloudDriveRssRunSummary?>
}
