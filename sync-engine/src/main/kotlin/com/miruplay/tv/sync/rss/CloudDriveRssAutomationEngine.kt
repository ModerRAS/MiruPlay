package com.miruplay.tv.sync.rss

import android.content.Context
import android.util.Log
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import com.miruplay.tv.scanner.ScanCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudDriveRssAutomationEngine @Inject constructor(
    @ApplicationContext context: Context,
    repository: CloudDriveAutomationRepository,
    securePreferences: CloudDriveCredentialStore,
    feedFetcher: RssFeedReader,
    cloudDriveClient: CloudDriveClient,
    organizer: CloudDriveLibraryOrganizer,
    scanCoordinator: ScanCoordinator,
    submissionPreparer: CloudDriveRssSubmissionPreparer
) {
    private val posterCacheDirectory = File(context.cacheDir, "miruplay_image_cache")

    private val core = CloudDriveRssAutomationCore(
        repository = repository,
        credentials = securePreferences,
        cloudDriveClient = cloudDriveClient,
        feedFetcher = feedFetcher,
        organizer = organizer,
        submissionPreparer = submissionPreparer,
        events = AndroidRssAutomationEvents,
        afterIngested = { config, _, _ -> scanLinkedSource(config, scanCoordinator) },
    )

    override suspend fun login(endpointUrl: String, username: String, password: String): Result<Unit> =
        core.login(endpointUrl, username, password)

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

    override suspend fun runOnce(): Result<CloudDriveRssRunSummary> =
        core.runOnce()

    suspend fun runIfDue(): Result<CloudDriveRssRunSummary?> =
        core.runIfDue()

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
            is Result.Error -> {
                Log.w("CloudDriveRss", "Post-sync scan failed: ${scan.error}")
                CloudDriveRssIngestionSummary()
            }
        }
    }

    private object AndroidRssAutomationEvents : CloudDriveRssAutomationEvents {
        override fun onPrepareFailed(title: String, error: AppError) {
            Log.w("CloudDriveRss", "Prepare failed: $title: $error")
        }

        override fun onSubmitFailed(title: String, error: AppError) {
            Log.w("CloudDriveRss", "Submit failed: $title: $error")
        }
    }
}
