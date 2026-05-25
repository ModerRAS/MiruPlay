package com.miruplay.tv.repository.desktop

import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.repository.PlaybackPreferencesRepository

internal class FileBackedPlaybackPreferencesRepository(
    private val store: DesktopRepositoryStore,
) : PlaybackPreferencesRepository {
    override suspend fun getEndAction(): PlaybackEndAction =
        store.read { state -> state.playbackEndAction }

    override suspend fun setEndAction(action: PlaybackEndAction) {
        store.update { state -> state.copy(playbackEndAction = action) to Unit }
    }
}
