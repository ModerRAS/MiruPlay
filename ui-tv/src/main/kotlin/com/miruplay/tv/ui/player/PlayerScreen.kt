package com.miruplay.tv.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
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
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.PLAYBACK_SEEK_BACK_SECONDS
import com.miruplay.tv.model.PLAYBACK_SEEK_FORWARD_SECONDS
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.model.PlaybackTimingConventions
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.formatPlaybackPosition
import com.miruplay.tv.model.playbackAudioMenuTitle
import com.miruplay.tv.model.playbackAudioOptionLabel
import com.miruplay.tv.model.playbackAudioTrackCountLabel
import com.miruplay.tv.model.playbackBackLabel
import com.miruplay.tv.model.playbackErrorTitle
import com.miruplay.tv.model.playbackLocalSourceLabel
import com.miruplay.tv.model.playbackPauseLabel
import com.miruplay.tv.model.playbackPlayLabel
import com.miruplay.tv.model.playbackRetryLabel
import com.miruplay.tv.model.playbackSeekBackLabel
import com.miruplay.tv.model.playbackSeekForwardLabel
import com.miruplay.tv.model.playbackSpeedChipLabel
import com.miruplay.tv.model.playbackSpeedMenuTitle
import com.miruplay.tv.model.playbackSpeedValueLabel
import com.miruplay.tv.model.playbackSubtitleCountLabel
import com.miruplay.tv.model.playbackSubtitleOptionLabel
import com.miruplay.tv.model.playbackSubtitlesMenuTitle
import com.miruplay.tv.design.MiruPlayPlaybackInputAction
import com.miruplay.tv.design.shouldRefreshTvPlaybackControls
import com.miruplay.tv.design.tvPlaybackOverlayAction
import com.miruplay.tv.ui.components.isTvActivateKey
import com.miruplay.tv.ui.components.toMiruPlayInputIntent
import com.miruplay.tv.player.AudioTrack
import com.miruplay.tv.ui.theme.AnimeRed
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.FocusBorder
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    playbackSource: PlaybackSource,
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
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
    val keepScreenOn = playbackState.keepsScreenOn()
    val view = LocalView.current
    val playerFocusRequester = remember { FocusRequester() }
    val currentPlaybackSource = activePlaybackSource ?: playbackSource
    val title = remember(currentPlaybackSource) { currentPlaybackSource.displayTitle() }
    var openMenu by remember { mutableStateOf<PlayerMenu?>(null) }
    val navigateBack = remember(onNavigateBack) {
        {
            viewModel.saveCurrentProgressAndNavigate(onNavigateBack)
        }
    }

    LaunchedEffect(playbackSource) {
        viewModel.play(playbackSource)
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
            viewModel.stopPlaybackWhenLeaving()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                handlePlayerKey(
                    key = event.key,
                    type = event.type,
                    controlsVisible = controlsVisible,
                    hasOpenMenu = openMenu != null,
                    actions = PlayerKeyActions(
                        skipBackward = {
                            viewModel.skipBackward()
                            viewModel.showControls()
                        },
                        skipForward = {
                            viewModel.skipForward()
                            viewModel.showControls()
                        },
                        togglePlayback = {
                            viewModel.togglePlayback()
                        },
                        resume = {
                            viewModel.resume()
                        },
                        pause = {
                            viewModel.pause()
                        },
                        showControls = {
                            viewModel.showControls()
                        },
                        hideControls = {
                            openMenu = null
                            viewModel.hideControls()
                        },
                        closeMenu = {
                            openMenu = null
                            viewModel.showControls()
                        },
                        navigateBack = navigateBack
                    )
                )
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    viewModel.showControls()
                }
            }
    ) {
        val player = viewModel.getPlayer()
        if (player != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        isClickable = true
                        isFocusable = false
                        isFocusableInTouchMode = false
                        setOnClickListener { viewModel.showControls() }
                    }
                },
                update = { view ->
                    view.player = player
                    view.setOnClickListener { viewModel.showControls() }
                },
                modifier = Modifier.fillMaxSize()
            )
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
                onRetry = {
                    viewModel.showControls()
                    viewModel.play(currentPlaybackSource)
                }
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerChrome(
                title = title,
                sourceLabel = currentPlaybackSource.mediaSourceId,
                playbackState = playbackState,
                currentPosition = currentPosition,
                duration = duration,
                subtitles = availableSubtitles,
                audioTracks = availableAudioTracks,
                playbackSpeed = playbackSpeed,
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
    onBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
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
                onSelectSubtitle = onSelectSubtitle,
                onSelectAudioTrack = onSelectAudioTrack,
                onSelectSpeed = onSelectSpeed,
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

@Composable
private fun PlayerBottomBar(
    currentPosition: Long,
    duration: Long,
    subtitles: List<SubtitleTrack>,
    audioTracks: List<AudioTrack>,
    playbackSpeed: Float,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerInfoChip(
                icon = Icons.Filled.GraphicEq,
                text = playbackLocalSourceLabel()
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
                if (enabled && event.type == KeyEventType.KeyDown && event.key.isTvActivateKey()) {
                    onClick()
                    true
                } else {
                    false
                }
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
                if (enabled && event.type == KeyEventType.KeyDown && event.key.isTvActivateKey()) {
                    onClick()
                    true
                } else {
                    false
                }
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

@Composable
private fun PlayerOptionsPanel(
    menu: PlayerMenu,
    subtitles: List<SubtitleTrack>,
    audioTracks: List<AudioTrack>,
    playbackSpeed: Float,
    onSelectSubtitle: (Int) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstOptionFocus = remember(menu) { FocusRequester() }
    val speeds = remember { playbackSpeedOptions() }

    LaunchedEffect(menu) {
        firstOptionFocus.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(18.dp)
    ) {
        Text(
            text = when (menu) {
                PlayerMenu.Speed -> playbackSpeedMenuTitle()
                PlayerMenu.Subtitles -> playbackSubtitlesMenuTitle()
                PlayerMenu.Audio -> playbackAudioMenuTitle()
            },
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (menu) {
                PlayerMenu.Speed -> speeds.forEachIndexed { index, speed ->
                    PlayerOptionButton(
                        text = playbackSpeedValueLabel(speed),
                        selected = speed == playbackSpeed,
                        onClick = { onSelectSpeed(speed) },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstOptionFocus)
                        } else {
                            Modifier
                        }
                    )
                }
                PlayerMenu.Subtitles -> subtitles.forEachIndexed { index, track ->
                    PlayerOptionButton(
                        text = playbackSubtitleOptionLabel(track, index),
                        selected = false,
                        onClick = { onSelectSubtitle(index) },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstOptionFocus)
                        } else {
                            Modifier
                        }
                    )
                }
                PlayerMenu.Audio -> audioTracks.forEachIndexed { index, track ->
                    PlayerOptionButton(
                        text = playbackAudioOptionLabel(
                            title = track.title,
                            language = track.language,
                            index = index,
                        ),
                        selected = false,
                        onClick = { onSelectAudioTrack(index) },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstOptionFocus)
                        } else {
                            Modifier
                        }
                    )
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
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val background = when {
        selected -> AnimeRed
        isFocused -> AnimeRed.copy(alpha = 0.78f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key.isTvActivateKey()) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .border(
                width = if (isFocused || selected) 2.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = TextPrimary
        ),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        Text(
            text = text,
            color = TextPrimary,
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit
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
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = playbackRetryLabel(),
                    color = Color.White,
                    style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

private enum class PlayerMenu {
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

    if (controlsVisible) {
        return when (event.key) {
            Key.DirectionLeft -> {
                viewModel.skipBackward()
                viewModel.showControls()
                true
            }
            Key.DirectionRight -> {
                viewModel.skipForward()
                viewModel.showControls()
                true
            }
            Key.MediaPlayPause -> {
                viewModel.togglePlayback()
                true
            }
            Key.MediaPlay -> {
                viewModel.resume()
                true
            }
            Key.MediaPause -> {
                viewModel.pause()
                true
            }
            Key.Back -> {
                if (hasOpenMenu) {
                    onCloseMenu()
                } else {
                    onHideControls()
                }
                true
            }
            else -> false
        }
    }

    return when (event.key) {
        Key.DirectionLeft -> {
            viewModel.showControls()
            viewModel.skipBackward()
            true
        }
        Key.DirectionRight -> {
            viewModel.showControls()
            viewModel.skipForward()
            true
        }
        Key.DirectionUp,
        Key.DirectionDown -> {
            viewModel.showControls()
            true
        }
        Key.DirectionCenter,
        Key.Enter,
        Key.NumPadEnter,
        Key.Spacebar,
        Key.MediaPlayPause -> {
            viewModel.showControls()
            viewModel.togglePlayback()
            true
        }
        Key.MediaPlay -> {
            viewModel.showControls()
            viewModel.resume()
            true
        }
        Key.MediaPause -> {
            viewModel.showControls()
            viewModel.pause()
            true
        }
        Key.Back -> {
            if (controlsVisible) {
                viewModel.hideControls()
            } else {
                onNavigateBack()
            }
            true
        }
        else -> false
    }
}

private fun trimSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else "%.2f".format(speed).trimEnd('0')

private fun Key.isActivateKey(): Boolean = this == Key.DirectionCenter ||
    this == Key.Enter ||
    this == Key.NumPadEnter ||
    this == Key.Spacebar
