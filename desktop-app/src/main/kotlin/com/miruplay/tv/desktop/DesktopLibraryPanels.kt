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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayFocusAxis
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.design.focusIndexAfter
import com.miruplay.tv.design.focusTargetAfter
import com.miruplay.tv.design.gridFocusIndexAfter
import com.miruplay.tv.design.horizontalNavigationDelta
import com.miruplay.tv.design.verticalNavigationDelta
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.REMOTE_BROWSER_PAGE_SIZE
import com.miruplay.tv.model.formatFileSize
import com.miruplay.tv.model.libraryCollectedCountLabel
import com.miruplay.tv.model.libraryFeaturedSectionTitle
import com.miruplay.tv.model.libraryHasSourcesEmptyMessage
import com.miruplay.tv.model.libraryNoSourcesMessage
import com.miruplay.tv.model.libraryPosterWallSectionTitle
import com.miruplay.tv.model.libraryRecentlyAddedSectionTitle
import com.miruplay.tv.model.libraryScanActionLabel
import com.miruplay.tv.model.librarySearchActionLabel
import com.miruplay.tv.model.librarySearchFieldLabel
import com.miruplay.tv.model.librarySearchResultCountLabel
import com.miruplay.tv.model.librarySettingsActionLabel
import com.miruplay.tv.model.librarySourceLabels
import com.miruplay.tv.model.librarySubtitleLabel
import com.miruplay.tv.model.libraryTitleLabel
import com.miruplay.tv.model.mediaSourceListTitleLabel
import com.miruplay.tv.model.mediaSourceRemoteBrowserItemTypeLabel
import com.miruplay.tv.model.mediaSourceStatusText
import com.miruplay.tv.model.remoteBrowserCoercedPageStart
import com.miruplay.tv.model.remoteBrowserPathPreview
import com.miruplay.tv.model.remoteBrowserPageStartForIndex
import com.miruplay.tv.model.remoteBrowserPageSummary
import com.miruplay.tv.model.remoteSourcePreview
import com.miruplay.tv.model.sourceEndpointPlaceholderLabel
import com.miruplay.tv.model.tvBadgeLabel
import com.miruplay.tv.model.tvLabel
import com.miruplay.tv.model.tvLocationLabel
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexPosterGroup
import com.miruplay.tv.repository.displayName
import com.miruplay.tv.repository.toMediaIndexPosterGroups

private const val POSTER_WALL_COLUMNS = 6
private const val REMOTE_SOURCE_BADGE_WIDTH_DP = 74
private const val REMOTE_SOURCE_BADGE_HEIGHT_DP = 32

@Composable
internal fun LibraryPanel(
    libraryRoot: String,
    onLibraryRootChange: (String) -> Unit,
    savedSources: List<MediaSourceInfo>,
    activeSourceId: Long?,
    onSavedSourceSelected: (MediaSourceInfo) -> Unit,
    indexQuery: String,
    onIndexQueryChange: (String) -> Unit,
    entries: List<MediaIndexEntry>,
    selectedEntry: MediaIndexEntry?,
    status: String,
    onOpenLocal: () -> Unit,
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onClearIndex: () -> Unit,
    onRemoveSource: () -> Unit,
    mergeSameAnimeEnabled: Boolean,
    onEntryFocused: (MediaIndexEntry) -> Unit,
    onEntrySelected: (MediaIndexEntry) -> Unit,
    focusVersion: Int = 0,
    onFocusPreviousPanel: () -> Boolean = { false },
) {
    val posterGroups = remember(entries, mergeSameAnimeEnabled) {
        entries.toMediaIndexPosterGroups(mergeSameAnimeEnabled)
    }
    val emptyMediaFocusRequester = remember { FocusRequester() }
    var emptySourceFocusVersion by remember { mutableIntStateOf(0) }

    fun requestEmptyMediaFocus(): Boolean {
        emptyMediaFocusRequester.requestFocus()
        return true
    }

    fun requestSourceFocusFromEmpty(target: LibrarySourceFocusTarget?): Boolean =
        when (target) {
            is LibrarySourceFocusTarget.Field -> {
                emptySourceFocusVersion += 1
                true
            }
            is LibrarySourceFocusTarget.Action -> false
            LibrarySourceFocusTarget.EmptyMedia -> false
            LibrarySourceFocusTarget.PreviousPanel -> onFocusPreviousPanel()
            null -> false
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (posterGroups.isEmpty()) {
            LibraryControlBar(
                libraryRoot = libraryRoot,
                onLibraryRootChange = onLibraryRootChange,
                savedSources = savedSources,
                activeSourceId = activeSourceId,
                onSavedSourceSelected = onSavedSourceSelected,
                indexQuery = indexQuery,
                onIndexQueryChange = onIndexQueryChange,
                status = status,
                onOpenLocal = onOpenLocal,
                onScan = onScan,
                onSearch = onSearch,
                onClearIndex = onClearIndex,
                onRemoveSource = onRemoveSource,
                focusVersion = focusVersion + emptySourceFocusVersion,
                onFocusPreviousPanel = onFocusPreviousPanel,
                hasEmptyMedia = true,
                onFocusEmptyMedia = ::requestEmptyMediaFocus,
            )
            LibraryEmptyMediaState(
                text = if (savedSources.isEmpty()) libraryNoSourcesMessage() else libraryHasSourcesEmptyMessage(),
                focusRequester = emptyMediaFocusRequester,
                onMove = ::requestSourceFocusFromEmpty,
                heightDp = 300,
            )
        } else {
            val featuredGroups = remember(posterGroups) { posterGroups.toFeaturedPosterGroups() }
            val recentlyAddedGroups = remember(posterGroups) { posterGroups.toRecentlyAddedPosterGroups() }
            var posterWallFocusVersion by remember { mutableIntStateOf(0) }
            var posterWallFocusIndex by remember { mutableIntStateOf(0) }
            var featuredFocusVersion by remember { mutableIntStateOf(0) }
            var featuredFocusIndex by remember { mutableIntStateOf(0) }
            var recentlyAddedFocusVersion by remember { mutableIntStateOf(0) }
            var recentlyAddedFocusIndex by remember { mutableIntStateOf(0) }
            var posterSearchFocusVersion by remember { mutableIntStateOf(0) }
            var posterSearchFocusTarget by remember { mutableIntStateOf(LibrarySearchFocusTarget.Field.ordinal) }
            var librarySourceFocusVersion by remember { mutableIntStateOf(0) }

            fun requestPosterSearchFocus(target: LibrarySearchFocusTarget): Boolean {
                posterSearchFocusTarget = target.ordinal
                posterSearchFocusVersion += 1
                return true
            }
            fun requestLibraryMediaFocus(target: LibraryMediaFocusTarget): Boolean {
                return when (target) {
                    is LibraryMediaFocusTarget.PosterWall -> {
                        val group = posterGroups.getOrNull(target.index) ?: return false
                        posterWallFocusIndex = target.index
                        posterWallFocusVersion += 1
                        onEntryFocused(group.primaryEntry)
                        true
                    }
                    is LibraryMediaFocusTarget.Featured -> {
                        if (target.index !in featuredGroups.indices) return false
                        featuredFocusIndex = target.index
                        featuredFocusVersion += 1
                        true
                    }
                    is LibraryMediaFocusTarget.RecentlyAdded -> {
                        if (target.index !in recentlyAddedGroups.indices) return false
                        recentlyAddedFocusIndex = target.index
                        recentlyAddedFocusVersion += 1
                        true
                    }
                    LibraryMediaFocusTarget.SearchBar -> requestPosterSearchFocus(LibrarySearchFocusTarget.Field)
                    LibraryMediaFocusTarget.PreviousPanel -> onFocusPreviousPanel()
                }
            }
            fun requestLastMediaFocus(): Boolean =
                when {
                    recentlyAddedGroups.isNotEmpty() -> requestLibraryMediaFocus(
                        LibraryMediaFocusTarget.RecentlyAdded(recentlyAddedFocusIndex.coerceIn(recentlyAddedGroups.indices)),
                    )
                    featuredGroups.isNotEmpty() -> requestLibraryMediaFocus(
                        LibraryMediaFocusTarget.Featured(featuredFocusIndex.coerceIn(featuredGroups.indices)),
                    )
                    else -> requestLibraryMediaFocus(
                        LibraryMediaFocusTarget.PosterWall(posterWallFocusIndex.coerceIn(posterGroups.indices)),
                    )
                }
            LaunchedEffect(focusVersion) {
                if (focusVersion > 0) {
                    requestLibraryMediaFocus(
                        LibraryMediaFocusTarget.PosterWall(posterWallFocusIndex.coerceIn(posterGroups.indices)),
                    )
                }
            }

            PosterSectionHeader(title = libraryPosterWallSectionTitle(), trailing = libraryCollectedCountLabel(posterGroups.size))
            PosterWall(
                groups = posterGroups,
                featuredCount = featuredGroups.size,
                recentlyAddedCount = recentlyAddedGroups.size,
                selectedEntry = selectedEntry,
                focusVersion = posterWallFocusVersion,
                focusIndex = posterWallFocusIndex,
                onEntryFocused = onEntryFocused,
                onEntrySelected = onEntrySelected,
                onMediaFocusTarget = ::requestLibraryMediaFocus,
            )

            if (featuredGroups.isNotEmpty()) {
                PosterSectionHeader(title = libraryFeaturedSectionTitle())
                FeaturedPosterShelf(
                    groups = featuredGroups,
                    posterCount = posterGroups.size,
                    recentlyAddedCount = recentlyAddedGroups.size,
                    selectedEntry = selectedEntry,
                    focusVersion = featuredFocusVersion,
                    focusIndex = featuredFocusIndex,
                    onEntrySelected = onEntrySelected,
                    onMediaFocusTarget = ::requestLibraryMediaFocus,
                )
            }

            if (recentlyAddedGroups.isNotEmpty()) {
                PosterSectionHeader(title = libraryRecentlyAddedSectionTitle())
                PosterCardShelf(
                    groups = recentlyAddedGroups,
                    posterCount = posterGroups.size,
                    featuredCount = featuredGroups.size,
                    selectedEntry = selectedEntry,
                    focusVersion = recentlyAddedFocusVersion,
                    focusIndex = recentlyAddedFocusIndex,
                    onEntrySelected = onEntrySelected,
                    onMediaFocusTarget = ::requestLibraryMediaFocus,
                )
            }

            PosterSearchBar(
                indexQuery = indexQuery,
                onIndexQueryChange = onIndexQueryChange,
                onSearch = onSearch,
                resultCount = posterGroups.size,
                focusVersion = posterSearchFocusVersion,
                focusTarget = LibrarySearchFocusTarget.entries[
                    posterSearchFocusTarget.coerceIn(LibrarySearchFocusTarget.entries.indices),
                ],
                onFocusPreviousPanel = ::requestLastMediaFocus,
                onFocusNextPanel = {
                    librarySourceFocusVersion += 1
                    true
                },
            )
            LibraryControlBar(
                libraryRoot = libraryRoot,
                onLibraryRootChange = onLibraryRootChange,
                savedSources = savedSources,
                activeSourceId = activeSourceId,
                onSavedSourceSelected = onSavedSourceSelected,
                indexQuery = indexQuery,
                onIndexQueryChange = onIndexQueryChange,
                status = status,
                onOpenLocal = onOpenLocal,
                onScan = onScan,
                onSearch = onSearch,
                onClearIndex = onClearIndex,
                onRemoveSource = onRemoveSource,
                focusVersion = librarySourceFocusVersion,
                onFocusPreviousPanel = {
                    requestPosterSearchFocus(LibrarySearchFocusTarget.Field)
                },
            )
        }
    }
}

