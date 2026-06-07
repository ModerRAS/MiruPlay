package com.miruplay.tv.data.preferences

import android.content.Context
import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.AppModePreferencesRepository
import com.miruplay.tv.repository.AppModeSelectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppModePreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) : AppModePreferencesRepository {
    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun getSelectionState(): AppModeSelectionState =
        AppModeSelectionState(
            currentAppMode = AppMode.fromStorageValue(prefs.getString(KEY_CURRENT_APP_MODE, null)),
            hasCompletedModeSelection = prefs.getBoolean(KEY_HAS_COMPLETED_MODE_SELECTION, false),
        )

    override suspend fun completeModeSelection(mode: AppMode) {
        prefs.edit()
            .putString(KEY_CURRENT_APP_MODE, mode.storageValue)
            .putBoolean(KEY_HAS_COMPLETED_MODE_SELECTION, true)
            .apply()
    }

    override suspend fun setCurrentAppMode(mode: AppMode) {
        prefs.edit()
            .putString(KEY_CURRENT_APP_MODE, mode.storageValue)
            .putBoolean(KEY_HAS_COMPLETED_MODE_SELECTION, true)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "miruplay_app_mode_prefs"
        private const val KEY_CURRENT_APP_MODE = "current_app_mode"
        private const val KEY_HAS_COMPLETED_MODE_SELECTION = "has_completed_mode_selection"
    }
}
