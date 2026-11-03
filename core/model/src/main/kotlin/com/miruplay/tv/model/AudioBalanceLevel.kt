package com.miruplay.tv.model

import kotlinx.serialization.Serializable

/** Exo/Media3 PCM loudness balance strength; IJK and MPV do not apply this today. */
@Serializable
enum class AudioBalanceLevel(val storageValue: String) {
    OFF("off"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    val isEnabled: Boolean
        get() = this != OFF

    companion object {
        fun fromStorageValue(value: String?): AudioBalanceLevel =
            entries.firstOrNull { it.storageValue.equals(value?.trim()?.lowercase(), ignoreCase = true) }
                ?: OFF
    }
}
