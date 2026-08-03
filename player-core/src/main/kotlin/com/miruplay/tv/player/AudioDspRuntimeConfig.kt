package com.miruplay.tv.player

import com.miruplay.tv.model.AudioDspConfig

class AudioDspRuntimeConfig {
    @Volatile
    var config: AudioDspConfig = AudioDspConfig.neutral()
        private set

    fun update(value: AudioDspConfig) {
        config = value.normalized()
    }
}
