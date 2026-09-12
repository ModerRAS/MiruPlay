package com.miruplay.tv.repository

import com.miruplay.tv.model.EpisodeVersionSelectionPolicy
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.SubtitleLanguagePreference
import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.MusicSrcBypassMode
import kotlinx.serialization.Serializable

interface PlaybackPreferencesRepository {
    suspend fun getEndAction(): PlaybackEndAction
    suspend fun setEndAction(action: PlaybackEndAction)
    suspend fun getEpisodeVersionSelectionPolicy(): EpisodeVersionSelectionPolicy =
        EpisodeVersionSelectionPolicy.AUTO_NEAREST
    suspend fun setEpisodeVersionSelectionPolicy(policy: EpisodeVersionSelectionPolicy) = Unit
    suspend fun getPreferredSubtitleLanguage(): SubtitleLanguagePreference
    suspend fun setPreferredSubtitleLanguage(preference: SubtitleLanguagePreference)
    suspend fun getSubtitleBackgroundTransparent(): Boolean = false
    suspend fun setSubtitleBackgroundTransparent(transparent: Boolean) = Unit
    suspend fun getFormatAwareToneMappingPreferences(): FormatAwareToneMappingPreferences
    suspend fun setFormatAwareToneMappingPreferences(preferences: FormatAwareToneMappingPreferences)
    suspend fun getAudioDspConfig(): AudioDspConfig = AudioDspConfig.neutral()
    suspend fun setAudioDspConfig(config: AudioDspConfig) = Unit
    suspend fun getMusicSrcBypassMode(): MusicSrcBypassMode = MusicSrcBypassMode.SOFTWARE
    suspend fun setMusicSrcBypassMode(mode: MusicSrcBypassMode) = Unit
    /** All saved mic calibrations plus the id of the active one (null = none). */
    suspend fun getAudioMeasureCalibrations(): List<MicCalibrationSettings> = emptyList()
    suspend fun getAudioMeasureCalibrationActiveId(): String? = null
    suspend fun saveAudioMeasureCalibrations(calibrations: List<MicCalibrationSettings>, activeId: String?) = Unit
}

/**
 * Persisted mic calibration for sweep measurement; [data] is the raw .cal text.
 * [source] dedupes downloads ("umik-<sn>-<incidence>") so re-entering the same
 * serial reuses the saved file instead of re-fetching from miniDSP.
 */
@Serializable
data class MicCalibrationSettings(
    val id: String,
    val name: String,
    val source: String = "import",
    val data: String,
    val createdAtMs: Long = 0L,
)
