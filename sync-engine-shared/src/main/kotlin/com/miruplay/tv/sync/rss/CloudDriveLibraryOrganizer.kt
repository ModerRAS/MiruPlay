package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.clouddrive.CloudDriveEndpoint
import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.clouddrive.CloudDrivePaths
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result

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

        val videos = collectVideos(endpoint, inbox, depth = 0).getOrNull() ?: return Result.success(0)
        var moved = 0
        for (video in videos) {
            if (!CloudDrivePaths.isChild(video.path, inbox)) continue
            val classification = classifier.classify(video)
            val showFolder = sanitizePathSegment(classification.showName)
            val seasonFolder = "Season ${classification.seasonNumber.coerceAtLeast(1)}"
            val showPath = "$library/$showFolder"
            val seasonPath = "$showPath/$seasonFolder"

            ensureFolder(endpoint, library, showFolder)
            ensureFolder(endpoint, showPath, seasonFolder)

            cloudDriveClient.moveFiles(endpoint, listOf(video.path), seasonPath)
                .onSuccess { moved += 1 }
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
            if (!CloudDrivePaths.isSameOrChild(entry.path, path)) continue
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
            .replace(Regex("""[<>:"|?*]"""), "_")
            .trim()
            .ifBlank { "Unknown" }

    private companion object {
        private const val MAX_DEPTH = 5
        private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "m2ts")
    }
}

object HeuristicCloudDriveVideoClassifier : CloudDriveVideoClassifier {
    override fun classify(file: CloudDriveFileInfo): CloudDriveVideoClassification =
        CloudDriveVideoClassification(
            showName = inferShowName(file),
            seasonNumber = inferSeasonNumber(file.name),
        )

    private fun inferShowName(file: CloudDriveFileInfo): String {
        val parent = CloudDrivePaths.parentPath(file.path)
            .substringAfterLast('/', "")
            .takeUnless { it.isGenericFolderName() }
        val stem = file.name.substringBeforeLast('.', file.name)
            .replace(leadingReleaseGroupRegex, "")
            .replace(tagRegex, " ")
        val episodeMatch = seasonEpisodeRegex.find(stem) ?: episodeNumberRegex.findAll(stem).lastOrNull()
        val fromFile = episodeMatch
            ?.let { stem.substring(0, it.range.first) }
            ?: stem
        return cleanupTitle(fromFile).ifBlank { cleanupTitle(parent.orEmpty()) }.ifBlank { "Unknown" }
    }

    private fun inferSeasonNumber(fileName: String): Int =
        seasonEpisodeRegex.find(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 1

    private fun cleanupTitle(value: String): String =
        value.replace(Regex("""[_・]+"""), " ")
            .replace(Regex("""\s*[-–—]\s*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun String.isGenericFolderName(): Boolean =
        lowercase().trim() in GENERIC_FOLDER_NAMES

    private val GENERIC_FOLDER_NAMES = setOf("download", "downloads", "library", "media", "video", "videos", "anime", "动漫", "下载", "下載")
    private val leadingReleaseGroupRegex = Regex("""^\s*(?:\[[^\]]+]|【[^】]+】|\([^)]+\))\s*""")
    private val tagRegex = Regex("""[\[\(【][^\]\)】]{1,64}[\]\)】]""")
    private val seasonEpisodeRegex = Regex("""(?i)(?:^|[\s._-])S(\d{1,2})E(\d{1,3})(?:[\s._-]|$)""")
    private val episodeNumberRegex = Regex("""(?i)(?:^|[\s._-])(?:EP?)?(\d{1,4})(?:v\d+)?(?:[\s._-]|$)""")
}

typealias DesktopCloudDriveLibraryOrganizer = CloudDriveLibraryOrganizer
