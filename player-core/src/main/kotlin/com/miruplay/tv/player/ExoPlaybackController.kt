package com.miruplay.tv.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
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
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private var currentSource: PlaybackSource? = null
    private var autoResumeSeekCalled = false
    private var playbackPreferences = FormatAwareToneMappingPreferences()
    private var containerSignalDescriptor: VideoSignalDescriptor? = null
    private var sessionState = PlaybackSessionState()
    private val deviceGlEsMajorVersion = resolveDeviceGlEsMajorVersion(context)
    private var signalProbeCompletionJob: Job? = null

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
                runtimeProbe = { null },
            )
        }
        containerSignalDescriptor = initialProbeResult.containerValue
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
                val player = activeExoPlayer()
                stopInactivePlayers(player)
                preparePlayerForPlayback(player)

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
            activeExoPlayer().playWhenReady = false
        }
    }

    override suspend fun resume() {
        withContext(Dispatchers.Main) {
            activeExoPlayer().playWhenReady = true
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        withContext(Dispatchers.Main) {
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
            val player = activeExoPlayer()
            currentSource?.let { source ->
                MiruLog.i(
                    "ExoPlaybackController",
                    "Playback stopped",
                    mapOf(
                        "source_uri" to source.uri,
                        "position_ms" to player.currentPosition.toString(),
                    ),
                )
            }
            stopAllPlayers()
            dataSourceFactory.clearHttpConfig()
            currentSource = null
            autoResumeSeekCalled = false
            availableSubtitles.clear()
            availableAudioTracks.clear()
            containerSignalDescriptor = null
            _currentVideoSignalDescriptor.value = null
            PlaybackCodecSelectionState.decoderPreference = PlaybackDecoderPreference.DEFAULT
            sessionState = sessionState.afterPlaybackReset(clearSessionState)
            _requestedRenderBackend.value = playbackPreferences.defaultBackend
            _sessionRuleOverrides.value = sessionState.ruleOverrides
            signalProbeCompletionJob?.cancel()
            signalProbeCompletionJob = null
            refreshRuntimeConfig(null)
            _state.value = PlaybackState.Idle
        }
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        withContext(Dispatchers.Main) {
            activeExoPlayer().setPlaybackSpeed(speed.coerceIn(0.25f, 3.0f))
        }
    }

    override suspend fun setSubtitleTrack(trackIndex: Int) {
        updateAvailableTracks()
    }

    override suspend fun setAudioTrack(trackIndex: Int) {
        updateAvailableTracks()
    }

    override fun getAvailableSubtitles(): List<SubtitleTrack> = availableSubtitles.toList()

    override fun getAvailableAudioTracks(): List<AudioTrack> = availableAudioTracks.toList()

    override suspend fun getCurrentPosition(): Long = withContext(Dispatchers.Main) {
        activeExoPlayer().currentPosition
    }

    override suspend fun getDuration(): Long = withContext(Dispatchers.Main) {
        activeExoPlayer().duration
    }

    override fun isPlaying(): Boolean = activeExoPlayer().isPlaying

    override fun getPlayer(): Player? = activeExoPlayer()

    override fun usesVlcVideoLayout(): Boolean = false

    override fun bindVlcVideoHost(hostView: View) = Unit

    override fun unbindVlcVideoHost() = Unit

    override suspend fun setRequestedRenderBackend(backend: PlaybackRenderBackend?) {
        sessionState = sessionState.withRequestedBackendOverride(backend)
        val requested = sessionState.effectiveRequestedBackend(playbackPreferences.defaultBackend)
        _requestedRenderBackend.value = requested
        refreshRuntimeConfig(_currentVideoSignalDescriptor.value)
    }

    override suspend fun setSessionRuleOverride(ruleKey: VideoRenderRuleKey, ruleSet: ToneMappingRuleSet?) {
        sessionState = sessionState.withRuleOverride(ruleKey, ruleSet)
        _sessionRuleOverrides.value = sessionState.ruleOverrides
        refreshRuntimeConfig(_currentVideoSignalDescriptor.value)
    }

    override suspend fun clearSessionRuleOverrides() {
        sessionState = sessionState.clearRuleOverrides()
        _sessionRuleOverrides.value = sessionState.ruleOverrides
        refreshRuntimeConfig(_currentVideoSignalDescriptor.value)
    }

    override fun pendingGlFrameCaptureLabel(): String? =
        playbackDebugOverrides.peekPendingGlFrameCaptureLabel()

    override fun pendingLibVlcNativeSnapshotLabel(): String? = null

    override fun requestLibVlcNativeSnapshot(label: String) = Unit

    override fun currentLibVlcVoutMode(): LibVlcVoutMode? = null

    override fun clearPendingLibVlcNativeSnapshotLabel(label: String) = Unit

    override fun clearPendingGlFrameCaptureLabel(label: String) {
        playbackDebugOverrides.clearPendingGlFrameCaptureLabel(label)
    }

    fun release() {
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
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
        ) {
            if (!isCurrentPlayer(player)) return
            refreshVideoSignalDescriptor(format)
        }
    }

    private fun ensureMediaSessionService() {
        runCatching {
            context.startService(Intent(context, MiruPlayMediaService::class.java))
        }
    }

    private fun updateAvailableTracks() {
        availableSubtitles.clear()
        availableAudioTracks.clear()

        try {
            val tracks = activeExoPlayer().currentTracks
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
                                format = SubtitleFormat.SRT,
                            ),
                        )
                    }

                    C.TRACK_TYPE_AUDIO -> {
                        val format = group.getTrackFormat(0)
                        availableAudioTracks.add(
                            AudioTrack(
                                index = availableAudioTracks.size,
                                language = format.language ?: "und",
                                title = format.label,
                                codec = format.codecs,
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            MiruLog.w("ExoPlaybackController", "Failed to enumerate media tracks", e)
        }
    }

    private fun mimeTypeForFormat(format: SubtitleFormat): String =
        when (format) {
            SubtitleFormat.SRT -> "application/x-subrip"
            SubtitleFormat.ASS, SubtitleFormat.SSA -> "text/x-ass"
            SubtitleFormat.VTT -> "text/vtt"
            else -> "application/x-subrip"
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
        val descriptor = signalDescriptor ?: VideoSignalDescriptor()
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

    private fun activeExoPlayer(): ExoPlayer =
        if (_activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_GL) {
            experimentalExoPlayer()
        } else {
            standardExoPlayer()
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

typealias Tracks = androidx.media3.common.Tracks
