package com.miruplay.tv.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class PlaybackEndAction(val storageValue: String) {
    RETURN_TO_DETAIL("return_to_detail"),
    PLAY_NEXT_EPISODE("play_next_episode");

    companion object {
        fun fromStorageValue(value: String?): PlaybackEndAction =
            entries.firstOrNull { it.storageValue == value } ?: RETURN_TO_DETAIL
    }
}

@Singleton
class PlaybackPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("miruplay_playback_prefs", Context.MODE_PRIVATE)

    var endAction: PlaybackEndAction
        get() = PlaybackEndAction.fromStorageValue(prefs.getString(KEY_END_ACTION, null))
        set(value) {
            prefs.edit().putString(KEY_END_ACTION, value.storageValue).apply()
        }

    companion object {
        private const val KEY_END_ACTION = "end_action"
    }
}
