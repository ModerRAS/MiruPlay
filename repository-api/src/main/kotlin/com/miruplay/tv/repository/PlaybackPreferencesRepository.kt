package com.miruplay.tv.repository

import com.miruplay.tv.model.EpisodeVersionSelectionPolicy
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.SubtitleLanguagePreference
import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.MusicSrcBypassMode

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
}
