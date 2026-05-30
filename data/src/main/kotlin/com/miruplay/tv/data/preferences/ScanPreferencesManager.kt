package com.miruplay.tv.data.preferences

import android.content.Context
import com.miruplay.tv.model.PosterWallArrangement
import com.miruplay.tv.repository.SCAN_PREFERENCES_DEFAULT_INTERVAL_MS
import com.miruplay.tv.repository.SCAN_PREFERENCES_MIN_INTERVAL_MS
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.repository.ScanPreferencesSnapshot
import com.miruplay.tv.repository.scanPreferencesIntervalOptionsHours
import com.miruplay.tv.repository.shouldAutoScan
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) : ScanPreferencesRepository {
    private val prefs = context.getSharedPreferences("miruplay_scan_prefs", Context.MODE_PRIVATE)

    var autoScanEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCAN_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_SCAN_ENABLED, value).apply()
        }

    var autoScanIntervalMs: Long
        get() = prefs.getLong(KEY_AUTO_SCAN_INTERVAL_MS, SCAN_PREFERENCES_DEFAULT_INTERVAL_MS)
        set(value) {
            prefs.edit().putLong(KEY_AUTO_SCAN_INTERVAL_MS, value.coerceAtLeast(SCAN_PREFERENCES_MIN_INTERVAL_MS)).apply()
        }

    var lastScanAt: Long
        get() = prefs.getLong(KEY_LAST_SCAN_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_SCAN_AT, value).apply()
        }

    var mergeSameAnimeEnabled: Boolean
        get() = prefs.getBoolean(KEY_MERGE_SAME_ANIME_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MERGE_SAME_ANIME_ENABLED, value).apply()
        }

    var posterWallArrangement: PosterWallArrangement
        get() = runCatching {
            PosterWallArrangement.valueOf(
                prefs.getString(KEY_POSTER_WALL_ARRANGEMENT, null)
                    ?: PosterWallArrangement.TITLE.name
            )
        }.getOrDefault(PosterWallArrangement.TITLE)
        set(value) {
            prefs.edit().putString(KEY_POSTER_WALL_ARRANGEMENT, value.name).apply()
        }

    override suspend fun getPreferences(): ScanPreferencesSnapshot =
        ScanPreferencesSnapshot(
            autoScanEnabled = autoScanEnabled,
            autoScanIntervalMs = autoScanIntervalMs,
            lastScanAt = lastScanAt,
            mergeSameAnimeEnabled = mergeSameAnimeEnabled,
            posterWallArrangement = posterWallArrangement,
        )

    override suspend fun setAutoScanEnabled(enabled: Boolean) {
        autoScanEnabled = enabled
    }

    override suspend fun setAutoScanIntervalMs(intervalMs: Long) {
        autoScanIntervalMs = intervalMs
    }

    override suspend fun setLastScanAt(timestampMs: Long) {
        lastScanAt = timestampMs
    }

    override suspend fun setMergeSameAnimeEnabled(enabled: Boolean) {
        mergeSameAnimeEnabled = enabled
    }

    override suspend fun setPosterWallArrangement(arrangement: PosterWallArrangement) {
        posterWallArrangement = arrangement
    }

    suspend fun shouldAutoScan(now: Long = System.currentTimeMillis()): Boolean =
        (this as ScanPreferencesRepository).shouldAutoScan(now)

    companion object {
        private const val KEY_AUTO_SCAN_ENABLED = "auto_scan_enabled"
        private const val KEY_AUTO_SCAN_INTERVAL_MS = "auto_scan_interval_ms"
        private const val KEY_LAST_SCAN_AT = "last_scan_at"
        private const val KEY_MERGE_SAME_ANIME_ENABLED = "merge_same_anime_enabled"
        private const val KEY_POSTER_WALL_ARRANGEMENT = "poster_wall_arrangement"

        const val DEFAULT_INTERVAL_MS = SCAN_PREFERENCES_DEFAULT_INTERVAL_MS

        val INTERVAL_OPTIONS_HOURS = scanPreferencesIntervalOptionsHours
    }
}
