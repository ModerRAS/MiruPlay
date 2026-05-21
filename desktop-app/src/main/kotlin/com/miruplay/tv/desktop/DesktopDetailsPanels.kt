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
import androidx.compose.foundation.layout.widthIn
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
    episodeCount: Int = 0,
    focusVersion: Int = 0,
    onFocusRecentPlayback: () -> Boolean,
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

    fun moveFromAction(current: DesktopDetailHeroAction, key: Key): Boolean =
        when (key) {
            Key.DirectionLeft -> moveActionFocus(current, -1)
            Key.DirectionRight -> moveActionFocus(current, 1)
            Key.DirectionDown -> onFocusRecentPlayback()
            else -> false
        }

    val statLabels = detailHeroStatLabels(entry, episodeCount)

    LaunchedEffect(focusVersion) {
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
                    entry?.detailSubtitle(source) ?: desktopDetailHeroEmptySubtitle(),
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (statLabels.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        statLabels.forEachIndexed { index, label ->
                            DetailStatPill(
                                text = label,
                                color = when (index) {
                                    0 -> TextSecondary
                                    statLabels.lastIndex -> AnimeRed
                                    else -> AccentBlue
                                },
                            )
                        }
                    }
                }
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
                                onMove = ::moveFromAction,
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
                                onMove = ::moveFromAction,
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

@Composable
private fun DetailStatPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .widthIn(max = 190.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.38f), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.detailHeroActionNavigation(
    action: DesktopDetailHeroAction,
    focusRequester: FocusRequester,
    onMove: (DesktopDetailHeroAction, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                onMove(action, event.key)
            }
        }

internal enum class DesktopDetailHeroAction {
    Play,
    BackToLibrary,
}

internal enum class DesktopDetailDownTarget {
    EpisodeList,
    RecentPlayback,
    BangumiMetadata,
}

internal fun moveDesktopDetailHeroAction(
    current: DesktopDetailHeroAction,
    delta: Int,
): DesktopDetailHeroAction? {
    val actions = DesktopDetailHeroAction.entries
    val targetIndex = actions.indexOf(current) + delta
    return actions.getOrNull(targetIndex)
}

internal fun detailHeroDownTarget(
    hasRelatedEpisodes: Boolean,
    hasRecentPlayback: Boolean,
): DesktopDetailDownTarget =
    when {
        hasRelatedEpisodes -> DesktopDetailDownTarget.EpisodeList
        hasRecentPlayback -> DesktopDetailDownTarget.RecentPlayback
        else -> DesktopDetailDownTarget.BangumiMetadata
    }

