package com.miruplay.tv.model

enum class PlaybackEndAction(val storageValue: String) {
    RETURN_TO_DETAIL("return_to_detail"),
    PLAY_NEXT_EPISODE("play_next_episode");

    companion object {
        fun fromStorageValue(value: String?): PlaybackEndAction =
            entries.firstOrNull { it.storageValue == value } ?: RETURN_TO_DETAIL
    }
}
