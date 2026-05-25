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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayFocusAxis
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.MiruPlayPlaybackInputAction
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.design.desktopPlaybackGlobalMediaAction
import com.miruplay.tv.design.desktopPlaybackStageAction
import com.miruplay.tv.design.focusTargetAfter
import com.miruplay.tv.design.horizontalNavigationDelta
import com.miruplay.tv.design.verticalNavigationDelta
import com.miruplay.tv.model.PlaybackTimingConventions
import com.miruplay.tv.model.formatPlaybackPosition
import com.miruplay.tv.model.mpvPlaybackSourceLine
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.mpvPlaybackStatusText
import com.miruplay.tv.model.playbackBackToDetailsLabel
import com.miruplay.tv.model.playbackCheckRuntimeActionLabel
import com.miruplay.tv.model.playbackChooseMediaLabel
import com.miruplay.tv.model.playbackDiagnosticsTitleLabel
import com.miruplay.tv.model.playbackEndActionLabel
import com.miruplay.tv.model.playbackEndSettingsDescriptionLabel
import com.miruplay.tv.model.playbackEndSettingsTitleLabel
import com.miruplay.tv.model.playbackExternalSubtitleLabel
import com.miruplay.tv.model.playbackMediaTitle
import com.miruplay.tv.model.playbackMpvExecutableFieldLabel
import com.miruplay.tv.model.playbackMpvRuntimeStateLabel
import com.miruplay.tv.model.playbackPauseLabel
import com.miruplay.tv.model.playbackPortableConfigFieldLabel
import com.miruplay.tv.model.playbackRifeStateLabel
import com.miruplay.tv.model.playbackRuntimeStatusText
import com.miruplay.tv.model.playbackRuntimeTitleLabel
import com.miruplay.tv.model.playbackSeekBackCompactLabel
import com.miruplay.tv.model.playbackSeekForwardCompactLabel
import com.miruplay.tv.model.playbackSettingsTitleLabel
import com.miruplay.tv.model.playbackStartPositionLabel
import com.miruplay.tv.model.playbackSpeedChipLabel
import com.miruplay.tv.model.playbackSpeedMenuTitle
import com.miruplay.tv.model.playbackSpeedOptions
import com.miruplay.tv.model.playbackSpeedValueLabel
import com.miruplay.tv.model.playbackUiLabels
import com.miruplay.tv.model.playbackStopLabel
import com.miruplay.tv.model.playbackUnknownDurationLabel
import com.miruplay.tv.player.mpv.RifeBackend

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
    playbackEndAction: PlaybackEndAction,
    onPlaybackEndActionChange: (PlaybackEndAction) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    isPlayerActive: Boolean,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    launchStatus: String,
    onBackToDetails: () -> Unit,
    onLaunch: () -> Unit,
    onTogglePause: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
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
        modifier = modifier
            .fillMaxWidth()
            .onKeyEvent { event ->
                desktopPlayerPageKeyEvent(
                    key = event.key,
                    type = event.type,
                    isPlayerActive = isPlayerActive,
                    onLaunch = onLaunch,
                    onTogglePause = onTogglePause,
                    onResume = onResume,
                    onPause = onPause,
                    onStop = onStop,
                )
            },
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        DesktopPlayerStage(
            mediaPath = mediaPath,
            startSeconds = startSeconds,
            rifeEnabled = rifeEnabled,
            rifeBackend = rifeBackend,
            isPlayerActive = isPlayerActive,
            playbackPositionMs = playbackPositionMs,
            playbackDurationMs = playbackDurationMs,
            playbackSpeed = playbackSpeed,
            launchStatus = launchStatus,
            onBackToDetails = onBackToDetails,
            onLaunch = onLaunch,
            onTogglePause = onTogglePause,
            onResume = onResume,
            onPause = onPause,
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
            playbackEndAction = playbackEndAction,
            onPlaybackEndActionChange = onPlaybackEndActionChange,
            playbackSpeed = playbackSpeed,
            onPlaybackSpeedChange = onPlaybackSpeedChange,
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
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    playbackSpeed: Float,
    launchStatus: String,
    onBackToDetails: () -> Unit,
    onLaunch: () -> Unit,
    onTogglePause: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onStop: () -> Unit,
    focusVersion: Int = 0,
    onFocusNextPanel: () -> Boolean = { false },
) {
    val title = playbackMediaTitle(mediaPath)
    val backToDetailsFocusRequester = remember { FocusRequester() }
    val primaryTransportFocusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .onKeyEvent { event ->
                desktopPlayerKeyEvent(
                    key = event.key,
                    type = event.type,
                    isPlayerActive = isPlayerActive,
                    onLaunch = onLaunch,
                    onTogglePause = onTogglePause,
                    onResume = onResume,
                    onPause = onPause,
                    onSeekBack = onSeekBack,
                    onSeekForward = onSeekForward,
                    onStop = onStop,
                )
            }
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
            isPlayerActive = isPlayerActive,
            playbackPositionMs = playbackPositionMs,
            playbackDurationMs = playbackDurationMs,
            playbackSpeed = playbackSpeed,
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
            playbackBackToDetailsLabel(),
            onClick = onBackToDetails,
            secondary = true,
            modifier = Modifier
                .width(132.dp)
                .focusRequester(focusRequester)
                .desktopNavigationKeyHandler { key ->
                    when (desktopPlayerStageNavigationTarget(DesktopPlayerStageFocusTarget.BackToDetails, key, isPlayerActive)) {
                        DesktopPlayerStageFocusTarget.Primary -> {
                            onFocusTransport()
                            true
                        }
                        else -> false
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
        PlayerInfoChip(playbackMpvRuntimeStateLabel(isPlayerActive))
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
            playbackSeekBackCompactLabel(),
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
            playbackSeekForwardCompactLabel(),
            onClick = onSeekForward,
            size = 64.dp,
            enabled = isPlayerActive,
            onNavigationKey = { key -> moveFocus(DesktopPlayerStageFocusTarget.SeekForward, key) },
            modifier = Modifier.focusRequester(focusRequesters.getValue(DesktopPlayerStageFocusTarget.SeekForward)),
        )
        PlayerRoundButton(
            playbackStopLabel(),
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
                desktopConfirmOrNavigationKeyEvent(
                    key = event.key,
                    type = event.type,
                    onClick = if (isPlayerActive) onTogglePause else onLaunch,
                    onNavigationKey = onNavigationKey,
                )
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
            Text(playbackPauseLabel(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                desktopConfirmOrNavigationKeyEvent(
                    key = event.key,
                    type = event.type,
                    enabled = enabled,
                    onClick = onClick,
                    onNavigationKey = onNavigationKey,
                )
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
    key.toMiruPlayInputIntent()?.let { intent ->
        desktopPlayerStageNavigationTarget(current, intent, isPlayerActive)
    }

internal fun desktopPlayerStageNavigationTarget(
    current: DesktopPlayerStageFocusTarget,
    intent: MiruPlayInputIntent,
    isPlayerActive: Boolean,
): DesktopPlayerStageFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> DesktopPlayerStageFocusTarget.BackToDetails.takeIf { current in desktopPlayerTransportTargets(isPlayerActive) }
        1 -> DesktopPlayerStageFocusTarget.Primary.takeIf { current == DesktopPlayerStageFocusTarget.BackToDetails }
            ?: DesktopPlayerStageFocusTarget.NextPanel.takeIf { current in desktopPlayerTransportTargets(isPlayerActive) }
        else -> intent.horizontalNavigationDelta()?.let { delta ->
            current.transportStep(delta = delta, isPlayerActive = isPlayerActive)
        }
    }

private fun DesktopPlayerStageFocusTarget.transportStep(
    delta: Int,
    isPlayerActive: Boolean,
): DesktopPlayerStageFocusTarget? =
    desktopPlayerTransportTargets(isPlayerActive).focusTargetAfter(current = this, delta = delta)

internal enum class DesktopPlayerKeyAction {
    Launch,
    TogglePause,
    Resume,
    Pause,
    SeekBack,
    SeekForward,
    Stop,
}

private fun MiruPlayPlaybackInputAction.toDesktopPlayerKeyAction(): DesktopPlayerKeyAction? =
    when (this) {
        MiruPlayPlaybackInputAction.Launch -> DesktopPlayerKeyAction.Launch
        MiruPlayPlaybackInputAction.TogglePause -> DesktopPlayerKeyAction.TogglePause
        MiruPlayPlaybackInputAction.Resume -> DesktopPlayerKeyAction.Resume
        MiruPlayPlaybackInputAction.Pause -> DesktopPlayerKeyAction.Pause
        MiruPlayPlaybackInputAction.SeekBack -> DesktopPlayerKeyAction.SeekBack
        MiruPlayPlaybackInputAction.SeekForward -> DesktopPlayerKeyAction.SeekForward
        MiruPlayPlaybackInputAction.Stop -> DesktopPlayerKeyAction.Stop
        else -> null
    }

internal fun desktopPlayerKeyAction(
    key: Key,
    isPlayerActive: Boolean,
): DesktopPlayerKeyAction? =
    key.toMiruPlayInputIntent()
        ?.desktopPlaybackStageAction(isPlayerActive)
        ?.toDesktopPlayerKeyAction()

internal fun desktopPlayerPageKeyAction(
    key: Key,
    isPlayerActive: Boolean,
): DesktopPlayerKeyAction? =
    key.toMiruPlayInputIntent()
        ?.desktopPlaybackGlobalMediaAction(isPlayerActive)
        ?.toDesktopPlayerKeyAction()

internal fun desktopPlayerKeyEvent(
    key: Key,
    type: KeyEventType,
    isPlayerActive: Boolean,
    onLaunch: () -> Unit,
    onTogglePause: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onStop: () -> Unit,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    return when (desktopPlayerKeyAction(key, isPlayerActive)) {
        DesktopPlayerKeyAction.Launch -> onLaunch()
        DesktopPlayerKeyAction.TogglePause -> onTogglePause()
        DesktopPlayerKeyAction.Resume -> onResume()
        DesktopPlayerKeyAction.Pause -> onPause()
        DesktopPlayerKeyAction.SeekBack -> onSeekBack()
        DesktopPlayerKeyAction.SeekForward -> onSeekForward()
        DesktopPlayerKeyAction.Stop -> onStop()
        null -> return false
    }.let { true }
}

internal fun desktopPlayerPageKeyEvent(
    key: Key,
    type: KeyEventType,
    isPlayerActive: Boolean,
    onLaunch: () -> Unit,
    onTogglePause: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    return when (desktopPlayerPageKeyAction(key, isPlayerActive)) {
        DesktopPlayerKeyAction.Launch -> onLaunch()
        DesktopPlayerKeyAction.TogglePause -> onTogglePause()
        DesktopPlayerKeyAction.Resume -> onResume()
        DesktopPlayerKeyAction.Pause -> onPause()
        DesktopPlayerKeyAction.Stop -> onStop()
        DesktopPlayerKeyAction.SeekBack,
        DesktopPlayerKeyAction.SeekForward,
        null,
        -> return false
    }.let { true }
}

internal enum class DesktopPlayerKeyAction {
    Launch,
    TogglePause,
    Resume,
    Pause,
    SeekBack,
    SeekForward,
    Stop,
}

private fun MiruPlayPlaybackInputAction.toDesktopPlayerKeyAction(): DesktopPlayerKeyAction? =
    when (this) {
        MiruPlayPlaybackInputAction.Launch -> DesktopPlayerKeyAction.Launch
        MiruPlayPlaybackInputAction.TogglePause -> DesktopPlayerKeyAction.TogglePause
        MiruPlayPlaybackInputAction.Resume -> DesktopPlayerKeyAction.Resume
        MiruPlayPlaybackInputAction.Pause -> DesktopPlayerKeyAction.Pause
        MiruPlayPlaybackInputAction.SeekBack -> DesktopPlayerKeyAction.SeekBack
        MiruPlayPlaybackInputAction.SeekForward -> DesktopPlayerKeyAction.SeekForward
        MiruPlayPlaybackInputAction.Stop -> DesktopPlayerKeyAction.Stop
        else -> null
    }

internal fun desktopPlayerKeyAction(
    key: Key,
    isPlayerActive: Boolean,
): DesktopPlayerKeyAction? =
    key.toMiruPlayInputIntent()
        ?.desktopPlaybackStageAction(isPlayerActive)
        ?.toDesktopPlayerKeyAction()

internal fun desktopPlayerPageKeyAction(
    key: Key,
    isPlayerActive: Boolean,
): DesktopPlayerKeyAction? =
    key.toMiruPlayInputIntent()
        ?.desktopPlaybackGlobalMediaAction(isPlayerActive)
        ?.toDesktopPlayerKeyAction()

internal fun desktopPlayerKeyEvent(
    key: Key,
    type: KeyEventType,
    isPlayerActive: Boolean,
    onLaunch: () -> Unit,
    onTogglePause: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onStop: () -> Unit,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    return when (desktopPlayerKeyAction(key, isPlayerActive)) {
        DesktopPlayerKeyAction.Launch -> onLaunch()
        DesktopPlayerKeyAction.TogglePause -> onTogglePause()
        DesktopPlayerKeyAction.Resume -> onResume()
        DesktopPlayerKeyAction.Pause -> onPause()
        DesktopPlayerKeyAction.SeekBack -> onSeekBack()
        DesktopPlayerKeyAction.SeekForward -> onSeekForward()
        DesktopPlayerKeyAction.Stop -> onStop()
        null -> return false
    }.let { true }
}

internal fun desktopPlayerPageKeyEvent(
    key: Key,
    type: KeyEventType,
    isPlayerActive: Boolean,
    onLaunch: () -> Unit,
    onTogglePause: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    return when (desktopPlayerPageKeyAction(key, isPlayerActive)) {
        DesktopPlayerKeyAction.Launch -> onLaunch()
        DesktopPlayerKeyAction.TogglePause -> onTogglePause()
        DesktopPlayerKeyAction.Resume -> onResume()
        DesktopPlayerKeyAction.Pause -> onPause()
        DesktopPlayerKeyAction.Stop -> onStop()
        DesktopPlayerKeyAction.SeekBack,
        DesktopPlayerKeyAction.SeekForward,
        null,
        -> return false
    }.let { true }
}

internal enum class PlaybackSettingFocusTarget {
    MediaPath,
    StartSeconds,
    SubtitlePath,
    EndAction,
    Speed,
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
    PlaybackSettingFocusTarget.EndAction,
    PlaybackSettingFocusTarget.Speed,
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
    key.toMiruPlayInputIntent()?.let { intent ->
        playbackSettingNavigationTarget(current, intent)
    }

internal fun playbackSettingNavigationTarget(
    current: PlaybackSettingFocusTarget,
    intent: MiruPlayInputIntent,
): PlaybackSettingFocusTarget? =
    when (current) {
        PlaybackSettingFocusTarget.MediaPath -> when (intent.verticalNavigationDelta()) {
            -1 -> PlaybackSettingFocusTarget.PreviousPanel
            1 -> PlaybackSettingFocusTarget.SubtitlePath
            else -> PlaybackSettingFocusTarget.StartSeconds.takeIf {
                intent.horizontalNavigationDelta() == 1
            }
        }
        PlaybackSettingFocusTarget.StartSeconds -> when (intent.verticalNavigationDelta()) {
            -1 -> PlaybackSettingFocusTarget.PreviousPanel
            1 -> PlaybackSettingFocusTarget.SubtitlePath
            else -> PlaybackSettingFocusTarget.MediaPath.takeIf {
                intent.horizontalNavigationDelta() == -1
            }
        }
        PlaybackSettingFocusTarget.SubtitlePath -> when (intent.verticalNavigationDelta()) {
            -1 -> PlaybackSettingFocusTarget.MediaPath
            1 -> PlaybackSettingFocusTarget.EndAction
            else -> null
        }
        PlaybackSettingFocusTarget.EndAction -> when (intent.verticalNavigationDelta()) {
            -1 -> PlaybackSettingFocusTarget.SubtitlePath
            1 -> PlaybackSettingFocusTarget.Speed
            else -> null
        }
        PlaybackSettingFocusTarget.Speed -> when (intent.verticalNavigationDelta()) {
            -1 -> PlaybackSettingFocusTarget.EndAction
            1 -> PlaybackSettingFocusTarget.Fullscreen
            else -> null
        }
        PlaybackSettingFocusTarget.Fullscreen,
        PlaybackSettingFocusTarget.KeepOpen,
        PlaybackSettingFocusTarget.RifeToggle,
        PlaybackSettingFocusTarget.RifeBackend,
        -> when (intent.verticalNavigationDelta()) {
            -1 -> PlaybackSettingFocusTarget.Speed
            1 -> PlaybackSettingFocusTarget.NextPanel
            else -> playbackSettingToggleTargets.focusTargetAfter(
                current = current,
                intent = intent,
                axis = MiruPlayFocusAxis.Horizontal,
            )
        }
        PlaybackSettingFocusTarget.PreviousPanel,
        PlaybackSettingFocusTarget.NextPanel,
        -> null
    }

private fun Modifier.playbackSettingNavigation(
    target: PlaybackSettingFocusTarget,
    focusRequester: FocusRequester,
    onMove: (PlaybackSettingFocusTarget, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .desktopNavigationKeyHandler { key -> onMove(target, key) }

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
    key.toMiruPlayInputIntent()?.let { intent ->
        runtimeNavigationTarget(current, intent)
    }

internal fun runtimeNavigationTarget(
    current: RuntimeFocusTarget,
    intent: MiruPlayInputIntent,
): RuntimeFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> current.runtimeStep(delta = -1)
            ?: RuntimeFocusTarget.PreviousPanel.takeIf { current == RuntimeFocusTarget.MpvPath }
        1 -> current.runtimeStep(delta = 1)
        else -> null
    }

private fun RuntimeFocusTarget.runtimeStep(delta: Int): RuntimeFocusTarget? =
    runtimeFocusableTargets.focusTargetAfter(current = this, delta = delta)

private fun Modifier.runtimeNavigation(
    target: RuntimeFocusTarget,
    focusRequester: FocusRequester,
    onMove: (RuntimeFocusTarget, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .desktopNavigationKeyHandler { key -> onMove(target, key) }

@Composable
private fun PlayerStageBottomBar(
    startSeconds: String,
    isPlayerActive: Boolean,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    playbackSpeed: Float,
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
            TimeText(desktopPlaybackTimelineStartLabel(isPlayerActive, playbackPositionMs, startSeconds))
            PlaybackTimeline(
                progress = desktopPlaybackTimelineProgress(isPlayerActive, playbackPositionMs, playbackDurationMs),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp),
            )
            TimeText(desktopPlaybackTimelineDurationLabel(playbackDurationMs))
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerInfoChip(playbackSpeedChipLabel(playbackSpeed))
            PlayerInfoChip(playbackRifeStateLabel(rifeEnabled, rifeBackend.name))
            PlayerInfoChip(playbackExternalSubtitleLabel())
            Text(
                mpvPlaybackStatusText(launchStatus),
                color = TextSecondary,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

internal fun desktopPlaybackTimelineStartLabel(
    isPlayerActive: Boolean,
    playbackPositionMs: Long,
    startSeconds: String,
): String =
    if (isPlayerActive) {
        formatPlaybackPosition(playbackPositionMs)
    } else {
        playbackStartPositionLabel(startSeconds)
    }

internal fun desktopPlaybackTimelineDurationLabel(playbackDurationMs: Long): String =
    if (playbackDurationMs > 0L) {
        formatPlaybackPosition(playbackDurationMs)
    } else {
        playbackUnknownDurationLabel()
    }

internal fun desktopPlaybackTimelineProgress(
    isPlayerActive: Boolean,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
): Float =
    if (isPlayerActive) {
        PlaybackTimingConventions.playbackProgressFraction(playbackPositionMs, playbackDurationMs)
    } else {
        0f
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
    playbackEndAction: PlaybackEndAction,
    onPlaybackEndActionChange: (PlaybackEndAction) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    focusVersion: Int = 0,
    focusTarget: PlaybackSettingFocusTarget = PlaybackSettingFocusTarget.MediaPath,
    onFocusPreviousPanel: () -> Boolean = { false },
    onFocusNextPanel: () -> Boolean = { false },
) {
    val labels = playbackUiLabels()
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
        Text(playbackSettingsTitleLabel(), color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
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
        Text(playbackEndSettingsTitleLabel(), color = TextPrimary, fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(playbackEndSettingsDescriptionLabel(), color = TextSecondary, fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp)
        Spacer(Modifier.height(MiruPlayUiMetrics.SMALL_GAP_DP.dp))
        PlaybackEndActionPicker(
            playbackEndAction,
            onSelected = onPlaybackEndActionChange,
            modifier = Modifier.playbackSettingNavigation(
                target = PlaybackSettingFocusTarget.EndAction,
                focusRequester = settingFocusRequesters.getValue(PlaybackSettingFocusTarget.EndAction),
                onMove = ::movePlaybackSettingFocus,
            ),
        )
        Spacer(Modifier.height(MiruPlayUiMetrics.SMALL_GAP_DP.dp))
        PlaybackSpeedPicker(
            selected = playbackSpeed,
            onSelected = onPlaybackSpeedChange,
            modifier = Modifier.playbackSettingNavigation(
                target = PlaybackSettingFocusTarget.Speed,
                focusRequester = settingFocusRequesters.getValue(PlaybackSettingFocusTarget.Speed),
                onMove = ::movePlaybackSettingFocus,
            ),
        )
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
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
        Text(playbackRuntimeTitleLabel(), color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        LabeledTextField(
            playbackMpvExecutableFieldLabel(),
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
            playbackPortableConfigFieldLabel(),
            configDir,
            onValueChange = onConfigDirChange,
            inputModifier = Modifier.runtimeNavigation(
                target = RuntimeFocusTarget.ConfigDir,
                focusRequester = runtimeFocusRequesters.getValue(RuntimeFocusTarget.ConfigDir),
                onMove = ::moveRuntimeFocus,
            ),
        )
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        StatusBox(playbackRuntimeStatusText(status))
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        TvActionButton(
            playbackCheckRuntimeActionLabel(),
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
        Text(playbackDiagnosticsTitleLabel(), color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        StatusBox(mpvPlaybackStatusText(launchStatus))
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

internal fun desktopPlaybackSourceLine(
    mediaPath: String,
    rifeEnabled: Boolean,
    rifeBackend: RifeBackend,
    isPlayerActive: Boolean,
): String {
    return mpvPlaybackSourceLine(
        mediaPath = mediaPath,
        rifeEnabled = rifeEnabled,
        rifeBackendName = rifeBackend.name,
        isPlayerActive = isPlayerActive,
    )
}

@Composable
private fun PlaybackSpeedPicker(
    selected: Float,
    onSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val speedOptions = remember(selected) { playbackSpeedPickerOptions(selected) }
    Column {
        Text(
            playbackSpeedMenuTitle(),
            color = TextSecondary,
            fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) AnimeRed else Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                )
                .background(if (isFocused) CardBg.copy(alpha = 0.55f) else Color.Transparent)
                .focusable(interactionSource = interactionSource)
                .onPreviewKeyEvent { event ->
                    desktopConfirmOrNavigationKeyEvent(
                        key = event.key,
                        type = event.type,
                        onClick = { onSelected(selected) },
                        onNavigationKey = { key ->
                            val next = playbackSpeedNavigationTarget(selected, key)
                                ?: return@desktopConfirmOrNavigationKeyEvent false
                            onSelected(next)
                            true
                        },
                    )
                }
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            speedOptions.forEach { speed ->
                PlaybackSpeedChoiceChip(
                    text = playbackSpeedValueLabel(speed),
                    selected = speed == selected,
                    onClick = { onSelected(speed) },
                )
            }
        }
    }
}

@Composable
private fun PlaybackSpeedChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isFocused -> Color.White
        selected -> AnimeRed
        else -> Color.White.copy(alpha = 0.18f)
    }
    val background = when {
        selected -> AnimeRed.copy(alpha = 0.28f)
        isFocused -> AccentBlue
        else -> DarkSurface
    }
    Box(
        modifier = Modifier
            .width(74.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                width = if (selected || isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun playbackSpeedPickerOptions(selected: Float): List<Float> {
    val options = playbackSpeedOptions()
    return if (selected in options) {
        options
    } else {
        (options + selected).distinct().sorted()
    }
}

internal fun playbackSpeedNavigationTarget(
    current: Float,
    key: Key,
): Float? =
    key.toMiruPlayInputIntent()?.let { intent ->
        playbackSpeedNavigationTarget(current, intent)
    }

internal fun playbackSpeedNavigationTarget(
    current: Float,
    intent: MiruPlayInputIntent,
): Float? =
    playbackSpeedPickerOptions(current).focusTargetAfter(
        current = current,
        intent = intent,
        axis = MiruPlayFocusAxis.Horizontal,
    )

@Composable
private fun PlaybackEndActionPicker(
    selected: PlaybackEndAction,
    onSelected: (PlaybackEndAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) AnimeRed else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp),
            )
            .background(if (isFocused) CardBg.copy(alpha = 0.55f) else Color.Transparent)
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                desktopConfirmOrNavigationKeyEvent(
                    key = event.key,
                    type = event.type,
                    onClick = { onSelected(selected) },
                    onNavigationKey = { key ->
                        val next = playbackEndActionNavigationTarget(selected, key)
                            ?: return@desktopConfirmOrNavigationKeyEvent false
                        onSelected(next)
                        true
                    },
                )
            }
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaybackEndActionChoiceChip(
            text = PlaybackEndAction.RETURN_TO_DETAIL.playbackEndActionLabel(),
            selected = selected == PlaybackEndAction.RETURN_TO_DETAIL,
            onClick = { onSelected(PlaybackEndAction.RETURN_TO_DETAIL) },
            widthDp = 160,
        )
        PlaybackEndActionChoiceChip(
            text = PlaybackEndAction.PLAY_NEXT_EPISODE.playbackEndActionLabel(),
            selected = selected == PlaybackEndAction.PLAY_NEXT_EPISODE,
            onClick = { onSelected(PlaybackEndAction.PLAY_NEXT_EPISODE) },
            widthDp = 170,
        )
    }
}

@Composable
private fun PlaybackEndActionChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    widthDp: Int,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isFocused -> Color.White
        selected -> AnimeRed
        else -> Color.White.copy(alpha = 0.18f)
    }
    val background = when {
        selected -> AnimeRed.copy(alpha = 0.28f)
        isFocused -> AccentBlue
        else -> DarkSurface
    }
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                width = if (selected || isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun playbackEndActionNavigationTarget(
    current: PlaybackEndAction,
    key: Key,
): PlaybackEndAction? =
    key.toMiruPlayInputIntent()?.let { intent ->
        playbackEndActionNavigationTarget(current, intent)
    }

internal fun playbackEndActionNavigationTarget(
    current: PlaybackEndAction,
    intent: MiruPlayInputIntent,
): PlaybackEndAction? =
    when (intent.horizontalNavigationDelta()) {
        1 -> PlaybackEndAction.PLAY_NEXT_EPISODE.takeIf { current == PlaybackEndAction.RETURN_TO_DETAIL }
        -1 -> PlaybackEndAction.RETURN_TO_DETAIL.takeIf { current == PlaybackEndAction.PLAY_NEXT_EPISODE }
        else -> null
    }

private fun playerStageBrush(title: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFF111827), Color(0xFF7C1D2F), Color(0xFF030306)),
        listOf(Color(0xFF062C3F), Color(0xFF263A75), Color(0xFF030306)),
        listOf(Color(0xFF34210E), Color(0xFF5C253A), Color(0xFF030306)),
    )
    return Brush.horizontalGradient(palettes[Math.floorMod(title.hashCode(), palettes.size)])
}
