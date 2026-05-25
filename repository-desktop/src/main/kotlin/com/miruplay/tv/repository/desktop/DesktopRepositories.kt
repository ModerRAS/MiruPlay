package com.miruplay.tv.repository.desktop

import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.WebControlAccessManager
import java.nio.file.Path

class DesktopRepositories private constructor(
    val mediaSources: MediaSourceRepository,
    val progress: PlaybackProgressRepository,
    val playbackPreferences: PlaybackPreferencesRepository,
    val scanPreferences: ScanPreferencesRepository,
    val index: MediaIndexRepository,
    val metadata: MetadataRepository,
    val cloudDriveAutomation: CloudDriveAutomationRepository,
    val credentials: AppCredentialStore,
    val logUpload: LogUploadRepository,
    val webControlAccess: WebControlAccessManager,
) {
    companion object {
        fun fileBacked(storePath: Path = DesktopRepositoryPaths.defaultStorePath()): DesktopRepositories {
            val store = DesktopRepositoryStore(storePath)
            return DesktopRepositories(
                mediaSources = FileBackedMediaSourceRepository(store),
                progress = FileBackedProgressRepository(store),
                playbackPreferences = FileBackedPlaybackPreferencesRepository(store),
                scanPreferences = FileBackedScanPreferencesRepository(store),
                index = FileBackedMediaIndexRepository(store),
                metadata = FileBackedMetadataRepository(store),
                cloudDriveAutomation = FileBackedCloudDriveAutomationRepository(store),
                credentials = FileBackedCredentialStore(store),
                logUpload = FileBackedLogUploadRepository(store),
                webControlAccess = FileBackedWebControlAccessManager(store),
            )
        }
    }
}
