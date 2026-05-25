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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayFocusAxis
import com.miruplay.tv.design.MiruPlayFocusEdge
import com.miruplay.tv.design.MiruPlayFocusMove
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.design.focusIndexAfter
import com.miruplay.tv.design.focusMoveAfter
import com.miruplay.tv.design.focusTargetAfter
import com.miruplay.tv.design.horizontalNavigationDelta
import com.miruplay.tv.design.splitColumnFocusIndexAfter
import com.miruplay.tv.design.splitColumnSecondColumnStart
import com.miruplay.tv.design.verticalNavigationDelta
import com.miruplay.tv.model.DETAIL_EPISODE_PAGE_SIZE
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MEDIA_DETAILS_PAGE_SIZE
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.RECENT_PLAYBACK_PAGE_SIZE
import com.miruplay.tv.model.activeSeasonOrDefault
import com.miruplay.tv.model.detailBackToLibraryActionLabel
import com.miruplay.tv.model.detailEpisodeBadge
import com.miruplay.tv.model.detailEpisodeCoercedPageStart
import com.miruplay.tv.model.detailEpisodeCountLabel
import com.miruplay.tv.model.detailEpisodeEmptyMessage
import com.miruplay.tv.model.detailEpisodePageStartForIndex
import com.miruplay.tv.model.detailEpisodePageSummary
import com.miruplay.tv.model.detailEpisodeSectionTitle
import com.miruplay.tv.model.detailEpisodeShelfSubtitle
import com.miruplay.tv.model.detailEpisodeTitleLabel
import com.miruplay.tv.model.detailHeroEmptyTitle
import com.miruplay.tv.model.detailHeroEmptySubtitle
import com.miruplay.tv.model.detailHeroStatLabels
import com.miruplay.tv.model.detailPlayActionLabel
import com.miruplay.tv.model.detailSeasonLabel
import com.miruplay.tv.model.formatPlaybackPosition
import com.miruplay.tv.model.mediaDetailsCoercedPageStart
import com.miruplay.tv.model.mediaDetailsPageStartForIndex
import com.miruplay.tv.model.mediaDetailsPageSummary
import com.miruplay.tv.model.playbackProgressRecordLabel
import com.miruplay.tv.model.recentPlaybackCoercedPageStart
import com.miruplay.tv.model.mediaDetailsLabels
import com.miruplay.tv.model.recentPlaybackPageStartForIndex
import com.miruplay.tv.model.recentPlaybackPageSummary
import com.miruplay.tv.model.recentPlaybackLabels
import com.miruplay.tv.model.loadedPlaybackStatus
import com.miruplay.tv.model.resumeStartSecondsText
import com.miruplay.tv.repository.LibraryContinueWatchingEpisode
import com.miruplay.tv.repository.MediaDetailRows
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.displayName
import com.miruplay.tv.repository.mediaFilesOnly
import com.miruplay.tv.repository.mediaDisplayName
import com.miruplay.tv.repository.sortedByMediaIndexEpisodeOrder

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
        key.toMiruPlayInputIntent()?.let { intent ->
            when (intent) {
                MiruPlayInputIntent.DirectionDown -> onFocusRecentPlayback()
                else -> intent.horizontalNavigationDelta()?.let { delta ->
                    moveActionFocus(current, delta)
                } ?: false
            }
        }
            ?: false

    val statLabels = entry
        ?.let {
            detailHeroStatLabels(
                episodeCount = episodeCount,
                seasonNumber = it.seasonNumber,
                metadataSource = it.metadataSource,
            )
        }
        .orEmpty()

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
                    entry?.detailTitle() ?: detailHeroEmptyTitle(),
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.HERO_TITLE_SP.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    entry?.detailSubtitle(source) ?: detailHeroEmptySubtitle(),
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
                        detailPlayActionLabel(),
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
                        detailBackToLibraryActionLabel(),
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
        .desktopNavigationKeyHandler { key -> onMove(action, key) }

internal enum class DesktopDetailHeroAction {
    Play,
    BackToLibrary,
}

internal enum class DesktopDetailFocusPanel {
    Hero,
    EpisodeList,
    BangumiMetadata,
    RecentPlayback,
    MediaDetails,
}

internal fun moveDesktopDetailHeroAction(
    current: DesktopDetailHeroAction,
    delta: Int,
): DesktopDetailHeroAction? =
    DesktopDetailHeroAction.entries.focusTargetAfter(current = current, delta = delta)

internal fun detailHeroActionFocusTarget(
    current: DesktopDetailHeroAction,
    intent: MiruPlayInputIntent,
): DesktopDetailHeroAction? =
    DesktopDetailHeroAction.entries.focusTargetAfter(
        current = current,
        intent = intent,
        axis = MiruPlayFocusAxis.Horizontal,
    )

internal fun detailPanelFocusTarget(
    current: DesktopDetailFocusPanel,
    direction: Int,
    hasRelatedEpisodes: Boolean,
    hasRecentPlayback: Boolean,
): DesktopDetailFocusPanel? =
    when {
        direction < 0 -> detailPreviousPanelFocusTarget(current, hasRelatedEpisodes, hasRecentPlayback)
        direction > 0 -> detailNextPanelFocusTarget(current, hasRelatedEpisodes, hasRecentPlayback)
        else -> null
    }

