package com.miruplay.tv.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.miruplay.tv.data.secure.SecurePreferencesManager
import com.miruplay.tv.repository.WebControlAccessManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlPreferencesManager @Inject constructor(
    @ApplicationContext context: Context,
    private val securePreferences: SecurePreferencesManager
) : WebControlAccessManager {
    private val prefs = context.getSharedPreferences("miruplay_web_control_prefs", Context.MODE_PRIVATE)

    override var webControlEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEB_CONTROL_ENABLED, true)
        set(value) {
            if (value) securePreferences.ensureWebControlAccessToken()
            prefs.edit().putBoolean(KEY_WEB_CONTROL_ENABLED, value).apply()
        }

    override val accessToken: String
        get() = securePreferences.ensureWebControlAccessToken()

    override fun rotateAccessToken(): String = securePreferences.rotateWebControlAccessToken()

    override fun addEnabledChangeListener(
        onChanged: (Boolean) -> Unit
    ): Closeable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_WEB_CONTROL_ENABLED) {
                onChanged(webControlEnabled)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return Closeable { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun removeChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val KEY_WEB_CONTROL_ENABLED = "web_control_enabled"
    }
}