@Composable
internal fun DesktopLibraryHeader(
    onScan: () -> Unit,
    onSettings: () -> Unit,
    focusVersion: Int = 0,
    focusAction: DesktopLibraryHeaderAction = DesktopLibraryHeaderAction.Scan,
    onFocusNextPanel: () -> Boolean = { false },
) {
    val focusRequesters = remember {
        DesktopLibraryHeaderAction.entries.associateWith { FocusRequester() }
    }
    var activeActionIndex by remember { mutableIntStateOf(focusAction.ordinal) }
    fun requestHeaderFocus(target: DesktopLibraryHeaderFocusTarget?): Boolean =
        when (target) {
            is DesktopLibraryHeaderFocusTarget.Action -> {
                activeActionIndex = target.action.ordinal
                focusRequesters.getValue(target.action).requestFocus()
                true
            }
            DesktopLibraryHeaderFocusTarget.NextPanel -> onFocusNextPanel()
            null -> false
        }
    fun moveHeaderFocus(action: DesktopLibraryHeaderAction, key: Key): Boolean {
        activeActionIndex = action.ordinal
        return requestHeaderFocus(desktopLibraryHeaderFocusTarget(action, key))
    }
    LaunchedEffect(focusVersion) {
        if (focusVersion > 0) {
            val activeAction = DesktopLibraryHeaderAction.entries[
                activeActionIndex.coerceIn(DesktopLibraryHeaderAction.entries.indices),
            ]
            focusRequesters.getValue(activeAction).requestFocus()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(libraryTitleLabel(), color = TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(librarySubtitleLabel(), color = TextSecondary, fontSize = 24.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            desktopLibraryHeaderActions().forEach { action ->
                TvActionButton(
                    action.label,
                    onClick = when (action) {
                        DesktopLibraryHeaderAction.Scan -> onScan
                        DesktopLibraryHeaderAction.Settings -> onSettings
                    },
                    modifier = Modifier
                        .width(132.dp)
                        .focusRequester(focusRequesters.getValue(action))
                        .desktopNavigationKeyHandler { key -> moveHeaderFocus(action, key) },
                )
            }
        }
    }
}

internal enum class DesktopLibraryHeaderAction {
    Scan,
    Settings;

    val label: String
        get() = when (this) {
            Scan -> libraryScanActionLabel()
            Settings -> librarySettingsActionLabel()
        }
}

internal fun desktopLibraryHeaderActions(): List<DesktopLibraryHeaderAction> =
    DesktopLibraryHeaderAction.entries

internal sealed interface DesktopLibraryHeaderFocusTarget {
    data class Action(val action: DesktopLibraryHeaderAction) : DesktopLibraryHeaderFocusTarget
    data object NextPanel : DesktopLibraryHeaderFocusTarget
}

internal fun desktopLibraryHeaderFocusTarget(
    current: DesktopLibraryHeaderAction,
    key: Key,
): DesktopLibraryHeaderFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        desktopLibraryHeaderFocusTarget(current, intent)
    }

internal fun desktopLibraryHeaderFocusTarget(
    current: DesktopLibraryHeaderAction,
    intent: MiruPlayInputIntent,
): DesktopLibraryHeaderFocusTarget? =
    when {
        intent == MiruPlayInputIntent.DirectionDown -> DesktopLibraryHeaderFocusTarget.NextPanel
        else -> when (intent.horizontalNavigationDelta()) {
            -1 -> DesktopLibraryHeaderFocusTarget.Action(DesktopLibraryHeaderAction.Scan)
                .takeIf { current == DesktopLibraryHeaderAction.Settings }
            1 -> DesktopLibraryHeaderFocusTarget.Action(DesktopLibraryHeaderAction.Settings)
                .takeIf { current == DesktopLibraryHeaderAction.Scan }
            else -> null
        }
    }

internal enum class LibrarySourceAction {
    OpenLocal,
    Scan,
    Search,
    ClearIndex,
    RemoveSource,
}

internal enum class LibrarySourceField {
    LocalRoot,
    IndexQuery,
}

internal sealed interface LibrarySourceFocusTarget {
    data class Action(val action: LibrarySourceAction) : LibrarySourceFocusTarget
    data class Field(val field: LibrarySourceField) : LibrarySourceFocusTarget
    data object EmptyMedia : LibrarySourceFocusTarget
    data object PreviousPanel : LibrarySourceFocusTarget
}

private fun Modifier.librarySourceActionNavigation(
    action: LibrarySourceAction,
    focusRequester: FocusRequester,
    onMove: (LibrarySourceAction, MiruPlayInputIntent) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .desktopNavigationIntentHandler { intent -> onMove(action, intent) }

private fun Modifier.librarySourceFieldNavigation(
    field: LibrarySourceField,
    focusRequester: FocusRequester,
    onMove: (LibrarySourceField, MiruPlayInputIntent) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .desktopNavigationIntentHandler { intent -> onMove(field, intent) }

internal fun librarySourceActionFocusTarget(
    current: LibrarySourceAction,
    key: Key,
    hasEmptyMedia: Boolean = false,
): LibrarySourceFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        librarySourceActionFocusTarget(current, intent, hasEmptyMedia)
    }

internal fun librarySourceActionFocusTarget(
    current: LibrarySourceAction,
    intent: MiruPlayInputIntent,
    hasEmptyMedia: Boolean = false,
): LibrarySourceFocusTarget? =
    when {
        current == LibrarySourceAction.OpenLocal && intent.horizontalNavigationDelta() == -1 ->
            LibrarySourceFocusTarget.Field(LibrarySourceField.IndexQuery)
        intent.verticalNavigationDelta() == 1 -> librarySourceActionNavigationTarget(current, intent)
            ?.let(LibrarySourceFocusTarget::Action)
            ?: LibrarySourceFocusTarget.EmptyMedia.takeIf { hasEmptyMedia }
        else -> librarySourceActionNavigationTarget(current, intent)?.let(LibrarySourceFocusTarget::Action)
    }

internal fun librarySourceFieldFocusTarget(
    current: LibrarySourceField,
    key: Key,
    hasEmptyMedia: Boolean = false,
): LibrarySourceFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        librarySourceFieldFocusTarget(current, intent, hasEmptyMedia)
    }

internal fun librarySourceFieldFocusTarget(
    current: LibrarySourceField,
    intent: MiruPlayInputIntent,
    hasEmptyMedia: Boolean = false,
): LibrarySourceFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> when (current) {
            LibrarySourceField.LocalRoot -> LibrarySourceFocusTarget.PreviousPanel
            LibrarySourceField.IndexQuery -> LibrarySourceFocusTarget.Field(LibrarySourceField.LocalRoot)
        }
        1 -> when {
            current == LibrarySourceField.LocalRoot -> LibrarySourceFocusTarget.Field(LibrarySourceField.IndexQuery)
            hasEmptyMedia -> LibrarySourceFocusTarget.EmptyMedia
            else -> null
        }
        else -> when (intent.horizontalNavigationDelta()) {
            1 -> if (current == LibrarySourceField.IndexQuery) {
                LibrarySourceFocusTarget.Action(LibrarySourceAction.OpenLocal)
            } else {
                null
            }
            else -> null
        }
    }

