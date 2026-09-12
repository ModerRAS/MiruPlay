package com.miruplay.tv.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.miruplay.tv.repository.AppUpdateChannelStore
import com.miruplay.tv.repository.UpdateChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdatePreferencesManager @Inject constructor(
    @ApplicationContext context: Context,
) : AppUpdateChannelStore {
    private val prefs = context.getSharedPreferences("miruplay_app_update_prefs", Context.MODE_PRIVATE)

    override var updateChannel: UpdateChannel
        get() = UpdateChannel.fromId(prefs.getString(KEY_UPDATE_CHANNEL, null)) ?: UpdateChannel.ALPHA
        set(value) {
            prefs.edit().putString(KEY_UPDATE_CHANNEL, value.id).apply()
        }

    override fun addChannelChangeListener(onChanged: (UpdateChannel) -> Unit): Closeable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_UPDATE_CHANNEL) {
                onChanged(updateChannel)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return Closeable { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val KEY_UPDATE_CHANNEL = "app_update_channel"
    }
}
