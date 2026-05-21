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
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.formatFileSize
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.displayName

private const val POSTER_WALL_COLUMNS = 6
private const val REMOTE_SOURCE_PREVIEW_LIMIT = 70
private const val REMOTE_BROWSER_PATH_LIMIT = 86
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
    onEntryFocused: (MediaIndexEntry) -> Unit,
    onEntrySelected: (MediaIndexEntry) -> Unit,
) {
    val posterGroups = remember(entries) { entries.toDesktopPosterGroups() }

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
            )
            DesktopEmptyState(
                text = if (savedSources.isEmpty()) "添加媒体源开始使用" else "已配置媒体源\n点击扫描建立媒体库",
                heightDp = 300,
            )
        } else {
            val featuredGroups = remember(posterGroups) { posterGroups.toFeaturedPosterGroups() }
            val recentlyAddedGroups = remember(posterGroups) { posterGroups.toRecentlyAddedPosterGroups() }

            PosterSectionHeader(title = "海报墙", trailing = "已收录 ${posterGroups.size} 部")
            PosterWall(
                groups = posterGroups,
                selectedEntry = selectedEntry,
                onEntryFocused = onEntryFocused,
                onEntrySelected = onEntrySelected,
            )

            if (featuredGroups.isNotEmpty()) {
                PosterSectionHeader(title = "最高热度")
                FeaturedPosterShelf(
                    groups = featuredGroups,
                    selectedEntry = selectedEntry,
                    onEntrySelected = onEntrySelected,
                )
            }

            if (recentlyAddedGroups.isNotEmpty()) {
                PosterSectionHeader(title = "最近添加")
                PosterCardShelf(
                    groups = recentlyAddedGroups,
                    selectedEntry = selectedEntry,
                    onEntrySelected = onEntrySelected,
                )
            }

            PosterSearchBar(
                indexQuery = indexQuery,
                onIndexQueryChange = onIndexQueryChange,
                onSearch = onSearch,
                resultCount = posterGroups.size,
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
            )
        }
    }
}

@Composable
internal fun DesktopLibraryHeader(
    onScan: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("探索", color = TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("本地媒体库 · Bangumi 元数据", color = TextSecondary, fontSize = 24.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            desktopLibraryHeaderActions().forEach { action ->
                TvActionButton(
                    action.label,
                    onClick = when (action) {
                        DesktopLibraryHeaderAction.Scan -> onScan
                        DesktopLibraryHeaderAction.Settings -> onSettings
                    },
                    modifier = Modifier.width(132.dp),
                )
            }
        }
    }
}

internal enum class DesktopLibraryHeaderAction(val label: String) {
    Scan("扫描"),
    Settings("设置"),
}

internal fun desktopLibraryHeaderActions(): List<DesktopLibraryHeaderAction> =
    DesktopLibraryHeaderAction.entries

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
}

