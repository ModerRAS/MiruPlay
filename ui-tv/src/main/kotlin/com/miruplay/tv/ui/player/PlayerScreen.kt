@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
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
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.ToneMappingProfilePreset
import com.miruplay.tv.model.ToneMappingRuleSet
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
import com.miruplay.tv.model.playbackBackToDetailsLabel
import com.miruplay.tv.model.playbackErrorTitle
import com.miruplay.tv.model.playbackLocalSourceLabel
import com.miruplay.tv.model.playbackPauseLabel
import com.miruplay.tv.model.playbackPlayLabel
import com.miruplay.tv.model.playbackRetryLabel
import com.miruplay.tv.model.playbackSeekBackLabel
import com.miruplay.tv.model.playbackSeekForwardLabel
import com.miruplay.tv.model.playbackSpeedChipLabel
import com.miruplay.tv.model.playbackSpeedMenuTitle
import com.miruplay.tv.model.playbackSpeedOptions
import com.miruplay.tv.model.playbackSpeedValueLabel
import com.miruplay.tv.model.playbackSubtitleCountLabel
import com.miruplay.tv.model.playbackSubtitleOffLabel
import com.miruplay.tv.model.playbackSubtitleOptionLabel
import com.miruplay.tv.model.playbackSubtitlesMenuTitle
import com.miruplay.tv.model.toneMappingPresetLabel
import com.miruplay.tv.model.toneMappingPresetOptions
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.MiruPlayPlaybackInputAction
import com.miruplay.tv.design.allowsPlayerRemoteRepeat
import com.miruplay.tv.design.isDedicatedPlayerRemoteIntent
import com.miruplay.tv.design.shouldRefreshTvPlaybackControls
import com.miruplay.tv.design.tvPlaybackOverlayAction
import com.miruplay.tv.ui.components.EpisodeVersionDialog
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
private const val PLAYER_EXIT_CONFIRMATION_WINDOW_MS = 2_000L

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
    val pendingNextEpisode by viewModel.pendingNextEpisode.collectAsStateWithLifecycle()
    val canPlayPreviousEpisode by viewModel.canPlayPreviousEpisode.collectAsStateWithLifecycle()
    val canPlayNextEpisode by viewModel.canPlayNextEpisode.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val controlsVisible by viewModel.controlsVisible.collectAsStateWithLifecycle()
    val controlsInteractionToken by viewModel.controlsInteractionToken.collectAsStateWithLifecycle()
    val availableSubtitles by viewModel.availableSubtitles.collectAsStateWithLifecycle()
    val availableAudioTracks by viewModel.availableAudioTracks.collectAsStateWithLifecycle()
    val selectedSubtitleTrackIndex by viewModel.selectedSubtitleTrackIndex.collectAsStateWithLifecycle()
    val selectedAudioTrackIndex by viewModel.selectedAudioTrackIndex.collectAsStateWithLifecycle()
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
    val timelineFocusRequester = remember { FocusRequester() }
    val transportFocusRequester = remember { FocusRequester() }
    val pictureFocusRequester = remember { FocusRequester() }
    val speedFocusRequester = remember { FocusRequester() }
    val subtitlesFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
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
    val screenOwnerToken = remember(playbackSource) { Any() }
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
    val context = LocalContext.current
    var openMenu by remember { mutableStateOf<PlayerMenu?>(null) }
    var infoPanelVisible by remember { mutableStateOf(false) }
    var infoTab by remember { mutableStateOf(PlayerInfoTab.Information) }
    var pressedDedicatedIntent by remember { mutableStateOf<MiruPlayInputIntent?>(null) }
    var pendingChromeFocus by remember { mutableStateOf<PlayerChromeFocusTarget?>(null) }
    var exitConfirmationStartedAt by remember(playbackSource) { mutableStateOf(0L) }
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
    val navigateBack = remember(onNavigateBack, screenOwnerToken) {
        {
            viewModel.saveCurrentProgressAndNavigate(screenOwnerToken, onNavigateBack)
        }
    }
    val requestExit = {
        val now = SystemClock.elapsedRealtime()
        if (isConfirmedPlayerExit(exitConfirmationStartedAt, now)) {
            exitConfirmationStartedAt = 0L
            navigateBack()
        } else {
            exitConfirmationStartedAt = now
            Toast.makeText(context, "再按一次返回键退出播放", Toast.LENGTH_SHORT).show()
        }
    }

    val focusTargetForMenu: (PlayerMenu) -> PlayerChromeFocusTarget = { menu ->
        when (menu) {
            PlayerMenu.Picture -> PlayerChromeFocusTarget.Picture
            PlayerMenu.Speed -> PlayerChromeFocusTarget.Speed
            PlayerMenu.Subtitles -> PlayerChromeFocusTarget.Subtitles
            PlayerMenu.Audio -> PlayerChromeFocusTarget.Audio
        }
    }
    val closeOpenOverlay = {
        if (infoPanelVisible) {
            infoPanelVisible = false
        } else {
            openMenu?.let { pendingChromeFocus = focusTargetForMenu(it) }
            openMenu = null
            viewModel.showControls()
        }
    }
    val toggleInfoPanel = {
        if (infoPanelVisible) {
            infoPanelVisible = false
        } else {
            openMenu = null
            viewModel.hideControls()
            infoPanelVisible = true
        }
    }

    LaunchedEffect(playbackSource, hasStartedPlayback, screenOwnerToken) {
        if (!hasStartedPlayback) {
            hasStartedPlayback = true
            viewModel.play(playbackSource, screenOwnerToken)
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

    LaunchedEffect(controlsVisible, infoPanelVisible) {
        if (!controlsVisible && !infoPanelVisible) {
            openMenu = null
            playerFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(controlsVisible, openMenu, pendingChromeFocus) {
        if (controlsVisible && openMenu == null) {
            when (pendingChromeFocus) {
                PlayerChromeFocusTarget.Picture -> pictureFocusRequester.requestFocus()
                PlayerChromeFocusTarget.Speed -> speedFocusRequester.requestFocus()
                PlayerChromeFocusTarget.Subtitles -> subtitlesFocusRequester.requestFocus()
                PlayerChromeFocusTarget.Audio -> audioFocusRequester.requestFocus()
                null,
                PlayerChromeFocusTarget.Timeline,
                -> timelineFocusRequester.requestFocus()
            }
            pendingChromeFocus = null
        }
    }

    LaunchedEffect(controlsVisible, controlsInteractionToken, playbackState, openMenu) {
        if (controlsVisible || openMenu != null) {
            exitConfirmationStartedAt = 0L
        }
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

    DisposableEffect(screenOwnerToken) {
        onDispose {
            viewModel.unbindVlcVideoHost(screenOwnerToken)
            viewModel.stopPlaybackWhenLeaving(screenOwnerToken)
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
                val intent = event.key.toMiruPlayInputIntent()
                val repeatsDedicatedCommand =
                    event.type == KeyEventType.KeyDown &&
                        intent?.isDedicatedPlayerRemoteIntent() == true &&
                        pressedDedicatedIntent == intent
                val consumed = handlePlayerKey(
                    event = event,
                    repeatsDedicatedCommand = repeatsDedicatedCommand,
                    controlsVisible = controlsVisible,
                    hasOpenMenu = openMenu != null || infoPanelVisible,
                    hasSubtitles = availableSubtitles.isNotEmpty(),
                    canPlayPreviousEpisode = canPlayPreviousEpisode,
                    canPlayNextEpisode = canPlayNextEpisode,
                    viewModel = viewModel,
                    onCloseMenu = closeOpenOverlay,
                    onHideControls = {
                        openMenu = null
                        viewModel.hideControls()
                    },
                    onOpenCaptions = {
                        infoPanelVisible = false
                        if (openMenu == PlayerMenu.Subtitles) {
                            pendingChromeFocus = PlayerChromeFocusTarget.Subtitles
                            openMenu = null
                        } else {
                            viewModel.showControls()
                            openMenu = PlayerMenu.Subtitles
                        }
                    },
                    onFocusOptions = {
                        infoPanelVisible = false
                        openMenu = null
                        pendingChromeFocus = PlayerChromeFocusTarget.Picture
                        viewModel.showControls()
                    },
                    onToggleInfo = toggleInfoPanel,
                    onStop = navigateBack,
                    onNavigateBack = requestExit,
                )
                if (intent?.isDedicatedPlayerRemoteIntent() == true) {
                    when (event.type) {
                        KeyEventType.KeyDown -> pressedDedicatedIntent = intent
                        KeyEventType.KeyUp -> if (pressedDedicatedIntent == intent) {
                            pressedDedicatedIntent = null
                        }
                        else -> Unit
                    }
                }
                consumed
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    viewModel.showControls()
                }
            }
    ) {
        val player = viewModel.getPlayer()
        val usesNativeVideoHost = viewModel.usesVlcVideoLayout()
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
        } else if (usesNativeVideoHost) {
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        isClickable = true
                        isFocusable = false
                        isFocusableInTouchMode = false
                        setOnClickListener { viewModel.showControls() }
                        doOnAttach {
                            if (isAttachedToWindow && width > 0 && height > 0) {
                                viewModel.bindVlcVideoHost(this)
                            } else {
                                doOnLayout { viewModel.bindVlcVideoHost(this) }
                            }
                        }
                    }
                },
                update = { host ->
                    if (viewModel.needsVlcVideoHostBinding()) {
                        viewModel.bindVlcVideoHost(host)
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
                selectedSubtitleTrackIndex = selectedSubtitleTrackIndex,
                selectedAudioTrackIndex = selectedAudioTrackIndex,
                playbackSpeed = playbackSpeed,
                signalFormatLabel = currentVideoSignalDescriptor?.displayLabel().orEmpty(),
                currentPicturePreset = viewModel.currentToneMappingPreset(),
                timelineFocusRequester = timelineFocusRequester,
                transportFocusRequester = transportFocusRequester,
                pictureFocusRequester = pictureFocusRequester,
                speedFocusRequester = speedFocusRequester,
                subtitlesFocusRequester = subtitlesFocusRequester,
                audioFocusRequester = audioFocusRequester,
                canPlayPreviousEpisode = canPlayPreviousEpisode,
                canPlayNextEpisode = canPlayNextEpisode,
                onBack = navigateBack,
                onInfo = toggleInfoPanel,
                onTogglePlayback = {
                    viewModel.togglePlayback()
                    viewModel.showControls()
                },
                onPreviousEpisode = viewModel::playPreviousEpisode,
                onNextEpisode = viewModel::playNextEpisode,
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
                onResetPictureSession = {
                    viewModel.resetCurrentToneMappingToDefault()
                    viewModel.showControls()
                },
                openMenu = openMenu,
                onOpenMenuChange = { menu ->
                    if (openMenu == menu) {
                        pendingChromeFocus = focusTargetForMenu(menu)
                        openMenu = null
                    } else {
                        openMenu = menu
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = infoPanelVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            PlayerInfoPanel(
                tab = infoTab,
                onTabChange = { infoTab = it },
                title = displayTitle.ifBlank { currentPlaybackSource.mediaSourceId },
                sourceLabel = displaySubtitle.ifBlank { currentPlaybackSource.mediaSourceId },
                source = currentPlaybackSource,
                playbackState = playbackState,
                currentPosition = currentPosition,
                duration = duration,
                playbackSpeed = playbackSpeed,
                signal = currentVideoSignalDescriptor,
                requestedBackend = currentRequestedBackend,
                activeBackend = currentActiveBackend,
                fallbackReason = fallbackReason,
                subtitles = availableSubtitles,
                audioTracks = availableAudioTracks,
                selectedSubtitleTrackIndex = selectedSubtitleTrackIndex,
                selectedAudioTrackIndex = selectedAudioTrackIndex,
            )
        }

        pendingNextEpisode?.let { episode ->
            EpisodeVersionDialog(
                episode = episode,
                onDismiss = viewModel::cancelNextVersionSelection,
                onPlay = viewModel::playNextVersion,
            )
        }

        if (errorMessage != null) {
            ErrorOverlay(
                message = errorMessage!!,
                onRetry = viewModel::retry,
                onExit = navigateBack,
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
    selectedSubtitleTrackIndex: Int?,
    selectedAudioTrackIndex: Int?,
    playbackSpeed: Float,
    signalFormatLabel: String,
    currentPicturePreset: ToneMappingProfilePreset,
    timelineFocusRequester: FocusRequester,
    transportFocusRequester: FocusRequester,
    pictureFocusRequester: FocusRequester,
    speedFocusRequester: FocusRequester,
    subtitlesFocusRequester: FocusRequester,
    audioFocusRequester: FocusRequester,
    canPlayPreviousEpisode: Boolean,
    canPlayNextEpisode: Boolean,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onSelectSubtitle: (Int?) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectPicturePreset: (ToneMappingProfilePreset) -> Unit,
    onSavePictureDefault: () -> Unit,
    onResetPictureSession: () -> Unit,
    openMenu: PlayerMenu?,
    onOpenMenuChange: (PlayerMenu) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.54f),
                        Color.Black.copy(alpha = 0.02f),
                        Color.Black.copy(alpha = 0.64f)
                    )
                )
            )
    ) {
        PlayerTopBar(
            title = title,
            sourceLabel = sourceLabel,
            onBack = onBack,
            onInfo = onInfo,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        TransportControls(
            isPlaying = playbackState is PlaybackState.Playing,
            isLoading = playbackState is PlaybackState.Loading || playbackState is PlaybackState.Buffering,
            canPlayPreviousEpisode = canPlayPreviousEpisode,
            canPlayNextEpisode = canPlayNextEpisode,
            focusRequester = transportFocusRequester,
            onPreviousEpisode = onPreviousEpisode,
            onNextEpisode = onNextEpisode,
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
                selectedSubtitleTrackIndex = selectedSubtitleTrackIndex,
                selectedAudioTrackIndex = selectedAudioTrackIndex,
                playbackSpeed = playbackSpeed,
                currentPicturePreset = currentPicturePreset,
                onSelectSubtitle = onSelectSubtitle,
                onSelectAudioTrack = onSelectAudioTrack,
                onSelectSpeed = onSelectSpeed,
                onSelectPicturePreset = onSelectPicturePreset,
                onSavePictureDefault = onSavePictureDefault,
                onResetPictureSession = onResetPictureSession,
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
            selectedSubtitleTrackIndex = selectedSubtitleTrackIndex,
            selectedAudioTrackIndex = selectedAudioTrackIndex,
            playbackSpeed = playbackSpeed,
            signalFormatLabel = signalFormatLabel,
            openMenu = openMenu,
            timelineFocusRequester = timelineFocusRequester,
            transportFocusRequester = transportFocusRequester,
            pictureFocusRequester = pictureFocusRequester,
            speedFocusRequester = speedFocusRequester,
            subtitlesFocusRequester = subtitlesFocusRequester,
            audioFocusRequester = audioFocusRequester,
            onSkipBackward = onSkipBackward,
            onSkipForward = onSkipForward,
            onOpenMenu = { menu ->
                onOpenMenuChange(menu)
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
    onInfo: () -> Unit,
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
        PlayerIconButton(
            icon = Icons.Filled.Info,
            label = "播放信息",
            onClick = onInfo,
            size = 54.dp,
        )
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    canPlayPreviousEpisode: Boolean,
    canPlayNextEpisode: Boolean,
    focusRequester: FocusRequester,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerIconButton(
            icon = Icons.Filled.SkipPrevious,
            label = "上一集",
            onClick = onPreviousEpisode,
            size = 54.dp,
            enabled = canPlayPreviousEpisode,
        )
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
            modifier = Modifier.focusRequester(focusRequester),
            prominent = true,
            enabled = !isLoading
        )
        PlayerIconButton(
            icon = Icons.Filled.FastForward,
            label = playbackSeekForwardLabel(PLAYBACK_SEEK_FORWARD_SECONDS),
            onClick = onSkipForward,
            size = 62.dp
        )
        PlayerIconButton(
            icon = Icons.Filled.SkipNext,
            label = "下一集",
            onClick = onNextEpisode,
            size = 54.dp,
            enabled = canPlayNextEpisode,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerBottomBar(
    currentPosition: Long,
    duration: Long,
    subtitles: List<SubtitleTrack>,
    audioTracks: List<AudioTrack>,
    selectedSubtitleTrackIndex: Int?,
    selectedAudioTrackIndex: Int?,
    playbackSpeed: Float,
    signalFormatLabel: String,
    openMenu: PlayerMenu?,
    timelineFocusRequester: FocusRequester,
    transportFocusRequester: FocusRequester,
    pictureFocusRequester: FocusRequester,
    speedFocusRequester: FocusRequester,
    subtitlesFocusRequester: FocusRequester,
    audioFocusRequester: FocusRequester,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onOpenMenu: (PlayerMenu) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.44f))
            .padding(start = 52.dp, end = 52.dp, top = 22.dp, bottom = 36.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeText(formatPlaybackPosition(currentPosition))
            PlaybackTimeline(
                progress = PlaybackTimingConventions.playbackProgressFraction(currentPosition, duration),
                focusRequester = timelineFocusRequester,
                upFocusRequester = transportFocusRequester,
                downFocusRequester = when {
                    audioTracks.isNotEmpty() -> audioFocusRequester
                    subtitles.isNotEmpty() -> subtitlesFocusRequester
                    else -> speedFocusRequester
                },
                onSkipBackward = onSkipBackward,
                onSkipForward = onSkipForward,
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
                onClick = { onOpenMenu(PlayerMenu.Picture) },
                modifier = Modifier
                    .focusRequester(pictureFocusRequester)
                    .focusProperties {
                        if (openMenu == null) up = timelineFocusRequester
                    },
            )
            PlayerActionChip(
                icon = Icons.Filled.Speed,
                text = playbackSpeedChipLabel(playbackSpeed),
                selected = openMenu == PlayerMenu.Speed,
                onClick = { onOpenMenu(PlayerMenu.Speed) },
                modifier = Modifier
                    .focusRequester(speedFocusRequester)
                    .focusProperties {
                        if (openMenu == null) up = timelineFocusRequester
                    },
            )
            PlayerActionChip(
                icon = Icons.Filled.Subtitles,
                text = selectedSubtitleTrackIndex
                    ?.let { index -> subtitles.getOrNull(index)?.let { playbackSubtitleOptionLabel(it, index) } }
                    ?: playbackSubtitleCountLabel(subtitles.size),
                selected = openMenu == PlayerMenu.Subtitles,
                enabled = subtitles.isNotEmpty(),
                onClick = { onOpenMenu(PlayerMenu.Subtitles) },
                modifier = Modifier
                    .focusRequester(subtitlesFocusRequester)
                    .focusProperties {
                        if (openMenu == null) up = timelineFocusRequester
                    },
            )
            PlayerActionChip(
                icon = Icons.Filled.Audiotrack,
                text = selectedAudioTrackIndex
                    ?.let { index ->
                        audioTracks.getOrNull(index)?.let {
                            playbackAudioOptionLabel(it.title, it.language, index)
                        }
                    }
                    ?: playbackAudioTrackCountLabel(audioTracks.size),
                selected = openMenu == PlayerMenu.Audio,
                enabled = audioTracks.isNotEmpty(),
                onClick = { onOpenMenu(PlayerMenu.Audio) },
                modifier = Modifier
                    .focusRequester(audioFocusRequester)
                    .focusProperties {
                        if (openMenu == null) up = timelineFocusRequester
                    },
            )
        }
    }
}

@Composable
internal fun PlaybackTimeline(
    progress: Float,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .height(18.dp)
            .focusRequester(focusRequester)
            .focusProperties {
                up = upFocusRequester
                down = downFocusRequester
            }
            .testTag(PLAYER_TIMELINE_TEST_TAG)
            .clip(shape)
            .background(Color.White.copy(alpha = if (isFocused) 0.30f else 0.20f))
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.14f),
                shape = shape,
            )
            .onPreviewKeyEvent { event ->
                handlePlaybackTimelineKey(
                    key = event.key,
                    type = event.type,
                    onSkipBackward = onSkipBackward,
                    onSkipForward = onSkipForward,
                )
            }
            .focusable(interactionSource = interactionSource)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(shape)
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
            .scale(if (isFocused) 1.08f else 1f)
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.20f),
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
        modifier = modifier
            .scale(if (isFocused) 1.04f else 1f)
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
                width = when {
                    isFocused -> 3.dp
                    selected -> 2.dp
                    else -> 1.dp
                },
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.14f),
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
    selectedSubtitleTrackIndex: Int?,
    selectedAudioTrackIndex: Int?,
    playbackSpeed: Float,
    currentPicturePreset: ToneMappingProfilePreset,
    onSelectSubtitle: (Int?) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectPicturePreset: (ToneMappingProfilePreset) -> Unit,
    onSavePictureDefault: () -> Unit,
    onResetPictureSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialFocusHandle = rememberInitialFocusHandle(key = menu)
    val speeds = remember { playbackSpeedOptions() }
    val picturePresets = remember { toneMappingPresetOptions() }
    val initialSpeedIndex = speeds.indexOf(playbackSpeed).takeIf { it >= 0 } ?: 0
    val initialPictureIndex = picturePresets.indexOf(currentPicturePreset).takeIf { it >= 0 } ?: 0
    val initialAudioIndex = selectedAudioTrackIndex?.takeIf { it in audioTracks.indices } ?: 0
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.58f))
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
                        modifier = if (index == initialSpeedIndex) {
                            Modifier.then(initialFocusHandle.modifier())
                        } else {
                            Modifier
                        }
                    )
                }
            }
            PlayerMenu.Subtitles -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    PlayerOptionButton(
                        text = playbackSubtitleOffLabel(),
                        selected = selectedSubtitleTrackIndex == null,
                        onClick = { onSelectSubtitle(null) },
                        modifier = if (selectedSubtitleTrackIndex == null) {
                            Modifier.then(initialFocusHandle.modifier())
                        } else {
                            Modifier
                        },
                    )
                }
                itemsIndexed(subtitles) { index, track ->
                    PlayerOptionButton(
                        text = playbackSubtitleOptionLabel(track, index),
                        selected = index == selectedSubtitleTrackIndex,
                        onClick = { onSelectSubtitle(index) },
                        modifier = if (index == selectedSubtitleTrackIndex) {
                            Modifier.then(initialFocusHandle.modifier())
                        } else {
                            Modifier
                        },
                    )
                }
            }
            PlayerMenu.Audio -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(audioTracks) { index, track ->
                    PlayerOptionButton(
                        text = playbackAudioOptionLabel(
                            title = track.title,
                            language = track.language,
                            index = index,
                        ),
                        selected = index == selectedAudioTrackIndex,
                        onClick = { onSelectAudioTrack(index) },
                        modifier = if (index == initialAudioIndex) {
                            Modifier.then(initialFocusHandle.modifier())
                        } else {
                            Modifier
                        },
                    )
                }
            }
            PlayerMenu.Picture -> Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                            modifier = if (index == initialPictureIndex) {
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
                        text = "重置本次播放",
                        selected = false,
                        onClick = onResetPictureSession,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerInfoPanel(
    tab: PlayerInfoTab,
    onTabChange: (PlayerInfoTab) -> Unit,
    title: String,
    sourceLabel: String,
    source: PlaybackSource,
    playbackState: PlaybackState,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    signal: VideoSignalDescriptor?,
    requestedBackend: PlaybackRenderBackend,
    activeBackend: PlaybackRenderBackend,
    fallbackReason: String?,
    subtitles: List<SubtitleTrack>,
    audioTracks: List<AudioTrack>,
    selectedSubtitleTrackIndex: Int?,
    selectedAudioTrackIndex: Int?,
) {
    val infoTabFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        infoTabFocusRequester.requestFocus()
    }
    val selectedSubtitle = selectedSubtitleTrackIndex
        ?.let { index -> subtitles.getOrNull(index)?.let { playbackSubtitleOptionLabel(it, index) } }
        ?: playbackSubtitleOffLabel()
    val selectedAudio = selectedAudioTrackIndex
        ?.let { index ->
            audioTracks.getOrNull(index)?.let {
                playbackAudioOptionLabel(it.title, it.language, index)
            }
        }
        ?: "—"

    Column(
        modifier = Modifier
            .width(480.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.66f))
            .border(1.dp, Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 26.dp, vertical = 28.dp),
    ) {
        Text(
            text = "播放信息",
            style = TvTypography.subtitle,
            color = TextPrimary,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerOptionButton(
                text = "信息",
                selected = tab == PlayerInfoTab.Information,
                onClick = { onTabChange(PlayerInfoTab.Information) },
                modifier = Modifier.focusRequester(infoTabFocusRequester),
            )
            PlayerOptionButton(
                text = "播放 / 调试",
                selected = tab == PlayerInfoTab.Diagnostics,
                onClick = { onTabChange(PlayerInfoTab.Diagnostics) },
            )
        }
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (tab) {
                PlayerInfoTab.Information -> {
                    PlayerInfoRow("番剧 / 剧集", title)
                    PlayerInfoRow("元数据", sourceLabel)
                    PlayerInfoRow("文件", sanitizedPlaybackSourceName(source.uri))
                    PlayerInfoRow("来源类型", playbackSourceSchemeLabel(source.uri))
                    PlayerInfoRow("时长", formatPlaybackPosition(duration))
                    PlayerInfoRow("视频信号", signal?.displayLabel() ?: "—")
                    PlayerInfoRow("视频编码", signal?.codecId?.takeIf(String::isNotBlank) ?: "—")
                    PlayerInfoRow("位深", signal?.bitDepth?.let { "$it bit" } ?: "—")
                    PlayerInfoRow("当前音轨", selectedAudio)
                    PlayerInfoRow("当前字幕", selectedSubtitle)
                }
                PlayerInfoTab.Diagnostics -> {
                    PlayerInfoRow("状态", playbackStateInfoLabel(playbackState))
                    PlayerInfoRow("位置", formatPlaybackPosition(currentPosition))
                    PlayerInfoRow("总时长", formatPlaybackPosition(duration))
                    PlayerInfoRow("速度", playbackSpeedValueLabel(playbackSpeed))
                    PlayerInfoRow("请求后端", playbackBackendLabel(requestedBackend))
                    PlayerInfoRow("活动后端", playbackBackendLabel(activeBackend))
                    PlayerInfoRow("回退原因", fallbackReason?.takeIf(String::isNotBlank) ?: "—")
                    PlayerInfoRow("传递函数", signal?.transfer?.name ?: "—")
                    PlayerInfoRow("色彩原色", signal?.colorPrimaries?.name ?: "—")
                    PlayerInfoRow("HDR 静态元数据", signal?.hasHdrStaticMetadata?.yesNoLabel() ?: "—")
                    PlayerInfoRow("HDR10+ 元数据", signal?.hasHdr10PlusMetadata?.yesNoLabel() ?: "—")
                    PlayerInfoRow("字幕轨", subtitles.size.toString())
                    PlayerInfoRow("音轨", audioTracks.size.toString())
                }
            }
        }
    }
}

