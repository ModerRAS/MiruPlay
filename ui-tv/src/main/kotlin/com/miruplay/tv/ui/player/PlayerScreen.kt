package com.miruplay.tv.ui.player

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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
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
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.player.AudioTrack
import com.miruplay.tv.ui.theme.AnimeRed
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.FocusBorder
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography
import kotlinx.coroutines.delay
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
            icon = Icons.Filled.ArrowBack,
            label = "返回",
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
            label = "快退 10 秒",
            onClick = onSkipBackward,
            size = 62.dp
        )
        PlayerIconButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            label = if (isPlaying) "暂停" else "播放",
            onClick = onTogglePlayback,
            size = 82.dp,
            modifier = Modifier.focusRequester(playFocusRequester),
            prominent = true,
            enabled = !isLoading
        )
        PlayerIconButton(
            icon = Icons.Filled.FastForward,
            label = "快进 30 秒",
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
            TimeText(formatTime(currentPosition))
            PlaybackTimeline(
                progress = if (duration > 0L) {
                    currentPosition.toFloat() / duration.toFloat()
                } else {
                    0f
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp)
            )
            TimeText(formatTime(duration))
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerInfoChip(
                icon = Icons.Filled.GraphicEq,
                text = "本地播放"
            )
            PlayerActionChip(
                icon = Icons.Filled.Speed,
                text = "倍速 ${trimSpeed(playbackSpeed)}x",
                selected = openMenu == PlayerMenu.Speed,
                onClick = { onOpenMenu(PlayerMenu.Speed) }
            )
            PlayerActionChip(
                icon = Icons.Filled.Subtitles,
                text = if (subtitles.isEmpty()) "无字幕" else "字幕 ${subtitles.size}",
                selected = openMenu == PlayerMenu.Subtitles,
                enabled = subtitles.isNotEmpty(),
                onClick = { onOpenMenu(PlayerMenu.Subtitles) }
            )
            PlayerActionChip(
                icon = Icons.Filled.Audiotrack,
                text = if (audioTracks.isEmpty()) "音轨 0" else "音轨 ${audioTracks.size}",
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
                if (enabled && event.type == KeyEventType.KeyDown && event.key.isActivateKey()) {
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
                if (enabled && event.type == KeyEventType.KeyDown && event.key.isActivateKey()) {
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
    val speeds = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }

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
                PlayerMenu.Speed -> "播放速度"
                PlayerMenu.Subtitles -> "字幕"
                PlayerMenu.Audio -> "音轨"
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
                        text = "${trimSpeed(speed)}x",
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
                        text = track.title.ifBlank { track.language.ifBlank { "字幕 ${index + 1}" } },
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
                        text = track.title ?: track.language.ifBlank { "音轨 ${index + 1}" },
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
                if (event.type == KeyEventType.KeyDown && event.key.isActivateKey()) {
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
                text = "播放失败",
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
                    text = "重试",
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

private fun PlaybackSource.displayTitle(): String {
    val name = uri.substringAfterLast("/").substringBefore("?").substringBeforeLast(".")
    return runCatching {
        URLDecoder.decode(name, StandardCharsets.UTF_8.name())
    }.getOrDefault(name).ifBlank { mediaSourceId }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun trimSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else "%.2f".format(speed).trimEnd('0')

private fun Key.isActivateKey(): Boolean = this == Key.DirectionCenter ||
    this == Key.Enter ||
    this == Key.NumPadEnter ||
    this == Key.Spacebar
