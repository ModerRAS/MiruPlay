package com.miruplay.tv.repository.desktop

import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.AppCredentialStore
import java.nio.file.Path

class DesktopRepositories private constructor(
    val mediaSources: MediaSourceRepository,
    val progress: PlaybackProgressRepository,
    val index: MediaIndexRepository,
    val cloudDriveAutomation: CloudDriveAutomationRepository,
    val credentials: AppCredentialStore,
) {
    companion object {
        fun fileBacked(storePath: Path = DesktopRepositoryPaths.defaultStorePath()): DesktopRepositories {
            val store = DesktopRepositoryStore(storePath)
            return DesktopRepositories(
                mediaSources = FileBackedMediaSourceRepository(store),
                progress = FileBackedProgressRepository(store),
                index = FileBackedMediaIndexRepository(store),
                cloudDriveAutomation = FileBackedCloudDriveAutomationRepository(store),
                credentials = FileBackedCredentialStore(store),
            )
        }
    }
}
