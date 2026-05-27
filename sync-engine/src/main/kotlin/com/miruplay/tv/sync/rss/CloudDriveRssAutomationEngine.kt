package com.miruplay.tv.sync.rss

import android.content.Context
import android.util.Log
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import com.miruplay.tv.scanner.ScanCoordinator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudDriveRssAutomationEngine @Inject constructor(
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
        afterOrganized = { config, _, _ ->
            val webDavSourceId = config.webDavSourceId
            if (webDavSourceId != null && webDavSourceId > 0) {
                scanCoordinator.scanSource(webDavSourceId)
            }
        },
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
