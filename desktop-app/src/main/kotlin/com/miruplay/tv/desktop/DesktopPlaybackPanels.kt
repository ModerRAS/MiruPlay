package com.miruplay.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.formatPlaybackPosition
import com.miruplay.tv.player.mpv.RifeBackend
import kotlin.math.roundToLong

@Composable
internal fun PlaybackPanel(
    mediaPath: String,
    onMediaPathChange: (String) -> Unit,
    subtitlePath: String,
    onSubtitlePathChange: (String) -> Unit,
    startSeconds: String,
    onStartSecondsChange: (String) -> Unit,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    keepOpen: Boolean,
    onKeepOpenChange: (Boolean) -> Unit,
    rifeEnabled: Boolean,
    onRifeEnabledChange: (Boolean) -> Unit,
    rifeBackend: RifeBackend,
    onRifeBackendChange: (RifeBackend) -> Unit,
    isPlayerActive: Boolean,
    launchStatus: String,
    onBackToDetails: () -> Unit,
    onLaunch: () -> Unit,
    onTogglePause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    requestedSettingsFocusVersion: Int = 0,
    requestedSettingsFocusTarget: PlaybackSettingFocusTarget = PlaybackSettingFocusTarget.MediaPath,
    onFocusNextPanel: () -> Boolean = { false },
) {
    var stageFocusVersion by remember { mutableIntStateOf(0) }
    var settingsFocusVersion by remember { mutableIntStateOf(0) }
    var settingsFocusTarget by remember { mutableStateOf(PlaybackSettingFocusTarget.MediaPath) }
    fun requestSettingsFocus(target: PlaybackSettingFocusTarget): Boolean {
        settingsFocusTarget = target
        settingsFocusVersion += 1
        return true
    }
    LaunchedEffect(requestedSettingsFocusVersion) {
        if (requestedSettingsFocusVersion > 0) {
            requestSettingsFocus(requestedSettingsFocusTarget)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        DesktopPlayerStage(
            mediaPath = mediaPath,
            startSeconds = startSeconds,
            rifeEnabled = rifeEnabled,
            rifeBackend = rifeBackend,
            isPlayerActive = isPlayerActive,
            launchStatus = launchStatus,
            onBackToDetails = onBackToDetails,
            onLaunch = onLaunch,
            onTogglePause = onTogglePause,
            onSeekBack = onSeekBack,
            onSeekForward = onSeekForward,
            onStop = onStop,
            focusVersion = stageFocusVersion,
            onFocusNextPanel = {
                requestSettingsFocus(PlaybackSettingFocusTarget.MediaPath)
            },
        )
        PlaybackSettingsPanel(
            mediaPath = mediaPath,
            onMediaPathChange = onMediaPathChange,
            subtitlePath = subtitlePath,
            onSubtitlePathChange = onSubtitlePathChange,
            startSeconds = startSeconds,
            onStartSecondsChange = onStartSecondsChange,
            fullscreen = fullscreen,
            onFullscreenChange = onFullscreenChange,
            keepOpen = keepOpen,
            onKeepOpenChange = onKeepOpenChange,
            rifeEnabled = rifeEnabled,
            onRifeEnabledChange = onRifeEnabledChange,
            rifeBackend = rifeBackend,
            onRifeBackendChange = onRifeBackendChange,
            focusVersion = settingsFocusVersion,
            focusTarget = settingsFocusTarget,
            onFocusPreviousPanel = {
                stageFocusVersion += 1
                true
            },
            onFocusNextPanel = onFocusNextPanel,
        )
    }
}

@Composable
private fun DesktopPlayerStage(
    mediaPath: String,
    startSeconds: String,
    rifeEnabled: Boolean,
    rifeBackend: RifeBackend,
    isPlayerActive: Boolean,
    launchStatus: String,
    onBackToDetails: () -> Unit,
    onLaunch: () -> Unit,
    onTogglePause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onStop: () -> Unit,
    focusVersion: Int = 0,
    onFocusNextPanel: () -> Boolean = { false },
) {
    val title = desktopPlaybackTitle(mediaPath)
    val backToDetailsFocusRequester = remember { FocusRequester() }
    val primaryTransportFocusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(playerStageBrush(title))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.86f),
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.90f),
                        ),
                    ),
                ),
        )
        PlayerStageTopBar(
            title = title,
            subtitle = desktopPlaybackSourceLine(
                mediaPath = mediaPath,
                rifeEnabled = rifeEnabled,
                rifeBackend = rifeBackend,
                isPlayerActive = isPlayerActive,
            ),
            isPlayerActive = isPlayerActive,
            onBackToDetails = onBackToDetails,
            focusRequester = backToDetailsFocusRequester,
            onFocusTransport = { primaryTransportFocusRequester.requestFocus() },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        PlayerTransportControls(
            isPlayerActive = isPlayerActive,
            onLaunch = onLaunch,
            onTogglePause = onTogglePause,
            onSeekBack = onSeekBack,
            onSeekForward = onSeekForward,
            onStop = onStop,
            primaryFocusRequester = primaryTransportFocusRequester,
            onFocusBackToDetails = { backToDetailsFocusRequester.requestFocus() },
            onFocusNextPanel = onFocusNextPanel,
            focusVersion = focusVersion,
            modifier = Modifier.align(Alignment.Center),
        )
        PlayerStageBottomBar(
            startSeconds = startSeconds,
            rifeEnabled = rifeEnabled,
            rifeBackend = rifeBackend,
            launchStatus = launchStatus,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PlayerStageTopBar(
    title: String,
    subtitle: String,
    isPlayerActive: Boolean,
    onBackToDetails: () -> Unit,
    focusRequester: FocusRequester,
    onFocusTransport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        TvActionButton(
            "返回详情",
            onClick = onBackToDetails,
            secondary = true,
            modifier = Modifier
                .width(132.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (desktopPlayerStageNavigationTarget(DesktopPlayerStageFocusTarget.BackToDetails, event.key, isPlayerActive)) {
                            DesktopPlayerStageFocusTarget.Primary -> {
                                onFocusTransport()
                                true
                            }
                            else -> false
                        }
                    }
                },
        )
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PlayerInfoChip(desktopPlaybackStatusChip(isPlayerActive))
    }
}

