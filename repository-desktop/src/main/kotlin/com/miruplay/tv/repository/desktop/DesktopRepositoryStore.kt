package com.miruplay.tv.repository.desktop

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.MediaIndexEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
}

@Serializable
internal data class DesktopRepositoryState(
    val nextSourceId: Long = 1L,
    val nextRssSubscriptionId: Long = 1L,
    val nextRssDownloadTaskId: Long = 1L,
    val mediaSources: List<MediaSourceInfo> = emptyList(),
    val progress: List<ProgressRecord> = emptyList(),
    val index: List<MediaIndexEntry> = emptyList(),
    val indexBatchUndo: List<MediaIndexBatchUndoState> = emptyList(),
    val cloudDriveConfig: CloudDriveAutomationConfig = CloudDriveAutomationConfig(),
    val rssSubscriptions: List<RssSubscriptionInfo> = emptyList(),
    val rssProcessedItems: List<RssProcessedItemInfo> = emptyList(),
    val rssDownloadTasks: List<RssDownloadTaskInfo> = emptyList(),
    val cloudDriveToken: String? = null,
    val cloudDrivePassword: String? = null,
    val bangumiAccessToken: String? = null,
)

@Serializable
internal data class MediaIndexBatchUndoState(
    val sourceId: Long,
    val savedAt: Long = 0L,
    val entries: List<MediaIndexEntry> = emptyList(),
)
