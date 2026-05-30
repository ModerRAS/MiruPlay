package com.miruplay.tv.repository

import com.miruplay.tv.model.PosterWallArrangement
import kotlinx.serialization.Serializable

const val SCAN_PREFERENCES_MIN_INTERVAL_MS: Long = 30 * 60 * 1000L
const val SCAN_PREFERENCES_DEFAULT_INTERVAL_MS: Long = 6 * 60 * 60 * 1000L
const val SCAN_PREFERENCES_MILLIS_PER_HOUR: Long = 60 * 60 * 1000L

val scanPreferencesIntervalOptionsHours: List<Int> = listOf(1, 6, 12, 24)

@Serializable
data class ScanPreferencesSnapshot(
    val autoScanEnabled: Boolean = false,
    val autoScanIntervalMs: Long = SCAN_PREFERENCES_DEFAULT_INTERVAL_MS,
    val lastScanAt: Long = 0L,
    val mergeSameAnimeEnabled: Boolean = false,
    val posterWallArrangement: PosterWallArrangement = PosterWallArrangement.TITLE,
) {
    fun normalized(): ScanPreferencesSnapshot =
        copy(autoScanIntervalMs = autoScanIntervalMs.coerceAtLeast(SCAN_PREFERENCES_MIN_INTERVAL_MS))
}

interface ScanPreferencesRepository {
    suspend fun getPreferences(): ScanPreferencesSnapshot
    suspend fun setAutoScanEnabled(enabled: Boolean)
    suspend fun setAutoScanIntervalMs(intervalMs: Long)
    suspend fun setLastScanAt(timestampMs: Long)
    suspend fun setMergeSameAnimeEnabled(enabled: Boolean)
    suspend fun setPosterWallArrangement(arrangement: PosterWallArrangement)
}

suspend fun ScanPreferencesRepository.shouldAutoScan(now: Long = System.currentTimeMillis()): Boolean {
    val preferences = getPreferences().normalized()
    if (!preferences.autoScanEnabled) return false
    val last = preferences.lastScanAt
    return last <= 0L || now - last >= preferences.autoScanIntervalMs
}

fun Long.toScanIntervalHours(): Int =
    (coerceAtLeast(SCAN_PREFERENCES_MIN_INTERVAL_MS) / SCAN_PREFERENCES_MILLIS_PER_HOUR).toInt()

fun Int.toScanIntervalMillis(): Long =
    this * SCAN_PREFERENCES_MILLIS_PER_HOUR
