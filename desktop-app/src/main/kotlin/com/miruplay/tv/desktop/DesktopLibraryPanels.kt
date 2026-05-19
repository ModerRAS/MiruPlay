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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                text = "已配置媒体源\n点击扫描建立媒体库",
                heightDp = 300,
            )
        } else {
            PosterSearchBar(
                indexQuery = indexQuery,
                onIndexQueryChange = onIndexQueryChange,
                onSearch = onSearch,
                resultCount = posterGroups.size,
            )
            PosterSectionHeader(title = "最高热度", trailing = "已收录 ${posterGroups.size} 部")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                posterGroups.take(2).forEach { group ->
                    FeaturedPosterCard(
                        group = group,
                        selected = selectedEntry?.path?.let { it in group.entryPaths } == true,
                        onClick = { onEntrySelected(group.primaryEntry) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (posterGroups.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }

            PosterSectionHeader(title = "最近添加")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                posterGroups
                    .sortedByDescending { it.lastModified }
                    .take(4)
                    .forEach { group ->
                        LibraryPosterCard(
                            group = group,
                            selected = selectedEntry?.path?.let { it in group.entryPaths } == true,
                            onClick = { onEntrySelected(group.primaryEntry) },
                        )
                    }
            }

            PosterSectionHeader(title = "海报墙")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                posterGroups.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { group ->
                            LibraryPosterCard(
                                group = group,
                                selected = selectedEntry?.path?.let { it in group.entryPaths } == true,
                                onClick = { onEntrySelected(group.primaryEntry) },
                            )
                        }
                    }
                }
            }
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
    onDetails: () -> Unit,
    onPlayer: () -> Unit,
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
            TvActionButton("扫描", onClick = onScan, modifier = Modifier.width(132.dp))
            TvActionButton("详情", onClick = onDetails, secondary = true, modifier = Modifier.width(132.dp))
            TvActionButton("播放", onClick = onPlayer, secondary = true, modifier = Modifier.width(132.dp))
            TvActionButton("设置", onClick = onSettings, modifier = Modifier.width(132.dp))
        }
    }
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
                    LabeledTextField("Local library root", libraryRoot, onValueChange = onLibraryRootChange, modifier = Modifier.weight(1.25f))
                    SavedSourcePicker(
                        sources = savedSources,
                        activeSourceId = activeSourceId,
                        onSelected = onSavedSourceSelected,
                        modifier = Modifier.weight(0.82f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    LabeledTextField("Index query", indexQuery, onValueChange = onIndexQueryChange, modifier = Modifier.weight(1.4f))
                    TvActionButton("Open local", onClick = onOpenLocal, modifier = Modifier.weight(0.72f))
                    TvActionButton("Scan", onClick = onScan, secondary = true, modifier = Modifier.weight(0.54f))
                    TvActionButton("Search", onClick = onSearch, secondary = true, modifier = Modifier.weight(1f))
                }
            }
            Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                TvActionButton("Clear index", onClick = onClearIndex, secondary = true, modifier = Modifier.fillMaxWidth())
                TvActionButton("Remove source", onClick = onRemoveSource, secondary = true, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        StatusBox(status)
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
private fun FeaturedPosterCard(
    group: DesktopPosterGroup,
    selected: Boolean,
    onClick: () -> Unit,
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val active = selected || focused
    Box(
        modifier = Modifier
            .width(MiruPlayUiMetrics.POSTER_WIDTH_DP.dp)
            .height(MiruPlayUiMetrics.POSTER_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(CardBg)
            .border(
                width = if (active) 3.dp else 1.dp,
                color = if (active) AnimeRed else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
            )
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
    onEntrySelected: (FileEntry) -> Unit,
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.42f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                Text("Remote sources", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                LabeledTextField("WebDAV URL", webDavUrl, onValueChange = onWebDavUrlChange)
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                    LabeledTextField(
                        "WebDAV user",
                        webDavUsername,
                        onValueChange = onWebDavUsernameChange,
                        modifier = Modifier.weight(1f),
                    )
                    LabeledTextField(
                        "WebDAV password",
                        webDavPassword,
                        onValueChange = onWebDavPasswordChange,
                        modifier = Modifier.weight(1f),
                    )
                }
                TvActionButton("Open WebDAV", onClick = onOpenWebDav)
                Spacer(Modifier.height(MiruPlayUiMetrics.TINY_GAP_DP.dp))
                LabeledTextField("SMB URL", smbUrl, onValueChange = onSmbUrlChange)
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                    LabeledTextField(
                        "SMB domain",
                        smbDomain,
                        onValueChange = onSmbDomainChange,
                        modifier = Modifier.weight(1f),
                    )
                    LabeledTextField(
                        "SMB user",
                        smbUsername,
                        onValueChange = onSmbUsernameChange,
                        modifier = Modifier.weight(1f),
                    )
                    LabeledTextField(
                        "SMB password",
                        smbPassword,
                        onValueChange = onSmbPasswordChange,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                    TvActionButton("Open SMB", onClick = onOpenSmb)
                    TvActionButton("Scan source", onClick = onScan, secondary = true)
                }
                StatusBox(status)
            }
            Column(
                modifier = Modifier.weight(0.58f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Remote browser", color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            remotePath.ifBlank { "/" },
                            color = TextSecondary,
                            fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TvActionButton("Up", onClick = onUp, secondary = true, modifier = Modifier.width(MiruPlayUiMetrics.CONTROL_BUTTON_WIDTH_DP.dp))
                }
                if (entries.isEmpty()) {
                    DesktopEmptyState(
                        text = "Open a remote source to list files.",
                        heightDp = MiruPlayUiMetrics.REMOTE_EMPTY_STATE_HEIGHT_DP,
                    )
                } else {
                    entries.take(8).forEach { entry ->
                        RemoteFileRow(
                            entry = entry,
                            selected = selectedEntry?.path == entry.path,
                            onClick = { onEntrySelected(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteFileRow(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DesktopSelectableRow(selected = selected, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (entry.isDirectory) "DIR" else "VID",
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
