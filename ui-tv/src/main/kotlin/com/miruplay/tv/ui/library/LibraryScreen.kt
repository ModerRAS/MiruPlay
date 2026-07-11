package com.miruplay.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.PosterWallArrangement
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.libraryAddSourceActionLabel
import com.miruplay.tv.model.libraryCancelScanActionLabel
import com.miruplay.tv.model.libraryContinueWatchingSubtitle
import com.miruplay.tv.model.libraryCollectedCountLabel
import com.miruplay.tv.model.libraryContinueWatchingSectionTitle
import com.miruplay.tv.model.libraryFeaturedSectionTitle
import com.miruplay.tv.model.libraryFilesScannedLabel
import com.miruplay.tv.model.libraryHasSourcesEmptyMessage
import com.miruplay.tv.model.libraryManualScanActionLabel
import com.miruplay.tv.model.libraryNoSourcesMessage
import com.miruplay.tv.model.libraryPosterWallSectionTitle
import com.miruplay.tv.model.libraryRecentlyAddedSectionTitle
import com.miruplay.tv.model.libraryScanActionLabel
import com.miruplay.tv.model.libraryScanNowActionLabel
import com.miruplay.tv.model.libraryScanningTitle
import com.miruplay.tv.model.librarySettingsActionLabel
import com.miruplay.tv.model.librarySubtitleLabel
import com.miruplay.tv.model.libraryTitleLabel
import com.miruplay.tv.model.progressFraction
import com.miruplay.tv.model.posterWallSections
import com.miruplay.tv.scanner.LibraryScanState
import com.miruplay.tv.ui.components.*
import com.miruplay.tv.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val activeScan = scanState as? LibraryScanState.Scanning
    val lifecycleOwner = LocalLifecycleOwner.current
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var restoreSearchFocus by remember { mutableStateOf(false) }
    val headerFocusRequester = remember { FocusRequester() }
    val contentState = uiState as? LibraryUiState.HasContent
    val libraryScrollState = rememberScrollState()
    val initialContentFocus = rememberInitialFocusHandle(
        key = contentState?.allAnime?.firstOrNull()?.id,
        enabled = contentState != null,
        initialDelayMillis = 500,
    )

    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
        restoreSearchFocus = true
    }

    LaunchedEffect(searchActive, restoreSearchFocus) {
        if (!searchActive && restoreSearchFocus) {
            delay(50)
            headerFocusRequester.requestFocus()
            restoreSearchFocus = false
        }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (contentState != null) Modifier.verticalScroll(libraryScrollState) else Modifier,
                )
        ) {
            LibraryHeader(
                activeScan = activeScan,
                searchQuery = searchQuery,
                searchActive = searchActive,
                headerFocusRequester = headerFocusRequester,
                contentFocusRequester = initialContentFocus.requester(),
                onSearchQueryChange = { searchQuery = it },
                onSearchActiveChange = { active ->
                    searchActive = active
                    if (!active) {
                        searchQuery = ""
                        restoreSearchFocus = true
                    }
                },
                onScan = { viewModel.scanNow() },
                onCancelScan = { viewModel.cancelScan() },
                onNavigateToSettings = onNavigateToSettings
            )
            
            when (val state = uiState) {
                is LibraryUiState.Loading -> {
                    LoadingIndicator()
                }
                
                is LibraryUiState.NoSources -> {
                    EmptyState(
                        message = libraryNoSourcesMessage(),
                        buttonText = libraryAddSourceActionLabel(),
                        onClick = onNavigateToSettings
                    )
                }
                
                is LibraryUiState.HasSources -> {
                    EmptyState(
                        message = libraryHasSourcesEmptyMessage(),
                        buttonText = libraryScanNowActionLabel(),
                        onClick = { viewModel.scanNow() }
                    )
                }
                is LibraryUiState.ScanError -> {
                    EmptyState(
                        message = state.message,
                        buttonText = libraryManualScanActionLabel(),
                        onClick = { viewModel.scanNow() }
                    )
                }
                
                is LibraryUiState.HasContent -> {
                    LibraryContent(
                        continueWatching = state.continueWatching,
                        recentlyAdded = state.recentlyAdded,
                        allAnime = state.allAnime,
                        posterWallArrangement = state.posterWallArrangement,
                        searchQuery = searchQuery,
                        initialContentFocus = initialContentFocus,
                        onPrimaryDirectionUp = { headerFocusRequester.requestFocus() },
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    activeScan: LibraryScanState.Scanning?,
    searchQuery: String,
    searchActive: Boolean,
    headerFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.widthIn(min = 188.dp)
        ) {
            Text(
                text = libraryTitleLabel(),
                style = TvTypography.title,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = librarySubtitleLabel(),
                style = TvTypography.body,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (searchActive) {
            TvTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = "搜索片库",
                modifier = Modifier.width(260.dp),
                requestInitialFocus = true,
            )
            TvButton(
                text = "关闭搜索",
                icon = Icons.Filled.Close,
                onClick = { onSearchActiveChange(false) },
                modifier = Modifier.width(150.dp),
                secondary = true,
            )
        } else {
            TvButton(
                text = "搜索",
                icon = Icons.Filled.Search,
                onClick = { onSearchActiveChange(true) },
                modifier = Modifier
                    .width(132.dp)
                    .focusRequester(headerFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            contentFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    },
                secondary = true,
            )
        }

        if (activeScan != null) {
            ScanProgressBanner(
                state = activeScan,
                onCancel = onCancelScan,
                modifier = Modifier.weight(1f)
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
                    modifier = Modifier.width(132.dp)
                )
            }
            // Keep Settings out of any "initial focus" logic. On TV, reclaiming
            // focus into the top-right actions after the user starts navigating
            // feels like focus theft and breaks D-pad flow.
            TvButton(
                text = librarySettingsActionLabel(),
                onClick = onNavigateToSettings,
                modifier = Modifier.width(132.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    buttonText: String,
    onClick: () -> Unit
) {
    val initialFocus = rememberInitialFocusHandle(key = buttonText)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = TextSecondary,
            style = TvTypography.subtitle,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        TvButton(
            text = buttonText,
            onClick = onClick,
            modifier = initialFocus.modifier(),
        )
    }
}

@Composable
private fun ScanProgressBanner(
    state: LibraryScanState.Scanning,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit
) {
    val cancelFocus = rememberInitialFocusHandle(
        key = Unit,
        enabled = state.canCancel,
        initialDelayMillis = 50,
    )

    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, AnimeRed.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoadingIndicator(
            modifier = Modifier.size(34.dp),
            color = AnimeRed
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = libraryScanningTitle(),
                color = TextPrimary,
                style = TvTypography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = scanProgressDetail(state),
                color = TextSecondary,
                style = TvTypography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (state.canCancel) {
            Spacer(Modifier.width(12.dp))
            TvButton(
                text = libraryCancelScanActionLabel(),
                onClick = onCancel,
                modifier = Modifier
                    .width(132.dp)
                    .then(cancelFocus.modifier()),
                secondary = true
            )
        }
    }
}

private fun scanProgressDetail(state: LibraryScanState.Scanning): String {
    val files = libraryFilesScannedLabel(state.filesScanned)
    val path = state.currentPath.trim()
    return if (path.isBlank()) files else "$files · $path"
}

@Composable
private fun LibraryContent(
    continueWatching: List<ProgressWithEpisode>,
    recentlyAdded: List<Anime>,
    allAnime: List<Anime>,
    posterWallArrangement: PosterWallArrangement,
    searchQuery: String,
    initialContentFocus: InitialFocusHandle,
    onPrimaryDirectionUp: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val normalizedQuery = searchQuery.trim()
    val library = remember(allAnime, normalizedQuery) {
        allAnime
            .distinctBy { it.id }
            .filter { it.matchesLibraryQuery(normalizedQuery) }
            .sortedBy { it.displayTitle() }
    }
    val posterWallSections = remember(library, posterWallArrangement) {
        library.posterWallSections(posterWallArrangement)
    }
    val featured = remember(library) {
        library.sortedWith(
            compareByDescending<Anime> { it.rating }
                .thenByDescending { it.episodeCount }
                .thenBy { it.displayTitle() }
        ).take(8)
    }
    val initialContentModifier = initialContentFocus.modifier()

    Column(modifier = Modifier.fillMaxWidth()) {
        if (normalizedQuery.isNotEmpty()) {
            SectionHeader(title = "搜索结果", trailing = libraryCollectedCountLabel(library.size))
            if (library.isEmpty()) {
                Text(text = "没有匹配的内容", style = TvTypography.body, color = TextSecondary)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                ) {
                    library.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { anime ->
                                AnimePosterCard(
                                    anime = anime,
                                    width = 170.dp,
                                    height = 254.dp,
                                    onClick = { onNavigateToDetail(anime.id) },
                                    modifier = if (anime == library.first()) {
                                        initialContentModifier
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (normalizedQuery.isEmpty() && featured.isNotEmpty()) {
            SectionHeader(title = libraryFeaturedSectionTitle(), trailing = libraryCollectedCountLabel(library.size))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 24.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp)
            ) {
                items(featured, key = { it.id }) { anime ->
                    FeatureAnimeCard(
                        anime = anime,
                        onClick = { onNavigateToDetail(anime.id) },
                        onDirectionUp = if (anime == featured.first()) onPrimaryDirectionUp else null,
                        modifier = if (anime == featured.first()) {
                            initialContentModifier
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }

        if (normalizedQuery.isEmpty() && continueWatching.isNotEmpty()) {
            SectionHeader(title = libraryContinueWatchingSectionTitle())
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(end = 24.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp)
            ) {
                items(continueWatching.filter { it.anime != null }, key = { it.episode?.id ?: it.anime?.id.orEmpty() }) { item ->
                    val anime = item.anime ?: return@items
                    val animeId = anime.id
                    val episode = item.episode
                    val episodeNumber = episode?.episodeNumber
                    AnimePosterCard(
                        anime = anime,
                        subtitle = libraryContinueWatchingSubtitle(episodeNumber),
                        progress = episode?.progressFraction(item.progress) ?: 0f,
                        onClick = { onNavigateToDetail(animeId) }
                    )
                }
            }
        }

        if (normalizedQuery.isEmpty() && recentlyAdded.isNotEmpty()) {
            SectionHeader(title = libraryRecentlyAddedSectionTitle())
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(end = 24.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp)
            ) {
                items(recentlyAdded.filter { it.id.isNotBlank() }, key = { it.id }) { anime ->
                    AnimePosterCard(
                        anime = anime,
                        onClick = { onNavigateToDetail(anime.id) }
                    )
                }
            }
        }

        if (normalizedQuery.isEmpty() && posterWallSections.isNotEmpty()) {
            SectionHeader(title = libraryPosterWallSectionTitle())
            posterWallSections.forEach { section ->
                val sectionTitle = section.title
                if (!sectionTitle.isNullOrBlank()) {
                    Text(
                        text = sectionTitle,
                        style = TvTypography.body,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)
                ) {
                    section.anime.filter { it.id.isNotBlank() }.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { anime ->
                                AnimePosterCard(
                                    anime = anime,
                                    width = 170.dp,
                                    height = 254.dp,
                                    onClick = { onNavigateToDetail(anime.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun Anime.matchesLibraryQuery(query: String): Boolean {
    val normalized = query.trim()
    return normalized.isEmpty() || listOfNotNull(title, titleCn, id).any { it.contains(normalized, ignoreCase = true) }
}

@Composable
private fun SectionHeader(
    title: String,
    trailing: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = TvTypography.subtitle,
            color = TextPrimary
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = TvTypography.body,
                color = TextSecondary
            )
        }
    }
}
