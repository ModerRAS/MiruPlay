package com.miruplay.tv.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        dataSourceFactory: PlaybackDataSourceFactory,
    ): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    @Provides
    @Singleton
    fun providePlaybackConfig(): PlaybackConfig = PlaybackConfig()

    @Provides
    @Singleton
    fun providePlaybackController(
        @ApplicationContext context: Context,
        player: ExoPlayer,
        dataSourceFactory: PlaybackDataSourceFactory,
        httpRequestResolver: PlaybackHttpRequestResolver,
        config: PlaybackConfig
    ): PlaybackController {
        return ExoPlaybackController(
            context = context,
            exoPlayer = player,
            dataSourceFactory = dataSourceFactory,
            httpRequestResolver = httpRequestResolver,
            config = config,
        )
    }
}