@Composable
private fun PlayerTransportControls(
    isPlayerActive: Boolean,
    onLaunch: () -> Unit,
    onTogglePause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onStop: () -> Unit,
    primaryFocusRequester: FocusRequester,
    onFocusBackToDetails: () -> Unit,
    onFocusNextPanel: () -> Boolean,
    focusVersion: Int,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember(primaryFocusRequester) {
        DesktopPlayerStageFocusTarget.entries.associateWith { target ->
            if (target == DesktopPlayerStageFocusTarget.Primary) primaryFocusRequester else FocusRequester()
        }
    }
    LaunchedEffect(isPlayerActive, focusVersion) {
        focusRequesters.getValue(DesktopPlayerStageFocusTarget.Primary).requestFocus()
    }

    fun moveFocus(current: DesktopPlayerStageFocusTarget, key: Key): Boolean {
        val target = desktopPlayerStageNavigationTarget(current, key, isPlayerActive) ?: return false
        if (target == DesktopPlayerStageFocusTarget.BackToDetails) {
            onFocusBackToDetails()
        } else if (target == DesktopPlayerStageFocusTarget.NextPanel) {
            return onFocusNextPanel()
        } else {
            focusRequesters.getValue(target).requestFocus()
        }
        return true
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerRoundButton(
            "-10",
            onClick = onSeekBack,
            size = 64.dp,
            enabled = isPlayerActive,
            onNavigationKey = { key -> moveFocus(DesktopPlayerStageFocusTarget.SeekBack, key) },
            modifier = Modifier.focusRequester(focusRequesters.getValue(DesktopPlayerStageFocusTarget.SeekBack)),
        )
        PlayerPrimaryButton(
            isPlayerActive = isPlayerActive,
            onLaunch = onLaunch,
            onTogglePause = onTogglePause,
            onNavigationKey = { key -> moveFocus(DesktopPlayerStageFocusTarget.Primary, key) },
            modifier = Modifier.focusRequester(focusRequesters.getValue(DesktopPlayerStageFocusTarget.Primary)),
        )
        PlayerRoundButton(
            "+30",
            onClick = onSeekForward,
            size = 64.dp,
            enabled = isPlayerActive,
            onNavigationKey = { key -> moveFocus(DesktopPlayerStageFocusTarget.SeekForward, key) },
            modifier = Modifier.focusRequester(focusRequesters.getValue(DesktopPlayerStageFocusTarget.SeekForward)),
        )
        PlayerRoundButton(
            "停止",
            onClick = onStop,
            size = 64.dp,
            enabled = isPlayerActive,
            onNavigationKey = { key -> moveFocus(DesktopPlayerStageFocusTarget.Stop, key) },
            modifier = Modifier.focusRequester(focusRequesters.getValue(DesktopPlayerStageFocusTarget.Stop)),
        )
    }
}

