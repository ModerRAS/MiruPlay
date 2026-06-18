package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.logging.MiruLogRecord
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveLibraryMode
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.LocalLogSnapshot
import com.miruplay.tv.repository.LogUploadStatus
import com.miruplay.tv.repository.OtlpLogUploadConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiEnvelope<T>(
    val ok: Boolean,
    val data: T? = null,
    val error: String? = null,
)

@Serializable
data class ServerInfoDto(
    val appName: String,
    val deviceName: String,
    val port: Int,
    val localIps: List<String>,
    val startedAt: Long,
)

@Serializable
data class SourceRequest(
    val id: Long = 0L,
    val name: String,
    val type: String,
    val location: String,
    val displayName: String? = null,
    val username: String? = null,
    val password: String? = null,
)

@Serializable
data class SourceTestRequest(
    val type: String,
    val location: String,
    val displayName: String? = null,
    val username: String? = null,
    val password: String? = null,
)

@Serializable
data class LocalDirectoryDto(
    val path: String,
    val displayPath: String,
    val parentPath: String? = null,
    val entries: List<LocalDirectoryEntryDto>,
)

@Serializable
data class LocalDirectoryEntryDto(
    val name: String,
    val path: String,
    val canRead: Boolean,
)

@Serializable
data class CloudDriveDirectoryDto(
    val path: String,
    val displayPath: String,
    val parentPath: String? = null,
    val entries: List<CloudDriveDirectoryEntryDto>,
)

@Serializable
data class CloudDriveDirectoryEntryDto(
    val name: String,
    val path: String,
    val canRead: Boolean,
)

@Serializable
data class SourceTestResponse(
    val connected: Boolean,
    val message: String,
)

@Serializable
data class SourceScanResponse(
    val sourceId: Long,
    val animeName: String,
    val episodesFound: Int,
    val newEpisodes: Int,
    val updatedEpisodes: Int,
    val error: String? = null,
)

@Serializable
data class CloudDriveAutomationDto(
    val config: CloudDriveAutomationConfig,
    val subscriptions: List<RssSubscriptionInfo>,
    val tokenConfigured: Boolean,
    val passwordConfigured: Boolean = false,
)

@Serializable
data class CloudDriveConfigRequest(
    val endpointUrl: String,
    val username: String = "",
    val webDavSourceId: Long? = null,
    val inboxPath: String,
    val libraryPath: String,
    val libraryMode: CloudDriveLibraryMode = CloudDriveLibraryMode.ORGANIZED_LIBRARY,
    val intervalMinutes: Int = 30,
    val enabled: Boolean = false,
    val rssProxyEnabled: Boolean = false,
    val rssProxyHost: String = "",
    val rssProxyPort: Int = 1080,
)

@Serializable
data class NetworkProxyDto(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 1080,
)

@Serializable
data class NetworkProxyRequest(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 1080,
)

@Serializable
data class CloudDriveLoginRequest(
    val endpointUrl: String,
    val username: String,
    val password: String,
)

@Serializable
data class CloudDriveTokenRequest(
    val endpointUrl: String,
    val token: String,
)

@Serializable
data class CloudDriveTokenResponse(
    val rootDir: String,
    val friendlyName: String,
    val allowList: Boolean,
    val allowCreateFolder: Boolean,
    val allowCreateFile: Boolean,
    val allowWrite: Boolean,
    val allowMove: Boolean,
    val allowAddOfflineDownload: Boolean,
)

@Serializable
data class RssSubscriptionRequest(
    val id: Long = 0L,
    val name: String,
    val url: String,
    val filterRegex: String? = null,
    val enabled: Boolean = true,
)

@Serializable
data class CloudDriveRunResponse(
    val submitted: Int,
    val skipped: Int,
    val failed: Int,
    val organized: Int,
    val indexed: Int = 0,
    val scraped: Int = 0,
    val noMatch: Int = 0,
)

@Serializable
data class CloudDriveRunStatusDto(
    val status: String,
    val running: Boolean,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val summary: CloudDriveRunResponse? = null,
    val error: String? = null,
) {
    companion object {
        const val IDLE = "IDLE"
        const val RUNNING = "RUNNING"
        const val SUCCEEDED = "SUCCEEDED"
        const val FAILED = "FAILED"

        fun idle(): CloudDriveRunStatusDto =
            CloudDriveRunStatusDto(status = IDLE, running = false)
    }
}

@Serializable
data class LogUploadDto(
    val config: OtlpLogUploadConfigDto,
    val status: LogUploadStatusDto,
    val tokenConfigured: Boolean,
)

@Serializable
data class OtlpLogUploadConfigDto(
    val enabled: Boolean,
    val endpoint: String,
    val streamName: String,
    val lastUploadAt: Long,
    val lastUploadStatus: String?,
) {
    companion object {
        fun from(config: OtlpLogUploadConfig): OtlpLogUploadConfigDto =
            OtlpLogUploadConfigDto(
                enabled = config.enabled,
                endpoint = config.endpoint,
                streamName = config.streamName,
                lastUploadAt = config.lastUploadAt,
                lastUploadStatus = config.lastUploadStatus,
            )
    }
}

