package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore

class DesktopCloudDriveRssAutomationEngine(
    repository: CloudDriveAutomationRepository,
    credentials: CloudDriveCredentialStore,
    cloudDriveClient: CloudDriveClient,
    feedFetcher: RssFeedReader = RssFeedFetcher(),
    organizer: CloudDriveLibraryOrganizer = CloudDriveLibraryOrganizer(cloudDriveClient),
    submissionPreparer: CloudDriveRssSubmissionPreparer = CloudDriveRssSubmissionPreparer(cloudDriveClient),
) : SharedCloudDriveRssAutomationEngine(
    core = CloudDriveRssAutomationCore(
        repository = repository,
        credentials = credentials,
        cloudDriveClient = cloudDriveClient,
        feedFetcher = feedFetcher,
        organizer = organizer,
        submissionPreparer = submissionPreparer,
    ),
)
