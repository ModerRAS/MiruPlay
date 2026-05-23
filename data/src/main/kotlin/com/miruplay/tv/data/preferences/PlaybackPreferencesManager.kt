package com.miruplay.tv.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) : PlaybackPreferencesRepository {
    private val prefs = context.getSharedPreferences("miruplay_playback_prefs", Context.MODE_PRIVATE)

    var endAction: PlaybackEndAction
        get() = PlaybackEndAction.fromStorageValue(prefs.getString(KEY_END_ACTION, null))
        set(value) {
            prefs.edit().putString(KEY_END_ACTION, value.storageValue).apply()
        }

    override suspend fun getEndAction(): PlaybackEndAction =
        endAction

    override suspend fun setEndAction(action: PlaybackEndAction) {
        endAction = action
    }

    companion object {
        private const val KEY_END_ACTION = "end_action"
    }
}
