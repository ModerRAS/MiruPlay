package com.miruplay.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayUiMetrics
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
    modifier: Modifier = Modifier,
) {
    TvPanel(modifier) {
        Text("Featured playback", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
            PosterPlaceholder()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                LabeledTextField("Media URI or path", mediaPath, onValueChange = onMediaPathChange)
                LabeledTextField("Subtitle path", subtitlePath, onValueChange = onSubtitlePathChange)
                LabeledTextField("Start seconds", startSeconds, onValueChange = onStartSecondsChange)
            }
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.SECTION_GAP_DP.dp))
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
    onLaunch: () -> Unit,
    onTogglePause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onStop: () -> Unit,
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Text("mpv command", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
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
            )
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
            TvActionButton("Launch mpv", onClick = onLaunch)
            TvActionButton("Stop", onClick = onStop, secondary = true)
            TvActionButton("Pause", onClick = onTogglePause, secondary = true)
            TvActionButton("-10s", onClick = onSeekBack, secondary = true, modifier = Modifier.width(110.dp))
            TvActionButton("+30s", onClick = onSeekForward, secondary = true, modifier = Modifier.width(110.dp))
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        Text(launchStatus, color = TextSecondary, fontSize = MiruPlayUiMetrics.SECTION_BODY_SP.sp)
    }
}
