package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
enum class MusicSrcBypassMode(val storageValue: String) {
    SYSTEM("system"),
    SOFTWARE("software"),
    DIRECT("direct");

    companion object {
        fun fromStorageValue(value: String?): MusicSrcBypassMode =
            entries.firstOrNull { it.storageValue.equals(value?.trim()?.lowercase(), ignoreCase = true) } ?: SOFTWARE
    }
}
