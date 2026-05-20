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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.RssSubscriptionInfo

private const val CLOUD_RSS_PREVIEW_LIMIT = 58
private const val CLOUD_RSS_WIDE_PREVIEW_LIMIT = 86
private const val CLOUD_RSS_BADGE_WIDTH_DP = 82
private const val CLOUD_RSS_BADGE_HEIGHT_DP = 34

private enum class DesktopSettingsSection(
    val title: String,
    val description: String,
) {
    Sources("媒体源", "本地、WebDAV、SMB"),
    Playback("播放", "mpv 与 RIFE"),
    CloudDrive("云盘", "RSS 离线下载与入库"),
    Scan("扫描", "媒体库更新"),
    Metadata("元数据", "Bangumi 匹配"),
}

private fun DesktopSettingsSection.step(delta: Int): DesktopSettingsSection {
    val sections = DesktopSettingsSection.entries
    val nextIndex = (sections.indexOf(this) + delta + sections.size) % sections.size
    return sections[nextIndex]
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
                status = desktopLibraryStatusText(libraryStatus),
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
                status = "扫描入口保留在媒体库海报墙和 CloudDrive 同步流程中。",
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
    val labels = desktopCloudRssUiLabels()
    val schedulerStatusText = desktopCloudRssStatusText(schedulerStatus)
    val statusText = desktopCloudRssStatusText(status)
    val subscriptionFocusRequesters = remember(subscriptions) {
        subscriptions.associate { it.id to FocusRequester() }
    }
    fun selectSubscription(subscription: RssSubscriptionInfo) {
        onSubscriptionSelected(subscription)
        subscriptionFocusRequesters[subscription.id]?.requestFocus()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
            cloudRssOverviewTiles(
                endpointUrl = endpointUrl,
                subscriptions = subscriptions,
                enabled = enabled,
                linkedSourceLabel = linkedSourceLabel,
                schedulerStatus = schedulerStatus,
            ).forEach { tile ->
                SettingsSummaryCard(tile, Modifier.weight(1f))
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.46f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                CloudRssCard(
                    title = "CloudDrive2",
                    badge = if (enabled) labels.enabledBadge else labels.disabledBadge,
                    preview = cloudRssPreview(endpointUrl, fallback = labels.endpointFallback),
                ) {
                    LabeledTextField(labels.endpoint, endpointUrl, onValueChange = onEndpointUrlChange)
                    LabeledTextField(labels.username, username, onValueChange = onUsernameChange)
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                        LabeledTextField(labels.apiToken, token, onValueChange = onTokenChange, modifier = Modifier.weight(1f))
                        LabeledTextField(labels.password, password, onValueChange = onPasswordChange, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton(labels.saveCredentials, onClick = onSaveCredentials, secondary = true, modifier = Modifier.weight(1f))
                        TvActionButton(labels.clearCredentials, onClick = onClearCredentials, secondary = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton(labels.login, onClick = onLoginCloudDrive, secondary = true, modifier = Modifier.weight(1f))
                        TvActionButton(labels.verify, onClick = onVerifyApiToken, secondary = true, modifier = Modifier.weight(1f))
                    }
                }
                CloudRssCard(
                    title = "同步路径",
                    badge = labels.pathBadge,
                    preview = cloudRssPathPairPreview(inboxPath, libraryPath),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                        LabeledTextField(labels.inboxPath, inboxPath, onValueChange = onInboxPathChange, modifier = Modifier.weight(1f))
                        LabeledTextField(labels.libraryPath, libraryPath, onValueChange = onLibraryPathChange, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                        LabeledTextField(labels.intervalMinutes, intervalMinutes, onValueChange = onIntervalMinutesChange, modifier = Modifier.weight(1f))
                        LabeledTextField(labels.proxyHost, proxyHost, onValueChange = onProxyHostChange, modifier = Modifier.weight(1f))
                        LabeledTextField(labels.proxyPort, proxyPort, onValueChange = onProxyPortChange, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
                        ToggleRow(labels.enabledToggle, enabled, onEnabledChange)
                        ToggleRow(labels.rssProxy, proxyEnabled, onProxyEnabledChange)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton(labels.useActiveSource, onClick = onUseActiveScanSource, secondary = true)
                        TvActionButton(labels.clearSource, onClick = onClearScanSource, secondary = true)
                    }
                    Text("${labels.postSyncSource}$linkedSourceLabel", color = TextSecondary, fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton(labels.saveSyncConfig, onClick = onSaveConfig)
                        TvActionButton(labels.runSyncNow, onClick = onRunSync, secondary = true)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(0.54f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                CloudRssCard(
                    title = labels.rssSubscriptions,
                    badge = "${subscriptions.size}",
                    preview = selectedSubscription?.let { rssSubscriptionPreview(it) } ?: labels.rssPreviewFallback,
                ) {
                    LabeledTextField(labels.subscriptionName, rssName, onValueChange = onRssNameChange)
                    LabeledTextField(labels.subscriptionUrl, rssUrl, onValueChange = onRssUrlChange)
                    LabeledTextField(labels.filterRegex, rssFilter, onValueChange = onRssFilterChange)
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
                        ToggleRow(labels.enabledToggle, rssEnabled, onRssEnabledChange)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton(labels.saveRss, onClick = onSaveSubscription, secondary = true, modifier = Modifier.weight(1f))
                        TvActionButton(labels.deleteRss, onClick = onDeleteSubscription, secondary = true, modifier = Modifier.weight(1f))
                    }
                    if (subscriptions.isEmpty()) {
                        DesktopEmptyState(
                            text = labels.rssEmpty,
                            heightDp = MiruPlayUiMetrics.RSS_EMPTY_STATE_HEIGHT_DP,
                        )
                    } else {
                        subscriptions.forEach { subscription ->
                            RssSubscriptionRow(
                                subscription = subscription,
                                selected = selectedSubscription?.id == subscription.id,
                                onClick = { selectSubscription(subscription) },
                                onNavigate = { key ->
                                    subscriptions.rssSubscriptionNavigationTarget(
                                        currentSubscriptionId = subscription.id,
                                        key = key,
                                    )?.let { target ->
                                        selectSubscription(target)
                                        true
                                    } ?: false
                                },
                                modifier = Modifier.focusRequester(subscriptionFocusRequesters.getValue(subscription.id)),
                            )
                            Spacer(Modifier.height(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp))
                        }
                    }
                }
                CloudRssCard(
                    title = "运行状态",
                    badge = labels.runBadge,
                    preview = cloudRssPreview(schedulerStatusText, fallback = labels.schedulerIdle),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton(labels.startScheduler, onClick = onStartScheduler, secondary = true)
                        TvActionButton(labels.stopScheduler, onClick = onStopScheduler, secondary = true)
                    }
                    StatusBox(statusText)
                    Text(
                        schedulerStatusText,
                        color = TextSecondary,
                        fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudRssCard(
    title: String,
    badge: String,
    preview: String,
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
                    preview,
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .width(CLOUD_RSS_BADGE_WIDTH_DP.dp)
                    .height(CLOUD_RSS_BADGE_HEIGHT_DP.dp)
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

private data class SettingsQuickAction(
    val label: String,
    val onClick: () -> Unit,
)

internal data class SettingsSummaryTile(
    val label: String,
    val value: String,
    val detail: String,
)

internal data class DesktopCloudRssUiLabels(
    val endpoint: String,
    val username: String,
    val apiToken: String,
    val password: String,
    val saveCredentials: String,
    val clearCredentials: String,
    val login: String,
    val verify: String,
    val inboxPath: String,
    val libraryPath: String,
    val intervalMinutes: String,
    val proxyHost: String,
    val proxyPort: String,
    val enabledToggle: String,
    val rssProxy: String,
    val useActiveSource: String,
    val clearSource: String,
    val postSyncSource: String,
    val saveSyncConfig: String,
    val runSyncNow: String,
    val rssSubscriptions: String,
    val subscriptionName: String,
    val subscriptionUrl: String,
    val filterRegex: String,
    val saveRss: String,
    val deleteRss: String,
    val rssEmpty: String,
    val rssPreviewFallback: String,
    val startScheduler: String,
    val stopScheduler: String,
    val endpointFallback: String,
    val schedulerIdle: String,
    val enabledBadge: String,
    val disabledBadge: String,
    val pathBadge: String,
    val runBadge: String,
)

internal fun desktopCloudRssUiLabels(): DesktopCloudRssUiLabels =
    DesktopCloudRssUiLabels(
        endpoint = "CloudDrive2 地址",
        username = "CloudDrive2 用户名",
        apiToken = "API 令牌",
        password = "密码",
        saveCredentials = "保存凭据",
        clearCredentials = "清空凭据",
        login = "登录",
        verify = "验证令牌",
        inboxPath = "收件路径",
        libraryPath = "媒体库路径",
        intervalMinutes = "间隔分钟",
        proxyHost = "代理主机",
        proxyPort = "代理端口",
        enabledToggle = "启用",
        rssProxy = "RSS 代理",
        useActiveSource = "使用当前源",
        clearSource = "清除扫描源",
        postSyncSource = "同步后扫描源：",
        saveSyncConfig = "保存同步配置",
        runSyncNow = "立即同步",
        rssSubscriptions = "RSS 订阅",
        subscriptionName = "订阅名称",
        subscriptionUrl = "订阅地址",
        filterRegex = "过滤正则",
        saveRss = "保存 RSS",
        deleteRss = "删除订阅",
        rssEmpty = "保存订阅后会显示在这里。",
        rssPreviewFallback = "保存订阅后在这里显示",
        startScheduler = "启动调度",
        stopScheduler = "停止调度",
        endpointFallback = "填写 CloudDrive2 地址",
        schedulerIdle = "调度器待命",
        enabledBadge = "启用",
        disabledBadge = "停用",
        pathBadge = "路径",
        runBadge = "运行",
    )

internal fun cloudRssOverviewTiles(
    endpointUrl: String,
    subscriptions: List<RssSubscriptionInfo>,
    enabled: Boolean,
    linkedSourceLabel: String,
    schedulerStatus: String,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = "CloudDrive2",
            value = if (enabled) "已启用" else "未启用",
            detail = cloudRssPreview(endpointUrl, fallback = "未配置端点", maxLength = CLOUD_RSS_PREVIEW_LIMIT),
        ),
        SettingsSummaryTile(
            label = "RSS 订阅",
            value = "${subscriptions.size} 个",
            detail = subscriptions.firstOrNull()?.let { rssSubscriptionPreview(it, CLOUD_RSS_PREVIEW_LIMIT) } ?: "暂无订阅",
        ),
        SettingsSummaryTile(
            label = "同步后扫描",
            value = linkedSourceLabel,
            detail = cloudRssPreview(
                desktopCloudRssStatusText(schedulerStatus),
                fallback = "调度器待命",
                maxLength = CLOUD_RSS_PREVIEW_LIMIT,
            ),
        ),
    )

internal fun cloudRssPreview(
    value: String,
    fallback: String,
    maxLength: Int = CLOUD_RSS_WIDE_PREVIEW_LIMIT,
): String =
    value.trim()
        .ifBlank { fallback }
        .compactMiddle(maxLength)

internal fun cloudRssPathPairPreview(
    inboxPath: String,
    libraryPath: String,
    maxLength: Int = CLOUD_RSS_WIDE_PREVIEW_LIMIT,
): String {
    val separator = " -> "
    val safeMaxLength = maxLength.coerceAtLeast(separator.length + 8)
    val available = safeMaxLength - separator.length
    val inboxLength = available / 2
    val libraryLength = available - inboxLength
    return cloudRssPreview(inboxPath, fallback = "收件路径", maxLength = inboxLength) +
        separator +
        cloudRssPreview(libraryPath, fallback = "媒体库路径", maxLength = libraryLength)
}

internal fun rssSubscriptionPreview(
    subscription: RssSubscriptionInfo,
    maxLength: Int = CLOUD_RSS_WIDE_PREVIEW_LIMIT,
): String {
    val state = if (subscription.enabled) "启用" else "停用"
    val filter = subscription.filterRegex?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    val label = subscription.name.ifBlank { "RSS" }
    return "$state · $label · ${subscription.url}$filter".compactMiddle(maxLength)
}

private val schedulerErrorStatusRegex = Regex("""^Scheduler (running|idle)\. Last check failed: (.+)$""")
private val schedulerSummaryStatusRegex =
    Regex("""^Scheduler (running|idle)\. Last run: (\d+) submitted, (\d+) skipped, (\d+) failed, (\d+) organized\.$""")
private val syncCompleteStatusRegex =
    Regex("""^Sync complete: (\d+) submitted, (\d+) skipped, (\d+) failed, (\d+) organized\.$""")
private val loadedRssStatusRegex = Regex("""^Loaded (\d+) RSS subscription\(s\)\.$""")
private val showingRssStatusRegex = Regex("""^Showing (\d+) RSS subscription\(s\)\.$""")
private val verifiedTokenStatusRegex = Regex("""^CloudDrive2 API token verified and saved: (.+)\.$""")
private val linkedScanSourceStatusRegex =
    Regex("""^Linked Cloud/RSS post-sync scan source: (.+)\. Save sync config to persist it\.$""")
private val rescanStartedStatusRegex = Regex("""^(.+) Rescanning (.+)\.\.\.$""")
private val rssSubscriptionSavedRegex = Regex("""^RSS subscription saved: (.+)$""")
private val rssSubscriptionSelectedRegex = Regex("""^Selected RSS subscription: (.+)$""")

internal fun desktopCloudRssStatusText(status: String): String {
    val trimmed = status.trim()
    return when {
        trimmed.isBlank() -> "Cloud/RSS 待命。"
        trimmed == "Scheduler idle. No checks yet." -> "调度器待命，尚未检查。"
        trimmed == "Scheduler idle. Last check found no due sync." -> "调度器待命，上次检查没有待同步内容。"
        trimmed == "Scheduler running. No checks yet." -> "调度器运行中，尚未检查。"
        trimmed == "Scheduler running. Last check found no due sync." -> "调度器运行中，上次检查没有待同步内容。"
        trimmed == "Cloud/RSS automation settings saved." -> "Cloud/RSS 自动化设置已保存。"
        trimmed == "Load or save Cloud/RSS automation settings." -> "加载或保存 Cloud/RSS 自动化设置。"
        trimmed == "CloudDrive credentials saved." -> "CloudDrive 凭据已保存。"
        trimmed == "CloudDrive credentials cleared." -> "CloudDrive 凭据已清空。"
        trimmed == "Enter CloudDrive2 endpoint, username, and password first." -> "请先填写 CloudDrive2 地址、用户名和密码。"
        trimmed == "Logging into CloudDrive2..." -> "正在登录 CloudDrive2..."
        trimmed == "CloudDrive2 login succeeded; token saved." -> "CloudDrive2 登录成功，令牌已保存。"
        trimmed == "Enter CloudDrive2 endpoint and API token first." -> "请先填写 CloudDrive2 地址和 API 令牌。"
        trimmed == "Validating CloudDrive2 API token..." -> "正在验证 CloudDrive2 API 令牌..."
        trimmed == "Running Cloud/RSS sync..." -> "正在执行 Cloud/RSS 同步..."
        trimmed == "Enable and save Cloud/RSS sync before starting the scheduler." -> "启动调度前请先启用并保存 Cloud/RSS 同步。"
        trimmed == "Cloud/RSS scheduler started." -> "Cloud/RSS 调度器已启动。"
        trimmed == "Cloud/RSS scheduler is already running." -> "Cloud/RSS 调度器已经在运行。"
        trimmed == "Cloud/RSS scheduler stopped." -> "Cloud/RSS 调度器已停止。"
        trimmed == "Scheduled sync complete." -> "定时同步完成。"
        trimmed == "Open a saved media source before linking Cloud/RSS scanning." -> "请先打开已保存的媒体源，再绑定 Cloud/RSS 扫描。"
        trimmed == "Linked scan source was not found. Clear or relink the Cloud/RSS scan source." -> "未找到已绑定的扫描源，请清除或重新绑定 Cloud/RSS 扫描源。"
        trimmed == "Cloud/RSS post-sync scan source cleared. Save sync config to persist it." -> "同步后扫描源已清除，请保存同步配置。"
        trimmed == "Enter an RSS URL first." -> "请先填写 RSS 地址。"
        trimmed == "No RSS subscriptions configured." -> "尚未配置 RSS 订阅。"
        trimmed == "Failed to load RSS subscriptions." -> "RSS 订阅加载失败。"
        trimmed == "Failed to refresh RSS subscriptions." -> "RSS 订阅刷新失败。"
        trimmed == "Select an RSS subscription first." -> "请先选择一个 RSS 订阅。"
        trimmed == "RSS subscription deleted." -> "RSS 订阅已删除。"
        else -> desktopCloudRssDynamicStatusText(trimmed) ?: trimmed
    }
}

private fun desktopCloudRssDynamicStatusText(status: String): String? {
    schedulerErrorStatusRegex.matchEntire(status)?.let { match ->
        return "${schedulerStateLabel(match.groupValues[1])}，上次检查失败：${match.groupValues[2]}"
    }
    schedulerSummaryStatusRegex.matchEntire(status)?.let { match ->
        return "${schedulerStateLabel(match.groupValues[1])}，上次运行：提交 ${match.groupValues[2]} 个，跳过 ${match.groupValues[3]} 个，失败 ${match.groupValues[4]} 个，整理 ${match.groupValues[5]} 个。"
    }
    syncCompleteStatusRegex.matchEntire(status)?.let { match ->
        return "同步完成：提交 ${match.groupValues[1]} 个，跳过 ${match.groupValues[2]} 个，失败 ${match.groupValues[3]} 个，整理 ${match.groupValues[4]} 个。"
    }
    loadedRssStatusRegex.matchEntire(status)?.let { match ->
        return "已加载 ${match.groupValues[1]} 个 RSS 订阅。"
    }
    showingRssStatusRegex.matchEntire(status)?.let { match ->
        return "正在显示 ${match.groupValues[1]} 个 RSS 订阅。"
    }
    verifiedTokenStatusRegex.matchEntire(status)?.let { match ->
        return "CloudDrive2 API 令牌已验证并保存：${match.groupValues[1]}。"
    }
    linkedScanSourceStatusRegex.matchEntire(status)?.let { match ->
        return "已绑定同步后扫描源：${match.groupValues[1]}。请保存同步配置。"
    }
    rescanStartedStatusRegex.matchEntire(status)?.let { match ->
        val reason = desktopCloudRssStatusText(match.groupValues[1])
        return "${reason.removeSuffix("。")}，正在重扫 ${match.groupValues[2]}..."
    }
    rssSubscriptionSavedRegex.matchEntire(status)?.let { match ->
        return "RSS 订阅已保存：${match.groupValues[1]}"
    }
    rssSubscriptionSelectedRegex.matchEntire(status)?.let { match ->
        return "已选择 RSS 订阅：${match.groupValues[1]}"
    }
    return null
}

private fun schedulerStateLabel(state: String): String =
    if (state == "running") "调度器运行中" else "调度器待命"

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
    val sectionFocusRequesters = remember {
        DesktopSettingsSection.entries.associateWith { FocusRequester() }
    }
    LaunchedEffect(selectedSection) {
        sectionFocusRequesters[selectedSection]?.requestFocus()
    }

    TvPanel(
        modifier
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionDown -> {
                            onSectionSelected(selectedSection.step(1))
                            true
                        }
                        Key.DirectionUp -> {
                            onSectionSelected(selectedSection.step(-1))
                            true
                        }
                        else -> false
                    }
                }
            }
            .focusable(),
    ) {
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
                modifier = Modifier.focusRequester(sectionFocusRequesters.getValue(section)),
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
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val active = selected || focused
    Row(
        modifier = modifier
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
            detail = "媒体库、远程浏览器和 Cloud/RSS 共用这个活动源。",
        ),
        SettingsSummaryTile(
            label = "海报墙索引",
            value = "$indexedItemCount 条",
            detail = "扫描后优先回到媒体库海报墙。",
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
            value = desktopLibraryStatusText(libraryStatus),
            detail = "扫描入口也保留在媒体库顶部。",
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

internal fun desktopActiveSourceLabel(source: MediaSourceInfo?): String =
    source?.sourcePickerTitle() ?: "未选择"

internal fun desktopLinkedSourceLabel(
    sources: List<MediaSourceInfo>,
    sourceId: Long?,
): String {
    if (sourceId == null) return "未选择"
    return sources.firstOrNull { it.id == sourceId }?.sourcePickerTitle()
        ?: "缺失媒体源 #$sourceId"
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
    onNavigate: (Key) -> Boolean,
    modifier: Modifier = Modifier,
) {
    DesktopSelectableRow(
        selected = selected,
        onClick = onClick,
        modifier = modifier.onPreviewKeyEvent { event ->
            event.type == KeyEventType.KeyDown &&
                (event.key == Key.DirectionUp || event.key == Key.DirectionDown) &&
                onNavigate(event.key)
        },
    ) {
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

internal fun List<RssSubscriptionInfo>.rssSubscriptionNavigationTarget(
    currentSubscriptionId: Long?,
    key: Key,
): RssSubscriptionInfo? {
    if (isEmpty()) return null
    val currentIndex = currentSubscriptionId
        ?.let { id -> indexOfFirst { subscription -> subscription.id == id } }
        ?.takeIf { it >= 0 }
        ?: when (key) {
            Key.DirectionDown -> -1
            Key.DirectionUp -> size
            else -> return null
        }
    val targetIndex = when (key) {
        Key.DirectionDown -> currentIndex + 1
        Key.DirectionUp -> currentIndex - 1
        else -> return null
    }
    return getOrNull(targetIndex)
}
