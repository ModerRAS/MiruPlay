package com.miruplay.tv.player

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import `is`.xyz.mpv.subtitle.NativeAssRenderer
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
    fun provideAudioDspRuntimeConfig(): AudioDspRuntimeConfig = AudioDspRuntimeConfig()

    @Provides
    @Singleton
    @StandardPlaybackPlayer
    fun provideStandardExoPlayer(
        @ApplicationContext context: Context,
        dataSourceFactory: PlaybackDataSourceFactory,
        audioDspRuntimeConfig: AudioDspRuntimeConfig,
        playbackDebugOverrides: PlaybackDebugOverrides,
    ): ExoPlayer {
        val libassSession = LibassSubtitleSession(
            monitorProvider = libassSubtitleMonitorProvider {
                playbackDebugOverrides.libassSubtitleMonitorEnabled
            },
        )
        val nativeAssAvailable = NativeAssRenderer.isAvailable()
        val renderersFactory = DspRenderersFactory(context, audioDspRuntimeConfig, libassSession)
            .forceDisableMediaCodecAsynchronousQueueing()
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(PlaybackMediaCodecSelector)

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    ZlibSubtitleProtectingDataSourceFactory(dataSourceFactory),
                    ZlibSubtitleExtractorsFactory(
                        session = libassSession,
                        nativeAvailable = { nativeAssAvailable },
                    ),
                ).experimentalParseSubtitlesDuringExtraction(false),
            )
            .build()
            .also { player ->
                LibassSubtitleRegistry.register(player, libassSession)
                if (nativeAssAvailable) player.setVideoFrameMetadataListener(libassSession)
            }
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
        dataSourceFactory: PlaybackDataSourceFactory,
        httpRequestResolver: PlaybackHttpRequestResolver,
        playbackPreferencesRepository: PlaybackPreferencesRepository,
        playbackDebugOverrides: PlaybackDebugOverrides,
        externalMpvLauncher: AndroidExternalMpvLauncher,
        config: PlaybackConfig,
        audioDspRuntimeConfig: AudioDspRuntimeConfig,
    ): ExoPlaybackController {
        return ExoPlaybackController(
            context = context,
            standardExoPlayerProvider = standardPlayerProvider,
            dataSourceFactory = dataSourceFactory,
            httpRequestResolver = httpRequestResolver,
            playbackPreferencesRepository = playbackPreferencesRepository,
            playbackDebugOverrides = playbackDebugOverrides,
            externalMpvLauncher = externalMpvLauncher,
            config = config,
            audioDspRuntimeConfig = audioDspRuntimeConfig,
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
