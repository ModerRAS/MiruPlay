package com.miruplay.tv.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.SubtitleFormat
import com.miruplay.tv.model.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class ExoPlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val config: PlaybackConfig = PlaybackConfig()
) : PlaybackController {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val availableSubtitles = mutableListOf<SubtitleTrack>()
    private val availableAudioTracks = mutableListOf<AudioTrack>()
    private var currentSource: PlaybackSource? = null
    private var autoResumeSeekCalled = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val source = currentSource
            when (playbackState) {
                Player.STATE_READY -> {
                    if (source == null) return
                    val position = exoPlayer.currentPosition
                    when (_state.value) {
                        is PlaybackState.Buffering -> {
                            if (autoResumeSeekCalled) {
                                autoResumeSeekCalled = false
                            }
                            _state.value = PlaybackState.Playing(source, position)
                        }
                        is PlaybackState.Playing -> {
                            // Position update
                        }
                        else -> {
                            if (exoPlayer.playWhenReady) {
                                _state.value = PlaybackState.Playing(source, position)
                            }
                        }
                    }
                }
                Player.STATE_BUFFERING -> {
                    if (source == null) return
                    val current = _state.value
                    if (current is PlaybackState.Playing || current is PlaybackState.Paused) {
                        val currentPosition = when (current) {
                            is PlaybackState.Playing -> current.position
                            is PlaybackState.Paused -> current.position
                            is PlaybackState.Buffering -> current.position
                            else -> 0L
                        }
                        _state.value = PlaybackState.Buffering(source, currentPosition)
                    }
                }
                Player.STATE_ENDED -> {
                    source?.let { _state.value = PlaybackState.Ended(it) }
                }
                Player.STATE_IDLE -> {
                    // Player is idle
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val source = currentSource
            if (source != null) {
                val position = exoPlayer.currentPosition
                _state.value = if (exoPlayer.playbackState == Player.STATE_ENDED) {
                    PlaybackState.Ended(source)
                } else if (isPlaying) {
                    PlaybackState.Playing(source, position)
                } else {
                    if (_state.value is PlaybackState.Buffering) {
                        PlaybackState.Buffering(source, position)
                    } else {
                        PlaybackState.Paused(source, position)
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val source = currentSource
            _state.value = PlaybackState.Error(source, error.localizedMessage ?: "Playback error")
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateAvailableTracks()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (autoResumeSeekCalled) {
                autoResumeSeekCalled = false
            }
        }
    }

    init {
        exoPlayer.addListener(listener)
    }

    override suspend fun play(source: PlaybackSource) {
        withContext(Dispatchers.Main) {
            currentSource = source
            _state.value = PlaybackState.Loading(source)

            try {
                ensureMediaSessionService()

                // Build MediaItem with subtitle configurations
                val subtitleConfigs = source.subtitleTracks.map { track ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.path))
                        .setMimeType(mimeTypeForFormat(track.format))
                        .setLanguage(track.language)
                        .setLabel(track.title.ifEmpty { track.language })
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(source.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(source.uri.substringAfterLast("/").substringBeforeLast("."))
                            .setArtist(source.mediaSourceId)
                            .build()
                    )
                    .setSubtitleConfigurations(subtitleConfigs)
                    .build()

                exoPlayer.setMediaItem(mediaItem)
                if (source.startPosition > 0) {
                    exoPlayer.seekTo(source.startPosition)
                    autoResumeSeekCalled = true
                }
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } catch (e: Exception) {
                _state.value = PlaybackState.Error(source, e.message ?: "Failed to play")
            }
        }
    }

    private fun ensureMediaSessionService() {
        runCatching {
            context.startService(Intent(context, MiruPlayMediaService::class.java))
        }
    }

    override suspend fun pause() {
        withContext(Dispatchers.Main) {
            exoPlayer.playWhenReady = false
        }
    }

    override suspend fun resume() {
        withContext(Dispatchers.Main) {
            exoPlayer.playWhenReady = true
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        withContext(Dispatchers.Main) {
            exoPlayer.seekTo(positionMs)
            val source = currentSource
            if (source != null) {
                val newState = when (_state.value) {
                    is PlaybackState.Playing -> PlaybackState.Playing(source, positionMs)
                    is PlaybackState.Paused -> PlaybackState.Paused(source, positionMs)
                    is PlaybackState.Buffering -> PlaybackState.Buffering(source, positionMs)
                    else -> _state.value
                }
                _state.value = newState
            }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main) {
            exoPlayer.stop()
            exoPlayer.playWhenReady = false
            currentSource?.let { _state.value = PlaybackState.Ended(it) }
        }
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        withContext(Dispatchers.Main) {
            exoPlayer.setPlaybackSpeed(speed.coerceIn(0.25f, 3.0f))
        }
    }

    override suspend fun setSubtitleTrack(trackIndex: Int) {
        // Subtitle selection implementation
        updateAvailableTracks()
    }

    override suspend fun setAudioTrack(trackIndex: Int) {
        // Audio track selection implementation
        updateAvailableTracks()
    }

    override fun getAvailableSubtitles(): List<SubtitleTrack> = availableSubtitles.toList()

    override fun getAvailableAudioTracks(): List<AudioTrack> = availableAudioTracks.toList()

    override suspend fun getCurrentPosition(): Long = withContext(Dispatchers.Main) {
        exoPlayer.currentPosition
    }

    override suspend fun getDuration(): Long = withContext(Dispatchers.Main) {
        exoPlayer.duration
    }

    override fun isPlaying(): Boolean = exoPlayer.isPlaying

    override fun getPlayer(): androidx.media3.common.Player? = exoPlayer

    fun release() {
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }

    private fun updateAvailableTracks() {
        availableSubtitles.clear()
        availableAudioTracks.clear()
        
        try {
            val tracks = exoPlayer.currentTracks
            for (i in 0 until tracks.groups.size) {
                val group = tracks.groups[i]
                when (group.type) {
                    C.TRACK_TYPE_TEXT -> {
                        val format = group.getTrackFormat(0)
                        availableSubtitles.add(
                            SubtitleTrack(
                                language = format.language ?: "und",
                                title = format.label ?: "",
                                isExternal = false,
                                path = "",
                                format = SubtitleFormat.SRT
                            )
                        )
                    }
                    C.TRACK_TYPE_AUDIO -> {
                        val format = group.getTrackFormat(0)
                        availableAudioTracks.add(
                            AudioTrack(
                                index = availableAudioTracks.size,
                                language = format.language ?: "und",
                                title = format.label,
                                codec = format.codecs
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore track enumeration errors
        }
    }

    private fun mimeTypeForFormat(format: SubtitleFormat): String {
        return when (format) {
            SubtitleFormat.SRT -> "application/x-subrip"
            SubtitleFormat.ASS, SubtitleFormat.SSA -> "text/x-ass"
            SubtitleFormat.VTT -> "text/vtt"
            else -> "application/x-subrip"
        }
    }
}

// Type alias for Media3 Tracks
typealias Tracks = androidx.media3.common.Tracks
