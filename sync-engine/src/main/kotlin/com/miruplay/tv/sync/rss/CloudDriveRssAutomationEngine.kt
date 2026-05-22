package com.miruplay.tv.sync.rss

import android.util.Log
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.RssDownloadStatus
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import com.miruplay.tv.scanner.ScanCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudDriveRssAutomationEngine @Inject constructor(
    private val repository: CloudDriveAutomationRepository,
    private val securePreferences: CloudDriveCredentialStore,
    private val feedFetcher: RssFeedFetcher,
    private val cloudDriveClient: CloudDriveClient,
    private val organizer: CloudDriveLibraryOrganizer,
    private val scanCoordinator: ScanCoordinator,
    private val torrentDownloader: TorrentFileDownloader
) {
    suspend fun login(endpointUrl: String, username: String, password: String): Result<Unit> {
        val result = cloudDriveClient.login(endpointUrl, username, password)
        return result.map { login ->
            securePreferences.cloudDriveToken = login.token
            securePreferences.cloudDrivePassword = password
            Unit
        }
    }

    suspend fun saveApiToken(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> {
        val result = cloudDriveClient.getApiTokenInfo(endpointUrl, token)
        return result.map { info ->
            securePreferences.cloudDriveToken = token
            info
        }
    }

    suspend fun runOnce(): Result<CloudDriveRssRunSummary> = withContext(Dispatchers.IO) {
        val config = repository.getConfig().getOrNull()
            ?: return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "配置读取失败"))
        if (config.endpointUrl.isBlank()) {
            return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "请先设置 CloudDrive2 地址"))
        }
        val inboxPath = CloudDrivePathPolicy.normalize(config.inboxPath)
        val libraryPath = CloudDrivePathPolicy.normalize(config.libraryPath)
        if (!CloudDrivePathPolicy.isScopedDirectory(inboxPath)) {
            return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "请设置非根目录作为下载目录 A"))
        }
        if (!CloudDrivePathPolicy.isScopedDirectory(libraryPath)) {
            return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "请设置非根目录作为整理目录 B"))
        }
        if (CloudDrivePathPolicy.isSameOrChild(libraryPath, inboxPath)) {
            return@withContext Result.failure(AppError.SyncError.WriteFailed("CloudDrive", "整理目录 B 不能放在下载目录 A 内部"))
        }
        // Configure RSS feed fetcher proxy before fetching
        feedFetcher.configureProxy(config.rssProxyEnabled, config.rssProxyHost, config.rssProxyPort)
        torrentDownloader.configureProxy(config.rssProxyEnabled, config.rssProxyHost, config.rssProxyPort)

        val token = securePreferences.cloudDriveToken
            ?: return@withContext Result.failure(AppError.MediaSourceError.AuthenticationFailed("CloudDrive2"))
        val endpoint = CloudDriveEndpoint(config.endpointUrl, token)
        val subscriptions = repository.listEnabledSubscriptions().getOrNull().orEmpty()
        MiruLog.i(
            "CloudDriveRss",
            "CloudDrive RSS run started",
            mapOf(
                "subscription_count" to subscriptions.size.toString(),
                "inbox_path" to inboxPath,
                "library_path" to libraryPath
            )
        )

        var submitted = 0
        var skipped = 0
        var failed = 0

        subscriptions.forEach { subscription ->
            val feedResult = feedFetcher.fetch(subscription.url)
            val items = feedResult.getOrNull()
            if (items == null) {
                failed += 1
                return@forEach
            }

            val filter = subscription.filterRegex?.takeIf { it.isNotBlank() }?.let {
                runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull()
            }
            items.forEach { item ->
                if (filter != null && !filter.containsMatchIn(item.title)) {
                    skipped += 1
                    return@forEach
                }
                val submissionUrl = item.submissionUrl
                if (submissionUrl.isNullOrBlank()) {
                    failed += 1
                    return@forEach
                }
                val itemKey = item.guid?.takeIf { it.isNotBlank() } ?: sha1("${item.title}|$submissionUrl")
                if (repository.isItemProcessed(subscription.id, itemKey).getOrNull() == true) {
                    skipped += 1
                    return@forEach
                }

                val preparedSubmissionUrl = prepareSubmissionUrl(endpoint, item, itemKey, submissionUrl, inboxPath)
                if (preparedSubmissionUrl is Result.Error) {
                    failed += 1
                    Log.w("CloudDriveRss", "Prepare failed: ${item.title}: ${preparedSubmissionUrl.error}")
                    MiruLog.w(
                        "CloudDriveRss",
                        "RSS item preparation failed",
                        attributes = mapOf(
                            "title" to item.title,
                            "error_type" to preparedSubmissionUrl.error::class.simpleName.orEmpty(),
                            "error_message" to preparedSubmissionUrl.error.toUserMessage()
                        )
                    )
                    return@forEach
                }

                val now = System.currentTimeMillis()
                cloudDriveClient.addOfflineFiles(endpoint, listOf((preparedSubmissionUrl as Result.Success).data), inboxPath)
                    .onSuccess {
                        repository.markItemProcessed(
                            RssProcessedItemInfo(
                                subscriptionId = subscription.id,
                                itemKey = itemKey,
                                title = item.title,
                                url = submissionUrl,
                                processedAt = now
                            )
                        )
                        repository.saveDownloadTask(
                            RssDownloadTaskInfo(
                                subscriptionId = subscription.id,
                                itemKey = itemKey,
                                title = item.title,
                                url = submissionUrl,
                                status = RssDownloadStatus.SUBMITTED,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        submitted += 1
                    }
                    .onError { error ->
                        failed += 1
                        Log.w("CloudDriveRss", "Submit failed: ${item.title}: $error")
                        MiruLog.w(
                            "CloudDriveRss",
                            "RSS item submit failed",
                            attributes = mapOf(
                                "title" to item.title,
                                "error_type" to error::class.simpleName.orEmpty(),
                                "error_message" to error.toUserMessage()
                            )
                        )
                    }
            }
            repository.markSubscriptionChecked(subscription.id, System.currentTimeMillis())
        }

        val organized = organizer.organize(endpoint, inboxPath, libraryPath).getOrNull() ?: 0
        val webDavSourceId = config.webDavSourceId
        if (webDavSourceId != null && webDavSourceId > 0) {
            scanCoordinator.scanSource(webDavSourceId)
        }
        repository.updateLastRunAt(System.currentTimeMillis())

        val summary = CloudDriveRssRunSummary(
            submitted = submitted,
            skipped = skipped,
            failed = failed,
            organized = organized
        )
        MiruLog.i(
            "CloudDriveRss",
            "CloudDrive RSS run finished",
            mapOf(
                "submitted" to summary.submitted.toString(),
                "skipped" to summary.skipped.toString(),
                "failed" to summary.failed.toString(),
                "organized" to summary.organized.toString()
            )
        )
        Result.success(summary)
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
        inboxPath: String
    ): Result<String> {
        if (!item.isTorrentSubmission) return Result.success(submissionUrl)

        val downloaded = torrentDownloader.download(
            url = submissionUrl,
            title = item.title,
            keyPrefix = sha1(itemKey).take(12)
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
                remoteFileName = torrent.remoteFileName
            )
            if (uploaded is Result.Error && !uploaded.error.isAlreadyExists()) return uploaded

            magnet
        } finally {
            torrent.file.delete()
        }
    }

    private suspend fun ensureTorrentStagingFolder(endpoint: CloudDriveEndpoint, inboxPath: String): Result<String> {
        val normalizedInbox = CloudDrivePathPolicy.normalize(inboxPath)
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

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TORRENT_STAGING_FOLDER = ".miruplay-torrents"
    }
}
