package com.miruplay.tv.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.annotation.LayoutRes
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.PLAYBACK_SEEK_BACK_SECONDS
import com.miruplay.tv.model.PLAYBACK_SEEK_FORWARD_SECONDS
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.model.ToneMappingProfilePreset
import com.miruplay.tv.model.PlaybackTimingConventions
import com.miruplay.tv.model.formatPlaybackPosition
import com.miruplay.tv.model.pictureOsdMenuTitleLabel
import com.miruplay.tv.model.pictureSaveDefaultForFormatLabel
import com.miruplay.tv.model.pictureSessionOverrideLabel
import com.miruplay.tv.model.playbackBackendLabel
import com.miruplay.tv.model.playbackAudioMenuTitle
import com.miruplay.tv.model.playbackAudioOptionLabel
import com.miruplay.tv.model.playbackAudioTrackCountLabel
import com.miruplay.tv.model.playbackBackLabel
import com.miruplay.tv.model.playbackErrorTitle
import com.miruplay.tv.model.playbackLocalSourceLabel
import com.miruplay.tv.model.playbackPauseLabel
import com.miruplay.tv.model.playbackPlayLabel
import com.miruplay.tv.model.playbackConfirmExitLabel
import com.miruplay.tv.model.playbackSeekBackLabel
import com.miruplay.tv.model.playbackSeekForwardLabel
import com.miruplay.tv.model.playbackSpeedChipLabel
import com.miruplay.tv.model.playbackSpeedMenuTitle
import com.miruplay.tv.model.playbackSpeedOptions
import com.miruplay.tv.model.playbackSpeedValueLabel
import com.miruplay.tv.model.playbackSubtitleCountLabel
import com.miruplay.tv.model.playbackSubtitleOptionLabel
import com.miruplay.tv.model.playbackSubtitlesMenuTitle
import com.miruplay.tv.model.toneMappingPresetLabel
import com.miruplay.tv.model.toneMappingPresetOptions
import com.miruplay.tv.design.MiruPlayPlaybackInputAction
import com.miruplay.tv.design.shouldRefreshTvPlaybackControls
import com.miruplay.tv.design.tvPlaybackOverlayAction
import com.miruplay.tv.ui.components.rememberInitialFocusHandle
import com.miruplay.tv.ui.tv.R
import com.miruplay.tv.ui.components.toMiruPlayInputIntent
import com.miruplay.tv.ui.components.tvActivateKeyEvent
import com.miruplay.tv.player.AudioTrack
import com.miruplay.tv.player.resolveDeviceGlEsMajorVersion
import com.miruplay.tv.player.shouldUseDedicatedExperimentalGlSurface
import com.miruplay.tv.ui.theme.AnimeRed
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.FocusBorder
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography
import kotlinx.coroutines.delay

