package com.miruplay.tv.repository

import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.PosterWallArrangement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferenceActionCoordinatorTest {
    @Test
    fun `current scan preferences returns hour snapshot`() = runBlocking {
        val scanPreferences = FakeScanPreferencesRepository(
            ScanPreferencesSnapshot(
                autoScanEnabled = true,
                autoScanIntervalMs = 6.toScanIntervalMillis(),
                lastScanAt = 42L,
                mergeSameAnimeEnabled = true,
                posterWallArrangement = PosterWallArrangement.RELEASE_SEASON,
            ),
        )
        val coordinator = coordinator(scanPreferences)

        assertEquals(
            ScanPreferenceActionSnapshot(
                autoScanEnabled = true,
                autoScanIntervalHours = 6,
                lastScanAt = 42L,
                mergeSameAnimeEnabled = true,
                posterWallArrangement = PosterWallArrangement.RELEASE_SEASON,
            ),
            coordinator.currentScanPreferences(),
        )
    }

    @Test
    fun `scan preference setters persist values and return refreshed snapshot`() = runBlocking {
        val scanPreferences = FakeScanPreferencesRepository()
        val coordinator = coordinator(scanPreferences)

        val enabled = coordinator.setAutoScanEnabled(true)
        val interval = coordinator.setAutoScanIntervalHours(12)
        val merged = coordinator.setMergeSameAnimeEnabled(true)
        val arrangement = coordinator.setPosterWallArrangement(PosterWallArrangement.RELEASE_SEASON)

        assertEquals(true, scanPreferences.snapshot.autoScanEnabled)
        assertEquals(12.toScanIntervalMillis(), scanPreferences.snapshot.autoScanIntervalMs)
        assertEquals(true, scanPreferences.snapshot.mergeSameAnimeEnabled)
        assertEquals(PosterWallArrangement.RELEASE_SEASON, scanPreferences.snapshot.posterWallArrangement)
        assertEquals(true, enabled.autoScanEnabled)
        assertEquals(12, interval.autoScanIntervalHours)
        assertEquals(true, merged.mergeSameAnimeEnabled)
        assertEquals(PosterWallArrangement.RELEASE_SEASON, arrangement.posterWallArrangement)
    }

    @Test
    fun `playback end action setter persists and returns refreshed action`() = runBlocking {
        val playbackPreferences = FakePlaybackPreferencesRepository()
        val coordinator = coordinator(playbackPreferences = playbackPreferences)

        val action = coordinator.setPlaybackEndAction(PlaybackEndAction.PLAY_NEXT_EPISODE)

        assertEquals(PlaybackEndAction.PLAY_NEXT_EPISODE, action)
        assertEquals(PlaybackEndAction.PLAY_NEXT_EPISODE, coordinator.currentPlaybackEndAction())
    }

    private fun coordinator(
        scanPreferences: FakeScanPreferencesRepository = FakeScanPreferencesRepository(),
        playbackPreferences: FakePlaybackPreferencesRepository = FakePlaybackPreferencesRepository(),
    ): SettingsPreferenceActionCoordinator =
        SettingsPreferenceActionCoordinator(
            scanPreferences = scanPreferences,
            playbackPreferences = playbackPreferences,
        )

    private class FakeScanPreferencesRepository(
        initial: ScanPreferencesSnapshot = ScanPreferencesSnapshot(),
    ) : ScanPreferencesRepository {
        var snapshot: ScanPreferencesSnapshot = initial

        override suspend fun getPreferences(): ScanPreferencesSnapshot =
            snapshot

        override suspend fun setAutoScanEnabled(enabled: Boolean) {
            snapshot = snapshot.copy(autoScanEnabled = enabled)
        }

        override suspend fun setAutoScanIntervalMs(intervalMs: Long) {
            snapshot = snapshot.copy(autoScanIntervalMs = intervalMs.coerceAtLeast(SCAN_PREFERENCES_MIN_INTERVAL_MS))
        }

        override suspend fun setLastScanAt(timestampMs: Long) {
            snapshot = snapshot.copy(lastScanAt = timestampMs)
        }

        override suspend fun setMergeSameAnimeEnabled(enabled: Boolean) {
            snapshot = snapshot.copy(mergeSameAnimeEnabled = enabled)
        }

        override suspend fun setPosterWallArrangement(arrangement: PosterWallArrangement) {
            snapshot = snapshot.copy(posterWallArrangement = arrangement)
        }
    }

    private class FakePlaybackPreferencesRepository(
        private var action: PlaybackEndAction = PlaybackEndAction.RETURN_TO_DETAIL,
    ) : PlaybackPreferencesRepository {
        private var formatAwareToneMappingPreferences: FormatAwareToneMappingPreferences =
            FormatAwareToneMappingPreferences()

        override suspend fun getEndAction(): PlaybackEndAction =
            action

        override suspend fun setEndAction(action: PlaybackEndAction) {
            this.action = action
        }

        override suspend fun getFormatAwareToneMappingPreferences(): FormatAwareToneMappingPreferences =
            formatAwareToneMappingPreferences

        override suspend fun setFormatAwareToneMappingPreferences(preferences: FormatAwareToneMappingPreferences) {
            formatAwareToneMappingPreferences = preferences
        }
    }
}
