package com.miruplay.tv.webcontrol

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.buildRssSubscriptionFromForm
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import com.miruplay.tv.sync.rss.CloudDriveActionResult
import com.miruplay.tv.sync.rss.CloudDriveConfigActionResult
import com.miruplay.tv.sync.rss.CloudDriveRssActionCoordinator
import com.miruplay.tv.sync.rss.CloudDriveRunActionResult
import com.miruplay.tv.sync.rss.RssSubscriptionActionResult
import kotlinx.coroutines.flow.first

fun RssSubscriptionRequest.toSubscription(existingLastCheckedAt: Long = 0L): RssSubscriptionInfo? =
    buildRssSubscriptionFromForm(
        name = name,
        url = url,
        filterRegex = filterRegex.orEmpty(),
        enabled = enabled,
        existingId = id,
        existingLastCheckedAt = existingLastCheckedAt,
    )

suspend fun CloudDriveRssActionCoordinator.saveWebControlRssSubscription(
    request: RssSubscriptionRequest,
): RssSubscriptionInfo {
    return when (
        val result = saveRssSubscription(
            name = request.name,
            url = request.url,
            filterRegex = request.filterRegex.orEmpty(),
            enabled = request.enabled,
            selectedSubscription = null,
        )
    ) {
        is RssSubscriptionActionResult.Saved -> result.subscription
        is RssSubscriptionActionResult.Invalid -> throw IllegalArgumentException(result.status)
        is RssSubscriptionActionResult.Failed -> throw IllegalStateException("保存 RSS 订阅失败: ${result.status}")
        is RssSubscriptionActionResult.Deleted -> error("Unexpected RSS delete result while saving")
    }
}

suspend fun CloudDriveRssActionCoordinator.updateWebControlRssSubscription(
    id: Long,
    request: RssSubscriptionRequest,
    repository: CloudDriveAutomationRepository,
): RssSubscriptionInfo {
    val selected = repository.observeSubscriptions().first().firstOrNull { it.id == id }
        ?: request.copy(id = id).toSubscription()
        ?: RssSubscriptionInfo(id = id, name = request.name, url = request.url)
    return when (
        val result = saveRssSubscription(
            name = request.name,
            url = request.url,
            filterRegex = request.filterRegex.orEmpty(),
            enabled = request.enabled,
            selectedSubscription = selected,
        )
    ) {
        is RssSubscriptionActionResult.Saved -> result.subscription
        is RssSubscriptionActionResult.Invalid -> throw IllegalArgumentException(result.status)
        is RssSubscriptionActionResult.Failed -> throw IllegalStateException("保存 RSS 订阅失败: ${result.status}")
        is RssSubscriptionActionResult.Deleted -> error("Unexpected RSS delete result while updating")
    }
}

suspend fun CloudDriveRssActionCoordinator.deleteWebControlRssSubscription(id: Long) {
    when (val result = deleteRssSubscription(id)) {
        is RssSubscriptionActionResult.Deleted -> Unit
        is RssSubscriptionActionResult.Failed -> throw IllegalStateException("删除 RSS 订阅失败: ${result.status}")
        is RssSubscriptionActionResult.Invalid -> throw IllegalArgumentException(result.status)
        is RssSubscriptionActionResult.Saved -> error("Unexpected RSS save result while deleting")
    }
}

suspend fun CloudDriveAutomationRepository.getWebControlCloudDriveAutomation(
    credentials: CloudDriveCredentialStore,
): CloudDriveAutomationDto {
    val config = requireWebControlSuccess(getConfig(), "读取 CloudDrive 设置失败")
    return config.toWebControlAutomationDto(
        subscriptions = observeSubscriptions().first(),
        tokenConfigured = !credentials.cloudDriveToken.isNullOrBlank(),
    )
}

