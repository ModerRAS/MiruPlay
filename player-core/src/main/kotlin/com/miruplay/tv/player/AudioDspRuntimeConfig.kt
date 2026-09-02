package com.miruplay.tv.player

import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.MusicSrcBypassMode

class AudioDspRuntimeConfig {
    data class Snapshot(
        val config: AudioDspConfig,
        val revision: Long,
    )

    @Volatile
    private var state = Snapshot(AudioDspConfig.neutral(), 0L)

    @Volatile
    private var musicBypassModeState: MusicSrcBypassMode = MusicSrcBypassMode.SOFTWARE

    val config: AudioDspConfig
        get() = state.config

    val revision: Long
        get() = state.revision

    val musicBypassMode: MusicSrcBypassMode
        get() = musicBypassModeState

    fun snapshot(): Snapshot = state

    fun update(value: AudioDspConfig) {
        val normalized = value.normalized()
        while (true) {
            val current = state
            val next = Snapshot(normalized, current.revision + 1L)
            synchronized(this) {
                if (state === current) {
                    state = next
                    return
                }
            }
        }
    }

    fun updateMusicMode(mode: MusicSrcBypassMode) {
        musicBypassModeState = mode
    }
}
