package com.miruplay.tv.repository

import com.miruplay.tv.model.PlaybackEndAction

data class ScanPreferenceActionSnapshot(
    val autoScanEnabled: Boolean = false,
    val autoScanIntervalHours: Int = SCAN_PREFERENCES_DEFAULT_INTERVAL_MS.toScanIntervalHours(),
    val lastScanAt: Long = 0L,
    val mergeSameAnimeEnabled: Boolean = false,
)

class SettingsPreferenceActionCoordinator(
    private val scanPreferences: ScanPreferencesRepository,
    private val playbackPreferences: PlaybackPreferencesRepository,
) {
    suspend fun currentScanPreferences(): ScanPreferenceActionSnapshot =
        scanPreferences.getPreferences().toScanPreferenceActionSnapshot()

    suspend fun setAutoScanEnabled(enabled: Boolean): ScanPreferenceActionSnapshot {
        scanPreferences.setAutoScanEnabled(enabled)
        return currentScanPreferences()
    }

    suspend fun setAutoScanIntervalHours(hours: Int): ScanPreferenceActionSnapshot {
        scanPreferences.setAutoScanIntervalMs(hours.toScanIntervalMillis())
        return currentScanPreferences()
    }

    suspend fun setMergeSameAnimeEnabled(enabled: Boolean): ScanPreferenceActionSnapshot {
        scanPreferences.setMergeSameAnimeEnabled(enabled)
        return currentScanPreferences()
    }

    suspend fun currentPlaybackEndAction(): PlaybackEndAction =
        playbackPreferences.getEndAction()

    suspend fun setPlaybackEndAction(action: PlaybackEndAction): PlaybackEndAction {
        playbackPreferences.setEndAction(action)
        return playbackPreferences.getEndAction()
    }
}

fun ScanPreferencesSnapshot.toScanPreferenceActionSnapshot(): ScanPreferenceActionSnapshot {
    val normalized = normalized()
    return ScanPreferenceActionSnapshot(
        autoScanEnabled = normalized.autoScanEnabled,
        autoScanIntervalHours = normalized.autoScanIntervalMs.toScanIntervalHours(),
        lastScanAt = normalized.lastScanAt,
        mergeSameAnimeEnabled = normalized.mergeSameAnimeEnabled,
    )
}
