package com.miruplay.tv.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.MpvNativeDiagnostics
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.SubtitleFormat
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.model.defaultToneMappingRuleSet
import com.miruplay.tv.model.normalizeSupportedBackend
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import `is`.xyz.mpv.MiruMpvSurfaceView
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Provider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ExoTrackSelection(
    val group: Tracks.Group,
    val trackIndex: Int,
)

@UnstableApi
@Singleton
class ExoPlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    @StandardPlaybackPlayer
    private val standardExoPlayerProvider: Provider<ExoPlayer>,
    @ExperimentalPlaybackPlayer
    private val experimentalExoPlayerProvider: Provider<ExoPlayer>,
    private val dataSourceFactory: PlaybackDataSourceFactory,
    private val httpRequestResolver: PlaybackHttpRequestResolver,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val playbackDebugOverrides: PlaybackDebugOverrides,
    private val externalMpvLauncher: AndroidExternalMpvLauncher,
    private val config: PlaybackConfig = PlaybackConfig(),
) : PlaybackController {
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _requestedRenderBackend = MutableStateFlow(PlaybackRenderBackend.STANDARD_EXO)
    override val requestedRenderBackend: StateFlow<PlaybackRenderBackend> = _requestedRenderBackend.asStateFlow()
    private val _activeRenderBackend = MutableStateFlow(PlaybackRenderBackend.STANDARD_EXO)
    override val activeRenderBackend: StateFlow<PlaybackRenderBackend> = _activeRenderBackend.asStateFlow()
    private val _currentVideoSignalDescriptor = MutableStateFlow<VideoSignalDescriptor?>(null)
    override val currentVideoSignalDescriptor: StateFlow<VideoSignalDescriptor?> = _currentVideoSignalDescriptor.asStateFlow()
    private val _currentRenderRuleKey = MutableStateFlow(VideoRenderRuleKey.SDR)
    override val currentRenderRuleKey: StateFlow<VideoRenderRuleKey> = _currentRenderRuleKey.asStateFlow()
    private val _currentToneMappingRuleSet = MutableStateFlow(defaultToneMappingRuleSet(VideoRenderRuleKey.SDR))
    override val currentToneMappingRuleSet: StateFlow<ToneMappingRuleSet> = _currentToneMappingRuleSet.asStateFlow()
    private val _sessionRuleOverrides = MutableStateFlow<Map<VideoRenderRuleKey, ToneMappingRuleSet>>(emptyMap())
    override val sessionRuleOverrides: StateFlow<Map<VideoRenderRuleKey, ToneMappingRuleSet>> = _sessionRuleOverrides.asStateFlow()
    private val _fallbackReason = MutableStateFlow<String?>(null)
    override val fallbackReason: StateFlow<String?> = _fallbackReason.asStateFlow()

    private val availableSubtitles = mutableListOf<SubtitleTrack>()
    private val availableAudioTracks = mutableListOf<AudioTrack>()
    private val exoSubtitleSelections = mutableListOf<ExoTrackSelection>()
    private val exoAudioSelections = mutableListOf<ExoTrackSelection>()
    private val embeddedSubtitleTrackIds = mutableListOf<Int>()
    private var selectedSubtitleTrackIndex: Int? = null
    private var selectedAudioTrackIndex: Int? = null
    private var currentSource: PlaybackSource? = null
    private var autoResumeSeekCalled = false
    private var playbackPreferences = FormatAwareToneMappingPreferences()
    private var containerSignalDescriptor: VideoSignalDescriptor? = null
    private var sessionState = PlaybackSessionState()
    private val deviceGlEsMajorVersion = resolveDeviceGlEsMajorVersion(context)
    private var signalProbeCompletionJob: Job? = null
    private var externalMpvPlaying: Boolean = false
    private var externalMpvPositionMs: Long = 0L
    private var externalMpvSource: PlaybackSource? = null
    private var embeddedMpvPlaying: Boolean = false
    private var embeddedMpvPositionMs: Long = 0L
    private var embeddedMpvDurationMs: Long = 0L
    private var embeddedMpvSource: PlaybackSource? = null
    private var embeddedMpvHostView: ViewGroup? = null
    private var embeddedMpvView: MiruMpvSurfaceView? = null
    private var embeddedMpvPendingLoad: Boolean = false
    private var embeddedMpvPlaybackSpeed: Float = 1.0f
    // ponytail: copy-on-write 避免跨线程锁；写者基本只有 mpv 回调线程，CAS 无竞争。
    private val playbackClockSamples = AtomicReference<List<PlaybackClockSample>>(emptyList())

    private var standardExoPlayer: ExoPlayer? = null
    private var experimentalExoPlayer: ExoPlayer? = null
    private var standardListener: Player.Listener? = null
    private var experimentalListener: Player.Listener? = null
    private var standardAnalyticsListener: AnalyticsListener? = null
    private var experimentalAnalyticsListener: AnalyticsListener? = null

    override suspend fun play(source: PlaybackSource) {
        MiruLog.i(
            "ExoPlaybackController",
            "Preparing Exo playback session",
            mapOf(
                "source_uri" to source.uri,
                "source_id" to source.mediaSourceId,
            ),
        )
        playbackPreferences = playbackPreferencesRepository
            .getFormatAwareToneMappingPreferences()
            .normalized()
        _requestedRenderBackend.value = sessionState.effectiveRequestedBackend(playbackPreferences.defaultBackend)
        _sessionRuleOverrides.value = sessionState.ruleOverrides
        refreshRuntimeConfig(null)
        if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID) {
            playWithExternalMpv(source)
            return
        }
        val httpConfig = httpRequestResolver.configFor(source)
        signalProbeCompletionJob?.cancel()
        val initialProbeResult = withContext(Dispatchers.IO) {
            runInitialSignalProbe(
                containerTimeoutMs = INITIAL_CONTAINER_SIGNAL_PROBE_TIMEOUT_MS,
                runtimeTimeoutMs = INITIAL_RUNTIME_SIGNAL_PROBE_TIMEOUT_MS,
                containerProbe = {
                    probeContainerVideoSignalDescriptor(
                        context = context,
                        uri = source.uri,
                        httpConfig = httpConfig,
                    )
                },
                runtimeProbe = {
                    probeRuntimeVideoTrackMetadata(
                        context = context,
                        uri = source.uri,
                        httpConfig = httpConfig,
                    )
                },
            )
        }
        containerSignalDescriptor = initialProbeResult.containerValue
            ?: initialProbeResult.runtimeValue?.let(::resolveVideoSignalDescriptor)
        refreshVideoSignalDescriptor(null)
        scheduleContainerSignalProbeCompletionIfNeeded(
            sourceUri = source.uri,
            httpConfig = httpConfig,
            initialProbeResult = initialProbeResult,
        )
        withContext(Dispatchers.Main) {
            currentSource = source
            dataSourceFactory.setHttpConfig(httpConfig)
            _state.value = PlaybackState.Loading(source)
            MiruLog.i(
                "ExoPlaybackController",
                "Playback started",
                mapOf(
                    "source_uri" to source.uri,
                    "media_source_id" to source.mediaSourceId,
                    "start_position_ms" to source.startPosition.toString(),
                    "subtitle_count" to source.subtitleTracks.size.toString(),
                ),
            )

            try {
                ensureMediaSessionService()
                applyVideoEffectsForCurrentConfig()
                if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED) {
                    playWithEmbeddedMpv(source)
                    return@withContext
                }
                val player = activeExoPlayer()
                stopInactivePlayers(player)
                preparePlayerForPlayback(player)

                val subtitleConfigs = source.subtitleTracks.mapIndexed { index, track ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.path))
                        .setMimeType(subtitleMimeTypeForFormat(track.format))
                        .setLanguage(track.language)
                        .setLabel(track.title.ifEmpty { track.language })
                        .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(source.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(source.uri.substringAfterLast("/").substringBeforeLast("."))
                            .setArtist(source.mediaSourceId)
                            .build(),
                    )
                    .setSubtitleConfigurations(subtitleConfigs)
                    .build()

                player.setMediaItem(mediaItem)
                if (source.startPosition > 0) {
                    player.seekTo(source.startPosition)
                    autoResumeSeekCalled = true
                }
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                MiruLog.e(
                    "ExoPlaybackController",
                    "Failed to start playback",
                    e,
                    mapOf("source_uri" to source.uri),
                )
                _state.value = PlaybackState.Error(source, e.message ?: "Failed to play")
            }
        }
    }

    override suspend fun pause() {
        withContext(Dispatchers.Main) {
            if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID) {
                externalMpvPlaying = false
                val source = externalMpvSource
                if (source != null) {
                    _state.value = PlaybackState.Paused(source, externalMpvPositionMs)
                }
                return@withContext
            }
            if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED) {
                embeddedMpvPlaying = false
                embeddedMpvView?.pausePlayback()
                val source = embeddedMpvSource
                if (source != null) {
                    _state.value = PlaybackState.Paused(source, embeddedMpvPositionMs)
                }
                return@withContext
            }
            activeExoPlayer().playWhenReady = false
        }
    }

    override suspend fun resume() {
        withContext(Dispatchers.Main) {
            if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID) {
                externalMpvPlaying = true
                val source = externalMpvSource
                if (source != null) {
                    _state.value = PlaybackState.Playing(source, externalMpvPositionMs)
                }
                return@withContext
            }
            if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED) {
                embeddedMpvPlaying = true
                embeddedMpvView?.resumePlayback()
                val source = embeddedMpvSource
                if (source != null) {
                    _state.value = PlaybackState.Playing(source, embeddedMpvPositionMs)
                }
                return@withContext
            }
            activeExoPlayer().playWhenReady = true
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        withContext(Dispatchers.Main) {
            if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID) {
                externalMpvPositionMs = positionMs.coerceAtLeast(0L)
                val source = externalMpvSource
                if (source != null) {
                    _state.value = if (externalMpvPlaying) {
                        PlaybackState.Playing(source, externalMpvPositionMs)
                    } else {
                        PlaybackState.Paused(source, externalMpvPositionMs)
                    }
                }
                return@withContext
            }
            if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED) {
                embeddedMpvPositionMs = positionMs.coerceAtLeast(0L)
                embeddedMpvView?.seekTo(embeddedMpvPositionMs)
                val source = embeddedMpvSource
                if (source != null) {
                    _state.value = if (embeddedMpvPlaying) {
                        PlaybackState.Playing(source, embeddedMpvPositionMs)
                    } else {
                        PlaybackState.Paused(source, embeddedMpvPositionMs)
                    }
                }
                return@withContext
            }
            activeExoPlayer().seekTo(positionMs)
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
        stop(clearSessionState = true)
    }

    suspend fun stop(clearSessionState: Boolean) {
        withContext(Dispatchers.Main) {
            val currentBackend = _activeRenderBackend.value
            val player = activeExoPlayerOrNull()
            val stopPositionMs = when (currentBackend) {
                PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID -> externalMpvPositionMs
                PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED -> embeddedMpvPositionMs
                else -> player?.currentPosition ?: 0L
            }
            currentSource?.let { source ->
                MiruLog.i(
                    "ExoPlaybackController",
                    "Playback stopped",
                    mapOf(
                        "source_uri" to source.uri,
                        "position_ms" to stopPositionMs.toString(),
                    ),
                )
            }
            stopAllPlayers()
            externalMpvPlaying = false
            externalMpvPositionMs = 0L
            externalMpvSource = null
            embeddedMpvView?.releaseMpv()
            embeddedMpvView = null
            embeddedMpvPlaying = false
            embeddedMpvPositionMs = 0L
            embeddedMpvDurationMs = 0L
            embeddedMpvSource = null
            embeddedMpvPendingLoad = false
            embeddedMpvPlaybackSpeed = 1.0f
            playbackClockSamples.set(emptyList())
            dataSourceFactory.clearHttpConfig()
            currentSource = null
            autoResumeSeekCalled = false
            availableSubtitles.clear()
            availableAudioTracks.clear()
            exoSubtitleSelections.clear()
            exoAudioSelections.clear()
            embeddedSubtitleTrackIds.clear()
            selectedSubtitleTrackIndex = null
            selectedAudioTrackIndex = null
            containerSignalDescriptor = null
            _currentVideoSignalDescriptor.value = null
            PlaybackCodecSelectionState.decoderPreference = PlaybackDecoderPreference.DEFAULT
            sessionState = sessionState.afterPlaybackReset(clearSessionState)
            _requestedRenderBackend.value = playbackPreferences.defaultBackend.normalizeSupportedBackend()
            _sessionRuleOverrides.value = sessionState.ruleOverrides
            signalProbeCompletionJob?.cancel()
            signalProbeCompletionJob = null
            refreshRuntimeConfig(null)
            _state.value = PlaybackState.Idle
        }
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        withContext(Dispatchers.Main) {
            if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID) {
                return@withContext
            }
            if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED) {
                embeddedMpvPlaybackSpeed = speed.coerceIn(0.25f, 3.0f)
                embeddedMpvView?.let { it.applySessionOptions(embeddedSessionOptions(speed = embeddedMpvPlaybackSpeed)) }
                return@withContext
            }
            activeExoPlayer().setPlaybackSpeed(speed.coerceIn(0.25f, 3.0f))
        }
    }

    override suspend fun setSubtitleTrack(trackIndex: Int?) {
        when (_activeRenderBackend.value) {
            PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED -> withContext(Dispatchers.Main) {
                val nativeTrackId = when (trackIndex) {
                    null -> null
                    else -> embeddedSubtitleTrackIds.getOrNull(trackIndex) ?: return@withContext
                }
                embeddedMpvView?.setSubtitleTrack(nativeTrackId)
                selectedSubtitleTrackIndex = trackIndex
            }
            PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID -> Unit
            else -> selectExoTrack(C.TRACK_TYPE_TEXT, trackIndex)
        }
    }

    override suspend fun setAudioTrack(trackIndex: Int) {
        selectExoTrack(C.TRACK_TYPE_AUDIO, trackIndex)
    }

    override fun getAvailableSubtitles(): List<SubtitleTrack> = availableSubtitles.toList()

    override fun getAvailableAudioTracks(): List<AudioTrack> = availableAudioTracks.toList()

    override fun getSelectedSubtitleTrackIndex(): Int? = selectedSubtitleTrackIndex

    override fun getSelectedAudioTrackIndex(): Int? = selectedAudioTrackIndex

    override suspend fun getCurrentPosition(): Long = withContext(Dispatchers.Main) {
        when (_activeRenderBackend.value) {
            PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID -> externalMpvPositionMs
            PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED -> embeddedMpvPositionMs
            else -> activeExoPlayer().currentPosition
        }
    }

    override suspend fun getDuration(): Long = withContext(Dispatchers.Main) {
        when (_activeRenderBackend.value) {
            PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID -> 0L
            PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED -> embeddedMpvDurationMs
            else -> activeExoPlayer().duration
        }
    }

    override fun isPlaying(): Boolean =
        when (_activeRenderBackend.value) {
            PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID -> externalMpvPlaying
            PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED -> embeddedMpvPlaying
            else -> activeExoPlayer().isPlaying
        }

    override fun getPlayer(): Player? =
        when (_activeRenderBackend.value) {
            PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID,
            PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED -> null
            else -> activeExoPlayer()
        }

    override fun usesVlcVideoLayout(): Boolean =
        _activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED ||
            _requestedRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED

    override fun bindVlcVideoHost(hostView: View) {
        val container = hostView as? ViewGroup ?: return
        embeddedMpvHostView = container
        val mpvView = ensureEmbeddedMpvView(container)
        if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED) {
            applyEmbeddedMpvSessionOptions(mpvView, speed = embeddedMpvPlaybackSpeed)
            if (embeddedMpvPendingLoad) {
                embeddedMpvSource?.let { source ->
                    MiruLog.i(
                        "EmbeddedMpv",
                        "Rebinding embedded mpv pending load",
                        mapOf(
                            "source_uri" to source.uri,
                            "start_position_ms" to embeddedMpvPositionMs.toString(),
                        ),
                    )
                    mpvView.loadMedia(
                        path = source.uri,
                        startPositionMs = embeddedMpvPositionMs,
                        externalSubtitlePaths = source.subtitleTracks.map { it.path },
                    )
                }
            }
        }
    }

    override fun needsVlcVideoHostBinding(): Boolean =
        _activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED &&
            (embeddedMpvPendingLoad || embeddedMpvHostView == null)

    override fun unbindVlcVideoHost() {
        embeddedMpvView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        embeddedMpvHostView = null
        embeddedMpvPendingLoad = embeddedMpvSource != null
    }

    override suspend fun setRequestedRenderBackend(backend: PlaybackRenderBackend?) {
        withContext(Dispatchers.Main) {
            sessionState = sessionState.withRequestedBackendOverride(backend)
            val requested = sessionState.effectiveRequestedBackend(playbackPreferences.defaultBackend)
            val previousActiveBackend = _activeRenderBackend.value
            val currentPlaybackSource = currentSource
            val currentPlaybackPosition = when (previousActiveBackend) {
                PlaybackRenderBackend.EXPERIMENTAL_MPV_ANDROID -> externalMpvPositionMs
                PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED -> embeddedMpvPositionMs
                else -> activeExoPlayerOrNull()?.currentPosition ?: 0L
            }
            _requestedRenderBackend.value = requested
            refreshRuntimeConfig(_currentVideoSignalDescriptor.value)
            val nextActiveBackend = _activeRenderBackend.value
            if (currentPlaybackSource != null && previousActiveBackend != nextActiveBackend) {
                val resumedSource = currentPlaybackSource.copy(startPosition = currentPlaybackPosition.coerceAtLeast(0L))
                stop(clearSessionState = false)
                play(resumedSource)
            }
        }
    }

    override suspend fun setSessionRuleOverride(ruleKey: VideoRenderRuleKey, ruleSet: ToneMappingRuleSet?) {
        withContext(Dispatchers.Main) {
            sessionState = sessionState.withRuleOverride(ruleKey, ruleSet)
            _sessionRuleOverrides.value = sessionState.ruleOverrides
            refreshRuntimeConfig(_currentVideoSignalDescriptor.value)
        }
    }

    override suspend fun clearSessionRuleOverrides() {
        withContext(Dispatchers.Main) {
            sessionState = sessionState.clearRuleOverrides()
            _sessionRuleOverrides.value = sessionState.ruleOverrides
            refreshRuntimeConfig(_currentVideoSignalDescriptor.value)
        }
    }

    override suspend fun refreshActivePlaybackDebugConfig() {
        withContext(Dispatchers.Main) {
            if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED) {
                embeddedMpvView?.let { applyEmbeddedMpvSessionOptions(it, speed = embeddedMpvPlaybackSpeed) }
            }
        }
    }

    override fun pendingGlFrameCaptureLabel(): String? =
        playbackDebugOverrides.peekPendingGlFrameCaptureLabel()

    override fun pendingLibVlcNativeSnapshotLabel(): String? = null

    override fun requestLibVlcNativeSnapshot(label: String) = Unit

    override fun currentLibVlcVoutMode(): LibVlcVoutMode? = null

    override fun clearPendingLibVlcNativeSnapshotLabel(label: String) = Unit

    override fun recentPlaybackClockSamples(limit: Int): List<PlaybackClockSample> {
        val safeLimit = limit.coerceIn(1, MAX_PLAYBACK_CLOCK_SAMPLES)
        val samples = playbackClockSamples.get()
        return if (samples.size <= safeLimit) samples else samples.takeLast(safeLimit)
    }

    override fun currentMpvNativeDiagnostics(logLimit: Int): MpvNativeDiagnostics? =
        embeddedMpvView?.snapshotNativeDiagnostics(logLimit)

    override fun clearPendingGlFrameCaptureLabel(label: String) {
        playbackDebugOverrides.clearPendingGlFrameCaptureLabel(label)
    }

    fun release() {
        embeddedMpvView?.releaseMpv()
        embeddedMpvView = null
        embeddedMpvHostView = null
        standardExoPlayer?.let { player ->
            standardListener?.let(player::removeListener)
            standardAnalyticsListener?.let(player::removeAnalyticsListener)
            player.release()
        }
        experimentalExoPlayer?.let { player ->
            experimentalListener?.let(player::removeListener)
            experimentalAnalyticsListener?.let(player::removeAnalyticsListener)
            player.release()
        }
        dataSourceFactory.clearHttpConfig()
        standardExoPlayer = null
        experimentalExoPlayer = null
        standardListener = null
        experimentalListener = null
        standardAnalyticsListener = null
        experimentalAnalyticsListener = null
    }

    private fun createPlayerListener(player: ExoPlayer) = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!isCurrentPlayer(player)) return
            val source = currentSource
            when (playbackState) {
                Player.STATE_READY -> {
                    if (source == null) return
                    val position = player.currentPosition
                    when (_state.value) {
                        is PlaybackState.Buffering -> {
                            if (autoResumeSeekCalled) {
                                autoResumeSeekCalled = false
                            }
                            _state.value = PlaybackState.Playing(source, position)
                        }
                        is PlaybackState.Playing -> Unit
                        else -> {
                            if (player.playWhenReady) {
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

                Player.STATE_IDLE -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isCurrentPlayer(player)) return
            val source = currentSource
            if (source != null) {
                val position = player.currentPosition
                _state.value = if (player.playbackState == Player.STATE_ENDED) {
                    PlaybackState.Ended(source)
                } else if (isPlaying) {
                    PlaybackState.Playing(source, position)
                } else if (_state.value is PlaybackState.Buffering) {
                    PlaybackState.Buffering(source, position)
                } else {
                    PlaybackState.Paused(source, position)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!isCurrentPlayer(player)) return
            val source = currentSource
            MiruLog.e(
                "ExoPlaybackController",
                "Player error",
                error,
                mapOf(
                    "source_uri" to source?.uri.orEmpty(),
                    "media_source_id" to source?.mediaSourceId.orEmpty(),
                    "error_code" to error.errorCode.toString(),
                ),
            )
            _state.value = PlaybackState.Error(source, error.localizedMessage ?: "Playback error")
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (!isCurrentPlayer(player)) return
            updateAvailableTracks()
            refreshVideoSignalDescriptor(player.videoFormat)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (!isCurrentPlayer(player)) return
            if (autoResumeSeekCalled) {
                autoResumeSeekCalled = false
            }
        }

        override fun onRenderedFirstFrame() {
            if (!isCurrentPlayer(player)) return
            Log.i("ExoPlaybackController", "Video first frame rendered")
            MiruLog.i(
                "ExoPlaybackController",
                "Video first frame rendered",
                mapOf(
                    "source_uri" to currentSource?.uri.orEmpty(),
                    "active_backend" to _activeRenderBackend.value.name,
                    "signal_label" to _currentVideoSignalDescriptor.value?.displayLabel().orEmpty(),
                ),
            )
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (!isCurrentPlayer(player)) return
            Log.i(
                "ExoPlaybackController",
                "Video size changed to ${videoSize.width}x${videoSize.height} ratio=${videoSize.pixelWidthHeightRatio}",
            )
            MiruLog.i(
                "ExoPlaybackController",
                "Video size changed",
                mapOf(
                    "width" to videoSize.width.toString(),
                    "height" to videoSize.height.toString(),
                    "pixel_ratio" to videoSize.pixelWidthHeightRatio.toString(),
                ),
            )
        }
    }

    private fun createAnalyticsListener(player: ExoPlayer) = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMsMs: Long,
        ) {
            if (!isCurrentPlayer(player)) return
            MiruLog.i(
                "ExoPlaybackController",
                "Video decoder initialized",
                mapOf(
                    "decoder_name" to decoderName,
                    "position_ms" to eventTime.currentPlaybackPositionMs.toString(),
                    "init_duration_ms" to initializationDurationMsMs.toString(),
                    "source_uri" to currentSource?.uri.orEmpty(),
                    "active_backend" to _activeRenderBackend.value.name,
                ),
            )
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
        ) {
            if (!isCurrentPlayer(player)) return
            refreshVideoSignalDescriptor(format)
            MiruLog.i(
                "ExoPlaybackController",
                "Video input format changed",
                mapOf(
                    "sample_mime_type" to format.sampleMimeType.orEmpty(),
                    "codecs" to format.codecs.orEmpty(),
                    "width" to format.width.toString(),
                    "height" to format.height.toString(),
                    "frame_rate" to format.frameRate.toString(),
                    "bitrate" to format.bitrate.toString(),
                    "position_ms" to eventTime.currentPlaybackPositionMs.toString(),
                ),
            )
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            if (!isCurrentPlayer(player) || droppedFrames <= 0) return
            MiruLog.w(
                "ExoPlaybackController",
                "Dropped video frames",
                attributes = mapOf(
                    "dropped_frames" to droppedFrames.toString(),
                    "elapsed_ms" to elapsedMs.toString(),
                    "position_ms" to eventTime.currentPlaybackPositionMs.toString(),
                    "source_uri" to currentSource?.uri.orEmpty(),
                ),
            )
        }

        override fun onVideoDecoderReleased(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
        ) {
            if (!isCurrentPlayer(player)) return
            MiruLog.i(
                "ExoPlaybackController",
                "Video decoder released",
                mapOf(
                    "decoder_name" to decoderName,
                    "position_ms" to eventTime.currentPlaybackPositionMs.toString(),
                    "source_uri" to currentSource?.uri.orEmpty(),
                ),
            )
        }

        override fun onVideoCodecError(
            eventTime: AnalyticsListener.EventTime,
            videoCodecError: Exception,
        ) {
            if (!isCurrentPlayer(player)) return
            MiruLog.e(
                "ExoPlaybackController",
                "Video codec error",
                videoCodecError,
                mapOf(
                    "position_ms" to eventTime.currentPlaybackPositionMs.toString(),
                    "source_uri" to currentSource?.uri.orEmpty(),
                    "active_backend" to _activeRenderBackend.value.name,
                ),
            )
        }

        override fun onLoadStarted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            if (!isCurrentPlayer(player) || !shouldLogPlaybackLoad(loadEventInfo, mediaLoadData)) return
            MiruLog.i(
                "ExoPlaybackController",
                "Playback load started",
                mapOf(
                    "uri" to loadEventInfo.uri.toString(),
                    "position" to loadEventInfo.dataSpec.position.toString(),
                    "length" to loadEventInfo.dataSpec.length.toString(),
                    "track_type" to mediaTrackTypeLabel(mediaLoadData.trackType),
                    "position_ms" to eventTime.currentPlaybackPositionMs.toString(),
                ),
            )
        }

        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            if (!isCurrentPlayer(player) || !shouldLogPlaybackLoad(loadEventInfo, mediaLoadData)) return
            MiruLog.i(
                "ExoPlaybackController",
                "Playback load completed",
                mapOf(
                    "uri" to loadEventInfo.uri.toString(),
                    "position" to loadEventInfo.dataSpec.position.toString(),
                    "length" to loadEventInfo.dataSpec.length.toString(),
                    "bytes_loaded" to loadEventInfo.bytesLoaded.toString(),
                    "load_duration_ms" to loadEventInfo.loadDurationMs.toString(),
                    "track_type" to mediaTrackTypeLabel(mediaLoadData.trackType),
                    "position_ms" to eventTime.currentPlaybackPositionMs.toString(),
                ),
            )
        }

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean,
        ) {
            if (!isCurrentPlayer(player) || !shouldLogPlaybackLoad(loadEventInfo, mediaLoadData)) return
            MiruLog.e(
                "ExoPlaybackController",
                "Playback load error",
                error,
                mapOf(
                    "uri" to loadEventInfo.uri.toString(),
                    "position" to loadEventInfo.dataSpec.position.toString(),
                    "length" to loadEventInfo.dataSpec.length.toString(),
                    "bytes_loaded" to loadEventInfo.bytesLoaded.toString(),
                    "was_canceled" to wasCanceled.toString(),
                    "track_type" to mediaTrackTypeLabel(mediaLoadData.trackType),
                    "position_ms" to eventTime.currentPlaybackPositionMs.toString(),
                ),
            )
        }
    }

    private fun ensureMediaSessionService() {
        runCatching {
            context.startService(Intent(context, MiruPlayMediaService::class.java))
        }
    }

    private suspend fun selectExoTrack(trackType: Int, trackIndex: Int?) = withContext(Dispatchers.Main) {
        val player = activeExoPlayer()
        if (trackType == C.TRACK_TYPE_TEXT && trackIndex == null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(trackType, true)
                .clearOverridesOfType(trackType)
                .build()
            selectedSubtitleTrackIndex = null
            return@withContext
        }
        val target = when (trackType) {
            C.TRACK_TYPE_TEXT -> exoSubtitleSelections.getOrNull(trackIndex ?: return@withContext)
            C.TRACK_TYPE_AUDIO -> exoAudioSelections.getOrNull(trackIndex ?: return@withContext)
            else -> null
        } ?: return@withContext
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(trackType, false)
            .clearOverridesOfType(trackType)
            .addOverride(TrackSelectionOverride(target.group.mediaTrackGroup, target.trackIndex))
            .build()
        if (trackType == C.TRACK_TYPE_TEXT) {
            selectedSubtitleTrackIndex = trackIndex
        } else {
            selectedAudioTrackIndex = trackIndex
        }
    }

    private fun updateAvailableTracks() {
        availableSubtitles.clear()
        availableAudioTracks.clear()
        exoSubtitleSelections.clear()
        exoAudioSelections.clear()
        selectedSubtitleTrackIndex = null
        selectedAudioTrackIndex = null

        try {
            val tracks = activeExoPlayer().currentTracks
            for (group in tracks.groups) {
                for (trackInGroup in 0 until group.length) {
                    if (!group.isTrackSupported(trackInGroup)) continue
                    val format = group.getTrackFormat(trackInGroup)
                    when (group.type) {
                        C.TRACK_TYPE_TEXT -> {
                            val trackIndex = availableSubtitles.size
                            availableSubtitles.add(
                                SubtitleTrack(
                                    language = format.language ?: "und",
                                    title = format.label ?: "",
                                    isExternal = false,
                                    path = "",
                                    format = SubtitleFormat.SRT,
                                ),
                            )
                            exoSubtitleSelections.add(ExoTrackSelection(group, trackInGroup))
                            if (group.isTrackSelected(trackInGroup)) selectedSubtitleTrackIndex = trackIndex
                        }

                        C.TRACK_TYPE_AUDIO -> {
                            val trackIndex = availableAudioTracks.size
                            availableAudioTracks.add(
                                AudioTrack(
                                    index = trackIndex,
                                    language = format.language ?: "und",
                                    title = format.label,
                                    codec = format.codecs,
                                ),
                            )
                            exoAudioSelections.add(ExoTrackSelection(group, trackInGroup))
                            if (group.isTrackSelected(trackInGroup)) selectedAudioTrackIndex = trackIndex
                        }
                    }
                }
            }
        } catch (e: Exception) {
            MiruLog.w("ExoPlaybackController", "Failed to enumerate media tracks", e)
        }
    }

    private fun refreshVideoSignalDescriptor(format: Format?) {
        val runtimeDescriptor = resolveVideoSignalDescriptor(format)
        val mergedDescriptor = mergeVideoSignalDescriptor(
            runtimeDescriptor = runtimeDescriptor,
            containerHint = containerSignalDescriptor,
        )
        val effectiveDescriptor = playbackDebugOverrides.forcedVideoSignalDescriptor ?: when {
            format == null && containerSignalDescriptor == null -> null
            else -> mergedDescriptor
        }
        _currentVideoSignalDescriptor.value = effectiveDescriptor
        refreshRuntimeConfig(effectiveDescriptor)
    }

    private fun refreshRuntimeConfig(signalDescriptor: VideoSignalDescriptor?) {
        val descriptor = signalDescriptor
            ?: playbackDebugOverrides.forcedVideoSignalDescriptor
            ?: VideoSignalDescriptor()
        val config = resolveToneMappingRuntimeConfig(
            preferences = playbackPreferences.normalized(),
            sessionRuleOverrides = _sessionRuleOverrides.value,
            signalDescriptor = descriptor,
            requestedBackendOverride = _requestedRenderBackend.value,
        )
        PlaybackCodecSelectionState.decoderPreference = when {
            config.activeBackend == PlaybackRenderBackend.EXPERIMENTAL_GL &&
                shouldUseDedicatedExperimentalGlSurface(deviceGlEsMajorVersion) &&
                descriptor.isHdr -> {
                PlaybackDecoderPreference.PREFER_SOFTWARE_VIDEO_FOR_HDR
            }

            config.activeBackend == PlaybackRenderBackend.EXPERIMENTAL_GL &&
                shouldUseExperimentalVideoEffectsPipeline(
                    activeBackend = config.activeBackend,
                    glEsMajorVersion = deviceGlEsMajorVersion,
                ) &&
                (descriptor.signalKind == VideoSignalKind.HDR10 ||
                    descriptor.signalKind == VideoSignalKind.HDR10_PLUS ||
                    descriptor.signalKind == VideoSignalKind.UNKNOWN_HDR) -> {
                PlaybackDecoderPreference.PREFER_SOFTWARE_HEVC_FOR_HDR
            }

            else -> PlaybackDecoderPreference.DEFAULT
        }
        _currentRenderRuleKey.value = config.ruleKey
        _currentToneMappingRuleSet.value = config.appliedRuleSet
        _activeRenderBackend.value = config.activeBackend
        _fallbackReason.value = config.fallbackReason
        if (config.activeBackend == PlaybackRenderBackend.EXPERIMENTAL_MPV_EMBEDDED) {
            embeddedMpvView?.let { view ->
                applyEmbeddedMpvSessionOptions(view, speed = embeddedMpvPlaybackSpeed)
            }
        }
        MiruLog.i(
            "ExoPlaybackController",
            "Tone mapping runtime config refreshed",
            mapOf(
                "signal_label" to descriptor.displayLabel(),
                "signal_kind" to descriptor.signalKind.name,
                "transfer" to descriptor.transfer.name,
                "codec_id" to descriptor.codecId,
                "rule_key" to config.ruleKey.name,
                "requested_backend" to config.requestedBackend.name,
                "active_backend" to config.activeBackend.name,
                "fallback_reason" to config.fallbackReason.orEmpty(),
            ),
        )
        runCatching {
            applyVideoEffectsForCurrentConfig()
        }.onFailure { error ->
            MiruLog.w(
                "ExoPlaybackController",
                "Failed to apply Exo video effects",
                error,
                mapOf(
                    "active_backend" to config.activeBackend.name,
                    "signal_kind" to descriptor.signalKind.name,
                ),
            )
        }
    }

    private fun applyVideoEffectsForCurrentConfig() {
        val usesExperimentalVideoEffectsPipeline = shouldUseExperimentalVideoEffectsPipeline(
            activeBackend = _activeRenderBackend.value,
            glEsMajorVersion = deviceGlEsMajorVersion,
        )
        if (
            shouldBypassExoVideoEffectsDispatch(
                activeBackend = _activeRenderBackend.value,
                glEsMajorVersion = deviceGlEsMajorVersion,
            )
        ) {
            MiruLog.i(
                "ExoPlaybackController",
                "Bypassed Exo video effects dispatch for dedicated GL surface pipeline",
                mapOf(
                    "active_backend" to _activeRenderBackend.value.name,
                    "gl_es_major_version" to deviceGlEsMajorVersion.toString(),
                ),
            )
            return
        }
        if (!shouldUseExoVideoEffectsPipeline(true, _activeRenderBackend.value, usesExperimentalVideoEffectsPipeline)) {
            experimentalPlayerOrNull()?.setVideoEffects(emptyList())
            MiruLog.i(
                "ExoPlaybackController",
                "Skipped Exo video effects for current backend",
                mapOf(
                    "active_backend" to _activeRenderBackend.value.name,
                    "gl_es_major_version" to deviceGlEsMajorVersion.toString(),
                    "experimental_effects_pipeline" to usesExperimentalVideoEffectsPipeline.toString(),
                ),
            )
            return
        }
        if (_activeRenderBackend.value != PlaybackRenderBackend.EXPERIMENTAL_GL) {
            experimentalPlayerOrNull()?.setVideoEffects(emptyList())
            return
        }
        val effects = currentVideoEffects()
        experimentalExoPlayer().setVideoEffects(effects)
        MiruLog.i(
            "ExoPlaybackController",
            "Applied Exo video effects",
            mapOf(
                "active_backend" to _activeRenderBackend.value.name,
                "effect_count" to effects.size.toString(),
                "effects" to effects.joinToString(",") { it.javaClass.simpleName },
            ),
        )
    }

    private fun currentVideoEffects(): List<Effect> =
        if (
            shouldUseExperimentalVideoEffectsPipeline(
                activeBackend = _activeRenderBackend.value,
                glEsMajorVersion = deviceGlEsMajorVersion,
            )
        ) {
            buildExoVideoEffects(
                ruleSet = _currentToneMappingRuleSet.value,
                signalDescriptor = _currentVideoSignalDescriptor.value,
            )
        } else {
            emptyList()
        }

    private fun scheduleContainerSignalProbeCompletionIfNeeded(
        sourceUri: String,
        httpConfig: PlaybackHttpRequestConfig,
        initialProbeResult: InitialSignalProbeResult<VideoSignalDescriptor, *>,
    ) {
        if (initialProbeResult.containerCompletedWithinBudget) {
            return
        }
        signalProbeCompletionJob = controllerScope.launch(Dispatchers.IO) {
            val resolvedContainerDescriptor = runCatching {
                probeContainerVideoSignalDescriptor(
                    context = context,
                    uri = sourceUri,
                    httpConfig = httpConfig,
                )
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (currentSource?.uri != sourceUri) {
                    return@withContext
                }
                containerSignalDescriptor = resolvedContainerDescriptor ?: containerSignalDescriptor
                refreshVideoSignalDescriptor(null)
                MiruLog.i(
                    "ExoPlaybackController",
                    "Completed deferred container signal probe",
                    mapOf(
                        "source_uri" to sourceUri,
                        "signal_label" to _currentVideoSignalDescriptor.value?.displayLabel().orEmpty(),
                        "active_backend" to _activeRenderBackend.value.name,
                    ),
                )
            }
        }
    }

    private suspend fun playWithExternalMpv(source: PlaybackSource) {
        withContext(Dispatchers.Main) {
            currentSource = source
            externalMpvSource = source
            externalMpvPositionMs = source.startPosition.coerceAtLeast(0L)
            _state.value = PlaybackState.Loading(source)
            when (val launchResult = externalMpvLauncher.launch(source)) {
                is Result.Success -> {
                    externalMpvPlaying = true
                    _state.value = PlaybackState.Playing(source, externalMpvPositionMs)
                }
                is Result.Error -> {
                    externalMpvPlaying = false
                    _state.value = PlaybackState.Error(source, launchResult.error.toUserMessage())
                }
            }
        }
    }

    private fun playWithEmbeddedMpv(source: PlaybackSource) {
        currentSource = source
        embeddedMpvSource = source
        embeddedMpvPositionMs = source.startPosition.coerceAtLeast(0L)
        embeddedMpvDurationMs = 0L
        embeddedMpvPlaying = false
        embeddedMpvPendingLoad = true
        val boundHost = embeddedMpvHostView
        val host = boundHost?.takeIf(::isEmbeddedMpvHostReady)
        if (host == null) {
            MiruLog.i(
                "EmbeddedMpv",
                "Deferring embedded mpv load until host is ready",
                mapOf(
                    "has_host" to (boundHost != null).toString(),
                    "host_attached" to (boundHost?.isAttachedToWindow == true).toString(),
                    "host_width" to (boundHost?.width ?: 0).toString(),
                    "host_height" to (boundHost?.height ?: 0).toString(),
                ),
            )
            _state.value = PlaybackState.Loading(source)
            return
        }
        val mpvView = ensureEmbeddedMpvView(host)
        applyEmbeddedMpvSessionOptions(mpvView, speed = embeddedMpvPlaybackSpeed)
        MiruLog.i(
            "EmbeddedMpv",
            "Requesting embedded mpv load",
            mapOf(
                "source_uri" to source.uri,
                "start_position_ms" to embeddedMpvPositionMs.toString(),
            ),
        )
        mpvView.loadMedia(
            path = source.uri,
            startPositionMs = embeddedMpvPositionMs,
            externalSubtitlePaths = source.subtitleTracks.map { it.path },
        )
    }

    private fun ensureEmbeddedMpvView(container: ViewGroup): MiruMpvSurfaceView {
        embeddedMpvView?.let { existing ->
            if (existing.parent !== container) {
                (existing.parent as? ViewGroup)?.removeView(existing)
                container.removeAllViews()
                container.addView(
                    existing,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            return existing
        }
        val created = MiruMpvSurfaceView(container.context).apply {
            onSubtitleTracksChanged = { view -> refreshEmbeddedMpvSubtitleTracks(view) }
            onStateChanged = { snapshot ->
                embeddedMpvPositionMs = snapshot.positionMs
                embeddedMpvDurationMs = snapshot.durationMs
                recordPlaybackClockSample(snapshot)
                embeddedMpvSource?.let { source ->
                    val resolved = resolveEmbeddedMpvStartupState(
                        source = source,
                        snapshot = snapshot,
                        wasPlaying = embeddedMpvPlaying,
                    )
                    embeddedMpvPlaying = resolved.isPlaying
                    if (shouldPublishEmbeddedMpvStateChange(_state.value, resolved.playbackState)) {
                        _state.value = resolved.playbackState
                    }
                }
            }
            onFileLoaded = {
                embeddedMpvPendingLoad = false
                embeddedMpvSource?.let { source ->
                    val nativeProperties = embeddedMpvView
                        ?.snapshotNativeDiagnostics(logLimit = 1)
                        ?.properties
                        ?.associate { it.name to it.value.orEmpty() }
                        .orEmpty()
                    MiruLog.i(
                        "EmbeddedMpv",
                        "Embedded mpv file loaded",
                        mapOf(
                            "source_uri" to source.uri,
                            "position_ms" to embeddedMpvPositionMs.toString(),
                            "vo" to nativeProperties["vo"].orEmpty(),
                            "hwdec_current" to nativeProperties["hwdec-current"].orEmpty(),
                            "video_codec" to nativeProperties["video-codec"].orEmpty(),
                            "video_pixelformat" to nativeProperties["video-params/pixelformat"].orEmpty(),
                            "video_hw_pixelformat" to nativeProperties["video-params/hw-pixelformat"].orEmpty(),
                        ),
                    )
                    embeddedMpvPlaying = false
                    _state.value = PlaybackState.Buffering(source, embeddedMpvPositionMs)
                }
            }
            onPlaybackRestart = {
                embeddedMpvPendingLoad = false
                embeddedMpvPlaying = true
                embeddedMpvSource?.let { source ->
                    MiruLog.i(
                        "EmbeddedMpv",
                        "Embedded mpv playback restart",
                        mapOf(
                            "source_uri" to source.uri,
                            "position_ms" to embeddedMpvPositionMs.toString(),
                        ),
                    )
                    _state.value = PlaybackState.Playing(source, embeddedMpvPositionMs)
                }
            }
            ensureInitialized()
        }
        container.removeAllViews()
        container.addView(
            created,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        embeddedMpvView = created
        return created
    }

    private fun refreshEmbeddedMpvSubtitleTracks(view: MiruMpvSurfaceView) {
        val tracks = view.subtitleTracks()
        availableSubtitles.clear()
        embeddedSubtitleTrackIds.clear()
        selectedSubtitleTrackIndex = null
        tracks.forEach { track ->
            val index = availableSubtitles.size
            availableSubtitles.add(
                SubtitleTrack(
                    language = track.language,
                    title = track.title,
                    isExternal = track.external,
                    path = "",
                    format = when (track.codec.lowercase()) {
                        "ass" -> SubtitleFormat.ASS
                        "ssa" -> SubtitleFormat.SSA
                        "webvtt" -> SubtitleFormat.VTT
                        else -> SubtitleFormat.SRT
                    },
                ),
            )
            embeddedSubtitleTrackIds.add(track.id)
            if (track.selected) selectedSubtitleTrackIndex = index
        }
    }

    private fun recordPlaybackClockSample(snapshot: MiruMpvSurfaceView.StateSnapshot) {
        playbackClockSamples.updateAndGet { current ->
            (current + PlaybackClockSample(
                monotonicTimestampMs = android.os.SystemClock.elapsedRealtime(),
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                paused = snapshot.paused,
                eofReached = snapshot.eofReached,
            )).takeLast(MAX_PLAYBACK_CLOCK_SAMPLES)
        }
    }

    private fun applyEmbeddedMpvSessionOptions(
        mpvView: MiruMpvSurfaceView,
        speed: Float = embeddedMpvPlaybackSpeed,
    ) {
        val options = embeddedSessionOptions(speed = speed)
        if (!mpvView.applySessionOptions(options)) {
            return
        }
        MiruLog.i(
            "EmbeddedMpv",
            "Applying embedded mpv session options",
            mapOf(
                "vo" to options.vo,
                "hwdec" to options.hwdec,
                "profile" to options.profile,
                "target_prim" to options.targetPrim.orEmpty(),
                "target_trc" to options.targetTrc.orEmpty(),
                "target_peak" to options.targetPeak?.toString().orEmpty(),
                "hdr_reference_white" to options.hdrReferenceWhite?.toString().orEmpty(),
                "tone_mapping" to options.toneMapping.orEmpty(),
                "tone_mapping_param" to options.toneMappingParam?.toString().orEmpty(),
                "hdr_compute_peak" to options.hdrComputePeak?.toString().orEmpty(),
                "hdr_peak_percentile" to options.hdrPeakPercentile?.toString().orEmpty(),
                "hdr_peak_decay_rate" to options.hdrPeakDecayRate?.toString().orEmpty(),
                "hdr_scene_threshold_low" to options.hdrSceneThresholdLow?.toString().orEmpty(),
                "hdr_scene_threshold_high" to options.hdrSceneThresholdHigh?.toString().orEmpty(),
                "hdr_contrast_recovery" to options.hdrContrastRecovery?.toString().orEmpty(),
                "saturation" to options.saturation?.toString().orEmpty(),
                "gamut_mapping_mode" to options.gamutMappingMode.orEmpty(),
                "deband" to options.deband.toString(),
                "shader_count" to options.shaderPaths.size.toString(),
                "shader_names" to options.shaderPaths.joinToString("|") { java.io.File(it).name },
            ),
        )
    }

    private fun embeddedSessionOptions(speed: Float = embeddedMpvPlaybackSpeed): MiruMpvSurfaceView.SessionOptions {
        val ruleSet = _currentToneMappingRuleSet.value
        val shaderDir = File(context.getExternalFilesDir(null), "mpv/shaders/active")
        val shaderPaths = shaderDir
            .takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isFile && (it.extension.equals("glsl", true) || it.extension.equals("hook", true)) }
            ?.sortedBy { it.name }
            ?.map { it.absolutePath }
            .orEmpty()
        return buildEmbeddedMpvSessionOptions(
            ruleSet = ruleSet,
            shaderPaths = shaderPaths,
            speed = speed,
            debugConfig = playbackDebugOverrides.embeddedMpvDebugConfig,
        )
    }

    private fun activeExoPlayer(): ExoPlayer =
        if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_GL) {
            experimentalExoPlayer()
        } else {
            standardExoPlayer()
        }

    private fun activeExoPlayerOrNull(): ExoPlayer? =
        if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_GL) {
            experimentalPlayerOrNull()
        } else {
            standardPlayerOrNull()
        }

    private fun isCurrentPlayer(player: ExoPlayer): Boolean = player === activeExoPlayer()

    private fun stopInactivePlayers(activePlayer: ExoPlayer) {
        standardPlayerOrNull()?.let { player ->
            if (activePlayer !== player) {
                preparePlayerForPlayback(player)
            }
        }
        experimentalPlayerOrNull()?.let { player ->
            if (activePlayer !== player) {
                preparePlayerForPlayback(player)
            }
        }
    }

    private fun stopAllPlayers() {
        standardPlayerOrNull()?.let(::preparePlayerForPlayback)
        experimentalPlayerOrNull()?.let(::preparePlayerForPlayback)
    }

    private fun preparePlayerForPlayback(player: ExoPlayer) {
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
    }

    private fun standardExoPlayer(): ExoPlayer {
        standardExoPlayer?.let { return it }
        val player = standardExoPlayerProvider.get()
        val listener = createPlayerListener(player)
        val analyticsListener = createAnalyticsListener(player)
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        standardExoPlayer = player
        standardListener = listener
        standardAnalyticsListener = analyticsListener
        return player
    }

    private fun experimentalExoPlayer(): ExoPlayer {
        experimentalExoPlayer?.let { return it }
        val player = experimentalExoPlayerProvider.get()
        val listener = createPlayerListener(player)
        val analyticsListener = createAnalyticsListener(player)
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        experimentalExoPlayer = player
        experimentalListener = listener
        experimentalAnalyticsListener = analyticsListener
        return player
    }

    private fun standardPlayerOrNull(): ExoPlayer? = standardExoPlayer

    private fun experimentalPlayerOrNull(): ExoPlayer? = experimentalExoPlayer
}

