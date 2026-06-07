package com.miruplay.tv.ui.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.libraryHasSourcesEmptyMessage
import com.miruplay.tv.model.libraryCancelScanActionLabel
import com.miruplay.tv.model.libraryCollectedCountLabel
import com.miruplay.tv.model.libraryContinueWatchingSubtitle
import com.miruplay.tv.model.libraryContinueWatchingSectionTitle
import com.miruplay.tv.model.libraryFeaturedSectionTitle
import com.miruplay.tv.model.libraryFilesScannedLabel
import com.miruplay.tv.model.libraryManualScanActionLabel
import com.miruplay.tv.model.libraryNoSourcesMessage
import com.miruplay.tv.model.libraryPosterWallSectionTitle
import com.miruplay.tv.model.libraryRecentlyAddedSectionTitle
import com.miruplay.tv.model.libraryScanActionLabel
import com.miruplay.tv.model.libraryScanNowActionLabel
import com.miruplay.tv.model.libraryScanningTitle
import com.miruplay.tv.model.librarySettingsActionLabel
import com.miruplay.tv.scanner.LibraryScanState
import com.miruplay.tv.ui.components.AnimePosterCard
import com.miruplay.tv.ui.components.FeatureAnimeCard
import com.miruplay.tv.ui.components.LoadingIndicator
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.TvButton
import com.miruplay.tv.ui.theme.AnimeRed
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DramaLibraryScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: DramaLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val activeScan = scanState as? LibraryScanState.Scanning
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh(showLoading = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    OverscanContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            DramaLibraryHeader(
                activeScan = activeScan,
                onScan = viewModel::scanNow,
                onCancelScan = viewModel::cancelScan,
                onNavigateToSettings = onNavigateToSettings,
            )

            when (val uiState = state) {
                DramaLibraryUiState.Loading -> LoadingIndicator()
                DramaLibraryUiState.NoSources -> {
                    DramaEmptyState(
                        message = libraryNoSourcesMessage(),
                        buttonText = com.miruplay.tv.model.libraryAddSourceActionLabel(),
                        onClick = onNavigateToSettings,
                    )
                }
                DramaLibraryUiState.HasSources -> {
                    DramaEmptyState(
                        message = libraryHasSourcesEmptyMessage(),
                        buttonText = libraryScanNowActionLabel(),
                        onClick = viewModel::scanNow,
                    )
                }
                is DramaLibraryUiState.ScanError -> {
                    DramaEmptyState(
                        message = uiState.message,
                        buttonText = libraryManualScanActionLabel(),
                        onClick = viewModel::scanNow,
                    )
                }
                is DramaLibraryUiState.Ready -> {
                    DramaLibraryContent(
                        state = uiState,
                        onNavigateToDetail = onNavigateToDetail,
                    )
                }
            }
        }
    }
}

@Composable
private fun DramaLibraryHeader(
    activeScan: LibraryScanState.Scanning?,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.widthIn(min = 188.dp),
        ) {
            Text(
                text = "电视剧",
                style = TvTypography.title,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "电视剧媒体库 · TMDB 元数据",
                style = TvTypography.body,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (activeScan != null) {
            DramaScanProgressBanner(
                state = activeScan,
                onCancel = onCancelScan,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (activeScan == null) {
                TvButton(
                    text = libraryScanActionLabel(),
                    icon = Icons.Filled.Refresh,
                    onClick = onScan,
                    modifier = Modifier.width(132.dp),
                )
            }
            TvButton(
                text = librarySettingsActionLabel(),
                onClick = onNavigateToSettings,
                modifier = Modifier.width(132.dp),
            )
        }
    }
}

@Composable
private fun DramaEmptyState(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = TextSecondary,
            style = TvTypography.subtitle,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        TvButton(text = buttonText, onClick = onClick)
    }
}

