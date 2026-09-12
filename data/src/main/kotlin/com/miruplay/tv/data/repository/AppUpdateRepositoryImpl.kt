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
import com.miruplay.tv.repository.AppUpdateChannelStore
import com.miruplay.tv.repository.AppUpdateDownloadProgress
import com.miruplay.tv.repository.AppUpdateInfo
import com.miruplay.tv.repository.AppUpdateInstallLaunch
import com.miruplay.tv.repository.AppUpdateRepository
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.UpdateChannel
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
import okhttp3.Response
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
    private val channelStore: AppUpdateChannelStore,
    private val updateManifestUrl: String,
    private val latestReleaseApiUrl: String,
) : AppUpdateRepository {

    private val json = Json { ignoreUnknownKeys = true }

    @Inject
    constructor(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        cloudDriveRepository: CloudDriveAutomationRepository,
        channelStore: AppUpdateChannelStore,
    ) : this(
        context = context,
        okHttpClient = okHttpClient,
        cloudDriveRepository = cloudDriveRepository,
        channelStore = channelStore,
        updateManifestUrl = UPDATE_MANIFEST_URL,
        latestReleaseApiUrl = LATEST_RELEASE_API_URL,
    )

    override suspend fun checkLatestUpdate(): Result<AppUpdateCheck> = withContext(Dispatchers.IO) {
        val channel = channelStore.updateChannel
        val currentVersionName = currentVersionName()
        val currentVersionCode = currentVersionCode()
        MiruLog.i(
            TAG,
            "Checking app update",
            mapOf(
                "current_version_name" to currentVersionName,
                "current_version_code" to currentVersionCode.toString(),
                "channel" to channel.id,
                "update_manifest_url" to updateManifestUrl,
            )
        )

        val latestWithSource = when (val manifest = fetchLatestUpdate(updateManifestUrl, githubApi = false, channel = channel)) {
            is Result.Success -> manifest.data to "manifest"
            is Result.Error -> {
                MiruLog.w(
                    TAG,
                    "Update manifest unavailable; falling back to GitHub API",
                    attributes = mapOf("manifest_error" to manifest.error.toString()),
                )
                // 仅网络故障才走 fallback；渠道缺条目等内容性失败直接上报，
                // 否则渠道提示会被 fallback 结果掩盖
                if (manifest.error is AppError.NetworkError) {
                    when (val fallback = fetchLatestUpdate(latestReleaseApiUrl, githubApi = true, channel = channel)) {
                        is Result.Success -> fallback.data to "github_api"
                        is Result.Error -> return@withContext Result.failure(fallback.error)
                    }
                } else {
                    return@withContext Result.failure(manifest.error)
                }
            }
        }
        val latest = latestWithSource.first
        val updateAvailable = GitHubAppUpdateMapper.isNewerThanCurrent(
            latest = latest,
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
        )
        MiruLog.i(
            TAG,
            "App update check completed",
            mapOf(
                "source" to latestWithSource.second,
                "channel" to channel.id,
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
                channel = channel,
                latest = latest,
                updateAvailable = updateAvailable,
            )
        )
    }

    private suspend fun fetchLatestUpdate(url: String, githubApi: Boolean, channel: UpdateChannel): Result<AppUpdateInfo> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", if (githubApi) "application/vnd.github+json" else "application/json")
            .header("User-Agent", "MiruPlay/${currentVersionName()}")
            .apply {
                if (githubApi) {
                    header("X-GitHub-Api-Version", "2022-11-28")
                } else {
                    header("Cache-Control", "no-cache")
                }
            }
            .build()
        return try {
            githubClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val failureDetail = response.failureDetail()
                    MiruLog.w(
                        TAG,
                        "App update request failed",
                        attributes = mapOf(
                            "url" to url,
                            "http_code" to response.code.toString(),
                            "http_message" to response.message,
                            "http_failure_detail" to failureDetail,
                        )
                    )
                    return@use Result.failure(AppError.NetworkError.HttpError(response.code, failureDetail))
                }
                val responseBody = response.body?.string().orEmpty()
                if (responseBody.isBlank()) {
                    return@use Result.failure(AppError.AppUpdateError.NoReleaseFound)
                }
                val parsed: ChannelManifestResult = when (githubApi) {
                    true -> GitHubAppUpdateMapper.parseLatestRelease(responseBody, json)
                        ?.let { ChannelManifestResult.Found(it) }
                        ?: ChannelManifestResult.Malformed
                    false -> GitHubAppUpdateMapper.parseChannelManifest(responseBody, channel)
                }
                return@use when (parsed) {
                    is ChannelManifestResult.Found -> Result.success(parsed.info)
                    is ChannelManifestResult.ChannelMissing ->
                        Result.failure(AppError.AppUpdateError.ChannelNoRelease(channel.id))
                    is ChannelManifestResult.Malformed ->
                        Result.failure(AppError.AppUpdateError.NoInstallableApk)
                }
            }
        } catch (error: Exception) {
            MiruLog.w(TAG, "App update request threw", error, mapOf("url" to url))
            Result.failure(AppError.NetworkError.ServerUnreachable(url))
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
                throw IllegalStateException("HTTP ${response.code}: ${response.failureDetail()}")
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

    private suspend fun githubClient(): OkHttpClient =
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

    private fun Response.failureDetail(): String {
        val bodyText = runCatching { body?.string().orEmpty() }
            .getOrDefault("")
            .trim()
        val detail = bodyText.ifBlank { message.ifBlank { "HTTP $code" } }
        return detail.take(MAX_HTTP_ERROR_BODY_CHARS)
            .let { if (detail.length > MAX_HTTP_ERROR_BODY_CHARS) "$it..." else it }
    }

    companion object {
        private const val TAG = "AppUpdateRepository"
        private const val UPDATE_MANIFEST_URL =
            "https://github.com/ModerRAS/MiruPlay/releases/latest/download/latest.json"
        private const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/ModerRAS/MiruPlay/releases/latest"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val APK_DOWNLOAD_CONNECT_TIMEOUT_SECONDS = 120L
        private const val APK_DOWNLOAD_READ_TIMEOUT_SECONDS = 120L
        private const val APK_DOWNLOAD_CALL_TIMEOUT_MINUTES = 10L
        private const val MAX_HTTP_ERROR_BODY_CHARS = 4_096
    }
}

