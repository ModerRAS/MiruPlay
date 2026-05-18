package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDrivePaths
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result

data class PreparedRssSubmission(
    val originalUrl: String,
    val submissionUrl: String,
    val stagedTorrentPath: String? = null,
)

class CloudDriveRssSubmissionPreparer(
    private val cloudDriveClient: CloudDriveClient,
    private val torrentDownloader: RssTorrentDownloader = TorrentFileDownloader(),
) {
    fun configureProxy(enabled: Boolean, host: String, port: Int) {
        torrentDownloader.configureProxy(enabled, host, port)
    }

    suspend fun prepare(
        endpoint: CloudDriveEndpoint,
        item: RssFeedItem,
        itemKey: String,
        submissionUrl: String,
        inboxPath: String,
    ): Result<PreparedRssSubmission> {
        if (!submissionUrl.isTorrentUrl()) {
            return Result.success(
                PreparedRssSubmission(
                    originalUrl = submissionUrl,
                    submissionUrl = submissionUrl,
                )
            )
        }

        val downloaded = torrentDownloader.download(
            url = submissionUrl,
            title = item.title,
            keyPrefix = RssSubmissionPlanner.stableHash(itemKey).take(12),
        )
        if (downloaded is Result.Error) return downloaded
        val torrent = (downloaded as Result.Success).data
        return try {
            val magnet = TorrentMagnetParser.parse(torrent.file)
            if (magnet is Result.Error) return magnet

            val stagingPath = ensureTorrentStagingFolder(endpoint, inboxPath)
            if (stagingPath is Result.Error) return stagingPath
            val uploaded = cloudDriveClient.uploadFile(
                endpoint = endpoint,
                localFile = torrent.file,
                parentPath = (stagingPath as Result.Success).data,
                remoteFileName = torrent.remoteFileName,
            )
            if (uploaded is Result.Error && !uploaded.error.isAlreadyExists()) return uploaded

            Result.success(
                PreparedRssSubmission(
                    originalUrl = submissionUrl,
                    submissionUrl = (magnet as Result.Success).data,
                    stagedTorrentPath = (uploaded as? Result.Success)?.data,
                )
            )
        } finally {
            torrent.file.delete()
        }
    }

    private suspend fun ensureTorrentStagingFolder(endpoint: CloudDriveEndpoint, inboxPath: String): Result<String> {
        val normalizedInbox = CloudDrivePaths.normalizeScoped(inboxPath)
        val stagingPath = "$normalizedInbox/$TORRENT_STAGING_FOLDER"
        val listing = cloudDriveClient.listFolder(endpoint, normalizedInbox, forceRefresh = false)
        if (listing is Result.Error) return listing
        val exists = (listing as Result.Success).data.any { it.isDirectory && it.name == TORRENT_STAGING_FOLDER }
        if (!exists) {
            val created = cloudDriveClient.createFolder(endpoint, normalizedInbox, TORRENT_STAGING_FOLDER)
            if (created is Result.Error) return created
        }
        return Result.success(stagingPath)
    }

    private fun AppError.isAlreadyExists(): Boolean =
        toString().contains("ALREADY_EXISTS", ignoreCase = true) ||
            toString().contains("already exists", ignoreCase = true)

    private companion object {
        private const val TORRENT_STAGING_FOLDER = ".miruplay-torrents"
    }
}

private fun String.isTorrentUrl(): Boolean =
    substringBefore('?')
        .substringBefore('#')
        .endsWith(".torrent", ignoreCase = true)
