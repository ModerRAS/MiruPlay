package com.miruplay.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.RssSubscriptionInfo

private enum class DesktopSettingsSection(
    val title: String,
    val description: String,
) {
    Sources("媒体源", "本地、WebDAV、SMB"),
    Playback("播放", "mpv 与 RIFE"),
    CloudDrive("CloudDrive", "RSS 离线下载与入库"),
    Scan("扫描", "媒体库更新"),
    Metadata("元数据", "Bangumi 匹配"),
}

@Composable
internal fun CloudRssPanel(
    endpointUrl: String,
    onEndpointUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    inboxPath: String,
    onInboxPathChange: (String) -> Unit,
    libraryPath: String,
    onLibraryPathChange: (String) -> Unit,
    intervalMinutes: String,
    onIntervalMinutesChange: (String) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    proxyEnabled: Boolean,
    onProxyEnabledChange: (Boolean) -> Unit,
    proxyHost: String,
    onProxyHostChange: (String) -> Unit,
    proxyPort: String,
    onProxyPortChange: (String) -> Unit,
    rssName: String,
    onRssNameChange: (String) -> Unit,
    rssUrl: String,
    onRssUrlChange: (String) -> Unit,
    rssFilter: String,
    onRssFilterChange: (String) -> Unit,
    rssEnabled: Boolean,
    onRssEnabledChange: (Boolean) -> Unit,
    subscriptions: List<RssSubscriptionInfo>,
    selectedSubscription: RssSubscriptionInfo?,
    status: String,
    schedulerStatus: String,
    linkedSourceLabel: String,
    onSaveConfig: () -> Unit,
    onSaveCredentials: () -> Unit,
    onLoginCloudDrive: () -> Unit,
    onVerifyApiToken: () -> Unit,
    onClearCredentials: () -> Unit,
    onRunSync: () -> Unit,
    onStartScheduler: () -> Unit,
    onStopScheduler: () -> Unit,
    onUseActiveScanSource: () -> Unit,
    onClearScanSource: () -> Unit,
    onSaveSubscription: () -> Unit,
    onSubscriptionSelected: (RssSubscriptionInfo) -> Unit,
    onDeleteSubscription: () -> Unit,
    sources: List<MediaSourceInfo>,
    activeSourceLabel: String,
    indexedItemCount: Int,
    recentCount: Int,
    selectedMediaTitle: String,
    playbackSummary: String,
    metadataSummary: String,
    libraryStatus: String,
    onOpenLibrary: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenDetails: () -> Unit,
    onScanActiveSource: () -> Unit,
) {
    var selectedSection by remember { mutableStateOf(DesktopSettingsSection.Sources) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SettingsSectionMenu(
            selectedSection = selectedSection,
            sourcesCount = sources.size,
            rssCount = subscriptions.size,
            cloudEnabled = enabled,
            metadataSummary = metadataSummary,
            playbackSummary = playbackSummary,
            onSectionSelected = { selectedSection = it },
            modifier = Modifier.width(292.dp),
        )
        when (selectedSection) {
            DesktopSettingsSection.CloudDrive -> CloudRssAutomationContent(
                endpointUrl = endpointUrl,
                onEndpointUrlChange = onEndpointUrlChange,
                username = username,
                onUsernameChange = onUsernameChange,
                token = token,
                onTokenChange = onTokenChange,
                password = password,
                onPasswordChange = onPasswordChange,
                inboxPath = inboxPath,
                onInboxPathChange = onInboxPathChange,
                libraryPath = libraryPath,
                onLibraryPathChange = onLibraryPathChange,
                intervalMinutes = intervalMinutes,
                onIntervalMinutesChange = onIntervalMinutesChange,
                enabled = enabled,
                onEnabledChange = onEnabledChange,
                proxyEnabled = proxyEnabled,
                onProxyEnabledChange = onProxyEnabledChange,
                proxyHost = proxyHost,
                onProxyHostChange = onProxyHostChange,
                proxyPort = proxyPort,
                onProxyPortChange = onProxyPortChange,
                rssName = rssName,
                onRssNameChange = onRssNameChange,
                rssUrl = rssUrl,
                onRssUrlChange = onRssUrlChange,
                rssFilter = rssFilter,
                onRssFilterChange = onRssFilterChange,
                rssEnabled = rssEnabled,
                onRssEnabledChange = onRssEnabledChange,
                subscriptions = subscriptions,
                selectedSubscription = selectedSubscription,
                status = status,
                schedulerStatus = schedulerStatus,
                linkedSourceLabel = linkedSourceLabel,
                onSaveConfig = onSaveConfig,
                onSaveCredentials = onSaveCredentials,
                onLoginCloudDrive = onLoginCloudDrive,
                onVerifyApiToken = onVerifyApiToken,
                onClearCredentials = onClearCredentials,
                onRunSync = onRunSync,
                onStartScheduler = onStartScheduler,
                onStopScheduler = onStopScheduler,
                onUseActiveScanSource = onUseActiveScanSource,
                onClearScanSource = onClearScanSource,
                onSaveSubscription = onSaveSubscription,
                onSubscriptionSelected = onSubscriptionSelected,
                onDeleteSubscription = onDeleteSubscription,
                modifier = Modifier.weight(1f),
            )
            DesktopSettingsSection.Sources -> SettingsSummaryContent(
                section = selectedSection,
                tiles = sourceSettingsTiles(
                    sources = sources,
                    activeSourceLabel = activeSourceLabel,
                    indexedItemCount = indexedItemCount,
                ),
                status = libraryStatus,
                actions = listOf(
                    SettingsQuickAction("打开海报墙", onOpenLibrary),
                    SettingsQuickAction("扫描当前源", onScanActiveSource),
                ),
                modifier = Modifier.weight(1f),
            )
            DesktopSettingsSection.Playback -> SettingsSummaryContent(
                section = selectedSection,
                tiles = playbackSettingsTiles(
                    playbackSummary = playbackSummary,
                    recentCount = recentCount,
                    selectedMediaTitle = selectedMediaTitle,
                ),
                status = "mpv 播放设置保留在 Player 页面，RIFE/字幕/起播秒数仍可直接调整。",
                actions = listOf(SettingsQuickAction("打开播放器", onOpenPlayer)),
                modifier = Modifier.weight(1f),
            )
            DesktopSettingsSection.Scan -> SettingsSummaryContent(
                section = selectedSection,
                tiles = scanSettingsTiles(
                    indexedItemCount = indexedItemCount,
                    linkedSourceLabel = linkedSourceLabel,
                    libraryStatus = libraryStatus,
                ),
                status = "扫描入口保留在 Library 海报墙和 CloudDrive 同步流程中。",
                actions = listOf(
                    SettingsQuickAction("扫描当前源", onScanActiveSource),
                    SettingsQuickAction("打开海报墙", onOpenLibrary),
                ),
                modifier = Modifier.weight(1f),
            )
            DesktopSettingsSection.Metadata -> SettingsSummaryContent(
                section = selectedSection,
                tiles = metadataSettingsTiles(
                    selectedMediaTitle = selectedMediaTitle,
                    metadataSummary = metadataSummary,
                    indexedItemCount = indexedItemCount,
                ),
                status = "Bangumi 搜索、批量预览、应用和撤销保留在 Details 页面。",
                actions = listOf(SettingsQuickAction("打开详情", onOpenDetails)),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CloudRssAutomationContent(
    endpointUrl: String,
    onEndpointUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    inboxPath: String,
    onInboxPathChange: (String) -> Unit,
    libraryPath: String,
    onLibraryPathChange: (String) -> Unit,
    intervalMinutes: String,
    onIntervalMinutesChange: (String) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    proxyEnabled: Boolean,
    onProxyEnabledChange: (Boolean) -> Unit,
    proxyHost: String,
    onProxyHostChange: (String) -> Unit,
    proxyPort: String,
    onProxyPortChange: (String) -> Unit,
    rssName: String,
    onRssNameChange: (String) -> Unit,
    rssUrl: String,
    onRssUrlChange: (String) -> Unit,
    rssFilter: String,
    onRssFilterChange: (String) -> Unit,
    rssEnabled: Boolean,
    onRssEnabledChange: (Boolean) -> Unit,
    subscriptions: List<RssSubscriptionInfo>,
    selectedSubscription: RssSubscriptionInfo?,
    status: String,
    schedulerStatus: String,
    linkedSourceLabel: String,
    onSaveConfig: () -> Unit,
    onSaveCredentials: () -> Unit,
    onLoginCloudDrive: () -> Unit,
    onVerifyApiToken: () -> Unit,
    onClearCredentials: () -> Unit,
    onRunSync: () -> Unit,
    onStartScheduler: () -> Unit,
    onStopScheduler: () -> Unit,
    onUseActiveScanSource: () -> Unit,
    onClearScanSource: () -> Unit,
    onSaveSubscription: () -> Unit,
    onSubscriptionSelected: (RssSubscriptionInfo) -> Unit,
    onDeleteSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvPanel(modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.46f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                Text("Cloud/RSS sync", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                LabeledTextField("CloudDrive2 endpoint", endpointUrl, onValueChange = onEndpointUrlChange)
                LabeledTextField("CloudDrive2 username", username, onValueChange = onUsernameChange)
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                    LabeledTextField("API token", token, onValueChange = onTokenChange, modifier = Modifier.weight(1f))
                    LabeledTextField("Password", password, onValueChange = onPasswordChange, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                    LabeledTextField("Inbox path", inboxPath, onValueChange = onInboxPathChange, modifier = Modifier.weight(1f))
                    LabeledTextField("Library path", libraryPath, onValueChange = onLibraryPathChange, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                    LabeledTextField("Interval minutes", intervalMinutes, onValueChange = onIntervalMinutesChange, modifier = Modifier.weight(1f))
                    LabeledTextField("Proxy host", proxyHost, onValueChange = onProxyHostChange, modifier = Modifier.weight(1f))
                    LabeledTextField("Proxy port", proxyPort, onValueChange = onProxyPortChange, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
                    ToggleRow("Enabled", enabled, onEnabledChange)
                    ToggleRow("RSS proxy", proxyEnabled, onProxyEnabledChange)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                    TvActionButton("Use active source", onClick = onUseActiveScanSource, secondary = true)
                    TvActionButton("Clear source", onClick = onClearScanSource, secondary = true)
                }
                Text("Post-sync source: $linkedSourceLabel", color = TextSecondary, fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp)
                Column(verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SMALL_GAP_DP.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton("Save sync config", onClick = onSaveConfig)
                        TvActionButton("Run sync now", onClick = onRunSync, secondary = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton("Save credentials", onClick = onSaveCredentials, secondary = true)
                        TvActionButton("Clear credentials", onClick = onClearCredentials, secondary = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton("Login", onClick = onLoginCloudDrive, secondary = true)
                        TvActionButton("Verify token", onClick = onVerifyApiToken, secondary = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton("Start scheduler", onClick = onStartScheduler, secondary = true)
                        TvActionButton("Stop scheduler", onClick = onStopScheduler, secondary = true)
                    }
                }
                StatusBox(status)
                Text(
                    schedulerStatus,
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                    lineHeight = 18.sp,
                )
            }
            Column(
                modifier = Modifier.weight(0.54f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                Text("RSS subscriptions", color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                LabeledTextField("Subscription name", rssName, onValueChange = onRssNameChange)
                LabeledTextField("Subscription URL", rssUrl, onValueChange = onRssUrlChange)
                LabeledTextField("Filter regex", rssFilter, onValueChange = onRssFilterChange)
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
                    ToggleRow("Enabled", rssEnabled, onRssEnabledChange)
                    TvActionButton("Save subscription", onClick = onSaveSubscription, secondary = true)
                    TvActionButton("Delete", onClick = onDeleteSubscription, secondary = true)
                }
                if (subscriptions.isEmpty()) {
                    DesktopEmptyState(
                        text = "Save a subscription to show it here.",
                        heightDp = MiruPlayUiMetrics.RSS_EMPTY_STATE_HEIGHT_DP,
                    )
                } else {
                    subscriptions.forEach { subscription ->
                        RssSubscriptionRow(
                            subscription = subscription,
                            selected = selectedSubscription?.id == subscription.id,
                            onClick = { onSubscriptionSelected(subscription) },
                        )
                    }
                }
            }
        }
    }
}

private data class SettingsQuickAction(
    val label: String,
    val onClick: () -> Unit,
)

internal data class SettingsSummaryTile(
    val label: String,
    val value: String,
    val detail: String,
)

@Composable
private fun SettingsSectionMenu(
    selectedSection: DesktopSettingsSection,
    sourcesCount: Int,
    rssCount: Int,
    cloudEnabled: Boolean,
    metadataSummary: String,
    playbackSummary: String,
    onSectionSelected: (DesktopSettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvPanel(modifier) {
        Text("设置菜单", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("像 TV 版一样按分类管理桌面能力。", color = TextSecondary, fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        DesktopSettingsSection.entries.forEach { section ->
            SettingsSectionMenuRow(
                section = section,
                summary = section.menuSummary(
                    sourcesCount = sourcesCount,
                    rssCount = rssCount,
                    cloudEnabled = cloudEnabled,
                    metadataSummary = metadataSummary,
                    playbackSummary = playbackSummary,
                ),
                selected = section == selectedSection,
                onClick = { onSectionSelected(section) },
            )
            Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        }
    }
}

@Composable
private fun SettingsSectionMenuRow(
    section: DesktopSettingsSection,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val active = selected || focused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(
                when {
                    selected -> AnimeRed.copy(alpha = 0.22f)
                    focused -> AccentBlue.copy(alpha = 0.34f)
                    else -> Color.Transparent
                },
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) AnimeRed else Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA),
                shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                section.title,
                color = TextPrimary,
                fontSize = MiruPlayUiMetrics.ACTION_BUTTON_TEXT_SP.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                summary,
                color = TextSecondary,
                fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsSummaryContent(
    section: DesktopSettingsSection,
    tiles: List<SettingsSummaryTile>,
    status: String,
    actions: List<SettingsQuickAction>,
    modifier: Modifier = Modifier,
) {
    TvPanel(modifier.fillMaxWidth()) {
        Text(section.title, color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(section.description, color = TextSecondary, fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Column(verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
            tiles.chunked(3).forEach { row ->
                SettingsSummaryTileRow(row)
            }
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        StatusBox(status)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
            actions.forEachIndexed { index, action ->
                TvActionButton(
                    action.label,
                    onClick = action.onClick,
                    secondary = index != 0,
                )
            }
        }
    }
}

@Composable
private fun SettingsSummaryTileRow(tiles: List<SettingsSummaryTile>) {
    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
        tiles.forEach { tile ->
            SettingsSummaryCard(tile, Modifier.weight(1f))
        }
        repeat(3 - tiles.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingsSummaryCard(
    tile: SettingsSummaryTile,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(CardBg.copy(alpha = 0.46f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA),
                shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
            )
            .padding(MiruPlayUiMetrics.STATUS_BOX_PADDING_DP.dp),
    ) {
        Text(tile.label, color = TextSecondary, fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            tile.value,
            color = TextPrimary,
            fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            tile.detail,
            color = TextSecondary,
            fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun sourceSettingsTiles(
    sources: List<MediaSourceInfo>,
    activeSourceLabel: String,
    indexedItemCount: Int,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = "媒体源",
            value = "${sources.size} 个",
            detail = sourceTypeBreakdown(sources),
        ),
        SettingsSummaryTile(
            label = "当前源",
            value = activeSourceLabel,
            detail = "Library、远程浏览器和 Cloud/RSS 共用这个活动源。",
        ),
        SettingsSummaryTile(
            label = "海报墙索引",
            value = "$indexedItemCount 条",
            detail = "扫描后优先回到 Library 海报墙。",
        ),
    )

internal fun playbackSettingsTiles(
    playbackSummary: String,
    recentCount: Int,
    selectedMediaTitle: String,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = "播放模式",
            value = playbackSummary,
            detail = "mpv、RIFE、字幕和起播时间在 Player 页面调整。",
        ),
        SettingsSummaryTile(
            label = "继续观看",
            value = "$recentCount 条",
            detail = "mpv 进度同步后会刷新这里。",
        ),
        SettingsSummaryTile(
            label = "当前媒体",
            value = selectedMediaTitle,
            detail = "从海报墙或详情页选择后可直接播放。",
        ),
    )

internal fun scanSettingsTiles(
    indexedItemCount: Int,
    linkedSourceLabel: String,
    libraryStatus: String,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = "索引",
            value = "$indexedItemCount 条",
            detail = "本地、WebDAV、SMB 都写入同一桌面索引。",
        ),
        SettingsSummaryTile(
            label = "同步后扫描源",
            value = linkedSourceLabel,
            detail = "CloudDrive 完成后可触发这个源的重扫。",
        ),
        SettingsSummaryTile(
            label = "最近扫描状态",
            value = libraryStatus,
            detail = "扫描入口也保留在 Library 顶部。",
        ),
    )

internal fun metadataSettingsTiles(
    selectedMediaTitle: String,
    metadataSummary: String,
    indexedItemCount: Int,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = "选中条目",
            value = selectedMediaTitle,
            detail = "详情页会显示可应用的 Bangumi 匹配。",
        ),
        SettingsSummaryTile(
            label = "匹配状态",
            value = metadataSummary,
            detail = "支持单条应用、批量预览、应用和撤销。",
        ),
        SettingsSummaryTile(
            label = "候选范围",
            value = "$indexedItemCount 条索引",
            detail = "批量匹配会跳过已有冲突元数据。",
        ),
    )

private fun sourceTypeBreakdown(sources: List<MediaSourceInfo>): String {
    if (sources.isEmpty()) return "尚未添加本地、WebDAV 或 SMB 源。"
    return MediaSourceType.entries
        .mapNotNull { type ->
            val count = sources.count { it.type == type }
            if (count == 0) null else "${type.settingsLabel()} $count"
        }
        .joinToString(" · ")
}

private fun MediaSourceType.settingsLabel(): String =
    when (this) {
        MediaSourceType.LOCAL -> "本地"
        MediaSourceType.WEBDAV -> "WebDAV"
        MediaSourceType.SMB -> "SMB"
    }

private fun DesktopSettingsSection.menuSummary(
    sourcesCount: Int,
    rssCount: Int,
    cloudEnabled: Boolean,
    metadataSummary: String,
    playbackSummary: String,
): String = when (this) {
    DesktopSettingsSection.Sources -> "$sourcesCount 个源"
    DesktopSettingsSection.Playback -> playbackSummary
    DesktopSettingsSection.CloudDrive -> if (cloudEnabled) "$rssCount 个订阅" else "未启用"
    DesktopSettingsSection.Scan -> "媒体库更新"
    DesktopSettingsSection.Metadata -> metadataSummary
}

@Composable
private fun RssSubscriptionRow(
    subscription: RssSubscriptionInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DesktopSelectableRow(selected = selected, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                subscription.name,
                color = TextPrimary,
                fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subscription.url,
                color = TextSecondary,
                fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
