package com.miruplay.tv.repository

import com.miruplay.tv.model.EpisodeVersionSelectionPolicy
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.SubtitleLanguagePreference

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
}
