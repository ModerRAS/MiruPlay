package com.miruplay.tv.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.Player
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.SubtitleFormat
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.model.ToneMappingCurvePreset
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoTransferCharacteristic
import com.miruplay.tv.model.defaultToneMappingRuleSet
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.util.DisplayManager
import org.videolan.libvlc.util.VLCUtil
import org.videolan.libvlc.util.VLCVideoLayout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridPlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoController: ExoPlaybackController,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val playbackDebugOverrides: PlaybackDebugOverrides,
    private val httpRequestResolver: PlaybackHttpRequestResolver,
    private val libVlcStartupProbe: LibVlcStartupProbe,
    private val libVlcSnapshotBridge: LibVlcSnapshotBridge,
    private val libVlcFrameProbeBridge: LibVlcFrameProbeBridge,
    private val libVlcOutputCallbacksBridge: LibVlcOutputCallbacksBridge,
    private val libVlcVmemStreamBridge: LibVlcVmemStreamBridge,
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
    private var playbackPreferences = FormatAwareToneMappingPreferences()
    private var currentSource: PlaybackSource? = null
    private var usingVlcBackend = false
    private var libVlc: LibVLC? = null
    private var vlcMediaPlayer: MediaPlayer? = null
    private var vlcVideoHost: VLCVideoLayout? = null
    private var attachedVlcVideoHost: VLCVideoLayout? = null
    private var vlcDisplayManager: DisplayManager? = null
    private var vlcDisplayManagerActivity: Activity? = null
    private var vlcRendererSelection = MutableLiveData<RendererItem>()
    private var vlcHostLayoutChangeListener: View.OnLayoutChangeListener? = null
    private var containerSignalDescriptor: VideoSignalDescriptor? = null
    private var runtimeSignalDescriptor: VideoSignalDescriptor? = null
    private var currentHttpConfig: PlaybackHttpRequestConfig = PlaybackHttpRequestConfig.Empty
    private var sessionState = PlaybackSessionState()
    private var hasLoggedVlcDisplayedFrames = false
    private var hasLoggedVlcDecodedWithoutDisplay = false
    private var pendingLibVlcNativeSnapshotJob: Job? = null
    private var pendingLibVlcNativeSnapshotKickJob: Job? = null
    private var pendingLibVlcFrameProbeJob: Job? = null
    private var activeLibVlcFrameProbeSession: LibVlcFrameProbeSession? = null
    private var activeLibVlcOutputCallbackSession: LibVlcOutputCallbacksSession? = null
    private var activeLibVlcVmemStreamSession: LibVlcVmemStreamSession? = null
    private var signalProbeCompletionJob: Job? = null
    private var pendingVlcPlaybackStart = false
    private var usingDirectTextureAttach = false
    private var usingSurfaceVideoHostAttach = false
    private var usingOutputCallbackAttach = false
    private var usingHiddenCarrierAttach = false
    private var vlcSurfaceHostReadyListener: ((Boolean) -> Unit)? = null
    private var vlcOutputCallbackHostReadyListener: ((Boolean) -> Unit)? = null

    private object NoopLibVlcStartupProbe : LibVlcStartupProbe {
        override fun canStartLibVlc(options: List<String>): LibVlcStartupProbeResult =
            LibVlcStartupProbeResult(canStart = true)
    }

    constructor(
        context: Context,
        exoController: ExoPlaybackController,
        playbackPreferencesRepository: PlaybackPreferencesRepository,
        playbackDebugOverrides: PlaybackDebugOverrides,
        httpRequestResolver: PlaybackHttpRequestResolver,
        libVlcSnapshotBridge: LibVlcSnapshotBridge,
        libVlcFrameProbeBridge: LibVlcFrameProbeBridge,
        libVlcOutputCallbacksBridge: LibVlcOutputCallbacksBridge,
        libVlcVmemStreamBridge: LibVlcVmemStreamBridge,
    ) : this(
        context = context,
        exoController = exoController,
        playbackPreferencesRepository = playbackPreferencesRepository,
        playbackDebugOverrides = playbackDebugOverrides,
        httpRequestResolver = httpRequestResolver,
        libVlcStartupProbe = NoopLibVlcStartupProbe,
        libVlcSnapshotBridge = libVlcSnapshotBridge,
        libVlcFrameProbeBridge = libVlcFrameProbeBridge,
        libVlcOutputCallbacksBridge = libVlcOutputCallbacksBridge,
        libVlcVmemStreamBridge = libVlcVmemStreamBridge,
    )

    init {
        exoController.state.onEach {
            if (shouldMirrorExoControllerState()) {
                _state.value = it
            }
        }.launchIn(controllerScope)
        exoController.requestedRenderBackend.onEach {
            if (shouldMirrorExoControllerState()) {
                _requestedRenderBackend.value = it
            }
        }.launchIn(controllerScope)
        exoController.activeRenderBackend.onEach {
            if (shouldMirrorExoControllerState()) {
                _activeRenderBackend.value = it
            }
        }.launchIn(controllerScope)
        exoController.currentVideoSignalDescriptor.onEach {
            if (shouldMirrorExoControllerState()) {
                _currentVideoSignalDescriptor.value = it
            }
        }.launchIn(controllerScope)
        exoController.currentRenderRuleKey.onEach {
            if (shouldMirrorExoControllerState()) {
                _currentRenderRuleKey.value = it
            }
        }.launchIn(controllerScope)
        exoController.currentToneMappingRuleSet.onEach {
            if (shouldMirrorExoControllerState()) {
                _currentToneMappingRuleSet.value = it
            }
        }.launchIn(controllerScope)
        exoController.sessionRuleOverrides.onEach {
            if (shouldMirrorExoControllerState()) {
                _sessionRuleOverrides.value = it
            }
        }.launchIn(controllerScope)
        exoController.fallbackReason.onEach {
            if (shouldMirrorExoControllerState()) {
                _fallbackReason.value = it
            }
        }.launchIn(controllerScope)
    }

    private fun shouldMirrorExoControllerState(): Boolean =
        !usingVlcBackend &&
            vlcMediaPlayer == null &&
            _requestedRenderBackend.value != PlaybackRenderBackend.EXPERIMENTAL_LIBVLC &&
            _activeRenderBackend.value != PlaybackRenderBackend.EXPERIMENTAL_LIBVLC

    private val vlcVoutCallback = object : IVLCVout.Callback {
        override fun onSurfacesCreated(vlcVout: IVLCVout?) {
            emitHybridPlaybackLogInfo(
                "libVLC onSurfacesCreated " +
                    "viewsAttached=${runCatching { vlcVout?.areViewsAttached() }.getOrDefault(false)} " +
                    "pendingStart=$pendingVlcPlaybackStart " +
                    "attachedHost=${attachedVlcVideoHost != null} " +
                    "voutMode=${playbackDebugOverrides.libVlcDebugConfig.voutMode}",
            )
            vlcMediaPlayer?.let { player ->
                maybeAttachLibVlcVmemStream(
                    player = player,
                    reason = "surfaces_created",
                )
                maybeStartPendingVlcPlayback(
                    player = player,
                    reason = "surfaces_created",
                )
            }
            refreshVlcVideoSurfaces(vlcMediaPlayer, reason = "surfaces_created")
            logVlcVideoOutputState(vlcMediaPlayer, reason = "surfaces_created")
            logVlcRenderEvidence(vlcMediaPlayer, reason = "surfaces_created")
        }

        override fun onSurfacesDestroyed(vlcVout: IVLCVout?) {
            emitHybridPlaybackLogInfo(
                "libVLC onSurfacesDestroyed " +
                    "viewsAttached=${runCatching { vlcVout?.areViewsAttached() }.getOrDefault(false)} " +
                    "attachedHost=${attachedVlcVideoHost != null} " +
                    "voutMode=${playbackDebugOverrides.libVlcDebugConfig.voutMode}",
            )
            MiruLog.i(
                "HybridPlaybackController",
                "libVLC video surfaces destroyed",
                mapOf(
                    "views_attached" to runCatching {
                        vlcMediaPlayer?.getVLCVout()?.areViewsAttached()
                    }.getOrDefault(false).toString(),
                )
            )
        }
    }

    private val vlcEventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                currentSource?.let { _state.value = PlaybackState.Loading(it) }
            }
            MediaPlayer.Event.Buffering -> {
                currentSource?.let {
                    _state.value = PlaybackState.Buffering(it, vlcMediaPlayer?.time ?: 0L)
                }
            }
            MediaPlayer.Event.Playing -> {
                emitHybridPlaybackLogInfo(
                    "libVLC event=Playing " +
                        "timeMs=${vlcMediaPlayer?.time ?: 0L} " +
                        "viewsAttached=${runCatching { vlcMediaPlayer?.getVLCVout()?.areViewsAttached() }.getOrDefault(false)} " +
                        "pendingStart=$pendingVlcPlaybackStart",
                )
                currentSource?.let {
                    _state.value = PlaybackState.Playing(it, vlcMediaPlayer?.time ?: 0L)
                }
                updateVlcTracks()
                scheduleFallbackLibVlcNativeSnapshot(vlcMediaPlayer, reason = "playing_event")
                logVlcRenderEvidence(vlcMediaPlayer, reason = "playing")
            }
            MediaPlayer.Event.Paused -> {
                currentSource?.let {
                    _state.value = PlaybackState.Paused(it, vlcMediaPlayer?.time ?: 0L)
                }
            }
            MediaPlayer.Event.Stopped -> {
                emitHybridPlaybackLogInfo("libVLC event=Stopped")
                _state.value = PlaybackState.Idle
            }
            MediaPlayer.Event.EndReached -> {
                emitHybridPlaybackLogInfo("libVLC event=EndReached")
                currentSource?.let { _state.value = PlaybackState.Ended(it) }
            }
            MediaPlayer.Event.EncounteredError -> {
                emitHybridPlaybackLogError(
                    "libVLC event=EncounteredError " +
                        "timeMs=${vlcMediaPlayer?.time ?: 0L} " +
                        "viewsAttached=${runCatching { vlcMediaPlayer?.getVLCVout()?.areViewsAttached() }.getOrDefault(false)}",
                )
                val source = currentSource
                _state.value = PlaybackState.Error(source, "libVLC playback error")
            }
            MediaPlayer.Event.TimeChanged,
            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESDeleted,
            MediaPlayer.Event.ESSelected,
            -> {
                updateVlcTracks()
                logVlcRenderEvidence(vlcMediaPlayer, reason = "track_or_time_changed")
            }
            MediaPlayer.Event.Vout -> {
                emitHybridPlaybackLogInfo(
                    "libVLC event=Vout " +
                        "timeMs=${vlcMediaPlayer?.time ?: 0L} " +
                        "viewsAttached=${runCatching { vlcMediaPlayer?.getVLCVout()?.areViewsAttached() }.getOrDefault(false)}",
                )
                updateVlcTracks()
                refreshVlcVideoSurfaces(vlcMediaPlayer, reason = "vout_event")
                logVlcVideoOutputState(vlcMediaPlayer, reason = "vout_event")
                logVlcRenderEvidence(vlcMediaPlayer, reason = "vout_event")
            }
            MediaPlayer.Event.PositionChanged -> {
                updateVlcTracks()
                currentSource?.let { source ->
                    if (vlcMediaPlayer?.isPlaying == true) {
                        _state.value = PlaybackState.Playing(source, vlcMediaPlayer?.time ?: 0L)
                    }
                }
            }
        }
    }

    override suspend fun play(source: PlaybackSource) {
        MiruLog.i(
            "HybridPlaybackController",
            "Starting hybrid playback session",
            mapOf(
                "source_uri" to source.uri,
                "source_id" to source.mediaSourceId,
            ),
        )
        playbackPreferences = playbackPreferencesRepository
            .getFormatAwareToneMappingPreferences()
            .normalized()
        MiruLog.i(
            "HybridPlaybackController",
            "Loaded playback preferences",
            mapOf(
                "default_backend" to playbackPreferences.defaultBackend.name,
                "forced_signal" to (playbackDebugOverrides.forcedVideoSignalDescriptor?.signalKind?.name ?: "none"),
                "libvlc_hw_mode" to playbackDebugOverrides.libVlcDebugConfig.hwMode.name,
                "libvlc_vout_mode" to playbackDebugOverrides.libVlcDebugConfig.voutMode.name,
            ),
        )
        currentSource = source
        _requestedRenderBackend.value = sessionState.effectiveRequestedBackend(playbackPreferences.defaultBackend)
        _sessionRuleOverrides.value = sessionState.ruleOverrides
        currentHttpConfig = httpRequestResolver.configFor(source)
        signalProbeCompletionJob?.cancel()
        MiruLog.i(
            "HybridPlaybackController",
            "Starting initial video signal probe",
            mapOf(
                "source_uri" to source.uri,
                "requested_backend" to _requestedRenderBackend.value.name,
            ),
        )
        val initialProbeResult = withContext(Dispatchers.IO) {
            runInitialSignalProbe(
                containerTimeoutMs = INITIAL_CONTAINER_SIGNAL_PROBE_TIMEOUT_MS,
                runtimeTimeoutMs = INITIAL_RUNTIME_SIGNAL_PROBE_TIMEOUT_MS,
                containerProbe = {
                    probeContainerVideoSignalDescriptor(
                        context = context,
                        uri = source.uri,
                        httpConfig = currentHttpConfig,
                    )
                },
                runtimeProbe = {
                    probeRuntimeVideoTrackMetadata(
                        context = context,
                        uri = source.uri,
                        httpConfig = currentHttpConfig,
                    )?.let(::resolveVideoSignalDescriptor)
                },
            )
        }
        containerSignalDescriptor = initialProbeResult.containerValue
        runtimeSignalDescriptor = initialProbeResult.runtimeValue
        MiruLog.i(
            "HybridPlaybackController",
            "Finished initial video signal probe",
            mapOf(
                "container_completed_within_budget" to initialProbeResult.containerCompletedWithinBudget.toString(),
                "runtime_completed_within_budget" to initialProbeResult.runtimeCompletedWithinBudget.toString(),
                "container_signal" to (containerSignalDescriptor?.displayLabel().orEmpty()),
                "runtime_signal" to (runtimeSignalDescriptor?.displayLabel().orEmpty()),
            ),
        )
        val mergedDescriptor = mergeVideoSignalDescriptor(
            runtimeDescriptor = runtimeSignalDescriptor ?: VideoSignalDescriptor(),
            containerHint = containerSignalDescriptor,
        )
        _currentVideoSignalDescriptor.value = playbackDebugOverrides.forcedVideoSignalDescriptor
            ?: when {
                runtimeSignalDescriptor == null && containerSignalDescriptor == null -> null
                else -> mergedDescriptor
            }
        refreshRuntimeConfig()
        scheduleSignalProbeCompletionIfNeeded(
            source = source,
            currentHttpConfigSnapshot = currentHttpConfig,
            probeResult = initialProbeResult,
        )
        val resolvedBackend = _activeRenderBackend.value
        MiruLog.i(
            "HybridPlaybackController",
            "Resolved playback runtime config",
            mapOf(
                "requested_backend" to _requestedRenderBackend.value.name,
                "active_backend" to resolvedBackend.name,
                "signal_label" to _currentVideoSignalDescriptor.value?.displayLabel().orEmpty(),
                "rule_key" to _currentRenderRuleKey.value.name,
                "fallback_reason" to _fallbackReason.value.orEmpty(),
            ),
        )
        usingVlcBackend = resolvedBackend == PlaybackRenderBackend.EXPERIMENTAL_LIBVLC
        when (resolvedBackend) {
            PlaybackRenderBackend.STANDARD_EXO -> playWithExo(source)
            PlaybackRenderBackend.EXPERIMENTAL_LIBVLC -> playWithLibVlc(source)
            PlaybackRenderBackend.EXPERIMENTAL_GL -> playWithExo(source)
        }
    }

    private suspend fun playWithExo(source: PlaybackSource) {
        usingVlcBackend = false
        exoController.play(source)
        syncStateFromExo()
    }

    private suspend fun playWithLibVlc(source: PlaybackSource) {
        usingVlcBackend = true
        MiruLog.i(
            "HybridPlaybackController",
            "Entering libVLC playback path",
            mapOf(
                "source_uri" to source.uri,
                "signal_label" to _currentVideoSignalDescriptor.value?.displayLabel().orEmpty(),
                "rule_key" to _currentRenderRuleKey.value.name,
                "vout_mode" to playbackDebugOverrides.libVlcDebugConfig.voutMode.name,
                "hw_mode" to playbackDebugOverrides.libVlcDebugConfig.hwMode.name,
            ),
        )
        hasLoggedVlcDisplayedFrames = false
        hasLoggedVlcDecodedWithoutDisplay = false
        pendingVlcPlaybackStart = false
        val requestedOptions =
            buildLibVlcOptions(
                _currentToneMappingRuleSet.value,
                _currentVideoSignalDescriptor.value,
                playbackDebugOverrides.libVlcDebugConfig,
            )
        val effectiveRequestedOptions = if (playbackDebugOverrides.skipLibVlcStartupOptions) {
            emptyList()
        } else {
            requestedOptions
        }
        val startupProbeResult = if (playbackDebugOverrides.skipLibVlcStartupProbe) {
            LibVlcStartupProbeResult(canStart = true)
        } else {
            withContext(Dispatchers.IO) {
                libVlcStartupProbe.canStartLibVlc(effectiveRequestedOptions)
            }
        }
        if (!startupProbeResult.canStart) {
            usingVlcBackend = false
            releaseVlcPlayer()
            val reason = startupProbeResult.errorMessage.orEmpty().ifBlank { "unknown" }
            MiruLog.w(
                "HybridPlaybackController",
                "libVLC startup probe failed",
                attributes = mapOf(
                    "source_uri" to source.uri,
                    "reason" to reason,
                    "vlc_requested_options" to effectiveRequestedOptions.joinToString(" "),
                ),
            )
            _state.value = PlaybackState.Error(source, "libVLC 启动探测失败: $reason")
            return
        }
        val compatibilityError = withContext(Dispatchers.IO) {
            runCatching {
                LibVlcLibraryBootstrap.ensureCompatibleCpu(context)
                null
            }.getOrElse { error ->
                error.message.orEmpty().ifBlank { "libVLC CPU/ABI incompatible" }
            }
        }
        if (compatibilityError != null) {
            usingVlcBackend = false
            releaseVlcPlayer()
            MiruLog.w(
                "HybridPlaybackController",
                "libVLC compatibility check failed",
                attributes = mapOf(
                    "source_uri" to source.uri,
                    "reason" to compatibilityError,
                    "vlc_requested_options" to effectiveRequestedOptions.joinToString(" "),
                ),
            )
            _state.value = PlaybackState.Error(source, "libVLC 兼容性检查失败: $compatibilityError")
            return
        }
        pendingLibVlcNativeSnapshotJob?.cancel()
        pendingLibVlcNativeSnapshotJob = null
        pendingLibVlcNativeSnapshotKickJob?.cancel()
        pendingLibVlcNativeSnapshotKickJob = null
        pendingLibVlcFrameProbeJob?.cancel()
        pendingLibVlcFrameProbeJob = null
        activeLibVlcFrameProbeSession?.let { libVlcFrameProbeBridge.releaseProbe(it) }
        activeLibVlcFrameProbeSession = null
        activeLibVlcVmemStreamSession?.let { libVlcVmemStreamBridge.releaseStream(it) }
        activeLibVlcVmemStreamSession = null
        withContext(Dispatchers.Main) {
            releaseVlcPlayer()
            val effectiveOptions = effectiveRequestedOptions.toMutableList()
            runCatching {
                MiruLog.i(
                    "HybridPlaybackController",
                    "Creating libVLC instance",
                    mapOf(
                        "vlc_effective_options" to effectiveOptions.joinToString(" "),
                    ),
                )
                LibVlcLibraryBootstrap.ensureLibrariesLoaded()
                libVlc = LibVLC(context, effectiveOptions)
                MiruLog.i(
                    "HybridPlaybackController",
                    "Created libVLC instance",
                    mapOf(
                        "vlc_instance_present" to (libVlc != null).toString(),
                    ),
                )
                MiruLog.i("HybridPlaybackController", "Creating libVLC MediaPlayer")
                vlcMediaPlayer = MediaPlayer(libVlc).also { player ->
                    MiruLog.i(
                        "HybridPlaybackController",
                        "Created libVLC MediaPlayer",
                        mapOf(
                            "player_present" to "true",
                            "host_present" to (vlcVideoHost != null).toString(),
                        ),
                    )
                    player.getVLCVout().addCallback(vlcVoutCallback)
                    player.setEventListener(vlcEventListener)
                    bindExistingVlcHost(player)
                    val armedFrameProbe = maybeArmLibVlcFrameProbe(player)
                    val playbackUri = currentHttpConfig.libVlcUriFor(source.uri)
                    val mediaTarget = resolveLibVlcMediaTarget(playbackUri)
                    val appliedMediaOptions = mutableListOf<String>()
                    MiruLog.i(
                        "HybridPlaybackController",
                        "Creating libVLC media",
                        mapOf(
                            "resolved_vlc_uri" to playbackUri,
                            "media_target" to mediaTarget.javaClass.simpleName,
                        ),
                    )
                    val media = when (mediaTarget) {
                        is LibVlcMediaTarget.LocalPath -> Media(libVlc, mediaTarget.path)
                        is LibVlcMediaTarget.Location -> Media(libVlc, Uri.parse(mediaTarget.uri))
                    }.apply {
                        setDefaultMediaPlayerOptions()
                        applyLibVlcMediaOptionsInternal(
                            media = this,
                            ruleSet = this@HybridPlaybackController._currentToneMappingRuleSet.value,
                            appliedOptions = appliedMediaOptions,
                            debugConfig = playbackDebugOverrides.libVlcDebugConfig,
                        )
                        source.subtitleTracks.forEach { track ->
                            addSlave(
                                IMedia.Slave(
                                    IMedia.Slave.Type.Subtitle,
                                    4,
                                    Uri.parse(track.path).toString(),
                                )
                            )
                        }
                    }
                    MiruLog.i(
                        "HybridPlaybackController",
                        "Setting libVLC media",
                        mapOf(
                            "vlc_media_options" to appliedMediaOptions.joinToString(" "),
                            "subtitle_count" to source.subtitleTracks.size.toString(),
                        ),
                    )
                    player.setMedia(media)
                    media.release()
                    if (source.startPosition > 0L) {
                        player.time = source.startPosition
                    }
                    _state.value = PlaybackState.Loading(source)
                    armedFrameProbe?.let { session ->
                        activeLibVlcFrameProbeSession = session
                        awaitLibVlcFrameProbe(session)
                    }
                    val playbackStartDeferred = shouldDeferLibVlcPlaybackStartUntilHostAttach()
                    if (playbackStartDeferred) {
                        pendingVlcPlaybackStart = true
                        val playbackStartAttributes = mutableMapOf(
                            "backend" to _activeRenderBackend.value.name,
                            "vout_mode" to playbackDebugOverrides.libVlcDebugConfig.voutMode.name,
                            "host_attached" to (attachedVlcVideoHost != null).toString(),
                        )
                        vlcVideoHost?.let { host ->
                            playbackStartAttributes += buildVlcHostStateAttributes(
                                host = host,
                                reason = "playback_start_deferred",
                            )
                        }
                        MiruLog.i(
                            "HybridPlaybackController",
                            "Deferring libVLC playback start until video host attaches",
                            playbackStartAttributes,
                        )
                    } else {
                        MiruLog.i(
                            "HybridPlaybackController",
                            "Calling libVLC player.play",
                            mapOf(
                                "views_attached" to runCatching { player.getVLCVout().areViewsAttached() }.getOrDefault(false).toString(),
                            ),
                        )
                        player.play()
                        MiruLog.i("HybridPlaybackController", "Returned from libVLC player.play")
                    }
                    MiruLog.i(
                        "HybridPlaybackController",
                        "Started libVLC playback",
                        mapOf(
                        "source_uri" to source.uri,
                        "resolved_vlc_uri" to playbackUri,
                        "signal_label" to _currentVideoSignalDescriptor.value?.displayLabel().orEmpty(),
                        "rule_key" to _currentRenderRuleKey.value.name,
                        "backend" to _activeRenderBackend.value.name,
                        "playback_start_deferred" to playbackStartDeferred.toString(),
                        "vlc_requested_options" to requestedOptions.joinToString(" "),
                        "vlc_effective_options" to effectiveOptions.joinToString(" "),
                        "vlc_media_options" to appliedMediaOptions.joinToString(" "),
                    )
                    )
                    MiruLog.i(
                        "HybridPlaybackController",
                        "Started libVLC playback (debug summary)",
                        mapOf(
                            "rule_key" to _currentRenderRuleKey.value.name,
                            "vlc_requested_options" to requestedOptions.joinToString(" "),
                            "vlc_media_options" to appliedMediaOptions.joinToString(" "),
                        ),
                    )
                }
            }.onFailure { error ->
                releaseVlcPlayer()
                MiruLog.w(
                    "HybridPlaybackController",
                    "Failed to initialize libVLC backend",
                    error,
                    mapOf(
                        "source_uri" to source.uri,
                        "rule_key" to _currentRenderRuleKey.value.name,
                        "vlc_requested_options" to requestedOptions.joinToString(" "),
                        "vlc_effective_options" to effectiveOptions.joinToString(" "),
                    )
                )
                _state.value = PlaybackState.Error(source, "libVLC 初始化失败: ${error.message.orEmpty()}")
            }
        }
    }

    override suspend fun pause() {
        if (usingVlcBackend) {
            withContext(Dispatchers.Main) {
                vlcMediaPlayer?.pause()
            }
        } else {
            exoController.pause()
            syncStateFromExo()
        }
    }

    override suspend fun resume() {
        if (usingVlcBackend) {
            withContext(Dispatchers.Main) {
                vlcMediaPlayer?.play()
            }
        } else {
            exoController.resume()
            syncStateFromExo()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        if (usingVlcBackend) {
            withContext(Dispatchers.Main) {
                vlcMediaPlayer?.time = positionMs
            }
        } else {
            exoController.seekTo(positionMs)
            syncStateFromExo()
        }
    }

    override suspend fun stop() {
        stopInternal(clearSessionState = true)
    }

    private suspend fun stopInternal(clearSessionState: Boolean) {
        if (usingVlcBackend) {
            withContext(Dispatchers.Main) {
                releaseVlcPlayer()
                currentSource = null
                availableSubtitles.clear()
                availableAudioTracks.clear()
                sessionState = sessionState.afterPlaybackReset(clearSessionState)
                _requestedRenderBackend.value = playbackPreferences.defaultBackend
                _sessionRuleOverrides.value = sessionState.ruleOverrides
                containerSignalDescriptor = null
                runtimeSignalDescriptor = null
                currentHttpConfig = PlaybackHttpRequestConfig.Empty
                _currentVideoSignalDescriptor.value = null
                refreshRuntimeConfig()
                _state.value = PlaybackState.Idle
            }
        } else {
            exoController.stop(clearSessionState = clearSessionState)
            syncStateFromExo()
            currentSource = null
            currentHttpConfig = PlaybackHttpRequestConfig.Empty
        }
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        if (usingVlcBackend) {
            withContext(Dispatchers.Main) {
                vlcMediaPlayer?.rate = speed.coerceIn(0.25f, 3.0f)
            }
        } else {
            exoController.setPlaybackSpeed(speed)
        }
    }

    override suspend fun setSubtitleTrack(trackIndex: Int) {
        if (usingVlcBackend) {
            withContext(Dispatchers.Main) {
                if (trackIndex < 0) {
                    vlcMediaPlayer?.unselectSubtitleTrackCompat()
                } else {
                    val tracks = vlcMediaPlayer?.subtitleTracksCompat().orEmpty()
                    val track = tracks.getOrNull(trackIndex) ?: return@withContext
                    vlcMediaPlayer?.selectSubtitleTrackCompat(track)
                }
            }
        } else {
            exoController.setSubtitleTrack(trackIndex)
        }
    }

    override suspend fun setAudioTrack(trackIndex: Int) {
        if (usingVlcBackend) {
            withContext(Dispatchers.Main) {
                val tracks = vlcMediaPlayer?.audioTracksCompat().orEmpty()
                val track = tracks.getOrNull(trackIndex) ?: return@withContext
                vlcMediaPlayer?.selectAudioTrackCompat(track)
            }
        } else {
            exoController.setAudioTrack(trackIndex)
        }
    }

    override fun getAvailableSubtitles(): List<SubtitleTrack> =
        if (usingVlcBackend) availableSubtitles.toList() else exoController.getAvailableSubtitles()

    override fun getAvailableAudioTracks(): List<AudioTrack> =
        if (usingVlcBackend) availableAudioTracks.toList() else exoController.getAvailableAudioTracks()

    override suspend fun getCurrentPosition(): Long =
        if (usingVlcBackend) withContext(Dispatchers.Main) { vlcMediaPlayer?.time ?: 0L }
        else exoController.getCurrentPosition()

    override suspend fun getDuration(): Long =
        if (usingVlcBackend) withContext(Dispatchers.Main) { vlcMediaPlayer?.length ?: 0L }
        else exoController.getDuration()

    override fun isPlaying(): Boolean =
        if (usingVlcBackend) vlcMediaPlayer?.isPlaying == true else exoController.isPlaying()

    override fun getPlayer(): Player? =
        if (usingVlcBackend) null else exoController.getPlayer()

    override fun usesVlcVideoLayout(): Boolean =
        _activeRenderBackend.value == PlaybackRenderBackend.EXPERIMENTAL_LIBVLC

    override fun bindVlcVideoHost(hostView: View) {
        val videoLayout = hostView as? VLCVideoLayout ?: return
        emitHybridPlaybackLogInfo(
            "bindVlcVideoHost host=${hostView.javaClass.simpleName} " +
                "sameHost=${vlcVideoHost === videoLayout} " +
                "sameAttached=${attachedVlcVideoHost === videoLayout} " +
                "usingVlc=$usingVlcBackend " +
                "playerPresent=${vlcMediaPlayer != null} " +
                "requested=${_requestedRenderBackend.value} active=${_activeRenderBackend.value} " +
                "voutMode=${playbackDebugOverrides.libVlcDebugConfig.voutMode}",
        )
        MiruLog.i(
            "HybridPlaybackController",
            "bindVlcVideoHost",
            mapOf(
                "host" to hostView.javaClass.simpleName,
                "using_vlc" to usingVlcBackend.toString(),
                "player_present" to (vlcMediaPlayer != null).toString(),
                "requested_backend" to _requestedRenderBackend.value.name,
                "active_backend" to _activeRenderBackend.value.name,
            ),
        )
        if (vlcVideoHost === videoLayout && attachedVlcVideoHost === videoLayout) {
            return
        }
        vlcVideoHost = videoLayout
        vlcMediaPlayer?.let { bindExistingVlcHost(it) }
    }

    override fun unbindVlcVideoHost() {
        removeVlcHostLayoutListener()
        detachBoundVlcVideoHost(vlcMediaPlayer)
        releaseVlcDisplayManager()
        attachedVlcVideoHost = null
        vlcVideoHost = null
    }

    override suspend fun setRequestedRenderBackend(backend: PlaybackRenderBackend?) {
        val previousBackend = _activeRenderBackend.value
        sessionState = sessionState.withRequestedBackendOverride(backend)
        _requestedRenderBackend.value = sessionState.effectiveRequestedBackend(playbackPreferences.defaultBackend)
        refreshRuntimeConfig()
        if (previousBackend != _activeRenderBackend.value) {
            rebuildActivePlayback()
        }
    }

    override suspend fun setSessionRuleOverride(ruleKey: VideoRenderRuleKey, ruleSet: ToneMappingRuleSet?) {
        sessionState = sessionState.withRuleOverride(ruleKey, ruleSet)
        _sessionRuleOverrides.value = sessionState.ruleOverrides
        val previousRuleSet = _currentToneMappingRuleSet.value
        refreshRuntimeConfig()
        if (_currentToneMappingRuleSet.value != previousRuleSet) {
            rebuildActivePlayback()
        }
    }

    override suspend fun clearSessionRuleOverrides() {
        val previousRuleSet = _currentToneMappingRuleSet.value
        sessionState = sessionState.clearRuleOverrides()
        _sessionRuleOverrides.value = sessionState.ruleOverrides
        refreshRuntimeConfig()
        if (_currentToneMappingRuleSet.value != previousRuleSet) {
            rebuildActivePlayback()
        }
    }

    override fun pendingGlFrameCaptureLabel(): String? =
        playbackDebugOverrides.peekPendingGlFrameCaptureLabel()

    override fun pendingLibVlcNativeSnapshotLabel(): String? =
        playbackDebugOverrides.peekPendingLibVlcNativeSnapshotLabel()

    override fun requestLibVlcNativeSnapshot(label: String) {
        playbackDebugOverrides.requestPendingLibVlcNativeSnapshotLabel(label)
        val player = vlcMediaPlayer ?: return
        val evidence = readLibVlcRenderEvidence(player)
        if (evidence != null) {
            maybeCapturePendingLibVlcNativeSnapshot(
                player = player,
                evidence = evidence,
                reason = "gl_frame_captured",
            )
        } else {
            scheduleFallbackLibVlcNativeSnapshot(
                player = player,
                reason = "gl_frame_captured",
            )
        }
    }

    override fun currentLibVlcVoutMode(): LibVlcVoutMode? =
        playbackDebugOverrides.libVlcDebugConfig.voutMode

    override fun clearPendingGlFrameCaptureLabel(label: String) {
        playbackDebugOverrides.clearPendingGlFrameCaptureLabel(label)
    }

    override fun clearPendingLibVlcNativeSnapshotLabel(label: String) {
        playbackDebugOverrides.clearPendingLibVlcNativeSnapshotLabel(label)
    }

    private fun updateVlcTracks() {
        val player = vlcMediaPlayer ?: return
        availableAudioTracks.clear()
        availableSubtitles.clear()
        player.audioTracksCompat().forEachIndexed { index, track ->
            availableAudioTracks += AudioTrack(
                index = index,
                language = track.language?.takeIf { it.isNotBlank() } ?: "und",
                title = track.name ?: track.description ?: track.language ?: "Audio ${index + 1}",
                codec = null,
            )
        }
        player.subtitleTracksCompat().forEach { track ->
            availableSubtitles += SubtitleTrack(
                language = track.language?.takeIf { it.isNotBlank() } ?: "und",
                title = track.name ?: track.description ?: track.language.orEmpty(),
                isExternal = false,
                path = "",
                format = SubtitleFormat.SRT,
            )
        }
    }

    private fun refreshRuntimeConfig() {
        val descriptor = _currentVideoSignalDescriptor.value ?: VideoSignalDescriptor()
        val config = resolveToneMappingRuntimeConfig(
            preferences = playbackPreferences.normalized(),
            sessionRuleOverrides = _sessionRuleOverrides.value,
            signalDescriptor = descriptor,
            requestedBackendOverride = _requestedRenderBackend.value,
        )
        _currentRenderRuleKey.value = config.ruleKey
        _currentToneMappingRuleSet.value = config.appliedRuleSet
        _activeRenderBackend.value = config.activeBackend
        _fallbackReason.value = config.fallbackReason
    }

    private fun buildLibVlcOptions(
        ruleSet: ToneMappingRuleSet,
        signalDescriptor: VideoSignalDescriptor?,
        debugConfig: LibVlcDebugConfig,
    ): List<String> =
        buildLibVlcOptionsInternal(ruleSet, signalDescriptor, debugConfig)

    private suspend fun rebuildActivePlayback() {
        val source = currentSource ?: return
        val resumePosition = getCurrentPosition()
        val wasPlaying = isPlaying()
        val rebuiltSource = source.copy(startPosition = resumePosition.coerceAtLeast(0L))
        stopInternal(clearSessionState = false)
        currentSource = rebuiltSource
        play(rebuiltSource)
        if (!wasPlaying) {
            pause()
        }
    }

    private fun syncStateFromExo() {
        _state.value = exoController.state.value
        _requestedRenderBackend.value = exoController.requestedRenderBackend.value
        _activeRenderBackend.value = exoController.activeRenderBackend.value
        _currentVideoSignalDescriptor.value = exoController.currentVideoSignalDescriptor.value
        _currentRenderRuleKey.value = exoController.currentRenderRuleKey.value
        _currentToneMappingRuleSet.value = exoController.currentToneMappingRuleSet.value
        _sessionRuleOverrides.value = exoController.sessionRuleOverrides.value
        _fallbackReason.value = exoController.fallbackReason.value
    }

    private fun bindExistingVlcHost(player: MediaPlayer) {
        val host = vlcVideoHost ?: return
        emitHybridPlaybackLogInfo(
            "bindExistingVlcHost host=${host.javaClass.simpleName} " +
                "attachedHost=${attachedVlcVideoHost === host} " +
                "hostSize=${host.width}x${host.height} " +
                "hostAttached=${host.isAttachedToWindow} laidOut=${host.isLaidOut} " +
                "surfaceMode=${shouldUseSurfaceVideoHostAttach()} " +
                "outputCallbacks=${shouldUseOutputCallbackAttach()} " +
                "vmem=${shouldUseVmemProbeVout()} " +
                "voutMode=${playbackDebugOverrides.libVlcDebugConfig.voutMode}",
        )
        MiruLog.i(
            "HybridPlaybackController",
            "bindExistingVlcHost",
            mapOf(
                "host" to host.javaClass.simpleName,
                "output_callbacks" to shouldUseOutputCallbackAttach().toString(),
                "surface_host" to shouldUseSurfaceVideoHostAttach().toString(),
                "direct_texture" to shouldUseTextureViewAttach().toString(),
                "vmem" to shouldUseVmemProbeVout().toString(),
                "host_width" to host.width.toString(),
                "host_height" to host.height.toString(),
                "attached" to host.isAttachedToWindow.toString(),
                "laid_out" to host.isLaidOut.toString(),
            ),
        )
        ensureVlcHostLayoutListener(host, player)
        val directTextureEnabled = shouldUseTextureViewAttach() || shouldUseHiddenTextureCarrier()
        val surfaceVideoHostEnabled = shouldUseSurfaceVideoHostAttach()
        val outputCallbackEnabled = shouldUseOutputCallbackAttach()
        val vmemStreamEnabled = shouldUseVmemStreamVout()
        (host as? LibVlcDirectVideoHost)?.setLibVlcDirectTextureEnabled(directTextureEnabled)
        (host as? LibVlcSurfaceVideoHost)?.setLibVlcVideoSurfaceEnabled(surfaceVideoHostEnabled)
        (host as? LibVlcOutputCallbackVideoHost)?.setLibVlcOutputCallbackEnabled(outputCallbackEnabled)
        (host as? LibVlcVmemVideoHost)?.setLibVlcVmemStreamEnabled(vmemStreamEnabled)
        (host as? LibVlcVmemVideoHost)?.bindLibVlcVmemStream(
            bridge = if (vmemStreamEnabled) libVlcVmemStreamBridge else null,
            session = if (vmemStreamEnabled) activeLibVlcVmemStreamSession else null,
        )
        if (vmemStreamEnabled) {
            if (!isVlcHostReadyForAttach(host)) {
                emitHybridPlaybackLogInfo(
                    "Deferring VMEM stream host activation because host is not ready " +
                        buildVlcHostStateAttributes(host, reason = "vmem_stream_bind_deferred_logcat")
                            .entries
                            .joinToString(" ") { "${it.key}=${it.value}" },
                )
                MiruLog.i(
                    "HybridPlaybackController",
                    "Deferring libVLC VMEM stream host activation until video host is ready",
                    buildVlcHostStateAttributes(host, reason = "vmem_stream_bind_deferred"),
                )
                return
            }
            if (
                attachedVlcVideoHost === host &&
                !usingDirectTextureAttach &&
                !usingSurfaceVideoHostAttach &&
                !usingOutputCallbackAttach &&
                !usingHiddenCarrierAttach
            ) {
                syncVlcHostWindowSize(player, host, reason = "rebind_vmem_stream_host")
                maybeStartPendingVlcPlayback(player, reason = "rebind_vmem_stream_host")
                return
            }
            if (attachedVlcVideoHost != null) {
                removeVlcHostLayoutListener()
                if (
                    usingDirectTextureAttach ||
                    usingSurfaceVideoHostAttach ||
                    usingOutputCallbackAttach ||
                    usingHiddenCarrierAttach
                ) {
                    detachBoundVlcVideoHost(player)
                } else {
                    (attachedVlcVideoHost as? LibVlcVmemVideoHost)?.bindLibVlcVmemStream(null, null)
                    (attachedVlcVideoHost as? LibVlcVmemVideoHost)?.setLibVlcVmemStreamEnabled(false)
                }
                attachedVlcVideoHost = null
                ensureVlcHostLayoutListener(host, player)
            }
            runCatching {
                player.setVideoTrackEnabled(true)
            }.onFailure { error ->
                MiruLog.w(
                    "HybridPlaybackController",
                    "Failed to enable libVLC video track for VMEM stream mode",
                    error,
                    buildVlcHostStateAttributes(host, reason = "vmem_stream_enable_video_track_failed"),
                )
            }
            val hiddenCarrierAttached = runCatching {
                player.attachViews(
                    host,
                    resolveVlcDisplayManager(host),
                    false,
                    true,
                )
                true
            }.onFailure { error ->
                MiruLog.w(
                    "HybridPlaybackController",
                    "Failed to attach hidden libVLC texture carrier for VMEM stream mode",
                    error,
                    buildVlcHostStateAttributes(host, reason = "vmem_stream_hidden_carrier_attach_failed"),
                )
            }.getOrDefault(false)
            usingDirectTextureAttach = false
            usingSurfaceVideoHostAttach = false
            usingOutputCallbackAttach = false
            usingHiddenCarrierAttach = hiddenCarrierAttached
            attachedVlcVideoHost = host
            player.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
            syncVlcHostWindowSize(player, host, reason = "vmem_stream_attach_views")
            logVlcVideoOutputState(player, reason = "vmem_stream_attach_views")
            maybeStartPendingVlcPlayback(player, reason = "vmem_stream_ready")
            return
        }
        if (shouldUseVmemProbeVout()) {
            if (attachedVlcVideoHost !== host) {
                (attachedVlcVideoHost as? LibVlcVmemVideoHost)?.bindLibVlcVmemStream(null, null)
                (attachedVlcVideoHost as? LibVlcVmemVideoHost)?.setLibVlcVmemStreamEnabled(false)
            }
            usingHiddenCarrierAttach = false
            attachedVlcVideoHost = host
            MiruLog.i(
                "HybridPlaybackController",
                "Skipping libVLC host attachment because VMEM probe mode is active",
                buildVlcHostStateAttributes(host, reason = "vmem_probe"),
            )
            return
        }
        if (attachedVlcVideoHost === host) {
            emitHybridPlaybackLogInfo(
                "bindExistingVlcHost reusing already attached host " +
                    "viewsAttached=${runCatching { player.getVLCVout().areViewsAttached() }.getOrDefault(false)}",
            )
            syncVlcHostWindowSize(player, host, reason = "rebind_existing_host")
            maybeStartPendingVlcPlayback(player, reason = "rebind_existing_host")
            return
        }
        if (!isVlcHostReadyForAttach(host)) {
            emitHybridPlaybackLogInfo(
                "Deferring bindExistingVlcHost because host is not ready " +
                    buildVlcHostStateAttributes(host, reason = "bind_deferred_logcat")
                        .entries
                        .joinToString(" ") { "${it.key}=${it.value}" },
            )
            MiruLog.i(
                "HybridPlaybackController",
                "Deferring libVLC host attach until video host is ready",
                buildVlcHostStateAttributes(host, reason = "bind_deferred")
            )
            return
        }
        val surfaceVideoHost = host as? LibVlcSurfaceVideoHost
        val outputCallbackHost = host as? LibVlcOutputCallbackVideoHost
        if (attachedVlcVideoHost != null) {
            removeVlcHostLayoutListener()
            detachBoundVlcVideoHost(player)
            attachedVlcVideoHost = null
            ensureVlcHostLayoutListener(host, player)
        }
        if (outputCallbackEnabled) {
            if (outputCallbackHost == null) {
                MiruLog.w(
                    "HybridPlaybackController",
                    "Requested libVLC output callbacks host is unavailable",
                    null,
                    buildVlcHostStateAttributes(host, reason = "output_callbacks_host_missing"),
                )
                return
            }
            MiruLog.i(
                "HybridPlaybackController",
                "Attaching libVLC output callbacks host",
                buildVlcHostStateAttributes(host, reason = "output_callbacks_attach"),
            )
            val attachSucceeded = runCatching {
                player.setVideoTrackEnabled(true)
                val surface = outputCallbackHost.libVlcOutputCallbackSurface()
                    ?: error("Output callbacks host did not expose a Surface")
                val width = outputCallbackHost.libVlcOutputCallbackWidth()
                    .takeIf { it > 0 }
                    ?: host.width
                val height = outputCallbackHost.libVlcOutputCallbackHeight()
                    .takeIf { it > 0 }
                    ?: host.height
                if (!surface.isValid) {
                    error("Output callbacks host exposed an invalid Surface")
                }
                val playerInstance = resolveNativeVlcObjectInstance(
                    directInstance = 0L,
                    holder = player,
                )
                if (playerInstance == 0L) {
                    error("Failed to resolve libVLC player instance for output callbacks attach")
                }
                MiruLog.i(
                    "HybridPlaybackController",
                    "Attaching libVLC output callback host through native output callback bridge",
                    mapOf(
                        "player_instance" to playerInstance.toString(),
                        "surface_hash" to surface.hashCode().toString(),
                        "surface_valid" to surface.isValid.toString(),
                        "width" to width.toString(),
                        "height" to height.toString(),
                    ),
                )
                Log.i(
                    "HybridPlaybackController",
                    "Attaching libVLC output callback host through native output callback bridge " +
                        "playerInstance=$playerInstance " +
                        "surfaceHash=${surface.hashCode()} " +
                        "surfaceValid=${surface.isValid} size=${width}x${height}",
                )
                val attachResult = libVlcOutputCallbacksBridge.attachOutput(
                    playerInstance = playerInstance,
                    surface = surface,
                    width = width,
                    height = height,
                )
                if (!attachResult.success || attachResult.session == null) {
                    error("libVLC output callbacks bridge attach failed resultCode=${attachResult.resultCode}")
                }
                activeLibVlcOutputCallbackSession = attachResult.session
                usingOutputCallbackAttach = true
                usingSurfaceVideoHostAttach = false
                usingDirectTextureAttach = false
                usingHiddenCarrierAttach = false
                player.getVLCVout().setWindowSize(width, height)
            }.onFailure { error ->
                activeLibVlcOutputCallbackSession?.let { session ->
                    libVlcOutputCallbacksBridge.releaseOutput(session)
                }
                activeLibVlcOutputCallbackSession = null
                usingOutputCallbackAttach = false
                usingSurfaceVideoHostAttach = false
                usingDirectTextureAttach = false
                usingHiddenCarrierAttach = false
                MiruLog.w(
                    "HybridPlaybackController",
                    "Failed to attach libVLC output callbacks host",
                    error,
                    buildVlcHostStateAttributes(host, reason = "output_callbacks_attach_failed"),
                )
            }.isSuccess
            if (!attachSucceeded) {
                return
            }
        } else if (surfaceVideoHostEnabled && surfaceVideoHost != null) {
            val vout = player.getVLCVout()
            val surface = surfaceVideoHost.libVlcVideoSurface()
            MiruLog.i(
                "HybridPlaybackController",
                "Attaching libVLC GL surface host",
                buildVlcHostStateAttributes(host, reason = "surface_host_attach"),
            )
            emitHybridPlaybackLogInfo(
                "Attaching libVLC GL surface host " +
                    "surfacePresent=${surface != null} " +
                    "surfaceValid=${runCatching { surface?.isValid }.getOrNull()} " +
                    "surfaceHash=${surface?.hashCode()} " +
                    "surfaceSize=${surfaceVideoHost.libVlcVideoSurfaceWidth()}x${surfaceVideoHost.libVlcVideoSurfaceHeight()} " +
                    "viewsAttachedBefore=${runCatching { vout.areViewsAttached() }.getOrDefault(false)}",
            )
            runCatching {
                player.setVideoTrackEnabled(true)
                val boundSurfaceTexture = surfaceVideoHost.libVlcVideoSurfaceTexture()
                if (boundSurfaceTexture != null) {
                    vout.setVideoSurface(boundSurfaceTexture)
                } else {
                    val boundSurface = surface
                        ?: error("GL surface host did not expose a Surface while GL surface mode is enabled")
                    vout.setVideoSurface(boundSurface, null)
                }
                vout.attachViews(vlcOnNewVideoLayoutListener)
                val width = surfaceVideoHost.libVlcVideoSurfaceWidth().takeIf { it > 0 } ?: host.width
                val height = surfaceVideoHost.libVlcVideoSurfaceHeight().takeIf { it > 0 } ?: host.height
                if (width > 0 && height > 0) {
                    vout.setWindowSize(width, height)
                }
                emitHybridPlaybackLogInfo(
                    "Attached libVLC GL surface host " +
                        "surfaceHash=${runCatching { surface?.hashCode() }.getOrNull()} " +
                        "surfaceTextureHash=${runCatching { boundSurfaceTexture?.hashCode() }.getOrNull()} " +
                        "windowSize=${width}x${height} " +
                        "viewsAttachedAfter=${runCatching { vout.areViewsAttached() }.getOrDefault(false)}",
                )
                usingSurfaceVideoHostAttach = true
                usingDirectTextureAttach = false
                usingHiddenCarrierAttach = false
            }.onFailure { error ->
                emitHybridPlaybackLogError(
                    "Failed to attach libVLC GL surface host " +
                        buildVlcHostStateAttributes(host, reason = "surface_host_attach_failed_logcat")
                            .entries
                            .joinToString(" ") { "${it.key}=${it.value}" },
                    error = error,
                )
                usingSurfaceVideoHostAttach = false
                usingDirectTextureAttach = false
                usingHiddenCarrierAttach = false
                MiruLog.w(
                    "HybridPlaybackController",
                    "Failed to attach libVLC GL surface host; falling back to layout attach",
                    error,
                    buildVlcHostStateAttributes(host, reason = "surface_host_attach_failed"),
                )
                (host as? LibVlcSurfaceVideoHost)?.setLibVlcVideoSurfaceEnabled(false)
                player.attachViews(
                    host,
                    resolveVlcDisplayManager(host),
                    shouldEnableSubtitleSurface(),
                    shouldUseTextureViewAttach(),
                )
            }
        } else {
            usingDirectTextureAttach = false
            usingSurfaceVideoHostAttach = false
            usingHiddenCarrierAttach = false
            player.attachViews(
                host,
                resolveVlcDisplayManager(host),
                shouldEnableSubtitleSurface(),
                shouldUseTextureViewAttach(),
            )
        }
        attachedVlcVideoHost = host
        player.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
        syncVlcHostWindowSize(player, host, reason = "attach_views")
        if (!usingOutputCallbackAttach) {
            refreshVlcVideoSurfaces(player, reason = "attach_views")
        }
        logVlcVideoOutputState(player, reason = "attach_views")
        maybeStartPendingVlcPlayback(player, reason = "attach_views")
    }

    private fun shouldUseHiddenTextureCarrier(): Boolean =
        shouldUseVmemStreamVout()

    private fun shouldUseOutputCallbackAttach(): Boolean =
        playbackDebugOverrides.libVlcDebugConfig.voutMode == LibVlcVoutMode.OUTPUT_CALLBACKS

    private fun shouldUseSurfaceVideoHostAttach(): Boolean =
        playbackDebugOverrides.libVlcDebugConfig.voutMode == LibVlcVoutMode.GL_SURFACE

    private fun shouldUseVmemStreamVout(): Boolean =
        playbackDebugOverrides.libVlcDebugConfig.voutMode == LibVlcVoutMode.VMEM_STREAM

    private fun shouldUseTextureViewAttach(): Boolean =
        playbackDebugOverrides.libVlcDebugConfig.voutMode == LibVlcVoutMode.DIRECT_TEXTURE

    private fun shouldUseVmemProbeVout(): Boolean =
        playbackDebugOverrides.libVlcDebugConfig.voutMode == LibVlcVoutMode.VMEM_PROBE

    private fun shouldEnableSubtitleSurface(): Boolean =
        when (playbackDebugOverrides.libVlcDebugConfig.voutMode) {
            LibVlcVoutMode.GL_SURFACE,
            LibVlcVoutMode.DIRECT_TEXTURE,
            LibVlcVoutMode.OUTPUT_CALLBACKS,
            LibVlcVoutMode.VMEM_STREAM,
            LibVlcVoutMode.VMEM_PROBE,
            -> false
            else -> true
        }

    private val vlcOnNewVideoLayoutListener = IVLCVout.OnNewVideoLayoutListener { _, width, height, placeWidth, placeHeight, placeX, placeY ->
        emitHybridPlaybackLogInfo(
            "libVLC onNewVideoLayout " +
                "video=${width}x${height} place=${placeWidth}x${placeHeight}@${placeX},${placeY} " +
                "voutMode=${playbackDebugOverrides.libVlcDebugConfig.voutMode}",
        )
        vlcVideoHost?.post {
            refreshVlcVideoSurfaces(vlcMediaPlayer, reason = "new_video_layout")
        }
    }

    private fun resolveVlcDisplayManager(host: VLCVideoLayout): DisplayManager? {
        if (playbackDebugOverrides.libVlcDebugConfig.voutMode != LibVlcVoutMode.ANDROID_DISPLAY) {
            return null
        }
        // MiruPlay currently only hosts libVLC inside the primary Compose view hierarchy.
        // Creating libVLC's DisplayManager here can switch VideoHelper into the presentation
        // branch on HDMI boxes, which leaves the primary host with an invalid surface size and
        // no visible video output. Keep the stock android_display attach on the primary host.
        return null
    }

    private fun releaseVlcDisplayManager() {
        runCatching { vlcDisplayManager?.release() }
        vlcDisplayManager = null
        vlcDisplayManagerActivity = null
        vlcRendererSelection = MutableLiveData()
    }

    private fun releaseVlcPlayer() {
        emitHybridPlaybackLogInfo(
            "releaseVlcPlayer " +
                "playerPresent=${vlcMediaPlayer != null} attachedHost=${attachedVlcVideoHost != null} " +
                "usingSurfaceHost=$usingSurfaceVideoHostAttach usingOutputCallbacks=$usingOutputCallbackAttach " +
                "usingDirectTexture=$usingDirectTextureAttach usingHiddenCarrier=$usingHiddenCarrierAttach",
        )
        activeLibVlcFrameProbeSession?.let { libVlcFrameProbeBridge.releaseProbe(it) }
        activeLibVlcFrameProbeSession = null
        activeLibVlcOutputCallbackSession?.let { libVlcOutputCallbacksBridge.releaseOutput(it) }
        activeLibVlcOutputCallbackSession = null
        activeLibVlcVmemStreamSession?.let { libVlcVmemStreamBridge.releaseStream(it) }
        activeLibVlcVmemStreamSession = null
        signalProbeCompletionJob?.cancel()
        signalProbeCompletionJob = null
        pendingLibVlcFrameProbeJob?.cancel()
        pendingLibVlcFrameProbeJob = null
        pendingLibVlcNativeSnapshotJob?.cancel()
        pendingLibVlcNativeSnapshotJob = null
        pendingLibVlcNativeSnapshotKickJob?.cancel()
        pendingLibVlcNativeSnapshotKickJob = null
        pendingVlcPlaybackStart = false
        removeVlcHostLayoutListener()
        vlcMediaPlayer?.setEventListener(null)
        runCatching { vlcMediaPlayer?.getVLCVout()?.removeCallback(vlcVoutCallback) }
        runCatching { vlcMediaPlayer?.stop() }
        detachBoundVlcVideoHost(vlcMediaPlayer)
        releaseVlcDisplayManager()
        attachedVlcVideoHost = null
        vlcMediaPlayer?.release()
        vlcMediaPlayer = null
        libVlc?.release()
        libVlc = null
        usingDirectTextureAttach = false
        usingSurfaceVideoHostAttach = false
        usingOutputCallbackAttach = false
        usingHiddenCarrierAttach = false
        hasLoggedVlcDisplayedFrames = false
        hasLoggedVlcDecodedWithoutDisplay = false
        (vlcVideoHost as? LibVlcVmemVideoHost)?.bindLibVlcVmemStream(null, null)
        (vlcVideoHost as? LibVlcVmemVideoHost)?.setLibVlcVmemStreamEnabled(false)
    }

    private fun scheduleSignalProbeCompletionIfNeeded(
        source: PlaybackSource,
        currentHttpConfigSnapshot: PlaybackHttpRequestConfig,
        probeResult: InitialSignalProbeResult<VideoSignalDescriptor, VideoSignalDescriptor>,
    ) {
        if (!probeResult.requiresBackgroundCompletion) {
            return
        }
        signalProbeCompletionJob = controllerScope.launch(Dispatchers.IO) {
            val currentSourceUri = source.uri
            val containerDeferred = async {
                probeResult.containerValue ?: probeContainerVideoSignalDescriptor(
                    context = context,
                    uri = currentSourceUri,
                    httpConfig = currentHttpConfigSnapshot,
                )
            }
            val runtimeDeferred = async {
                probeResult.runtimeValue ?: probeRuntimeVideoTrackMetadata(
                    context = context,
                    uri = currentSourceUri,
                    httpConfig = currentHttpConfigSnapshot,
                )?.let(::resolveVideoSignalDescriptor)
            }
            val completedContainerDescriptor = runCatching { containerDeferred.await() }.getOrNull()
            val completedRuntimeDescriptor = runCatching { runtimeDeferred.await() }.getOrNull()
            withContext(Dispatchers.Main) {
                if (currentSource?.uri != currentSourceUri || currentHttpConfig != currentHttpConfigSnapshot) {
                    return@withContext
                }
                val updated = applyResolvedSignalDescriptors(
                    resolvedContainerDescriptor = completedContainerDescriptor,
                    resolvedRuntimeDescriptor = completedRuntimeDescriptor,
                )
                MiruLog.i(
                    "HybridPlaybackController",
                    "Completed deferred video signal probe",
                    mapOf(
                        "source_uri" to currentSourceUri,
                        "container_completed_within_budget" to probeResult.containerCompletedWithinBudget.toString(),
                        "runtime_completed_within_budget" to probeResult.runtimeCompletedWithinBudget.toString(),
                        "signal_label" to updated?.displayLabel().orEmpty(),
                        "rule_key" to _currentRenderRuleKey.value.name,
                        "active_backend" to _activeRenderBackend.value.name,
                    ),
                )
            }
        }
    }

    private fun applyResolvedSignalDescriptors(
        resolvedContainerDescriptor: VideoSignalDescriptor?,
        resolvedRuntimeDescriptor: VideoSignalDescriptor?,
    ): VideoSignalDescriptor? {
        containerSignalDescriptor = resolvedContainerDescriptor ?: containerSignalDescriptor
        runtimeSignalDescriptor = resolvedRuntimeDescriptor ?: runtimeSignalDescriptor
        val effectiveDescriptor = playbackDebugOverrides.forcedVideoSignalDescriptor
            ?: when {
                runtimeSignalDescriptor == null && containerSignalDescriptor == null -> null
                else -> mergeVideoSignalDescriptor(
                    runtimeDescriptor = runtimeSignalDescriptor ?: VideoSignalDescriptor(),
                    containerHint = containerSignalDescriptor,
                )
            }
        _currentVideoSignalDescriptor.value = effectiveDescriptor
        refreshRuntimeConfig()
        return effectiveDescriptor
    }

    private fun detachBoundVlcVideoHost(player: MediaPlayer?) {
        val targetPlayer = player ?: return
        emitHybridPlaybackLogInfo(
            "detachBoundVlcVideoHost " +
                "usingSurfaceHost=$usingSurfaceVideoHostAttach usingOutputCallbacks=$usingOutputCallbackAttach " +
                "usingDirectTexture=$usingDirectTextureAttach usingHiddenCarrier=$usingHiddenCarrierAttach " +
                "viewsAttached=${runCatching { targetPlayer.getVLCVout().areViewsAttached() }.getOrDefault(false)}",
        )
        if (usingOutputCallbackAttach) {
            activeLibVlcOutputCallbackSession?.let { session ->
                libVlcOutputCallbacksBridge.releaseOutput(session)
            }
            activeLibVlcOutputCallbackSession = null
        } else if (shouldUseVmemStreamVout()) {
            activeLibVlcVmemStreamSession?.let { session ->
                libVlcVmemStreamBridge.releaseStream(session)
            }
            activeLibVlcVmemStreamSession = null
            if (usingHiddenCarrierAttach || usingDirectTextureAttach || usingSurfaceVideoHostAttach) {
                runCatching { targetPlayer.setVideoTrackEnabled(false) }
                runCatching { targetPlayer.detachViews() }
            }
        } else if (usingDirectTextureAttach || usingSurfaceVideoHostAttach) {
            runCatching { targetPlayer.setVideoTrackEnabled(false) }
            runCatching { targetPlayer.getVLCVout().detachViews() }
        } else {
            runCatching { targetPlayer.detachViews() }
        }
        usingDirectTextureAttach = false
        usingSurfaceVideoHostAttach = false
        usingOutputCallbackAttach = false
        usingHiddenCarrierAttach = false
        (attachedVlcVideoHost as? LibVlcVmemVideoHost)?.bindLibVlcVmemStream(null, null)
        (attachedVlcVideoHost as? LibVlcVmemVideoHost)?.setLibVlcVmemStreamEnabled(false)
    }

    private fun refreshVlcVideoSurfaces(
        player: MediaPlayer?,
        reason: String,
    ) {
        val targetPlayer = player ?: return
        if (shouldUseVmemProbeVout() || shouldUseVmemStreamVout()) {
            return
        }
        syncVlcHostWindowSize(targetPlayer, attachedVlcVideoHost ?: vlcVideoHost, reason = reason)
        if (usingOutputCallbackAttach) {
            return
        }
        runCatching {
            targetPlayer.updateVideoSurfaces()
        }.onFailure { error ->
            MiruLog.w(
                "HybridPlaybackController",
                "Failed to refresh libVLC video surfaces",
                error,
                mapOf(
                    "reason" to reason,
                    "host_bound" to (vlcVideoHost != null).toString(),
                    "host_attached" to (attachedVlcVideoHost != null).toString(),
                )
            )
        }
    }

    private fun maybeStartPendingVlcPlayback(
        player: MediaPlayer,
        reason: String,
    ) {
        if (!pendingVlcPlaybackStart) {
            return
        }
        if (shouldDeferLibVlcPlaybackStartUntilHostAttach()) {
            emitHybridPlaybackLogInfo(
                "Still deferring pending libVLC playback start reason=$reason " +
                    "attachedHost=${attachedVlcVideoHost != null} " +
                    "viewsAttached=${runCatching { player.getVLCVout().areViewsAttached() }.getOrDefault(false)}",
            )
            return
        }
        pendingVlcPlaybackStart = false
        emitHybridPlaybackLogInfo(
            "Starting deferred libVLC playback reason=$reason " +
                "attachedHost=${attachedVlcVideoHost != null} " +
                "viewsAttached=${runCatching { player.getVLCVout().areViewsAttached() }.getOrDefault(false)}",
        )
        runCatching {
            player.play()
        }.onSuccess {
            emitHybridPlaybackLogInfo(
                "Started deferred libVLC playback successfully reason=$reason " +
                    "timeMs=${player.time.coerceAtLeast(0L)}",
            )
            MiruLog.i(
                "HybridPlaybackController",
                "Started deferred libVLC playback after host attachment",
                mapOf(
                    "reason" to reason,
                    "backend" to _activeRenderBackend.value.name,
                    "vout_mode" to playbackDebugOverrides.libVlcDebugConfig.voutMode.name,
                    "host_attached" to (attachedVlcVideoHost != null).toString(),
                ),
            )
        }.onFailure { error ->
            pendingVlcPlaybackStart = true
            MiruLog.w(
                "HybridPlaybackController",
                "Failed to start deferred libVLC playback after host attachment",
                error,
                mapOf(
                    "reason" to reason,
                    "backend" to _activeRenderBackend.value.name,
                    "vout_mode" to playbackDebugOverrides.libVlcDebugConfig.voutMode.name,
                    "host_attached" to (attachedVlcVideoHost != null).toString(),
                ),
            )
        }
    }

    private fun shouldDeferLibVlcPlaybackStartUntilHostAttach(): Boolean =
        !shouldUseVmemProbeVout() &&
            (
                attachedVlcVideoHost == null ||
                    (shouldUseVmemStreamVout() && activeLibVlcVmemStreamSession == null)
            )

    private fun ensureVlcHostLayoutListener(
        host: VLCVideoLayout,
        fallbackPlayer: MediaPlayer? = null,
    ) {
        if (vlcHostLayoutChangeListener != null) {
            return
        }
        vlcHostLayoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            emitHybridPlaybackLogInfo(
                "libVLC host layout changed " +
                    buildVlcHostStateAttributes(host, reason = "host_layout_change_logcat")
                        .entries
                        .joinToString(" ") { "${it.key}=${it.value}" },
            )
            MiruLog.i(
                "HybridPlaybackController",
                "libVLC video host layout changed",
                buildVlcHostStateAttributes(host, reason = "host_layout_change")
            )
            if (attachedVlcVideoHost == null) {
                resolveVlcPlayerForHostCallbacks(fallbackPlayer)?.let { bindExistingVlcHost(it) }
            } else {
                refreshVlcVideoSurfaces(
                    resolveVlcPlayerForHostCallbacks(fallbackPlayer),
                    reason = "host_layout_change",
                )
            }
        }
        host.addOnLayoutChangeListener(vlcHostLayoutChangeListener)
        val surfaceVideoHost = host as? LibVlcSurfaceVideoHost
        if (surfaceVideoHost != null && vlcSurfaceHostReadyListener == null) {
            vlcSurfaceHostReadyListener = { ready ->
                emitHybridPlaybackLogInfo(
                    "libVLC surface host readiness changed ready=$ready " +
                        buildVlcHostStateAttributes(
                            host = host,
                            reason = if (ready) "surface_host_ready_logcat" else "surface_host_not_ready_logcat",
                        ).entries.joinToString(" ") { "${it.key}=${it.value}" },
                )
                MiruLog.i(
                    "HybridPlaybackController",
                    "libVLC surface host readiness changed",
                    buildVlcHostStateAttributes(
                        host = host,
                        reason = if (ready) "surface_host_ready" else "surface_host_not_ready",
                    ) + ("ready" to ready.toString()),
                )
                if (ready) {
                    if (attachedVlcVideoHost == null) {
                        resolveVlcPlayerForHostCallbacks(fallbackPlayer)?.let { bindExistingVlcHost(it) }
                    } else if (usingSurfaceVideoHostAttach) {
                        refreshVlcVideoSurfaces(
                            resolveVlcPlayerForHostCallbacks(fallbackPlayer),
                            reason = "surface_host_ready",
                        )
                    }
                }
            }
            surfaceVideoHost.setOnLibVlcVideoSurfaceReadyChanged(vlcSurfaceHostReadyListener)
        }
        val outputCallbackHost = host as? LibVlcOutputCallbackVideoHost
        if (outputCallbackHost != null && vlcOutputCallbackHostReadyListener == null) {
            vlcOutputCallbackHostReadyListener = { ready ->
                emitHybridPlaybackLogInfo(
                    "libVLC output callbacks host readiness changed ready=$ready " +
                        buildVlcHostStateAttributes(
                            host = host,
                            reason = if (ready) "output_callbacks_ready_logcat" else "output_callbacks_not_ready_logcat",
                        ).entries.joinToString(" ") { "${it.key}=${it.value}" },
                )
                MiruLog.i(
                    "HybridPlaybackController",
                    "libVLC output callbacks host readiness changed",
                    buildVlcHostStateAttributes(
                        host = host,
                        reason = if (ready) "output_callbacks_ready" else "output_callbacks_not_ready",
                    ) + ("ready" to ready.toString()),
                )
                if (ready) {
                    if (attachedVlcVideoHost == null) {
                        resolveVlcPlayerForHostCallbacks(fallbackPlayer)?.let { bindExistingVlcHost(it) }
                    } else if (usingOutputCallbackAttach) {
                        syncVlcHostWindowSize(
                            resolveVlcPlayerForHostCallbacks(fallbackPlayer),
                            host,
                            reason = "output_callbacks_ready",
                        )
                    }
                }
            }
            outputCallbackHost.setOnLibVlcOutputCallbackReadyChanged(vlcOutputCallbackHostReadyListener)
        }
    }

    private fun resolveVlcPlayerForHostCallbacks(
        fallbackPlayer: MediaPlayer?,
    ): MediaPlayer? = vlcMediaPlayer ?: fallbackPlayer

    private fun removeVlcHostLayoutListener() {
        val host = attachedVlcVideoHost ?: vlcVideoHost
        val listener = vlcHostLayoutChangeListener ?: return
        runCatching { host?.removeOnLayoutChangeListener(listener) }
        vlcHostLayoutChangeListener = null
        val surfaceReadyListener = vlcSurfaceHostReadyListener
        if (surfaceReadyListener != null) {
            runCatching { (host as? LibVlcSurfaceVideoHost)?.setOnLibVlcVideoSurfaceReadyChanged(null) }
            vlcSurfaceHostReadyListener = null
        }
        val outputReadyListener = vlcOutputCallbackHostReadyListener
        if (outputReadyListener != null) {
            runCatching { (host as? LibVlcOutputCallbackVideoHost)?.setOnLibVlcOutputCallbackReadyChanged(null) }
            vlcOutputCallbackHostReadyListener = null
        }
    }

    private fun syncVlcHostWindowSize(
        player: MediaPlayer?,
        host: VLCVideoLayout?,
        reason: String,
    ) {
        val targetPlayer = player ?: return
        val targetHost = host ?: return
        val outputCallbackHost = if (usingOutputCallbackAttach || shouldUseOutputCallbackAttach()) {
            targetHost as? LibVlcOutputCallbackVideoHost
        } else {
            null
        }
        val directTextureView = if (shouldUseTextureViewAttach()) {
            (targetHost as? LibVlcDirectVideoHost)?.libVlcDirectVideoTextureView()
        } else {
            null
        }
        val surfaceVideoHost = if (usingSurfaceVideoHostAttach || shouldUseSurfaceVideoHostAttach()) {
            targetHost as? LibVlcSurfaceVideoHost
        } else {
            null
        }
        val width = when {
            outputCallbackHost != null -> {
                outputCallbackHost.libVlcOutputCallbackWidth().takeIf { it > 0 } ?: targetHost.width
            }
            else -> surfaceVideoHost?.libVlcVideoSurfaceWidth()?.takeIf { it > 0 } ?: targetHost.width
        }
        val height = when {
            outputCallbackHost != null -> {
                outputCallbackHost.libVlcOutputCallbackHeight().takeIf { it > 0 } ?: targetHost.height
            }
            else -> surfaceVideoHost?.libVlcVideoSurfaceHeight()?.takeIf { it > 0 } ?: targetHost.height
        }
        if (width <= 0 || height <= 0) {
            MiruLog.w(
                "HybridPlaybackController",
                "Skipping libVLC window size sync because host size is invalid",
                null,
                mapOf(
                    "reason" to reason,
                    "width" to width.toString(),
                    "height" to height.toString(),
                )
            )
            return
        }
        if (outputCallbackHost != null) {
            activeLibVlcOutputCallbackSession?.let { session ->
                libVlcOutputCallbacksBridge.updateOutputWindow(
                    session = session,
                    width = width,
                    height = height,
                )
            }
            runCatching {
                targetPlayer.getVLCVout().setWindowSize(width, height)
            }.onFailure { error ->
                MiruLog.w(
                    "HybridPlaybackController",
                    "Failed to sync libVLC output callbacks window size",
                    error,
                    mapOf(
                        "reason" to reason,
                        "width" to width.toString(),
                        "height" to height.toString(),
                    ),
                )
            }
            return
        }
        runCatching {
            targetPlayer.getVLCVout().setWindowSize(width, height)
        }.onFailure { error ->
            MiruLog.w(
                "HybridPlaybackController",
                "Failed to sync libVLC host window size",
                error,
                mapOf(
                    "reason" to reason,
                    "width" to width.toString(),
                    "height" to height.toString(),
                )
            )
        }
    }

    private fun isVlcHostReadyForAttach(host: VLCVideoLayout): Boolean {
        if (host.width <= 0 || host.height <= 0 || !host.isAttachedToWindow || !host.isLaidOut) {
            return false
        }
        if (shouldUseVmemStreamVout()) {
            val vmemView = (host as? LibVlcVmemVideoHost)?.libVlcVmemVideoView() ?: return false
            return vmemView.width > 0 &&
                vmemView.height > 0 &&
                vmemView.isAttachedToWindow &&
                vmemView.isLaidOut
        }
        val outputCallbackHost = if (shouldUseOutputCallbackAttach()) {
            host as? LibVlcOutputCallbackVideoHost
        } else {
            null
        }
        if (outputCallbackHost != null) {
            val surface = outputCallbackHost.libVlcOutputCallbackSurface()
            val width = outputCallbackHost.libVlcOutputCallbackWidth()
            val height = outputCallbackHost.libVlcOutputCallbackHeight()
            return isLibVlcOutputCallbackAttachReadyForHost(
                surfacePresent = surface != null,
                hostWidth = width,
                hostHeight = height,
            )
        }
        val surfaceVideoHost = if (shouldUseSurfaceVideoHostAttach()) {
            host as? LibVlcSurfaceVideoHost
        } else {
            null
        }
        if (surfaceVideoHost != null) {
            val surface = surfaceVideoHost.libVlcVideoSurface()
            val surfaceTexture = surfaceVideoHost.libVlcVideoSurfaceTexture()
            val surfaceWidth = surfaceVideoHost.libVlcVideoSurfaceWidth()
            val surfaceHeight = surfaceVideoHost.libVlcVideoSurfaceHeight()
            return (surface != null || surfaceTexture != null) &&
                surfaceWidth >= 4 &&
                surfaceHeight >= 4
        }
        val playerSurfaceFrame = findVlcPlayerSurfaceFrame(host)
        if (playerSurfaceFrame != null && (playerSurfaceFrame.width <= 0 || playerSurfaceFrame.height <= 0)) {
            return false
        }
        // Stock libVLC attach paths inflate and bind their SurfaceView/TextureView
        // inside player.attachViews(...), so checking nested helper surfaces here can
        // deadlock default playback before libVLC ever gets a chance to attach.
        return true
    }

    private fun buildVlcHostStateAttributes(
        host: VLCVideoLayout,
        reason: String,
    ): Map<String, String> {
        val hostViews = inspectVlcHostViews(host)
        val surfaceView = hostViews.primarySurfaceView
        val textureView = hostViews.primaryTextureView
        val playerSurfaceFrame = findVlcPlayerSurfaceFrame(host)
        val surfaceVideoHost = host as? LibVlcSurfaceVideoHost
        val outputCallbackHost = host as? LibVlcOutputCallbackVideoHost
        val directTextureCarrier = (host as? LibVlcDirectVideoHost)?.libVlcDirectVideoTextureView()
        val surface = runCatching { surfaceView?.holder?.surface }.getOrNull()
        val surfaceVideoSurface = surfaceVideoHost?.libVlcVideoSurface()
        val surfaceVideoTexture = surfaceVideoHost?.libVlcVideoSurfaceTexture()
        return buildMap {
            put("reason", reason)
            put("host_width", host.width.toString())
            put("host_height", host.height.toString())
            put("host_attached_to_window", host.isAttachedToWindow.toString())
            put("host_is_laid_out", host.isLaidOut.toString())
            put("player_surface_frame_present", (playerSurfaceFrame != null).toString())
            put("player_surface_frame_width", (playerSurfaceFrame?.width ?: 0).toString())
            put("player_surface_frame_height", (playerSurfaceFrame?.height ?: 0).toString())
            put("surface_host_enabled", shouldUseSurfaceVideoHostAttach().toString())
            put("output_callbacks_enabled", shouldUseOutputCallbackAttach().toString())
            put("surface_host_surface_present", (surfaceVideoSurface != null).toString())
            put("surface_host_surface_texture_present", (surfaceVideoTexture != null).toString())
            put(
                "surface_host_surface_valid",
                runCatching { surfaceVideoSurface?.isValid?.toString() ?: "false" }.getOrDefault("false"),
            )
            put("surface_host_width", (surfaceVideoHost?.libVlcVideoSurfaceWidth() ?: 0).toString())
            put("surface_host_height", (surfaceVideoHost?.libVlcVideoSurfaceHeight() ?: 0).toString())
            put("output_callbacks_surface_present", (outputCallbackHost?.libVlcOutputCallbackSurface() != null).toString())
            put("output_callbacks_width", (outputCallbackHost?.libVlcOutputCallbackWidth() ?: 0).toString())
            put("output_callbacks_height", (outputCallbackHost?.libVlcOutputCallbackHeight() ?: 0).toString())
            put(
                "output_callbacks_attach_ready",
                outputCallbackHost?.let {
                    isLibVlcOutputCallbackAttachReadyForHost(
                        surfacePresent = it.libVlcOutputCallbackSurface() != null,
                        hostWidth = it.libVlcOutputCallbackWidth(),
                        hostHeight = it.libVlcOutputCallbackHeight(),
                    ).toString()
                } ?: false.toString(),
            )
            put("vmem_stream_enabled", shouldUseVmemStreamVout().toString())
            put("direct_texture_carrier_present", (directTextureCarrier != null).toString())
            if (directTextureCarrier != null) {
                put("direct_texture_carrier_width", directTextureCarrier.width.toString())
                put("direct_texture_carrier_height", directTextureCarrier.height.toString())
                put("direct_texture_carrier_available", directTextureCarrier.isAvailable.toString())
                put(
                    "direct_texture_carrier_surface_present",
                    (directTextureCarrier.surfaceTexture != null).toString(),
                )
            }
            put("surface_present", (surfaceView != null).toString())
            put("surface_count", hostViews.surfaceViews.size.toString())
            put(
                "surface_sizes",
                hostViews.surfaceViews.joinToString(separator = ";") { "${it.width}x${it.height}" },
            )
            if (surfaceView != null) {
                put("surface_width", surfaceView.width.toString())
                put("surface_height", surfaceView.height.toString())
            }
            if (surface != null) {
                put("surface_valid", surface.isValid.toString())
            }
            put("texture_present", (textureView != null).toString())
            put("texture_count", hostViews.textureViews.size.toString())
            put(
                "texture_sizes",
                hostViews.textureViews.joinToString(separator = ";") { "${it.width}x${it.height}" },
            )
            if (textureView != null) {
                put("texture_width", textureView.width.toString())
                put("texture_height", textureView.height.toString())
                put("texture_available", textureView.isAvailable.toString())
                put("texture_surface_present", (textureView.surfaceTexture != null).toString())
            }
        }
    }

    private fun isLibVlcOutputCallbackAttachReadyForHost(
        surfacePresent: Boolean,
        hostWidth: Int,
        hostHeight: Int,
    ): Boolean = surfacePresent && hostWidth >= 4 && hostHeight >= 4

    private fun findVlcSurfaceView(host: VLCVideoLayout): SurfaceView? =
        inspectVlcHostViews(host).primarySurfaceView

    private fun findVlcTextureView(host: VLCVideoLayout): TextureView? =
        inspectVlcHostViews(host).primaryTextureView

    private fun findVlcPlayerSurfaceFrame(host: VLCVideoLayout): FrameLayout? =
        runCatching { host.findViewById<FrameLayout>(org.videolan.R.id.player_surface_frame) }
            .getOrNull()

    private fun inspectVlcHostViews(host: VLCVideoLayout): VlcHostViews {
        val surfaceViews = mutableListOf<SurfaceView>()
        val textureViews = mutableListOf<TextureView>()
        collectVlcHostViews(host, surfaceViews, textureViews)
        val surfaceHostView = (host as? LibVlcSurfaceVideoHost)?.libVlcVideoSurfaceView()
        if (surfaceHostView is SurfaceView && !shouldUseSurfaceVideoHostAttach()) {
            surfaceViews.removeAll { it === surfaceHostView }
        }
        val directTextureView = if (shouldUseTextureViewAttach()) {
            (host as? LibVlcDirectVideoHost)?.libVlcDirectVideoTextureView()
        } else {
            null
        }
        if (directTextureView != null && !shouldUseHiddenTextureCarrier()) {
            textureViews.removeAll { it === directTextureView }
        }
        return VlcHostViews(
            surfaceViews = surfaceViews,
            textureViews = textureViews,
        )
    }

    private fun collectVlcHostViews(
        view: View,
        surfaceViews: MutableList<SurfaceView>,
        textureViews: MutableList<TextureView>,
    ) {
        when (view) {
            is SurfaceView -> surfaceViews += view
            is TextureView -> textureViews += view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectVlcHostViews(view.getChildAt(index), surfaceViews, textureViews)
            }
        }
    }

    private fun logVlcVideoOutputState(
        player: MediaPlayer?,
        reason: String,
    ) {
        val targetPlayer = player ?: return
        val videoTrack = runCatching { targetPlayer.currentVideoTrackCompat() }.getOrNull()
        val viewsAttached = runCatching { targetPlayer.getVLCVout().areViewsAttached() }.getOrDefault(false)
        MiruLog.i(
            "HybridPlaybackController",
            "libVLC video output state",
            mapOf(
                "reason" to reason,
                "views_attached" to viewsAttached.toString(),
                "video_track_count" to targetPlayer.videoTracksCountCompat().toString(),
                "current_video_track_id" to (videoTrack?.id?.toString() ?: ""),
                "current_video_width" to (videoTrack?.width?.toString() ?: ""),
                "current_video_height" to (videoTrack?.height?.toString() ?: ""),
                "current_video_projection" to (videoTrack?.projection?.toString() ?: ""),
                "current_video_frame_rate_num" to (videoTrack?.frameRateNum?.toString() ?: ""),
                "current_video_frame_rate_den" to (videoTrack?.frameRateDen?.toString() ?: ""),
                "video_scale" to targetPlayer.videoScale.name,
            )
        )
    }

    private fun logVlcRenderEvidence(
        player: MediaPlayer?,
        reason: String,
    ) {
        val targetPlayer = player ?: return
        val evidence = readLibVlcRenderEvidence(targetPlayer) ?: return
        maybeCapturePendingLibVlcNativeSnapshot(
            player = targetPlayer,
            evidence = evidence,
            reason = reason,
        )
        if (evidence.hasDisplayedFrames) {
            if (hasLoggedVlcDisplayedFrames) return
            hasLoggedVlcDisplayedFrames = true
            MiruLog.i(
                "HybridPlaybackController",
                "libVLC displayed video frames",
                mapOf(
                    "reason" to reason,
                    "decoded_video" to evidence.decodedVideo.toString(),
                    "displayed_pictures" to evidence.displayedPictures.toString(),
                    "lost_pictures" to evidence.lostPictures.toString(),
                    "current_time_ms" to (targetPlayer.time.coerceAtLeast(0L)).toString(),
                )
            )
            return
        }
        if (evidence.decodedVideo > 0 && !hasLoggedVlcDecodedWithoutDisplay) {
            hasLoggedVlcDecodedWithoutDisplay = true
            MiruLog.w(
                "HybridPlaybackController",
                "libVLC decoded video without displayed frames yet",
                null,
                mapOf(
                    "reason" to reason,
                    "decoded_video" to evidence.decodedVideo.toString(),
                    "displayed_pictures" to evidence.displayedPictures.toString(),
                    "lost_pictures" to evidence.lostPictures.toString(),
                    "current_time_ms" to (targetPlayer.time.coerceAtLeast(0L)).toString(),
                )
            )
        }
    }

    private fun maybeAttachLibVlcVmemStream(
        player: MediaPlayer,
        reason: String = "unspecified",
    ) {
        if (!shouldUseVmemStreamVout()) {
            return
        }
        val existingSession = activeLibVlcVmemStreamSession
        if (existingSession != null) {
            emitHybridPlaybackLogInfo(
                "Skipping libVLC VMEM stream attach because a session is already active " +
                    "reason=$reason playerInstance=${existingSession.playerInstance}",
            )
            (vlcVideoHost as? LibVlcVmemVideoHost)?.bindLibVlcVmemStream(
                bridge = libVlcVmemStreamBridge,
                session = existingSession,
            )
            return
        }
        val host = attachedVlcVideoHost ?: vlcVideoHost
        if (host == null || !isVlcHostReadyForAttach(host)) {
            emitHybridPlaybackLogInfo(
                "Deferring libVLC VMEM stream attach until host is ready reason=$reason " +
                    "attachedHost=${attachedVlcVideoHost != null}",
            )
            return
        }
        val playerInstance = resolveNativeVlcObjectInstance(
            directInstance = 0L,
            holder = player,
        )
        if (playerInstance == 0L) {
            MiruLog.w(
                "HybridPlaybackController",
                "Failed to resolve libVLC player instance for VMEM stream attach",
                attributes = mapOf("vout_mode" to playbackDebugOverrides.libVlcDebugConfig.voutMode.name),
            )
            return
        }
        val preferredOutputChroma = playbackDebugOverrides.libVlcDebugConfig.displayChroma
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.length == 4 }
        val createResult = libVlcVmemStreamBridge.createStream(preferredOutputChroma)
        if (!createResult.success || createResult.session == null) {
            MiruLog.w(
                "HybridPlaybackController",
                "Failed to create libVLC VMEM stream session",
                attributes = mapOf(
                    "preferred_output_chroma" to (preferredOutputChroma ?: "native"),
                    "result_code" to createResult.resultCode.toString(),
                ),
            )
            return
        }
        val attachResult = libVlcVmemStreamBridge.attachStream(
            playerInstance = playerInstance,
            session = createResult.session,
            windowWidth = host.width,
            windowHeight = host.height,
        )
        if (!attachResult.success || attachResult.session == null) {
            libVlcVmemStreamBridge.releaseStream(createResult.session)
            MiruLog.w(
                "HybridPlaybackController",
                "Failed to attach libVLC VMEM stream session",
                attributes = mapOf(
                    "preferred_output_chroma" to (preferredOutputChroma ?: "native"),
                    "result_code" to attachResult.resultCode.toString(),
                ),
            )
            return
        }
        activeLibVlcVmemStreamSession = attachResult.session
        (host as? LibVlcVmemVideoHost)?.bindLibVlcVmemStream(
            bridge = libVlcVmemStreamBridge,
            session = attachResult.session,
        )
        MiruLog.i(
            "HybridPlaybackController",
            "Attached libVLC VMEM stream session",
            mapOf(
                "preferred_output_chroma" to (preferredOutputChroma ?: "native"),
                "player_instance" to playerInstance.toString(),
                "reason" to reason,
            ),
        )
    }

    private fun maybeArmLibVlcFrameProbe(
        player: MediaPlayer,
    ): LibVlcFrameProbeSession? {
        if (!shouldUseVmemProbeVout()) {
            return null
        }
        val label = playbackDebugOverrides.consumePendingLibVlcNativeSnapshotLabel() ?: return null
        val directInstance = 0L
        val playerInstance = resolveNativeVlcObjectInstance(
            directInstance = directInstance,
            holder = player,
        )
        val outputDir = File(context.filesDir, "MiruPlayLibVlcCaptures")
        val host = attachedVlcVideoHost ?: vlcVideoHost
        val windowWidth = host?.width ?: 0
        val windowHeight = host?.height ?: 0
        val preferredOutputChroma = resolvePreferredLibVlcProbeOutputChroma(
            signalDescriptor = _currentVideoSignalDescriptor.value,
            debugConfig = playbackDebugOverrides.libVlcDebugConfig,
        )
        emitHybridPlaybackLogInfo(
            "Arming libVLC VMEM frame probe " +
                "label=$label playerInstance=$playerInstance directInstance=$directInstance " +
                "window=${windowWidth}x${windowHeight} " +
                "preferredOutputChroma=${preferredOutputChroma.orEmpty()} outputDir=${outputDir.absolutePath}",
        )
        val armResult = libVlcFrameProbeBridge.armFirstFrameProbe(
            playerInstance = playerInstance,
            outputDir = outputDir,
            label = label,
            preferredOutputChroma = preferredOutputChroma,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
        )
        emitHybridPlaybackLogInfo(
            "libVLC VMEM frame probe arm result " +
                "label=$label success=${armResult.success} resultCode=${armResult.resultCode} " +
                "sessionPresent=${armResult.session != null}",
        )
        if (!armResult.success) {
            MiruLog.w(
                "HybridPlaybackController",
                "Failed to arm libVLC VMEM frame probe",
                attributes = mapOf(
                    "label" to label,
                    "player_instance" to playerInstance.toString(),
                    "direct_instance" to directInstance.toString(),
                    "preferred_output_chroma" to preferredOutputChroma.orEmpty(),
                    "result_code" to armResult.resultCode.toString(),
                ),
            )
        }
        return armResult.session
    }

    private fun awaitLibVlcFrameProbe(
        session: LibVlcFrameProbeSession,
    ) {
        pendingLibVlcFrameProbeJob?.cancel()
        pendingLibVlcFrameProbeJob = controllerScope.launch(Dispatchers.IO) {
            emitHybridPlaybackLogInfo(
                "Awaiting libVLC VMEM probe label=${session.captureLabel} metadata=${session.metadataFile.absolutePath}",
            )
            val result = libVlcFrameProbeBridge.awaitFirstFrameProbe(session)
            if (result.success) {
                emitHybridPlaybackLogInfo(
                    "libVLC VMEM probe completed label=${session.captureLabel} resultCode=${result.resultCode} " +
                        "metadata=${result.metadataFile.absolutePath}",
                )
                MiruLog.i(
                    "HybridPlaybackController",
                    "Captured libVLC VMEM probe frame",
                    mapOf(
                        "label" to session.captureLabel,
                        "metadata_path" to result.metadataFile.absolutePath,
                        "preview_path" to result.previewFile.absolutePath,
                        "luma_path" to result.lumaFile.absolutePath,
                        "raw_path" to result.rawFrameFile.absolutePath,
                    ),
                )
            } else {
                emitHybridPlaybackLogInfo(
                    "libVLC VMEM probe failed label=${session.captureLabel} resultCode=${result.resultCode} " +
                        "metadata=${result.metadataFile.absolutePath}",
                )
                MiruLog.w(
                    "HybridPlaybackController",
                    "libVLC VMEM probe timed out before producing a frame",
                    attributes = mapOf(
                        "label" to session.captureLabel,
                        "result_code" to result.resultCode.toString(),
                        "metadata_path" to result.metadataFile.absolutePath,
                        "preview_path" to result.previewFile.absolutePath,
                        "luma_path" to result.lumaFile.absolutePath,
                        "raw_path" to result.rawFrameFile.absolutePath,
                    ),
                )
            }
        }
    }

    private fun maybeCapturePendingLibVlcNativeSnapshot(
        player: MediaPlayer,
        evidence: LibVlcRenderEvidence,
        reason: String,
    ) {
        val label = playbackDebugOverrides.peekPendingLibVlcNativeSnapshotLabel() ?: return
        val voutMode = playbackDebugOverrides.libVlcDebugConfig.voutMode
        val vmemFrameReady = if (voutMode == LibVlcVoutMode.VMEM_STREAM) {
            activeLibVlcVmemStreamSession
                ?.let { session -> libVlcVmemStreamBridge.readState(session) }
                ?.let { it.configured && it.frameVersion > 0L }
                ?: false
        } else {
            true
        }
        if (
            !shouldAttemptLibVlcNativeSnapshot(
                evidence = evidence,
                voutMode = voutMode,
                isPlaying = player.isPlaying,
                currentTimeMs = player.time.coerceAtLeast(0L),
                vmemFrameReady = vmemFrameReady,
            )
        ) {
            return
        }
        if (pendingLibVlcNativeSnapshotJob?.isActive == true) {
            return
        }
        val directInstance = 0L
        val playerInstance = resolveNativeVlcObjectInstance(
            directInstance = directInstance,
            holder = player,
        )
        if (playerInstance == 0L) {
            MiruLog.w(
                "HybridPlaybackController",
                "libVLC native snapshot skipped because player instance resolved to zero",
                attributes = mapOf(
                    "label" to label,
                    "reason" to reason,
                    "direct_instance" to directInstance.toString(),
                ),
            )
            return
        }
        val videoTrack = runCatching { player.currentVideoTrackCompat() }.getOrNull()
        val outputFile = File(
            File(context.filesDir, "MiruPlayLibVlcCaptures"),
            "${sanitizeCaptureLabel(label)}_native.png",
        )
        pendingLibVlcNativeSnapshotJob?.cancel()
        pendingLibVlcNativeSnapshotJob = controllerScope.launch(Dispatchers.IO) {
            val result = libVlcSnapshotBridge.takeSnapshot(
                playerInstance = playerInstance,
                outputFile = outputFile,
                width = videoTrack?.width ?: 0,
                height = videoTrack?.height ?: 0,
            )
            if (result.success) {
                MiruLog.i(
                    "HybridPlaybackController",
                    "Captured libVLC native snapshot",
                    mapOf(
                        "label" to label,
                        "reason" to reason,
                        "path" to result.outputFile.absolutePath,
                        "result_code" to result.resultCode.toString(),
                        "width" to (videoTrack?.width?.toString() ?: "0"),
                        "height" to (videoTrack?.height?.toString() ?: "0"),
                    ),
                )
                playbackDebugOverrides.clearPendingLibVlcNativeSnapshotLabel(label)
            } else {
                MiruLog.w(
                    "HybridPlaybackController",
                    "libVLC native snapshot failed",
                    attributes = mapOf(
                        "label" to label,
                        "reason" to reason,
                        "path" to result.outputFile.absolutePath,
                        "result_code" to result.resultCode.toString(),
                        "width" to (videoTrack?.width?.toString() ?: "0"),
                        "height" to (videoTrack?.height?.toString() ?: "0"),
                    ),
                )
                playbackDebugOverrides.clearPendingLibVlcNativeSnapshotLabel(label)
            }
        }
    }

    private fun scheduleFallbackLibVlcNativeSnapshot(
        player: MediaPlayer?,
        reason: String,
    ) {
        val targetPlayer = player ?: return
        if (playbackDebugOverrides.libVlcDebugConfig.voutMode != LibVlcVoutMode.ANDROID_DISPLAY) return
        if (playbackDebugOverrides.peekPendingLibVlcNativeSnapshotLabel() == null) return
        if (pendingLibVlcNativeSnapshotJob?.isActive == true || pendingLibVlcNativeSnapshotKickJob?.isActive == true) {
            return
        }
        pendingLibVlcNativeSnapshotKickJob = controllerScope.launch {
            kotlinx.coroutines.delay(750L)
            val label = playbackDebugOverrides.peekPendingLibVlcNativeSnapshotLabel() ?: return@launch
            if (pendingLibVlcNativeSnapshotJob?.isActive == true) return@launch
            maybeCapturePendingLibVlcNativeSnapshot(
                player = targetPlayer,
                evidence = LibVlcRenderEvidence(
                    decodedVideo = 1,
                    displayedPictures = 0,
                    lostPictures = 0,
                    hasDisplayedFrames = false,
                ),
                reason = reason,
            )
        }
    }
}