@Composable
private fun PlayerPrimaryButton(
    isPlayerActive: Boolean,
    onLaunch: () -> Unit,
    onTogglePause: () -> Unit,
    onNavigationKey: (Key) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(90.dp)
            .clip(CircleShape)
            .background(if (focused) AnimeRed.copy(alpha = 0.92f) else AnimeRed)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.20f),
                shape = CircleShape,
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        -> {
                            if (isPlayerActive) onTogglePause() else onLaunch()
                            true
                        }
                        else -> onNavigationKey(event.key)
                    }
                }
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = if (isPlayerActive) onTogglePause else onLaunch,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlayerActive) {
            Text("暂停", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
    }
}

@Composable
private fun PlayerRoundButton(
    text: String,
    onClick: () -> Unit,
    size: Dp,
    enabled: Boolean = true,
    onNavigationKey: (Key) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val background = when {
        !enabled -> Color.White.copy(alpha = 0.08f)
        focused -> AnimeRed.copy(alpha = 0.84f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.18f),
                shape = CircleShape,
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        -> {
                            if (enabled) onClick()
                            enabled
                        }
                        else -> onNavigationKey(event.key)
                    }
                }
            }
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.55f),
            fontSize = if (text.length <= 3) 18.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal enum class DesktopPlayerStageFocusTarget {
    BackToDetails,
    SeekBack,
    Primary,
    SeekForward,
    Stop,
    NextPanel,
}

internal fun desktopPlayerTransportTargets(isPlayerActive: Boolean): List<DesktopPlayerStageFocusTarget> =
    listOfNotNull(
        DesktopPlayerStageFocusTarget.SeekBack.takeIf { isPlayerActive },
        DesktopPlayerStageFocusTarget.Primary,
        DesktopPlayerStageFocusTarget.SeekForward.takeIf { isPlayerActive },
        DesktopPlayerStageFocusTarget.Stop.takeIf { isPlayerActive },
    )

internal fun desktopPlayerStageNavigationTarget(
    current: DesktopPlayerStageFocusTarget,
    key: Key,
    isPlayerActive: Boolean,
): DesktopPlayerStageFocusTarget? =
    when (key) {
        Key.DirectionUp -> DesktopPlayerStageFocusTarget.BackToDetails.takeIf { current in desktopPlayerTransportTargets(isPlayerActive) }
        Key.DirectionDown -> DesktopPlayerStageFocusTarget.Primary.takeIf { current == DesktopPlayerStageFocusTarget.BackToDetails }
            ?: DesktopPlayerStageFocusTarget.NextPanel.takeIf { current in desktopPlayerTransportTargets(isPlayerActive) }
        Key.DirectionLeft -> current.transportStep(delta = -1, isPlayerActive = isPlayerActive)
        Key.DirectionRight -> current.transportStep(delta = 1, isPlayerActive = isPlayerActive)
        else -> null
    }

private fun DesktopPlayerStageFocusTarget.transportStep(
    delta: Int,
    isPlayerActive: Boolean,
): DesktopPlayerStageFocusTarget? {
    val targets = desktopPlayerTransportTargets(isPlayerActive)
    val currentIndex = targets.indexOf(this)
    if (currentIndex < 0) return null
    return targets.getOrNull(currentIndex + delta)
}

internal enum class PlaybackSettingFocusTarget {
    MediaPath,
    StartSeconds,
    SubtitlePath,
    Fullscreen,
    KeepOpen,
    RifeToggle,
    RifeBackend,
    PreviousPanel,
    NextPanel,
}

private val playbackSettingFocusableTargets = listOf(
    PlaybackSettingFocusTarget.MediaPath,
    PlaybackSettingFocusTarget.StartSeconds,
    PlaybackSettingFocusTarget.SubtitlePath,
    PlaybackSettingFocusTarget.Fullscreen,
    PlaybackSettingFocusTarget.KeepOpen,
    PlaybackSettingFocusTarget.RifeToggle,
    PlaybackSettingFocusTarget.RifeBackend,
)

