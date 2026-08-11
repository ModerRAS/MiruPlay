package com.miruplay.tv.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@UnstableApi
@Singleton
class PlayerFactoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @StandardPlaybackPlayer
    private val standardExoPlayerProvider: Provider<ExoPlayer>,
    private val dataSourceFactory: PlaybackDataSourceFactory,
    private val httpRequestResolver: PlaybackHttpRequestResolver,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val playbackDebugOverrides: PlaybackDebugOverrides,
    private val externalMpvLauncher: AndroidExternalMpvLauncher,
) : PlayerFactory {
    override fun create(config: PlaybackConfig): PlaybackController {
        return ExoPlaybackController(
            context = context,
            standardExoPlayerProvider = standardExoPlayerProvider,
            dataSourceFactory = dataSourceFactory,
            httpRequestResolver = httpRequestResolver,
            playbackPreferencesRepository = playbackPreferencesRepository,
            playbackDebugOverrides = playbackDebugOverrides,
            externalMpvLauncher = externalMpvLauncher,
            config = config,
        )
    }
}
