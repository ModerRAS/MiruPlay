package com.miruplay.tv.player

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.miruplay.tv.repository.PlaybackPreferencesRepository
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
    @StandardPlaybackPlayer
    fun provideStandardExoPlayer(
        @ApplicationContext context: Context,
        dataSourceFactory: PlaybackDataSourceFactory,
    ): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .forceDisableMediaCodecAsynchronousQueueing()
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(PlaybackMediaCodecSelector)

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    ZlibSubtitleProtectingDataSourceFactory(dataSourceFactory),
                    ZlibSubtitleExtractorsFactory(),
                ),
            )
            .build()
    }

    @Provides
    @Singleton
    @ExperimentalPlaybackPlayer
    fun provideExperimentalExoPlayer(
        @ApplicationContext context: Context,
        dataSourceFactory: PlaybackDataSourceFactory,
    ): ExoPlayer {
        val renderersFactory = ExperimentalRenderersFactory(context)
            // The experimental HDR backend relies on stable HEVC surface attachment across
            // vendor codecs, so we bias toward compatibility over async throughput here.
            .forceDisableMediaCodecAsynchronousQueueing()
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(PlaybackMediaCodecSelector)

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    ZlibSubtitleProtectingDataSourceFactory(dataSourceFactory),
                    ZlibSubtitleExtractorsFactory(),
                ),
            )
            .build()
    }

    @Provides
    @Singleton
    fun providePlaybackConfig(): PlaybackConfig = PlaybackConfig()

    @Provides
    @Singleton
    fun provideExoPlaybackController(
        @ApplicationContext context: Context,
        @StandardPlaybackPlayer
        standardPlayerProvider: javax.inject.Provider<ExoPlayer>,
        @ExperimentalPlaybackPlayer
        experimentalPlayerProvider: javax.inject.Provider<ExoPlayer>,
        dataSourceFactory: PlaybackDataSourceFactory,
        httpRequestResolver: PlaybackHttpRequestResolver,
        playbackPreferencesRepository: PlaybackPreferencesRepository,
        playbackDebugOverrides: PlaybackDebugOverrides,
        externalMpvLauncher: AndroidExternalMpvLauncher,
        config: PlaybackConfig,
    ): ExoPlaybackController {
        return ExoPlaybackController(
            context = context,
            standardExoPlayerProvider = standardPlayerProvider,
            experimentalExoPlayerProvider = experimentalPlayerProvider,
            dataSourceFactory = dataSourceFactory,
            httpRequestResolver = httpRequestResolver,
            playbackPreferencesRepository = playbackPreferencesRepository,
            playbackDebugOverrides = playbackDebugOverrides,
            externalMpvLauncher = externalMpvLauncher,
            config = config,
        )
    }

    @Provides
    @Singleton
    fun providePlaybackController(
        exoController: ExoPlaybackController,
    ): PlaybackController = exoController

    @Provides
    @Singleton
    fun providePlayerFactory(
        playerFactoryImpl: PlayerFactoryImpl,
    ): PlayerFactory {
        return playerFactoryImpl
    }
}
