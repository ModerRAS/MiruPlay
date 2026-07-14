package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveRssRunSummary

open class SharedCloudDriveRssAutomationEngine(
    private val core: CloudDriveRssAutomationCore,
) : CloudDriveRssAutomationRunner {
    override suspend fun login(endpointUrl: String, username: String, password: String): Result<Unit> =
        core.login(endpointUrl, username, password)

    override suspend fun saveApiToken(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> =
        core.saveApiToken(endpointUrl, token)

    override suspend fun runOnce(): Result<CloudDriveRssRunSummary> =
        core.runOnce()

    suspend fun runIfDue(): Result<CloudDriveRssRunSummary?> =
        core.runIfDue()
}
