package com.miruplay.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.formatPlaybackPosition
import com.miruplay.tv.repository.MediaDetailRows
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.displayName
import com.miruplay.tv.repository.mediaDisplayName

@Composable
internal fun DesktopDetailHero(
    entry: MediaIndexEntry?,
    source: MediaSourceInfo?,
    onBackToLibrary: () -> Unit,
    onPlay: () -> Unit,
) {
    val actionFocusRequesters = remember {
        mapOf(
            DesktopDetailHeroAction.Play to FocusRequester(),
            DesktopDetailHeroAction.BackToLibrary to FocusRequester(),
        )
    }

    fun moveActionFocus(current: DesktopDetailHeroAction, delta: Int): Boolean {
        val target = moveDesktopDetailHeroAction(current, delta) ?: return false
        actionFocusRequesters.getValue(target).requestFocus()
        return true
    }

    LaunchedEffect(entry?.sourceId, entry?.path) {
        actionFocusRequesters.getValue(DesktopDetailHeroAction.Play).requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(detailHeroBrush(entry?.detailTitle().orEmpty()))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.34f)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            DetailPoster(entry?.detailTitle().orEmpty())
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    entry?.detailTitle() ?: "选择一部番剧",
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.HERO_TITLE_SP.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    entry?.detailSubtitle(source) ?: "从 Library 海报墙选择内容后显示详情。",
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                entry?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        plot,
                        color = TextPrimary.copy(alpha = 0.84f),
                        fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        "播放",
                        onClick = onPlay,
                        modifier = Modifier
                            .detailHeroActionNavigation(
                                action = DesktopDetailHeroAction.Play,
                                focusRequester = actionFocusRequesters.getValue(DesktopDetailHeroAction.Play),
                                onMove = ::moveActionFocus,
                            )
                            .width(180.dp),
                    )
                    TvActionButton(
                        "返回海报墙",
                        onClick = onBackToLibrary,
                        secondary = true,
                        modifier = Modifier
                            .detailHeroActionNavigation(
                                action = DesktopDetailHeroAction.BackToLibrary,
                                focusRequester = actionFocusRequesters.getValue(DesktopDetailHeroAction.BackToLibrary),
                                onMove = ::moveActionFocus,
                            )
                            .width(180.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
                    .background(AnimeRed),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
            }
        }
    }
}

private fun Modifier.detailHeroActionNavigation(
    action: DesktopDetailHeroAction,
    focusRequester: FocusRequester,
    onMove: (DesktopDetailHeroAction, Int) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                event.key.toDetailHeroActionDelta()?.let { delta -> onMove(action, delta) } ?: false
            }
        }

internal enum class DesktopDetailHeroAction {
    Play,
    BackToLibrary,
}

internal fun moveDesktopDetailHeroAction(
    current: DesktopDetailHeroAction,
    delta: Int,
): DesktopDetailHeroAction? {
    val actions = DesktopDetailHeroAction.entries
    val targetIndex = actions.indexOf(current) + delta
    return actions.getOrNull(targetIndex)
}

private fun Key.toDetailHeroActionDelta(): Int? =
    when (this) {
        Key.DirectionLeft -> -1
        Key.DirectionRight -> 1
        else -> null
    }

@Composable
private fun DetailPoster(title: String) {
    Box(
        modifier = Modifier
            .width(205.dp)
            .height(302.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(detailPosterBrush(title))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp)),
    ) {
        Text(
            title.take(2).ifBlank { "MP" }.uppercase(),
            color = Color.White.copy(alpha = 0.18f),
            fontSize = 58.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

internal fun MediaIndexEntry.detailTitle(): String =
    metadataTitle?.takeIf { it.isNotBlank() }
        ?: animeName?.takeIf { it.isNotBlank() }
        ?: MediaPathConventions.stem(path).takeIf { it.isNotBlank() }
        ?: displayName()

internal fun MediaIndexEntry.detailSubtitle(source: MediaSourceInfo?): String = buildString {
    source?.name?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
    seasonNumber?.let { append("S").append(it).append(" · ") }
    episodeNumber?.let { append("EP").append(it).append(" · ") }
    episodeTitle?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
    append(MediaPathConventions.stem(path))
}.trim().trimEnd('·').trim()

private fun detailPosterBrush(title: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFFB83250), Color(0xFF2E183F), CardBg),
        listOf(Color(0xFF1E6A8A), Color(0xFF12213C), CardBg),
        listOf(Color(0xFF7C4D1D), Color(0xFF221A12), CardBg),
        listOf(Color(0xFF4F6F2A), Color(0xFF142115), CardBg),
    )
    return Brush.verticalGradient(palettes[Math.floorMod(title.hashCode(), palettes.size)])
}

private fun detailHeroBrush(title: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFF9B2D45), Color(0xFF182447), Color(0xFF08080C)),
        listOf(Color(0xFF1C6582), Color(0xFF271B4A), Color(0xFF08080C)),
        listOf(Color(0xFF6C4A1A), Color(0xFF172A22), Color(0xFF08080C)),
    )
    return Brush.horizontalGradient(palettes[Math.floorMod(title.hashCode(), palettes.size)])
}

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