internal data class EmbeddedMpvStartupState(
    val isPlaying: Boolean,
    val playbackState: PlaybackState,
)

internal fun resolveEmbeddedMpvStartupState(
    source: PlaybackSource,
    snapshot: MiruMpvSurfaceView.StateSnapshot,
    wasPlaying: Boolean,
): EmbeddedMpvStartupState {
    if (snapshot.eofReached) {
        return EmbeddedMpvStartupState(
            isPlaying = false,
            playbackState = PlaybackState.Ended(source),
        )
    }
    if (snapshot.paused) {
        return EmbeddedMpvStartupState(
            isPlaying = false,
            playbackState = PlaybackState.Paused(source, snapshot.positionMs),
        )
    }
    val isPlaying = wasPlaying || snapshot.positionMs > 0L
    return EmbeddedMpvStartupState(
        isPlaying = isPlaying,
        playbackState = if (isPlaying) {
            PlaybackState.Playing(source, snapshot.positionMs)
        } else {
            PlaybackState.Buffering(source, snapshot.positionMs)
        },
    )
}

internal fun shouldPublishEmbeddedMpvStateChange(
    current: PlaybackState,
    next: PlaybackState,
): Boolean = when {
    current is PlaybackState.Playing && next is PlaybackState.Playing -> current.source != next.source
    current is PlaybackState.Paused && next is PlaybackState.Paused -> current.source != next.source
    current is PlaybackState.Buffering && next is PlaybackState.Buffering -> current.source != next.source
    else -> current != next
}

