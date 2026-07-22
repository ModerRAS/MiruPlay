package com.miruplay.tv.model

enum class EpisodeVersionSelectionPolicy(val storageValue: String) {
    AUTO_NEAREST("auto_nearest"),
    MANUAL("manual");

    companion object {
        fun fromStorageValue(value: String?): EpisodeVersionSelectionPolicy =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: AUTO_NEAREST
    }
}