internal fun libraryEmptyMediaFocusTarget(key: Key): LibrarySourceFocusTarget? =
    key.toMiruPlayInputIntent()?.let(::libraryEmptyMediaFocusTarget)

internal fun libraryEmptyMediaFocusTarget(intent: MiruPlayInputIntent): LibrarySourceFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> LibrarySourceFocusTarget.Field(LibrarySourceField.LocalRoot)
        else -> null
    }

internal fun librarySourceActionNavigationTarget(
    current: LibrarySourceAction,
    key: Key,
): LibrarySourceAction? =
    key.toMiruPlayInputIntent()?.let { intent ->
        librarySourceActionNavigationTarget(current, intent)
    }

internal fun librarySourceActionNavigationTarget(
    current: LibrarySourceAction,
    intent: MiruPlayInputIntent,
): LibrarySourceAction? =
    when (intent.verticalNavigationDelta()) {
        -1 -> if (current == LibrarySourceAction.RemoveSource) LibrarySourceAction.ClearIndex else null
        1 -> if (current == LibrarySourceAction.ClearIndex) LibrarySourceAction.RemoveSource else null
        else -> intent.horizontalNavigationDelta()?.let { delta ->
            librarySourceHorizontalAction(current, delta)
        }
    }

private fun librarySourceHorizontalAction(
    current: LibrarySourceAction,
    delta: Int,
): LibrarySourceAction? =
    when (current) {
        LibrarySourceAction.OpenLocal,
        LibrarySourceAction.Scan,
        LibrarySourceAction.Search,
        -> listOf(LibrarySourceAction.OpenLocal, LibrarySourceAction.Scan, LibrarySourceAction.Search, LibrarySourceAction.ClearIndex)
            .focusTargetAfter(current = current, delta = delta)
        LibrarySourceAction.ClearIndex -> listOf(LibrarySourceAction.Search, LibrarySourceAction.ClearIndex)
            .focusTargetAfter(current = current, delta = delta)
        LibrarySourceAction.RemoveSource -> null
    }

internal enum class RemoteSourceAction {
    OpenWebDav,
    OpenSmb,
    ScanSource,
}

internal enum class RemoteSourceField {
    WebDavUrl,
    WebDavUsername,
    WebDavPassword,
    SmbUrl,
    SmbDomain,
    SmbUsername,
    SmbPassword,
}

internal sealed interface RemoteSourceFocusTarget {
    data class Action(val action: RemoteSourceAction) : RemoteSourceFocusTarget
    data class Field(val field: RemoteSourceField) : RemoteSourceFocusTarget
    data object NextPanel : RemoteSourceFocusTarget
}

private fun Modifier.remoteSourceActionNavigation(
    action: RemoteSourceAction,
    focusRequester: FocusRequester,
    onMove: (RemoteSourceAction, MiruPlayInputIntent) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .desktopNavigationIntentHandler { intent -> onMove(action, intent) }

private fun Modifier.remoteSourceFieldNavigation(
    field: RemoteSourceField,
    focusRequester: FocusRequester,
    onMove: (RemoteSourceField, MiruPlayInputIntent) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .desktopNavigationIntentHandler { intent -> onMove(field, intent) }

internal fun remoteSourceActionFocusTarget(
    current: RemoteSourceAction,
    key: Key,
): RemoteSourceFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        remoteSourceActionFocusTarget(current, intent)
    }

internal fun remoteSourceActionFocusTarget(
    current: RemoteSourceAction,
    intent: MiruPlayInputIntent,
): RemoteSourceFocusTarget? =
    when (intent.horizontalNavigationDelta()) {
        1 -> when (current) {
            RemoteSourceAction.OpenWebDav,
            RemoteSourceAction.ScanSource,
            -> RemoteSourceFocusTarget.NextPanel
            RemoteSourceAction.OpenSmb -> remoteSourceActionNavigationTarget(current, intent)
                ?.let(RemoteSourceFocusTarget::Action)
        }
        else -> when (intent.verticalNavigationDelta()) {
            -1 -> when (current) {
                RemoteSourceAction.OpenWebDav -> RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavPassword)
                RemoteSourceAction.OpenSmb -> RemoteSourceFocusTarget.Field(RemoteSourceField.SmbDomain)
                RemoteSourceAction.ScanSource -> RemoteSourceFocusTarget.Field(RemoteSourceField.SmbPassword)
            }
            else -> remoteSourceActionNavigationTarget(current, intent)?.let(RemoteSourceFocusTarget::Action)
        }
    }

internal fun remoteSourceFieldFocusTarget(
    current: RemoteSourceField,
    key: Key,
): RemoteSourceFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        remoteSourceFieldFocusTarget(current, intent)
    }

internal fun remoteSourceFieldFocusTarget(
    current: RemoteSourceField,
    intent: MiruPlayInputIntent,
): RemoteSourceFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> when (current) {
            RemoteSourceField.WebDavUsername,
            RemoteSourceField.WebDavPassword,
            -> RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavUrl)
            RemoteSourceField.SmbDomain,
            RemoteSourceField.SmbUsername,
            RemoteSourceField.SmbPassword,
            -> RemoteSourceFocusTarget.Field(RemoteSourceField.SmbUrl)
            else -> null
        }
        1 -> when (current) {
            RemoteSourceField.WebDavUrl -> RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavUsername)
            RemoteSourceField.WebDavUsername,
            RemoteSourceField.WebDavPassword,
            -> RemoteSourceFocusTarget.Action(RemoteSourceAction.OpenWebDav)
            RemoteSourceField.SmbUrl -> RemoteSourceFocusTarget.Field(RemoteSourceField.SmbDomain)
            RemoteSourceField.SmbDomain -> RemoteSourceFocusTarget.Action(RemoteSourceAction.OpenSmb)
            RemoteSourceField.SmbUsername,
            RemoteSourceField.SmbPassword,
            -> RemoteSourceFocusTarget.Action(RemoteSourceAction.ScanSource)
        }
        else -> intent.horizontalNavigationDelta()?.let { delta ->
            remoteSourceHorizontalField(current, delta)?.let(RemoteSourceFocusTarget::Field)
                ?: RemoteSourceFocusTarget.NextPanel.takeIf { delta == 1 && current.canExitToRemoteBrowser() }
        }
    }

private fun RemoteSourceField.canExitToRemoteBrowser(): Boolean =
    this == RemoteSourceField.WebDavUrl ||
        this == RemoteSourceField.WebDavPassword ||
        this == RemoteSourceField.SmbUrl ||
        this == RemoteSourceField.SmbPassword

private fun remoteSourceHorizontalField(
    current: RemoteSourceField,
    delta: Int,
): RemoteSourceField? {
    val row = when (current) {
        RemoteSourceField.WebDavUrl -> listOf(RemoteSourceField.WebDavUrl)
        RemoteSourceField.WebDavUsername,
        RemoteSourceField.WebDavPassword,
        -> listOf(RemoteSourceField.WebDavUsername, RemoteSourceField.WebDavPassword)
        RemoteSourceField.SmbUrl -> listOf(RemoteSourceField.SmbUrl)
        RemoteSourceField.SmbDomain,
        RemoteSourceField.SmbUsername,
        RemoteSourceField.SmbPassword,
        -> listOf(RemoteSourceField.SmbDomain, RemoteSourceField.SmbUsername, RemoteSourceField.SmbPassword)
    }
    return row.focusTargetAfter(current = current, delta = delta)
}

internal fun remoteSourceActionNavigationTarget(
    current: RemoteSourceAction,
    key: Key,
): RemoteSourceAction? =
    key.toMiruPlayInputIntent()?.let { intent ->
        remoteSourceActionNavigationTarget(current, intent)
    }

internal fun remoteSourceActionNavigationTarget(
    current: RemoteSourceAction,
    intent: MiruPlayInputIntent,
): RemoteSourceAction? =
    when (intent.verticalNavigationDelta()) {
        -1 -> when (current) {
            RemoteSourceAction.OpenSmb,
            RemoteSourceAction.ScanSource,
            -> RemoteSourceAction.OpenWebDav
            RemoteSourceAction.OpenWebDav -> null
        }
        1 -> if (current == RemoteSourceAction.OpenWebDav) RemoteSourceAction.OpenSmb else null
        else -> when (intent.horizontalNavigationDelta()) {
            -1 -> if (current == RemoteSourceAction.ScanSource) RemoteSourceAction.OpenSmb else null
            1 -> if (current == RemoteSourceAction.OpenSmb) RemoteSourceAction.ScanSource else null
            else -> null
        }
    }

internal sealed interface RemoteBrowserFocusTarget {
    data object UpButton : RemoteBrowserFocusTarget
    data class Row(val index: Int) : RemoteBrowserFocusTarget
    data object EmptyState : RemoteBrowserFocusTarget
    data object PreviousPanel : RemoteBrowserFocusTarget
}

