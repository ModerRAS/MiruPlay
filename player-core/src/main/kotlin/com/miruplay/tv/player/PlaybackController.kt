package com.miruplay.tv.player

import androidx.media3.common.Player
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.SubtitleTrack
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract playback controller interface.
 * Implementations wrap ExoPlayer/Media3 for Android TV.
 */
interface PlaybackController {
    /**
     * Observable playback state
     */
    val state: StateFlow<PlaybackState>

    /**
     * Start playing the given source
     */
    suspend fun play(source: PlaybackSource)

    /**
     * Pause current playback
     */
    suspend fun pause()

    /**
     * Resume from paused state
     */
    suspend fun resume()

    /**
     * Seek to position in milliseconds
     */
    suspend fun seekTo(positionMs: Long)

    /**
     * Stop and release playback resources
     */
    suspend fun stop()

    /**
     * Set playback speed (0.5x - 3.0x)
     */
    suspend fun setPlaybackSpeed(speed: Float)

    /**
     * Select subtitle track by index
     */
    suspend fun setSubtitleTrack(trackIndex: Int)

    /**
     * Select audio track by index
     */
    suspend fun setAudioTrack(trackIndex: Int)

    /**
     * Get available subtitle tracks
     */
    fun getAvailableSubtitles(): List<SubtitleTrack>

    /**
     * Get available audio tracks
     */
    fun getAvailableAudioTracks(): List<AudioTrack>

    /**
     * Get current playback position in milliseconds
     */
    suspend fun getCurrentPosition(): Long

    /**
     * Get total duration in milliseconds
     */
    suspend fun getDuration(): Long

    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean

    /**
     * Get the underlying Media3 Player for UI integration (PlayerView)
     */
    fun getPlayer(): Player?
}

/**
 * Audio track information
 */
data class AudioTrack(
    val index: Int,
    val language: String,
    val title: String? = null,
    val codec: String? = null,
)