@Composable
private fun PlayerInfoRow(label: String, value: String) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = if (isFocused) 0.14f else 0.04f))
            .border(
                width = if (isFocused) 1.dp else 0.dp,
                color = if (isFocused) FocusBorder else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = TextSecondary,
            style = TvTypography.caption,
            modifier = Modifier.width(118.dp),
        )
        Text(
            text = value.ifBlank { "—" },
            color = TextPrimary,
            style = TvTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun sanitizedPlaybackSourceName(uri: String): String =
    runCatching {
        val rawName = if (uri.isLocalPlaybackPath()) {
            uri.substringAfterLast('/').substringAfterLast('\\')
        } else {
            Uri.parse(uri).lastPathSegment.orEmpty()
        }
        Uri.decode(rawName).takeIf(String::isNotBlank)
    }.getOrNull() ?: "—"

internal fun playbackSourceSchemeLabel(uri: String): String =
    if (uri.isLocalPlaybackPath()) {
        "本地文件"
    } else {
        runCatching { Uri.parse(uri).scheme }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.uppercase()
            ?: "本地文件"
    }

private fun String.isLocalPlaybackPath(): Boolean =
    startsWith('/') || matches(Regex("^[A-Za-z]:[\\\\/].*"))

private fun playbackStateInfoLabel(state: PlaybackState): String =
    when (state) {
        PlaybackState.Idle -> "空闲"
        is PlaybackState.Loading -> "加载中"
        is PlaybackState.Playing -> "播放中"
        is PlaybackState.Paused -> "已暂停"
        is PlaybackState.Buffering -> "缓冲中"
        is PlaybackState.Ended -> "已结束"
        is PlaybackState.Error -> "错误"
    }

private fun Boolean.yesNoLabel(): String = if (this) "是" else "否"

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
            .scale(if (isFocused) 1.04f else 1f)
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
                width = when {
                    isFocused -> 3.dp
                    selected -> 2.dp
                    else -> 1.dp
                },
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.14f),
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
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val retryFocus = rememberInitialFocusHandle(key = message)

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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerOptionButton(
                    text = playbackRetryLabel(),
                    selected = false,
                    onClick = onRetry,
                    modifier = retryFocus.modifier(),
                )
                PlayerOptionButton(
                    text = playbackBackToDetailsLabel(),
                    selected = false,
                    onClick = onExit,
                )
            }
        }
    }
}

