package com.miruplay.tv.sync.rss

import android.content.Context
import android.util.Log
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
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
) : SharedCloudDriveRssAutomationEngine(
    core = CloudDriveRssAutomationCore(
        repository = repository,
        credentials = securePreferences,
        cloudDriveClient = cloudDriveClient,
        feedFetcher = feedFetcher,
        organizer = organizer,
        submissionPreparer = submissionPreparer,
        events = androidRssAutomationEvents,
        afterIngested = { config, _, _ ->
            scanLinkedSource(
                config = config,
                scanCoordinator = scanCoordinator,
                posterCacheDirectory = File(context.cacheDir, "miruplay_image_cache"),
            )
        }
    ),
)

private object androidRssAutomationEvents : CloudDriveRssAutomationEvents {
    override fun onPrepareFailed(title: String, error: AppError) {
        Log.w("CloudDriveRss", "Prepare failed: $title: $error")
    }

    override fun onSubmitFailed(title: String, error: AppError) {
        Log.w("CloudDriveRss", "Submit failed: $title: $error")
    }
}

private suspend fun scanLinkedSource(
    config: CloudDriveAutomationConfig,
    scanCoordinator: ScanCoordinator,
    posterCacheDirectory: File,
): CloudDriveRssIngestionSummary {
    val webDavSourceId = config.webDavSourceId ?: return CloudDriveRssIngestionSummary()
    if (webDavSourceId <= 0) return CloudDriveRssIngestionSummary()

    return when (val scan = scanCoordinator.scanSource(webDavSourceId, posterCacheDirectory = posterCacheDirectory)) {
        is Result.Success -> CloudDriveRssIngestionSummary(
            indexed = scan.data.episodesFound,
            scraped = scan.data.scraped,
            noMatch = scan.data.noMatch,
        )
        is Result.Error -> {
            Log.w("CloudDriveRss", "Post-sync scan failed: ${scan.error}")
            CloudDriveRssIngestionSummary()
        }
    }
}
