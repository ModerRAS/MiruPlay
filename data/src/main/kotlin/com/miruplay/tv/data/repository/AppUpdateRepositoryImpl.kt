package com.miruplay.tv.data.repository

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.repository.AppUpdateCheck
import com.miruplay.tv.repository.AppUpdateDownloadProgress
import com.miruplay.tv.repository.AppUpdateInfo
import com.miruplay.tv.repository.AppUpdateInstallLaunch
import com.miruplay.tv.repository.AppUpdateRepository
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl internal constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    private val latestReleaseApiUrl: String,
) : AppUpdateRepository {

    private val json = Json { ignoreUnknownKeys = true }

    @Inject
    constructor(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        cloudDriveRepository: CloudDriveAutomationRepository,
    ) : this(
        context = context,
        okHttpClient = okHttpClient,
        cloudDriveRepository = cloudDriveRepository,
        latestReleaseApiUrl = LATEST_RELEASE_API_URL,
    )

    override suspend fun checkLatestUpdate(): Result<AppUpdateCheck> = withContext(Dispatchers.IO) {
        val currentVersionName = currentVersionName()
        val currentVersionCode = currentVersionCode()
        MiruLog.i(
            TAG,
            "Checking GitHub app update",
            mapOf(
                "current_version_name" to currentVersionName,
                "current_version_code" to currentVersionCode.toString(),
                "release_api_url" to latestReleaseApiUrl,
            )
        )

        val request = Request.Builder()
            .url(latestReleaseApiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "MiruPlay/$currentVersionName")
            .build()

        try {
            githubApiClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    MiruLog.w(
                        TAG,
                        "GitHub app update check failed",
                        attributes = mapOf(
                            "http_code" to response.code.toString(),
                            "http_message" to response.message,
                        )
                    )
                    return@withContext Result.failure(
                        AppError.NetworkError.HttpError(response.code, response.message)
                    )
                }
                val responseBody = response.body?.string().orEmpty()
                if (responseBody.isBlank()) {
                    return@withContext Result.failure(AppError.AppUpdateError.NoReleaseFound)
                }
                val latest = GitHubAppUpdateMapper.parseLatestRelease(responseBody, json)
                    ?: return@withContext Result.failure(AppError.AppUpdateError.NoInstallableApk)
                val updateAvailable = GitHubAppUpdateMapper.isNewerThanCurrent(
                    latest = latest,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                )
                MiruLog.i(
                    TAG,
                    "GitHub app update check completed",
                    mapOf(
                        "latest_version_name" to latest.versionName,
                        "latest_version_code" to latest.versionCode.orEmptyString(),
                        "asset_name" to latest.assetName,
                        "asset_size_bytes" to latest.assetSizeBytes.toString(),
                        "update_available" to updateAvailable.toString(),
                    )
                )
                Result.success(
                    AppUpdateCheck(
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode,
                        latest = latest,
                        updateAvailable = updateAvailable,
                    )
                )
            }
        } catch (error: Exception) {
            MiruLog.w(TAG, "GitHub app update check threw", error)
            Result.failure(AppError.NetworkError.ServerUnreachable(latestReleaseApiUrl))
        }
    }

    override suspend fun downloadAndLaunchInstaller(
        update: AppUpdateInfo,
        onProgress: (AppUpdateDownloadProgress) -> Unit,
    ): Result<AppUpdateInstallLaunch> = withContext(Dispatchers.IO) {
        MiruLog.i(
            TAG,
            "Downloading app update APK",
            mapOf(
                "version_name" to update.versionName,
                "asset_name" to update.assetName,
                "asset_size_bytes" to update.assetSizeBytes.toString(),
                "download_url" to update.downloadUrl,
            )
        )
        val apkFile = try {
            downloadApk(update, onProgress)
        } catch (error: Exception) {
            MiruLog.w(TAG, "App update APK download failed", error)
            return@withContext Result.failure(
                AppError.AppUpdateError.DownloadFailed(error.message ?: error.javaClass.simpleName)
            )
        }

        if (!canRequestPackageInstalls()) {
            MiruLog.i(TAG, "App update install permission required")
            return@withContext Result.success(AppUpdateInstallLaunch.INSTALL_PERMISSION_REQUIRED)
        }

        launchInstaller(apkFile).also { result ->
            if (result is Result.Success) {
                MiruLog.i(
                    TAG,
                    "App update installer opened",
                    mapOf(
                        "version_name" to update.versionName,
                        "apk_path" to apkFile.absolutePath,
                        "apk_size_bytes" to apkFile.length().toString(),
                    )
                )
            }
        }
    }

    override fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings(): Result<Unit> {
        if (canRequestPackageInstalls()) return Result.success(Unit)
        val packageUri = Uri.parse("package:${context.packageName}")
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            Result.success(Unit)
        } catch (error: ActivityNotFoundException) {
            MiruLog.w(TAG, "Specific install permission settings unavailable", error)
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                Result.success(Unit)
            } catch (fallbackError: Exception) {
                Result.failure(
                    AppError.AppUpdateError.InstallIntentFailed(
                        fallbackError.message ?: fallbackError.javaClass.simpleName
                    )
                )
            }
        }
    }

    private fun launchInstaller(apkFile: File): Result<AppUpdateInstallLaunch> {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.app_update_file_provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        grantReadUriPermission(intent, uri)
        return try {
            context.startActivity(intent)
            Result.success(AppUpdateInstallLaunch.INSTALLER_OPENED)
        } catch (error: Exception) {
            MiruLog.w(TAG, "App update installer launch failed", error)
            Result.failure(
                AppError.AppUpdateError.InstallIntentFailed(
                    error.message ?: error.javaClass.simpleName
                )
            )
        }
    }

    private fun grantReadUriPermission(intent: Intent, uri: Uri) {
        context.packageManager
            .queryIntentActivities(intent, 0)
            .forEach { info ->
                context.grantUriPermission(
                    info.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
    }

    private suspend fun downloadApk(
        update: AppUpdateInfo,
        onProgress: (AppUpdateDownloadProgress) -> Unit,
    ): File {
        val request = Request.Builder()
            .url(update.downloadUrl)
            .header("User-Agent", "MiruPlay/${currentVersionName()}")
            .build()
        val updateDir = File(context.cacheDir, "app-updates").apply { mkdirs() }
        updateDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".apk")) file.delete()
        }
        val safeVersion = update.versionName.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        val target = File(updateDir, "miruplay-$safeVersion.apk")
        val partial = File(updateDir, "${target.name}.part")
        if (partial.exists()) partial.delete()

        githubDownloadClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} ${response.message}")
            }
            val body = response.body ?: throw IllegalStateException("empty response body")
            val total = body.contentLength().takeIf { it > 0L } ?: update.assetSizeBytes.takeIf { it > 0L }
            onProgress(AppUpdateDownloadProgress(downloadedBytes = 0L, totalBytes = total))
            var downloaded = 0L
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(AppUpdateDownloadProgress(downloadedBytes = downloaded, totalBytes = total))
                    }
                }
            }
        }

        if (target.exists()) target.delete()
        check(partial.renameTo(target)) { "cannot finalize APK download" }
        return target
    }

    private suspend fun githubApiClient(): OkHttpClient =
        okHttpClient.newBuilder()
            .proxy(currentProxy())
            .build()

    private suspend fun githubDownloadClient(): OkHttpClient =
        okHttpClient.newBuilder()
            .connectTimeout(APK_DOWNLOAD_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(APK_DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(APK_DOWNLOAD_CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .proxy(currentProxy())
            .build()

    private suspend fun currentProxy(): Proxy {
        val config = cloudDriveRepository.getConfig().getOrNull() ?: return Proxy.NO_PROXY
        if (!config.rssProxyEnabled || config.rssProxyHost.isBlank()) return Proxy.NO_PROXY
        return Proxy(
            Proxy.Type.HTTP,
            InetSocketAddress(
                config.rssProxyHost.trim(),
                config.rssProxyPort.coerceIn(1, 65_535),
            )
        )
    }

    private fun currentVersionName(): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "unknown" }

    private fun currentVersionCode(): Long =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        }.getOrDefault(0L)

    private fun Long?.orEmptyString(): String = this?.toString().orEmpty()

    companion object {
        private const val TAG = "AppUpdateRepository"
        private const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/ModerRAS/MiruPlay/releases/latest"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val APK_DOWNLOAD_CONNECT_TIMEOUT_SECONDS = 120L
        private const val APK_DOWNLOAD_READ_TIMEOUT_SECONDS = 120L
        private const val APK_DOWNLOAD_CALL_TIMEOUT_MINUTES = 10L
    }
}

