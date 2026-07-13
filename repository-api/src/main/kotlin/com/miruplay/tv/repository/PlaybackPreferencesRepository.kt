package com.miruplay.tv.repository

import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.SubtitleLanguagePreference

interface PlaybackPreferencesRepository {
    suspend fun getEndAction(): PlaybackEndAction
    suspend fun setEndAction(action: PlaybackEndAction)
    suspend fun getPreferredSubtitleLanguage(): SubtitleLanguagePreference
    suspend fun setPreferredSubtitleLanguage(preference: SubtitleLanguagePreference)
    suspend fun getFormatAwareToneMappingPreferences(): FormatAwareToneMappingPreferences
    suspend fun setFormatAwareToneMappingPreferences(preferences: FormatAwareToneMappingPreferences)
}
