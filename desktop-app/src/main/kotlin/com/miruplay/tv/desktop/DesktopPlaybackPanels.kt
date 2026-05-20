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
import androidx.compose.runtime.remember
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
) {
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
) {
    val title = desktopPlaybackTitle(mediaPath)
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
            onBackToDetails = onBackToDetails,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        PlayerTransportControls(
            isPlayerActive = isPlayerActive,
            onLaunch = onLaunch,
            onTogglePause = onTogglePause,
            onSeekBack = onSeekBack,
            onSeekForward = onSeekForward,
            onStop = onStop,
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
    onBackToDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        TvActionButton("返回详情", onClick = onBackToDetails, secondary = true, modifier = Modifier.width(132.dp))
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
        PlayerInfoChip(if (title == "选择媒体") "mpv ready" else "Windows mpv")
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
    modifier: Modifier = Modifier,
) {
    val actions = remember(isPlayerActive) {
        listOfNotNull(
            PlayerTransportAction.SeekBack.takeIf { isPlayerActive },
            PlayerTransportAction.Primary,
            PlayerTransportAction.SeekForward.takeIf { isPlayerActive },
            PlayerTransportAction.Stop.takeIf { isPlayerActive },
        )
    }
    val focusRequesters = remember {
        PlayerTransportAction.values().associateWith { FocusRequester() }
    }
    LaunchedEffect(isPlayerActive) {
        focusRequesters.getValue(PlayerTransportAction.Primary).requestFocus()
    }

    fun moveFocus(current: PlayerTransportAction, delta: Int): Boolean {
        val currentIndex = actions.indexOf(current)
        if (currentIndex < 0) return false
        val target = actions.getOrNull(currentIndex + delta) ?: return false
        focusRequesters.getValue(target).requestFocus()
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
            onNavigationKey = { key -> key.toTransportDelta()?.let { moveFocus(PlayerTransportAction.SeekBack, it) } ?: false },
            modifier = Modifier.focusRequester(focusRequesters.getValue(PlayerTransportAction.SeekBack)),
        )
        PlayerPrimaryButton(
            isPlayerActive = isPlayerActive,
            onLaunch = onLaunch,
            onTogglePause = onTogglePause,
            onNavigationKey = { key -> key.toTransportDelta()?.let { moveFocus(PlayerTransportAction.Primary, it) } ?: false },
            modifier = Modifier.focusRequester(focusRequesters.getValue(PlayerTransportAction.Primary)),
        )
        PlayerRoundButton(
            "+30",
            onClick = onSeekForward,
            size = 64.dp,
            enabled = isPlayerActive,
            onNavigationKey = { key -> key.toTransportDelta()?.let { moveFocus(PlayerTransportAction.SeekForward, it) } ?: false },
            modifier = Modifier.focusRequester(focusRequesters.getValue(PlayerTransportAction.SeekForward)),
        )
        PlayerRoundButton(
            "停止",
            onClick = onStop,
            size = 64.dp,
            enabled = isPlayerActive,
            onNavigationKey = { key -> key.toTransportDelta()?.let { moveFocus(PlayerTransportAction.Stop, it) } ?: false },
            modifier = Modifier.focusRequester(focusRequesters.getValue(PlayerTransportAction.Stop)),
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

private enum class PlayerTransportAction {
    SeekBack,
    Primary,
    SeekForward,
    Stop,
}

private fun Key.toTransportDelta(): Int? =
    when (this) {
        Key.DirectionLeft -> -1
        Key.DirectionRight -> 1
        else -> null
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
                launchStatus,
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
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Text("播放设置", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
            LabeledTextField("Media URI or path", mediaPath, onValueChange = onMediaPathChange, modifier = Modifier.weight(1.6f))
            LabeledTextField("Start seconds", startSeconds, onValueChange = onStartSecondsChange, modifier = Modifier.weight(0.42f))
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        LabeledTextField("Subtitle path", subtitlePath, onValueChange = onSubtitlePathChange)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
        ) {
            ToggleRow("Fullscreen", fullscreen, onFullscreenChange)
            ToggleRow("Keep open", keepOpen, onKeepOpenChange)
            ToggleRow("RIFE", rifeEnabled, onRifeEnabledChange)
            RifeBackendPicker(rifeBackend, onSelected = onRifeBackendChange)
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
) {
    TvPanel(modifier) {
        Text("Runtime", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        LabeledTextField("mpv.exe", mpvPath, onValueChange = onMpvPathChange)
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        LabeledTextField("portable_config", configDir, onValueChange = onConfigDirChange)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        StatusBox(status)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        TvActionButton("Check runtime", onClick = onCheckRuntime)
    }
}

@Composable
internal fun CommandPanel(
    commandPreview: String,
    launchStatus: String,
    modifier: Modifier = Modifier,
) {
    TvPanel(modifier) {
        Text("mpv diagnostics", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        StatusBox(launchStatus)
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
