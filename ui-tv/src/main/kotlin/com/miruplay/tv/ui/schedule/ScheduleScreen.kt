package com.miruplay.tv.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.model.scheduleCachedBadgeLabel
import com.miruplay.tv.model.scheduleConfidenceLabel
import com.miruplay.tv.model.scheduleCrossDayNextDayLabel
import com.miruplay.tv.model.scheduleCrossDayPreviousDayLabel
import com.miruplay.tv.model.scheduleEmptyDayLabel
import com.miruplay.tv.model.scheduleEmptyDescription
import com.miruplay.tv.model.scheduleEmptyTitle
import com.miruplay.tv.model.scheduleErrorMessage
import com.miruplay.tv.model.scheduleErrorTitle
import com.miruplay.tv.model.scheduleGoSettingsActionLabel
import com.miruplay.tv.model.scheduleJstTimeLabel
import com.miruplay.tv.model.scheduleLoadingMessage
import com.miruplay.tv.model.scheduleLocalTimeLabel
import com.miruplay.tv.model.scheduleLocalZoneNote
import com.miruplay.tv.model.scheduleMatchedLabel
import com.miruplay.tv.model.scheduleMatchedSummary
import com.miruplay.tv.model.scheduleOpenDetailHint
import com.miruplay.tv.model.scheduleRefreshActionLabel
import com.miruplay.tv.model.scheduleRetryActionLabel
import com.miruplay.tv.model.scheduleBackActionLabel
import com.miruplay.tv.model.scheduleSeasonLabel
import com.miruplay.tv.model.scheduleSkippedItemsLabel
import com.miruplay.tv.model.scheduleSourceCountLabel
import com.miruplay.tv.model.scheduleScreenSubtitleLabel
import com.miruplay.tv.model.scheduleScreenTitleLabel
import com.miruplay.tv.model.scheduleStaleBannerLabel
import com.miruplay.tv.model.scheduleTodayLabel
import com.miruplay.tv.model.scheduleUnconfiguredDescription
import com.miruplay.tv.model.scheduleUnconfiguredTitle
import com.miruplay.tv.model.scheduleUnmatchedLabel
import com.miruplay.tv.model.scheduleUpdatedAtLabel
import com.miruplay.tv.model.scheduleWeekdayLabel
import com.miruplay.tv.ui.common.resolve
import com.miruplay.tv.ui.components.InitialFocusHandle
import com.miruplay.tv.ui.components.LoadingIndicator
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.RemoteImage
import com.miruplay.tv.ui.components.TvButton
import com.miruplay.tv.ui.components.rememberInitialFocusHandle
import com.miruplay.tv.ui.components.tvFocusableClickable
import com.miruplay.tv.ui.theme.AccentBlue
import com.miruplay.tv.ui.theme.AnimeRed
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.FocusBorder
import com.miruplay.tv.ui.theme.ProgressGreen
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography
import com.miruplay.tv.ui.theme.WarningYellow