@Composable
private fun PosterSearchBar(
    indexQuery: String,
    onIndexQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    resultCount: Int,
    focusVersion: Int = 0,
    focusTarget: LibrarySearchFocusTarget = LibrarySearchFocusTarget.Field,
    onFocusPreviousPanel: () -> Boolean = { false },
    onFocusNextPanel: () -> Boolean = { false },
) {
    val focusRequesters = remember {
        LibrarySearchFocusTarget.entries.associateWith { FocusRequester() }
    }
    fun moveSearchFocus(target: LibrarySearchFocusTarget, key: Key): Boolean =
        when (val next = librarySearchFocusTarget(target, key)) {
            LibrarySearchFocusTarget.Field,
            LibrarySearchFocusTarget.Action,
            -> {
                focusRequesters.getValue(next).requestFocus()
                true
            }
            LibrarySearchFocusTarget.PreviousPanel -> onFocusPreviousPanel()
            LibrarySearchFocusTarget.NextPanel -> onFocusNextPanel()
            null -> false
        }
    LaunchedEffect(focusVersion) {
        if (focusVersion > 0) {
            focusRequesters.getValue(focusTarget).requestFocus()
        }
    }
    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledTextField(
                librarySearchFieldLabel(),
                indexQuery,
                onValueChange = onIndexQueryChange,
                modifier = Modifier.weight(1f),
                inputModifier = Modifier
                    .focusRequester(focusRequesters.getValue(LibrarySearchFocusTarget.Field))
                    .desktopNavigationKeyHandler { key -> moveSearchFocus(LibrarySearchFocusTarget.Field, key) },
            )
            TvActionButton(
                librarySearchActionLabel(),
                onClick = onSearch,
                modifier = Modifier
                    .width(132.dp)
                    .focusRequester(focusRequesters.getValue(LibrarySearchFocusTarget.Action))
                    .desktopNavigationKeyHandler { key -> moveSearchFocus(LibrarySearchFocusTarget.Action, key) },
            )
            Text(
                librarySearchResultCountLabel(resultCount),
                color = TextSecondary,
                fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LibraryControlBar(
    libraryRoot: String,
    onLibraryRootChange: (String) -> Unit,
    savedSources: List<MediaSourceInfo>,
    activeSourceId: Long?,
    onSavedSourceSelected: (MediaSourceInfo) -> Unit,
    indexQuery: String,
    onIndexQueryChange: (String) -> Unit,
    status: String,
    onOpenLocal: () -> Unit,
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onClearIndex: () -> Unit,
    onRemoveSource: () -> Unit,
    focusVersion: Int = 0,
    onFocusPreviousPanel: () -> Boolean = { false },
    hasEmptyMedia: Boolean = false,
    onFocusEmptyMedia: () -> Boolean = { false },
) {
    val labels = librarySourceLabels()
    val sourcePickerFocusRequester = remember { FocusRequester() }
    val actionFocusRequesters = remember {
        LibrarySourceAction.entries.associateWith { FocusRequester() }
    }
    val fieldFocusRequesters = remember {
        LibrarySourceField.entries.associateWith { FocusRequester() }
    }
    var sourcePickerFocusVersion by remember { mutableIntStateOf(0) }
    fun refocusSourcePicker() {
        sourcePickerFocusVersion += 1
    }
    fun requestLibrarySourceFocus(target: LibrarySourceFocusTarget?): Boolean =
        when (target) {
            is LibrarySourceFocusTarget.Action -> {
                actionFocusRequesters.getValue(target.action).requestFocus()
                true
            }
            is LibrarySourceFocusTarget.Field -> {
                fieldFocusRequesters.getValue(target.field).requestFocus()
                true
            }
            LibrarySourceFocusTarget.EmptyMedia -> onFocusEmptyMedia()
            LibrarySourceFocusTarget.PreviousPanel -> onFocusPreviousPanel()
            null -> false
        }
    fun moveLibrarySourceActionFocus(action: LibrarySourceAction, intent: MiruPlayInputIntent): Boolean {
        return requestLibrarySourceFocus(librarySourceActionFocusTarget(action, intent, hasEmptyMedia))
    }
    fun moveLibrarySourceFieldFocus(field: LibrarySourceField, intent: MiruPlayInputIntent): Boolean =
        requestLibrarySourceFocus(librarySourceFieldFocusTarget(field, intent, hasEmptyMedia))
    LaunchedEffect(sourcePickerFocusVersion) {
        if (sourcePickerFocusVersion > 0) {
            sourcePickerFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(focusVersion) {
        if (focusVersion > 0) {
            fieldFocusRequesters.getValue(LibrarySourceField.LocalRoot).requestFocus()
        }
    }

    TvPanel(Modifier.fillMaxWidth()) {
        Text(
            mediaSourceListTitleLabel(),
            color = TextPrimary,
            fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    LabeledTextField(
                        labels.localLibraryRoot,
                        libraryRoot,
                        onValueChange = onLibraryRootChange,
                        modifier = Modifier.weight(1.25f),
                        inputModifier = Modifier.librarySourceFieldNavigation(
                            field = LibrarySourceField.LocalRoot,
                            focusRequester = fieldFocusRequesters.getValue(LibrarySourceField.LocalRoot),
                            onMove = ::moveLibrarySourceFieldFocus,
                        ),
                    )
                    SavedSourcePicker(
                        sources = savedSources,
                        activeSourceId = activeSourceId,
                        onSelected = onSavedSourceSelected,
                        modifier = Modifier.weight(0.82f),
                        focusRequester = sourcePickerFocusRequester,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    LabeledTextField(
                        labels.indexQuery,
                        indexQuery,
                        onValueChange = onIndexQueryChange,
                        modifier = Modifier.weight(1.4f),
                        inputModifier = Modifier.librarySourceFieldNavigation(
                            field = LibrarySourceField.IndexQuery,
                            focusRequester = fieldFocusRequesters.getValue(LibrarySourceField.IndexQuery),
                            onMove = ::moveLibrarySourceFieldFocus,
                        ),
                    )
                    TvActionButton(
                        labels.openLocal,
                        onClick = {
                            onOpenLocal()
                            refocusSourcePicker()
                        },
                        modifier = Modifier
                            .weight(0.72f)
                            .librarySourceActionNavigation(
                                action = LibrarySourceAction.OpenLocal,
                                focusRequester = actionFocusRequesters.getValue(LibrarySourceAction.OpenLocal),
                                onMove = ::moveLibrarySourceActionFocus,
                            ),
                    )
                    TvActionButton(
                        labels.scan,
                        onClick = {
                            onScan()
                            refocusSourcePicker()
                        },
                        secondary = true,
                        modifier = Modifier
                            .weight(0.54f)
                            .librarySourceActionNavigation(
                                action = LibrarySourceAction.Scan,
                                focusRequester = actionFocusRequesters.getValue(LibrarySourceAction.Scan),
                                onMove = ::moveLibrarySourceActionFocus,
                            ),
                    )
                    TvActionButton(
                        labels.search,
                        onClick = onSearch,
                        secondary = true,
                        modifier = Modifier
                            .weight(1f)
                            .librarySourceActionNavigation(
                                action = LibrarySourceAction.Search,
                                focusRequester = actionFocusRequesters.getValue(LibrarySourceAction.Search),
                                onMove = ::moveLibrarySourceActionFocus,
                            ),
                    )
                }
            }
            Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                TvActionButton(
                    labels.clearIndex,
                    onClick = onClearIndex,
                    secondary = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .librarySourceActionNavigation(
                            action = LibrarySourceAction.ClearIndex,
                            focusRequester = actionFocusRequesters.getValue(LibrarySourceAction.ClearIndex),
                            onMove = ::moveLibrarySourceActionFocus,
                        ),
                )
                TvActionButton(
                    labels.removeSource,
                    onClick = onRemoveSource,
                    secondary = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .librarySourceActionNavigation(
                            action = LibrarySourceAction.RemoveSource,
                            focusRequester = actionFocusRequesters.getValue(LibrarySourceAction.RemoveSource),
                            onMove = ::moveLibrarySourceActionFocus,
                        ),
                )
            }
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        StatusBox(mediaSourceStatusText(status))
    }
}

@Composable
private fun LibraryEmptyMediaState(
    text: String,
    focusRequester: FocusRequester,
    onMove: (LibrarySourceFocusTarget?) -> Boolean,
    heightDp: Int,
) {
    DesktopSelectableRow(
        selected = false,
        onClick = {},
        modifier = Modifier
            .focusRequester(focusRequester),
        heightDp = heightDp,
        inactiveAlpha = 0.48f,
        onNavigationIntent = { intent ->
            onMove(libraryEmptyMediaFocusTarget(intent))
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
private fun PosterSectionHeader(
    title: String,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = TextPrimary,
            fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (!trailing.isNullOrBlank()) {
            Text(trailing, color = TextSecondary, fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp)
        }
    }
}

@Composable
private fun PosterWall(
    groups: List<DesktopPosterGroup>,
    featuredCount: Int,
    recentlyAddedCount: Int,
    selectedEntry: MediaIndexEntry?,
    focusVersion: Int = 0,
    focusIndex: Int = 0,
    onEntryFocused: (MediaIndexEntry) -> Unit,
    onEntrySelected: (MediaIndexEntry) -> Unit,
    onMediaFocusTarget: (LibraryMediaFocusTarget) -> Boolean = { false },
) {
    val focusRequesters = remember(groups) {
        groups.associate { it.primaryEntry.path to FocusRequester() }
    }
    val selectedGroup = groups.firstOrNull { group ->
        selectedEntry?.path?.let { it in group.entryPaths } == true
    }
    LaunchedEffect(groups, selectedGroup?.primaryEntry?.path) {
        val focusTarget = selectedGroup ?: groups.firstOrNull()
        focusTarget?.let { group ->
            focusRequesters[group.primaryEntry.path]?.requestFocus()
        }
    }
    LaunchedEffect(focusVersion) {
        if (focusVersion > 0) {
            groups.getOrNull(focusIndex)?.let { group ->
                focusRequesters[group.primaryEntry.path]?.requestFocus()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        groups.toPosterWallRows().forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEachIndexed { columnIndex, group ->
                    val groupIndex = rowIndex * POSTER_WALL_COLUMNS + columnIndex
                    LibraryPosterCard(
                        group = group,
                        selected = selectedEntry?.path?.let { it in group.entryPaths } == true,
                        onClick = { onEntrySelected(group.primaryEntry) },
                        onNavigationKey = { key ->
                            val target = libraryMediaFocusTarget(
                                current = LibraryMediaFocusTarget.PosterWall(groupIndex),
                                key = key,
                                posterCount = groups.size,
                                featuredCount = featuredCount,
                                recentlyAddedCount = recentlyAddedCount,
                            )
                            when (target) {
                                is LibraryMediaFocusTarget.PosterWall -> {
                                    val targetGroup = groups[target.index]
                                    onEntryFocused(targetGroup.primaryEntry)
                                    focusRequesters.getValue(targetGroup.primaryEntry.path).requestFocus()
                                    true
                                }
                                is LibraryMediaFocusTarget.Featured,
                                is LibraryMediaFocusTarget.RecentlyAdded,
                                LibraryMediaFocusTarget.SearchBar,
                                LibraryMediaFocusTarget.PreviousPanel,
                                -> onMediaFocusTarget(target)
                                null -> false
                            }
                        },
                        modifier = Modifier.focusRequester(focusRequesters.getValue(group.primaryEntry.path)),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedPosterShelf(
    groups: List<DesktopPosterGroup>,
    posterCount: Int,
    recentlyAddedCount: Int,
    selectedEntry: MediaIndexEntry?,
    focusVersion: Int = 0,
    focusIndex: Int = 0,
    onEntrySelected: (MediaIndexEntry) -> Unit,
    onMediaFocusTarget: (LibraryMediaFocusTarget) -> Boolean = { false },
) {
    val focusRequesters = remember(groups) {
        groups.associate { it.primaryEntry.path to FocusRequester() }
    }
    LaunchedEffect(focusVersion) {
        if (focusVersion > 0) {
            groups.getOrNull(focusIndex)?.let { group ->
                focusRequesters[group.primaryEntry.path]?.requestFocus()
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        groups.forEachIndexed { index, group ->
            FeaturedPosterCard(
                group = group,
                selected = selectedEntry?.path?.let { it in group.entryPaths } == true,
                onClick = { onEntrySelected(group.primaryEntry) },
                onNavigationKey = { key ->
                    val target = libraryMediaFocusTarget(
                        current = LibraryMediaFocusTarget.Featured(index),
                        key = key,
                        posterCount = posterCount,
                        featuredCount = groups.size,
                        recentlyAddedCount = recentlyAddedCount,
                    )
                    when (target) {
                        is LibraryMediaFocusTarget.Featured -> {
                            val targetGroup = groups[target.index]
                            focusRequesters.getValue(targetGroup.primaryEntry.path).requestFocus()
                            true
                        }
                        is LibraryMediaFocusTarget.PosterWall,
                        is LibraryMediaFocusTarget.RecentlyAdded,
                        LibraryMediaFocusTarget.SearchBar,
                        LibraryMediaFocusTarget.PreviousPanel,
                        -> onMediaFocusTarget(target)
                        null -> false
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequesters.getValue(group.primaryEntry.path)),
            )
        }
        if (groups.size == 1) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PosterCardShelf(
    groups: List<DesktopPosterGroup>,
    posterCount: Int,
    featuredCount: Int,
    selectedEntry: MediaIndexEntry?,
    focusVersion: Int = 0,
    focusIndex: Int = 0,
    onEntrySelected: (MediaIndexEntry) -> Unit,
    onMediaFocusTarget: (LibraryMediaFocusTarget) -> Boolean = { false },
) {
    val focusRequesters = remember(groups) {
        groups.associate { it.primaryEntry.path to FocusRequester() }
    }
    LaunchedEffect(focusVersion) {
        if (focusVersion > 0) {
            groups.getOrNull(focusIndex)?.let { group ->
                focusRequesters[group.primaryEntry.path]?.requestFocus()
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        groups.forEachIndexed { index, group ->
            LibraryPosterCard(
                group = group,
                selected = selectedEntry?.path?.let { it in group.entryPaths } == true,
                onClick = { onEntrySelected(group.primaryEntry) },
                onNavigationKey = { key ->
                    val target = libraryMediaFocusTarget(
                        current = LibraryMediaFocusTarget.RecentlyAdded(index),
                        key = key,
                        posterCount = posterCount,
                        featuredCount = featuredCount,
                        recentlyAddedCount = groups.size,
                    )
                    when (target) {
                        is LibraryMediaFocusTarget.RecentlyAdded -> {
                            val targetGroup = groups[target.index]
                            focusRequesters.getValue(targetGroup.primaryEntry.path).requestFocus()
                            true
                        }
                        is LibraryMediaFocusTarget.PosterWall,
                        is LibraryMediaFocusTarget.Featured,
                        LibraryMediaFocusTarget.SearchBar,
                        LibraryMediaFocusTarget.PreviousPanel,
                        -> onMediaFocusTarget(target)
                        null -> false
                    }
                },
                modifier = Modifier.focusRequester(focusRequesters.getValue(group.primaryEntry.path)),
            )
        }
    }
}

@Composable
private fun FeaturedPosterCard(
    group: DesktopPosterGroup,
    selected: Boolean,
    onClick: () -> Unit,
    onNavigationKey: (Key) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val active = selected || focused
    Box(
        modifier = modifier
            .height(300.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(featureBrush(group.title))
            .border(
                width = if (active) 3.dp else 1.dp,
                color = if (active) AnimeRed else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
            )
            .onPreviewKeyEvent { event ->
                desktopConfirmOrNavigationKeyEvent(
                    key = event.key,
                    type = event.type,
                    onClick = onClick,
                    onNavigationKey = onNavigationKey,
                )
            }
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.82f), Color.Black.copy(alpha = 0.18f)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PosterArtwork(group.title, selected = active, widthDp = 118, heightDp = 170)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    group.title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    group.subtitle,
                    color = TextSecondary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    group.primaryEntry.displayName(),
                    color = TextPrimary.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(52.dp)
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
private fun LibraryPosterCard(
    group: DesktopPosterGroup,
    selected: Boolean,
    onClick: () -> Unit,
    onNavigationKey: (Key) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val active = selected || focused
    Box(
        modifier = modifier
            .width(MiruPlayUiMetrics.POSTER_WIDTH_DP.dp)
            .height(MiruPlayUiMetrics.POSTER_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(CardBg)
            .border(
                width = if (active) 3.dp else 1.dp,
                color = if (active) AnimeRed else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
            )
            .onPreviewKeyEvent { event ->
                desktopConfirmOrNavigationKeyEvent(
                    key = event.key,
                    type = event.type,
                    onClick = onClick,
                    onNavigationKey = onNavigationKey,
                )
            }
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        PosterArtwork(group.title, selected = active)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.48f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)))),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                group.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                group.subtitle,
                color = TextSecondary,
                fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PosterArtwork(
    title: String,
    selected: Boolean,
    widthDp: Int = MiruPlayUiMetrics.POSTER_WIDTH_DP,
    heightDp: Int = MiruPlayUiMetrics.POSTER_HEIGHT_DP,
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(posterBrush(title))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AnimeRed else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
            ),
    ) {
        Text(
            title.take(2).uppercase(),
            color = Color.White.copy(alpha = 0.20f),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

internal typealias DesktopPosterGroup = MediaIndexPosterGroup

internal fun List<DesktopPosterGroup>.toPosterWallRows(columns: Int = POSTER_WALL_COLUMNS): List<List<DesktopPosterGroup>> =
    chunked(columns.coerceAtLeast(1))

internal fun List<DesktopPosterGroup>.toFeaturedPosterGroups(limit: Int = 2): List<DesktopPosterGroup> =
    sortedWith(
        compareByDescending<DesktopPosterGroup> { it.entries.size }
            .thenByDescending { it.lastModified }
            .thenBy { it.title.lowercase() },
    ).take(limit.coerceAtLeast(0))

internal fun List<DesktopPosterGroup>.toRecentlyAddedPosterGroups(limit: Int = 4): List<DesktopPosterGroup> =
    sortedWith(
        compareByDescending<DesktopPosterGroup> { it.lastModified }
            .thenBy { it.title.lowercase() },
    ).take(limit.coerceAtLeast(0))

internal sealed interface LibraryMediaFocusTarget {
    data class PosterWall(val index: Int) : LibraryMediaFocusTarget
    data class Featured(val index: Int) : LibraryMediaFocusTarget
    data class RecentlyAdded(val index: Int) : LibraryMediaFocusTarget
    data object SearchBar : LibraryMediaFocusTarget
    data object PreviousPanel : LibraryMediaFocusTarget
}

internal enum class LibrarySearchFocusTarget {
    Field,
    Action,
    PreviousPanel,
    NextPanel,
}

internal fun librarySearchFocusTarget(
    current: LibrarySearchFocusTarget,
    key: Key,
): LibrarySearchFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        librarySearchFocusTarget(current, intent)
    }

internal fun librarySearchFocusTarget(
    current: LibrarySearchFocusTarget,
    intent: MiruPlayInputIntent,
): LibrarySearchFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> LibrarySearchFocusTarget.PreviousPanel
        1 -> LibrarySearchFocusTarget.NextPanel
        else -> when (intent.horizontalNavigationDelta()) {
            -1 -> LibrarySearchFocusTarget.Field.takeIf { current == LibrarySearchFocusTarget.Action }
            1 -> LibrarySearchFocusTarget.Action.takeIf { current == LibrarySearchFocusTarget.Field }
            else -> null
        }
    }

internal fun List<DesktopPosterGroup>.posterShelfNavigationTarget(
    currentIndex: Int,
    key: Key,
): DesktopPosterGroup? =
    key.toMiruPlayInputIntent()?.let { intent ->
        posterShelfNavigationTarget(currentIndex, intent)
    }

internal fun List<DesktopPosterGroup>.posterShelfNavigationTarget(
    currentIndex: Int,
    intent: MiruPlayInputIntent,
): DesktopPosterGroup? {
    if (currentIndex !in indices) return null
    return focusIndexAfter(
        currentIndex = currentIndex,
        intent = intent,
        axis = MiruPlayFocusAxis.Horizontal,
        itemCount = size,
    )
        ?.let(::get)
}

internal fun libraryMediaFocusTarget(
    current: LibraryMediaFocusTarget,
    key: Key,
    posterCount: Int,
    featuredCount: Int,
    recentlyAddedCount: Int,
    columns: Int = POSTER_WALL_COLUMNS,
): LibraryMediaFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        libraryMediaFocusTarget(
            current = current,
            intent = intent,
            posterCount = posterCount,
            featuredCount = featuredCount,
            recentlyAddedCount = recentlyAddedCount,
            columns = columns,
        )
    }

internal fun libraryMediaFocusTarget(
    current: LibraryMediaFocusTarget,
    intent: MiruPlayInputIntent,
    posterCount: Int,
    featuredCount: Int,
    recentlyAddedCount: Int,
    columns: Int = POSTER_WALL_COLUMNS,
): LibraryMediaFocusTarget? =
    when (current) {
        is LibraryMediaFocusTarget.PosterWall -> posterWallMediaFocusTarget(
            currentIndex = current.index,
            intent = intent,
            posterCount = posterCount,
            featuredCount = featuredCount,
            recentlyAddedCount = recentlyAddedCount,
            columns = columns,
        )
        is LibraryMediaFocusTarget.Featured -> posterShelfMediaFocusTarget(
            currentIndex = current.index,
            intent = intent,
            shelfCount = featuredCount,
            previousCount = posterCount,
            nextCount = recentlyAddedCount.takeIf { it > 0 } ?: 1,
            previousFactory = LibraryMediaFocusTarget::PosterWall,
            currentFactory = LibraryMediaFocusTarget::Featured,
            nextFactory = if (recentlyAddedCount > 0) {
                LibraryMediaFocusTarget::RecentlyAdded
            } else {
                { LibraryMediaFocusTarget.SearchBar }
            },
        )
        is LibraryMediaFocusTarget.RecentlyAdded -> posterShelfMediaFocusTarget(
            currentIndex = current.index,
            intent = intent,
            shelfCount = recentlyAddedCount,
            previousCount = featuredCount.takeIf { it > 0 } ?: posterCount,
            nextCount = 1,
            previousFactory = if (featuredCount > 0) {
                LibraryMediaFocusTarget::Featured
            } else {
                LibraryMediaFocusTarget::PosterWall
            },
            currentFactory = LibraryMediaFocusTarget::RecentlyAdded,
            nextFactory = { LibraryMediaFocusTarget.SearchBar },
        )
        LibraryMediaFocusTarget.SearchBar -> null
        LibraryMediaFocusTarget.PreviousPanel -> null
    }

private fun posterWallMediaFocusTarget(
    currentIndex: Int,
    intent: MiruPlayInputIntent,
    posterCount: Int,
    featuredCount: Int,
    recentlyAddedCount: Int,
    columns: Int,
): LibraryMediaFocusTarget? {
    if (currentIndex !in 0 until posterCount) return null
    val safeColumns = columns.coerceAtLeast(1)
    val currentColumn = currentIndex % safeColumns
    val targetIndex = gridFocusIndexAfter(
        currentIndex = currentIndex,
        intent = intent,
        columns = columns,
        itemCount = posterCount,
    ) ?: return when {
        intent.verticalNavigationDelta() == -1 -> LibraryMediaFocusTarget.PreviousPanel
        intent.verticalNavigationDelta() == 1 -> when {
            featuredCount > 0 -> LibraryMediaFocusTarget.Featured(currentColumn.coerceAtMost(featuredCount - 1))
            recentlyAddedCount > 0 -> LibraryMediaFocusTarget.RecentlyAdded(currentColumn.coerceAtMost(recentlyAddedCount - 1))
            else -> LibraryMediaFocusTarget.SearchBar
        }
        else -> null
    }
    return LibraryMediaFocusTarget.PosterWall(targetIndex)
}

private fun posterShelfMediaFocusTarget(
    currentIndex: Int,
    intent: MiruPlayInputIntent,
    shelfCount: Int,
    previousCount: Int,
    nextCount: Int,
    previousFactory: (Int) -> LibraryMediaFocusTarget,
    currentFactory: (Int) -> LibraryMediaFocusTarget,
    nextFactory: (Int) -> LibraryMediaFocusTarget?,
): LibraryMediaFocusTarget? {
    if (currentIndex !in 0 until shelfCount) return null
    return when (intent.horizontalNavigationDelta()) {
        1 -> currentFactory(currentIndex + 1).takeIf { currentIndex + 1 < shelfCount }
        -1 -> currentFactory(currentIndex - 1).takeIf { currentIndex > 0 }
        else -> when (intent.verticalNavigationDelta()) {
            -1 -> previousFactory(currentIndex.coerceAtMost(previousCount - 1)).takeIf { previousCount > 0 }
            1 -> nextFactory(currentIndex.coerceAtMost(nextCount - 1)).takeIf { nextCount > 0 }
            else -> null
        }
    }
}

internal fun List<DesktopPosterGroup>.posterNavigationTarget(
    currentIndex: Int,
    key: Key,
    columns: Int = POSTER_WALL_COLUMNS,
): DesktopPosterGroup? =
    key.toMiruPlayInputIntent()?.let { intent ->
        posterNavigationTarget(currentIndex, intent, columns)
    }

internal fun List<DesktopPosterGroup>.posterNavigationTarget(
    currentIndex: Int,
    intent: MiruPlayInputIntent,
    columns: Int = POSTER_WALL_COLUMNS,
): DesktopPosterGroup? {
    return gridFocusIndexAfter(
        currentIndex = currentIndex,
        intent = intent,
        columns = columns,
        itemCount = size,
    )?.let(::get)
}

private fun posterBrush(title: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFFB83250), Color(0xFF2E183F), CardBg),
        listOf(Color(0xFF1E6A8A), Color(0xFF12213C), CardBg),
        listOf(Color(0xFF7C4D1D), Color(0xFF221A12), CardBg),
        listOf(Color(0xFF4F6F2A), Color(0xFF142115), CardBg),
        listOf(Color(0xFF6D3D7A), Color(0xFF1B1732), CardBg),
    )
    val colors = palettes[Math.floorMod(title.hashCode(), palettes.size)]
    return Brush.verticalGradient(colors)
}

private fun featureBrush(title: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFF9B2D45), Color(0xFF182447), Color(0xFF08080C)),
        listOf(Color(0xFF1C6582), Color(0xFF271B4A), Color(0xFF08080C)),
        listOf(Color(0xFF6C4A1A), Color(0xFF172A22), Color(0xFF08080C)),
    )
    return Brush.horizontalGradient(palettes[Math.floorMod(title.hashCode(), palettes.size)])
}

@Composable
internal fun RemoteSourcesPanel(
    webDavUrl: String,
    onWebDavUrlChange: (String) -> Unit,
    webDavUsername: String,
    onWebDavUsernameChange: (String) -> Unit,
    webDavPassword: String,
    onWebDavPasswordChange: (String) -> Unit,
    smbUrl: String,
    onSmbUrlChange: (String) -> Unit,
    smbDomain: String,
    onSmbDomainChange: (String) -> Unit,
    smbUsername: String,
    onSmbUsernameChange: (String) -> Unit,
    smbPassword: String,
    onSmbPasswordChange: (String) -> Unit,
    remotePath: String,
    entries: List<FileEntry>,
    selectedEntry: FileEntry?,
    status: String,
    onOpenWebDav: () -> Unit,
    onOpenSmb: () -> Unit,
    onUp: () -> Unit,
    onScan: () -> Unit,
    onEntryFocused: (FileEntry) -> Unit,
    onEntrySelected: (FileEntry) -> Unit,
) {
    val labels = librarySourceLabels()
    val actionFocusRequesters = remember {
        RemoteSourceAction.entries.associateWith { FocusRequester() }
    }
    val fieldFocusRequesters = remember {
        RemoteSourceField.entries.associateWith { FocusRequester() }
    }
    val browserUpFocusRequester = remember { FocusRequester() }
    var remoteBrowserFocusVersion by remember { mutableIntStateOf(0) }
    var remoteBrowserFocusTarget by remember { mutableStateOf<RemoteBrowserFocusTarget>(RemoteBrowserFocusTarget.UpButton) }
    var previousEditorFocusTarget by remember {
        mutableStateOf<RemoteSourceFocusTarget>(RemoteSourceFocusTarget.Action(RemoteSourceAction.OpenWebDav))
    }
    fun requestRemoteBrowserFocus(target: RemoteBrowserFocusTarget): Boolean {
        remoteBrowserFocusTarget = target
        remoteBrowserFocusVersion += 1
        return true
    }
    fun requestRemoteSourceFocus(target: RemoteSourceFocusTarget?): Boolean =
        when (target) {
            is RemoteSourceFocusTarget.Action -> {
                previousEditorFocusTarget = target
                actionFocusRequesters.getValue(target.action).requestFocus()
                true
            }
            is RemoteSourceFocusTarget.Field -> {
                previousEditorFocusTarget = target
                fieldFocusRequesters.getValue(target.field).requestFocus()
                true
            }
            RemoteSourceFocusTarget.NextPanel -> requestRemoteBrowserFocus(RemoteBrowserFocusTarget.Row(0))
            null -> false
        }
    fun moveRemoteSourceActionFocus(action: RemoteSourceAction, intent: MiruPlayInputIntent): Boolean {
        previousEditorFocusTarget = RemoteSourceFocusTarget.Action(action)
        return requestRemoteSourceFocus(remoteSourceActionFocusTarget(action, intent))
    }
    fun moveRemoteSourceFieldFocus(field: RemoteSourceField, intent: MiruPlayInputIntent): Boolean {
        previousEditorFocusTarget = RemoteSourceFocusTarget.Field(field)
        return requestRemoteSourceFocus(remoteSourceFieldFocusTarget(field, intent))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(0.43f),
            verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
        ) {
            RemoteSourceEditorCard(
                title = MediaSourceType.WEBDAV.tvLabel(),
                badge = MediaSourceType.WEBDAV.tvBadgeLabel(),
                endpoint = remoteSourcePreview(webDavUrl, fallback = MediaSourceType.WEBDAV.sourceEndpointPlaceholderLabel()),
            ) {
                LabeledTextField(
                    labels.webDavUrl,
                    webDavUrl,
                    onValueChange = onWebDavUrlChange,
                    inputModifier = Modifier.remoteSourceFieldNavigation(
                        field = RemoteSourceField.WebDavUrl,
                        focusRequester = fieldFocusRequesters.getValue(RemoteSourceField.WebDavUrl),
                        onMove = ::moveRemoteSourceFieldFocus,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                    LabeledTextField(
                        labels.webDavUser,
                        webDavUsername,
                        onValueChange = onWebDavUsernameChange,
                        modifier = Modifier.weight(1f),
                        inputModifier = Modifier.remoteSourceFieldNavigation(
                            field = RemoteSourceField.WebDavUsername,
                            focusRequester = fieldFocusRequesters.getValue(RemoteSourceField.WebDavUsername),
                            onMove = ::moveRemoteSourceFieldFocus,
                        ),
                    )
                    LabeledTextField(
                        labels.webDavPassword,
                        webDavPassword,
                        onValueChange = onWebDavPasswordChange,
                        modifier = Modifier.weight(1f),
                        inputModifier = Modifier.remoteSourceFieldNavigation(
                            field = RemoteSourceField.WebDavPassword,
                            focusRequester = fieldFocusRequesters.getValue(RemoteSourceField.WebDavPassword),
                            onMove = ::moveRemoteSourceFieldFocus,
                        ),
                    )
                }
                TvActionButton(
                    labels.openWebDav,
                    onClick = onOpenWebDav,
                    modifier = Modifier.remoteSourceActionNavigation(
                        action = RemoteSourceAction.OpenWebDav,
                        focusRequester = actionFocusRequesters.getValue(RemoteSourceAction.OpenWebDav),
                        onMove = ::moveRemoteSourceActionFocus,
                    ),
                )
            }
            RemoteSourceEditorCard(
                title = MediaSourceType.SMB.tvLabel(),
                badge = MediaSourceType.SMB.tvBadgeLabel(),
                endpoint = remoteSourcePreview(smbUrl, fallback = MediaSourceType.SMB.sourceEndpointPlaceholderLabel()),
            ) {
                LabeledTextField(
                    labels.smbUrl,
                    smbUrl,
                    onValueChange = onSmbUrlChange,
                    inputModifier = Modifier.remoteSourceFieldNavigation(
                        field = RemoteSourceField.SmbUrl,
                        focusRequester = fieldFocusRequesters.getValue(RemoteSourceField.SmbUrl),
                        onMove = ::moveRemoteSourceFieldFocus,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                    LabeledTextField(
                        labels.smbDomain,
                        smbDomain,
                        onValueChange = onSmbDomainChange,
                        modifier = Modifier.weight(1f),
                        inputModifier = Modifier.remoteSourceFieldNavigation(
                            field = RemoteSourceField.SmbDomain,
                            focusRequester = fieldFocusRequesters.getValue(RemoteSourceField.SmbDomain),
                            onMove = ::moveRemoteSourceFieldFocus,
                        ),
                    )
                    LabeledTextField(
                        labels.smbUser,
                        smbUsername,
                        onValueChange = onSmbUsernameChange,
                        modifier = Modifier.weight(1f),
                        inputModifier = Modifier.remoteSourceFieldNavigation(
                            field = RemoteSourceField.SmbUsername,
                            focusRequester = fieldFocusRequesters.getValue(RemoteSourceField.SmbUsername),
                            onMove = ::moveRemoteSourceFieldFocus,
                        ),
                    )
                    LabeledTextField(
                        labels.smbPassword,
                        smbPassword,
                        onValueChange = onSmbPasswordChange,
                        modifier = Modifier.weight(1f),
                        inputModifier = Modifier.remoteSourceFieldNavigation(
                            field = RemoteSourceField.SmbPassword,
                            focusRequester = fieldFocusRequesters.getValue(RemoteSourceField.SmbPassword),
                            onMove = ::moveRemoteSourceFieldFocus,
                        ),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                    TvActionButton(
                        labels.openSmb,
                        onClick = onOpenSmb,
                        modifier = Modifier.remoteSourceActionNavigation(
                            action = RemoteSourceAction.OpenSmb,
                            focusRequester = actionFocusRequesters.getValue(RemoteSourceAction.OpenSmb),
                            onMove = ::moveRemoteSourceActionFocus,
                        ),
                    )
                    TvActionButton(
                        labels.scanSource,
                        onClick = onScan,
                        secondary = true,
                        modifier = Modifier.remoteSourceActionNavigation(
                            action = RemoteSourceAction.ScanSource,
                            focusRequester = actionFocusRequesters.getValue(RemoteSourceAction.ScanSource),
                            onMove = ::moveRemoteSourceActionFocus,
                        ),
                    )
                }
            }
            StatusBox(mediaSourceStatusText(status))
        }
        RemoteBrowserPanel(
            remotePath = remotePath,
            entries = entries,
            selectedEntry = selectedEntry,
            onUp = onUp,
            onEntryFocused = onEntryFocused,
            onEntrySelected = onEntrySelected,
            modifier = Modifier.weight(0.57f),
            upFocusRequester = browserUpFocusRequester,
            focusVersion = remoteBrowserFocusVersion,
            focusTarget = remoteBrowserFocusTarget,
            onFocusPreviousPanel = {
                requestRemoteSourceFocus(previousEditorFocusTarget)
            },
        )
    }
}

@Composable
private fun RemoteSourceEditorCard(
    title: String,
    badge: String,
    endpoint: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(CardBg.copy(alpha = 0.48f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .padding(MiruPlayUiMetrics.PANEL_PADDING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    endpoint,
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .width(REMOTE_SOURCE_BADGE_WIDTH_DP.dp)
                    .height(REMOTE_SOURCE_BADGE_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
                    .background(AnimeRed.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, color = Color.White, fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp, fontWeight = FontWeight.Bold)
            }
        }
        content()
    }
}

@Composable
private fun RemoteBrowserPanel(
    remotePath: String,
    entries: List<FileEntry>,
    selectedEntry: FileEntry?,
    onUp: () -> Unit,
    onEntryFocused: (FileEntry) -> Unit,
    onEntrySelected: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
    upFocusRequester: FocusRequester,
    focusVersion: Int = 0,
    focusTarget: RemoteBrowserFocusTarget = RemoteBrowserFocusTarget.UpButton,
    onFocusPreviousPanel: () -> Boolean = { false },
) {
    val labels = librarySourceLabels()
    var remoteBrowserPageStart by remember(remotePath, entries.size) { mutableStateOf(0) }
    var pendingRemoteBrowserRowFocus by remember(remotePath, entries.size) { mutableStateOf<Int?>(null) }
    val pageStart = remoteBrowserCoercedPageStart(
        pageStart = remoteBrowserPageStart,
        itemCount = entries.size,
    )
    val visibleEntries = entries
        .drop(pageStart)
        .take(REMOTE_BROWSER_PAGE_SIZE)
    val focusRequesters = remember(pageStart, visibleEntries.map { it.path }) {
        visibleEntries.associate { it.path to FocusRequester() }
    }
    val emptyFocusRequester = remember { FocusRequester() }
    LaunchedEffect(pageStart, visibleEntries.map { it.path }, pendingRemoteBrowserRowFocus) {
        val pendingIndex = pendingRemoteBrowserRowFocus ?: return@LaunchedEffect
        if (pendingIndex in pageStart until pageStart + visibleEntries.size) {
            focusRequesters[visibleEntries[pendingIndex - pageStart].path]?.requestFocus()
            pendingRemoteBrowserRowFocus = null
        }
    }
    LaunchedEffect(pageStart, visibleEntries.map { it.path }, selectedEntry?.path, pendingRemoteBrowserRowFocus) {
        if (pendingRemoteBrowserRowFocus != null) return@LaunchedEffect
        val selectedIndex = entries.indexOfFirst { it.path == selectedEntry?.path }
        if (selectedIndex >= 0 && selectedIndex !in pageStart until pageStart + visibleEntries.size) {
            remoteBrowserPageStart = remoteBrowserPageStartForIndex(selectedIndex, entries.size)
            pendingRemoteBrowserRowFocus = selectedIndex
            return@LaunchedEffect
        }
        val focusTarget = visibleEntries.firstOrNull { it.path == selectedEntry?.path }
            ?: visibleEntries.firstOrNull()
        focusTarget?.let { entry ->
            focusRequesters[entry.path]?.requestFocus()
        }
    }
    fun requestRemoteBrowserFocus(target: RemoteBrowserFocusTarget?): Boolean {
        return when (target) {
            RemoteBrowserFocusTarget.UpButton -> {
                upFocusRequester.requestFocus()
                true
            }
            is RemoteBrowserFocusTarget.Row -> {
                val index = target.index.takeIf { it in entries.indices } ?: return false
                val targetPageStart = remoteBrowserPageStartForIndex(
                    index = index,
                    itemCount = entries.size,
                )
                remoteBrowserPageStart = targetPageStart
                val visibleIndex = index - targetPageStart
                if (targetPageStart == pageStart) {
                    val entry = visibleEntries.getOrNull(visibleIndex) ?: return false
                    focusRequesters.getValue(entry.path).requestFocus()
                } else {
                    pendingRemoteBrowserRowFocus = index
                }
                true
            }
            RemoteBrowserFocusTarget.EmptyState -> {
                if (visibleEntries.isEmpty()) {
                    emptyFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
            RemoteBrowserFocusTarget.PreviousPanel -> onFocusPreviousPanel()
            null -> false
        }
    }
    LaunchedEffect(focusVersion, entries.size) {
        if (focusVersion > 0) {
            val target = when (focusTarget) {
                is RemoteBrowserFocusTarget.Row -> if (entries.isEmpty()) {
                    RemoteBrowserFocusTarget.EmptyState
                } else {
                    RemoteBrowserFocusTarget.Row(focusTarget.index.coerceIn(entries.indices))
                }
                RemoteBrowserFocusTarget.EmptyState -> if (entries.isEmpty()) {
                    RemoteBrowserFocusTarget.EmptyState
                } else {
                    RemoteBrowserFocusTarget.UpButton
                }
                else -> focusTarget
            }
            requestRemoteBrowserFocus(target)
        }
    }

    TvPanel(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(labels.remoteBrowser, color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    remoteBrowserPathPreview(remotePath),
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvActionButton(
                labels.up,
                onClick = onUp,
                secondary = true,
                modifier = Modifier
                    .width(MiruPlayUiMetrics.CONTROL_BUTTON_WIDTH_DP.dp)
                    .focusRequester(upFocusRequester)
                    .desktopNavigationIntentHandler { intent ->
                        when (val target = remoteBrowserUpButtonFocusTarget(entries.size, intent)) {
                            RemoteBrowserFocusTarget.PreviousPanel -> onFocusPreviousPanel()
                            is RemoteBrowserFocusTarget.Row,
                            RemoteBrowserFocusTarget.EmptyState,
                            -> requestRemoteBrowserFocus(target)
                            RemoteBrowserFocusTarget.UpButton,
                            null,
                            -> false
                        }
                    },
            )
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        if (entries.isEmpty()) {
            RemoteBrowserEmptyState(
                text = labels.remoteEmpty,
                focusRequester = emptyFocusRequester,
                onMove = ::requestRemoteBrowserFocus,
            )
        } else {
            visibleEntries.forEachIndexed { index, entry ->
                RemoteFileRow(
                    entry = entry,
                    selected = selectedEntry?.path == entry.path,
                    onClick = { onEntrySelected(entry) },
                    onNavigationIntent = { intent ->
                        val absoluteIndex = pageStart + index
                        when (val target = entries.remoteBrowserFocusTarget(absoluteIndex, intent)) {
                            is RemoteBrowserFocusTarget.Row -> {
                                val targetEntry = entries[target.index]
                                onEntryFocused(targetEntry)
                                requestRemoteBrowserFocus(target)
                            }
                            RemoteBrowserFocusTarget.UpButton -> {
                                upFocusRequester.requestFocus()
                                true
                            }
                            RemoteBrowserFocusTarget.EmptyState -> false
                            RemoteBrowserFocusTarget.PreviousPanel -> onFocusPreviousPanel()
                            null -> if (remoteBrowserShouldNavigateUp(absoluteIndex, intent)) {
                                onUp()
                                true
                            } else {
                                false
                            }
                        }
                    },
                    modifier = Modifier.focusRequester(focusRequesters.getValue(entry.path)),
                )
                Spacer(Modifier.height(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp))
            }
            remoteBrowserPageSummary(
                pageStart = pageStart,
                visibleCount = visibleEntries.size,
                itemCount = entries.size,
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

@Composable
private fun RemoteBrowserEmptyState(
    text: String,
    focusRequester: FocusRequester,
    onMove: (RemoteBrowserFocusTarget?) -> Boolean,
) {
    DesktopSelectableRow(
        selected = false,
        onClick = {},
        modifier = Modifier
            .focusRequester(focusRequester),
        heightDp = MiruPlayUiMetrics.REMOTE_EMPTY_STATE_HEIGHT_DP,
        inactiveAlpha = 0.48f,
        onNavigationIntent = { intent ->
            onMove(remoteBrowserEmptyFocusTarget(intent))
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
private fun RemoteFileRow(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onNavigationIntent: (MiruPlayInputIntent) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    DesktopSelectableRow(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        onNavigationIntent = onNavigationIntent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                mediaSourceRemoteBrowserItemTypeLabel(entry.isDirectory),
                color = if (entry.isDirectory) AnimeRed else TextSecondary,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(MiruPlayUiMetrics.TYPE_TAG_WIDTH_DP.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
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
            if (!entry.isDirectory && entry.size > 0L) {
                Text(formatFileSize(entry.size), color = TextSecondary, fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp)
            }
        }
    }
}

internal fun List<FileEntry>.remoteBrowserFocusTarget(
    currentIndex: Int,
    key: Key,
): RemoteBrowserFocusTarget? {
    return key.toMiruPlayInputIntent()?.let { intent ->
        remoteBrowserFocusTarget(currentIndex, intent)
    }
}

internal fun List<FileEntry>.remoteBrowserFocusTarget(
    currentIndex: Int,
    intent: MiruPlayInputIntent,
): RemoteBrowserFocusTarget? {
    if (currentIndex !in indices) return null
    return when (intent.horizontalNavigationDelta()) {
        -1 -> RemoteBrowserFocusTarget.PreviousPanel
        else -> focusIndexAfter(
            currentIndex = currentIndex,
            intent = intent,
            axis = MiruPlayFocusAxis.Vertical,
            itemCount = size,
        )?.let(RemoteBrowserFocusTarget::Row)
    }
}

internal fun remoteBrowserUpButtonFocusTarget(
    itemCount: Int,
    key: Key,
): RemoteBrowserFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        remoteBrowserUpButtonFocusTarget(itemCount, intent)
    }

internal fun remoteBrowserUpButtonFocusTarget(
    itemCount: Int,
    intent: MiruPlayInputIntent,
): RemoteBrowserFocusTarget? =
    when (intent.horizontalNavigationDelta()) {
        -1 -> RemoteBrowserFocusTarget.PreviousPanel
        else -> when (intent.verticalNavigationDelta()) {
            1 -> if (itemCount > 0) {
                RemoteBrowserFocusTarget.Row(0)
            } else {
                RemoteBrowserFocusTarget.EmptyState
            }
            else -> null
        }
    }

internal fun remoteBrowserEmptyFocusTarget(key: Key): RemoteBrowserFocusTarget? =
    key.toMiruPlayInputIntent()?.let(::remoteBrowserEmptyFocusTarget)

internal fun remoteBrowserEmptyFocusTarget(intent: MiruPlayInputIntent): RemoteBrowserFocusTarget? =
    when (intent.horizontalNavigationDelta()) {
        -1 -> RemoteBrowserFocusTarget.PreviousPanel
        else -> when (intent.verticalNavigationDelta()) {
            -1 -> RemoteBrowserFocusTarget.UpButton
            else -> null
        }
    }

internal fun remoteBrowserShouldNavigateUp(
    currentIndex: Int,
    key: Key,
): Boolean =
    key.toMiruPlayInputIntent()?.let { intent ->
        remoteBrowserShouldNavigateUp(currentIndex, intent)
    } == true

internal fun remoteBrowserShouldNavigateUp(
    currentIndex: Int,
    intent: MiruPlayInputIntent,
): Boolean = currentIndex == 0 && intent.verticalNavigationDelta() == -1
