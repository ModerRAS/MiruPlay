package com.miruplay.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.EpisodeVersion
import com.miruplay.tv.model.availableVersions
import com.miruplay.tv.ui.theme.AccentBlue
import com.miruplay.tv.ui.theme.CardBg
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.FocusBorder
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography

@Composable
fun EpisodeVersionDialog(
    episode: Episode,
    onDismiss: () -> Unit,
    onPlay: (EpisodeVersion) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 720.dp, max = 980.dp)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                .padding(24.dp),
        ) {
            Text(
                text = "第 ${episode.episodeNumber} 集 · 选择播放版本",
                style = TvTypography.subtitle,
                color = TextPrimary,
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(episode.availableVersions(), key = EpisodeVersion::episodeId) { version ->
                    EpisodeVersionOption(version = version, onClick = { onPlay(version) })
                }
            }
        }
    }
}

@Composable
private fun EpisodeVersionOption(
    version: EpisodeVersion,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) AccentBlue.copy(alpha = 0.68f) else CardBg)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) FocusBorder else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp),
            )
            .tvFocusableClickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = version.fileName,
            style = TvTypography.body,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = version.filePath,
            fontSize = 12.sp,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