/**
 * 新番时间表：按设备本地时区的周一 … 周日分组、组内按本地时间升序。
 *
 * - 未配置数据源 → 引导去设置；
 * - 服务端 404 无数据 → 空态；请求失败且有缓存 → 旧数据 + 提示条；
 * - 匹配到本地媒体库（Bangumi subject id）的行按确定键进详情，未匹配显示「未在媒体库」。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    OverscanContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            ScheduleHeader(
                showRefresh = state !is ScheduleUiState.Loading,
                onRefresh = viewModel::refresh,
                onNavigateBack = onNavigateBack,
            )

            when (val current = state) {
                ScheduleUiState.Loading -> LoadingState()
                ScheduleUiState.Unconfigured -> UnconfiguredState(
                    onNavigateToSettings = onNavigateToSettings,
                )
                ScheduleUiState.Empty -> EmptyState(onRetry = viewModel::retry)
                is ScheduleUiState.Error -> ErrorState(
                    state = current,
                    onRetry = viewModel::retry,
                    onNavigateToSettings = onNavigateToSettings,
                )
                is ScheduleUiState.Content -> ScheduleContent(
                    content = current,
                    onNavigateToDetail = onNavigateToDetail,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScheduleHeader(
    showRefresh: Boolean,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scheduleScreenTitleLabel().resolve(),
                style = TvTypography.title,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = scheduleScreenSubtitleLabel().resolve(),
                style = TvTypography.body,
                color = TextSecondary,
            )
        }
        if (showRefresh) {
            TvButton(
                text = scheduleRefreshActionLabel().resolve(),
                icon = Icons.Filled.Refresh,
                onClick = onRefresh,
                modifier = Modifier.width(150.dp),
            )
        }
        TvButton(
            text = scheduleBackActionLabel().resolve(),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onNavigateBack,
            modifier = Modifier.width(150.dp),
            secondary = true,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(64.dp)) { LoadingIndicator() }
            Spacer(Modifier.height(16.dp))
            Text(
                text = scheduleLoadingMessage().resolve(),
                style = TvTypography.body,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun UnconfiguredState(onNavigateToSettings: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = scheduleUnconfiguredTitle().resolve(),
                style = TvTypography.subtitle,
                color = TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = scheduleUnconfiguredDescription().resolve(),
                style = TvTypography.body,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 64.dp),
            )
            Spacer(Modifier.height(20.dp))
            TvButton(
                text = scheduleGoSettingsActionLabel().resolve(),
                icon = Icons.Filled.Settings,
                onClick = onNavigateToSettings,
                modifier = Modifier.width(220.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = scheduleEmptyTitle().resolve(),
                style = TvTypography.subtitle,
                color = TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = scheduleEmptyDescription().resolve(),
                style = TvTypography.body,
                color = TextSecondary,
            )
            Spacer(Modifier.height(20.dp))
            TvButton(
                text = scheduleRetryActionLabel().resolve(),
                icon = Icons.Filled.Refresh,
                onClick = onRetry,
                modifier = Modifier.width(180.dp),
            )
        }
    }
}

@Composable
private fun ErrorState(
    state: ScheduleUiState.Error,
    onRetry: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = scheduleErrorTitle().resolve(),
                style = TvTypography.subtitle,
                color = WarningYellow,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = scheduleErrorMessage(state.error).resolve(),
                style = TvTypography.body,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 64.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = scheduleRetryActionLabel().resolve(),
                    icon = Icons.Filled.Refresh,
                    onClick = onRetry,
                    modifier = Modifier.width(180.dp),
                )
                TvButton(
                    text = scheduleGoSettingsActionLabel().resolve(),
                    icon = Icons.Filled.Settings,
                    onClick = onNavigateToSettings,
                    modifier = Modifier.width(220.dp),
                    secondary = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScheduleContent(
    content: ScheduleUiState.Content,
    onNavigateToDetail: (String) -> Unit,
) {
    val firstNonEmptyDay = content.days.firstOrNull { it.rows.isNotEmpty() }
    val firstRowKey = firstNonEmptyDay?.rows?.firstOrNull()?.airing?.subjectId
    val initialFocus = rememberInitialFocusHandle(
        key = firstRowKey,
        enabled = firstRowKey != null,
        initialDelayMillis = 300,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        ContentMetaRow(content)
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            content.days.forEach { day ->
                item(key = "day-${day.weekday}") {
                    DayHeader(day = day)
                }
                if (day.rows.isEmpty()) {
                    item(key = "empty-${day.weekday}") {
                        EmptyDayRow()
                    }
                } else {
                    itemsIndexed(
                        items = day.rows,
                        key = { index, row -> "entry-${day.weekday}-${row.airing.subjectId}-$index" },
                    ) { index, row ->
                        val isFirstRow = day.weekday == firstNonEmptyDay?.weekday && index == 0
                        ScheduleRow(
                            row = row,
                            initialFocus = if (isFirstRow) initialFocus else null,
                            onOpenDetail = onNavigateToDetail,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContentMetaRow(content: ScheduleUiState.Content) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scheduleSeasonLabel(
                    season = content.season,
                    seasonStart = content.seasonStart,
                    seasonEnd = content.seasonEnd,
                )?.resolve() ?: "",
                style = TvTypography.subtitle,
                color = TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                content.generatedAtLocal?.let { generatedAt ->
                    Text(
                        text = scheduleUpdatedAtLabel(generatedAt).resolve(),
                        style = TvTypography.caption,
                        color = TextSecondary,
                    )
                }
                Text(
                    text = scheduleLocalZoneNote(content.localTimezone).resolve(),
                    style = TvTypography.caption,
                    color = TextSecondary,
                )
                Text(
                    text = scheduleMatchedSummary(content.matchedCount, content.totalCount).resolve(),
                    style = TvTypography.caption,
                    color = if (content.matchedCount > 0) ProgressGreen else TextSecondary,
                )
                if (content.skippedItemCount > 0) {
                    Text(
                        text = scheduleSkippedItemsLabel(content.skippedItemCount).resolve(),
                        style = TvTypography.caption,
                        color = WarningYellow,
                    )
                }
            }
        }
        if (content.fromCache) {
            Text(
                text = scheduleCachedBadgeLabel().resolve(),
                style = TvTypography.caption,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurface)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
    if (content.stale) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = scheduleStaleBannerLabel().resolve(),
            style = TvTypography.caption,
            color = WarningYellow,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DayHeader(day: ScheduleDayUi) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = scheduleWeekdayLabel(day.weekday).resolve(),
            style = TvTypography.subtitle,
            color = if (day.isToday) AnimeRed else TextPrimary,
        )
        if (day.isToday) {
            Text(
                text = scheduleTodayLabel().resolve(),
                style = TvTypography.caption,
                color = Color.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AnimeRed)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            text = "${day.rows.size}",
            style = TvTypography.caption,
            color = TextSecondary,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EmptyDayRow() {
    Text(
        text = scheduleEmptyDayLabel().resolve(),
        style = TvTypography.caption,
        color = TextSecondary.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScheduleRow(
    row: ScheduleRowUi,
    initialFocus: InitialFocusHandle?,
    onOpenDetail: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val airing = row.airing
    val borderColor = if (focused) FocusBorder else Color.White.copy(alpha = 0.10f)
    val backgroundColor = if (focused) AccentBlue else DarkSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(if (focused) 3.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .then(initialFocus?.modifier() ?: Modifier)
            .tvFocusableClickable(
                interactionSource = interactionSource,
                onClick = { row.matchedAnimeId?.let(onOpenDetail) },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.width(186.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = scheduleLocalTimeLabel(airing.localWeekday, airing.localTimeLabel).resolve(),
                    style = TvTypography.subtitle,
                    color = if (focused) Color.White else AnimeRed,
                    maxLines = 1,
                )
                val crossDayLabel = when {
                    airing.crossDayOffset > 0 -> scheduleCrossDayNextDayLabel()
                    airing.crossDayOffset < 0 -> scheduleCrossDayPreviousDayLabel()
                    else -> null
                }
                if (crossDayLabel != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = crossDayLabel.resolve(),
                        style = TvTypography.caption,
                        color = WarningYellow,
                    )
                }
            }
            Text(
                text = scheduleJstTimeLabel(airing.jstWeekday, airing.jstTimeLabel).resolve(),
                style = TvTypography.caption,
                color = if (focused) Color.White.copy(alpha = 0.82f) else TextSecondary,
                maxLines = 1,
            )
        }

        RemoteImage(
            url = airing.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .width(46.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(6.dp)),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = airing.displayName,
                style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (focused) Color.White else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (airing.originalName.isNotBlank() && airing.originalName != airing.displayName) {
                Text(
                    text = airing.originalName,
                    style = TvTypography.caption,
                    color = if (focused) Color.White.copy(alpha = 0.82f) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = scheduleConfidenceLabel(airing.confidence).resolve(),
                    style = TvTypography.caption,
                    color = if (focused) Color.White.copy(alpha = 0.82f) else TextSecondary,
                )
                if (airing.sourceCount > 0) {
                    Text(
                        text = scheduleSourceCountLabel(airing.sourceCount).resolve(),
                        style = TvTypography.caption,
                        color = if (focused) Color.White.copy(alpha = 0.82f) else TextSecondary,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.width(260.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (row.isMatched) {
                Text(
                    text = scheduleMatchedLabel().resolve(),
                    style = TvTypography.caption,
                    color = if (focused) Color.White else ProgressGreen,
                    maxLines = 1,
                )
                row.matchedAnimeTitle?.let { title ->
                    Text(
                        text = title,
                        style = TvTypography.caption,
                        color = if (focused) Color.White.copy(alpha = 0.9f) else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = scheduleOpenDetailHint().resolve(),
                    style = TvTypography.caption,
                    color = if (focused) Color.White.copy(alpha = 0.82f) else TextSecondary.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            } else {
                Text(
                    text = scheduleUnmatchedLabel().resolve(),
                    style = TvTypography.caption,
                    color = if (focused) Color.White else WarningYellow,
                    maxLines = 1,
                )
            }
        }
    }
}
