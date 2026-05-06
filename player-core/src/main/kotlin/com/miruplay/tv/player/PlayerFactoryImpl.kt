package com.miruplay.tv.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerFactoryImpl @Inject constructor(
    private val exoPlayerProvider: Provider<ExoPlayer>
) : PlayerFactory {
    override fun create(config: PlaybackConfig): PlaybackController {
        val player = exoPlayerProvider.get()
        return ExoPlaybackController(player, config)
    }
}
