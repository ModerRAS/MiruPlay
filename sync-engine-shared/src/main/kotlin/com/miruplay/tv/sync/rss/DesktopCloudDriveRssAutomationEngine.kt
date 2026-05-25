package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveTokenInfo
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore

class DesktopCloudDriveRssAutomationEngine(
    repository: CloudDriveAutomationRepository,
    credentials: CloudDriveCredentialStore,
    cloudDriveClient: CloudDriveClient,
    feedFetcher: RssFeedReader = RssFeedFetcher(),
    organizer: CloudDriveLibraryOrganizer = CloudDriveLibraryOrganizer(cloudDriveClient),
    submissionPreparer: CloudDriveRssSubmissionPreparer = CloudDriveRssSubmissionPreparer(cloudDriveClient),
) {
    private val core = CloudDriveRssAutomationCore(
        repository = repository,
        credentials = credentials,
        cloudDriveClient = cloudDriveClient,
        feedFetcher = feedFetcher,
        organizer = organizer,
        submissionPreparer = submissionPreparer,
    )

    suspend fun login(endpointUrl: String, username: String, password: String): Result<Unit> =
        core.login(endpointUrl, username, password)

    suspend fun saveApiToken(endpointUrl: String, token: String): Result<CloudDriveTokenInfo> =
        core.saveApiToken(endpointUrl, token)

    suspend fun runOnce(): Result<CloudDriveRssRunSummary> =
        core.runOnce()

    suspend fun runIfDue(): Result<CloudDriveRssRunSummary?> =
        core.runIfDue()
}