private fun detailPreviousPanelFocusTarget(
    current: DesktopDetailFocusPanel,
    hasRelatedEpisodes: Boolean,
    hasRecentPlayback: Boolean,
): DesktopDetailFocusPanel? =
    when (current) {
        DesktopDetailFocusPanel.Hero -> null
        DesktopDetailFocusPanel.EpisodeList -> DesktopDetailFocusPanel.Hero
        DesktopDetailFocusPanel.BangumiMetadata -> if (hasRelatedEpisodes) {
            DesktopDetailFocusPanel.EpisodeList
        } else {
            DesktopDetailFocusPanel.Hero
        }
        DesktopDetailFocusPanel.RecentPlayback -> DesktopDetailFocusPanel.BangumiMetadata
        DesktopDetailFocusPanel.MediaDetails -> if (hasRecentPlayback) {
            DesktopDetailFocusPanel.RecentPlayback
        } else {
            DesktopDetailFocusPanel.BangumiMetadata
        }
    }

private fun detailNextPanelFocusTarget(
    current: DesktopDetailFocusPanel,
    hasRelatedEpisodes: Boolean,
    hasRecentPlayback: Boolean,
): DesktopDetailFocusPanel? =
    when (current) {
        DesktopDetailFocusPanel.Hero -> if (hasRelatedEpisodes) {
            DesktopDetailFocusPanel.EpisodeList
        } else {
            DesktopDetailFocusPanel.BangumiMetadata
        }
        DesktopDetailFocusPanel.EpisodeList -> DesktopDetailFocusPanel.BangumiMetadata
        DesktopDetailFocusPanel.BangumiMetadata -> if (hasRecentPlayback) {
            DesktopDetailFocusPanel.RecentPlayback
        } else {
            DesktopDetailFocusPanel.MediaDetails
        }
        DesktopDetailFocusPanel.RecentPlayback -> DesktopDetailFocusPanel.MediaDetails
        DesktopDetailFocusPanel.MediaDetails -> null
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
    val seasonEpisodes = remember(episodes, activeSeason) { detailEpisodesForSeason(episodes, activeSeason) }
    var pageStartState by remember(activeSeason, seasonEpisodes.map { it.path }) { mutableStateOf(0) }
    val pageStart = detailEpisodeCoercedPageStart(
        pageStart = pageStartState,
        itemCount = seasonEpisodes.size,
    )
    val visibleEpisodes = remember(seasonEpisodes, pageStart) {
        seasonEpisodes
            .drop(pageStart)
            .take(DETAIL_EPISODE_PAGE_SIZE)
    }
    val progressByPath = remember(recentRecords) { recentRecords.associateBy { it.episodeId } }
    var pendingEpisodeFocus by remember { mutableStateOf<Int?>(null) }
    val episodeFocusRequesters = remember(pageStart, visibleEpisodes.map { it.path }) {
        List(visibleEpisodes.size) { FocusRequester() }
    }
    val emptyFocusRequester = remember { FocusRequester() }
    val seasonFocusRequesters = remember(seasons) {
        List(seasons.size) { FocusRequester() }
    }
    val activeSeasonIndex = seasons.indexOf(activeSeason).coerceAtLeast(0)
    val selectedEpisodeIndex = seasonEpisodes
        .indexOfFirst { it.path == selectedEntry?.path }
        .coerceAtLeast(0)

    fun requestEpisodePanelFocus(target: DetailEpisodeFocusTarget?): Boolean {
        return when (target) {
            is DetailEpisodeFocusTarget.Row -> {
                val index = target.index.takeIf { it in seasonEpisodes.indices } ?: return false
                val episode = seasonEpisodes[index]
                onEpisodeFocused(episode)
                val targetPageStart = detailEpisodePageStartForIndex(
                    index = index,
                    itemCount = seasonEpisodes.size,
                )
                pageStartState = targetPageStart
                val visibleIndex = index - targetPageStart
                if (targetPageStart == pageStart) {
                    episodeFocusRequesters.getOrNull(visibleIndex)?.requestFocus() ?: return false
                } else {
                    pendingEpisodeFocus = index
                }
                true
            }
            is DetailEpisodeFocusTarget.Season -> {
                val season = seasons.getOrNull(target.index) ?: return false
                onSeasonSelected(season)
                seasonFocusRequesters.getOrNull(target.index)?.requestFocus()
                true
            }
            DetailEpisodeFocusTarget.EmptyState -> {
                if (seasonEpisodes.isEmpty()) {
                    emptyFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
            DetailEpisodeFocusTarget.PreviousPanel -> onFocusPreviousPanel()
            DetailEpisodeFocusTarget.NextPanel -> onFocusNextPanel()
            null -> false
        }
    }

    fun moveEpisodeFocus(currentIndex: Int, intent: MiruPlayInputIntent): Boolean {
        return requestEpisodePanelFocus(
            moveDetailEpisodeFocusTarget(
                currentIndex = currentIndex,
                itemCount = seasonEpisodes.size,
                intent = intent,
                seasonCount = seasons.size,
                activeSeasonIndex = activeSeasonIndex,
            ),
        )
    }

    fun moveSeasonFocus(currentIndex: Int, intent: MiruPlayInputIntent): Boolean =
        requestEpisodePanelFocus(
            detailEpisodeSeasonFocusTarget(
                currentIndex = currentIndex,
                seasonCount = seasons.size,
                episodeCount = seasonEpisodes.size,
                selectedEpisodeIndex = selectedEpisodeIndex,
                intent = intent,
            ),
        )

    LaunchedEffect(pageStart, visibleEpisodes.map { it.path }, pendingEpisodeFocus) {
        val pendingIndex = pendingEpisodeFocus ?: return@LaunchedEffect
        if (pendingIndex in pageStart until pageStart + visibleEpisodes.size) {
            episodeFocusRequesters.getOrNull(pendingIndex - pageStart)?.requestFocus()
            pendingEpisodeFocus = null
        }
    }

    LaunchedEffect(focusVersion, seasonEpisodes.map { it.path }, selectedEntry?.path) {
        if (focusVersion > 0) {
            if (seasonEpisodes.isNotEmpty()) {
                val selectedIndex = seasonEpisodes.indexOfFirst { it.path == selectedEntry?.path }.coerceAtLeast(0)
                requestEpisodePanelFocus(DetailEpisodeFocusTarget.Row(selectedIndex))
            } else {
                emptyFocusRequester.requestFocus()
            }
        }
    }

    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    detailEpisodeSectionTitle(),
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    detailEpisodeShelfSubtitle(episodes.size),
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                )
            }
            if (seasons.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                    seasons.forEachIndexed { index, season ->
                        TvActionButton(
                            text = detailSeasonLabel(season),
                            onClick = { onSeasonSelected(season) },
                            secondary = activeSeason != season,
                            modifier = Modifier
                                .focusRequester(seasonFocusRequesters[index])
                                .desktopNavigationIntentHandler { intent -> moveSeasonFocus(index, intent) }
                                .width(132.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        if (seasonEpisodes.isEmpty()) {
            DetailEpisodeEmptyState(
                text = detailEpisodeEmptyMessage(),
                focusRequester = emptyFocusRequester,
                onMove = ::requestEpisodePanelFocus,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                visibleEpisodes.forEachIndexed { index, episode ->
                    val absoluteIndex = pageStart + index
                    DetailEpisodeRow(
                        entry = episode,
                        selected = selectedEntry?.path == episode.path,
                        progress = progressByPath[episode.path],
                        onClick = {
                            onEpisodeSelected(episode)
                            requestEpisodePanelFocus(DetailEpisodeFocusTarget.Row(absoluteIndex))
                        },
                        modifier = Modifier
                            .focusRequester(episodeFocusRequesters[index]),
                        onNavigationIntent = { intent -> moveEpisodeFocus(absoluteIndex, intent) },
                    )
                }
                detailEpisodePageSummary(
                    pageStart = pageStart,
                    visibleCount = visibleEpisodes.size,
                    itemCount = seasonEpisodes.size,
                )?.let { summary ->
                    Text(
                        summary,
                        color = TextSecondary,
                        fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailEpisodeEmptyState(
    text: String,
    focusRequester: FocusRequester,
    onMove: (DetailEpisodeFocusTarget?) -> Boolean,
) {
    DesktopSelectableRow(
        selected = false,
        onClick = {},
        modifier = Modifier
            .focusRequester(focusRequester),
        heightDp = 180,
        inactiveAlpha = 0.48f,
        onNavigationIntent = { intent ->
            onMove(detailEpisodeEmptyFocusTarget(intent))
        },
    ) { active ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = if (active) TextPrimary else TextSecondary,
                fontSize = MiruPlayUiMetrics.SECTION_BODY_SP.sp,
            )
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
    onNavigationIntent: (MiruPlayInputIntent) -> Boolean = { false },
) {
    DesktopSelectableRow(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        heightDp = 78,
        onNavigationIntent = onNavigationIntent,
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
                    detailEpisodeBadge(entry.episodeNumber),
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    detailEpisodeTitleLabel(entry.episodeNumber, entry.episodeTitle),
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
                playbackProgressRecordLabel(progress),
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

internal fun detailEpisodeSeasons(episodes: List<MediaIndexEntry>): List<Int> =
    episodes
        .map { it.seasonNumber ?: 1 }
        .distinct()
        .sorted()

internal fun detailActiveEpisodeSeason(
    episodes: List<MediaIndexEntry>,
    selectedEntry: MediaIndexEntry?,
    requestedSeason: Int?,
): Int? {
    if (episodes.isEmpty()) return null
    return episodes
        .map { it.toDetailEpisode() }
        .activeSeasonOrDefault(
            requestedSeason = requestedSeason ?: selectedEntry?.seasonNumber,
        )
}

internal fun detailEpisodesForSeason(
    episodes: List<MediaIndexEntry>,
    season: Int?,
): List<MediaIndexEntry> =
    if (season == null) {
        episodes.sortedByMediaIndexEpisodeOrder()
    } else {
        episodes
            .filter { (it.seasonNumber ?: 1) == season }
            .sortedByMediaIndexEpisodeOrder()
    }

private fun MediaIndexEntry.toDetailEpisode(): Episode =
    Episode(
        id = path,
        animeId = animeName.orEmpty(),
        seasonNumber = seasonNumber ?: 1,
        episodeNumber = episodeNumber ?: 1,
        filePath = path,
        fileName = MediaPathConventions.fileName(path),
    )

internal fun moveDetailEpisodeSelection(
    currentIndex: Int,
    itemCount: Int,
    delta: Int,
): Int? {
    return (moveDetailEpisodeFocusTarget(currentIndex, itemCount, delta) as? DetailEpisodeFocusTarget.Row)?.index
}

internal fun moveDetailEpisodeSelection(
    currentIndex: Int,
    itemCount: Int,
    intent: MiruPlayInputIntent,
): Int? {
    return (moveDetailEpisodeFocusTarget(currentIndex, itemCount, intent) as? DetailEpisodeFocusTarget.Row)?.index
}

internal sealed interface DetailEpisodeFocusTarget {
    data class Row(val index: Int) : DetailEpisodeFocusTarget
    data class Season(val index: Int) : DetailEpisodeFocusTarget
    data object EmptyState : DetailEpisodeFocusTarget
    data object PreviousPanel : DetailEpisodeFocusTarget
    data object NextPanel : DetailEpisodeFocusTarget
}

internal fun moveDetailEpisodeFocusTarget(
    currentIndex: Int,
    itemCount: Int,
    delta: Int,
    seasonCount: Int = 0,
    activeSeasonIndex: Int = 0,
): DetailEpisodeFocusTarget? {
    if (itemCount <= 0) return null
    return when (val move = focusMoveAfter(
        currentIndex = currentIndex,
        delta = delta,
        itemCount = itemCount,
    )) {
        is MiruPlayFocusMove.Index -> DetailEpisodeFocusTarget.Row(move.index)
        is MiruPlayFocusMove.Edge -> when (move.edge) {
            MiruPlayFocusEdge.Before -> if (seasonCount > 1) {
                DetailEpisodeFocusTarget.Season(activeSeasonIndex.coerceIn(0, seasonCount - 1))
            } else {
                DetailEpisodeFocusTarget.PreviousPanel
            }
            MiruPlayFocusEdge.After -> DetailEpisodeFocusTarget.NextPanel
        }
        null -> when {
            delta < 0 && seasonCount > 1 ->
                DetailEpisodeFocusTarget.Season(activeSeasonIndex.coerceIn(0, seasonCount - 1))
            delta < 0 -> DetailEpisodeFocusTarget.PreviousPanel
            delta > 0 -> DetailEpisodeFocusTarget.NextPanel
            else -> null
        }
    }
}

internal fun moveDetailEpisodeFocusTarget(
    currentIndex: Int,
    itemCount: Int,
    intent: MiruPlayInputIntent,
    seasonCount: Int = 0,
    activeSeasonIndex: Int = 0,
): DetailEpisodeFocusTarget? =
    intent.verticalNavigationDelta()?.let { delta ->
        moveDetailEpisodeFocusTarget(
            currentIndex = currentIndex,
            itemCount = itemCount,
            delta = delta,
            seasonCount = seasonCount,
            activeSeasonIndex = activeSeasonIndex,
        )
    }

internal fun detailEpisodeSeasonFocusTarget(
    currentIndex: Int,
    seasonCount: Int,
    episodeCount: Int,
    selectedEpisodeIndex: Int,
    key: Key,
): DetailEpisodeFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        detailEpisodeSeasonFocusTarget(
            currentIndex = currentIndex,
            seasonCount = seasonCount,
            episodeCount = episodeCount,
            selectedEpisodeIndex = selectedEpisodeIndex,
            intent = intent,
        )
    }

internal fun detailEpisodeSeasonFocusTarget(
    currentIndex: Int,
    seasonCount: Int,
    episodeCount: Int,
    selectedEpisodeIndex: Int,
    intent: MiruPlayInputIntent,
): DetailEpisodeFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> DetailEpisodeFocusTarget.PreviousPanel
        1 -> if (episodeCount > 0) {
            DetailEpisodeFocusTarget.Row(selectedEpisodeIndex.coerceIn(0, episodeCount - 1))
        } else {
            DetailEpisodeFocusTarget.NextPanel
        }
        else -> focusIndexAfter(
            currentIndex = currentIndex,
            intent = intent,
            axis = MiruPlayFocusAxis.Horizontal,
            itemCount = seasonCount,
        )?.let(DetailEpisodeFocusTarget::Season)
    }

internal fun detailEpisodeEmptyFocusTarget(key: Key): DetailEpisodeFocusTarget? =
    key.toMiruPlayInputIntent()?.let(::detailEpisodeEmptyFocusTarget)

internal fun detailEpisodeEmptyFocusTarget(intent: MiruPlayInputIntent): DetailEpisodeFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> DetailEpisodeFocusTarget.PreviousPanel
        1 -> DetailEpisodeFocusTarget.NextPanel
        else -> null
    }

internal data class DesktopRecentPlaybackItem(
    val progress: ProgressRecord,
    val displayName: String,
    val pathLabel: String,
)

internal fun LibraryContinueWatchingEpisode.toDesktopRecentPlaybackItem(): DesktopRecentPlaybackItem =
    DesktopRecentPlaybackItem(
        progress = progress,
        displayName = episode.title
            .takeIf { it.isNotBlank() }
            ?: episode.fileName.takeIf { it.isNotBlank() }
            ?: anime?.titleCn?.takeIf { it.isNotBlank() }
            ?: anime?.title?.takeIf { it.isNotBlank() }
            ?: progress.mediaDisplayName(),
        pathLabel = episode.filePath.ifBlank { progress.episodeId },
    )

internal fun DesktopRecentPlaybackItem.loadedPlaybackStatus(): String =
    progress.loadedPlaybackStatus(displayName)

internal fun DesktopRecentPlaybackItem.resumeStartSecondsText(): String =
    progress.resumeStartSecondsText()

internal fun DesktopRecentPlaybackItem?.retainedSelectionInRecentPlaybackItems(
    items: List<DesktopRecentPlaybackItem>,
): DesktopRecentPlaybackItem? =
    this?.let { selected ->
        items.firstOrNull { it.progress.episodeId == selected.progress.episodeId }
    }

@Composable
internal fun RecentPlaybackPanel(
    records: List<DesktopRecentPlaybackItem>,
    selectedRecord: DesktopRecentPlaybackItem?,
    status: String,
    focusVersion: Int,
    onFocusPreviousPanel: () -> Boolean,
    onFocusNextPanel: () -> Boolean,
    onRefresh: () -> Unit,
    onRecordSelected: (DesktopRecentPlaybackItem) -> Unit,
    onClearSelected: () -> Unit,
) {
    var pageStartState by remember(records.map { it.progress.episodeId }) { mutableStateOf(0) }
    val pageStart = recentPlaybackCoercedPageStart(
        pageStart = pageStartState,
        itemCount = records.size,
    )
    val visibleRecords = remember(records, pageStart) {
        records
            .drop(pageStart)
            .take(RECENT_PLAYBACK_PAGE_SIZE)
    }
    var pendingRecordFocus by remember { mutableStateOf<Int?>(null) }
    val labels = recentPlaybackLabels()
    val recordFocusRequesters = remember(pageStart, visibleRecords.map { it.progress.episodeId }) {
        List(visibleRecords.size) { FocusRequester() }
    }
    val actionFocusRequesters = remember {
        mapOf(
            RecentPlaybackAction.Refresh to FocusRequester(),
            RecentPlaybackAction.Clear to FocusRequester(),
        )
    }
    val emptyFocusRequester = remember { FocusRequester() }

    fun requestRecentFocus(target: RecentPlaybackFocusTarget?): Boolean {
        return when (target) {
            is RecentPlaybackFocusTarget.Action -> {
                actionFocusRequesters.getValue(target.action).requestFocus()
                true
            }
            is RecentPlaybackFocusTarget.Row -> {
                val index = target.index.takeIf { it in records.indices } ?: return false
                val targetPageStart = recentPlaybackPageStartForIndex(
                    index = index,
                    itemCount = records.size,
                )
                pageStartState = targetPageStart
                val visibleIndex = index - targetPageStart
                if (targetPageStart == pageStart) {
                    recordFocusRequesters.getOrNull(visibleIndex)?.requestFocus() ?: return false
                } else {
                    pendingRecordFocus = index
                }
                true
            }
            RecentPlaybackFocusTarget.EmptyState -> {
                if (visibleRecords.isEmpty()) {
                    emptyFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
            RecentPlaybackFocusTarget.PreviousPanel -> onFocusPreviousPanel()
            RecentPlaybackFocusTarget.NextPanel -> onFocusNextPanel()
            null -> false
        }
    }

    fun moveRecentFocus(currentIndex: Int, intent: MiruPlayInputIntent): Boolean =
        requestRecentFocus(moveRecentPlaybackFocusTarget(currentIndex, records.size, intent))

    fun moveRecentActionFocus(current: RecentPlaybackAction, intent: MiruPlayInputIntent): Boolean =
        requestRecentFocus(recentPlaybackActionFocusTarget(current, intent, hasRecords = records.isNotEmpty()))

    fun moveRecentEmptyFocus(intent: MiruPlayInputIntent): Boolean =
        requestRecentFocus(recentPlaybackEmptyFocusTarget(intent))

    LaunchedEffect(pageStart, visibleRecords.map { it.progress.episodeId }, pendingRecordFocus) {
        val pendingIndex = pendingRecordFocus ?: return@LaunchedEffect
        if (pendingIndex in pageStart until pageStart + visibleRecords.size) {
            recordFocusRequesters.getOrNull(pendingIndex - pageStart)?.requestFocus()
            pendingRecordFocus = null
        }
    }

    LaunchedEffect(focusVersion, records.map { it.progress.episodeId }) {
        if (focusVersion > 0) {
            if (records.isNotEmpty()) {
                requestRecentFocus(RecentPlaybackFocusTarget.Row(0))
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
                            .desktopNavigationIntentHandler { intent -> moveRecentActionFocus(RecentPlaybackAction.Refresh, intent) },
                    )
                    TvActionButton(
                        labels.clearAction,
                        onClick = onClearSelected,
                        secondary = true,
                        modifier = Modifier
                            .focusRequester(actionFocusRequesters.getValue(RecentPlaybackAction.Clear))
                            .desktopNavigationIntentHandler { intent -> moveRecentActionFocus(RecentPlaybackAction.Clear, intent) },
                    )
                }
                StatusBox(status)
            }
            Column(
                modifier = Modifier.weight(0.68f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                if (records.isEmpty()) {
                    RecentPlaybackEmptyState(
                        text = labels.emptyState,
                        focusRequester = emptyFocusRequester,
                        onMove = ::moveRecentEmptyFocus,
                    )
                } else {
                    visibleRecords.forEachIndexed { index, record ->
                        val absoluteIndex = pageStart + index
                        RecentProgressRow(
                            record = record,
                            selected = selectedRecord?.progress?.episodeId == record.progress.episodeId,
                            onClick = {
                                onRecordSelected(record)
                                requestRecentFocus(RecentPlaybackFocusTarget.Row(absoluteIndex))
                            },
                            modifier = Modifier
                                .focusRequester(recordFocusRequesters[index]),
                            onNavigationIntent = { intent -> moveRecentFocus(absoluteIndex, intent) },
                        )
                    }
                    recentPlaybackPageSummary(
                        pageStart = pageStart,
                        visibleCount = visibleRecords.size,
                        itemCount = records.size,
                    )?.let { summary ->
                        Text(
                            summary,
                            color = TextSecondary,
                            fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentPlaybackEmptyState(
    text: String,
    focusRequester: FocusRequester,
    onMove: (MiruPlayInputIntent) -> Boolean,
) {
    DesktopSelectableRow(
        selected = false,
        onClick = {},
        modifier = Modifier
            .focusRequester(focusRequester),
        heightDp = MiruPlayUiMetrics.EMPTY_STATE_HEIGHT_DP,
        inactiveAlpha = 0.48f,
        onNavigationIntent = onMove,
    ) { active ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = if (active) TextPrimary else TextSecondary,
                fontSize = MiruPlayUiMetrics.SECTION_BODY_SP.sp,
            )
        }
    }
}

@Composable
private fun RecentProgressRow(
    record: DesktopRecentPlaybackItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigationIntent: (MiruPlayInputIntent) -> Boolean = { false },
) {
    DesktopSelectableRow(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        onNavigationIntent = onNavigationIntent,
    ) { active ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatPlaybackPosition(record.progress.positionMs),
                color = if (active) AnimeRed else TextSecondary,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    record.displayName,
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    record.pathLabel,
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("x${record.progress.playCount}", color = TextSecondary, fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp)
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

internal fun moveRecentPlaybackSelection(
    currentIndex: Int,
    itemCount: Int,
    intent: MiruPlayInputIntent,
): Int? {
    return (moveRecentPlaybackFocusTarget(currentIndex, itemCount, intent) as? RecentPlaybackFocusTarget.Row)?.index
}

internal enum class RecentPlaybackAction {
    Refresh,
    Clear,
}

internal sealed interface RecentPlaybackFocusTarget {
    data class Action(val action: RecentPlaybackAction) : RecentPlaybackFocusTarget
    data class Row(val index: Int) : RecentPlaybackFocusTarget
    data object EmptyState : RecentPlaybackFocusTarget
    data object PreviousPanel : RecentPlaybackFocusTarget
    data object NextPanel : RecentPlaybackFocusTarget
}

internal fun moveRecentPlaybackAction(
    current: RecentPlaybackAction,
    delta: Int,
): RecentPlaybackAction? =
    RecentPlaybackAction.entries.focusTargetAfter(current = current, delta = delta)

internal fun recentPlaybackActionVerticalFocusTarget(
    direction: Int,
    hasRecords: Boolean,
): RecentPlaybackFocusTarget? =
    when {
        direction < 0 -> RecentPlaybackFocusTarget.PreviousPanel
        direction > 0 && hasRecords -> RecentPlaybackFocusTarget.Row(0)
        direction > 0 -> RecentPlaybackFocusTarget.EmptyState
        else -> null
    }

internal fun recentPlaybackActionFocusTarget(
    current: RecentPlaybackAction,
    key: Key,
    hasRecords: Boolean,
): RecentPlaybackFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        recentPlaybackActionFocusTarget(current, intent, hasRecords)
    }

internal fun recentPlaybackActionFocusTarget(
    current: RecentPlaybackAction,
    intent: MiruPlayInputIntent,
    hasRecords: Boolean,
): RecentPlaybackFocusTarget? =
    intent.horizontalNavigationDelta()
        ?.let { delta -> moveRecentPlaybackAction(current, delta)?.let(RecentPlaybackFocusTarget::Action) }
        ?: intent.verticalNavigationDelta()?.let { direction ->
            recentPlaybackActionVerticalFocusTarget(direction, hasRecords)
        }

internal fun recentPlaybackEmptyFocusTarget(key: Key): RecentPlaybackFocusTarget? =
    key.toMiruPlayInputIntent()?.let(::recentPlaybackEmptyFocusTarget)

internal fun recentPlaybackEmptyFocusTarget(intent: MiruPlayInputIntent): RecentPlaybackFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> RecentPlaybackFocusTarget.Action(RecentPlaybackAction.Refresh)
        1 -> RecentPlaybackFocusTarget.NextPanel
        else -> null
    }

internal fun moveRecentPlaybackFocusTarget(
    currentIndex: Int,
    itemCount: Int,
    delta: Int,
): RecentPlaybackFocusTarget? {
    if (itemCount <= 0) return null
    return focusIndexAfter(
        currentIndex = currentIndex,
        delta = delta,
        itemCount = itemCount,
    )?.let(RecentPlaybackFocusTarget::Row) ?: when {
        delta < 0 -> RecentPlaybackFocusTarget.Action(RecentPlaybackAction.Refresh)
        delta > 0 -> RecentPlaybackFocusTarget.NextPanel
        else -> null
    }
}

internal fun moveRecentPlaybackFocusTarget(
    currentIndex: Int,
    itemCount: Int,
    intent: MiruPlayInputIntent,
): RecentPlaybackFocusTarget? =
    intent.verticalNavigationDelta()?.let { delta ->
        moveRecentPlaybackFocusTarget(currentIndex, itemCount, delta)
    }

@Composable
internal fun MediaDetailsPanel(
    source: MediaSourceInfo?,
    indexEntry: MediaIndexEntry?,
    remoteEntry: FileEntry?,
    recentRecord: ProgressRecord?,
    focusVersion: Int = 0,
    onFocusPreviousPanel: () -> Boolean = { false },
) {
    val labels = mediaDetailsLabels()
    val rows = remember(source, indexEntry, remoteEntry, recentRecord) {
        MediaDetailRows.build(
            source = source,
            indexEntry = indexEntry,
            remoteEntry = remoteEntry,
            recentRecord = recentRecord,
        )
    }
    var pageStartState by remember(rows.map { it.label to it.value }) { mutableStateOf(0) }
    val pageStart = mediaDetailsCoercedPageStart(
        pageStart = pageStartState,
        itemCount = rows.size,
    )
    val visibleRows = remember(rows, pageStart) {
        rows
            .drop(pageStart)
            .take(MEDIA_DETAILS_PAGE_SIZE)
    }
    val splitIndex = mediaDetailsSplitIndex(pageStart, visibleRows.size)
    val visibleSplitCount = splitIndex - pageStart
    val focusRequesters = remember(pageStart, visibleRows.map { it.label to it.value }) {
        List(visibleRows.size) { FocusRequester() }
    }
    var pendingRowFocus by remember { mutableStateOf<Int?>(null) }
    val emptyFocusRequester = remember { FocusRequester() }

    fun requestMediaDetailFocus(target: MediaDetailsFocusTarget?): Boolean {
        return when (target) {
            is MediaDetailsFocusTarget.Row -> {
                val index = target.index.takeIf { it in rows.indices } ?: return false
                val targetPageStart = mediaDetailsPageStartForIndex(
                    index = index,
                    itemCount = rows.size,
                )
                pageStartState = targetPageStart
                val visibleIndex = index - targetPageStart
                if (targetPageStart == pageStart) {
                    focusRequesters.getOrNull(visibleIndex)?.requestFocus() ?: return false
                } else {
                    pendingRowFocus = index
                }
                true
            }
            MediaDetailsFocusTarget.EmptyState -> {
                emptyFocusRequester.requestFocus()
                true
            }
            MediaDetailsFocusTarget.PreviousPanel -> onFocusPreviousPanel()
            null -> false
        }
    }

    fun moveMediaDetailFocus(currentIndex: Int, key: Key): Boolean {
        return requestMediaDetailFocus(
            mediaDetailsFocusTarget(
                currentIndex = currentIndex,
                rowCount = rows.size,
                pageStart = pageStart,
                visibleCount = visibleRows.size,
                key = key,
            ),
        )
    }

    LaunchedEffect(pageStart, visibleRows.map { it.label to it.value }, pendingRowFocus) {
        val pendingIndex = pendingRowFocus ?: return@LaunchedEffect
        if (pendingIndex in pageStart until pageStart + visibleRows.size) {
            focusRequesters.getOrNull(pendingIndex - pageStart)?.requestFocus()
            pendingRowFocus = null
        }
    }

    LaunchedEffect(focusVersion) {
        if (focusVersion > 0) {
            requestMediaDetailFocus(
                mediaDetailsInitialFocusTarget(
                    hasRows = rows.isNotEmpty() && (source != null || indexEntry != null || remoteEntry != null || recentRecord != null),
                ),
            )
        }
    }

    TvPanel(Modifier.fillMaxWidth()) {
        Text(labels.title, color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        if (rows.isEmpty() || (source == null && indexEntry == null && remoteEntry == null && recentRecord == null)) {
            MediaDetailsEmptyState(
                text = labels.emptyState,
                modifier = Modifier
                    .focusRequester(emptyFocusRequester),
                onNavigationKey = { key ->
                    requestMediaDetailFocus(mediaDetailsEmptyFocusTarget(key))
                },
            )
            return@TvPanel
        }

        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
            Column(
                modifier = Modifier.weight(0.5f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SMALL_GAP_DP.dp),
            ) {
                visibleRows.take(visibleSplitCount).forEachIndexed { index, row ->
                    val rowIndex = pageStart + index
                    DetailLine(
                        row.label,
                        row.value,
                        modifier = Modifier
                            .focusRequester(focusRequesters[index]),
                        onNavigationKey = { key ->
                            moveMediaDetailFocus(rowIndex, key)
                        },
                    )
                }
            }
            Column(
                modifier = Modifier.weight(0.5f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SMALL_GAP_DP.dp),
            ) {
                visibleRows.drop(visibleSplitCount).forEachIndexed { index, row ->
                    val visibleIndex = visibleSplitCount + index
                    val rowIndex = pageStart + visibleIndex
                    DetailLine(
                        row.label,
                        row.value,
                        modifier = Modifier
                            .focusRequester(focusRequesters[visibleIndex]),
                        onNavigationKey = { key ->
                            moveMediaDetailFocus(rowIndex, key)
                        },
                    )
                }
            }
        }
        mediaDetailsPageSummary(
            pageStart = pageStart,
            visibleCount = visibleRows.size,
            itemCount = rows.size,
        )?.let { summary ->
            Spacer(Modifier.height(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp))
            Text(
                summary,
                color = TextSecondary,
                fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
            )
        }
    }
}

internal sealed interface MediaDetailsFocusTarget {
    data class Row(val index: Int) : MediaDetailsFocusTarget
    data object EmptyState : MediaDetailsFocusTarget
    data object PreviousPanel : MediaDetailsFocusTarget
}

internal fun mediaDetailsInitialFocusTarget(hasRows: Boolean): MediaDetailsFocusTarget =
    if (hasRows) MediaDetailsFocusTarget.Row(0) else MediaDetailsFocusTarget.EmptyState

internal fun mediaDetailsEmptyFocusTarget(key: Key): MediaDetailsFocusTarget? =
    key.toMiruPlayInputIntent()?.let(::mediaDetailsEmptyFocusTarget)

internal fun mediaDetailsEmptyFocusTarget(intent: MiruPlayInputIntent): MediaDetailsFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> MediaDetailsFocusTarget.PreviousPanel
        else -> null
    }

internal fun mediaDetailsFocusTarget(
    currentIndex: Int,
    rowCount: Int,
    pageStart: Int,
    visibleCount: Int,
    key: Key,
): MediaDetailsFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        mediaDetailsFocusTarget(
            currentIndex = currentIndex,
            rowCount = rowCount,
            pageStart = pageStart,
            visibleCount = visibleCount,
            intent = intent,
        )
    }

internal fun mediaDetailsFocusTarget(
    currentIndex: Int,
    rowCount: Int,
    pageStart: Int,
    visibleCount: Int,
    intent: MiruPlayInputIntent,
): MediaDetailsFocusTarget? {
    if (rowCount <= 0) return null
    val safePageStart = mediaDetailsCoercedPageStart(pageStart, rowCount)
    val safeVisibleCount = visibleCount.coerceIn(1, rowCount - safePageStart)
    return splitColumnFocusIndexAfter(
        currentIndex = currentIndex,
        intent = intent,
        pageStart = safePageStart,
        visibleCount = safeVisibleCount,
        itemCount = rowCount,
    )?.let(MediaDetailsFocusTarget::Row)
        ?: MediaDetailsFocusTarget.PreviousPanel.takeIf {
            currentIndex == 0 && intent.verticalNavigationDelta() == -1
        }
}

internal fun mediaDetailsSplitIndex(pageStart: Int, visibleCount: Int): Int {
    return splitColumnSecondColumnStart(pageStart = pageStart, visibleCount = visibleCount)
}

@Composable
private fun MediaDetailsEmptyState(
    text: String,
    modifier: Modifier = Modifier,
    onNavigationKey: (Key) -> Boolean = { false },
) {
    DesktopSelectableRow(
        selected = false,
        onClick = {},
        modifier = modifier,
        heightDp = MiruPlayUiMetrics.DETAIL_PREVIEW_HEIGHT_DP,
        inactiveAlpha = 0.48f,
        onNavigationKey = onNavigationKey,
    ) { active ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = if (active) TextPrimary else TextSecondary,
                fontSize = MiruPlayUiMetrics.SECTION_BODY_SP.sp,
            )
        }
    }
}

@Composable
internal fun DetailLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onNavigationKey: (Key) -> Boolean = { false },
) {
    DesktopSelectableRow(
        selected = false,
        onClick = {},
        modifier = modifier,
        heightDp = 92,
        inactiveAlpha = 0.42f,
        onNavigationKey = onNavigationKey,
    ) { active ->
        Column(Modifier.fillMaxWidth()) {
            Text(label, color = TextSecondary, fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp, fontWeight = FontWeight.SemiBold)
            Text(
                value,
                color = if (active) TextPrimary else TextPrimary.copy(alpha = 0.84f),
                fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
