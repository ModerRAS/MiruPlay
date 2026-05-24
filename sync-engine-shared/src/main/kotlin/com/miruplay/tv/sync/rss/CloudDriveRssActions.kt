package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveApiTokenFormResult
import com.miruplay.tv.model.CloudDriveLoginFormResult
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.cloudDriveApiTokenRequiredStatus
import com.miruplay.tv.model.cloudDriveLoginStartedStatus
import com.miruplay.tv.model.cloudDriveLoginSucceededStatus
import com.miruplay.tv.model.cloudDriveTokenValidationStartedStatus
import com.miruplay.tv.model.cloudDriveTokenVerifiedStatus
import com.miruplay.tv.model.validateCloudDriveApiTokenForm
import com.miruplay.tv.model.validateCloudDriveLoginForm
import com.miruplay.tv.model.withAutomationFormValues
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore

interface CloudDriveRssAutomationRunner {
    suspend fun login(endpointUrl: String, username: String, password: String): Result<Unit>
    suspend fun saveApiToken(endpointUrl: String, token: String): Result<CloudDriveTokenInfo>
    suspend fun runOnce(): Result<CloudDriveRssRunSummary>
}

sealed class CloudDriveActionResult {
    data class Invalid(val status: String) : CloudDriveActionResult()
    data class Success(val status: String, val token: String? = null) : CloudDriveActionResult()
    data class Failed(val status: String) : CloudDriveActionResult()
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

    suspend fun loginCloudDrive(
        endpointUrl: String,
        username: String,
        password: String,
        onStarted: (String) -> Unit = {},
    ): CloudDriveActionResult {
        val form = when (val validation = validateCloudDriveLoginForm(endpointUrl, username, password)) {
            is CloudDriveLoginFormResult.Ready -> validation.request
            is CloudDriveLoginFormResult.Invalid -> {
                return CloudDriveActionResult.Invalid(validation.status)
            }
        }

        onStarted(cloudDriveLoginStartedStatus())
        return when (val result = login(form.endpointUrl, form.username, form.password)) {
            is Result.Success -> CloudDriveActionResult.Success(
                status = cloudDriveLoginSucceededStatus(),
                token = credentials.cloudDriveToken,
            )
            is Result.Error -> CloudDriveActionResult.Failed(result.error.toUserMessage())
        }
    }

    suspend fun verifyCloudDriveApiToken(
        endpointUrl: String,
        token: String,
        blankTokenStatus: String = cloudDriveApiTokenRequiredStatus(),
        onStarted: (String) -> Unit = {},
    ): CloudDriveActionResult {
        val form = when (
            val validation = validateCloudDriveApiTokenForm(
                endpointUrl = endpointUrl,
                token = token,
                blankTokenStatus = blankTokenStatus,
            )
        ) {
            is CloudDriveApiTokenFormResult.Ready -> validation.request
            is CloudDriveApiTokenFormResult.Invalid -> {
                return CloudDriveActionResult.Invalid(validation.status)
            }
        }

        onStarted(cloudDriveTokenValidationStartedStatus())
        return when (val result = verifyApiToken(form.endpointUrl, form.token)) {
            is Result.Success -> CloudDriveActionResult.Success(
                status = cloudDriveTokenVerifiedStatus(
                    friendlyName = result.data.friendlyName,
                    rootDir = result.data.rootDir,
                ),
                token = form.token,
            )
            is Result.Error -> CloudDriveActionResult.Failed(result.error.toUserMessage())
        }
    }

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
