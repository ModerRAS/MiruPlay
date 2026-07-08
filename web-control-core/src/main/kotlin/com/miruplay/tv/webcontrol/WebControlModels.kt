package com.miruplay.tv.webcontrol

import com.miruplay.tv.core.common.logging.MiruLogRecord
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveLibraryMode
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaRecognitionMode
import com.miruplay.tv.model.MlipMetadataMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.PosterWallArrangement
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
    val versionName: String = "",
    val versionCode: Long = 0L,
    val packageName: String = "",
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
    val contentMode: MediaContentMode = MediaContentMode.ANIME,
    val recognitionMode: MediaRecognitionMode = MediaRecognitionMode.DIRECTORY,
    val mlipMetadataMode: MlipMetadataMode = MlipMetadataMode.LIBRARY_DB_LOCAL_PRIORITY,
    val disableOnlineMetadata: Boolean = false,
)

@Serializable
data class SourceTestRequest(
    val type: String,
    val location: String,
    val displayName: String? = null,
    val username: String? = null,
    val password: String? = null,
    val contentMode: MediaContentMode = MediaContentMode.ANIME,
    val recognitionMode: MediaRecognitionMode = MediaRecognitionMode.DIRECTORY,
    val mlipMetadataMode: MlipMetadataMode = MlipMetadataMode.LIBRARY_DB_LOCAL_PRIORITY,
    val disableOnlineMetadata: Boolean = false,
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
    val curlCommand: String = "",
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
    val tmdbTokenConfigured: Boolean = false,
)

@Serializable
data class TmdbTokenRequest(
    val token: String,
)

@Serializable
data class ScanSettingsDto(
    val autoScanEnabled: Boolean,
    val autoScanIntervalHours: Int,
    val lastScanAt: Long,
    val mergeSameAnimeEnabled: Boolean,
    val posterWallArrangement: PosterWallArrangement,
    val currentAppMode: String?,
    val appModeOptions: List<String> = listOf("anime", "drama"),
    val posterWallArrangementOptions: List<PosterWallArrangement> = PosterWallArrangement.entries,
    val autoScanIntervalOptionsHours: List<Int> = listOf(1, 6, 12, 24),
)

@Serializable
data class ScanSettingsRequest(
    val autoScanEnabled: Boolean? = null,
    val autoScanIntervalHours: Int? = null,
    val mergeSameAnimeEnabled: Boolean? = null,
    val posterWallArrangement: PosterWallArrangement? = null,
    val currentAppMode: String? = null,
)

@Serializable
data class PlaybackSettingsDto(
    val endAction: String,
    val formatAwareToneMapping: FormatAwareToneMappingPreferences,
    val endActionOptions: List<String> = listOf("return_to_detail", "play_next_episode"),
)

@Serializable
data class PlaybackSettingsRequest(
    val endAction: String? = null,
    val formatAwareToneMapping: FormatAwareToneMappingPreferences? = null,
)

@Serializable
data class WebControlAccessDto(
    val enabled: Boolean,
    val accessToken: String,
    val urls: List<String>,
)

@Serializable
data class WebControlAccessRequest(
    val enabled: Boolean? = null,
)

