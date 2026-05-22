package com.miruplay.tv.clouddrive

data class CloudDriveOfflineFilesRequest(
    val urls: String,
    val targetFolder: String,
    val checkFolderAfterSeconds: Long,
)

data class CloudDriveUploadTarget(
    val parentPath: String,
    val remoteFileName: String,
    val remotePath: String,
)

object CloudDriveRequests {
    private const val DEFAULT_CHECK_FOLDER_AFTER_SECONDS = 30L

    fun offlineFiles(urls: List<String>, targetFolder: String): CloudDriveOfflineFilesRequest =
        CloudDriveOfflineFilesRequest(
            urls = urls.joinToString("\n"),
            targetFolder = targetFolder,
            checkFolderAfterSeconds = DEFAULT_CHECK_FOLDER_AFTER_SECONDS,
        )

    fun uploadTarget(parentPath: String, remoteFileName: String): CloudDriveUploadTarget {
        val normalizedParent = CloudDrivePaths.normalize(parentPath)
        return CloudDriveUploadTarget(
            parentPath = normalizedParent,
            remoteFileName = remoteFileName,
            remotePath = CloudDrivePaths.join(normalizedParent, remoteFileName),
        )
    }

    fun fileInfo(name: String, fullPathName: String, isDirectory: Boolean, size: Long = 0L): CloudDriveFileInfo =
        CloudDriveFileInfo(
            name = name.ifBlank { fullPathName.substringAfterLast('/') },
            path = fullPathName,
            isDirectory = isDirectory,
            size = size,
        )
}
