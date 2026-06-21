package com.miruplay.tv.repository.desktop

import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.AppModePreferencesRepository
import com.miruplay.tv.repository.AppModeSelectionState

internal class FileBackedAppModePreferencesRepository(
    private val store: DesktopRepositoryStore,
) : AppModePreferencesRepository {
    override suspend fun getSelectionState(): AppModeSelectionState = store.read { state ->
        AppModeSelectionState(
            currentAppMode = AppMode.fromStorageValue(state.appModeStorageValue) ?: DEFAULT_APP_MODE,
            hasCompletedModeSelection = state.hasCompletedAppModeSelection,
        )
    }

    override suspend fun completeModeSelection(mode: AppMode) = persist(mode)

    override suspend fun setCurrentAppMode(mode: AppMode) = persist(mode)

    private suspend fun persist(mode: AppMode) {
        store.update { state ->
            state.copy(
                appModeStorageValue = mode.storageValue,
                hasCompletedAppModeSelection = true,
            ) to Unit
        }
    }

    private companion object {
        val DEFAULT_APP_MODE = AppMode.ANIME
    }
}
