package com.miruplay.tv.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.SubtitleLanguagePreference
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) : PlaybackPreferencesRepository {
    private val prefs = context.getSharedPreferences("miruplay_playback_prefs", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var endAction: PlaybackEndAction
        get() = PlaybackEndAction.fromStorageValue(prefs.getString(KEY_END_ACTION, null))
        set(value) {
            prefs.edit().putString(KEY_END_ACTION, value.storageValue).apply()
        }

    var preferredSubtitleLanguage: SubtitleLanguagePreference
        get() = SubtitleLanguagePreference.fromStorageValue(
            prefs.getString(KEY_PREFERRED_SUBTITLE_LANGUAGE, null),
        )
        set(value) {
            prefs.edit().putString(KEY_PREFERRED_SUBTITLE_LANGUAGE, value.storageValue).apply()
        }

    var formatAwareToneMappingPreferences: FormatAwareToneMappingPreferences
        get() {
            val stored = prefs.getString(KEY_FORMAT_AWARE_TONE_MAPPING_PREFERENCES, null)
            return runCatching {
                stored
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::normalizeLegacyBackendPayload)
                    ?.let { json.decodeFromString<FormatAwareToneMappingPreferences>(it) }
                    ?.normalized()
                    ?: FormatAwareToneMappingPreferences()
            }.getOrElse {
                FormatAwareToneMappingPreferences()
            }
        }
        set(value) {
            val serialized = json.encodeToString(value.normalized())
            prefs.edit().putString(KEY_FORMAT_AWARE_TONE_MAPPING_PREFERENCES, serialized).apply()
        }

    override suspend fun getEndAction(): PlaybackEndAction =
        endAction

    override suspend fun setEndAction(action: PlaybackEndAction) {
        endAction = action
    }

    override suspend fun getPreferredSubtitleLanguage(): SubtitleLanguagePreference =
        preferredSubtitleLanguage

    override suspend fun setPreferredSubtitleLanguage(preference: SubtitleLanguagePreference) {
        preferredSubtitleLanguage = preference
    }

    override suspend fun getFormatAwareToneMappingPreferences(): FormatAwareToneMappingPreferences =
        formatAwareToneMappingPreferences

    override suspend fun setFormatAwareToneMappingPreferences(preferences: FormatAwareToneMappingPreferences) {
        formatAwareToneMappingPreferences = preferences
    }

    companion object {
        private const val KEY_END_ACTION = "end_action"
        private const val KEY_PREFERRED_SUBTITLE_LANGUAGE = "preferred_subtitle_language"
        private const val KEY_FORMAT_AWARE_TONE_MAPPING_PREFERENCES = "format_aware_tone_mapping_preferences"
    }
}

private fun normalizeLegacyBackendPayload(raw: String): String =
    raw.replace(
        "\"defaultBackend\":\"EXPERIMENTAL_LIBVLC\"",
        "\"defaultBackend\":\"${PlaybackRenderBackend.STANDARD_EXO.name}\"",
    ).replace(
        "\"requestedBackendOverride\":\"EXPERIMENTAL_LIBVLC\"",
        "\"requestedBackendOverride\":\"${PlaybackRenderBackend.STANDARD_EXO.name}\"",
    )
