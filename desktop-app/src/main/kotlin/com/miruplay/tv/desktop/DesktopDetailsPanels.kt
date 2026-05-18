package com.miruplay.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.formatPlaybackPosition
import com.miruplay.tv.repository.MediaDetailRows
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.mediaDisplayName

@Composable
internal fun RecentPlaybackPanel(
    records: List<ProgressRecord>,
    selectedRecord: ProgressRecord?,
    status: String,
    onRefresh: () -> Unit,
    onRecordSelected: (ProgressRecord) -> Unit,
    onClearSelected: () -> Unit,
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.32f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                Text("Continue watching", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                    TvActionButton("Refresh", onClick = onRefresh, secondary = true)
                    TvActionButton("Clear item", onClick = onClearSelected, secondary = true)
                }
                StatusBox(status)
            }
            Column(
                modifier = Modifier.weight(0.68f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                if (records.isEmpty()) {
                    DesktopEmptyState("Launch playback to create recent items.")
                } else {
                    records.take(6).forEach { record ->
                        RecentProgressRow(
                            record = record,
                            selected = selectedRecord?.episodeId == record.episodeId,
                            onClick = { onRecordSelected(record) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentProgressRow(
    record: ProgressRecord,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DesktopSelectableRow(selected = selected, onClick = onClick) { active ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatPlaybackPosition(record.positionMs),
                color = if (active) AnimeRed else TextSecondary,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    record.mediaDisplayName(),
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    record.episodeId,
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("x${record.playCount}", color = TextSecondary, fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp)
        }
    }
}

@Composable
internal fun MediaDetailsPanel(
    source: MediaSourceInfo?,
    indexEntry: MediaIndexEntry?,
    remoteEntry: FileEntry?,
    recentRecord: ProgressRecord?,
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Text("Media details", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        if (source == null && indexEntry == null && remoteEntry == null && recentRecord == null) {
            DesktopEmptyState(
                text = "Select media to show details.",
                heightDp = MiruPlayUiMetrics.DETAIL_PREVIEW_HEIGHT_DP,
            )
            return@TvPanel
        }

        val rows = MediaDetailRows.build(
            source = source,
            indexEntry = indexEntry,
            remoteEntry = remoteEntry,
            recentRecord = recentRecord,
        )
        val splitIndex = (rows.size + 1) / 2

        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
            Column(
                modifier = Modifier.weight(0.5f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SMALL_GAP_DP.dp),
            ) {
                rows.take(splitIndex).forEach { row ->
                    DetailLine(row.label, row.value)
                }
            }
            Column(
                modifier = Modifier.weight(0.5f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SMALL_GAP_DP.dp),
            ) {
                rows.drop(splitIndex).forEach { row ->
                    DetailLine(row.label, row.value)
                }
            }
        }
    }
}

@Composable
internal fun DetailLine(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(CardBg.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .padding(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
    ) {
        Text(label, color = TextSecondary, fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            color = TextPrimary,
            fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
