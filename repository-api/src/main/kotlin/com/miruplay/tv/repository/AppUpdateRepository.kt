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
    val channel: UpdateChannel,
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

enum class UpdateChannel {
    ALPHA,
    BETA,
    STABLE;

    val id: String get() = name.lowercase()

    companion object {
        fun fromId(value: String?): UpdateChannel? =
            entries.firstOrNull { it.id == value?.trim()?.lowercase() }
    }
}

/** 持久化的更新渠道选择；data 模块提供实现，App/WebUI 共享同一存储 */
interface AppUpdateChannelStore {
    var updateChannel: UpdateChannel

    /** 渠道被其他表面（WebAPI/WebUI）修改时回调；默认不通知 */
    fun addChannelChangeListener(onChanged: (UpdateChannel) -> Unit): java.io.Closeable =
        java.io.Closeable { }
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