internal sealed class ChannelManifestResult {
    data class Found(val info: AppUpdateInfo) : ChannelManifestResult()
    data object ChannelMissing : ChannelManifestResult()
    data object Malformed : ChannelManifestResult()
}

internal object GitHubAppUpdateMapper {

    /**
     * 滚动式三渠道 manifest：顶层 = 旧 schema（host release 信息），
     * channels = { alpha|beta|stable: {tag_name, version_code, asset_name, asset_size,
     * download_url, published_at, html_url} }。
     * 所选渠道无条目时返回 ChannelMissing。
     */
    fun parseChannelManifest(
        responseBody: String,
        channel: UpdateChannel,
        json: Json = Json { ignoreUnknownKeys = true },
    ): ChannelManifestResult {
        val root = runCatching { json.parseToJsonElement(responseBody).jsonObject }.getOrNull()
            ?: return ChannelManifestResult.Malformed
        val channelsObject = root["channels"]
            ?.takeIf { it is kotlinx.serialization.json.JsonObject }
            ?.jsonObject
            ?: run {
                // 旧 manifest（无 channels 字段）退回顶层旧行为
                if (root.containsKey("channels")) return ChannelManifestResult.Malformed
                return parseLatestRelease(responseBody, json)
                    ?.let { ChannelManifestResult.Found(it) }
                    ?: ChannelManifestResult.Malformed
            }
        val entry = channelsObject[channel.id]
            ?.takeIf { it is kotlinx.serialization.json.JsonObject }
            ?.jsonObject
            ?: return ChannelManifestResult.ChannelMissing
        val tagName = entry.string("tag_name")
        val downloadUrl = entry.string("download_url")
        if (tagName.isBlank() || downloadUrl.isBlank()) return ChannelManifestResult.Malformed
        return ChannelManifestResult.Found(
            AppUpdateInfo(
                versionName = normalizeReleaseVersionName(tagName),
                versionCode = entry["version_code"]?.jsonPrimitive?.longOrNull,
                releaseName = tagName,
                tagName = tagName,
                publishedAt = entry.string("published_at"),
                releaseUrl = entry.string("html_url"),
                assetName = entry.string("asset_name"),
                assetSizeBytes = entry["asset_size"]?.jsonPrimitive?.longOrNull ?: 0L,
                downloadUrl = downloadUrl,
            )
        )
    }

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
            versionCode = root["version_code"]?.jsonPrimitive?.longOrNull
                ?: versionCodeFromName(versionName),
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