private val playbackSettingToggleTargets = listOf(
    PlaybackSettingFocusTarget.Fullscreen,
    PlaybackSettingFocusTarget.KeepOpen,
    PlaybackSettingFocusTarget.RifeToggle,
    PlaybackSettingFocusTarget.RifeBackend,
)

internal fun playbackSettingNavigationTarget(
    current: PlaybackSettingFocusTarget,
    key: Key,
): PlaybackSettingFocusTarget? =
    when (current) {
        PlaybackSettingFocusTarget.MediaPath -> when (key) {
            Key.DirectionRight -> PlaybackSettingFocusTarget.StartSeconds
            Key.DirectionDown -> PlaybackSettingFocusTarget.SubtitlePath
            Key.DirectionUp -> PlaybackSettingFocusTarget.PreviousPanel
            else -> null
        }
        PlaybackSettingFocusTarget.StartSeconds -> when (key) {
            Key.DirectionLeft -> PlaybackSettingFocusTarget.MediaPath
            Key.DirectionDown -> PlaybackSettingFocusTarget.SubtitlePath
            Key.DirectionUp -> PlaybackSettingFocusTarget.PreviousPanel
            else -> null
        }
        PlaybackSettingFocusTarget.SubtitlePath -> when (key) {
            Key.DirectionUp -> PlaybackSettingFocusTarget.MediaPath
            Key.DirectionDown -> PlaybackSettingFocusTarget.Fullscreen
            else -> null
        }
        PlaybackSettingFocusTarget.Fullscreen,
        PlaybackSettingFocusTarget.KeepOpen,
        PlaybackSettingFocusTarget.RifeToggle,
        PlaybackSettingFocusTarget.RifeBackend,
        -> when (key) {
            Key.DirectionLeft -> current.playbackSettingToggleStep(delta = -1)
            Key.DirectionRight -> current.playbackSettingToggleStep(delta = 1)
            Key.DirectionUp -> PlaybackSettingFocusTarget.SubtitlePath
            Key.DirectionDown -> PlaybackSettingFocusTarget.NextPanel
            else -> null
        }
        PlaybackSettingFocusTarget.PreviousPanel,
        PlaybackSettingFocusTarget.NextPanel,
        -> null
    }

private fun PlaybackSettingFocusTarget.playbackSettingToggleStep(delta: Int): PlaybackSettingFocusTarget? {
    val targets = playbackSettingToggleTargets
    val currentIndex = targets.indexOf(this)
    if (currentIndex < 0) return null
    return targets.getOrNull(currentIndex + delta)
}