private fun emitHybridPlaybackLogInfo(message: String) {
    runCatching { Log.i("HybridPlaybackController", message) }
}

private fun emitHybridPlaybackLogError(
    message: String,
    error: Throwable? = null,
) {
    runCatching {
        if (error != null) {
            Log.e("HybridPlaybackController", message, error)
        } else {
            Log.e("HybridPlaybackController", message)
        }
    }
}

private data class VlcHostViews(
    val surfaceViews: List<SurfaceView>,
    val textureViews: List<TextureView>,
) {
    val primarySurfaceView: SurfaceView?
        get() = surfaceViews.maxByOrNull { it.width * it.height }

    val primaryTextureView: TextureView?
        get() = textureViews.maxByOrNull { it.width * it.height }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun sanitizeCaptureLabel(label: String): String =
    label.replace(Regex("[^A-Za-z0-9._-]"), "_")

internal fun resolveNativeVlcObjectInstance(
    directInstance: Long,
    holder: Any,
): Long {
    if (directInstance != 0L) {
        return directInstance
    }
    val reflectedPublicInstance = runCatching {
        val method = holder.javaClass.getMethod("getInstance")
        (method.invoke(holder) as? Number)?.toLong()
    }.getOrNull()
    if (reflectedPublicInstance != null && reflectedPublicInstance != 0L) {
        return reflectedPublicInstance
    }
    var currentClass: Class<*>? = holder.javaClass
    while (currentClass != null) {
        val reflectedInstance = runCatching {
            val field = currentClass.getDeclaredField("mInstance")
            field.isAccessible = true
            field.getLong(holder)
        }.getOrNull()
        if (reflectedInstance != null) {
            return reflectedInstance
        }
        currentClass = currentClass.superclass
    }
    return 0L
}

internal fun refreshVlcVideoSurfacesForTest(player: MediaPlayer?) {
    player ?: return
    runCatching { player.updateVideoSurfaces() }
}

internal data class LibVlcRenderEvidence(
    val decodedVideo: Int,
    val displayedPictures: Int,
    val lostPictures: Int,
    val hasDisplayedFrames: Boolean,
)

internal fun resolveLibVlcRenderEvidenceForTest(
    stats: IMedia.Stats?,
): LibVlcRenderEvidence? = resolveLibVlcRenderEvidence(stats)

internal fun shouldAttemptLibVlcNativeSnapshotForTest(
    evidence: LibVlcRenderEvidence,
    voutMode: LibVlcVoutMode,
    isPlaying: Boolean,
    currentTimeMs: Long,
    vmemFrameReady: Boolean = true,
): Boolean = shouldAttemptLibVlcNativeSnapshot(
    evidence = evidence,
    voutMode = voutMode,
    isPlaying = isPlaying,
    currentTimeMs = currentTimeMs,
    vmemFrameReady = vmemFrameReady,
)

private fun readLibVlcRenderEvidence(
    player: MediaPlayer,
): LibVlcRenderEvidence? {
    val media = runCatching { player.media }.getOrNull() ?: return null
    return try {
        resolveLibVlcRenderEvidence(media.stats)
    } finally {
        runCatching { media.release() }
    }
}

internal fun buildLibVlcOptionsForTest(
    ruleSet: ToneMappingRuleSet,
    signalDescriptor: VideoSignalDescriptor?,
    debugConfig: LibVlcDebugConfig = LibVlcDebugConfig(),
): List<String> = buildLibVlcOptionsInternal(ruleSet, signalDescriptor, debugConfig)

internal fun applyLibVlcMediaOptionsForTest(
    media: Media,
    ruleSet: ToneMappingRuleSet,
    debugConfig: LibVlcDebugConfig = LibVlcDebugConfig(),
) {
    applyLibVlcMediaOptionsInternal(media, ruleSet, mutableListOf(), debugConfig)
}

private fun buildLibVlcOptionsInternal(
    ruleSet: ToneMappingRuleSet,
    signalDescriptor: VideoSignalDescriptor?,
    debugConfig: LibVlcDebugConfig = LibVlcDebugConfig(),
): List<String> {
    val options = mutableListOf(
        "--verbose=2",
        "--aout=opensles",
        "--avcodec-skiploopfilter=4",
    )
    when (debugConfig.voutMode) {
        LibVlcVoutMode.DIRECT_TEXTURE -> options += "--vout=gles2,none"
        LibVlcVoutMode.OUTPUT_CALLBACKS -> options += "--vout=android_display,none"
        LibVlcVoutMode.ANDROID_DISPLAY -> options += "--vout=android_display,none"
        else -> Unit
    }
    debugConfig.displayChroma?.let { options += "--android-display-chroma=$it" }
    if (shouldUseStockAndroidDisplayStartupOptions(debugConfig)) {
        return options
    }
    options += "--target-prim=${targetPrimariesValue(ruleSet, signalDescriptor, debugConfig)}"
    options += "--target-trc=${targetTransferValue(ruleSet, signalDescriptor, debugConfig)}"
    libVlcToneMappingFunctionValue(ruleSet, signalDescriptor, debugConfig)?.let { functionValue ->
        options += "--gl-tone-mapping-function=$functionValue"
    }
    if (shouldApplyLibVlcToneMappingParams(ruleSet, signalDescriptor, debugConfig)) {
        options += "--gl-tone-mapping-param=${toneMappingParamValue(ruleSet, signalDescriptor)}"
    }
    return options
}

private fun applyLibVlcMediaOptionsInternal(
    media: Media,
    ruleSet: ToneMappingRuleSet,
    appliedOptions: MutableList<String>,
    debugConfig: LibVlcDebugConfig = LibVlcDebugConfig(),
) {
    val usesVmemCallbacks =
        debugConfig.voutMode == LibVlcVoutMode.VMEM_PROBE ||
            debugConfig.voutMode == LibVlcVoutMode.VMEM_STREAM
    if (usesVmemCallbacks) {
        media.addOption(":vout=vmem")
        appliedOptions += ":vout=vmem"
        media.addOption(":dec-dev=${LibVlcVmemStreamBridge.DEFAULT_DECODER_DEVICE}")
        appliedOptions += ":dec-dev=${LibVlcVmemStreamBridge.DEFAULT_DECODER_DEVICE}"
    }
    when {
        usesVmemCallbacks -> {
            media.setHWDecoderEnabled(false, false)
            appliedOptions += "setHWDecoderEnabled(false,false)"
            media.addOption(":codec=avcodec")
            media.addOption(":avcodec-hw=none")
            appliedOptions += ":codec=avcodec"
            appliedOptions += ":avcodec-hw=none"
        }
        debugConfig.hwMode == LibVlcHardwareAccelerationMode.DISABLED -> {
            media.setHWDecoderEnabled(false, false)
            appliedOptions += "setHWDecoderEnabled(false,false)"
            media.addOption(":codec=avcodec")
            media.addOption(":avcodec-hw=none")
            appliedOptions += ":codec=avcodec"
            appliedOptions += ":avcodec-hw=none"
        }
        debugConfig.hwMode == LibVlcHardwareAccelerationMode.DECODING_ONLY -> {
            media.setHWDecoderEnabled(true, true)
            appliedOptions += "setHWDecoderEnabled(true,true)"
            media.addOption(":no-mediacodec-dr")
            media.addOption(":no-omxil-dr")
            appliedOptions += ":no-mediacodec-dr"
            appliedOptions += ":no-omxil-dr"
        }
        else -> {
            media.setHWDecoderEnabled(true, true)
            appliedOptions += "setHWDecoderEnabled(true,true)"
        }
    }
    if (ruleSet.enabled) {
        media.addOption(":file-caching=1000")
        appliedOptions += ":file-caching=1000"
    } else {
        media.addOption(":file-caching=300")
        appliedOptions += ":file-caching=300"
    }
}

private fun libVlcToneMappingFunctionValue(
    ruleSet: ToneMappingRuleSet,
    signalDescriptor: VideoSignalDescriptor?,
    debugConfig: LibVlcDebugConfig,
): Int? =
    if (!shouldApplyLibVlcToneMappingParams(ruleSet, signalDescriptor, debugConfig)) {
        null
    } else {
        when (ruleSet.curvePreset) {
            ToneMappingCurvePreset.PASSTHROUGH -> null
            ToneMappingCurvePreset.MOBIUS -> 4
            ToneMappingCurvePreset.REINHARD -> 3
        }
    }

private fun toneMappingParamValue(
    ruleSet: ToneMappingRuleSet,
    signalDescriptor: VideoSignalDescriptor?,
): Float {
    val base = when (ruleSet.curvePreset) {
        ToneMappingCurvePreset.PASSTHROUGH -> 0f
        ToneMappingCurvePreset.MOBIUS -> 0.18f + (ruleSet.highlightCompression / 200f)
        ToneMappingCurvePreset.REINHARD -> 0.22f + (ruleSet.contrastRecovery / 240f)
    }
    val peakBias = when (ruleSet.peakDetectionStrategy) {
        com.miruplay.tv.model.PeakDetectionStrategy.DISABLED -> 0f
        com.miruplay.tv.model.PeakDetectionStrategy.STATIC_METADATA -> 0.02f
        com.miruplay.tv.model.PeakDetectionStrategy.DYNAMIC -> 0.05f
        com.miruplay.tv.model.PeakDetectionStrategy.DYNAMIC_AGGRESSIVE -> 0.1f
    }
    val signalBias = when (signalDescriptor?.signalKind) {
        VideoSignalKind.HDR10_PLUS -> 0.05f
        VideoSignalKind.DOLBY_VISION -> 0.03f
        else -> 0f
    }
    return (base + peakBias + signalBias).coerceIn(0f, 1f)
}

private fun toneMappingDesaturationValue(ruleSet: ToneMappingRuleSet): Float =
    (ruleSet.saturationRecovery / 24f).coerceIn(0f, 1f)

private fun targetPrimariesValue(
    ruleSet: ToneMappingRuleSet,
    signalDescriptor: VideoSignalDescriptor?,
    debugConfig: LibVlcDebugConfig,
): Int =
    when {
        shouldRequestHdrPassthroughTarget(signalDescriptor, debugConfig) -> LIBPLACEBO_COLOR_PRIMARIES_BT2020
        !shouldApplyLibVlcToneMappingParams(ruleSet, signalDescriptor, debugConfig) -> 0
        else -> LIBPLACEBO_COLOR_PRIMARIES_BT709
    }

private fun targetTransferValue(
    ruleSet: ToneMappingRuleSet,
    signalDescriptor: VideoSignalDescriptor?,
    debugConfig: LibVlcDebugConfig,
): Int =
    when {
        shouldRequestHdrPassthroughTarget(signalDescriptor, debugConfig) ->
            when (signalDescriptor?.transfer) {
                VideoTransferCharacteristic.HLG -> LIBPLACEBO_TRANSFER_HLG
                else -> LIBPLACEBO_TRANSFER_PQ
            }
        !shouldApplyLibVlcToneMappingParams(ruleSet, signalDescriptor, debugConfig) -> 0
        else -> LIBPLACEBO_TRANSFER_BT1886
    }

private fun shouldApplyLibVlcToneMappingParams(
    ruleSet: ToneMappingRuleSet,
    signalDescriptor: VideoSignalDescriptor?,
    debugConfig: LibVlcDebugConfig,
): Boolean =
    shouldUseLibVlcManagedToneMapping(debugConfig) &&
        ruleSet.enabled &&
        shouldForceLibVlcHdrOutput(ruleSet, signalDescriptor)

private fun shouldUseLibVlcManagedToneMapping(
    debugConfig: LibVlcDebugConfig,
): Boolean =
    when (debugConfig.voutMode) {
        LibVlcVoutMode.ANDROID_DISPLAY,
        LibVlcVoutMode.GL_SURFACE,
        LibVlcVoutMode.OUTPUT_CALLBACKS,
        LibVlcVoutMode.VMEM_PROBE,
        -> false
        else -> true
    }

private fun shouldRequestHdrPassthroughTarget(
    signalDescriptor: VideoSignalDescriptor?,
    debugConfig: LibVlcDebugConfig,
): Boolean =
    shouldUseSelfManagedHdrTarget(debugConfig) && signalDescriptor?.isHdr == true

private fun shouldUseSelfManagedHdrTarget(
    debugConfig: LibVlcDebugConfig,
): Boolean =
    when (debugConfig.voutMode) {
        LibVlcVoutMode.GL_SURFACE,
        LibVlcVoutMode.OUTPUT_CALLBACKS,
        LibVlcVoutMode.VMEM_PROBE,
        -> true
        else -> false
    }

private fun shouldForceLibVlcHdrOutput(
    ruleSet: ToneMappingRuleSet,
    signalDescriptor: VideoSignalDescriptor?,
): Boolean =
    ruleSet.enabled && signalDescriptor?.isHdr == true

private fun shouldUseStockAndroidDisplayStartupOptions(
    debugConfig: LibVlcDebugConfig,
): Boolean = debugConfig.voutMode == LibVlcVoutMode.ANDROID_DISPLAY

private fun resolveLibVlcRenderEvidence(
    stats: IMedia.Stats?,
): LibVlcRenderEvidence? {
    stats ?: return null
    return LibVlcRenderEvidence(
        decodedVideo = stats.decodedVideo.safeIntStat(),
        displayedPictures = stats.displayedPictures.safeIntStat(),
        lostPictures = stats.lostPictures.safeIntStat(),
        hasDisplayedFrames = stats.displayedPictures > 0,
    )
}

private fun shouldAttemptLibVlcNativeSnapshot(
    evidence: LibVlcRenderEvidence,
    voutMode: LibVlcVoutMode,
    isPlaying: Boolean,
    currentTimeMs: Long,
    vmemFrameReady: Boolean = true,
): Boolean {
    if (voutMode == LibVlcVoutMode.VMEM_STREAM) {
        return vmemFrameReady
    }
    if (evidence.hasDisplayedFrames) {
        return true
    }
    if (voutMode != LibVlcVoutMode.ANDROID_DISPLAY) {
        return false
    }
    if (evidence.decodedVideo <= 0) {
        return false
    }
    return isPlaying || currentTimeMs > 0L
}

private data class VlcTrackInfo(
    val idToken: String,
    val legacyId: Int? = null,
    val language: String? = null,
    val name: String? = null,
    val description: String? = null,
)

private fun MediaPlayer.audioTracksCompat(): List<VlcTrackInfo> =
    modernTracksCompat(IMedia.Track.Type.Audio)
        ?: legacyTrackDescriptionsCompat("getAudioTracks")

private fun MediaPlayer.subtitleTracksCompat(): List<VlcTrackInfo> =
    modernTracksCompat(IMedia.Track.Type.Text)
        ?: legacyTrackDescriptionsCompat("getSpuTracks")

private fun MediaPlayer.selectAudioTrackCompat(track: VlcTrackInfo): Boolean =
    modernSelectTrackCompat(track) || legacySetTrackCompat("setAudioTrack", track.legacyId)

private fun MediaPlayer.selectSubtitleTrackCompat(track: VlcTrackInfo): Boolean =
    modernSelectTrackCompat(track) || legacySetTrackCompat("setSpuTrack", track.legacyId)

private fun MediaPlayer.unselectSubtitleTrackCompat() {
    if (!modernUnselectTrackTypeCompat(IMedia.Track.Type.Text)) {
        legacySetTrackCompat("setSpuTrack", -1)
    }
}

private fun MediaPlayer.currentVideoTrackCompat(): IMedia.VideoTrack? =
    runCatching {
        javaClass.getMethod("getSelectedTrack", Int::class.javaPrimitiveType)
            .invoke(this, IMedia.Track.Type.Video) as? IMedia.VideoTrack
    }.getOrNull()
        ?: runCatching {
            javaClass.getMethod("getCurrentVideoTrack").invoke(this) as? IMedia.VideoTrack
        }.getOrNull()

private fun MediaPlayer.videoTracksCountCompat(): Int =
    modernTracksCompat(IMedia.Track.Type.Video)?.size
        ?: runCatching {
            javaClass.getMethod("getVideoTracksCount").invoke(this) as? Int
        }.getOrNull()
        ?: 0

private fun MediaPlayer.modernSelectTrackCompat(track: VlcTrackInfo): Boolean =
    runCatching {
        javaClass.getMethod("selectTrack", String::class.java)
            .invoke(this, track.idToken) as? Boolean
    }.getOrNull() == true

private fun MediaPlayer.modernUnselectTrackTypeCompat(trackType: Int): Boolean =
    runCatching {
        javaClass.getMethod("unselectTrackType", Int::class.javaPrimitiveType)
            .invoke(this, trackType)
        true
    }.getOrDefault(false)

private fun MediaPlayer.legacySetTrackCompat(
    methodName: String,
    trackId: Int?,
): Boolean {
    val resolvedTrackId = trackId ?: return false
    return runCatching {
        javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
            .invoke(this, resolvedTrackId) as? Boolean
    }.getOrNull() == true
}

private fun MediaPlayer.modernTracksCompat(trackType: Int): List<VlcTrackInfo>? =
    runCatching {
        @Suppress("UNCHECKED_CAST")
        javaClass.getMethod("getTracks", Int::class.javaPrimitiveType)
            .invoke(this, trackType) as? Array<IMedia.Track>
    }.getOrNull()
        ?.map { track ->
            val rawId = runCatching {
                track.javaClass.getField("id").get(track)
            }.getOrNull()
            VlcTrackInfo(
                idToken = rawId?.toString().orEmpty(),
                legacyId = (rawId as? Number)?.toInt(),
                language = track.language,
                name = runCatching {
                    track.javaClass.getField("name").get(track) as? String
                }.getOrNull(),
                description = track.description,
            )
        }

private fun MediaPlayer.legacyTrackDescriptionsCompat(
    methodName: String,
): List<VlcTrackInfo> =
    runCatching {
        javaClass.getMethod(methodName).invoke(this) as? Array<*>
    }.getOrNull()
        ?.mapNotNull { description ->
            val trackDescription = description ?: return@mapNotNull null
            val trackId = runCatching {
                trackDescription.javaClass.getField("id").getInt(trackDescription)
            }.getOrNull() ?: return@mapNotNull null
            val name = runCatching {
                trackDescription.javaClass.getField("name").get(trackDescription) as? String
            }.getOrNull()
            VlcTrackInfo(
                idToken = trackId.toString(),
                legacyId = trackId,
                name = name,
                description = name,
            )
        }
        .orEmpty()

private fun Number.safeIntStat(): Int =
    toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

private const val LIBPLACEBO_COLOR_PRIMARIES_BT709 = 3
private const val LIBPLACEBO_COLOR_PRIMARIES_BT2020 = 5
private const val LIBPLACEBO_TRANSFER_BT1886 = 1
private const val LIBPLACEBO_TRANSFER_PQ = 8
private const val LIBPLACEBO_TRANSFER_HLG = 9