private const val STANDARD_DEBUG_CAPTURE_MIN_POSITION_MS = 5_000L
private const val STANDARD_DEBUG_CAPTURE_RETRY_INTERVAL_MS = 1_500L

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    playbackSource: PlaybackSource,
    onNavigateBack: () -> Unit,
) {
    remember(playbackSource) {
        Log.i(
            "PlayerScreen",
            "Startup trace: composable_enter source=${playbackSource.mediaSourceId} uri=${playbackSource.uri}",
        )
        true
    }
    val viewModel: PlayerViewModel = hiltViewModel()
    remember(playbackSource, viewModel) {
        Log.i(
            "PlayerScreen",
            "Startup trace: viewModel_resolved source=${playbackSource.mediaSourceId} viewModel=${viewModel::class.java.simpleName}",
        )
        true
    }
    PlayerScreenContent(
        playbackSource = playbackSource,
        onNavigateBack = onNavigateBack,
        viewModel = viewModel,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlayerScreenContent(
    playbackSource: PlaybackSource,
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val activePlaybackSource by viewModel.activePlaybackSource.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val controlsVisible by viewModel.controlsVisible.collectAsStateWithLifecycle()
    val controlsInteractionToken by viewModel.controlsInteractionToken.collectAsStateWithLifecycle()
    val availableSubtitles by viewModel.availableSubtitles.collectAsStateWithLifecycle()
    val availableAudioTracks by viewModel.availableAudioTracks.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val displayTitle by viewModel.displayTitle.collectAsStateWithLifecycle()
    val displaySubtitle by viewModel.displaySubtitle.collectAsStateWithLifecycle()
    val currentVideoSignalDescriptor by viewModel.currentVideoSignalDescriptor.collectAsStateWithLifecycle()
    val currentRenderRuleKey by viewModel.currentRenderRuleKey.collectAsStateWithLifecycle()
    val currentToneMappingRuleSet by viewModel.currentToneMappingRuleSet.collectAsStateWithLifecycle()
    val currentRequestedBackend by viewModel.currentRequestedBackend.collectAsStateWithLifecycle()
    val currentActiveBackend by viewModel.currentActiveBackend.collectAsStateWithLifecycle()
    val fallbackReason by viewModel.fallbackReason.collectAsStateWithLifecycle()
    val formatAwarePreferences by viewModel.formatAwarePreferences.collectAsStateWithLifecycle()
    val keepScreenOn = playbackState.keepsScreenOn()
    val view = LocalView.current
    val deviceGlEsMajorVersion = remember(view.context) {
        resolveDeviceGlEsMajorVersion(view.context)
    }
    val playerFocusRequester = remember { FocusRequester() }
    val currentPlaybackSource = activePlaybackSource ?: playbackSource
    val pendingDebugCaptureLabel = viewModel.pendingGlFrameCaptureLabel()
    var preferCapturableTextureView by remember(playbackSource) {
        mutableStateOf(!pendingDebugCaptureLabel.isNullOrBlank())
    }
    LaunchedEffect(pendingDebugCaptureLabel) {
        preferCapturableTextureView = latchCapturableTextureViewForPlaybackSession(
            wasAlreadyLatched = preferCapturableTextureView,
            pendingLabel = pendingDebugCaptureLabel,
        )
    }
    var hasStartedPlayback by remember(playbackSource) { mutableStateOf(false) }
    var preferDedicatedExperimentalSurface by remember(playbackSource) {
        mutableStateOf(
            latchDedicatedExperimentalSurfaceForPlaybackSession(
                wasAlreadyLatched = false,
                deviceGlEsMajorVersion = deviceGlEsMajorVersion,
                activeBackend = currentActiveBackend,
                requestedBackend = currentRequestedBackend,
                defaultBackend = formatAwarePreferences.defaultBackend,
            ),
        )
    }
    LaunchedEffect(
        currentActiveBackend,
        currentRequestedBackend,
        formatAwarePreferences.defaultBackend,
    ) {
        preferDedicatedExperimentalSurface = latchDedicatedExperimentalSurfaceForPlaybackSession(
            wasAlreadyLatched = preferDedicatedExperimentalSurface,
            deviceGlEsMajorVersion = deviceGlEsMajorVersion,
            activeBackend = currentActiveBackend,
            requestedBackend = currentRequestedBackend,
            defaultBackend = formatAwarePreferences.defaultBackend,
        )
    }
    val playerViewHost = remember(
        currentActiveBackend,
        currentRequestedBackend,
        hasStartedPlayback,
        formatAwarePreferences.defaultBackend,
        preferCapturableTextureView,
        preferDedicatedExperimentalSurface,
    ) {
        resolvePlayerViewHost(
            activeBackend = currentActiveBackend,
            requestedBackend = currentRequestedBackend,
            hasStartedPlayback = hasStartedPlayback,
            defaultBackend = formatAwarePreferences.defaultBackend,
            preferCapturableTextureView = preferCapturableTextureView,
            preferDedicatedGlSurface = preferDedicatedExperimentalSurface,
        )
    }
    var openMenu by remember { mutableStateOf<PlayerMenu?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var lastStandardDebugCaptureAttempt by remember(playbackSource) {
        mutableStateOf<StandardDebugCaptureAttempt?>(null)
    }
    val shouldShowExperimentalSurface = playerViewHost == PlayerViewHost.DedicatedGlSurface
    val shouldCaptureStandardDebugFrame = shouldScheduleStandardDebugCapture(
        pendingLabel = viewModel.pendingGlFrameCaptureLabel(),
        playbackState = playbackState,
        currentPosition = currentPosition,
    )
    val navigateBack = remember(onNavigateBack) {
        {
            viewModel.saveCurrentProgressAndNavigate(onNavigateBack)
        }
    }

    LaunchedEffect(playbackSource, hasStartedPlayback) {
        if (!hasStartedPlayback) {
            hasStartedPlayback = true
            viewModel.play(playbackSource)
        }
    }

    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        viewModel.finishEvents.collect { event ->
            when (event) {
                PlaybackFinishEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            openMenu = null
            playerFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(controlsVisible, controlsInteractionToken, playbackState, openMenu) {
        if (controlsVisible && openMenu == null && playbackState is PlaybackState.Playing) {
            delay(4200)
            viewModel.hideControls()
        }
    }

    LaunchedEffect(
        shouldShowExperimentalSurface,
        playbackState,
        currentPosition,
        playerViewRef,
    ) {
        if (shouldShowExperimentalSurface) {
            playerViewRef = null
            return@LaunchedEffect
        }
        val pendingLabel = viewModel.pendingGlFrameCaptureLabel() ?: return@LaunchedEffect
        if (!shouldCaptureStandardDebugFrame) {
            return@LaunchedEffect
        }
        val host = playerViewRef ?: return@LaunchedEffect
        val previousAttempt = lastStandardDebugCaptureAttempt
        if (
            previousAttempt?.label == pendingLabel &&
            currentPosition - previousAttempt.positionMs < STANDARD_DEBUG_CAPTURE_RETRY_INTERVAL_MS
        ) {
            return@LaunchedEffect
        }
        lastStandardDebugCaptureAttempt = StandardDebugCaptureAttempt(
            label = pendingLabel,
            positionMs = currentPosition,
        )
        Log.i(
            "PlayerScreen",
            "Scheduling PlayerView debug capture label=$pendingLabel positionMs=$currentPosition host=${host.javaClass.simpleName}",
        )
        delay(250)
        if (viewModel.pendingGlFrameCaptureLabel() == pendingLabel) {
            host.post {
                host.captureCurrentFrame(pendingLabel) { capturedLabel ->
                    viewModel.clearPendingGlFrameCaptureLabel(capturedLabel)
                }
            }
        }
    }

    DisposableEffect(view, keepScreenOn) {
        val window = view.context.findActivity()?.window
        if (keepScreenOn) {
            view.keepScreenOn = true
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            view.keepScreenOn = false
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            view.keepScreenOn = false
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.unbindVlcVideoHost()
            viewModel.stopPlaybackWhenLeaving()
        }
    }

    LaunchedEffect(
        playerViewHost,
        currentActiveBackend,
        currentRequestedBackend,
        shouldShowExperimentalSurface,
    ) {
        Log.i(
            "PlayerScreen",
            "Resolved video host host=$playerViewHost active=$currentActiveBackend requested=$currentRequestedBackend experimental=$shouldShowExperimentalSurface",
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                handlePlayerKey(
                    event = event,
                    controlsVisible = controlsVisible,
                    hasOpenMenu = openMenu != null,
                    viewModel = viewModel,
                    onCloseMenu = {
                        openMenu = null
                        viewModel.showControls()
                    },
                    onHideControls = {
                        openMenu = null
                        viewModel.hideControls()
                    },
                    onNavigateBack = navigateBack
                )
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    viewModel.showControls()
                }
            }
    ) {
        val player = viewModel.getPlayer()
        if (shouldShowExperimentalSurface) {
            AndroidView(
                factory = { context ->
                    Log.i(
                        "PlayerScreen",
                        "Creating GLVideoSurfaceView host active=$currentActiveBackend requested=$currentRequestedBackend hasPlayer=${player != null}",
                    )
                    GLVideoSurfaceView(context).apply {
                        isClickable = true
                        isFocusable = false
                        isFocusableInTouchMode = false
                        setOnClickListener { viewModel.showControls() }
                        setOnFrameCaptured { label ->
                            viewModel.clearPendingGlFrameCaptureLabel(label)
                        }
                        bind(
                            player = player,
                            ruleSet = currentToneMappingRuleSet,
                            signalDescriptor = currentVideoSignalDescriptor,
                        )
                        if (shouldCaptureStandardDebugFrame) {
                            viewModel.pendingGlFrameCaptureLabel()?.let { label ->
                                post { captureNextRenderedFrame(label) }
                            }
                        }
                    }
                },
                update = { glView ->
                    Log.i(
                        "PlayerScreen",
                        "Updating GLVideoSurfaceView host active=$currentActiveBackend requested=$currentRequestedBackend hasPlayer=${player != null}",
                    )
                    glView.setOnClickListener { viewModel.showControls() }
                    glView.setOnFrameCaptured { label ->
                        viewModel.clearPendingGlFrameCaptureLabel(label)
                    }
                    glView.bind(
                        player = player,
                        ruleSet = currentToneMappingRuleSet,
                        signalDescriptor = currentVideoSignalDescriptor,
                    )
                    if (shouldCaptureStandardDebugFrame) {
                        viewModel.pendingGlFrameCaptureLabel()?.let { label ->
                            glView.post { glView.captureNextRenderedFrame(label) }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (player != null) {
            key(playerViewHost) {
                AndroidView(
                     factory = { context ->
                         (LayoutInflater.from(context).inflate(
                             playerViewHost.layoutResId,
                             /* root = */ null as android.view.ViewGroup?,
                             false
                         ) as PlayerView).apply {
                             playerViewRef = this
                             this.player = player
                             useController = false
                             resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                             setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                             isClickable = true
                             isFocusable = false
                             isFocusableInTouchMode = false
                             setOnClickListener { viewModel.showControls() }
                         }
                     },
                     update = { view ->
                         playerViewRef = view
                         view.player = player
                         view.setOnClickListener { viewModel.showControls() }
                     },
                     modifier = Modifier.fillMaxSize()
                 )
             }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(96.dp)
                )
            }
        }

        if (errorMessage != null) {
            ErrorOverlay(
                message = errorMessage!!,
                onExit = onNavigateBack
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerChrome(
                title = displayTitle.ifBlank { currentPlaybackSource.mediaSourceId },
                sourceLabel = displaySubtitle.ifBlank { currentPlaybackSource.mediaSourceId },
                playbackState = playbackState,
                currentPosition = currentPosition,
                duration = duration,
                subtitles = availableSubtitles,
                audioTracks = availableAudioTracks,
                playbackSpeed = playbackSpeed,
                signalFormatLabel = currentVideoSignalDescriptor?.displayLabel().orEmpty(),
                activeBackend = currentActiveBackend,
                requestedBackend = currentRequestedBackend,
                fallbackReason = fallbackReason,
                currentPicturePreset = viewModel.currentToneMappingPreset(),
                onBack = navigateBack,
                onTogglePlayback = {
                    viewModel.togglePlayback()
                    viewModel.showControls()
                },
                onSkipBackward = {
                    viewModel.skipBackward()
                    viewModel.showControls()
                },
                onSkipForward = {
                    viewModel.skipForward()
                    viewModel.showControls()
                },
                onSelectSubtitle = { index ->
                    viewModel.selectSubtitle(index)
                    viewModel.showControls()
                },
                onSelectAudioTrack = { index ->
                    viewModel.selectAudioTrack(index)
                    viewModel.showControls()
                },
                onSelectSpeed = { speed ->
                    viewModel.setPlaybackSpeed(speed)
                    viewModel.showControls()
                },
                onSelectPicturePreset = { preset ->
                    viewModel.applyToneMappingPresetForCurrentFormat(preset)
                    viewModel.showControls()
                },
                onSavePictureDefault = {
                    viewModel.saveCurrentToneMappingRuleAsDefault()
                    viewModel.showControls()
                },
                onSelectBackend = { backend ->
                    viewModel.setToneMappingBackendForSession(backend)
                    viewModel.showControls()
                },
                openMenu = openMenu,
                onOpenMenuChange = { openMenu = it }
            )
        }
    }
}

private fun PlaybackState.keepsScreenOn(): Boolean =
    this is PlaybackState.Loading ||
        this is PlaybackState.Playing ||
        this is PlaybackState.Buffering

internal data class StandardDebugCaptureAttempt(
    val label: String,
    val positionMs: Long,
)

internal fun shouldScheduleStandardDebugCapture(
    pendingLabel: String?,
    playbackState: PlaybackState,
    currentPosition: Long,
): Boolean {
    if (pendingLabel.isNullOrBlank()) {
        return false
    }
    if (playbackState !is PlaybackState.Playing && playbackState !is PlaybackState.Paused) {
        return false
    }
    return currentPosition >= STANDARD_DEBUG_CAPTURE_MIN_POSITION_MS
}

internal enum class PlayerViewHost(@LayoutRes val layoutResId: Int) {
    SurfaceView(R.layout.player_view_surface),
    SurfaceTextureView(R.layout.player_view_texture),
    DedicatedGlSurface(0),
}

internal fun resolvePlayerViewHost(
    activeBackend: PlaybackRenderBackend,
    requestedBackend: PlaybackRenderBackend,
    hasStartedPlayback: Boolean = true,
    defaultBackend: PlaybackRenderBackend = PlaybackRenderBackend.STANDARD_EXO,
    preferCapturableTextureView: Boolean = false,
    preferDedicatedGlSurface: Boolean = false,
): PlayerViewHost =
    if (preferDedicatedGlSurface) {
        PlayerViewHost.DedicatedGlSurface
    } else if (
        !hasStartedPlayback &&
        defaultBackend == PlaybackRenderBackend.EXPERIMENTAL_GL
    ) {
        PlayerViewHost.DedicatedGlSurface
    } else if (
        preferCapturableTextureView ||
        activeBackend == PlaybackRenderBackend.EXPERIMENTAL_GL ||
        requestedBackend == PlaybackRenderBackend.EXPERIMENTAL_GL
    ) {
        PlayerViewHost.SurfaceTextureView
    } else {
        PlayerViewHost.SurfaceView
    }

internal fun latchCapturableTextureViewForPlaybackSession(
    wasAlreadyLatched: Boolean,
    pendingLabel: String?,
): Boolean = wasAlreadyLatched || !pendingLabel.isNullOrBlank()

internal fun latchDedicatedExperimentalSurfaceForPlaybackSession(
    wasAlreadyLatched: Boolean,
    deviceGlEsMajorVersion: Int,
    activeBackend: PlaybackRenderBackend,
    requestedBackend: PlaybackRenderBackend,
    defaultBackend: PlaybackRenderBackend,
): Boolean =
    wasAlreadyLatched || (
        shouldUseDedicatedExperimentalGlSurface(deviceGlEsMajorVersion) &&
            (
                activeBackend == PlaybackRenderBackend.EXPERIMENTAL_GL ||
                    requestedBackend == PlaybackRenderBackend.EXPERIMENTAL_GL ||
                    defaultBackend == PlaybackRenderBackend.EXPERIMENTAL_GL
                )
        )

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun PlayerChrome(
    title: String,
    sourceLabel: String,
    playbackState: PlaybackState,
    currentPosition: Long,
    duration: Long,
    subtitles: List<SubtitleTrack>,
    audioTracks: List<AudioTrack>,
    playbackSpeed: Float,
    signalFormatLabel: String,
    activeBackend: PlaybackRenderBackend,
    requestedBackend: PlaybackRenderBackend,
    fallbackReason: String?,
    currentPicturePreset: ToneMappingProfilePreset,
    onBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectPicturePreset: (ToneMappingProfilePreset) -> Unit,
    onSavePictureDefault: () -> Unit,
    onSelectBackend: (PlaybackRenderBackend?) -> Unit,
    openMenu: PlayerMenu?,
    onOpenMenuChange: (PlayerMenu?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.78f),
                        Color.Black.copy(alpha = 0.10f),
                        Color.Black.copy(alpha = 0.88f)
                    )
                )
            )
    ) {
        PlayerTopBar(
            title = title,
            sourceLabel = sourceLabel,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        TransportControls(
            isPlaying = playbackState is PlaybackState.Playing,
            isLoading = playbackState is PlaybackState.Loading || playbackState is PlaybackState.Buffering,
            onTogglePlayback = onTogglePlayback,
            onSkipBackward = onSkipBackward,
            onSkipForward = onSkipForward,
            modifier = Modifier.align(Alignment.Center)
        )

        if (openMenu != null) {
            PlayerOptionsPanel(
                menu = openMenu!!,
                subtitles = subtitles,
                audioTracks = audioTracks,
                playbackSpeed = playbackSpeed,
                signalFormatLabel = signalFormatLabel,
                activeBackend = activeBackend,
                requestedBackend = requestedBackend,
                fallbackReason = fallbackReason,
                currentPicturePreset = currentPicturePreset,
                onSelectSubtitle = onSelectSubtitle,
                onSelectAudioTrack = onSelectAudioTrack,
                onSelectSpeed = onSelectSpeed,
                onSelectPicturePreset = onSelectPicturePreset,
                onSavePictureDefault = onSavePictureDefault,
                onSelectBackend = onSelectBackend,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 52.dp, end = 52.dp, bottom = 164.dp)
            )
        }

        PlayerBottomBar(
            currentPosition = currentPosition,
            duration = duration,
            subtitles = subtitles,
            audioTracks = audioTracks,
            playbackSpeed = playbackSpeed,
            signalFormatLabel = signalFormatLabel,
            openMenu = openMenu,
            onOpenMenu = { menu ->
                onOpenMenuChange(if (openMenu == menu) null else menu)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    sourceLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 44.dp, vertical = 34.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            label = playbackBackLabel(),
            onClick = onBack,
            size = 54.dp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TvTypography.subtitle,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sourceLabel,
                style = TvTypography.body,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    onTogglePlayback: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        playFocusRequester.requestFocus()
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.38f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerIconButton(
            icon = Icons.Filled.FastRewind,
            label = playbackSeekBackLabel(PLAYBACK_SEEK_BACK_SECONDS),
            onClick = onSkipBackward,
            size = 62.dp
        )
        PlayerIconButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            label = if (isPlaying) playbackPauseLabel() else playbackPlayLabel(),
            onClick = onTogglePlayback,
            size = 82.dp,
            modifier = Modifier.focusRequester(playFocusRequester),
            prominent = true,
            enabled = !isLoading
        )
        PlayerIconButton(
            icon = Icons.Filled.FastForward,
            label = playbackSeekForwardLabel(PLAYBACK_SEEK_FORWARD_SECONDS),
            onClick = onSkipForward,
            size = 62.dp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerBottomBar(
    currentPosition: Long,
    duration: Long,
    subtitles: List<SubtitleTrack>,
    audioTracks: List<AudioTrack>,
    playbackSpeed: Float,
    signalFormatLabel: String,
    openMenu: PlayerMenu?,
    onOpenMenu: (PlayerMenu) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(start = 52.dp, end = 52.dp, top = 22.dp, bottom = 36.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeText(formatPlaybackPosition(currentPosition))
            PlaybackTimeline(
                progress = PlaybackTimingConventions.playbackProgressFraction(currentPosition, duration),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp)
            )
            TimeText(formatPlaybackPosition(duration))
        }

        Spacer(Modifier.height(18.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            maxItemsInEachRow = 5,
        ) {
            PlayerInfoChip(
                icon = Icons.Filled.GraphicEq,
                text = signalFormatLabel.ifBlank { playbackLocalSourceLabel() }
            )
            PlayerActionChip(
                icon = Icons.Filled.PhotoFilter,
                text = pictureOsdMenuTitleLabel(),
                selected = openMenu == PlayerMenu.Picture,
                onClick = { onOpenMenu(PlayerMenu.Picture) }
            )
            PlayerActionChip(
                icon = Icons.Filled.Speed,
                text = playbackSpeedChipLabel(playbackSpeed),
                selected = openMenu == PlayerMenu.Speed,
                onClick = { onOpenMenu(PlayerMenu.Speed) }
            )
            PlayerActionChip(
                icon = Icons.Filled.Subtitles,
                text = playbackSubtitleCountLabel(subtitles.size),
                selected = openMenu == PlayerMenu.Subtitles,
                enabled = subtitles.isNotEmpty(),
                onClick = { onOpenMenu(PlayerMenu.Subtitles) }
            )
            PlayerActionChip(
                icon = Icons.Filled.Audiotrack,
                text = playbackAudioTrackCountLabel(audioTracks.size),
                selected = openMenu == PlayerMenu.Audio,
                enabled = audioTracks.isNotEmpty(),
                onClick = { onOpenMenu(PlayerMenu.Audio) }
            )
        }
    }
}

@Composable
private fun PlaybackTimeline(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.20f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(8.dp))
                .background(AnimeRed)
        )
    }
}

@Composable
private fun TimeText(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val background = when {
        !enabled -> DarkSurface.copy(alpha = 0.45f)
        prominent -> AnimeRed
        isFocused -> AnimeRed.copy(alpha = 0.90f)
        else -> Color.White.copy(alpha = 0.16f)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.20f),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .onKeyEvent { event ->
                tvActivateKeyEvent(
                    key = event.key,
                    type = event.type,
                    enabled = enabled,
                    onActivate = onClick,
                )
            }
            .focusable(enabled = enabled, interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) Color.White else TextSecondary.copy(alpha = 0.60f),
            modifier = Modifier.size(if (prominent) 40.dp else 30.dp)
        )
    }
}

@Composable
private fun PlayerInfoChip(
    icon: ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = TextPrimary,
            style = TvTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}

@Composable
private fun PlayerActionChip(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val background = when {
        !enabled -> Color.White.copy(alpha = 0.08f)
        selected -> AnimeRed.copy(alpha = 0.34f)
        isFocused -> AnimeRed.copy(alpha = 0.80f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.55f)

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .onPreviewKeyEvent { event ->
                tvActivateKeyEvent(
                    key = event.key,
                    type = event.type,
                    enabled = enabled,
                    onActivate = onClick,
                )
            }
            .border(
                width = if (isFocused || selected) 2.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = color,
            disabledContainerColor = background,
            disabledContentColor = color
        ),
        contentPadding = PaddingValues(horizontal = 14.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                color = color,
                style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerOptionsPanel(
    menu: PlayerMenu,
    subtitles: List<SubtitleTrack>,
    audioTracks: List<AudioTrack>,
    playbackSpeed: Float,
    signalFormatLabel: String,
    activeBackend: PlaybackRenderBackend,
    requestedBackend: PlaybackRenderBackend,
    fallbackReason: String?,
    currentPicturePreset: ToneMappingProfilePreset,
    onSelectSubtitle: (Int) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectPicturePreset: (ToneMappingProfilePreset) -> Unit,
    onSavePictureDefault: () -> Unit,
    onSelectBackend: (PlaybackRenderBackend?) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialFocusHandle = rememberInitialFocusHandle(key = menu)
    val speeds = remember { playbackSpeedOptions() }
    val picturePresets = remember { toneMappingPresetOptions() }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(18.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = when (menu) {
                PlayerMenu.Speed -> playbackSpeedMenuTitle()
                PlayerMenu.Subtitles -> playbackSubtitlesMenuTitle()
                PlayerMenu.Audio -> playbackAudioMenuTitle()
                PlayerMenu.Picture -> pictureOsdMenuTitleLabel()
            },
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        when (menu) {
            PlayerMenu.Speed -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                speeds.forEachIndexed { index, speed ->
                    PlayerOptionButton(
                        text = playbackSpeedValueLabel(speed),
                        selected = speed == playbackSpeed,
                        onClick = { onSelectSpeed(speed) },
                        modifier = if (index == 0) {
                            Modifier.then(initialFocusHandle.modifier())
                        } else {
                            Modifier
                        }
                    )
                }
            }
            PlayerMenu.Subtitles -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                subtitles.forEachIndexed { index, track ->
                    PlayerOptionButton(
                        text = playbackSubtitleOptionLabel(track, index),
                        selected = false,
                        onClick = { onSelectSubtitle(index) },
                        modifier = if (index == 0) {
                            Modifier.then(initialFocusHandle.modifier())
                        } else {
                            Modifier
                        }
                    )
                }
            }
            PlayerMenu.Audio -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                audioTracks.forEachIndexed { index, track ->
                    PlayerOptionButton(
                        text = playbackAudioOptionLabel(
                            title = track.title,
                            language = track.language,
                            index = index,
                        ),
                        selected = false,
                        onClick = { onSelectAudioTrack(index) },
                        modifier = if (index == 0) {
                            Modifier.then(initialFocusHandle.modifier())
                        } else {
                            Modifier
                        }
                    )
                }
            }
            PlayerMenu.Picture -> {
                val infoItems = buildList {
                    add(signalFormatLabel.ifBlank { "Auto" })
                    add(playbackBackendLabel(activeBackend))
                    if (fallbackReason != null) {
                        add(fallbackReason)
                    } else if (requestedBackend != activeBackend) {
                        add("Requested ${playbackBackendLabel(requestedBackend)}")
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        infoItems.forEach { label ->
                            PlayerOptionButton(
                                text = label,
                                selected = false,
                                onClick = {},
                                enabled = false,
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        picturePresets.forEachIndexed { index, preset ->
                            PlayerOptionButton(
                                text = "${pictureSessionOverrideLabel()} ${toneMappingPresetLabel(preset)}",
                                selected = preset == currentPicturePreset,
                                onClick = { onSelectPicturePreset(preset) },
                                modifier = if (index == 0) {
                                    Modifier.then(initialFocusHandle.modifier())
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerOptionButton(
                            text = pictureSaveDefaultForFormatLabel(),
                            selected = false,
                            onClick = onSavePictureDefault,
                        )
                        PlayerOptionButton(
                            text = "标准 Exo",
                            selected = activeBackend == PlaybackRenderBackend.STANDARD_EXO,
                            onClick = { onSelectBackend(PlaybackRenderBackend.STANDARD_EXO) },
                        )
                        PlayerOptionButton(
                            text = "旧实验 GL",
                            selected = requestedBackend == PlaybackRenderBackend.EXPERIMENTAL_GL,
                            onClick = { onSelectBackend(PlaybackRenderBackend.EXPERIMENTAL_GL) },
                        )
                        PlayerOptionButton(
                            text = "跟随默认",
                            selected = false,
                            onClick = { onSelectBackend(null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerOptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val background = when {
        !enabled -> Color.White.copy(alpha = 0.08f)
        selected -> AnimeRed
        isFocused -> AnimeRed.copy(alpha = 0.78f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .onPreviewKeyEvent { event ->
                tvActivateKeyEvent(
                    key = event.key,
                    type = event.type,
                    onActivate = onClick,
                )
            }
            .border(
                width = if (isFocused || selected) 2.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = TextPrimary,
            disabledContainerColor = background,
            disabledContentColor = TextSecondary.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        Text(
            text = text,
            color = TextPrimary,
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = playbackErrorTitle(),
                style = TvTypography.subtitle,
                color = TextPrimary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = TvTypography.body,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AnimeRed)
                    .clickable(onClick = onExit)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = playbackConfirmExitLabel(),
                    color = Color.White,
                    style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

internal enum class PlayerMenu {
    Picture,
    Speed,
    Subtitles,
    Audio
}

private fun handlePlayerKey(
    event: KeyEvent,
    controlsVisible: Boolean,
    hasOpenMenu: Boolean,
    viewModel: PlayerViewModel,
    onCloseMenu: () -> Unit,
    onHideControls: () -> Unit,
    onNavigateBack: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    val action = event.key.toMiruPlayInputIntent()
        ?.tvPlaybackOverlayAction(
            controlsVisible = controlsVisible,
            hasOpenMenu = hasOpenMenu,
        )
        ?: return false

    if (action.shouldRefreshTvPlaybackControls(controlsVisible)) {
        viewModel.showControls()
    }

    return when (action) {
        MiruPlayPlaybackInputAction.SeekBack -> {
            viewModel.skipBackward()
            true
        }
        MiruPlayPlaybackInputAction.SeekForward -> {
            viewModel.skipForward()
            true
        }
        MiruPlayPlaybackInputAction.ShowControls -> true
        MiruPlayPlaybackInputAction.TogglePause -> {
            viewModel.togglePlayback()
            true
        }
        MiruPlayPlaybackInputAction.Resume -> {
            viewModel.resume()
            true
        }
        MiruPlayPlaybackInputAction.Pause -> {
            viewModel.pause()
            true
        }
        MiruPlayPlaybackInputAction.HideControls -> {
            onHideControls()
            true
        }
        MiruPlayPlaybackInputAction.CloseMenu -> {
            onCloseMenu()
            true
        }
        MiruPlayPlaybackInputAction.NavigateBack -> {
            onNavigateBack()
            true
        }
        MiruPlayPlaybackInputAction.Launch,
        MiruPlayPlaybackInputAction.Stop,
        -> false
    }
}