@Serializable
data class LogUploadStatusDto(
    val pendingCount: Int,
    val isUploading: Boolean,
    val lastUploadAt: Long,
    val lastUploadStatus: String?,
    val tokenConfigured: Boolean,
) {
    companion object {
        fun from(status: LogUploadStatus, tokenConfigured: Boolean = status.tokenConfigured): LogUploadStatusDto =
            LogUploadStatusDto(
                pendingCount = status.pendingCount,
                isUploading = status.isUploading,
                lastUploadAt = status.lastUploadAt,
                lastUploadStatus = status.lastUploadStatus,
                tokenConfigured = tokenConfigured,
            )
    }
}

@Serializable
data class LogUploadConfigRequest(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val streamName: String = "miruplay",
)

@Serializable
data class LogUploadTokenRequest(
    val token: String,
)

@Serializable
data class LocalLogsDto(
    val totalCount: Int,
    val returnedCount: Int,
    val truncatedCount: Int,
    val records: List<MiruLogRecord>,
) {
    companion object {
        fun from(snapshot: LocalLogSnapshot): LocalLogsDto =
            LocalLogsDto(
                totalCount = snapshot.totalCount,
                returnedCount = snapshot.returnedCount,
                truncatedCount = snapshot.truncatedCount,
                records = snapshot.records,
            )
    }
}

data class LocalLogDownload(
    val fileName: String,
    val contentType: String,
    val content: ByteArray,
)

@Serializable
data class MetadataSettingsDto(
    val bangumiTokenConfigured: Boolean,
)

@Serializable
data class BangumiArchiveDto(
    val available: Boolean,
    val hasSubjectData: Boolean,
    val latestName: String? = null,
    val latestCreatedAt: String? = null,
    val latestUpdatedAt: String? = null,
    val subjectFileSizeBytes: Long = 0L,
    val autoUpdateEnabled: Boolean = true,
    val autoUpdateIntervalDays: Int = 7,
    val isDownloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val lastError: String? = null,
)

@Serializable
data class BangumiTokenRequest(
    val token: String,
)

@Serializable
data class LibraryDto(
    val continueWatching: List<ContinueWatchingDto>,
    val recentlyAdded: List<Anime>,
    val allAnime: List<Anime>,
)

@Serializable
data class ContinueWatchingDto(
    val progressEpisodeId: String,
    val positionMs: Long,
    val lastWatched: Long,
    val playCount: Int,
    val episode: Episode?,
    val anime: Anime?,
)

@Serializable
data class AnimeDetailDto(
    val anime: Anime,
    val episodes: List<EpisodeWithProgressDto>,
)

@Serializable
data class EpisodeWithProgressDto(
    val episode: Episode,
    val progressMs: Long = 0L,
    val lastWatched: Long = 0L,
    val playCount: Int = 0,
)

@Serializable
data class PlayEpisodeRequest(
    val episodeId: String,
    val startPositionMs: Long? = null,
)

@Serializable
data class PlaybackCommandRequest(
    val command: String,
    val positionMs: Long? = null,
    val deltaMs: Long? = null,
    val speed: Float? = null,
)

@Serializable
data class PlaybackStatusDto(
    val state: String,
    val uri: String? = null,
    val mediaSourceId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val error: String? = null,
)

@Serializable
data class PlaybackDebugConfigRequest(
    val defaultBackend: String? = null,
    val requestedBackend: String? = null,
    val forcedSignalKind: String? = null,
    val libVlcHardwareMode: String? = null,
    val libVlcVoutMode: String? = null,
    val libVlcDisplayChroma: String? = null,
    val glFrameCaptureLabel: String? = null,
    val libVlcNativeSnapshotLabel: String? = null,
    val skipLibVlcStartupProbe: Boolean? = null,
)

@Serializable
data class PlaybackDebugConfigDto(
    val defaultBackend: String = "",
    val requestedBackend: String = "",
    val activeBackend: String = "",
    val forcedSignalKind: String? = null,
    val currentSignalKind: String? = null,
    val currentSignalLabel: String = "",
    val currentRuleKey: String = "",
    val fallbackReason: String? = null,
    val libVlcHardwareMode: String = "",
    val libVlcVoutMode: String = "",
    val libVlcDisplayChroma: String? = null,
    val pendingGlFrameCaptureLabel: String? = null,
    val pendingLibVlcNativeSnapshotLabel: String? = null,
    val skipLibVlcStartupProbe: Boolean = false,
)

@Serializable
data class NavigationCommand(
    val type: String,
    val payload: JsonElement? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class WebPlaybackSource(
    val uri: String,
    val mediaSourceId: String,
    val startPositionMs: Long = 0L,
    val episodeId: String? = null,
)

fun MediaSourceInfo.safeForApi(): MediaSourceInfo = copy(
    connectionInfo = connectionInfo.filterKeys { key ->
        !key.equals(MediaSourceInfoConventions.CONNECTION_PASSWORD, ignoreCase = true)
    },
)
