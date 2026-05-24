package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveApiTokenFormResult
import com.miruplay.tv.model.CloudDriveLoginFormResult
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.RssSubscriptionFormResult
import com.miruplay.tv.model.cloudDriveApiTokenRequiredStatus
import com.miruplay.tv.model.cloudDriveCredentialsClearedStatus
import com.miruplay.tv.model.cloudDriveCredentialsSavedStatus
import com.miruplay.tv.model.cloudDriveLoginStartedStatus
import com.miruplay.tv.model.cloudDriveLoginSucceededStatus
import com.miruplay.tv.model.cloudDriveTokenValidationStartedStatus
import com.miruplay.tv.model.cloudDriveTokenVerifiedStatus
import com.miruplay.tv.model.cloudRssConfigSavedStatus
import com.miruplay.tv.model.cloudRssRunStartedStatus
import com.miruplay.tv.model.completeStatus
import com.miruplay.tv.model.prepareRssSubscriptionForm
import com.miruplay.tv.model.rssSubscriptionDeletedStatus
import com.miruplay.tv.model.rssSubscriptionSavedStatus
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
    data class Success(
        val status: String,
        val token: String? = null,
        val tokenInfo: CloudDriveTokenInfo? = null,
    ) : CloudDriveActionResult()
    data class Failed(val status: String) : CloudDriveActionResult()
}

sealed class CloudDriveConfigActionResult {
    data class Saved(val config: CloudDriveAutomationConfig, val status: String) : CloudDriveConfigActionResult()
    data class Failed(val status: String) : CloudDriveConfigActionResult()
}

sealed class CloudDriveCredentialActionResult {
    data class Saved(
        val token: String?,
        val password: String?,
        val status: String,
    ) : CloudDriveCredentialActionResult()

    data class Cleared(val status: String) : CloudDriveCredentialActionResult()
}

sealed class RssSubscriptionActionResult {
    data class Invalid(val status: String) : RssSubscriptionActionResult()
    data class Saved(val subscription: RssSubscriptionInfo, val status: String) : RssSubscriptionActionResult()
    data class Deleted(val status: String) : RssSubscriptionActionResult()
    data class Failed(val status: String) : RssSubscriptionActionResult()
}

sealed class CloudDriveRunActionResult {
    data class Completed(val summary: CloudDriveRssRunSummary, val status: String) : CloudDriveRunActionResult()
    data class Failed(val status: String) : CloudDriveRunActionResult()
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
    ): CloudDriveConfigActionResult {
        val current = when (val configResult = repository.getConfig()) {
            is Result.Success -> configResult.data
            is Result.Error -> return CloudDriveConfigActionResult.Failed(configResult.error.toUserMessage())
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
            is Result.Success -> CloudDriveConfigActionResult.Saved(
                config = config,
                status = cloudRssConfigSavedStatus(),
            )
            is Result.Error -> CloudDriveConfigActionResult.Failed(saveResult.error.toUserMessage())
        }
    }

    fun saveCredentials(token: String?, password: String?): CloudDriveCredentialActionResult.Saved {
        val normalizedToken = token?.trim()?.takeIf { it.isNotBlank() }
        val normalizedPassword = password?.takeIf { it.isNotBlank() }
        credentials.cloudDriveToken = normalizedToken
        credentials.cloudDrivePassword = normalizedPassword
        return CloudDriveCredentialActionResult.Saved(
            token = normalizedToken,
            password = normalizedPassword,
            status = cloudDriveCredentialsSavedStatus(),
        )
    }

    fun clearCredentials(): CloudDriveCredentialActionResult.Cleared {
        credentials.clearCloudDriveCredentials()
        return CloudDriveCredentialActionResult.Cleared(cloudDriveCredentialsClearedStatus())
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
                tokenInfo = result.data,
            )
            is Result.Error -> CloudDriveActionResult.Failed(result.error.toUserMessage())
        }
    }

    suspend fun runOnce(): Result<CloudDriveRssRunSummary> =
        runner.runOnce()

    suspend fun runCloudDriveOnce(
        onStarted: (String) -> Unit = {},
    ): CloudDriveRunActionResult {
        onStarted(cloudRssRunStartedStatus())
        return when (val result = runOnce()) {
            is Result.Success -> CloudDriveRunActionResult.Completed(
                summary = result.data,
                status = result.data.completeStatus(),
            )
            is Result.Error -> CloudDriveRunActionResult.Failed(result.error.toUserMessage())
        }
    }

    suspend fun saveSubscription(subscription: RssSubscriptionInfo): Result<Long> =
        when (val result = repository.saveSubscription(subscription)) {
            is Result.Success -> result
            is Result.Error -> result
        }

    suspend fun saveRssSubscription(
        name: String,
        url: String,
        filterRegex: String,
        enabled: Boolean,
        selectedSubscription: RssSubscriptionInfo? = null,
    ): RssSubscriptionActionResult {
        val subscription = when (
            val result = prepareRssSubscriptionForm(
                name = name,
                url = url,
                filterRegex = filterRegex,
                enabled = enabled,
                selectedSubscription = selectedSubscription,
            )
        ) {
            is RssSubscriptionFormResult.Ready -> result.subscription
            is RssSubscriptionFormResult.Invalid -> {
                return RssSubscriptionActionResult.Invalid(result.status)
            }
        }

        return when (val result = saveSubscription(subscription)) {
            is Result.Success -> {
                val savedSubscription = subscription.copy(
                    id = subscription.id.takeIf { it > 0L } ?: result.data,
                )
                RssSubscriptionActionResult.Saved(
                    subscription = savedSubscription,
                    status = rssSubscriptionSavedStatus(savedSubscription.name),
                )
            }
            is Result.Error -> RssSubscriptionActionResult.Failed(result.error.toUserMessage())
        }
    }

    suspend fun deleteSubscription(id: Long): Result<Unit> =
        repository.deleteSubscription(id)

    suspend fun deleteRssSubscription(id: Long): RssSubscriptionActionResult =
        when (val result = deleteSubscription(id)) {
            is Result.Success -> RssSubscriptionActionResult.Deleted(rssSubscriptionDeletedStatus())
            is Result.Error -> RssSubscriptionActionResult.Failed(result.error.toUserMessage())
        }
}
