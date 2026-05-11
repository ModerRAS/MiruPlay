package com.miruplay.tv.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("miruplay_scan_prefs", Context.MODE_PRIVATE)

    var autoScanEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCAN_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_SCAN_ENABLED, value).apply()
        }

    var autoScanIntervalMs: Long
        get() = prefs.getLong(KEY_AUTO_SCAN_INTERVAL_MS, DEFAULT_INTERVAL_MS)
        set(value) {
            prefs.edit().putLong(KEY_AUTO_SCAN_INTERVAL_MS, value.coerceAtLeast(MIN_INTERVAL_MS)).apply()
        }

    var lastScanAt: Long
        get() = prefs.getLong(KEY_LAST_SCAN_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_SCAN_AT, value).apply()
        }

    fun shouldAutoScan(now: Long = System.currentTimeMillis()): Boolean {
        if (!autoScanEnabled) return false
        val last = lastScanAt
        return last <= 0L || now - last >= autoScanIntervalMs
    }

    companion object {
        private const val KEY_AUTO_SCAN_ENABLED = "auto_scan_enabled"
        private const val KEY_AUTO_SCAN_INTERVAL_MS = "auto_scan_interval_ms"
        private const val KEY_LAST_SCAN_AT = "last_scan_at"

        private const val MIN_INTERVAL_MS = 30 * 60 * 1000L
        const val DEFAULT_INTERVAL_MS = 6 * 60 * 60 * 1000L

        val INTERVAL_OPTIONS_HOURS = listOf(1, 6, 12, 24)
    }
}
