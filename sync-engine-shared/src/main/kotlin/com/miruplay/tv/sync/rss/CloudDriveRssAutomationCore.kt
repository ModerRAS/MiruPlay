package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDrivePaths
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveRssRunSummary
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

class CloudDriveRssAutomationCore(
    private val repository: CloudDriveAutomationRepository,
    private val credentials: CloudDriveCredentialStore,
    private val cloudDriveClient: CloudDriveClient,
    private val feedFetcher: RssFeedReader = RssFeedFetcher(),
    private val organizer: CloudDriveLibraryOrganizer = CloudDriveLibraryOrganizer(cloudDriveClient),
    private val submissionPreparer: CloudDriveRssSubmissionPreparer = CloudDriveRssSubmissionPreparer(cloudDriveClient),
    private val events: CloudDriveRssAutomationEvents = CloudDriveRssAutomationEvents.None,
    private val afterOrganized: suspend (CloudDriveAutomationConfig, CloudDriveEndpoint, Int) -> Unit = { _, _, _ -> },
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
            return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "请设置非根目录作为下载目录 A"))
        }
        if (!CloudDrivePaths.isScopedDirectory(libraryPath)) {
            return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "请设置非根目录作为整理目录 B"))
        }
        if (CloudDrivePaths.isSameOrChild(libraryPath, inboxPath)) {
            return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "整理目录 B 不能放在下载目录 A 内部"))
        }

        feedFetcher.configureProxy(config.rssProxyEnabled, config.rssProxyHost, config.rssProxyPort)
        submissionPreparer.configureProxy(config.rssProxyEnabled, config.rssProxyHost, config.rssProxyPort)

        val token = credentials.cloudDriveToken
            ?: return@withContext Result.failure(AppError.MediaSourceError.AuthenticationFailed("CloudDrive2"))
        val endpoint = CloudDriveEndpoint(config.endpointUrl, token)
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
                    val submittedToCloudDrive = cloudDriveClient.addOfflineFiles(
                        endpoint,
                        listOf((preparedSubmission as Result.Success).data.submissionUrl),
                        inboxPath
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

        val organized = when (val organizedResult = organizer.organize(endpoint, inboxPath, libraryPath)) {
            is Result.Error -> return@withContext organizedResult
            is Result.Success -> organizedResult.data
        }
        afterOrganized(config, endpoint, organized)
        when (val lastRunUpdated = repository.updateLastRunAt(System.currentTimeMillis())) {
            is Result.Error -> return@withContext lastRunUpdated
            is Result.Success -> Unit
        }

        Result.success(
            CloudDriveRssRunSummary(
                submitted = submitted,
                skipped = skipped,
                failed = failed,
                organized = organized,
            )
        )
    }

    suspend fun runIfDue(): Result<CloudDriveRssRunSummary?> {
        val config = when (val configResult = repository.getConfig()) {
            is Result.Error -> return configResult
            is Result.Success -> configResult.data
        }
        if (!config.enabled) return Result.success(null)
        val intervalMs = config.intervalMinutes.coerceAtLeast(5) * 60_000L
        if (System.currentTimeMillis() - config.lastRunAt < intervalMs) {
            return Result.success(null)
        }
        return runOnce().map { it }
    }
}
