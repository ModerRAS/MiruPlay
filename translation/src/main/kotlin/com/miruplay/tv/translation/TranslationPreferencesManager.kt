package com.miruplay.tv.translation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationPreferencesManager @Inject constructor(
    @ApplicationContext context: Context,
) : TranslationPreferencesRepository {

    private val prefs = context.getSharedPreferences("miruplay_translation_prefs", Context.MODE_PRIVATE)

    override var deepSeekApiKey: String
        get() = prefs.getString(KEY_DEEPSEEK_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_DEEPSEEK_API_KEY, value).apply()
        }

    override var defaultTargetLanguage: String
        get() = prefs.getString(KEY_DEFAULT_TARGET_LANGUAGE, DEFAULT_TARGET_LANGUAGE) ?: DEFAULT_TARGET_LANGUAGE
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_TARGET_LANGUAGE, value).apply()
        }

    private companion object {
        const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
        const val KEY_DEFAULT_TARGET_LANGUAGE = "default_target_language"
        const val DEFAULT_TARGET_LANGUAGE = "zh-Hans"
    }
}
