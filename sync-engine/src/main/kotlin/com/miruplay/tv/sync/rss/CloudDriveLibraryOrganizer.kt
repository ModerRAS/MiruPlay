package com.miruplay.tv.sync.rss

import android.util.Log
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.scanner.DefaultEpisodeDetector
import com.miruplay.tv.scanner.VideoDirectoryClassifier
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudDriveLibraryOrganizer @Inject constructor(
    private val cloudDriveClient: CloudDriveClient,
    filenameMetadataParser: FilenameMetadataParser
) {
    private val classifier = VideoDirectoryClassifier(DefaultEpisodeDetector(), filenameMetadataParser)

    suspend fun organize(endpoint: CloudDriveEndpoint, inboxPath: String, libraryPath: String): Result<Int> {
        val inbox = CloudDrivePathPolicy.normalize(inboxPath)
        val library = CloudDrivePathPolicy.normalize(libraryPath)
        if (!CloudDrivePathPolicy.isScopedDirectory(inbox)) {
            return Result.failure(AppError.SyncError.WriteFailed("CloudDrive2", "下载目录不能是空目录或根目录"))
        }
        if (!CloudDrivePathPolicy.isScopedDirectory(library)) {
            return Result.failure(AppError.SyncError.WriteFailed("CloudDrive2", "整理目录不能是空目录或根目录"))
        }
        if (CloudDrivePathPolicy.isSameOrChild(library, inbox)) {
            return Result.failure(AppError.SyncError.WriteFailed("CloudDrive2", "整理目录不能位于下载目录内部"))
        }

        val files = collectVideos(endpoint, inbox, depth = 0).getOrNull() ?: return Result.success(0)
        var moved = 0
        for (file in files) {
            if (!CloudDrivePathPolicy.isChild(file.path, inbox)) continue
            val classification = classifier.classifyVideo(file.path, file.name)
            val showFolder = sanitizePathSegment(classification.animeName)
            val seasonFolder = "Season ${classification.seasonNumber}"
            val showPath = "$library/$showFolder"
            val seasonPath = "$showPath/$seasonFolder"

            ensureFolder(endpoint, library, showFolder)
            ensureFolder(endpoint, showPath, seasonFolder)

            cloudDriveClient.moveFiles(endpoint, listOf(file.path), seasonPath)
                .onSuccess { moved += 1 }
                .onError { error -> Log.w("CloudDriveOrganizer", "Move failed for ${file.path}: $error") }
        }
        return Result.success(moved)
    }

    private suspend fun collectVideos(
        endpoint: CloudDriveEndpoint,
        path: String,
        depth: Int
    ): Result<List<CloudDriveFileInfo>> {
        if (depth > MAX_DEPTH) return Result.success(emptyList())
        val listing = cloudDriveClient.listFolder(endpoint, path, forceRefresh = true)
        val entries = listing.getOrNull() ?: return listing
        val videos = mutableListOf<CloudDriveFileInfo>()
        for (entry in entries) {
            if (!CloudDrivePathPolicy.isSameOrChild(entry.path, path)) continue
            if (entry.name.startsWith(".") || entry.name.endsWith(".trickplay")) continue
            if (entry.isDirectory) {
                videos += collectVideos(endpoint, entry.path, depth + 1).getOrNull().orEmpty()
            } else if (entry.name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS) {
                videos += entry
            }
        }
        return Result.success(videos)
    }

    private suspend fun ensureFolder(endpoint: CloudDriveEndpoint, parentPath: String, folderName: String) {
        val exists = cloudDriveClient.listFolder(endpoint, parentPath, forceRefresh = false)
            .getOrNull()
            .orEmpty()
            .any { it.isDirectory && it.name == folderName }
        if (!exists) {
            cloudDriveClient.createFolder(endpoint, parentPath, folderName)
        }
    }

    private fun sanitizePathSegment(value: String): String =
        value.replace("/", "_")
            .replace("\\", "_")
            .trim()
            .ifBlank { "Unknown" }

    companion object {
        private const val MAX_DEPTH = 5
        private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "m2ts")
    }
}
