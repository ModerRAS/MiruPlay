package com.miruplay.tv.player.ijk.android

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.IOException
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.misc.IAndroidIO
import tv.danmaku.ijk.media.player.misc.ITrackInfo

interface MiruIjkAndroidIo {
    @Throws(IOException::class)
    fun open(url: String): Int

    @Throws(IOException::class)
    fun read(buffer: ByteArray, size: Int): Int

    @Throws(IOException::class)
    fun seek(offset: Long, whence: Int): Long

    @Throws(IOException::class)
    fun close(): Int
}

data class MiruIjkPlaybackRequest(
    val uri: String,
    val startPositionMs: Long = 0L,
    val headers: Map<String, String> = emptyMap(),
    val androidIo: MiruIjkAndroidIo? = null,
    val hardwareDecode: Boolean = true,
    val audioDspOptions: Map<String, String> = emptyMap(),
)

data class MiruIjkAudioTrack(
    val rawStreamIndex: Int,
    val language: String,
    val details: String,
)

interface MiruIjkPlayerListener {
    fun onPrepared(durationMs: Long, width: Int, height: Int)
    fun onBufferingChanged(buffering: Boolean)
    fun onCompletion()
    fun onError(code: Int, extra: Int)
    fun onAudioTracksChanged(tracks: List<MiruIjkAudioTrack>, selectedRawStreamIndex: Int?)
}

class MiruIjkSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    var listener: MiruIjkPlayerListener? = null

    private var player: IjkMediaPlayer? = null
    private var request: MiruIjkPlaybackRequest? = null
    private var androidIoAdapter: IAndroidIO? = null
    private var surfaceReady = false

    init {
        holder.addCallback(this)
        isFocusable = false
        isFocusableInTouchMode = false
    }

    fun load(request: MiruIjkPlaybackRequest) {
        releasePlayer()
        this.request = request
        if (surfaceReady) prepare(request)
    }

    fun pausePlayback() {
        player?.takeIf(IjkMediaPlayer::isPlaying)?.pause()
    }

    fun resumePlayback() {
        player?.start()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.setSpeed(speed.coerceIn(0.25f, 3.0f))
    }

    fun currentPositionMs(): Long = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun durationMs(): Long = player?.duration?.coerceAtLeast(0L) ?: 0L

    fun isPlaybackActive(): Boolean = player?.isPlaying == true

    fun selectedAudioRawStreamIndex(): Int? =
        player?.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO)?.takeIf { it >= 0 }

    fun audioTracks(): List<MiruIjkAudioTrack> =
        player?.trackInfo.orEmpty().mapIndexedNotNull { rawIndex, track ->
            if (track.trackType != ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) return@mapIndexedNotNull null
            MiruIjkAudioTrack(
                rawStreamIndex = rawIndex,
                language = track.language ?: "und",
                details = track.infoInline.orEmpty(),
            )
        }

    fun selectAudioRawStream(rawStreamIndex: Int) {
        val mediaPlayer = player ?: return
        val previous = selectedAudioRawStreamIndex()
        if (previous != null && previous != rawStreamIndex) mediaPlayer.deselectTrack(previous)
        mediaPlayer.selectTrack(rawStreamIndex)
        publishAudioTracks()
    }

    fun releasePlayer() {
        request = null
        androidIoAdapter = null
        player?.runCatching {
            setDisplay(null)
            release()
        }
        player = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        val activePlayer = player
        if (activePlayer != null) {
            activePlayer.setDisplay(holder)
        } else {
            request?.let(::prepare)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        player?.setDisplay(null)
    }

    private fun prepare(request: MiruIjkPlaybackRequest) {
        val mediaPlayer = IjkMediaPlayer().also { player = it }
        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", if (request.hardwareDecode) 1L else 0L)
        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L)
        request.audioDspOptions.forEach { (name, value) ->
            mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, name, value)
        }
        mediaPlayer.setScreenOnWhilePlaying(true)
        mediaPlayer.setDisplay(holder)
        mediaPlayer.setOnPreparedListener { prepared ->
            if (request.startPositionMs > 0L) prepared.seekTo(request.startPositionMs)
            prepared.start()
            listener?.onPrepared(prepared.duration, prepared.videoWidth, prepared.videoHeight)
            publishAudioTracks()
        }
        mediaPlayer.setOnCompletionListener { listener?.onCompletion() }
        mediaPlayer.setOnInfoListener { _, what, _ ->
            when (what) {
                IMediaPlayer.MEDIA_INFO_BUFFERING_START -> listener?.onBufferingChanged(true)
                IMediaPlayer.MEDIA_INFO_BUFFERING_END -> listener?.onBufferingChanged(false)
            }
            false
        }
        mediaPlayer.setOnErrorListener { _, what, extra ->
            listener?.onError(what, extra)
            true
        }
        request.androidIo?.let { bridge ->
            androidIoAdapter = object : IAndroidIO {
                override fun open(url: String): Int = bridge.open(url)
                override fun read(buffer: ByteArray, size: Int): Int = bridge.read(buffer, size)
                override fun seek(offset: Long, whence: Int): Long = bridge.seek(offset, whence)
                override fun close(): Int = bridge.close()
            }
            mediaPlayer.setAndroidIOCallback(androidIoAdapter)
            mediaPlayer.setDataSource("ijkio:androidio:${request.uri}")
        } ?: run {
            val parsed = Uri.parse(request.uri)
            if (parsed.scheme.equals("content", ignoreCase = true) ||
                parsed.scheme.equals("file", ignoreCase = true)
            ) {
                mediaPlayer.setDataSource(context, parsed, request.headers)
            } else if (parsed.scheme.isNullOrBlank()) {
                mediaPlayer.setDataSource(request.uri)
            } else {
                mediaPlayer.setDataSource(request.uri, request.headers)
            }
        }
        mediaPlayer.prepareAsync()
    }

    private fun publishAudioTracks() {
        listener?.onAudioTracksChanged(audioTracks(), selectedAudioRawStreamIndex())
    }
}
