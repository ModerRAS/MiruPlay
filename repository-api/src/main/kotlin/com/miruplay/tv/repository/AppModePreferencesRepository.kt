package com.miruplay.tv.repository

import com.miruplay.tv.model.MediaContentMode

enum class AppMode(val storageValue: String) {
    ANIME("anime"),
    DRAMA("drama");

    companion object {
        fun fromStorageValue(value: String?): AppMode? =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() }
    }
}

fun AppMode.toMediaContentMode(): MediaContentMode =
    when (this) {
        AppMode.ANIME -> MediaContentMode.ANIME
        AppMode.DRAMA -> MediaContentMode.DRAMA
    }

data class AppModeSelectionState(
    val currentAppMode: AppMode? = null,
    val hasCompletedModeSelection: Boolean = false,
)

interface AppModePreferencesRepository {
    suspend fun getSelectionState(): AppModeSelectionState
    suspend fun completeModeSelection(mode: AppMode)
    suspend fun setCurrentAppMode(mode: AppMode)
}
