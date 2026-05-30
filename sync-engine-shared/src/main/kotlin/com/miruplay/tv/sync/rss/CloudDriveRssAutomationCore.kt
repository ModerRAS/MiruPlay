package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDrivePaths
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveLibraryMode
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.MIN_CLOUD_DRIVE_INTERVAL_MINUTES
import com.miruplay.tv.model.RssDownloadStatus
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CloudDriveRssAutomationEvents {
    fun onPrepareFailed(title: String, error: AppError) = Unit
    fun onSubmitFailed(title: String, error: AppError) = Unit

    object None : CloudDriveRssAutomationEvents
}

data class CloudDriveRssIngestionSummary(
    val indexed: Int = 0,
    val scraped: Int = 0,
    val noMatch: Int = 0,
)

class CloudDriveRssAutomationCore(
    private val repository: CloudDriveAutomationRepository,
    private val credentials: CloudDriveCredentialStore,
    private val cloudDriveClient: CloudDriveClient,
    private val feedFetcher: RssFeedReader = RssFeedFetcher(),
    private val organizer: CloudDriveLibraryOrganizer = CloudDriveLibraryOrganizer(cloudDriveClient),
    private val submissionPreparer: CloudDriveRssSubmissionPreparer = CloudDriveRssSubmissionPreparer(cloudDriveClient),
    private val events: CloudDriveRssAutomationEvents = CloudDriveRssAutomationEvents.None,
    private val afterIngested: suspend (CloudDriveAutomationConfig, CloudDriveEndpoint, Int) -> CloudDriveRssIngestionSummary =
        { _, _, _ -> CloudDriveRssIngestionSummary() },
) {
    suspend fun login(endpointUrl: String, username: String, password: String): Result<Unit> {
        val result = cloudDriveClient.login(endpointUrl, username, password)
        return result.map { login ->
            credentials.cloudDriveToken = login.token
            credentials.cloudDrivePassword = password
            Unit
        }
    }

    suspend fun saveApiToken(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> {
        val result = cloudDriveClient.getApiTokenInfo(endpointUrl, token)
        return result.map { info ->
            credentials.cloudDriveToken = token
            info
        }
    }

    suspend fun runOnce(): Result<CloudDriveRssRunSummary> = withContext(Dispatchers.IO) {
        val config = when (val configResult = repository.getConfig()) {
            is Result.Error -> return@withContext configResult
            is Result.Success -> configResult.data
        }
        if (config.endpointUrl.isBlank()) {
            return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "请先设置 CloudDrive2 地址"))
        }
        val inboxPath = CloudDrivePaths.normalizeScoped(config.inboxPath)
        val libraryPath = CloudDrivePaths.normalizeScoped(config.libraryPath)
        if (!CloudDrivePaths.isScopedDirectory(inboxPath)) {
            return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "请设置非根目录作为下载/入库目录"))
        }
        if (config.libraryMode == CloudDriveLibraryMode.ORGANIZED_LIBRARY) {
            if (!CloudDrivePaths.isScopedDirectory(libraryPath)) {
                return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "请设置非根目录作为整理目录 B"))
            }
            if (CloudDrivePaths.isSameOrChild(libraryPath, inboxPath)) {
                return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "整理目录 B 不能放在下载目录 A 内部"))
            }
        }

        feedFetcher.configureProxy(config.rssProxyEnabled, config.rssProxyHost, config.rssProxyPort)
        submissionPreparer.configureProxy(config.rssProxyEnabled, config.rssProxyHost, config.rssProxyPort)

        var endpoint = when (val endpointResult = resolveAuthenticatedEndpoint(config)) {
            is Result.Success -> endpointResult.data
            is Result.Error -> return@withContext endpointResult
        }
        val subscriptions = when (val subscriptionResult = repository.listEnabledSubscriptions()) {
            is Result.Error -> return@withContext subscriptionResult
            is Result.Success -> subscriptionResult.data
        }

        var submitted = 0
        var skipped = 0
        var failed = 0

        for (subscription in subscriptions) {
            val feedResult = feedFetcher.fetch(subscription.url)
            val items = feedResult.getOrNull()
            if (items == null) {
                failed += 1
                continue
            }

            val decisions = when (val planned = RssSubmissionPlanner.plan(items, subscription.filterRegex)) {
                is Result.Success -> planned.data
                is Result.Error -> {
                    failed += 1
                    continue
                }
            }
            for (decision in decisions) {
                if (decision.status == RssSubmissionDecisionStatus.SKIPPED_FILTER) {
                    skipped += 1
                    continue
                }
                val submissionUrl = decision.submissionUrl
                val itemKey = decision.itemKey
                if (decision.status == RssSubmissionDecisionStatus.MISSING_SUBMISSION ||
                    submissionUrl.isNullOrBlank() ||
                    itemKey.isNullOrBlank()
                ) {
                    failed += 1
                    continue
                }
                when (val processed = repository.isItemProcessed(subscription.id, itemKey)) {
                    is Result.Error -> return@withContext processed
                    is Result.Success -> if (processed.data) {
                        skipped += 1
                        continue
                    }
                }

                val preparedSubmission = submissionPreparer.prepare(endpoint, decision.item, itemKey, submissionUrl, inboxPath)
                if (preparedSubmission is Result.Error) {
                    failed += 1
                    events.onPrepareFailed(decision.item.title, preparedSubmission.error)
                    continue
                }

                val now = System.currentTimeMillis()
                when (
                    val submittedToCloudDrive = submitOfflineFilesWithRelogin(
                        config = config,
                        currentEndpoint = endpoint,
                        urls = listOf((preparedSubmission as Result.Success).data.submissionUrl),
                        targetFolder = inboxPath,
                        onEndpointRefreshed = { endpoint = it },
                    )
                ) {
                    is Result.Error -> {
                        failed += 1
                        events.onSubmitFailed(decision.item.title, submittedToCloudDrive.error)
                    }
                    is Result.Success -> {
                        when (
                            val processedSaved = repository.markItemProcessed(
                                RssProcessedItemInfo(
                                    subscriptionId = subscription.id,
                                    itemKey = itemKey,
                                    title = decision.item.title,
                                    url = submissionUrl,
                                    processedAt = now,
                                )
                            )
                        ) {
                            is Result.Error -> return@withContext processedSaved
                            is Result.Success -> Unit
                        }
                        when (
                            val taskSaved = repository.saveDownloadTask(
                                RssDownloadTaskInfo(
                                    subscriptionId = subscription.id,
                                    itemKey = itemKey,
                                    title = decision.item.title,
                                    url = submissionUrl,
                                    status = RssDownloadStatus.SUBMITTED,
                                    createdAt = now,
                                    updatedAt = now,
                                )
                            )
                        ) {
                            is Result.Error -> return@withContext taskSaved
                            is Result.Success -> submitted += 1
                        }
                    }
                }
            }
            when (val checked = repository.markSubscriptionChecked(subscription.id, System.currentTimeMillis())) {
                is Result.Error -> return@withContext checked
                is Result.Success -> Unit
            }
        }

        val organized = if (config.libraryMode == CloudDriveLibraryMode.ORGANIZED_LIBRARY) {
            when (val organizedResult = organizer.organize(endpoint, inboxPath, libraryPath)) {
                is Result.Error -> return@withContext organizedResult
                is Result.Success -> organizedResult.data
            }
        } else {
            0
        }
        when (val lastRunUpdated = repository.updateLastRunAt(System.currentTimeMillis())) {
            is Result.Error -> return@withContext lastRunUpdated
            is Result.Success -> Unit
        }
        val ingestion = afterIngested(config, endpoint, organized)

        Result.success(
            CloudDriveRssRunSummary(
                submitted = submitted,
                skipped = skipped,
                failed = failed,
                organized = organized,
                indexed = ingestion.indexed,
                scraped = ingestion.scraped,
                noMatch = ingestion.noMatch,
            )
        )
    }

    suspend fun runIfDue(): Result<CloudDriveRssRunSummary?> {
        val config = when (val configResult = repository.getConfig()) {
            is Result.Error -> return configResult
            is Result.Success -> configResult.data
        }
        if (!config.enabled) return Result.success(null)
        val intervalMs = config.intervalMinutes.coerceAtLeast(MIN_CLOUD_DRIVE_INTERVAL_MINUTES) * 60_000L
        if (System.currentTimeMillis() - config.lastRunAt < intervalMs) {
            return Result.success(null)
        }
        return runOnce().map { it }
    }

    private suspend fun resolveAuthenticatedEndpoint(config: CloudDriveAutomationConfig): Result<CloudDriveEndpoint> {
        val token = credentials.cloudDriveToken?.trim()?.takeIf { it.isNotBlank() }
        if (token != null) {
            return Result.success(CloudDriveEndpoint(config.endpointUrl, token))
        }
        return loginWithSavedCredentials(config)
    }

    private suspend fun submitOfflineFilesWithRelogin(
        config: CloudDriveAutomationConfig,
        currentEndpoint: CloudDriveEndpoint,
        urls: List<String>,
        targetFolder: String,
        onEndpointRefreshed: (CloudDriveEndpoint) -> Unit,
    ): Result<Unit> {
        val first = cloudDriveClient.addOfflineFiles(currentEndpoint, urls, targetFolder)
        if (first !is Result.Error || !first.error.isCloudDriveAuthenticationFailure()) return first

        val refreshed = when (val relogin = loginWithSavedCredentials(config)) {
            is Result.Success -> relogin.data
            is Result.Error -> return relogin
        }
        onEndpointRefreshed(refreshed)
        return cloudDriveClient.addOfflineFiles(refreshed, urls, targetFolder)
    }

    private suspend fun loginWithSavedCredentials(config: CloudDriveAutomationConfig): Result<CloudDriveEndpoint> {
        val username = config.username.trim()
        val password = credentials.cloudDrivePassword?.takeIf { it.isNotBlank() }
        if (username.isBlank() || password.isNullOrBlank()) {
            return Result.failure(AppError.MediaSourceError.AuthenticationFailed("CloudDrive2"))
        }
        return cloudDriveClient.login(config.endpointUrl, username, password).map { login ->
            credentials.cloudDriveToken = login.token
            CloudDriveEndpoint(config.endpointUrl, login.token)
        }
    }

    private fun AppError.isCloudDriveAuthenticationFailure(): Boolean {
        val detail = when (this) {
            is AppError.MediaSourceError.AuthenticationFailed -> return true
            is AppError.NetworkError.ServerUnreachable -> url
            is AppError.SyncError.WriteFailed -> cause
            else -> toUserMessage()
        }
        return detail.contains("UNAUTHENTICATED", ignoreCase = true) ||
            detail.contains("Invalid auth token", ignoreCase = true) ||
            detail.contains("认证失败", ignoreCase = true)
    }
}