suspend fun CloudDriveRssActionCoordinator.saveWebControlCloudDriveConfig(
    request: CloudDriveConfigRequest,
    repository: CloudDriveAutomationRepository,
    credentials: CloudDriveCredentialStore,
): CloudDriveAutomationDto {
    return when (
        val result = saveConfig(
            endpointUrl = request.endpointUrl,
            username = request.username,
            webDavSourceId = request.webDavSourceId?.takeIf { it > 0L },
            inboxPath = request.inboxPath,
            libraryPath = request.libraryPath,
            intervalMinutes = request.intervalMinutes,
            enabled = request.enabled,
            rssProxyEnabled = request.rssProxyEnabled,
            rssProxyHost = request.rssProxyHost,
            rssProxyPort = request.rssProxyPort,
        )
    ) {
        is CloudDriveConfigActionResult.Saved -> repository.getWebControlCloudDriveAutomation(credentials)
        is CloudDriveConfigActionResult.Failed -> throw IllegalStateException("保存 CloudDrive 设置失败: ${result.status}")
    }
}

suspend fun CloudDriveRssActionCoordinator.loginWebControlCloudDrive(
    request: CloudDriveLoginRequest,
    repository: CloudDriveAutomationRepository,
    credentials: CloudDriveCredentialStore,
): CloudDriveAutomationDto {
    return when (
        val result = loginCloudDrive(
            endpointUrl = request.endpointUrl,
            username = request.username,
            password = request.password,
        )
    ) {
        is CloudDriveActionResult.Success -> repository.getWebControlCloudDriveAutomation(credentials)
        is CloudDriveActionResult.Invalid -> throw IllegalArgumentException(result.status)
        is CloudDriveActionResult.Failed -> throw IllegalStateException("CloudDrive2 登录失败: ${result.status}")
    }
}

suspend fun CloudDriveRssActionCoordinator.saveWebControlCloudDriveToken(
    request: CloudDriveTokenRequest,
): CloudDriveTokenResponse {
    return when (
        val result = verifyCloudDriveApiToken(
            endpointUrl = request.endpointUrl,
            token = request.token,
        )
    ) {
        is CloudDriveActionResult.Success -> requireNotNull(result.tokenInfo).toWebControlResponse()
        is CloudDriveActionResult.Invalid -> throw IllegalArgumentException(result.status)
        is CloudDriveActionResult.Failed -> throw IllegalStateException("CloudDrive2 API Token 验证失败: ${result.status}")
    }
}

suspend fun CloudDriveRssActionCoordinator.runWebControlCloudDriveAutomationNow(
    afterRun: suspend (CloudDriveRssRunSummary) -> Unit = {},
): CloudDriveRunResponse {
    return when (val result = runCloudDriveOnce()) {
        is CloudDriveRunActionResult.Completed -> {
            afterRun(result.summary)
            result.summary.toWebControlResponse()
        }
        is CloudDriveRunActionResult.Failed -> throw IllegalStateException("CloudDrive/RSS 执行失败: ${result.status}")
    }
}

fun CloudDriveAutomationConfig.toWebControlAutomationDto(
    subscriptions: List<RssSubscriptionInfo>,
    tokenConfigured: Boolean,
): CloudDriveAutomationDto =
    CloudDriveAutomationDto(
        config = this,
        subscriptions = subscriptions,
        tokenConfigured = tokenConfigured,
    )

fun CloudDriveTokenInfo.toWebControlResponse(): CloudDriveTokenResponse =
    CloudDriveTokenResponse(
        rootDir = rootDir,
        friendlyName = friendlyName,
        allowList = allowList,
        allowCreateFolder = allowCreateFolder,
        allowCreateFile = allowCreateFile,
        allowWrite = allowWrite,
        allowMove = allowMove,
        allowAddOfflineDownload = allowAddOfflineDownload,
    )

fun CloudDriveRssRunSummary.toWebControlResponse(): CloudDriveRunResponse =
    CloudDriveRunResponse(
        submitted = submitted,
        skipped = skipped,
        failed = failed,
        organized = organized,
    )
