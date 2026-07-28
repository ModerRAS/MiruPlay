package com.miruplay.tv.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.PlaybackException
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.miruplay.tv.core.common.logging.MiruLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Provider

@UnstableApi
@AndroidEntryPoint
class MiruPlayMediaService : MediaSessionService() {

    @Inject
    @StandardPlaybackPlayer
    lateinit var exoPlayerProvider: Provider<ExoPlayer>

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            MiruLog.d(
                "MiruPlayMediaService",
                "Player playback state changed",
                mapOf("playback_state" to playbackState.label())
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            MiruLog.e(
                "MiruPlayMediaService",
                "Player error",
                error,
                mapOf(
                    "error_code" to error.errorCode.toString(),
                    "error_code_name" to error.errorCodeName,
                )
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        MiruLog.i("MiruPlayMediaService", "Media service created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        ensureMediaSession()
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        MiruLog.i(
            "MiruPlayMediaService",
            "Media service task removed",
            mapOf(
                "has_player" to (player != null).toString(),
                "play_when_ready" to (player?.playWhenReady ?: false).toString(),
            )
        )
        if (player != null && !player.playWhenReady) {
            // Stop service if not playing when task is removed
            stopSelf()
        }
    }

    override fun onDestroy() {
        MiruLog.i("MiruPlayMediaService", "Media service destroying")
        mediaSession?.run {
            release()
            mediaSession = null
        }
        exoPlayer?.removeListener(playerListener)
        exoPlayer = null
        super.onDestroy()
    }

    private fun ensureMediaSession() {
        if (mediaSession != null) {
            return
        }
        val player = exoPlayer ?: exoPlayerProvider.get().also { createdPlayer ->
            createdPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            createdPlayer.addListener(playerListener)
            exoPlayer = createdPlayer
            MiruLog.i("MiruPlayMediaService", "Standard Exo player created for media session")
        }
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(createSessionActivityPendingIntent())
            .build()
        MiruLog.i("MiruPlayMediaService", "Media session created")
    }

    private fun Int.label(): String = when (this) {
        Player.STATE_IDLE -> "idle"
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        else -> "unknown_$this"
    }

    private fun createSessionActivityPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().also { it.setPackage(packageName) }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
