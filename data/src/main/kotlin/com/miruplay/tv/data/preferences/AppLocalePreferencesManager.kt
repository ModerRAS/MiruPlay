package com.miruplay.tv.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.miruplay.tv.repository.AppLanguage
import com.miruplay.tv.repository.AppLocaleStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLocalePreferencesManager @Inject constructor(
    @ApplicationContext context: Context,
) : AppLocaleStore {
    private val prefs = context.getSharedPreferences(AppLocaleStore.PREFS_FILE, Context.MODE_PRIVATE)

    override var appLanguage: AppLanguage
        get() = AppLanguage.fromId(prefs.getString(AppLocaleStore.KEY_APP_LANGUAGE, null)) ?: AppLanguage.SYSTEM
        set(value) {
            prefs.edit().putString(AppLocaleStore.KEY_APP_LANGUAGE, value.id).apply()
        }

    override fun addLanguageChangeListener(onChanged: (AppLanguage) -> Unit): Closeable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppLocaleStore.KEY_APP_LANGUAGE) {
                onChanged(appLanguage)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return Closeable { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