internal fun detailHeroStatLabels(
    entry: MediaIndexEntry?,
    episodeCount: Int,
): List<String> {
    if (entry == null) return emptyList()
    return buildList {
        if (episodeCount > 0) {
            add("全 $episodeCount 话")
        }
        entry.seasonNumber?.let { add("第 $it 季") }
        entry.metadataSource
            ?.takeIf { it.isNotBlank() }
            ?.let { add(it.trim()) }
    }
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

internal fun desktopDetailHeroEmptySubtitle(): String =
    "从媒体库海报墙选择内容后显示详情。"

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
internal fun DetailEpisodePanel(
    episodes: List<MediaIndexEntry>,
    selectedEntry: MediaIndexEntry?,
    selectedSeason: Int?,
    recentRecords: List<ProgressRecord>,
    focusVersion: Int,
    onFocusPreviousPanel: () -> Boolean,
    onFocusNextPanel: () -> Boolean,
    onSeasonSelected: (Int?) -> Unit,
    onEpisodeFocused: (MediaIndexEntry) -> Unit,
    onEpisodeSelected: (MediaIndexEntry) -> Unit,
) {
    val seasons = remember(episodes) { detailEpisodeSeasons(episodes) }
    val activeSeason = detailActiveEpisodeSeason(
        episodes = episodes,
        selectedEntry = selectedEntry,
        requestedSeason = selectedSeason,
    )
    val visibleEpisodes = remember(episodes, activeSeason) { detailEpisodesForSeason(episodes, activeSeason) }
    val progressByPath = remember(recentRecords) { recentRecords.associateBy { it.episodeId } }
    val episodeFocusRequesters = remember(visibleEpisodes.map { it.path }) {
        List(visibleEpisodes.size) { FocusRequester() }
    }

    fun moveEpisodeFocus(currentIndex: Int, delta: Int): Boolean {
        return when (val target = moveDetailEpisodeFocusTarget(currentIndex, visibleEpisodes.size, delta)) {
            is DetailEpisodeFocusTarget.Row -> {
                val episode = visibleEpisodes[target.index]
                onEpisodeFocused(episode)
                episodeFocusRequesters.getOrNull(target.index)?.requestFocus()
                true
            }
            DetailEpisodeFocusTarget.PreviousPanel -> onFocusPreviousPanel()
            DetailEpisodeFocusTarget.NextPanel -> onFocusNextPanel()
            null -> false
        }
    }

    LaunchedEffect(focusVersion, visibleEpisodes.map { it.path }, selectedEntry?.path) {
        if (focusVersion > 0 && visibleEpisodes.isNotEmpty()) {
            val selectedIndex = visibleEpisodes.indexOfFirst { it.path == selectedEntry?.path }.coerceAtLeast(0)
            episodeFocusRequesters.getOrNull(selectedIndex)?.requestFocus()
        }
    }

    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("选集", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    detailEpisodeShelfSubtitle(episodes.size),
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                )
            }
            if (seasons.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                    seasons.forEach { season ->
                        TvActionButton(
                            text = "第 $season 季",
                            onClick = { onSeasonSelected(season) },
                            secondary = activeSeason != season,
                            modifier = Modifier.width(132.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        if (visibleEpisodes.isEmpty()) {
            DesktopEmptyState("扫描媒体库后会在这里显示同番选集。", heightDp = 180)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                visibleEpisodes.forEachIndexed { index, episode ->
                    DetailEpisodeRow(
                        entry = episode,
                        selected = selectedEntry?.path == episode.path,
                        progress = progressByPath[episode.path],
                        onClick = { onEpisodeSelected(episode) },
                        modifier = Modifier
                            .focusRequester(episodeFocusRequesters[index])
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    false
                                } else {
                                    when (event.key) {
                                        Key.DirectionUp -> moveEpisodeFocus(index, -1)
                                        Key.DirectionDown -> moveEpisodeFocus(index, 1)
                                        else -> false
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailEpisodeRow(
    entry: MediaIndexEntry,
    selected: Boolean,
    progress: ProgressRecord?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopSelectableRow(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        heightDp = 78,
    ) { active ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
                    .background(if (active) AnimeRed else AnimeRed.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.episodeBadge(),
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    entry.detailEpisodeTitle(),
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.path,
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                detailEpisodeProgressLabel(progress),
                color = if (progress != null) AnimeRed else TextSecondary,
                fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(148.dp),
            )
        }
    }
}

internal fun detailEpisodesForSelection(
    entries: List<MediaIndexEntry>,
    selectedEntry: MediaIndexEntry?,
): List<MediaIndexEntry> {
    val selected = selectedEntry?.takeUnless { it.isDirectory } ?: return emptyList()
    val title = selected.posterTitle()
    return entries
        .asSequence()
        .filterNot { it.isDirectory }
        .filter { it.sourceId == selected.sourceId && it.posterTitle() == title }
        .sortedWith(detailEpisodeComparator)
        .toList()
}

internal fun detailEpisodeShelfSubtitle(episodeCount: Int): String =
    if (episodeCount <= 0) "当前详情没有可播放索引项" else "全 $episodeCount 话 · 同番选集"

internal fun detailEpisodeSeasons(episodes: List<MediaIndexEntry>): List<Int> =
    episodes
        .mapNotNull { it.seasonNumber }
        .distinct()
        .sorted()

internal fun detailActiveEpisodeSeason(
    episodes: List<MediaIndexEntry>,
    selectedEntry: MediaIndexEntry?,
    requestedSeason: Int?,
): Int? {
    val seasons = detailEpisodeSeasons(episodes)
    if (seasons.isEmpty()) return null
    return when {
        requestedSeason in seasons -> requestedSeason
        selectedEntry?.seasonNumber in seasons -> selectedEntry?.seasonNumber
        else -> seasons.first()
    }
}

internal fun detailEpisodesForSeason(
    episodes: List<MediaIndexEntry>,
    season: Int?,
): List<MediaIndexEntry> =
    if (season == null) episodes.sortedWith(detailEpisodeComparator) else episodes.filter { it.seasonNumber == season }

internal fun moveDetailEpisodeSelection(
    currentIndex: Int,
    itemCount: Int,
    delta: Int,
): Int? {
    return (moveDetailEpisodeFocusTarget(currentIndex, itemCount, delta) as? DetailEpisodeFocusTarget.Row)?.index
}

internal sealed interface DetailEpisodeFocusTarget {
    data class Row(val index: Int) : DetailEpisodeFocusTarget
    data object PreviousPanel : DetailEpisodeFocusTarget
    data object NextPanel : DetailEpisodeFocusTarget
}

internal fun moveDetailEpisodeFocusTarget(
    currentIndex: Int,
    itemCount: Int,
    delta: Int,
): DetailEpisodeFocusTarget? {
    if (itemCount <= 0) return null
    val targetIndex = currentIndex + delta
    return when {
        targetIndex < 0 -> DetailEpisodeFocusTarget.PreviousPanel
        targetIndex >= itemCount -> DetailEpisodeFocusTarget.NextPanel
        else -> DetailEpisodeFocusTarget.Row(targetIndex)
    }
}

private val detailEpisodeComparator =
    compareBy<MediaIndexEntry>(
        { it.seasonNumber ?: Int.MAX_VALUE },
        { it.episodeNumber ?: Int.MAX_VALUE },
        { it.path.lowercase() },
    )

private fun MediaIndexEntry.episodeBadge(): String =
    episodeNumber?.toString()?.padStart(2, '0') ?: "--"

private fun MediaIndexEntry.detailEpisodeTitle(): String {
    val number = episodeNumber?.let { "第 $it 集" } ?: "未编号"
    val title = episodeTitle?.takeIf { it.isNotBlank() }
    return if (title == null) number else "$number · $title"
}

private fun detailEpisodeProgressLabel(progress: ProgressRecord?): String =
    progress?.let { "继续 ${formatPlaybackPosition(it.positionMs)}" } ?: "未观看"

@Composable
internal fun RecentPlaybackPanel(
    records: List<ProgressRecord>,
    selectedRecord: ProgressRecord?,
    status: String,
    focusVersion: Int,
    onFocusPreviousPanel: () -> Boolean,
    onFocusNextPanel: () -> Boolean,
    onRefresh: () -> Unit,
    onRecordSelected: (ProgressRecord) -> Unit,
    onClearSelected: () -> Unit,
) {
    val visibleRecords = records.take(6)
    val labels = desktopRecentPlaybackLabels()
    val recordFocusRequesters = remember(visibleRecords.map { it.episodeId }) {
        List(visibleRecords.size) { FocusRequester() }
    }
    val actionFocusRequesters = remember {
        mapOf(
            RecentPlaybackAction.Refresh to FocusRequester(),
            RecentPlaybackAction.Clear to FocusRequester(),
        )
    }

    fun requestRecentFocus(target: RecentPlaybackFocusTarget?): Boolean {
        return when (target) {
            is RecentPlaybackFocusTarget.Action -> {
                actionFocusRequesters.getValue(target.action).requestFocus()
                true
            }
            is RecentPlaybackFocusTarget.Row -> {
                recordFocusRequesters.getOrNull(target.index)?.requestFocus()
                true
            }
            RecentPlaybackFocusTarget.PreviousPanel -> onFocusPreviousPanel()
            RecentPlaybackFocusTarget.NextPanel -> onFocusNextPanel()
            null -> false
        }
    }

    fun moveRecentFocus(currentIndex: Int, delta: Int): Boolean =
        requestRecentFocus(moveRecentPlaybackFocusTarget(currentIndex, visibleRecords.size, delta))

    fun moveRecentActionFocus(current: RecentPlaybackAction, key: Key): Boolean {
        val target = when (key) {
            Key.DirectionLeft -> moveRecentPlaybackAction(current, -1)?.let(RecentPlaybackFocusTarget::Action)
            Key.DirectionRight -> moveRecentPlaybackAction(current, 1)?.let(RecentPlaybackFocusTarget::Action)
            Key.DirectionUp -> recentPlaybackActionVerticalFocusTarget(direction = -1, hasRecords = visibleRecords.isNotEmpty())
            Key.DirectionDown -> recentPlaybackActionVerticalFocusTarget(direction = 1, hasRecords = visibleRecords.isNotEmpty())
            else -> null
        }
        return requestRecentFocus(target)
    }

    LaunchedEffect(focusVersion) {
        if (focusVersion > 0) {
            if (visibleRecords.isNotEmpty()) {
                recordFocusRequesters.firstOrNull()?.requestFocus()
            } else {
                actionFocusRequesters.getValue(RecentPlaybackAction.Refresh).requestFocus()
            }
        }
    }

    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.32f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                Text(labels.title, color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                    TvActionButton(
                        labels.refreshAction,
                        onClick = onRefresh,
                        secondary = true,
                        modifier = Modifier
                            .focusRequester(actionFocusRequesters.getValue(RecentPlaybackAction.Refresh))
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    moveRecentActionFocus(RecentPlaybackAction.Refresh, event.key)
                                } else {
                                    false
                                }
                            },
                    )
                    TvActionButton(
                        labels.clearAction,
                        onClick = onClearSelected,
                        secondary = true,
                        modifier = Modifier
                            .focusRequester(actionFocusRequesters.getValue(RecentPlaybackAction.Clear))
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    moveRecentActionFocus(RecentPlaybackAction.Clear, event.key)
                                } else {
                                    false
                                }
                            },
                    )
                }
                StatusBox(status)
            }
            Column(
                modifier = Modifier.weight(0.68f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                if (records.isEmpty()) {
                    DesktopEmptyState(labels.emptyState)
                } else {
                    visibleRecords.forEachIndexed { index, record ->
                        RecentProgressRow(
                            record = record,
                            selected = selectedRecord?.episodeId == record.episodeId,
                            onClick = { onRecordSelected(record) },
                            modifier = Modifier
                                .focusRequester(recordFocusRequesters[index])
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) {
                                        false
                                    } else {
                                        when (event.key) {
                                            Key.DirectionUp -> moveRecentFocus(index, -1)
                                            Key.DirectionDown -> moveRecentFocus(index, 1)
                                            else -> false
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

internal data class DesktopRecentPlaybackLabels(
    val title: String,
    val refreshAction: String,
    val clearAction: String,
    val emptyState: String,
)

internal fun desktopRecentPlaybackLabels(): DesktopRecentPlaybackLabels =
    DesktopRecentPlaybackLabels(
        title = "继续观看",
        refreshAction = "刷新",
        clearAction = "清除条目",
        emptyState = "开始播放后会在这里显示最近记录。",
    )

@Composable
private fun RecentProgressRow(
    record: ProgressRecord,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopSelectableRow(selected = selected, onClick = onClick, modifier = modifier) { active ->
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

internal fun moveRecentPlaybackSelection(
    currentIndex: Int,
    itemCount: Int,
    delta: Int,
): Int? {
    return (moveRecentPlaybackFocusTarget(currentIndex, itemCount, delta) as? RecentPlaybackFocusTarget.Row)?.index
}

internal enum class RecentPlaybackAction {
    Refresh,
    Clear,
}

internal sealed interface RecentPlaybackFocusTarget {
    data class Action(val action: RecentPlaybackAction) : RecentPlaybackFocusTarget
    data class Row(val index: Int) : RecentPlaybackFocusTarget
    data object PreviousPanel : RecentPlaybackFocusTarget
    data object NextPanel : RecentPlaybackFocusTarget
}

internal fun moveRecentPlaybackAction(
    current: RecentPlaybackAction,
    delta: Int,
): RecentPlaybackAction? {
    val actions = RecentPlaybackAction.entries
    val targetIndex = current.ordinal + delta
    return actions.getOrNull(targetIndex)
}

internal fun recentPlaybackActionVerticalFocusTarget(
    direction: Int,
    hasRecords: Boolean,
): RecentPlaybackFocusTarget? =
    when {
        direction < 0 -> RecentPlaybackFocusTarget.PreviousPanel
        direction > 0 && hasRecords -> RecentPlaybackFocusTarget.Row(0)
        direction > 0 -> RecentPlaybackFocusTarget.NextPanel
        else -> null
    }

internal fun moveRecentPlaybackFocusTarget(
    currentIndex: Int,
    itemCount: Int,
    delta: Int,
): RecentPlaybackFocusTarget? {
    if (itemCount <= 0) return null
    val targetIndex = currentIndex + delta
    return when {
        targetIndex < 0 -> RecentPlaybackFocusTarget.Action(RecentPlaybackAction.Refresh)
        targetIndex >= itemCount -> RecentPlaybackFocusTarget.NextPanel
        else -> RecentPlaybackFocusTarget.Row(targetIndex)
    }
}

@Composable
internal fun MediaDetailsPanel(
    source: MediaSourceInfo?,
    indexEntry: MediaIndexEntry?,
    remoteEntry: FileEntry?,
    recentRecord: ProgressRecord?,
) {
    val labels = desktopMediaDetailsLabels()
    TvPanel(Modifier.fillMaxWidth()) {
        Text(labels.title, color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        if (source == null && indexEntry == null && remoteEntry == null && recentRecord == null) {
            DesktopEmptyState(
                text = labels.emptyState,
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

internal data class DesktopMediaDetailsLabels(
    val title: String,
    val emptyState: String,
)

internal fun desktopMediaDetailsLabels(): DesktopMediaDetailsLabels =
    DesktopMediaDetailsLabels(
        title = "媒体详情",
        emptyState = "选择媒体后会在这里显示详细信息。",
    )

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
