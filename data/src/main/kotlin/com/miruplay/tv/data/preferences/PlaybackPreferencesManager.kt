package com.miruplay.tv.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.miruplay.tv.model.EpisodeVersionSelectionPolicy
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.SubtitleLanguagePreference
import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.MusicSrcBypassMode
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

    var episodeVersionSelectionPolicy: EpisodeVersionSelectionPolicy
        get() = EpisodeVersionSelectionPolicy.fromStorageValue(
            prefs.getString(KEY_EPISODE_VERSION_SELECTION_POLICY, null),
        )
        set(value) {
            prefs.edit().putString(KEY_EPISODE_VERSION_SELECTION_POLICY, value.storageValue).apply()
        }

    var preferredSubtitleLanguage: SubtitleLanguagePreference
        get() = SubtitleLanguagePreference.fromStorageValue(
            prefs.getString(KEY_PREFERRED_SUBTITLE_LANGUAGE, null),
        )
        set(value) {
            prefs.edit().putString(KEY_PREFERRED_SUBTITLE_LANGUAGE, value.storageValue).apply()
        }

    var subtitleBackgroundTransparent: Boolean
        get() = prefs.getBoolean(KEY_SUBTITLE_BACKGROUND_TRANSPARENT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SUBTITLE_BACKGROUND_TRANSPARENT, value).apply()
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

    var audioDspConfig: AudioDspConfig
        get() {
            val stored = prefs.getString(KEY_AUDIO_DSP_CONFIG, null)
            return runCatching {
                stored?.takeIf(String::isNotBlank)
                    ?.let { json.decodeFromString<AudioDspConfig>(it) }
                    ?.normalized()
                    ?: AudioDspConfig.neutral()
            }.getOrElse { AudioDspConfig.neutral() }
        }
        set(value) {
            prefs.edit().putString(KEY_AUDIO_DSP_CONFIG, json.encodeToString(value.normalized())).apply()
        }

    var musicSrcBypassMode: MusicSrcBypassMode
        get() = MusicSrcBypassMode.fromStorageValue(prefs.getString(KEY_MUSIC_SRC_BYPASS_MODE, null))
        set(value) {
            prefs.edit().putString(KEY_MUSIC_SRC_BYPASS_MODE, value.storageValue).apply()
        }

    override suspend fun getEndAction(): PlaybackEndAction =
        endAction

    override suspend fun setEndAction(action: PlaybackEndAction) {
        endAction = action
    }

    override suspend fun getEpisodeVersionSelectionPolicy(): EpisodeVersionSelectionPolicy =
        episodeVersionSelectionPolicy

    override suspend fun setEpisodeVersionSelectionPolicy(policy: EpisodeVersionSelectionPolicy) {
        episodeVersionSelectionPolicy = policy
    }

    override suspend fun getPreferredSubtitleLanguage(): SubtitleLanguagePreference =
        preferredSubtitleLanguage

    override suspend fun setPreferredSubtitleLanguage(preference: SubtitleLanguagePreference) {
        preferredSubtitleLanguage = preference
    }

    override suspend fun getSubtitleBackgroundTransparent(): Boolean =
        subtitleBackgroundTransparent

    override suspend fun setSubtitleBackgroundTransparent(transparent: Boolean) {
        subtitleBackgroundTransparent = transparent
    }

    override suspend fun getFormatAwareToneMappingPreferences(): FormatAwareToneMappingPreferences =
        formatAwareToneMappingPreferences

    override suspend fun setFormatAwareToneMappingPreferences(preferences: FormatAwareToneMappingPreferences) {
        formatAwareToneMappingPreferences = preferences
    }

    override suspend fun getAudioDspConfig(): AudioDspConfig = audioDspConfig

    override suspend fun setAudioDspConfig(config: AudioDspConfig) {
        audioDspConfig = config
    }

    override suspend fun getMusicSrcBypassMode(): MusicSrcBypassMode = musicSrcBypassMode

    override suspend fun setMusicSrcBypassMode(mode: MusicSrcBypassMode) {
        musicSrcBypassMode = mode
    }

    companion object {
        private const val KEY_END_ACTION = "end_action"
        private const val KEY_EPISODE_VERSION_SELECTION_POLICY = "episode_version_selection_policy"
        private const val KEY_PREFERRED_SUBTITLE_LANGUAGE = "preferred_subtitle_language"
        private const val KEY_SUBTITLE_BACKGROUND_TRANSPARENT = "subtitle_background_transparent"
        private const val KEY_FORMAT_AWARE_TONE_MAPPING_PREFERENCES = "format_aware_tone_mapping_preferences"
        private const val KEY_AUDIO_DSP_CONFIG = "audio_dsp_config"
        private const val KEY_MUSIC_SRC_BYPASS_MODE = "music_src_bypass_mode"
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
