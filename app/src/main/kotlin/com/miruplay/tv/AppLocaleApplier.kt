package com.miruplay.tv

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import com.miruplay.tv.repository.AppLanguage
import com.miruplay.tv.repository.AppLocaleStore

/**
 * 应用内语言切换：无 appcompat 依赖，手动包 configuration。
 * Application / Activity 的 attachBaseContext 里先包一层；语言变化时 recreate() 生效。
 */
object AppLocaleApplier {
    fun wrap(base: Context): Context {
        val locale = readLocale(base)?.toJavaLocale() ?: return base
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    /** 返回监听器，调用方在 onDestroy 里 unregister；语言变化且与当前不同则 recreate。 */
    fun recreateOnLanguageChange(activity: ComponentActivity): SharedPreferences.OnSharedPreferenceChangeListener {
        val prefs = prefs(activity)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppLocaleStore.KEY_APP_LANGUAGE) {
                val expected = readLocale(activity)?.toJavaLocale()
                val current = activity.resources.configuration.locales.get(0)
                if (expected?.toLanguageTag() != current?.toLanguageTag()) {
                    activity.recreate()
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregister(activity: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(activity).unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(AppLocaleStore.PREFS_FILE, Context.MODE_PRIVATE)

    private fun readLocale(context: Context): AppLanguage? =
        AppLanguage.fromId(prefs(context).getString(AppLocaleStore.KEY_APP_LANGUAGE, null))
            ?.takeIf { it != AppLanguage.SYSTEM }
}
