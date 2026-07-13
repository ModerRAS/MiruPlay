package com.miruplay.tv.repository.desktop

import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.SubtitleLanguagePreference
import com.miruplay.tv.repository.PlaybackPreferencesRepository

internal class FileBackedPlaybackPreferencesRepository(
    private val store: DesktopRepositoryStore,
) : PlaybackPreferencesRepository {
    override suspend fun getEndAction(): PlaybackEndAction =
        store.read { state -> state.playbackEndAction }

    override suspend fun setEndAction(action: PlaybackEndAction) {
        store.update { state -> state.copy(playbackEndAction = action) to Unit }
    }

    override suspend fun getPreferredSubtitleLanguage(): SubtitleLanguagePreference =
        store.read { state -> state.preferredSubtitleLanguage }

    override suspend fun setPreferredSubtitleLanguage(preference: SubtitleLanguagePreference) {
        store.update { state -> state.copy(preferredSubtitleLanguage = preference) to Unit }
    }

    override suspend fun getFormatAwareToneMappingPreferences(): FormatAwareToneMappingPreferences =
        store.read { state -> state.formatAwareToneMappingPreferences.normalized() }

    override suspend fun setFormatAwareToneMappingPreferences(preferences: FormatAwareToneMappingPreferences) {
        store.update { state ->
            state.copy(
                formatAwareToneMappingPreferences = preferences.normalized(),
            ) to Unit
        }
    }
}
