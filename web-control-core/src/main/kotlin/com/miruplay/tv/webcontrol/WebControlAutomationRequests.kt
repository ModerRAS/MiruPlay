package com.miruplay.tv.webcontrol

import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.buildRssSubscriptionFromForm
import com.miruplay.tv.model.withAutomationFormValues
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationRunner
import kotlinx.coroutines.flow.first

fun CloudDriveConfigRequest.toAutomationConfig(current: CloudDriveAutomationConfig): CloudDriveAutomationConfig =
    current.withAutomationFormValues(
        endpointUrl = endpointUrl,
        username = username,
        webDavSourceId = webDavSourceId?.takeIf { it > 0L },
        inboxPath = inboxPath,
        libraryPath = libraryPath,
        intervalMinutes = intervalMinutes,
        enabled = enabled,
        rssProxyEnabled = rssProxyEnabled,
        rssProxyHost = rssProxyHost,
        rssProxyPort = rssProxyPort,
    )

fun RssSubscriptionRequest.toSubscription(existingLastCheckedAt: Long = 0L): RssSubscriptionInfo? =
    buildRssSubscriptionFromForm(
        name = name,
        url = url,
        filterRegex = filterRegex.orEmpty(),
        enabled = enabled,
        existingId = id,
        existingLastCheckedAt = existingLastCheckedAt,
    )

fun RssSubscriptionInfo.withSavedId(savedId: Long): RssSubscriptionInfo =
    copy(id = if (id > 0L) id else savedId)

suspend fun CloudDriveAutomationRepository.saveWebControlRssSubscription(
    request: RssSubscriptionRequest,
): RssSubscriptionInfo {
    val subscription = request.toSubscription() ?: throw IllegalArgumentException("请填写 RSS 地址")
    val id = requireWebControlSuccess(saveSubscription(subscription), "保存 RSS 订阅失败")
    return subscription.withSavedId(id)
}

suspend fun CloudDriveAutomationRepository.updateWebControlRssSubscription(
    id: Long,
    request: RssSubscriptionRequest,
): RssSubscriptionInfo =
    saveWebControlRssSubscription(request.copy(id = id))

suspend fun CloudDriveAutomationRepository.deleteWebControlRssSubscription(id: Long) {
    requireWebControlSuccess(deleteSubscription(id), "删除 RSS 订阅失败")
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

suspend fun CloudDriveAutomationRepository.saveWebControlCloudDriveConfig(
    request: CloudDriveConfigRequest,
    credentials: CloudDriveCredentialStore,
): CloudDriveAutomationDto {
    val current = requireWebControlSuccess(getConfig(), "读取 CloudDrive 设置失败")
    val config = request.toAutomationConfig(current)
    requireWebControlSuccess(saveConfig(config), "保存 CloudDrive 设置失败")
    return getWebControlCloudDriveAutomation(credentials)
}

suspend fun CloudDriveRssAutomationRunner.loginWebControlCloudDrive(
    request: CloudDriveLoginRequest,
    repository: CloudDriveAutomationRepository,
    credentials: CloudDriveCredentialStore,
): CloudDriveAutomationDto {
    val validatedLogin = request.validated()
    requireWebControlSuccess(
        login(validatedLogin.endpointUrl, validatedLogin.username, validatedLogin.password),
        "CloudDrive2 登录失败",
    )
    return repository.getWebControlCloudDriveAutomation(credentials)
}

suspend fun CloudDriveRssAutomationRunner.saveWebControlCloudDriveToken(
    request: CloudDriveTokenRequest,
): CloudDriveTokenResponse {
    val tokenRequest = request.validated()
    val tokenInfo = requireWebControlSuccess(
        saveApiToken(tokenRequest.endpointUrl, tokenRequest.token),
        "CloudDrive2 API Token 验证失败",
    )
    return tokenInfo.toWebControlResponse()
}

suspend fun CloudDriveRssAutomationRunner.runWebControlCloudDriveAutomationNow(
    afterRun: suspend (CloudDriveRssRunSummary) -> Unit = {},
): CloudDriveRunResponse {
    val summary = requireWebControlSuccess(runOnce(), "CloudDrive/RSS 执行失败")
    afterRun(summary)
    return summary.toWebControlResponse()
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

fun CloudDriveLoginRequest.validated(): CloudDriveLoginRequest {
    val endpoint = endpointUrl.trim()
    val user = username.trim()
    if (endpoint.isBlank() || user.isBlank() || password.isBlank()) {
        throw IllegalArgumentException("请填写 CloudDrive2 地址、用户名和密码")
    }
    return copy(endpointUrl = endpoint, username = user)
}

fun CloudDriveTokenRequest.validated(): CloudDriveTokenRequest {
    val endpoint = endpointUrl.trim()
    val apiToken = token.trim()
    if (endpoint.isBlank() || apiToken.isBlank()) {
        throw IllegalArgumentException("请填写 CloudDrive2 地址和 API Token")
    }
    return copy(endpointUrl = endpoint, token = apiToken)
}

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
