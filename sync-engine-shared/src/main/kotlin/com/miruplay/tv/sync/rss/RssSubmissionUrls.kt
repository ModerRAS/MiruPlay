package com.miruplay.tv.sync.rss

object RssSubmissionUrls {
    fun select(link: String?, enclosureUrl: String?): String? =
        listOfNotNull(link, enclosureUrl)
            .firstOrNull { typeOf(it).isOfflineDownloadCandidate }
            ?: link
            ?: enclosureUrl

    fun typeOf(url: String?): RssSubmissionUrlType {
        val value = url?.trim().orEmpty()
        return when {
            value.isBlank() -> RssSubmissionUrlType.NONE
            value.startsWith("magnet:", ignoreCase = true) -> RssSubmissionUrlType.MAGNET
            value.substringBefore('?').substringBefore('#').endsWith(".torrent", ignoreCase = true) ->
                RssSubmissionUrlType.TORRENT
            else -> RssSubmissionUrlType.OTHER
        }
    }

    fun isTorrent(url: String?): Boolean =
        typeOf(url) == RssSubmissionUrlType.TORRENT

    private val RssSubmissionUrlType.isOfflineDownloadCandidate: Boolean
        get() = this == RssSubmissionUrlType.MAGNET || this == RssSubmissionUrlType.TORRENT
}
