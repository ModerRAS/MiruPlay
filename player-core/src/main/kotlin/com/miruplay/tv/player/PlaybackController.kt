package com.miruplay.tv.player

import android.view.View
import androidx.media3.common.Player
import com.miruplay.tv.model.MpvNativeDiagnostics
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.VideoSignalDescriptor
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

    /**
     * True when the current active backend expects a VLC render host instead of PlayerView.
     */
    fun usesVlcVideoLayout(): Boolean

    /**
     * Bind the backend to a VLC render host when active.
     */
    fun bindVlcVideoHost(hostView: View)

    /**
     * True when a native video host should be rebound after Compose creates or lays it out.
     */
    fun needsVlcVideoHostBinding(): Boolean = false

    /**
     * Release the currently bound VLC render host, if any.
     */
    fun unbindVlcVideoHost()

    /**
     * Active backend after runtime fallback handling.
     */
    val activeRenderBackend: StateFlow<PlaybackRenderBackend>

    /**
     * Requested backend before runtime fallback handling.
     */
    val requestedRenderBackend: StateFlow<PlaybackRenderBackend>

    /**
     * Current detected video signal descriptor.
     */
    val currentVideoSignalDescriptor: StateFlow<VideoSignalDescriptor?>

    /**
     * Current applied render rule key.
     */
    val currentRenderRuleKey: StateFlow<VideoRenderRuleKey>

    /**
     * Current applied rule set, including session overrides if present.
     */
    val currentToneMappingRuleSet: StateFlow<ToneMappingRuleSet>

    /**
     * Session-only rule overrides keyed by signal family.
     */
    val sessionRuleOverrides: StateFlow<Map<VideoRenderRuleKey, ToneMappingRuleSet>>

    /**
     * Human-readable fallback reason when the requested backend cannot stay active.
     */
    val fallbackReason: StateFlow<String?>

    /**
     * Update the requested backend for the current session.
     */
    suspend fun setRequestedRenderBackend(backend: PlaybackRenderBackend?)

    /**
     * Update or clear the current session rule override for a format family.
     */
    suspend fun setSessionRuleOverride(ruleKey: VideoRenderRuleKey, ruleSet: ToneMappingRuleSet?)

    /**
     * Clear all session-only rule overrides.
     */
    suspend fun clearSessionRuleOverrides()

    /**
     * Re-apply runtime debug/session options to the active backend when supported.
     */
    suspend fun refreshActivePlaybackDebugConfig()

    /**
     * Peek a pending debug GL frame capture label, if any.
     */
    fun pendingGlFrameCaptureLabel(): String?

    /**
     * Clear the pending debug GL frame capture label after a successful capture.
     */
    fun clearPendingGlFrameCaptureLabel(label: String)

    /**
     * Peek a pending libVLC native snapshot label, if any.
     */
    fun pendingLibVlcNativeSnapshotLabel(): String?

    /**
     * Request a libVLC native snapshot for the provided label.
     */
    fun requestLibVlcNativeSnapshot(label: String)

    /**
     * Returns the active libVLC debug vout mode when the current backend uses libVLC.
     */
    fun currentLibVlcVoutMode(): LibVlcVoutMode?

    /**
     * Clear the pending libVLC native snapshot label after success or when callers need to unblock
     * alternative verification paths.
     */
    fun clearPendingLibVlcNativeSnapshotLabel(label: String)

    /**
     * Recent playback clock samples captured from the active backend, newest last.
     */
    fun recentPlaybackClockSamples(limit: Int = 120): List<PlaybackClockSample>

    /**
     * Current embedded mpv native diagnostics snapshot when an embedded mpv view exists.
     */
    fun currentMpvNativeDiagnostics(logLimit: Int = 80): MpvNativeDiagnostics?

}

/**
 * Backend-reported playback clock sample.
 */
data class PlaybackClockSample(
    val monotonicTimestampMs: Long,
    val positionMs: Long,
    val durationMs: Long,
    val paused: Boolean,
    val eofReached: Boolean,
)

/**
 * Audio track information
 */
data class AudioTrack(
    val index: Int,
    val language: String,
    val title: String? = null,
    val codec: String? = null,
)