@Composable
private fun DramaScanProgressBanner(
    state: LibraryScanState.Scanning,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
) {
    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .background(DarkSurface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .border(1.dp, AnimeRed.copy(alpha = 0.42f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoadingIndicator(
            modifier = Modifier.size(34.dp),
            color = AnimeRed,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = libraryScanningTitle(),
                color = TextPrimary,
                style = TvTypography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${libraryFilesScannedLabel(state.filesScanned)} · ${state.currentPath.ifBlank { "电视剧媒体源" }}",
                color = TextSecondary,
                style = TvTypography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.canCancel) {
            TvButton(
                text = libraryCancelScanActionLabel(),
                onClick = onCancel,
                modifier = Modifier.width(132.dp),
                secondary = true,
            )
        }
    }
}

@Composable
private fun DramaLibraryContent(
    state: DramaLibraryUiState.Ready,
    onNavigateToDetail: (String) -> Unit,
) {
    val firstFeaturedId = state.featuredSeries.firstOrNull()?.id
    val firstContentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(firstFeaturedId) {
        if (firstFeaturedId != null) {
            firstContentFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (state.featuredSeries.isNotEmpty()) {
            DramaSectionHeader(
                title = libraryFeaturedSectionTitle(),
                trailing = libraryCollectedCountLabel(state.totalSeriesCount),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 30.dp),
            ) {
                items(state.featuredSeries, key = { it.id }) { series ->
                    FeatureAnimeCard(
                        anime = series.toAnimeProxy(),
                        modifier = if (series.id == firstFeaturedId) {
                            Modifier.focusRequester(firstContentFocusRequester)
                        } else {
                            Modifier
                        },
                        onClick = { onNavigateToDetail(series.id) },
                    )
                }
            }
        }

        if (state.continueWatching.isNotEmpty()) {
            DramaSectionHeader(title = libraryContinueWatchingSectionTitle())
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(end = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 30.dp),
            ) {
                items(state.continueWatching, key = { it.episode.id }) { item ->
                    AnimePosterCard(
                        anime = item.series.toAnimeProxy(),
                        subtitle = libraryContinueWatchingSubtitle(item.episode.episodeNumber),
                        onClick = { onNavigateToDetail(item.series.id) },
                    )
                }
            }
        }

        if (state.recentlyAdded.isNotEmpty()) {
            DramaSectionHeader(title = libraryRecentlyAddedSectionTitle())
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(end = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 30.dp),
            ) {
                items(state.recentlyAdded, key = { it.id }) { series ->
                    AnimePosterCard(
                        anime = series.toAnimeProxy(),
                        subtitle = "${series.seasonCount} 季 · ${series.episodeCount} 集",
                        onClick = { onNavigateToDetail(series.id) },
                    )
                }
            }
        }

        DramaSectionHeader(
            title = libraryPosterWallSectionTitle(),
            trailing = libraryCollectedCountLabel(state.totalSeriesCount),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
        ) {
            state.browseSections.forEach { section ->
                if (!section.title.isNullOrBlank()) {
                    Text(
                        text = section.title,
                        style = TvTypography.body,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                ) {
                    section.series.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { item ->
                                AnimePosterCard(
                                    anime = item.toAnimeProxy(),
                                    subtitle = "${item.seasonCount} 季 · ${item.episodeCount} 集",
                                    width = 170.dp,
                                    height = 254.dp,
                                    onClick = { onNavigateToDetail(item.id) },
                                )
                            }
                            repeat((6 - row.size).coerceAtLeast(0)) {
                                Box(modifier = Modifier.width(170.dp))
                            }
                        }
                    }
                }
            }
            if (state.browseSections.isEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = 24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(state.series, key = { it.id }) { item ->
                        AnimePosterCard(
                            anime = item.toAnimeProxy(),
                            subtitle = "${item.seasonCount} 季 · ${item.episodeCount} 集",
                            onClick = { onNavigateToDetail(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DramaSectionHeader(
    title: String,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TvTypography.subtitle,
            color = TextPrimary,
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = TvTypography.body,
                color = TextSecondary,
            )
        }
    }
}

private fun com.miruplay.tv.model.DramaSeries.toAnimeProxy() =
    Anime(
        id = id,
        title = displayTitle(),
        summary = summary,
        episodeCount = episodeCount,
        airDate = firstAirDate,
        tmdbId = tmdbId,
        posterUrl = posterUrl,
        fanartUrl = fanartUrl,
    )

