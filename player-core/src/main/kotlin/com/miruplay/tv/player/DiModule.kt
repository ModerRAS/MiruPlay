package com.miruplay.tv.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer {
        return ExoPlayer.Builder(context).build()
    }

    @Provides
    @Singleton
    fun providePlaybackConfig(): PlaybackConfig = PlaybackConfig()

    @Provides
    @Singleton
    fun providePlaybackController(
        @ApplicationContext context: Context,
        player: ExoPlayer,
        config: PlaybackConfig
    ): PlaybackController {
        return ExoPlaybackController(context, player, config)
    }
}
