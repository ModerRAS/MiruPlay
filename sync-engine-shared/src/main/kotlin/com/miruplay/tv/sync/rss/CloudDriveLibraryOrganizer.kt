package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.clouddrive.CloudDrivePaths
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.VideoFilenameInference

data class CloudDriveVideoClassification(
    val showName: String,
    val seasonNumber: Int,
)

fun interface CloudDriveVideoClassifier {
    fun classify(file: CloudDriveFileInfo): CloudDriveVideoClassification
}

class CloudDriveLibraryOrganizer(
    private val cloudDriveClient: CloudDriveClient,
    private val classifier: CloudDriveVideoClassifier = HeuristicCloudDriveVideoClassifier,
) {
    suspend fun organize(endpoint: CloudDriveEndpoint, inboxPath: String, libraryPath: String): Result<Int> {
        val inbox = CloudDrivePaths.normalizeScoped(inboxPath)
        val library = CloudDrivePaths.normalizeScoped(libraryPath)
        if (!CloudDrivePaths.isScopedDirectory(inbox)) {
            return Result.failure(AppError.SyncError.WriteFailed("CloudDrive2", "下载目录不能是空目录或根目录"))
        }
        if (!CloudDrivePaths.isScopedDirectory(library)) {
            return Result.failure(AppError.SyncError.WriteFailed("CloudDrive2", "整理目录不能是空目录或根目录"))
        }
        if (CloudDrivePaths.isSameOrChild(library, inbox)) {
            return Result.failure(AppError.SyncError.WriteFailed("CloudDrive2", "整理目录不能位于下载目录内部"))
        }

        val videos = when (val collected = collectVideos(endpoint, inbox, depth = 0)) {
            is Result.Error -> return collected
            is Result.Success -> collected.data
        }
        var moved = 0
        for (video in videos) {
            if (!CloudDrivePaths.isChild(video.path, inbox)) continue
            val classification = classifier.classify(video)
            val showFolder = CloudDriveRssNames.folderSegment(classification.showName)
            val seasonFolder = "Season ${classification.seasonNumber.coerceAtLeast(1)}"
            val showPath = "$library/$showFolder"
            val seasonPath = "$showPath/$seasonFolder"

            when (val ensuredShowFolder = ensureFolder(endpoint, library, showFolder)) {
                is Result.Error -> return ensuredShowFolder
                is Result.Success -> Unit
            }
            when (val ensuredSeasonFolder = ensureFolder(endpoint, showPath, seasonFolder)) {
                is Result.Error -> return ensuredSeasonFolder
                is Result.Success -> Unit
            }

            when (val movedResult = cloudDriveClient.moveFiles(endpoint, listOf(video.path), seasonPath)) {
                is Result.Error -> return movedResult
                is Result.Success -> moved += 1
            }
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
        val entries = when (listing) {
            is Result.Error -> return listing
            is Result.Success -> listing.data
        }
        val videos = mutableListOf<CloudDriveFileInfo>()
        for (entry in entries) {
            if (!CloudDrivePaths.isSameOrChild(entry.path, path)) continue
            if (entry.name.startsWith(".") || entry.name.endsWith(".trickplay")) continue
            if (entry.isDirectory) {
                when (val childVideos = collectVideos(endpoint, entry.path, depth + 1)) {
                    is Result.Error -> return childVideos
                    is Result.Success -> videos += childVideos.data
                }
            } else if (entry.name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS) {
                videos += entry
            }
        }
        return Result.success(videos)
    }

    private suspend fun ensureFolder(
        endpoint: CloudDriveEndpoint,
        parentPath: String,
        folderName: String
    ): Result<Unit> {
        val entries = when (val listing = cloudDriveClient.listFolder(endpoint, parentPath, forceRefresh = false)) {
            is Result.Error -> return listing
            is Result.Success -> listing.data
        }
        val exists = entries.any { it.isDirectory && it.name == folderName }
        if (!exists) {
            val created = cloudDriveClient.createFolder(endpoint, parentPath, folderName)
            if (created is Result.Error) return created
        }
        return Result.success(Unit)
    }

    private companion object {
        private const val MAX_DEPTH = 5
        private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "m2ts")
    }
}

object HeuristicCloudDriveVideoClassifier : CloudDriveVideoClassifier {
    override fun classify(file: CloudDriveFileInfo): CloudDriveVideoClassification {
        val metadata = VideoFilenameInference.infer(
            fileName = file.name,
            parentName = CloudDrivePaths.parentPath(file.path).substringAfterLast('/', ""),
        )
        return CloudDriveVideoClassification(
            showName = metadata.title,
            seasonNumber = metadata.seasonNumber ?: 1,
        )
    }
}

typealias DesktopCloudDriveLibraryOrganizer = CloudDriveLibraryOrganizer
