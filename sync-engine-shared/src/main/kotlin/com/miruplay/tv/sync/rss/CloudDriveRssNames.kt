package com.miruplay.tv.sync.rss

import okhttp3.Request

object CloudDriveRssNames {
    fun folderSegment(value: String, fallback: String = UNKNOWN_FOLDER): String =
        value.replace("/", "_")
            .replace("\\", "_")
            .replace(INVALID_CLOUD_DRIVE_SEGMENT_CHARS, "_")
            .trim()
            .ifBlank { fallback }

    fun torrentFileName(title: String, url: String, keyPrefix: String): String {
        val fromTitle = title.trim().takeIf { it.endsWith(TORRENT_EXTENSION, ignoreCase = true) }
        val fromUrl = runCatching {
            Request.Builder().url(url).build().url.pathSegments.lastOrNull()
        }.getOrNull()
        val baseName = (fromTitle ?: fromUrl ?: DEFAULT_TORRENT_FILE_NAME)
            .substringBefore('?')
            .substringBefore('#')
            .ifBlank { DEFAULT_TORRENT_FILE_NAME }
            .let { if (it.endsWith(TORRENT_EXTENSION, ignoreCase = true)) it else "$it$TORRENT_EXTENSION" }
        val safeBaseName = fileName(baseName, DEFAULT_TORRENT_FILE_NAME).take(MAX_TORRENT_FILE_NAME_LENGTH)
        val prefix = torrentPrefix(keyPrefix)
        return if (prefix.isBlank()) safeBaseName else "$prefix-$safeBaseName"
    }

    private fun fileName(value: String, fallback: String): String =
        value.replace(INVALID_FILE_NAME_CHARS, "_")
            .replace(WHITESPACE, " ")
            .trim()
            .ifBlank { fallback }

    private fun torrentPrefix(value: String): String =
        value.replace(NON_PREFIX_CHARS, "").take(MAX_TORRENT_PREFIX_LENGTH)

    private val INVALID_CLOUD_DRIVE_SEGMENT_CHARS = Regex("""[<>:"|?*]""")
    private val INVALID_FILE_NAME_CHARS = Regex("""[\\/:*?"<>|]""")
    private val WHITESPACE = Regex("""\s+""")
    private val NON_PREFIX_CHARS = Regex("""[^A-Za-z0-9_-]""")

    private const val TORRENT_EXTENSION = ".torrent"
    private const val UNKNOWN_FOLDER = "Unknown"
    private const val DEFAULT_TORRENT_FILE_NAME = "rss-item.torrent"
    private const val MAX_TORRENT_FILE_NAME_LENGTH = 180
    private const val MAX_TORRENT_PREFIX_LENGTH = 12
}