@Serializable
data class AppUpdateInfoDto(
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

@Serializable
data class AppUpdateDto(
    val currentVersionName: String,
    val currentVersionCode: Long,
    val latest: AppUpdateInfoDto? = null,
    val updateAvailable: Boolean = false,
    val lastCheckedAt: Long = 0L,
    val lastError: String? = null,
    val canRequestPackageInstalls: Boolean = false,
)

@Serializable
data class AppUpdateDownloadResponse(
    val installLaunch: String,
    val error: String? = null,
)

@Serializable
data class AppControlRequest(
    val action: String,
)

@Serializable
data class AppControlDto(
    val action: String,
    val accepted: Boolean = false,
    val message: String? = null,
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
    val clearSessionToneMapping: Boolean? = null,
    val sessionToneMappingPreset: String? = null,
    val sessionPeakDetectionStrategy: String? = null,
    val sessionGamutMappingMode: String? = null,
    val embeddedMpvVo: String? = null,
    val embeddedMpvHwdec: String? = null,
    val libVlcHardwareMode: String? = null,
    val libVlcVoutMode: String? = null,
    val libVlcDisplayChroma: String? = null,
    val glFrameCaptureLabel: String? = null,
    val libVlcNativeSnapshotLabel: String? = null,
    val skipLibVlcStartupProbe: Boolean? = null,
    val skipLibVlcStartupOptions: Boolean? = null,
)

@Serializable
data class PlaybackDebugCurrentToneMappingDto(
    val enabled: Boolean = false,
    val curvePreset: String = "",
    val peakDetectionStrategy: String = "",
    val gamutMappingMode: String? = null,
    val targetSdrNits: Int = 0,
    val contrastRecovery: Int = 0,
    val saturationRecovery: Int = 0,
    val highlightCompression: Int = 0,
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
    val currentToneMapping: PlaybackDebugCurrentToneMappingDto = PlaybackDebugCurrentToneMappingDto(),
    val fallbackReason: String? = null,
    val embeddedMpvVo: String? = null,
    val embeddedMpvHwdec: String? = null,
    val effectiveEmbeddedMpvVo: String? = null,
    val effectiveEmbeddedMpvHwdec: String? = null,
    val libVlcHardwareMode: String = "",
    val libVlcVoutMode: String = "",
    val libVlcDisplayChroma: String? = null,
    val pendingGlFrameCaptureLabel: String? = null,
    val pendingLibVlcNativeSnapshotLabel: String? = null,
    val skipLibVlcStartupProbe: Boolean = false,
    val skipLibVlcStartupOptions: Boolean = false,
)

@Serializable
data class PlaybackClockSampleDto(
    val monotonicTimestampMs: Long = 0L,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val paused: Boolean = false,
    val eofReached: Boolean = false,
)

@Serializable
data class PlaybackClockSamplesDto(
    val activeBackend: String = "",
    val requestedBackend: String = "",
    val currentSignalKind: String? = null,
    val currentRuleKey: String = "",
    val samples: List<PlaybackClockSampleDto> = emptyList(),
)

@Serializable
data class PlaybackNativePropertyDto(
    val name: String = "",
    val value: String? = null,
)

@Serializable
data class PlaybackNativeLogMessageDto(
    val observedAtElapsedRealtimeMs: Long = 0L,
    val prefix: String = "",
    val level: Int = 0,
    val text: String = "",
)

@Serializable
data class PlaybackNativeDiagnosticsDto(
    val activeBackend: String = "",
    val requestedBackend: String = "",
    val available: Boolean = false,
    val collectedAtElapsedRealtimeMs: Long = 0L,
    val surfaceAttached: Boolean = false,
    val pendingStartPositionMs: Long? = null,
    val properties: List<PlaybackNativePropertyDto> = emptyList(),
    val recentLogMessages: List<PlaybackNativeLogMessageDto> = emptyList(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class PlaybackNativeProfileRequest(
    val durationMs: Long = 8_000L,
    val sampleFrequency: Int = 1_000,
    val event: String = "task-clock:u",
    val callGraph: String = "dwarf",
    val traceOffCpu: Boolean = false,
    val sampleTids: List<Int> = emptyList(),
)

@Serializable
data class PlaybackNativeProfileCaptureDto(
    val fileName: String = "",
    val generatedAtMs: Long = 0L,
    val durationMs: Long = 0L,
    val sampleFrequency: Int = 0,
    val event: String = "",
    val callGraph: String = "",
    val traceOffCpu: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val notes: List<String> = emptyList(),
)

@Serializable
data class PlaybackProfileRequest(
    val durationMs: Long = 10_000L,
    val intervalMs: Long = 20L,
    val maxStacks: Int = 120,
    val includeThreadNames: List<String> = emptyList(),
    val excludeThreadNames: List<String> = emptyList(),
)

@Serializable
data class PlaybackProfileStackDto(
    val stack: String = "",
    val samples: Int = 0,
)

@Serializable
data class PlaybackProfileThreadDto(
    val threadName: String = "",
    val samples: Int = 0,
    val runnableSamples: Int = 0,
    val nativeTopFrameSamples: Int = 0,
    val topStack: String = "",
)

@Serializable
data class PlaybackProfileReportDto(
    val durationMs: Long = 0L,
    val intervalMs: Long = 0L,
    val samplePasses: Int = 0,
    val sampledThreadCount: Int = 0,
    val totalStackSamples: Int = 0,
    val trimmedStackCount: Int = 0,
    val collapsedStacks: List<PlaybackProfileStackDto> = emptyList(),
    val collapsedText: String = "",
    val threadSummaries: List<PlaybackProfileThreadDto> = emptyList(),
    val notes: List<String> = emptyList(),
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
