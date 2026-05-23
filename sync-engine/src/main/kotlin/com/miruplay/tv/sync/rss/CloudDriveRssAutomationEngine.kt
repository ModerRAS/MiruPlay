package com.miruplay.tv.sync.rss

import android.util.Log
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveRssRunSummary
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
) {
    private val core = CloudDriveRssAutomationCore(
        repository = repository,
        credentials = securePreferences,
        cloudDriveClient = cloudDriveClient,
        feedFetcher = feedFetcher,
        organizer = organizer,
        submissionPreparer = submissionPreparer,
        events = AndroidRssAutomationEvents,
        afterOrganized = { config, _, _ ->
            val webDavSourceId = config.webDavSourceId
            if (webDavSourceId != null && webDavSourceId > 0) {
                scanCoordinator.scanSource(webDavSourceId)
            }
        },
    )

    suspend fun login(endpointUrl: String, username: String, password: String): Result<Unit> =
        core.login(endpointUrl, username, password)

    suspend fun saveApiToken(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> =
        core.saveApiToken(endpointUrl, token)

    suspend fun runOnce(): Result<CloudDriveRssRunSummary> =
        core.runOnce()

    suspend fun runIfDue(): Result<CloudDriveRssRunSummary?> =
        core.runIfDue()

    private object AndroidRssAutomationEvents : CloudDriveRssAutomationEvents {
        override fun onPrepareFailed(title: String, error: AppError) {
            Log.w("CloudDriveRss", "Prepare failed: $title: $error")
        }

        override fun onSubmitFailed(title: String, error: AppError) {
            Log.w("CloudDriveRss", "Submit failed: $title: $error")
        }
    }
}
