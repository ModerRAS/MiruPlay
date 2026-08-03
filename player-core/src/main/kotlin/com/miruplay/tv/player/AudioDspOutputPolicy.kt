package com.miruplay.tv.player

import com.miruplay.tv.model.AudioDspConfig

data class AudioDspOutputPolicy(
    val forcePcm: Boolean,
    val allowOffload: Boolean,
    val allowPassthrough: Boolean,
    val allowTunneling: Boolean,
) {
    companion object {
        fun forConfig(config: AudioDspConfig): AudioDspOutputPolicy =
            if (config.enabled) {
                AudioDspOutputPolicy(
                    forcePcm = true,
                    allowOffload = false,
                    allowPassthrough = false,
                    allowTunneling = false,
                )
            } else {
                AudioDspOutputPolicy(
                    forcePcm = false,
                    allowOffload = true,
                    allowPassthrough = true,
                    allowTunneling = true,
                )
            }
    }
}