internal fun isEmbeddedMpvHostReady(hostView: ViewGroup?): Boolean =
    hostView?.isAttachedToWindow == true && hostView.width > 0 && hostView.height > 0

internal fun shouldLogPlaybackLoad(
    loadEventInfo: LoadEventInfo,
    mediaLoadData: MediaLoadData,
): Boolean {
    val scheme = loadEventInfo.dataSpec.uri.scheme?.lowercase()
    return mediaLoadData.dataType == C.DATA_TYPE_MEDIA &&
        (scheme == "http" || scheme == "https")
}

internal fun subtitleMimeTypeForFormat(format: SubtitleFormat): String = when (format) {
    SubtitleFormat.SRT -> MimeTypes.APPLICATION_SUBRIP
    SubtitleFormat.ASS, SubtitleFormat.SSA -> MimeTypes.TEXT_SSA
    SubtitleFormat.VTT -> MimeTypes.TEXT_VTT
    else -> MimeTypes.APPLICATION_SUBRIP
}

internal fun mediaTrackTypeLabel(trackType: Int): String = when (trackType) {
    C.TRACK_TYPE_VIDEO -> "video"
    C.TRACK_TYPE_AUDIO -> "audio"
    C.TRACK_TYPE_TEXT -> "text"
    C.TRACK_TYPE_DEFAULT -> "default"
    C.TRACK_TYPE_METADATA -> "metadata"
    C.TRACK_TYPE_IMAGE -> "image"
    C.TRACK_TYPE_NONE -> "none"
    C.TRACK_TYPE_UNKNOWN -> "unknown"
    else -> trackType.toString()
}

private const val MAX_PLAYBACK_CLOCK_SAMPLES = 240

typealias Tracks = androidx.media3.common.Tracks
