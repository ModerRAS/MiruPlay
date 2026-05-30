package com.miruplay.tv.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerFactoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayerProvider: Provider<ExoPlayer>,
    private val dataSourceFactory: PlaybackDataSourceFactory,
    private val httpRequestResolver: PlaybackHttpRequestResolver,
) : PlayerFactory {
    override fun create(config: PlaybackConfig): PlaybackController {
        val player = exoPlayerProvider.get()
        return ExoPlaybackController(
            context = context,
            exoPlayer = player,
            dataSourceFactory = dataSourceFactory,
            httpRequestResolver = httpRequestResolver,
            config = config,
        )
    }
}
