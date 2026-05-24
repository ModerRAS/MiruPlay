package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.withAutomationFormValues
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore

interface CloudDriveRssAutomationRunner {
    suspend fun login(endpointUrl: String, username: String, password: String): Result<Unit>
    suspend fun saveApiToken(endpointUrl: String, token: String): Result<CloudDriveTokenInfo>
    suspend fun runOnce(): Result<CloudDriveRssRunSummary>
}

class CloudDriveRssActionCoordinator(
    private val repository: CloudDriveAutomationRepository,
    private val credentials: CloudDriveCredentialStore,
    private val runner: CloudDriveRssAutomationRunner,
) {
    suspend fun saveConfig(
        endpointUrl: String,
        username: String,
        webDavSourceId: Long?,
        inboxPath: String,
        libraryPath: String,
        intervalMinutes: Int,
        enabled: Boolean,
        rssProxyEnabled: Boolean = false,
        rssProxyHost: String = "",
        rssProxyPort: Int = 1080,
    ): Result<CloudDriveAutomationConfig> {
        val current = when (val configResult = repository.getConfig()) {
            is Result.Success -> configResult.data
            is Result.Error -> return configResult
        }
        val config = current.withAutomationFormValues(
            endpointUrl = endpointUrl,
            username = username,
            webDavSourceId = webDavSourceId,
            inboxPath = inboxPath,
            libraryPath = libraryPath,
            intervalMinutes = intervalMinutes,
            enabled = enabled,
            rssProxyEnabled = rssProxyEnabled,
            rssProxyHost = rssProxyHost,
            rssProxyPort = rssProxyPort,
        )
        return when (val saveResult = repository.saveConfig(config)) {
            is Result.Success -> Result.success(config)
            is Result.Error -> saveResult
        }
    }

    fun saveCredentials(token: String?, password: String?) {
        credentials.cloudDriveToken = token?.trim()?.takeIf { it.isNotBlank() }
        credentials.cloudDrivePassword = password?.takeIf { it.isNotBlank() }
    }

    fun clearCredentials() {
        credentials.clearCloudDriveCredentials()
    }

    suspend fun login(endpointUrl: String, username: String, password: String): Result<Unit> =
        runner.login(endpointUrl, username, password)

    suspend fun verifyApiToken(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> =
        runner.saveApiToken(endpointUrl, token)

    suspend fun runOnce(): Result<CloudDriveRssRunSummary> =
        runner.runOnce()

    suspend fun saveSubscription(subscription: RssSubscriptionInfo): Result<Unit> =
        when (val result = repository.saveSubscription(subscription)) {
            is Result.Success -> Result.success(Unit)
            is Result.Error -> result
        }

    suspend fun deleteSubscription(id: Long): Result<Unit> =
        repository.deleteSubscription(id)
}
