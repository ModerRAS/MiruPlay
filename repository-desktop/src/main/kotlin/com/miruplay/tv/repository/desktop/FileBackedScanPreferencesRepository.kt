package com.miruplay.tv.repository.desktop

import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.repository.ScanPreferencesSnapshot

internal class FileBackedScanPreferencesRepository(
    private val store: DesktopRepositoryStore,
) : ScanPreferencesRepository {
    override suspend fun getPreferences(): ScanPreferencesSnapshot =
        store.read { state -> state.scanPreferences.normalized() }

    override suspend fun setAutoScanEnabled(enabled: Boolean) {
        store.update { state ->
            val preferences = state.scanPreferences.normalized().copy(autoScanEnabled = enabled)
            state.copy(scanPreferences = preferences) to Unit
        }
    }

    override suspend fun setAutoScanIntervalMs(intervalMs: Long) {
        store.update { state ->
            val preferences = state.scanPreferences.copy(autoScanIntervalMs = intervalMs).normalized()
            state.copy(scanPreferences = preferences) to Unit
        }
    }

    override suspend fun setLastScanAt(timestampMs: Long) {
        store.update { state ->
            val preferences = state.scanPreferences.normalized().copy(lastScanAt = timestampMs)
            state.copy(scanPreferences = preferences) to Unit
        }
    }

    override suspend fun setMergeSameAnimeEnabled(enabled: Boolean) {
        store.update { state ->
            val preferences = state.scanPreferences.normalized().copy(mergeSameAnimeEnabled = enabled)
            state.copy(scanPreferences = preferences) to Unit
        }
    }
}
