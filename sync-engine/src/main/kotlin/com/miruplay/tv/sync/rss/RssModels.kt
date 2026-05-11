package com.miruplay.tv.sync.rss

data class RssFeedItem(
    val title: String,
    val guid: String?,
    val link: String?,
    val enclosureUrl: String?
) {
    val submissionUrl: String?
        get() = listOfNotNull(link, enclosureUrl)
            .firstOrNull { it.startsWith("magnet:", ignoreCase = true) || it.endsWith(".torrent", ignoreCase = true) }
            ?: link
            ?: enclosureUrl
}

data class CloudDriveRssRunSummary(
    val submitted: Int,
    val skipped: Int,
    val failed: Int,
    val organized: Int
)
