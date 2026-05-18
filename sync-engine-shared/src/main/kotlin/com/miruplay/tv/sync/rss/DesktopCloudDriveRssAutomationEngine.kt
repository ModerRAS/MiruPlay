package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDrivePaths
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.RssDownloadStatus
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopCloudDriveRssAutomationEngine(
    private val repository: CloudDriveAutomationRepository,
    private val credentials: CloudDriveCredentialStore,
    private val cloudDriveClient: CloudDriveClient,
    private val feedFetcher: RssFeedReader = RssFeedFetcher(),
    private val organizer: CloudDriveLibraryOrganizer = CloudDriveLibraryOrganizer(cloudDriveClient),
    private val torrentDownloader: TorrentFileDownloader = TorrentFileDownloader(),
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
        val config = repository.getConfig().getOrNull()
            ?: return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "配置读取失败"))
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
        torrentDownloader.configureProxy(config.rssProxyEnabled, config.rssProxyHost, config.rssProxyPort)

        val token = credentials.cloudDriveToken
            ?: return@withContext Result.failure(AppError.MediaSourceError.AuthenticationFailed("CloudDrive2"))
        val endpoint = CloudDriveEndpoint(config.endpointUrl, token)
        val subscriptions = repository.listEnabledSubscriptions().getOrNull().orEmpty()

        var submitted = 0
        var skipped = 0
        var failed = 0

        subscriptions.forEach subscriptionLoop@ { subscription ->
            val feedResult = feedFetcher.fetch(subscription.url)
            val items = feedResult.getOrNull()
            if (items == null) {
                failed += 1
                return@subscriptionLoop
            }

            val decisions = when (val planned = RssSubmissionPlanner.plan(items, subscription.filterRegex)) {
                is Result.Success -> planned.data
                is Result.Error -> {
                    failed += 1
                    return@subscriptionLoop
                }
            }
            decisions.forEach decisionLoop@ { decision ->
                if (decision.status == RssSubmissionDecisionStatus.SKIPPED_FILTER) {
                    skipped += 1
                    return@decisionLoop
                }
                val submissionUrl = decision.submissionUrl
                val itemKey = decision.itemKey
                if (decision.status == RssSubmissionDecisionStatus.MISSING_SUBMISSION ||
                    submissionUrl.isNullOrBlank() ||
                    itemKey.isNullOrBlank()
                ) {
                    failed += 1
                    return@decisionLoop
                }
                if (repository.isItemProcessed(subscription.id, itemKey).getOrNull() == true) {
                    skipped += 1
                    return@decisionLoop
                }

                val preparedSubmissionUrl = prepareSubmissionUrl(endpoint, decision.item, itemKey, submissionUrl, inboxPath)
                if (preparedSubmissionUrl is Result.Error) {
                    failed += 1
                    return@decisionLoop
                }

                val now = System.currentTimeMillis()
                cloudDriveClient.addOfflineFiles(endpoint, listOf((preparedSubmissionUrl as Result.Success).data), inboxPath)
                    .onSuccess {
                        repository.markItemProcessed(
                            RssProcessedItemInfo(
                                subscriptionId = subscription.id,
                                itemKey = itemKey,
                                title = decision.item.title,
                                url = submissionUrl,
                                processedAt = now,
                            )
                        )
                        repository.saveDownloadTask(
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
                        submitted += 1
                    }
                    .onError {
                        failed += 1
                    }
            }
            repository.markSubscriptionChecked(subscription.id, System.currentTimeMillis())
        }

        val organized = organizer.organize(endpoint, inboxPath, libraryPath).getOrNull() ?: 0
        repository.updateLastRunAt(System.currentTimeMillis())

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
        val config = repository.getConfig().getOrNull() ?: return Result.success(null)
        if (!config.enabled) return Result.success(null)
        val intervalMs = config.intervalMinutes.coerceAtLeast(5) * 60_000L
        if (System.currentTimeMillis() - config.lastRunAt < intervalMs) {
            return Result.success(null)
        }
        return runOnce().map { it }
    }

    private suspend fun prepareSubmissionUrl(
        endpoint: CloudDriveEndpoint,
        item: RssFeedItem,
        itemKey: String,
        submissionUrl: String,
        inboxPath: String,
    ): Result<String> {
        if (!item.isTorrentSubmission) return Result.success(submissionUrl)

        val downloaded = torrentDownloader.download(
            url = submissionUrl,
            title = item.title,
            keyPrefix = RssSubmissionPlanner.stableHash(itemKey).take(12),
        )
        if (downloaded is Result.Error) return downloaded
        val torrent = (downloaded as Result.Success).data
        return try {
            val magnet = TorrentMagnetParser.parse(torrent.file)
            if (magnet is Result.Error) return magnet

            val stagingPath = ensureTorrentStagingFolder(endpoint, inboxPath)
            if (stagingPath is Result.Error) return stagingPath
            val uploaded = cloudDriveClient.uploadFile(
                endpoint = endpoint,
                localFile = torrent.file,
                parentPath = (stagingPath as Result.Success).data,
                remoteFileName = torrent.remoteFileName,
            )
            if (uploaded is Result.Error && !uploaded.error.isAlreadyExists()) return uploaded

            magnet
        } finally {
            torrent.file.delete()
        }
    }

    private suspend fun ensureTorrentStagingFolder(endpoint: CloudDriveEndpoint, inboxPath: String): Result<String> {
        val normalizedInbox = CloudDrivePaths.normalizeScoped(inboxPath)
        val stagingPath = "$normalizedInbox/$TORRENT_STAGING_FOLDER"
        val listing = cloudDriveClient.listFolder(endpoint, normalizedInbox, forceRefresh = false)
        if (listing is Result.Error) return listing
        val exists = (listing as Result.Success).data.any { it.isDirectory && it.name == TORRENT_STAGING_FOLDER }
        if (!exists) {
            val created = cloudDriveClient.createFolder(endpoint, normalizedInbox, TORRENT_STAGING_FOLDER)
            if (created is Result.Error) return created
        }
        return Result.success(stagingPath)
    }

    private fun AppError.isAlreadyExists(): Boolean =
        toString().contains("ALREADY_EXISTS", ignoreCase = true) ||
            toString().contains("already exists", ignoreCase = true)

    companion object {
        private const val TORRENT_STAGING_FOLDER = ".miruplay-torrents"
    }
}
