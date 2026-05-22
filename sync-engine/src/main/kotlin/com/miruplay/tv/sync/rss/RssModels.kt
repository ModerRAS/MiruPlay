package com.miruplay.tv.sync.rss

data class RssFeedItem(
    val title: String,
    val guid: String?,
    val link: String?,
    val enclosureUrl: String?
) {
    val submissionUrl: String?
        get() = listOfNotNull(link, enclosureUrl)
            .firstOrNull { it.startsWith("magnet:", ignoreCase = true) || it.isTorrentUrl() }
            ?: link
            ?: enclosureUrl

    val isTorrentSubmission: Boolean
        get() = submissionUrl?.isTorrentUrl() == true
}

private fun String.isTorrentUrl(): Boolean =
    substringBefore('?')
        .substringBefore('#')
        .endsWith(".torrent", ignoreCase = true)
