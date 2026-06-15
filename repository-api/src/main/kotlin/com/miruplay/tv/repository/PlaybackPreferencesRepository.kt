package com.miruplay.tv.repository

import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackEndAction

interface PlaybackPreferencesRepository {
    suspend fun getEndAction(): PlaybackEndAction
    suspend fun setEndAction(action: PlaybackEndAction)
    suspend fun getFormatAwareToneMappingPreferences(): FormatAwareToneMappingPreferences
    suspend fun setFormatAwareToneMappingPreferences(preferences: FormatAwareToneMappingPreferences)
}