private fun Modifier.playbackSettingNavigation(
    target: PlaybackSettingFocusTarget,
    focusRequester: FocusRequester,
    onMove: (PlaybackSettingFocusTarget, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            event.type == KeyEventType.KeyDown && onMove(target, event.key)
        }

internal enum class RuntimeFocusTarget {
    MpvPath,
    ConfigDir,
    CheckRuntime,
    PreviousPanel,
}

private val runtimeFocusableTargets = listOf(
    RuntimeFocusTarget.MpvPath,
    RuntimeFocusTarget.ConfigDir,
    RuntimeFocusTarget.CheckRuntime,
)

internal fun runtimeNavigationTarget(
    current: RuntimeFocusTarget,
    key: Key,
): RuntimeFocusTarget? =
    when (key) {
        Key.DirectionUp -> current.runtimeStep(delta = -1)
            ?: RuntimeFocusTarget.PreviousPanel.takeIf { current == RuntimeFocusTarget.MpvPath }
        Key.DirectionDown -> current.runtimeStep(delta = 1)
        else -> null
    }

private fun RuntimeFocusTarget.runtimeStep(delta: Int): RuntimeFocusTarget? {
    val targets = runtimeFocusableTargets
    val currentIndex = targets.indexOf(this)
    if (currentIndex < 0) return null
    return targets.getOrNull(currentIndex + delta)
}

private fun Modifier.runtimeNavigation(
    target: RuntimeFocusTarget,
    focusRequester: FocusRequester,
    onMove: (RuntimeFocusTarget, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            event.type == KeyEventType.KeyDown && onMove(target, event.key)
        }

@Composable
private fun PlayerStageBottomBar(
    startSeconds: String,
    rifeEnabled: Boolean,
    rifeBackend: RifeBackend,
    launchStatus: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(start = 36.dp, end = 36.dp, top = 18.dp, bottom = 26.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeText(desktopPlaybackStartPositionLabel(startSeconds))
            PlaybackTimeline(
                progress = 0f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp),
            )
            TimeText("--:--")
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerInfoChip(if (rifeEnabled) "RIFE ${rifeBackend.name}" else "RIFE 关闭")
            PlayerInfoChip("字幕外载")
            Text(
                desktopPlaybackStatusText(launchStatus),
                color = TextSecondary,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlaybackTimeline(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.20f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(8.dp))
                .background(AnimeRed),
        )
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PlayerInfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(MiruPlayUiMetrics.ACTION_BUTTON_RADIUS_DP.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(MiruPlayUiMetrics.ACTION_BUTTON_RADIUS_DP.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = TextPrimary, fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlaybackSettingsPanel(
    mediaPath: String,
    onMediaPathChange: (String) -> Unit,
    subtitlePath: String,
    onSubtitlePathChange: (String) -> Unit,
    startSeconds: String,
    onStartSecondsChange: (String) -> Unit,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    keepOpen: Boolean,
    onKeepOpenChange: (Boolean) -> Unit,
    rifeEnabled: Boolean,
    onRifeEnabledChange: (Boolean) -> Unit,
    rifeBackend: RifeBackend,
    onRifeBackendChange: (RifeBackend) -> Unit,
    focusVersion: Int = 0,
    focusTarget: PlaybackSettingFocusTarget = PlaybackSettingFocusTarget.MediaPath,
    onFocusPreviousPanel: () -> Boolean = { false },
    onFocusNextPanel: () -> Boolean = { false },
) {
    val labels = desktopPlaybackUiLabels()
    val settingFocusRequesters = remember {
        playbackSettingFocusableTargets.associateWith { FocusRequester() }
    }
    fun movePlaybackSettingFocus(target: PlaybackSettingFocusTarget, key: Key): Boolean {
        val next = playbackSettingNavigationTarget(target, key) ?: return false
        if (next == PlaybackSettingFocusTarget.PreviousPanel) return onFocusPreviousPanel()
        if (next == PlaybackSettingFocusTarget.NextPanel) return onFocusNextPanel()
        settingFocusRequesters.getValue(next).requestFocus()
        return true
    }
    LaunchedEffect(focusVersion) {
        if (focusVersion > 0) {
            settingFocusRequesters.getValue(focusTarget).requestFocus()
        }
    }
    TvPanel(Modifier.fillMaxWidth()) {
        Text("播放设置", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
            LabeledTextField(
                labels.mediaPath,
                mediaPath,
                onValueChange = onMediaPathChange,
                modifier = Modifier.weight(1.6f),
                inputModifier = Modifier.playbackSettingNavigation(
                    target = PlaybackSettingFocusTarget.MediaPath,
                    focusRequester = settingFocusRequesters.getValue(PlaybackSettingFocusTarget.MediaPath),
                    onMove = ::movePlaybackSettingFocus,
                ),
            )
            LabeledTextField(
                labels.startSeconds,
                startSeconds,
                onValueChange = onStartSecondsChange,
                modifier = Modifier.weight(0.42f),
                inputModifier = Modifier.playbackSettingNavigation(
                    target = PlaybackSettingFocusTarget.StartSeconds,
                    focusRequester = settingFocusRequesters.getValue(PlaybackSettingFocusTarget.StartSeconds),
                    onMove = ::movePlaybackSettingFocus,
                ),
            )
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        LabeledTextField(
            labels.subtitlePath,
            subtitlePath,
            onValueChange = onSubtitlePathChange,
            inputModifier = Modifier.playbackSettingNavigation(
                target = PlaybackSettingFocusTarget.SubtitlePath,
                focusRequester = settingFocusRequesters.getValue(PlaybackSettingFocusTarget.SubtitlePath),
                onMove = ::movePlaybackSettingFocus,
            ),
        )
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
        ) {
            ToggleRow(
                labels.fullscreen,
                fullscreen,
                onFullscreenChange,
                modifier = Modifier.playbackSettingNavigation(
                    target = PlaybackSettingFocusTarget.Fullscreen,
                    focusRequester = settingFocusRequesters.getValue(PlaybackSettingFocusTarget.Fullscreen),
                    onMove = ::movePlaybackSettingFocus,
                ),
            )
            ToggleRow(
                labels.keepOpen,
                keepOpen,
                onKeepOpenChange,
                modifier = Modifier.playbackSettingNavigation(
                    target = PlaybackSettingFocusTarget.KeepOpen,
                    focusRequester = settingFocusRequesters.getValue(PlaybackSettingFocusTarget.KeepOpen),
                    onMove = ::movePlaybackSettingFocus,
                ),
            )
            ToggleRow(
                labels.rife,
                rifeEnabled,
                onRifeEnabledChange,
                modifier = Modifier.playbackSettingNavigation(
                    target = PlaybackSettingFocusTarget.RifeToggle,
                    focusRequester = settingFocusRequesters.getValue(PlaybackSettingFocusTarget.RifeToggle),
                    onMove = ::movePlaybackSettingFocus,
                ),
            )
            RifeBackendPicker(
                rifeBackend,
                onSelected = onRifeBackendChange,
                modifier = Modifier.playbackSettingNavigation(
                    target = PlaybackSettingFocusTarget.RifeBackend,
                    focusRequester = settingFocusRequesters.getValue(PlaybackSettingFocusTarget.RifeBackend),
                    onMove = ::movePlaybackSettingFocus,
                ),
            )
        }
    }
}

@Composable
internal fun RuntimePanel(
    mpvPath: String,
    onMpvPathChange: (String) -> Unit,
    configDir: String,
    onConfigDirChange: (String) -> Unit,
    status: String,
    onCheckRuntime: () -> Unit,
    modifier: Modifier = Modifier,
    focusVersion: Int = 0,
    onFocusPreviousPanel: () -> Boolean = { false },
) {
    val runtimeFocusRequesters = remember {
        runtimeFocusableTargets.associateWith { FocusRequester() }
    }
    fun moveRuntimeFocus(target: RuntimeFocusTarget, key: Key): Boolean {
        val next = runtimeNavigationTarget(target, key) ?: return false
        if (next == RuntimeFocusTarget.PreviousPanel) return onFocusPreviousPanel()
        runtimeFocusRequesters.getValue(next).requestFocus()
        return true
    }
    LaunchedEffect(focusVersion) {
        if (focusVersion > 0) {
            runtimeFocusRequesters.getValue(RuntimeFocusTarget.MpvPath).requestFocus()
        }
    }
    TvPanel(modifier) {
        Text("运行时", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        LabeledTextField(
            "mpv.exe",
            mpvPath,
            onValueChange = onMpvPathChange,
            inputModifier = Modifier.runtimeNavigation(
                target = RuntimeFocusTarget.MpvPath,
                focusRequester = runtimeFocusRequesters.getValue(RuntimeFocusTarget.MpvPath),
                onMove = ::moveRuntimeFocus,
            ),
        )
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        LabeledTextField(
            "portable_config",
            configDir,
            onValueChange = onConfigDirChange,
            inputModifier = Modifier.runtimeNavigation(
                target = RuntimeFocusTarget.ConfigDir,
                focusRequester = runtimeFocusRequesters.getValue(RuntimeFocusTarget.ConfigDir),
                onMove = ::moveRuntimeFocus,
            ),
        )
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        StatusBox(desktopRuntimeStatusText(status))
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        TvActionButton(
            "检查运行时",
            onClick = onCheckRuntime,
            modifier = Modifier.runtimeNavigation(
                target = RuntimeFocusTarget.CheckRuntime,
                focusRequester = runtimeFocusRequesters.getValue(RuntimeFocusTarget.CheckRuntime),
                onMove = ::moveRuntimeFocus,
            ),
        )
    }
}

@Composable
internal fun CommandPanel(
    commandPreview: String,
    launchStatus: String,
    modifier: Modifier = Modifier,
) {
    TvPanel(modifier) {
        Text("mpv 诊断", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        StatusBox(desktopPlaybackStatusText(launchStatus))
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .border(1.dp, Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
                .padding(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
        ) {
            Text(
                commandPreview,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                lineHeight = 18.sp,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun desktopPlaybackTitle(mediaPath: String): String {
    val trimmed = mediaPath.trim()
    if (trimmed.isBlank()) return "选择媒体"
    return MediaPathConventions.stem(trimmed).takeIf { it.isNotBlank() }
        ?: trimmed.substringAfterLast('/').substringAfterLast('\\').ifBlank { trimmed }
}

internal data class DesktopPlaybackUiLabels(
    val mediaPath: String,
    val startSeconds: String,
    val subtitlePath: String,
    val fullscreen: String,
    val keepOpen: String,
    val rife: String,
)

internal fun desktopPlaybackUiLabels(): DesktopPlaybackUiLabels =
    DesktopPlaybackUiLabels(
        mediaPath = "媒体 URI 或文件路径",
        startSeconds = "起播秒数",
        subtitlePath = "外挂字幕路径",
        fullscreen = "全屏",
        keepOpen = "播完保留窗口",
        rife = "RIFE",
    )

internal fun desktopPlaybackStatusChip(isPlayerActive: Boolean): String =
    if (isPlayerActive) "mpv 播放中" else "mpv 待命"

internal fun desktopPlaybackStatusText(status: String): String {
    val trimmed = status.trim()
    return when {
        trimmed.isBlank() -> "mpv 待命。"
        trimmed == "mpv is idle." -> "mpv 待命。"
        trimmed.startsWith("mpv launched: pid ") -> "mpv 已启动：pid ${trimmed.removePrefix("mpv launched: pid ")}"
        trimmed == "Unable to launch mpv." -> "无法启动 mpv。"
        trimmed == "No mpv process is active." -> "没有正在运行的 mpv 进程。"
        trimmed == "mpv pause toggled." -> "已切换暂停状态。"
        trimmed.startsWith("mpv seeked back ") && trimmed.endsWith("s.") ->
            "已后退 ${trimmed.removePrefix("mpv seeked back ").removeSuffix("s.")} 秒。"
        trimmed.startsWith("mpv seeked forward ") && trimmed.endsWith("s.") ->
            "已快进 ${trimmed.removePrefix("mpv seeked forward ").removeSuffix("s.")} 秒。"
        trimmed == "mpv stopped." -> "mpv 已停止。"
        trimmed.startsWith("mpv position synced at ") && trimmed.endsWith(".") ->
            "播放进度已同步至 ${trimmed.removePrefix("mpv position synced at ").removeSuffix(".")}。"
        trimmed.startsWith("播放出错：mpv executable not found: ") ->
            trimmed.removePrefix("播放出错：").localizedMissingMpvExecutableMessage(prefix = "播放出错：")
        trimmed.startsWith("播放出错：RIFE is enabled but script was not found: ") ->
            trimmed.removePrefix("播放出错：").localizedMissingRifeScriptMessage(prefix = "播放出错：")
        trimmed == "播放出错：RIFE is enabled but configDirectory is empty. Set portable_config, choose a runtime root, or turn RIFE off." ->
            "播放出错：已开启 RIFE，但 portable_config 为空。请设置 portable_config、选择运行时目录，或关闭 RIFE。"
        trimmed == "Choose a media URI or file path before launching mpv." -> "请先选择媒体，再启动 mpv。"
        trimmed == "Unable to build mpv command." -> "无法生成 mpv 命令。"
        else -> trimmed
    }
}

internal fun desktopRuntimeStatusText(status: String): String =
    status.trim()
        .takeIf { it.isNotBlank() }
        ?.lineSequence()
        ?.joinToString(separator = "\n") { line -> desktopRuntimeStatusLine(line.trim()) }
        ?: "尚未检查运行时。"

private fun desktopRuntimeStatusLine(line: String): String {
    val marker = " Manifest: present."
    val hasManifest = line.endsWith(marker)
    val body = if (hasManifest) line.removeSuffix(marker) else line
    val suffix = if (hasManifest) "清单：已发现。" else ""
    return when {
        body.startsWith("Bundled mpv runtime is ready. RIFE: ") ->
            "内置 mpv 运行时已就绪。RIFE：${
                body.removePrefix("Bundled mpv runtime is ready. RIFE: ").removeSuffix(".").localizedRifeBackends()
            }。$suffix"
        body == "mpv runtime is playable. RIFE scripts are missing; leave RIFE off or prepare a RIFE backend." ->
            "mpv 运行时可播放。缺少 RIFE 脚本；请关闭 RIFE 或准备 RIFE 后端。$suffix"
        body.startsWith("mpv runtime is playable. Runtime manifest entries are missing or invalid: ") ->
            "mpv 运行时可播放，但运行时清单声明的条目缺失或无效：${
                body.removePrefix("mpv runtime is playable. Runtime manifest entries are missing or invalid: ").removeSuffix(".")
            }。$suffix"
        body.startsWith("mpv runtime is playable. Missing optional files: ") ->
            "mpv 运行时可播放。缺少可选文件：${
                body.removePrefix("mpv runtime is playable. Missing optional files: ").removeSuffix(".")
            }。$suffix"
        body.startsWith("mpv runtime is incomplete. Missing: ") ->
            "mpv 运行时不完整。缺少：${
                body.removePrefix("mpv runtime is incomplete. Missing: ").removeSuffix(".")
            }。$suffix"
        body.startsWith("Runtime check failed: ") ->
            "运行时检查失败：${body.removePrefix("Runtime check failed: ")}"
        body == "Runtime manifest" -> "运行时清单"
        body.startsWith("Verified at: ") -> "验证时间：${body.removePrefix("Verified at: ")}"
        body.startsWith("Source: ") -> "来源：${body.removePrefix("Source: ")}"
        body.startsWith("Overlay source: ") -> "叠加包来源：${body.removePrefix("Overlay source: ")}"
        body.startsWith("Runtime root: ") -> "运行时目录：${body.removePrefix("Runtime root: ")}"
        body.startsWith("Required RIFE: ") -> "要求的 RIFE：${body.removePrefix("Required RIFE: ").localizedRifeBackends()}"
        body.startsWith("Manifest files: ") -> "清单文件：${body.removePrefix("Manifest files: ")}"
        else -> line
    }
}

private fun String.localizedRifeBackends(): String =
    if (equals("none", ignoreCase = true)) "无" else this

private fun String.localizedMissingMpvExecutableMessage(prefix: String = ""): String {
    val path = removePrefix("mpv executable not found: ")
        .removeSuffix(". Choose the bundled runtime path, install mpv, or run Check runtime before launching.")
    return "${prefix}找不到 mpv.exe：$path。请选择内置运行时路径、安装 mpv，或先检查运行时。"
}

private fun String.localizedMissingRifeScriptMessage(prefix: String = ""): String {
    val path = removePrefix("RIFE is enabled but script was not found: ")
        .removeSuffix(". Pick an installed backend, prepare the bundled runtime, or turn RIFE off.")
    return "${prefix}已开启 RIFE，但找不到脚本：$path。请选择已安装后端、准备内置运行时，或关闭 RIFE。"
}

internal fun desktopPlaybackSourceLine(
    mediaPath: String,
    rifeEnabled: Boolean,
    rifeBackend: RifeBackend,
    isPlayerActive: Boolean,
): String {
    val source = when {
        mediaPath.isBlank() -> "等待媒体"
        mediaPath.startsWith("http://", ignoreCase = true) || mediaPath.startsWith("https://", ignoreCase = true) -> "远程串流"
        else -> "本地播放"
    }
    val runtime = if (isPlayerActive) "mpv 运行中" else "mpv 待命"
    val rife = if (rifeEnabled) "RIFE ${rifeBackend.name}" else "RIFE 关闭"
    return "$source · $runtime · $rife"
}

internal fun desktopPlaybackStartPositionLabel(startSeconds: String): String {
    val startMs = startSeconds.trim()
        .toDoubleOrNull()
        ?.takeIf { it > 0.0 }
        ?.let { (it * 1000.0).roundToLong() }
        ?: 0L
    return formatPlaybackPosition(startMs)
}

private fun playerStageBrush(title: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFF111827), Color(0xFF7C1D2F), Color(0xFF030306)),
        listOf(Color(0xFF062C3F), Color(0xFF263A75), Color(0xFF030306)),
        listOf(Color(0xFF34210E), Color(0xFF5C253A), Color(0xFF030306)),
    )
    return Brush.horizontalGradient(palettes[Math.floorMod(title.hashCode(), palettes.size)])
}