internal fun isConfirmedPlayerExit(lastBackAtMs: Long, nowMs: Long): Boolean =
    lastBackAtMs > 0L && nowMs - lastBackAtMs in 0L..PLAYER_EXIT_CONFIRMATION_WINDOW_MS

internal enum class PlayerMenu {
    Picture,
    Speed,
    Subtitles,
    Audio
}

internal enum class PlayerInfoTab {
    Information,
    Diagnostics,
}

private enum class PlayerChromeFocusTarget {
    Timeline,
    Picture,
    Speed,
    Subtitles,
    Audio,
}

internal const val PLAYER_TIMELINE_TEST_TAG = "player-timeline"

private fun handlePlayerKey(
    event: KeyEvent,
    repeatsDedicatedCommand: Boolean,
    controlsVisible: Boolean,
    hasOpenMenu: Boolean,
    hasSubtitles: Boolean,
    canPlayPreviousEpisode: Boolean,
    canPlayNextEpisode: Boolean,
    viewModel: PlayerViewModel,
    onCloseMenu: () -> Unit,
    onHideControls: () -> Unit,
    onOpenCaptions: () -> Unit,
    onFocusOptions: () -> Unit,
    onToggleInfo: () -> Unit,
    onStop: () -> Unit,
    onNavigateBack: () -> Unit,
): Boolean {
    val intent = event.key.toMiruPlayInputIntent() ?: return false
    val dedicatedAvailable = when (intent) {
        com.miruplay.tv.design.MiruPlayInputIntent.MediaPrevious -> canPlayPreviousEpisode
        com.miruplay.tv.design.MiruPlayInputIntent.MediaNext -> canPlayNextEpisode
        com.miruplay.tv.design.MiruPlayInputIntent.Captions -> hasSubtitles
        else -> true
    }
    if (!dedicatedAvailable) return false

    if (event.type != KeyEventType.KeyDown) {
        return event.type == KeyEventType.KeyUp && intent.isDedicatedPlayerRemoteIntent()
    }
    if (
        intent.isDedicatedPlayerRemoteIntent() &&
        repeatsDedicatedCommand &&
        !intent.allowsPlayerRemoteRepeat()
    ) {
        return true
    }

    val action = intent.tvPlaybackOverlayAction(
        controlsVisible = controlsVisible,
        hasOpenMenu = hasOpenMenu,
    ) ?: return false

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
        MiruPlayPlaybackInputAction.PreviousEpisode -> {
            viewModel.playPreviousEpisode()
            true
        }
        MiruPlayPlaybackInputAction.NextEpisode -> {
            viewModel.playNextEpisode()
            true
        }
        MiruPlayPlaybackInputAction.OpenCaptions -> {
            onOpenCaptions()
            true
        }
        MiruPlayPlaybackInputAction.FocusOptions -> {
            onFocusOptions()
            true
        }
        MiruPlayPlaybackInputAction.ToggleInfo -> {
            onToggleInfo()
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
        MiruPlayPlaybackInputAction.Stop -> {
            onStop()
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
        MiruPlayPlaybackInputAction.Launch -> false
    }
}
