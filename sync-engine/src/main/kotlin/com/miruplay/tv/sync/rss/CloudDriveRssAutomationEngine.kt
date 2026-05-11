package com.miruplay.tv.sync.rss

import android.util.Log
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.repository.CloudDriveAutomationRepository
import com.miruplay.tv.data.secure.SecurePreferencesManager
import com.miruplay.tv.model.RssDownloadStatus
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.scanner.ScanCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudDriveRssAutomationEngine @Inject constructor(
    private val repository: CloudDriveAutomationRepository,
    private val securePreferences: SecurePreferencesManager,
    private val feedFetcher: RssFeedFetcher,
    private val cloudDriveClient: CloudDriveClient,
    private val organizer: CloudDriveLibraryOrganizer,
    private val scanCoordinator: ScanCoordinator
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

        val token = securePreferences.cloudDriveToken
            ?: return@withContext Result.failure(AppError.MediaSourceError.AuthenticationFailed("CloudDrive2"))
        val endpoint = CloudDriveEndpoint(config.endpointUrl, token)
        val subscriptions = repository.listEnabledSubscriptions().getOrNull().orEmpty()

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

                val now = System.currentTimeMillis()
                cloudDriveClient.addOfflineFiles(endpoint, listOf(submissionUrl), inboxPath)
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

        Result.success(
            CloudDriveRssRunSummary(
                submitted = submitted,
                skipped = skipped,
                failed = failed,
                organized = organized
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

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
