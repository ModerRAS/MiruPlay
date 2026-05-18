package com.miruplay.tv.sync.rss

data class RssFeedItem(
    val title: String,
    val guid: String?,
    val link: String?,
    val enclosureUrl: String?
) {
    val submissionUrl: String?
        get() = RssSubmissionUrls.select(link, enclosureUrl)

    val isTorrentSubmission: Boolean
        get() = RssSubmissionUrls.isTorrent(submissionUrl)
}

data class CloudDriveRssRunSummary(
    val submitted: Int,
    val skipped: Int,
    val failed: Int,
    val organized: Int
)