internal object GitHubAppUpdateMapper {
    fun parseLatestRelease(
        responseBody: String,
        json: Json = Json { ignoreUnknownKeys = true },
    ): AppUpdateInfo? {
        val root = json.parseToJsonElement(responseBody).jsonObject
        if (root["draft"]?.jsonPrimitive?.booleanOrNull == true) return null
        val tagName = root.string("tag_name")
        val releaseName = root.string("name").ifBlank { tagName }
        val releaseUrl = root.string("html_url")
        val publishedAt = root.string("published_at")
        val asset = root["assets"]
            ?.jsonArray
            ?.mapNotNull { it.jsonObject }
            ?.filter { assetObject ->
                assetObject.string("name").endsWith(".apk", ignoreCase = true)
            }
            ?.sortedWith(
                compareByDescending<kotlinx.serialization.json.JsonObject> {
                    it.string("name").contains("release", ignoreCase = true)
                }.thenBy { it.string("name") }
            )
            ?.firstOrNull()
            ?: return null

        val versionName = normalizeReleaseVersionName(tagName.ifBlank { releaseName })
        return AppUpdateInfo(
            versionName = versionName,
            versionCode = versionCodeFromName(versionName),
            releaseName = releaseName,
            tagName = tagName,
            publishedAt = publishedAt,
            releaseUrl = releaseUrl,
            assetName = asset.string("name"),
            assetSizeBytes = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            downloadUrl = asset.string("browser_download_url"),
        ).takeIf { it.downloadUrl.isNotBlank() }
    }

    fun isNewerThanCurrent(
        latest: AppUpdateInfo,
        currentVersionName: String,
        currentVersionCode: Long,
    ): Boolean =
        latest.versionCode?.let { it > currentVersionCode }
            ?: (latest.versionName.isNotBlank() && latest.versionName != currentVersionName)

    fun normalizeReleaseVersionName(value: String): String =
        value.trim()
            .removePrefix("nightly-")
            .removePrefix("v")
            .removePrefix("V")

    fun versionCodeFromName(versionName: String): Long? {
        val normalized = normalizeReleaseVersionName(versionName)
        val dateMatch = Regex("""^(\d{4})\.(\d{2})\.(\d{2})$""").matchEntire(normalized)
        if (dateMatch != null) {
            return dateMatch.groupValues.drop(1).joinToString("").toLongOrNull()
        }
        return null
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}
