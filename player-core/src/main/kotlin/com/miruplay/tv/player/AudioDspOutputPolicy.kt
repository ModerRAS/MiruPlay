package com.miruplay.tv.player

import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.MusicSrcBypassMode

data class AudioDspOutputPolicy(
    val forcePcm: Boolean,
    val allowOffload: Boolean,
    val allowPassthrough: Boolean,
    val allowTunneling: Boolean,
) {
    companion object {
        fun forMusic(config: AudioDspConfig, mode: MusicSrcBypassMode): AudioDspOutputPolicy =
            when (mode) {
                MusicSrcBypassMode.SYSTEM -> forConfig(config)
                MusicSrcBypassMode.SOFTWARE -> AudioDspOutputPolicy(
                    forcePcm = true,
                    allowOffload = false,
                    allowPassthrough = false,
                    allowTunneling = false,
                )
                MusicSrcBypassMode.DIRECT -> if (!config.enabled) {
                    AudioDspOutputPolicy(
                        forcePcm = false,
                        allowOffload = true,
                        allowPassthrough = true,
                        allowTunneling = true,
                    )
                } else {
                    // DIRECT with DSP cannot offload; fallback to SOFTWARE high-quality soft SRC
                    AudioDspOutputPolicy(
                        forcePcm = true,
                        allowOffload = false,
                        allowPassthrough = false,
                        allowTunneling = false,
                    )
                }
            }

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
