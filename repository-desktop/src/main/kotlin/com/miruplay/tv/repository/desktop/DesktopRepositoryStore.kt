package com.miruplay.tv.repository.desktop

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.SubtitleLanguagePreference
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.AppModePreferencesRepository
import com.miruplay.tv.repository.ScanPreferencesSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class DesktopRepositoryStore(
    private val storePath: Path,
) {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun <T> read(block: (DesktopRepositoryState) -> T): T = mutex.withLock {
        block(readState())
    }

    suspend fun <T> update(block: (DesktopRepositoryState) -> Pair<DesktopRepositoryState, T>): T =
        mutex.withLock {
            val (nextState, result) = block(readState())
            writeState(nextState)
            result
        }

    private fun readState(): DesktopRepositoryState {
        if (!Files.isRegularFile(storePath)) return DesktopRepositoryState()
        return runCatching {
            json.decodeFromString<DesktopRepositoryState>(Files.readString(storePath))
        }.getOrDefault(DesktopRepositoryState())
    }

    private fun writeState(state: DesktopRepositoryState) {
        Files.createDirectories(storePath.parent)
        val tempPath = storePath.resolveSibling("${storePath.fileName}.tmp")
        Files.writeString(tempPath, json.encodeToString(state))
        Files.move(tempPath, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun path(): Path = storePath
}

@Serializable
internal data class DesktopRepositoryState(
    val nextSourceId: Long = 1L,
    val nextRssSubscriptionId: Long = 1L,
    val nextRssDownloadTaskId: Long = 1L,
    val mediaSources: List<MediaSourceInfo> = emptyList(),
    val progress: List<ProgressRecord> = emptyList(),
    val index: List<MediaIndexEntry> = emptyList(),
    val animeMetadata: List<Anime> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val indexBatchUndo: List<MediaIndexBatchUndoState> = emptyList(),
    val playbackEndAction: PlaybackEndAction = PlaybackEndAction.RETURN_TO_DETAIL,
    val preferredSubtitleLanguage: SubtitleLanguagePreference = SubtitleLanguagePreference.AUTO,
    val formatAwareToneMappingPreferences: FormatAwareToneMappingPreferences = FormatAwareToneMappingPreferences(),
    val scanPreferences: ScanPreferencesSnapshot = ScanPreferencesSnapshot(),
    val cloudDriveConfig: CloudDriveAutomationConfig = CloudDriveAutomationConfig(),
    val rssSubscriptions: List<RssSubscriptionInfo> = emptyList(),
    val rssProcessedItems: List<RssProcessedItemInfo> = emptyList(),
    val rssDownloadTasks: List<RssDownloadTaskInfo> = emptyList(),
    val cloudDriveToken: String? = null,
    val cloudDrivePassword: String? = null,
    val bangumiAccessToken: String? = null,
    val tmdbAccessToken: String? = null,
    val tmdbApiBaseUrlOverride: String? = null,
    val otlpAccessToken: String? = null,
    val otlpEnabled: Boolean = false,
    val otlpEndpoint: String = "",
    val otlpStreamName: String = "miruplay",
    val otlpLastUploadAt: Long = 0L,
    val otlpLastUploadStatus: String? = null,
    val webControlEnabled: Boolean = false,
    val webControlAccessToken: String? = null,
    val appModeStorageValue: String? = null,
    val hasCompletedAppModeSelection: Boolean = false,
)

@Serializable
internal data class MediaIndexBatchUndoState(
    val sourceId: Long,
    val savedAt: Long = 0L,
    val entries: List<MediaIndexEntry> = emptyList(),
)

internal fun generateDesktopWebControlAccessToken(): String {
    val bytes = ByteArray(WEB_CONTROL_TOKEN_BYTES)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes)
}

private const val WEB_CONTROL_TOKEN_BYTES = 24
