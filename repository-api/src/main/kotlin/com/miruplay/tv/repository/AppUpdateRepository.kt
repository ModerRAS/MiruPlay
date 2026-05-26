package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Long?,
    val releaseName: String,
    val tagName: String,
    val publishedAt: String,
    val releaseUrl: String,
    val assetName: String,
    val assetSizeBytes: Long,
    val downloadUrl: String,
)

data class AppUpdateCheck(
    val currentVersionName: String,
    val currentVersionCode: Long,
    val latest: AppUpdateInfo,
    val updateAvailable: Boolean,
)

data class AppUpdateDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
) {
    val percent: Int? =
        totalBytes?.takeIf { it > 0L }?.let { ((downloadedBytes * 100) / it).toInt().coerceIn(0, 100) }
}

enum class AppUpdateInstallLaunch {
    INSTALLER_OPENED,
    INSTALL_PERMISSION_REQUIRED,
}

interface AppUpdateRepository {
    suspend fun checkLatestUpdate(): Result<AppUpdateCheck>

    suspend fun downloadAndLaunchInstaller(
        update: AppUpdateInfo,
        onProgress: (AppUpdateDownloadProgress) -> Unit,
    ): Result<AppUpdateInstallLaunch>

    fun canRequestPackageInstalls(): Boolean

    fun openInstallPermissionSettings(): Result<Unit>
}