private fun Modifier.librarySourceActionNavigation(
    action: LibrarySourceAction,
    focusRequester: FocusRequester,
    onMove: (LibrarySourceAction, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            event.type == KeyEventType.KeyDown && onMove(action, event.key)
        }

private fun Modifier.librarySourceFieldNavigation(
    field: LibrarySourceField,
    focusRequester: FocusRequester,
    onMove: (LibrarySourceField, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            event.type == KeyEventType.KeyDown && onMove(field, event.key)
        }

internal fun librarySourceActionFocusTarget(
    current: LibrarySourceAction,
    key: Key,
): LibrarySourceFocusTarget? =
    when {
        current == LibrarySourceAction.OpenLocal && key == Key.DirectionLeft ->
            LibrarySourceFocusTarget.Field(LibrarySourceField.IndexQuery)
        else -> librarySourceActionNavigationTarget(current, key)?.let(LibrarySourceFocusTarget::Action)
    }

internal fun librarySourceFieldFocusTarget(
    current: LibrarySourceField,
    key: Key,
): LibrarySourceFocusTarget? =
    when (key) {
        Key.DirectionUp -> if (current == LibrarySourceField.IndexQuery) {
            LibrarySourceFocusTarget.Field(LibrarySourceField.LocalRoot)
        } else {
            null
        }
        Key.DirectionDown -> if (current == LibrarySourceField.LocalRoot) {
            LibrarySourceFocusTarget.Field(LibrarySourceField.IndexQuery)
        } else {
            null
        }
        Key.DirectionRight -> if (current == LibrarySourceField.IndexQuery) {
            LibrarySourceFocusTarget.Action(LibrarySourceAction.OpenLocal)
        } else {
            null
        }
        else -> null
    }

internal fun librarySourceActionNavigationTarget(
    current: LibrarySourceAction,
    key: Key,
): LibrarySourceAction? =
    when (key) {
        Key.DirectionLeft -> librarySourceHorizontalAction(current, -1)
        Key.DirectionRight -> librarySourceHorizontalAction(current, 1)
        Key.DirectionUp -> if (current == LibrarySourceAction.RemoveSource) LibrarySourceAction.ClearIndex else null
        Key.DirectionDown -> if (current == LibrarySourceAction.ClearIndex) LibrarySourceAction.RemoveSource else null
        else -> null
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
            .let { row -> row.getOrNull(row.indexOf(current) + delta) }
        LibrarySourceAction.ClearIndex -> listOf(LibrarySourceAction.Search, LibrarySourceAction.ClearIndex)
            .let { row -> row.getOrNull(row.indexOf(current) + delta) }
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
}

private fun Modifier.remoteSourceActionNavigation(
    action: RemoteSourceAction,
    focusRequester: FocusRequester,
    onMove: (RemoteSourceAction, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            event.type == KeyEventType.KeyDown && onMove(action, event.key)
        }

private fun Modifier.remoteSourceFieldNavigation(
    field: RemoteSourceField,
    focusRequester: FocusRequester,
    onMove: (RemoteSourceField, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            event.type == KeyEventType.KeyDown && onMove(field, event.key)
        }

internal fun remoteSourceActionFocusTarget(
    current: RemoteSourceAction,
    key: Key,
): RemoteSourceFocusTarget? =
    when (key) {
        Key.DirectionUp -> when (current) {
            RemoteSourceAction.OpenWebDav -> RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavPassword)
            RemoteSourceAction.OpenSmb -> RemoteSourceFocusTarget.Field(RemoteSourceField.SmbDomain)
            RemoteSourceAction.ScanSource -> RemoteSourceFocusTarget.Field(RemoteSourceField.SmbPassword)
        }
        else -> remoteSourceActionNavigationTarget(current, key)?.let(RemoteSourceFocusTarget::Action)
    }

internal fun remoteSourceFieldFocusTarget(
    current: RemoteSourceField,
    key: Key,
): RemoteSourceFocusTarget? =
    when (key) {
        Key.DirectionLeft -> remoteSourceHorizontalField(current, -1)?.let(RemoteSourceFocusTarget::Field)
        Key.DirectionRight -> remoteSourceHorizontalField(current, 1)?.let(RemoteSourceFocusTarget::Field)
        Key.DirectionUp -> when (current) {
            RemoteSourceField.WebDavUsername,
            RemoteSourceField.WebDavPassword,
            -> RemoteSourceFocusTarget.Field(RemoteSourceField.WebDavUrl)
            RemoteSourceField.SmbDomain,
            RemoteSourceField.SmbUsername,
            RemoteSourceField.SmbPassword,
            -> RemoteSourceFocusTarget.Field(RemoteSourceField.SmbUrl)
            else -> null
        }
        Key.DirectionDown -> when (current) {
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
        else -> null
    }

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
    val targetIndex = row.indexOf(current) + delta
    return row.getOrNull(targetIndex)
}

internal fun remoteSourceActionNavigationTarget(
    current: RemoteSourceAction,
    key: Key,
): RemoteSourceAction? =
    when (key) {
        Key.DirectionLeft -> if (current == RemoteSourceAction.ScanSource) RemoteSourceAction.OpenSmb else null
        Key.DirectionRight -> if (current == RemoteSourceAction.OpenSmb) RemoteSourceAction.ScanSource else null
        Key.DirectionUp -> when (current) {
            RemoteSourceAction.OpenSmb,
            RemoteSourceAction.ScanSource,
            -> RemoteSourceAction.OpenWebDav
            RemoteSourceAction.OpenWebDav -> null
        }
        Key.DirectionDown -> if (current == RemoteSourceAction.OpenWebDav) RemoteSourceAction.OpenSmb else null
        else -> null
    }

@Composable
private fun PosterSearchBar(
    indexQuery: String,
    onIndexQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    resultCount: Int,
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledTextField(
                "搜索媒体库",
                indexQuery,
                onValueChange = onIndexQueryChange,
                modifier = Modifier.weight(1f),
            )
            TvActionButton("搜索", onClick = onSearch, modifier = Modifier.width(132.dp))
            Text(
                "$resultCount 部",
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
) {
    val labels = desktopLibrarySourceLabels()
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
            null -> false
        }
    fun moveLibrarySourceActionFocus(action: LibrarySourceAction, key: Key): Boolean {
        return requestLibrarySourceFocus(librarySourceActionFocusTarget(action, key))
    }
    fun moveLibrarySourceFieldFocus(field: LibrarySourceField, key: Key): Boolean =
        requestLibrarySourceFocus(librarySourceFieldFocusTarget(field, key))
    LaunchedEffect(sourcePickerFocusVersion) {
        if (sourcePickerFocusVersion > 0) {
            sourcePickerFocusRequester.requestFocus()
        }
    }

    TvPanel(Modifier.fillMaxWidth()) {
        Text("媒体源", color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
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
        StatusBox(desktopLibraryStatusText(status))
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
    selectedEntry: MediaIndexEntry?,
    onEntryFocused: (MediaIndexEntry) -> Unit,
    onEntrySelected: (MediaIndexEntry) -> Unit,
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
                            groups.posterNavigationTarget(groupIndex, key)?.let { target ->
                                onEntryFocused(target.primaryEntry)
                                true
                            } ?: false
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
    selectedEntry: MediaIndexEntry?,
    onEntrySelected: (MediaIndexEntry) -> Unit,
) {
    val focusRequesters = remember(groups) {
        groups.associate { it.primaryEntry.path to FocusRequester() }
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
                    groups.posterShelfNavigationTarget(index, key)?.let { target ->
                        focusRequesters.getValue(target.primaryEntry.path).requestFocus()
                        true
                    } ?: false
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
    selectedEntry: MediaIndexEntry?,
    onEntrySelected: (MediaIndexEntry) -> Unit,
) {
    val focusRequesters = remember(groups) {
        groups.associate { it.primaryEntry.path to FocusRequester() }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        groups.forEachIndexed { index, group ->
            LibraryPosterCard(
                group = group,
                selected = selectedEntry?.path?.let { it in group.entryPaths } == true,
                onClick = { onEntrySelected(group.primaryEntry) },
                onNavigationKey = { key ->
                    groups.posterShelfNavigationTarget(index, key)?.let { target ->
                        focusRequesters.getValue(target.primaryEntry.path).requestFocus()
                        true
                    } ?: false
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
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        -> {
                            onClick()
                            true
                        }
                        else -> onNavigationKey(event.key)
                    }
                }
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
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        -> {
                            onClick()
                            true
                        }
                        else -> onNavigationKey(event.key)
                    }
                }
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

internal data class DesktopPosterGroup(
    val title: String,
    val entries: List<MediaIndexEntry>,
) {
    val primaryEntry: MediaIndexEntry = entries.sortedWith(compareBy<MediaIndexEntry> { it.episodeNumber ?: Int.MAX_VALUE }.thenBy { it.path }).first()
    val entryPaths: Set<String> = entries.map { it.path }.toSet()
    val lastModified: Long = entries.maxOfOrNull { it.lastModified } ?: 0L
    val subtitle: String = buildString {
        append(entries.size)
        append(" episode")
        if (entries.size != 1) append('s')
        primaryEntry.seasonNumber?.let { append(" · S").append(it) }
        primaryEntry.metadataSource?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }
}

internal fun List<MediaIndexEntry>.toDesktopPosterGroups(): List<DesktopPosterGroup> =
    filterNot { it.isDirectory }
        .groupBy { it.posterTitle() }
        .map { (title, groupEntries) -> DesktopPosterGroup(title = title, entries = groupEntries) }
        .sortedBy { it.title.lowercase() }

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

internal fun List<DesktopPosterGroup>.posterShelfNavigationTarget(
    currentIndex: Int,
    key: Key,
): DesktopPosterGroup? {
    if (currentIndex !in indices) return null
    val targetIndex = when (key) {
        Key.DirectionRight -> currentIndex + 1
        Key.DirectionLeft -> currentIndex - 1
        else -> null
    } ?: return null
    return getOrNull(targetIndex)
}

internal fun List<DesktopPosterGroup>.posterNavigationTarget(
    currentIndex: Int,
    key: Key,
    columns: Int = POSTER_WALL_COLUMNS,
): DesktopPosterGroup? {
    if (currentIndex !in indices) return null
    val safeColumns = columns.coerceAtLeast(1)
    val currentColumn = currentIndex % safeColumns
    val targetIndex = when (key) {
        Key.DirectionRight -> if (currentColumn == safeColumns - 1) null else currentIndex + 1
        Key.DirectionLeft -> if (currentColumn == 0) null else currentIndex - 1
        Key.DirectionDown -> {
            val nextRowStart = ((currentIndex / safeColumns) + 1) * safeColumns
            if (nextRowStart !in indices) {
                null
            } else {
                minOf(nextRowStart + currentColumn, lastIndex)
            }
        }
        Key.DirectionUp -> currentIndex - safeColumns
        else -> null
    } ?: return null
    return getOrNull(targetIndex)
}

internal fun MediaIndexEntry.posterTitle(): String =
    metadataTitle?.takeIf { it.isNotBlank() }
        ?: animeName?.takeIf { it.isNotBlank() }
        ?: MediaPathConventions.stem(path).takeIf { it.isNotBlank() }
        ?: path.substringAfterLast('/').substringAfterLast('\\')

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
    val labels = desktopLibrarySourceLabels()
    val actionFocusRequesters = remember {
        RemoteSourceAction.entries.associateWith { FocusRequester() }
    }
    val fieldFocusRequesters = remember {
        RemoteSourceField.entries.associateWith { FocusRequester() }
    }
    fun requestRemoteSourceFocus(target: RemoteSourceFocusTarget?): Boolean =
        when (target) {
            is RemoteSourceFocusTarget.Action -> {
                actionFocusRequesters.getValue(target.action).requestFocus()
                true
            }
            is RemoteSourceFocusTarget.Field -> {
                fieldFocusRequesters.getValue(target.field).requestFocus()
                true
            }
            null -> false
        }
    fun moveRemoteSourceActionFocus(action: RemoteSourceAction, key: Key): Boolean {
        return requestRemoteSourceFocus(remoteSourceActionFocusTarget(action, key))
    }
    fun moveRemoteSourceFieldFocus(field: RemoteSourceField, key: Key): Boolean =
        requestRemoteSourceFocus(remoteSourceFieldFocusTarget(field, key))
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
                title = "WebDAV",
                badge = "DAV",
                endpoint = remoteSourcePreview(webDavUrl, fallback = "填写 WebDAV 地址"),
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
                title = "SMB",
                badge = "SMB",
                endpoint = remoteSourcePreview(smbUrl, fallback = "填写 SMB 共享地址"),
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
            StatusBox(desktopLibraryStatusText(status))
        }
        RemoteBrowserPanel(
            remotePath = remotePath,
            entries = entries,
            selectedEntry = selectedEntry,
            onUp = onUp,
            onEntryFocused = onEntryFocused,
            onEntrySelected = onEntrySelected,
            modifier = Modifier.weight(0.57f),
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
) {
    val labels = desktopLibrarySourceLabels()
    val visibleEntries = remember(entries) { entries.take(8) }
    val focusRequesters = remember(visibleEntries) {
        visibleEntries.associate { it.path to FocusRequester() }
    }
    LaunchedEffect(visibleEntries, selectedEntry?.path) {
        val focusTarget = visibleEntries.firstOrNull { it.path == selectedEntry?.path } ?: visibleEntries.firstOrNull()
        focusTarget?.let { entry ->
            focusRequesters[entry.path]?.requestFocus()
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
            TvActionButton(labels.up, onClick = onUp, secondary = true, modifier = Modifier.width(MiruPlayUiMetrics.CONTROL_BUTTON_WIDTH_DP.dp))
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        if (entries.isEmpty()) {
            DesktopEmptyState(
                text = labels.remoteEmpty,
                heightDp = MiruPlayUiMetrics.REMOTE_EMPTY_STATE_HEIGHT_DP,
            )
        } else {
            visibleEntries.forEachIndexed { index, entry ->
                RemoteFileRow(
                    entry = entry,
                    selected = selectedEntry?.path == entry.path,
                    onClick = { onEntrySelected(entry) },
                    onNavigationKey = { key ->
                        visibleEntries.remoteBrowserNavigationTarget(index, key)?.let { target ->
                            onEntryFocused(target)
                            true
                        } ?: if (remoteBrowserShouldNavigateUp(index, key)) {
                            onUp()
                            true
                        } else {
                            false
                        }
                    },
                    modifier = Modifier.focusRequester(focusRequesters.getValue(entry.path)),
                )
                Spacer(Modifier.height(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp))
            }
        }
    }
}

internal fun remoteSourcePreview(
    value: String,
    fallback: String,
    maxLength: Int = REMOTE_SOURCE_PREVIEW_LIMIT,
): String =
    value.trim()
        .ifBlank { fallback }
        .compactMiddle(maxLength)

internal fun remoteBrowserPathPreview(
    path: String,
    maxLength: Int = REMOTE_BROWSER_PATH_LIMIT,
): String =
    path.trim()
        .ifBlank { "/" }
        .compactMiddle(maxLength)

@Composable
private fun RemoteFileRow(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onNavigationKey: (Key) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    DesktopSelectableRow(
        selected = selected,
        onClick = onClick,
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                when (event.key) {
                    Key.Enter,
                    Key.NumPadEnter,
                    -> {
                        onClick()
                        true
                    }
                    else -> onNavigationKey(event.key)
                }
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (entry.isDirectory) "目录" else "视频",
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

private fun List<FileEntry>.remoteBrowserNavigationTarget(
    currentIndex: Int,
    key: Key,
): FileEntry? {
    if (currentIndex !in indices) return null
    val targetIndex = when (key) {
        Key.DirectionDown -> currentIndex + 1
        Key.DirectionUp -> currentIndex - 1
        else -> null
    } ?: return null
    return getOrNull(targetIndex)
}

internal fun remoteBrowserShouldNavigateUp(
    currentIndex: Int,
    key: Key,
): Boolean = currentIndex == 0 && key == Key.DirectionUp

internal data class DesktopLibrarySourceLabels(
    val localLibraryRoot: String,
    val indexQuery: String,
    val openLocal: String,
    val scan: String,
    val search: String,
    val clearIndex: String,
    val removeSource: String,
    val webDavUrl: String,
    val webDavUser: String,
    val webDavPassword: String,
    val openWebDav: String,
    val smbUrl: String,
    val smbDomain: String,
    val smbUser: String,
    val smbPassword: String,
    val openSmb: String,
    val scanSource: String,
    val remoteBrowser: String,
    val up: String,
    val remoteEmpty: String,
)

internal fun desktopLibrarySourceLabels(): DesktopLibrarySourceLabels =
    DesktopLibrarySourceLabels(
        localLibraryRoot = "本地媒体库路径",
        indexQuery = "索引搜索",
        openLocal = "打开本地",
        scan = "扫描",
        search = "搜索",
        clearIndex = "清空索引",
        removeSource = "移除媒体源",
        webDavUrl = "WebDAV 地址",
        webDavUser = "WebDAV 用户名",
        webDavPassword = "WebDAV 密码",
        openWebDav = "打开 WebDAV",
        smbUrl = "SMB 地址",
        smbDomain = "SMB 域",
        smbUser = "SMB 用户名",
        smbPassword = "SMB 密码",
        openSmb = "打开 SMB",
        scanSource = "扫描媒体源",
        remoteBrowser = "远程浏览",
        up = "上级",
        remoteEmpty = "先打开一个远程媒体源以浏览文件。",
    )

private val loadedSourceStatusRegex = Regex("""^Loaded( saved)? (local|WebDAV|SMB) source: (.+)$""")
private val readySourceStatusRegex = Regex("""^(Local|WebDAV|SMB) source ready: (.+)$""")
private val scanCompleteStatusRegex = Regex("""^Scan complete: (\d+) videos, (\d+) directories\.$""")
private val rescanCompleteStatusRegex = Regex("""^Rescan complete: (\d+) videos, (\d+) directories\.$""")
private val indexClearedStatusRegex = Regex("""^Index cleared for source id: (\d+)\.$""")
private val loadingRemoteStatusRegex = Regex("""^Loading (LOCAL|WEBDAV|SMB) (.+)\.\.\.$""")
private val showingRemoteStatusRegex = Regex("""^Showing (\d+) item\(s\) from (.+)\.$""")
private val remotePlaybackStatusRegex = Regex("""^Selected remote media: (.+)\. mpv will stream through the local bridge\.$""")
private val selectedPlaybackStatusRegex = Regex("""^Selected (.+) for playback\.$""")
private val indexedNoMatchStatusRegex = Regex("""^No indexed media matched "(.*)"\.$""")
private val indexedResultStatusRegex = Regex("""^Showing (\d+) indexed video result\(s\)\.$""")

internal fun desktopLibraryStatusText(status: String): String =
    when {
        status == "Add a local library source or load an existing one." ->
            "添加本地媒体源，或载入已保存的媒体源。"
        status == "Open a WebDAV or SMB source to browse it." ->
            "打开 WebDAV 或 SMB 媒体源后即可浏览文件。"
        status == "Enter a local library root first." ->
            "请先填写本地媒体库路径。"
        status == "Enter a WebDAV URL first." ->
            "请先填写 WebDAV 地址。"
        status == "Enter an SMB URL first." ->
            "请先填写 SMB 地址。"
        status == "Open a source before scanning." ->
            "请先打开媒体源，再开始扫描。"
        status == "Open or scan a source before searching." ->
            "请先打开或扫描媒体源，再搜索。"
        status == "Open or scan a source before clearing its index." ->
            "请先打开或扫描媒体源，再清空索引。"
        status == "Open a source before removing it." ->
            "请先打开媒体源，再移除。"
        status == "Source removed. Associated index entries were cleared." ->
            "媒体源已移除，关联索引已清空。"
        status == "Already at the source root." ->
            "已经在媒体源根目录。"
        status == "Open a remote source before browsing." ->
            "请先打开远程媒体源，再浏览。"
        else -> desktopLibraryDynamicStatusText(status) ?: status
    }

private fun desktopLibraryDynamicStatusText(status: String): String? {
    loadedSourceStatusRegex.matchEntire(status)?.let { match ->
        val saved = match.groupValues[1].isNotBlank()
        val type = match.groupValues[2].desktopSourceTypeLabel()
        val name = match.groupValues[3]
        return if (saved) {
            "已载入已保存媒体源：$name · $type"
        } else {
            "已载入媒体源：$name · $type"
        }
    }
    readySourceStatusRegex.matchEntire(status)?.let { match ->
        val type = match.groupValues[1].desktopSourceTypeLabel()
        val sourceType = if (type == "本地") "${type}媒体源" else "$type 媒体源"
        return "${sourceType}已就绪：${match.groupValues[2]}"
    }
    status.removePrefix("Scanning ").takeIf { it != status && it.endsWith("...") }?.let { name ->
        return "正在扫描：${name.removeSuffix("...")}"
    }
    scanCompleteStatusRegex.matchEntire(status)?.let { match ->
        return "扫描完成：${match.groupValues[1]} 个视频，${match.groupValues[2]} 个目录。"
    }
    rescanCompleteStatusRegex.matchEntire(status)?.let { match ->
        return "重扫完成：${match.groupValues[1]} 个视频，${match.groupValues[2]} 个目录。"
    }
    indexClearedStatusRegex.matchEntire(status)?.let { match ->
        return "已清空媒体源 #${match.groupValues[1]} 的索引。"
    }
    loadingRemoteStatusRegex.matchEntire(status)?.let { match ->
        return "正在载入 ${match.groupValues[1].desktopSourceTypeLabel()}：${match.groupValues[2]}"
    }
    showingRemoteStatusRegex.matchEntire(status)?.let { match ->
        return "${match.groupValues[2]} 中显示 ${match.groupValues[1]} 个条目。"
    }
    remotePlaybackStatusRegex.matchEntire(status)?.let { match ->
        return "已选择远程媒体：${match.groupValues[1]}。mpv 将通过本地桥接串流。"
    }
    selectedPlaybackStatusRegex.matchEntire(status)?.let { match ->
        return "已选择播放：${match.groupValues[1]}"
    }
    indexedNoMatchStatusRegex.matchEntire(status)?.let { match ->
        return "没有匹配 \"${match.groupValues[1]}\" 的索引媒体。"
    }
    indexedResultStatusRegex.matchEntire(status)?.let { match ->
        return "显示 ${match.groupValues[1]} 条索引视频结果。"
    }
    return null
}

private fun String.desktopSourceTypeLabel(): String =
    when (this) {
        "local",
        "Local",
        "LOCAL",
        -> "本地"
        "WebDAV",
        "WEBDAV",
        -> "WebDAV"
        "SMB" -> "SMB"
        else -> this
    }
