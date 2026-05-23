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
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.design.horizontalNavigationDelta
import com.miruplay.tv.design.verticalNavigationDelta
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MiruPlaySettingsSection
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.cloudDriveRssApiTokenFieldLabel
import com.miruplay.tv.model.cloudDriveRssChooseDirectoryActionLabel
import com.miruplay.tv.model.cloudDriveRssClearCredentialsActionLabel
import com.miruplay.tv.model.cloudDriveRssClearScanSourceActionLabel
import com.miruplay.tv.model.cloudDriveRssCloseActionLabel
import com.miruplay.tv.model.cloudDriveRssDirectoryBadgeLabel
import com.miruplay.tv.model.cloudDriveRssDirectoryPageUnitLabel
import com.miruplay.tv.model.cloudDriveRssEmptyDirectoryMessage
import com.miruplay.tv.model.cloudDriveRssEnabledBadgeLabel
import com.miruplay.tv.model.cloudDriveRssEnabledToggleLabel
import com.miruplay.tv.model.cloudDriveRssEndpointFallbackLabel
import com.miruplay.tv.model.cloudDriveRssEndpointFieldLabel
import com.miruplay.tv.model.cloudDriveRssInboxPathFieldLabel
import com.miruplay.tv.model.cloudDriveRssIntervalMinutesFieldLabel
import com.miruplay.tv.model.cloudDriveRssLibraryPathFieldLabel
import com.miruplay.tv.model.cloudDriveRssLoadingDirectoriesMessage
import com.miruplay.tv.model.cloudDriveRssLoginActionLabel
import com.miruplay.tv.model.cloudDriveRssPasswordFieldLabel
import com.miruplay.tv.model.cloudDriveRssParentDirectoryActionLabel
import com.miruplay.tv.model.cloudDriveRssPathBadgeLabel
import com.miruplay.tv.model.cloudDriveRssPathPairSeparator
import com.miruplay.tv.model.cloudDriveRssPostSyncScanSummaryLabel
import com.miruplay.tv.model.cloudDriveRssPostSyncSourceLabel
import com.miruplay.tv.model.cloudDriveRssProxyHostFieldLabel
import com.miruplay.tv.model.cloudDriveRssProxyPortFieldLabel
import com.miruplay.tv.model.cloudDriveRssProxySettingLabel
import com.miruplay.tv.model.cloudDriveRssRunBadgeLabel
import com.miruplay.tv.model.cloudDriveRssRunNowActionLabel
import com.miruplay.tv.model.cloudDriveRssRuntimeTitleLabel
import com.miruplay.tv.model.cloudDriveRssSaveApiTokenActionLabel
import com.miruplay.tv.model.cloudDriveRssSaveConfigActionLabel
import com.miruplay.tv.model.cloudDriveRssSaveCredentialsActionLabel
import com.miruplay.tv.model.cloudDriveRssSchedulerIdleLabel
import com.miruplay.tv.model.cloudDriveRssStartSchedulerActionLabel
import com.miruplay.tv.model.cloudDriveRssStopSchedulerActionLabel
import com.miruplay.tv.model.cloudDriveRssSyncPathTitleLabel
import com.miruplay.tv.model.cloudDriveRssTitleLabel
import com.miruplay.tv.model.cloudDriveRssUnconfiguredEndpointLabel
import com.miruplay.tv.model.cloudDriveRssUseActiveSourceActionLabel
import com.miruplay.tv.model.cloudDriveRssUseCurrentDirectoryActionLabel
import com.miruplay.tv.model.cloudDriveRssUsernameFieldLabel
import com.miruplay.tv.model.cloudDriveRssVerifyApiTokenActionLabel
import com.miruplay.tv.model.desktopSettingsSectionOrder
import com.miruplay.tv.model.localizedCloudRssStatusText
import com.miruplay.tv.model.metadataBangumiTokenFieldLabel
import com.miruplay.tv.model.metadataBangumiTokenSettingsStatus
import com.miruplay.tv.model.metadataBangumiTokenTileDetail
import com.miruplay.tv.model.metadataBangumiTokenTileLabel
import com.miruplay.tv.model.pagedListCoercedPageStart
import com.miruplay.tv.model.pagedListPageStartForIndex
import com.miruplay.tv.model.pagedListPageSummary
import com.miruplay.tv.model.settingsActiveSourceSharedDetail
import com.miruplay.tv.model.settingsCloudDriveMenuSummary
import com.miruplay.tv.model.settingsCloudDriveRescanSourceDetail
import com.miruplay.tv.model.settingsClearTokenActionLabel
import com.miruplay.tv.model.settingsCountValue
import com.miruplay.tv.model.settingsDesktopControlTileDetail
import com.miruplay.tv.model.settingsDesktopControlTileValue
import com.miruplay.tv.model.settingsDesktopScanMenuSummary
import com.miruplay.tv.model.settingsDesktopScanStatusMessage
import com.miruplay.tv.model.settingsDesktopWebUiMenuSummary
import com.miruplay.tv.model.settingsDesktopWebUiStatusMessage
import com.miruplay.tv.model.settingsActiveSourceLabel
import com.miruplay.tv.model.settingsActiveSourceTileLabel
import com.miruplay.tv.model.settingsCloudRssLinkedSourceValue
import com.miruplay.tv.model.settingsCloudRssOverviewValue
import com.miruplay.tv.model.settingsCloudRssSubscriptionsValue
import com.miruplay.tv.model.settingsIndexedCountValue
import com.miruplay.tv.model.settingsIndexSharedDetail
import com.miruplay.tv.model.settingsIndexTileLabel
import com.miruplay.tv.model.settingsLinkedSourceLabel
import com.miruplay.tv.model.settingsMenuPanelDescription
import com.miruplay.tv.model.settingsMenuPanelTitle
import com.miruplay.tv.model.settingsMetadataCandidateScopeDetail
import com.miruplay.tv.model.settingsMetadataCandidateScopeTileLabel
import com.miruplay.tv.model.settingsMetadataMatchStatusDetail
import com.miruplay.tv.model.settingsMetadataMatchStatusTileLabel
import com.miruplay.tv.model.settingsOpenDetailsActionLabel
import com.miruplay.tv.model.settingsOpenLibraryActionLabel
import com.miruplay.tv.model.settingsOpenPlayerActionLabel
import com.miruplay.tv.model.settingsPlaybackPageDetail
import com.miruplay.tv.model.settingsPlaybackModeTileLabel
import com.miruplay.tv.model.settingsPlaybackStatusMessage
import com.miruplay.tv.model.settingsPosterWallIndexDetail
import com.miruplay.tv.model.settingsPosterWallIndexTileLabel
import com.miruplay.tv.model.settingsPostSyncSourceTileLabel
import com.miruplay.tv.model.settingsRecentPlaybackDetail
import com.miruplay.tv.model.settingsRecentPlaybackTileLabel
import com.miruplay.tv.model.settingsRecentScanStatusDetail
import com.miruplay.tv.model.settingsRecentScanStatusTileLabel
import com.miruplay.tv.model.settingsRecordCountValue
import com.miruplay.tv.model.settingsRemoteAutomationTileDetail
import com.miruplay.tv.model.settingsRemoteAutomationTileLabel
import com.miruplay.tv.model.settingsRemoteAutomationTileValue
import com.miruplay.tv.model.settingsSaveTokenActionLabel
import com.miruplay.tv.model.settingsSavedStateValue
import com.miruplay.tv.model.settingsScanActiveSourceActionLabel
import com.miruplay.tv.model.settingsSelectedMediaDetail
import com.miruplay.tv.model.settingsSelectedMediaTileLabel
import com.miruplay.tv.model.settingsSelectedMetadataEntryDetail
import com.miruplay.tv.model.settingsSelectedMetadataEntryTileLabel
import com.miruplay.tv.model.settingsSourceTileLabel
import com.miruplay.tv.model.settingsSourceTypeBreakdown
import com.miruplay.tv.model.settingsSourcesMenuSummary
import com.miruplay.tv.model.settingsWebUiNativeControlTileLabel
import com.miruplay.tv.model.settingsWebUiAndroidTvValue
import com.miruplay.tv.model.settingsWebUiTileLabel
import com.miruplay.tv.model.settingsWebUiTileDetail
import com.miruplay.tv.model.stepDesktopSettingsSection
import com.miruplay.tv.model.rssSubscriptionDeleteActionLabel
import com.miruplay.tv.model.rssSubscriptionEmptyMessage
import com.miruplay.tv.model.rssSubscriptionFallbackTitleLabel
import com.miruplay.tv.model.rssSubscriptionFilterRegexFieldLabel
import com.miruplay.tv.model.rssSubscriptionFormPreviewFallbackLabel
import com.miruplay.tv.model.rssSubscriptionNameFieldLabel
import com.miruplay.tv.model.rssSubscriptionPageUnitLabel
import com.miruplay.tv.model.rssSubscriptionPreviewFallbackLabel
import com.miruplay.tv.model.rssSubscriptionSaveActionLabel
import com.miruplay.tv.model.rssSubscriptionStateLabel
import com.miruplay.tv.model.rssSubscriptionUrlFieldLabel
import com.miruplay.tv.model.rssSubscriptionsTitleLabel
import com.miruplay.tv.sync.rss.CloudDriveDirectoryBrowserState
import com.miruplay.tv.sync.rss.CloudDriveDirectoryEntry
import com.miruplay.tv.sync.rss.CloudDriveDirectoryTarget

private const val CLOUD_RSS_PREVIEW_LIMIT = 58
private const val CLOUD_RSS_WIDE_PREVIEW_LIMIT = 86
private const val CLOUD_RSS_BADGE_WIDTH_DP = 82
private const val CLOUD_RSS_BADGE_HEIGHT_DP = 34
private const val CLOUD_DRIVE_DIRECTORY_PAGE_SIZE = 6
private const val CLOUD_RSS_SUBSCRIPTION_PAGE_SIZE = 6

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
    directoryBrowser: CloudDriveDirectoryBrowserState,
    onPickCloudDriveDirectory: (CloudDriveDirectoryTarget) -> Unit,
    onBrowseCloudDriveDirectory: (String) -> Unit,
    onSelectCloudDriveDirectory: (CloudDriveDirectoryTarget, String) -> Unit,
    onCloseCloudDriveDirectory: () -> Unit,
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
    bangumiToken: String,
    onBangumiTokenChange: (String) -> Unit,
    bangumiTokenConfigured: Boolean,
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
    onSaveBangumiToken: () -> Unit,
    onClearBangumiToken: () -> Unit,
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
    var selectedSection by remember { mutableStateOf(MiruPlaySettingsSection.SOURCES) }
    val sectionFocusRequesters = remember {
        desktopSettingsSectionOrder.associateWith { FocusRequester() }
    }
    fun focusSelectedSectionMenu() {
        sectionFocusRequesters[selectedSection]?.requestFocus()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SettingsSectionMenu(
            selectedSection = selectedSection,
            sectionFocusRequesters = sectionFocusRequesters,
            sourcesCount = sources.size,
            rssCount = subscriptions.size,
            cloudEnabled = enabled,
            metadataSummary = metadataSummary,
            playbackSummary = playbackSummary,
            onSectionSelected = { selectedSection = it },
            modifier = Modifier.width(292.dp),
        )
        when (selectedSection) {
            MiruPlaySettingsSection.CLOUD_DRIVE -> CloudRssAutomationContent(
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
                directoryBrowser = directoryBrowser,
                onPickCloudDriveDirectory = onPickCloudDriveDirectory,
                onBrowseCloudDriveDirectory = onBrowseCloudDriveDirectory,
                onSelectCloudDriveDirectory = onSelectCloudDriveDirectory,
                onCloseCloudDriveDirectory = onCloseCloudDriveDirectory,
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
            MiruPlaySettingsSection.SOURCES -> SettingsSummaryContent(
                section = selectedSection,
                tiles = sourceSettingsTiles(
                    sources = sources,
                    activeSourceLabel = activeSourceLabel,
                    indexedItemCount = indexedItemCount,
                ),
                status = desktopLibraryStatusText(libraryStatus),
                actions = listOf(
                    SettingsQuickAction(settingsOpenLibraryActionLabel(), onOpenLibrary),
                    SettingsQuickAction(settingsScanActiveSourceActionLabel(), onScanActiveSource),
                ),
                onFocusSectionMenu = { focusSelectedSectionMenu() },
                modifier = Modifier.weight(1f),
            )
            MiruPlaySettingsSection.PLAYBACK -> SettingsSummaryContent(
                section = selectedSection,
                tiles = playbackSettingsTiles(
                    playbackSummary = playbackSummary,
                    recentCount = recentCount,
                    selectedMediaTitle = selectedMediaTitle,
                ),
                status = desktopPlaybackSettingsStatus(),
                actions = listOf(SettingsQuickAction(settingsOpenPlayerActionLabel(), onOpenPlayer)),
                onFocusSectionMenu = { focusSelectedSectionMenu() },
                modifier = Modifier.weight(1f),
            )
            MiruPlaySettingsSection.SCAN -> SettingsSummaryContent(
                section = selectedSection,
                tiles = scanSettingsTiles(
                    indexedItemCount = indexedItemCount,
                    linkedSourceLabel = linkedSourceLabel,
                    libraryStatus = libraryStatus,
                ),
                status = settingsDesktopScanStatusMessage(),
                actions = listOf(
                    SettingsQuickAction(settingsScanActiveSourceActionLabel(), onScanActiveSource),
                    SettingsQuickAction(settingsOpenLibraryActionLabel(), onOpenLibrary),
                ),
                onFocusSectionMenu = { focusSelectedSectionMenu() },
                modifier = Modifier.weight(1f),
            )
            MiruPlaySettingsSection.METADATA -> SettingsSummaryContent(
                section = selectedSection,
                tiles = metadataSettingsTiles(
                    selectedMediaTitle = selectedMediaTitle,
                    metadataSummary = metadataSummary,
                    indexedItemCount = indexedItemCount,
                    bangumiTokenConfigured = bangumiTokenConfigured,
                ),
                status = desktopMetadataSettingsStatus(bangumiTokenConfigured),
                actions = listOf(
                    SettingsQuickAction(settingsOpenDetailsActionLabel(), onOpenDetails),
                    SettingsQuickAction(settingsSaveTokenActionLabel(), onSaveBangumiToken, enabled = bangumiToken.isNotBlank()),
                    SettingsQuickAction(settingsClearTokenActionLabel(), onClearBangumiToken, enabled = bangumiTokenConfigured),
                ),
                onFocusSectionMenu = { focusSelectedSectionMenu() },
                extraContent = {
                    LabeledTextField(
                        metadataBangumiTokenFieldLabel(),
                        bangumiToken,
                        onValueChange = onBangumiTokenChange,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            MiruPlaySettingsSection.WEB_UI -> SettingsSummaryContent(
                section = selectedSection,
                tiles = desktopWebUiSettingsTiles(),
                status = settingsDesktopWebUiStatusMessage(),
                actions = listOf(SettingsQuickAction(settingsOpenLibraryActionLabel(), onOpenLibrary)),
                onFocusSectionMenu = { focusSelectedSectionMenu() },
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
    directoryBrowser: CloudDriveDirectoryBrowserState,
    onPickCloudDriveDirectory: (CloudDriveDirectoryTarget) -> Unit,
    onBrowseCloudDriveDirectory: (String) -> Unit,
    onSelectCloudDriveDirectory: (CloudDriveDirectoryTarget, String) -> Unit,
    onCloseCloudDriveDirectory: () -> Unit,
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
    val subscriptionEmptyFocusRequester = remember { FocusRequester() }
    val actionFocusRequesters = remember {
        CloudRssAction.entries.associateWith { FocusRequester() }
    }
    val toggleFocusRequesters = remember {
        CloudRssToggle.entries.associateWith { FocusRequester() }
    }
    val fieldFocusRequesters = remember {
        CloudRssField.entries.associateWith { FocusRequester() }
    }
    var subscriptionPageStartState by remember(subscriptions.map { it.id }) { mutableStateOf(0) }
    val subscriptionPageStart = cloudRssSubscriptionCoercedPageStart(
        pageStart = subscriptionPageStartState,
        itemCount = subscriptions.size,
    )
    val visibleSubscriptions = remember(subscriptions, subscriptionPageStart) {
        subscriptions
            .drop(subscriptionPageStart)
            .take(CLOUD_RSS_SUBSCRIPTION_PAGE_SIZE)
    }
    val subscriptionFocusRequesters = remember(subscriptionPageStart, visibleSubscriptions.map { it.id }) {
        visibleSubscriptions.associate { it.id to FocusRequester() }
    }
    var pendingSubscriptionFocus by remember { mutableStateOf<Int?>(null) }

    fun requestCloudRssFocus(target: CloudRssFocusTarget?): Boolean {
        return when (target) {
            is CloudRssFocusTarget.Action -> {
                actionFocusRequesters.getValue(target.action).requestFocus()
                true
            }
            is CloudRssFocusTarget.Toggle -> {
                toggleFocusRequesters.getValue(target.toggle).requestFocus()
                true
            }
            is CloudRssFocusTarget.Field -> {
                fieldFocusRequesters.getValue(target.field).requestFocus()
                true
            }
            is CloudRssFocusTarget.Subscription -> {
                val index = target.index.takeIf { it in subscriptions.indices } ?: return false
                val subscription = subscriptions[index]
                onSubscriptionSelected(subscription)
                val targetPageStart = cloudRssSubscriptionPageStartForIndex(
                    index = index,
                    itemCount = subscriptions.size,
                )
                subscriptionPageStartState = targetPageStart
                if (targetPageStart == subscriptionPageStart) {
                    subscriptionFocusRequesters[subscription.id]?.requestFocus() ?: return false
                } else {
                    pendingSubscriptionFocus = index
                }
                true
            }
            CloudRssFocusTarget.EmptySubscriptions -> {
                if (subscriptions.isEmpty()) {
                    subscriptionEmptyFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
            null -> false
        }
    }
    fun moveCloudRssActionFocus(action: CloudRssAction, key: Key): Boolean =
        requestCloudRssFocus(
            cloudRssActionFocusTarget(
                current = action,
                key = key,
                subscriptionCount = subscriptions.size,
            ),
        )
    fun moveCloudRssSubscriptionFocus(subscriptionId: Long, key: Key): Boolean {
        val index = subscriptions.indexOfFirst { it.id == subscriptionId }
        return requestCloudRssFocus(
            cloudRssSubscriptionFocusTarget(
                currentIndex = index,
                itemCount = subscriptions.size,
                key = key,
            ),
        )
    }
    fun moveCloudRssToggleFocus(toggle: CloudRssToggle, key: Key): Boolean =
        requestCloudRssFocus(cloudRssToggleFocusTarget(toggle, key))
    fun moveCloudRssFieldFocus(field: CloudRssField, key: Key): Boolean =
        requestCloudRssFocus(cloudRssFieldFocusTarget(field, key))

    LaunchedEffect(selectedSubscription?.id, subscriptions.map { it.id }) {
        val selectedIndex = subscriptions.indexOfFirst { it.id == selectedSubscription?.id }
        if (selectedIndex >= 0) {
            subscriptionPageStartState = cloudRssSubscriptionPageStartForIndex(
                index = selectedIndex,
                itemCount = subscriptions.size,
            )
        }
    }

    LaunchedEffect(pendingSubscriptionFocus, subscriptionPageStart, visibleSubscriptions.map { it.id }) {
        val index = pendingSubscriptionFocus ?: return@LaunchedEffect
        val subscription = subscriptions.getOrNull(index) ?: return@LaunchedEffect
        subscriptionFocusRequesters[subscription.id]?.requestFocus() ?: return@LaunchedEffect
        pendingSubscriptionFocus = null
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
                    title = cloudDriveRssTitleLabel(),
                    badge = if (enabled) labels.enabledBadge else labels.disabledBadge,
                    preview = cloudRssPreview(endpointUrl, fallback = labels.endpointFallback),
                ) {
                    LabeledTextField(
                        labels.endpoint,
                        endpointUrl,
                        onValueChange = onEndpointUrlChange,
                        inputModifier = Modifier.cloudRssFieldNavigation(
                            field = CloudRssField.Endpoint,
                            focusRequester = fieldFocusRequesters.getValue(CloudRssField.Endpoint),
                            onMove = ::moveCloudRssFieldFocus,
                        ),
                    )
                    LabeledTextField(
                        labels.username,
                        username,
                        onValueChange = onUsernameChange,
                        inputModifier = Modifier.cloudRssFieldNavigation(
                            field = CloudRssField.Username,
                            focusRequester = fieldFocusRequesters.getValue(CloudRssField.Username),
                            onMove = ::moveCloudRssFieldFocus,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                        LabeledTextField(
                            labels.apiToken,
                            token,
                            onValueChange = onTokenChange,
                            modifier = Modifier.weight(1f),
                            inputModifier = Modifier.cloudRssFieldNavigation(
                                field = CloudRssField.ApiToken,
                                focusRequester = fieldFocusRequesters.getValue(CloudRssField.ApiToken),
                                onMove = ::moveCloudRssFieldFocus,
                            ),
                        )
                        LabeledTextField(
                            labels.password,
                            password,
                            onValueChange = onPasswordChange,
                            modifier = Modifier.weight(1f),
                            inputModifier = Modifier.cloudRssFieldNavigation(
                                field = CloudRssField.Password,
                                focusRequester = fieldFocusRequesters.getValue(CloudRssField.Password),
                                onMove = ::moveCloudRssFieldFocus,
                            ),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton(
                            labels.saveCredentials,
                            onClick = onSaveCredentials,
                            secondary = true,
                            modifier = Modifier
                                .weight(1f)
                                .cloudRssActionNavigation(
                                    action = CloudRssAction.SaveCredentials,
                                    focusRequester = actionFocusRequesters.getValue(CloudRssAction.SaveCredentials),
                                    onMove = ::moveCloudRssActionFocus,
                                ),
                        )
                        TvActionButton(
                            labels.clearCredentials,
                            onClick = onClearCredentials,
                            secondary = true,
                            modifier = Modifier
                                .weight(1f)
                                .cloudRssActionNavigation(
                                    action = CloudRssAction.ClearCredentials,
                                    focusRequester = actionFocusRequesters.getValue(CloudRssAction.ClearCredentials),
                                    onMove = ::moveCloudRssActionFocus,
                                ),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton(
                            labels.login,
                            onClick = onLoginCloudDrive,
                            secondary = true,
                            modifier = Modifier
                                .weight(1f)
                                .cloudRssActionNavigation(
                                    action = CloudRssAction.LoginCloudDrive,
                                    focusRequester = actionFocusRequesters.getValue(CloudRssAction.LoginCloudDrive),
                                    onMove = ::moveCloudRssActionFocus,
                                ),
                        )
                        TvActionButton(
                            labels.verify,
                            onClick = onVerifyApiToken,
                            secondary = true,
                            modifier = Modifier
                                .weight(1f)
                                .cloudRssActionNavigation(
                                    action = CloudRssAction.VerifyApiToken,
                                    focusRequester = actionFocusRequesters.getValue(CloudRssAction.VerifyApiToken),
                                    onMove = ::moveCloudRssActionFocus,
                                ),
                        )
                    }
                }
                CloudRssCard(
                    title = cloudDriveRssSyncPathTitleLabel(),
                    badge = labels.pathBadge,
                    preview = cloudRssPathPairPreview(inboxPath, libraryPath),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                        CloudDrivePathSelectorField(
                            label = labels.inboxPath,
                            value = inboxPath,
                            onValueChange = onInboxPathChange,
                            onPick = { onPickCloudDriveDirectory(CloudDriveDirectoryTarget.INBOX) },
                            fieldModifier = Modifier.cloudRssFieldNavigation(
                                field = CloudRssField.InboxPath,
                                focusRequester = fieldFocusRequesters.getValue(CloudRssField.InboxPath),
                                onMove = ::moveCloudRssFieldFocus,
                            ),
                            pickModifier = Modifier.cloudRssActionNavigation(
                                action = CloudRssAction.PickInboxPath,
                                focusRequester = actionFocusRequesters.getValue(CloudRssAction.PickInboxPath),
                                onMove = ::moveCloudRssActionFocus,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        CloudDrivePathSelectorField(
                            label = labels.libraryPath,
                            value = libraryPath,
                            onValueChange = onLibraryPathChange,
                            onPick = { onPickCloudDriveDirectory(CloudDriveDirectoryTarget.LIBRARY) },
                            fieldModifier = Modifier.cloudRssFieldNavigation(
                                field = CloudRssField.LibraryPath,
                                focusRequester = fieldFocusRequesters.getValue(CloudRssField.LibraryPath),
                                onMove = ::moveCloudRssFieldFocus,
                            ),
                            pickModifier = Modifier.cloudRssActionNavigation(
                                action = CloudRssAction.PickLibraryPath,
                                focusRequester = actionFocusRequesters.getValue(CloudRssAction.PickLibraryPath),
                                onMove = ::moveCloudRssActionFocus,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                        LabeledTextField(
                            labels.intervalMinutes,
                            intervalMinutes,
                            onValueChange = onIntervalMinutesChange,
                            modifier = Modifier.weight(1f),
                            inputModifier = Modifier.cloudRssFieldNavigation(
                                field = CloudRssField.IntervalMinutes,
                                focusRequester = fieldFocusRequesters.getValue(CloudRssField.IntervalMinutes),
                                onMove = ::moveCloudRssFieldFocus,
                            ),
                        )
                        LabeledTextField(
                            labels.proxyHost,
                            proxyHost,
                            onValueChange = onProxyHostChange,
                            modifier = Modifier.weight(1f),
                            inputModifier = Modifier.cloudRssFieldNavigation(
                                field = CloudRssField.ProxyHost,
                                focusRequester = fieldFocusRequesters.getValue(CloudRssField.ProxyHost),
                                onMove = ::moveCloudRssFieldFocus,
                            ),
                        )
                        LabeledTextField(
                            labels.proxyPort,
                            proxyPort,
                            onValueChange = onProxyPortChange,
                            modifier = Modifier.weight(1f),
                            inputModifier = Modifier.cloudRssFieldNavigation(
                                field = CloudRssField.ProxyPort,
                                focusRequester = fieldFocusRequesters.getValue(CloudRssField.ProxyPort),
                                onMove = ::moveCloudRssFieldFocus,
                            ),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
                        ToggleRow(
                            labels.enabledToggle,
                            enabled,
                            onEnabledChange,
                            modifier = Modifier.cloudRssToggleNavigation(
                                toggle = CloudRssToggle.SyncEnabled,
                                focusRequester = toggleFocusRequesters.getValue(CloudRssToggle.SyncEnabled),
                                onMove = ::moveCloudRssToggleFocus,
                            ),
                        )
                        ToggleRow(
                            labels.rssProxy,
                            proxyEnabled,
                            onProxyEnabledChange,
                            modifier = Modifier.cloudRssToggleNavigation(
                                toggle = CloudRssToggle.ProxyEnabled,
                                focusRequester = toggleFocusRequesters.getValue(CloudRssToggle.ProxyEnabled),
                                onMove = ::moveCloudRssToggleFocus,
                            ),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton(
                            labels.useActiveSource,
                            onClick = onUseActiveScanSource,
                            secondary = true,
                            modifier = Modifier.cloudRssActionNavigation(
                                action = CloudRssAction.UseActiveSource,
                                focusRequester = actionFocusRequesters.getValue(CloudRssAction.UseActiveSource),
                                onMove = ::moveCloudRssActionFocus,
                            ),
                        )
                        TvActionButton(
                            labels.clearSource,
                            onClick = onClearScanSource,
                            secondary = true,
                            modifier = Modifier.cloudRssActionNavigation(
                                action = CloudRssAction.ClearScanSource,
                                focusRequester = actionFocusRequesters.getValue(CloudRssAction.ClearScanSource),
                                onMove = ::moveCloudRssActionFocus,
                            ),
                        )
                    }
                    Text("${labels.postSyncSource}$linkedSourceLabel", color = TextSecondary, fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton(
                            labels.saveSyncConfig,
                            onClick = onSaveConfig,
                            modifier = Modifier.cloudRssActionNavigation(
                                action = CloudRssAction.SaveSyncConfig,
                                focusRequester = actionFocusRequesters.getValue(CloudRssAction.SaveSyncConfig),
                                onMove = ::moveCloudRssActionFocus,
                            ),
                        )
                        TvActionButton(
                            labels.runSyncNow,
                            onClick = onRunSync,
                            secondary = true,
                            modifier = Modifier.cloudRssActionNavigation(
                                action = CloudRssAction.RunSyncNow,
                                focusRequester = actionFocusRequesters.getValue(CloudRssAction.RunSyncNow),
                                onMove = ::moveCloudRssActionFocus,
                            ),
                        )
                    }
                }
                if (directoryBrowser.open) {
                    CloudDriveDirectoryBrowserCard(
                        state = directoryBrowser,
                        onBrowse = onBrowseCloudDriveDirectory,
                        onSelect = onSelectCloudDriveDirectory,
                        onClose = onCloseCloudDriveDirectory,
                    )
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
                    LabeledTextField(
                        labels.subscriptionName,
                        rssName,
                        onValueChange = onRssNameChange,
                        inputModifier = Modifier.cloudRssFieldNavigation(
                            field = CloudRssField.SubscriptionName,
                            focusRequester = fieldFocusRequesters.getValue(CloudRssField.SubscriptionName),
                            onMove = ::moveCloudRssFieldFocus,
                        ),
                    )
                    LabeledTextField(
                        labels.subscriptionUrl,
                        rssUrl,
                        onValueChange = onRssUrlChange,
                        inputModifier = Modifier.cloudRssFieldNavigation(
                            field = CloudRssField.SubscriptionUrl,
                            focusRequester = fieldFocusRequesters.getValue(CloudRssField.SubscriptionUrl),
                            onMove = ::moveCloudRssFieldFocus,
                        ),
                    )
                    LabeledTextField(
                        labels.filterRegex,
                        rssFilter,
                        onValueChange = onRssFilterChange,
                        inputModifier = Modifier.cloudRssFieldNavigation(
                            field = CloudRssField.FilterRegex,
                            focusRequester = fieldFocusRequesters.getValue(CloudRssField.FilterRegex),
                            onMove = ::moveCloudRssFieldFocus,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
                        ToggleRow(
                            labels.enabledToggle,
                            rssEnabled,
                            onRssEnabledChange,
                            modifier = Modifier.cloudRssToggleNavigation(
                                toggle = CloudRssToggle.RssEnabled,
                                focusRequester = toggleFocusRequesters.getValue(CloudRssToggle.RssEnabled),
                                onMove = ::moveCloudRssToggleFocus,
                            ),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton(
                            labels.saveRss,
                            onClick = onSaveSubscription,
                            secondary = true,
                            modifier = Modifier
                                .weight(1f)
                                .cloudRssActionNavigation(
                                    action = CloudRssAction.SaveRss,
                                    focusRequester = actionFocusRequesters.getValue(CloudRssAction.SaveRss),
                                    onMove = ::moveCloudRssActionFocus,
                                ),
                        )
                        TvActionButton(
                            labels.deleteRss,
                            onClick = onDeleteSubscription,
                            secondary = true,
                            modifier = Modifier
                                .weight(1f)
                                .cloudRssActionNavigation(
                                    action = CloudRssAction.DeleteRss,
                                    focusRequester = actionFocusRequesters.getValue(CloudRssAction.DeleteRss),
                                    onMove = ::moveCloudRssActionFocus,
                                ),
                        )
                    }
                    if (subscriptions.isEmpty()) {
                        CloudRssSubscriptionEmptyState(
                            text = labels.rssEmpty,
                            focusRequester = subscriptionEmptyFocusRequester,
                            onMove = ::requestCloudRssFocus,
                        )
                    } else {
                        visibleSubscriptions.forEachIndexed { visibleIndex, subscription ->
                            val absoluteIndex = subscriptionPageStart + visibleIndex
                            RssSubscriptionRow(
                                subscription = subscription,
                                selected = selectedSubscription?.id == subscription.id,
                                onClick = { requestCloudRssFocus(CloudRssFocusTarget.Subscription(absoluteIndex)) },
                                onNavigate = { key ->
                                    moveCloudRssSubscriptionFocus(subscription.id, key)
                                },
                                modifier = Modifier.focusRequester(subscriptionFocusRequesters.getValue(subscription.id)),
                            )
                            Spacer(Modifier.height(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp))
                        }
                        cloudRssSubscriptionPageSummary(
                            pageStart = subscriptionPageStart,
                            visibleCount = visibleSubscriptions.size,
                            itemCount = subscriptions.size,
                        )?.let { summary ->
                            Text(
                                summary,
                                color = TextSecondary,
                                fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                            )
                        }
                    }
                }
                CloudRssCard(
                    title = cloudDriveRssRuntimeTitleLabel(),
                    badge = labels.runBadge,
                    preview = cloudRssPreview(schedulerStatusText, fallback = labels.schedulerIdle),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                        TvActionButton(
                            labels.startScheduler,
                            onClick = onStartScheduler,
                            secondary = true,
                            modifier = Modifier.cloudRssActionNavigation(
                                action = CloudRssAction.StartScheduler,
                                focusRequester = actionFocusRequesters.getValue(CloudRssAction.StartScheduler),
                                onMove = ::moveCloudRssActionFocus,
                            ),
                        )
                        TvActionButton(
                            labels.stopScheduler,
                            onClick = onStopScheduler,
                            secondary = true,
                            modifier = Modifier.cloudRssActionNavigation(
                                action = CloudRssAction.StopScheduler,
                                focusRequester = actionFocusRequesters.getValue(CloudRssAction.StopScheduler),
                                onMove = ::moveCloudRssActionFocus,
                            ),
                        )
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
private fun CloudRssSubscriptionEmptyState(
    text: String,
    focusRequester: FocusRequester,
    onMove: (CloudRssFocusTarget?) -> Boolean,
) {
    DesktopSelectableRow(
        selected = false,
        onClick = {},
        modifier = Modifier
            .focusRequester(focusRequester),
        heightDp = MiruPlayUiMetrics.RSS_EMPTY_STATE_HEIGHT_DP,
        inactiveAlpha = 0.48f,
        onNavigationKey = { key ->
            onMove(cloudRssSubscriptionEmptyFocusTarget(key))
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
private fun CloudDrivePathSelectorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onPick: () -> Unit,
    fieldModifier: Modifier,
    pickModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
    ) {
        LabeledTextField(
            label = label,
            value = value,
            onValueChange = onValueChange,
            inputModifier = fieldModifier,
        )
            TvActionButton(
                text = cloudDriveRssChooseDirectoryActionLabel(),
            onClick = onPick,
            secondary = true,
            modifier = pickModifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CloudDriveDirectoryBrowserCard(
    state: CloudDriveDirectoryBrowserState,
    onBrowse: (String) -> Unit,
    onSelect: (CloudDriveDirectoryTarget, String) -> Unit,
    onClose: () -> Unit,
) {
    var directoryPageStart by remember(state.path, state.entries.size) { mutableStateOf(0) }
    var pendingDirectoryRowFocus by remember { mutableStateOf<Int?>(null) }
    val pageStart = cloudDriveDirectoryCoercedPageStart(
        pageStart = directoryPageStart,
        itemCount = state.entries.size,
    )
    val visibleEntries = state.entries
        .drop(pageStart)
        .take(CLOUD_DRIVE_DIRECTORY_PAGE_SIZE)
    val actionFocusRequesters = remember {
        CloudDriveDirectoryAction.entries.associateWith { FocusRequester() }
    }
    val entryFocusRequesters = remember(pageStart, visibleEntries.map { it.path }) {
        List(visibleEntries.size) { FocusRequester() }
    }
    val emptyFocusRequester = remember { FocusRequester() }
    val hasEmptyFocusTarget = state.isLoading || state.entries.isEmpty()

    fun requestDirectoryFocus(target: CloudDriveDirectoryFocusTarget?): Boolean {
        return when (target) {
            is CloudDriveDirectoryFocusTarget.Action -> {
                actionFocusRequesters.getValue(target.action).requestFocus()
                true
            }
            is CloudDriveDirectoryFocusTarget.Row -> {
                val index = target.index.takeIf { it in state.entries.indices } ?: return false
                val targetPageStart = cloudDriveDirectoryPageStartForIndex(
                    index = index,
                    itemCount = state.entries.size,
                )
                directoryPageStart = targetPageStart
                val visibleIndex = index - targetPageStart
                if (targetPageStart == pageStart) {
                    entryFocusRequesters.getOrNull(visibleIndex)?.requestFocus() ?: return false
                } else {
                    pendingDirectoryRowFocus = index
                }
                true
            }
            CloudDriveDirectoryFocusTarget.EmptyState -> {
                emptyFocusRequester.requestFocus()
                true
            }
            null -> false
        }
    }

    LaunchedEffect(state.open, state.path) {
        if (state.open) {
            directoryPageStart = 0
            pendingDirectoryRowFocus = null
            actionFocusRequesters.getValue(CloudDriveDirectoryAction.UseCurrent).requestFocus()
        }
    }
    LaunchedEffect(pageStart, visibleEntries.map { it.path }, pendingDirectoryRowFocus) {
        val pendingIndex = pendingDirectoryRowFocus ?: return@LaunchedEffect
        if (pendingIndex in pageStart until pageStart + visibleEntries.size) {
            entryFocusRequesters.getOrNull(pendingIndex - pageStart)?.requestFocus()
            pendingDirectoryRowFocus = null
        }
    }
    CloudRssCard(
        title = state.target.title,
        badge = cloudDriveRssDirectoryBadgeLabel(),
        preview = state.displayPath,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
            TvActionButton(
                text = cloudDriveRssUseCurrentDirectoryActionLabel(),
                onClick = { onSelect(state.target, state.path) },
                modifier = Modifier
                    .weight(1f)
                    .cloudDriveDirectoryActionNavigation(
                        action = CloudDriveDirectoryAction.UseCurrent,
                        focusRequester = actionFocusRequesters.getValue(CloudDriveDirectoryAction.UseCurrent),
                        itemCount = visibleEntries.size,
                        hasEmptyState = hasEmptyFocusTarget,
                        onMove = ::requestDirectoryFocus,
                    ),
            )
            TvActionButton(
                text = cloudDriveRssParentDirectoryActionLabel(),
                onClick = { state.parentPath?.let(onBrowse) },
                secondary = true,
                modifier = Modifier
                    .weight(1f)
                    .cloudDriveDirectoryActionNavigation(
                        action = CloudDriveDirectoryAction.Parent,
                        focusRequester = actionFocusRequesters.getValue(CloudDriveDirectoryAction.Parent),
                        itemCount = visibleEntries.size,
                        hasEmptyState = hasEmptyFocusTarget,
                        onMove = ::requestDirectoryFocus,
                    ),
            )
            TvActionButton(
                text = cloudDriveRssCloseActionLabel(),
                onClick = onClose,
                secondary = true,
                modifier = Modifier
                    .weight(1f)
                    .cloudDriveDirectoryActionNavigation(
                        action = CloudDriveDirectoryAction.Close,
                        focusRequester = actionFocusRequesters.getValue(CloudDriveDirectoryAction.Close),
                        itemCount = visibleEntries.size,
                        hasEmptyState = hasEmptyFocusTarget,
                        onMove = ::requestDirectoryFocus,
                    ),
            )
        }
        val browserMessage = state.message
        if (!browserMessage.isNullOrBlank()) {
            StatusBox(browserMessage)
        }
        if (state.isLoading) {
            CloudDriveDirectoryEmptyState(
                text = cloudDriveRssLoadingDirectoriesMessage(),
                focusRequester = emptyFocusRequester,
                onMove = ::requestDirectoryFocus,
            )
        } else if (state.entries.isEmpty()) {
            CloudDriveDirectoryEmptyState(
                text = cloudDriveRssEmptyDirectoryMessage(),
                focusRequester = emptyFocusRequester,
                onMove = ::requestDirectoryFocus,
            )
        } else {
            visibleEntries.forEachIndexed { index, entry ->
                CloudDriveDirectoryRow(
                    entry = entry,
                    onClick = { onBrowse(entry.path) },
                    onNavigate = { key ->
                        requestDirectoryFocus(
                            cloudDriveDirectoryRowFocusTarget(
                                currentIndex = pageStart + index,
                                itemCount = state.entries.size,
                                key = key,
                            ),
                        )
                    },
                    modifier = Modifier.focusRequester(entryFocusRequesters[index]),
                )
            }
            cloudDriveDirectoryPageSummary(
                pageStart = pageStart,
                visibleCount = visibleEntries.size,
                itemCount = state.entries.size,
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
private fun CloudDriveDirectoryEmptyState(
    text: String,
    focusRequester: FocusRequester,
    onMove: (CloudDriveDirectoryFocusTarget?) -> Boolean,
) {
    DesktopSelectableRow(
        selected = false,
        onClick = {},
        modifier = Modifier
            .focusRequester(focusRequester),
        heightDp = 110,
        inactiveAlpha = 0.48f,
        onNavigationKey = { key ->
            onMove(cloudDriveDirectoryEmptyFocusTarget(key))
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
private fun CloudDriveDirectoryRow(
    entry: CloudDriveDirectoryEntry,
    onClick: () -> Unit,
    onNavigate: (Key) -> Boolean,
    modifier: Modifier = Modifier,
) {
    DesktopSelectableRow(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        onNavigationKey = { key ->
            key.isCloudRssVerticalKey() && onNavigate(key)
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
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

internal enum class CloudRssAction {
    SaveCredentials,
    ClearCredentials,
    LoginCloudDrive,
    VerifyApiToken,
    PickInboxPath,
    PickLibraryPath,
    UseActiveSource,
    ClearScanSource,
    SaveSyncConfig,
    RunSyncNow,
    SaveRss,
    DeleteRss,
    StartScheduler,
    StopScheduler,
}

internal enum class CloudRssToggle {
    SyncEnabled,
    ProxyEnabled,
    RssEnabled,
}

internal enum class CloudRssField {
    Endpoint,
    Username,
    ApiToken,
    Password,
    InboxPath,
    LibraryPath,
    IntervalMinutes,
    ProxyHost,
    ProxyPort,
    SubscriptionName,
    SubscriptionUrl,
    FilterRegex,
}

internal sealed interface CloudRssFocusTarget {
    data class Action(val action: CloudRssAction) : CloudRssFocusTarget
    data class Toggle(val toggle: CloudRssToggle) : CloudRssFocusTarget
    data class Field(val field: CloudRssField) : CloudRssFocusTarget
    data class Subscription(val index: Int) : CloudRssFocusTarget
    data object EmptySubscriptions : CloudRssFocusTarget
}

private fun Modifier.cloudRssActionNavigation(
    action: CloudRssAction,
    focusRequester: FocusRequester,
    onMove: (CloudRssAction, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .desktopNavigationKeyHandler { key -> onMove(action, key) }

private fun Modifier.cloudRssToggleNavigation(
    toggle: CloudRssToggle,
    focusRequester: FocusRequester,
    onMove: (CloudRssToggle, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .desktopNavigationKeyHandler { key -> onMove(toggle, key) }

private fun Modifier.cloudRssFieldNavigation(
    field: CloudRssField,
    focusRequester: FocusRequester,
    onMove: (CloudRssField, Key) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .desktopNavigationKeyHandler { key -> onMove(field, key) }

internal fun cloudRssToggleFocusTarget(
    current: CloudRssToggle,
    key: Key,
): CloudRssFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        cloudRssToggleFocusTarget(current, intent)
    }

internal fun cloudRssToggleFocusTarget(
    current: CloudRssToggle,
    intent: MiruPlayInputIntent,
): CloudRssFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> when (current) {
            CloudRssToggle.SyncEnabled -> CloudRssFocusTarget.Field(CloudRssField.IntervalMinutes)
            CloudRssToggle.ProxyEnabled -> CloudRssFocusTarget.Field(CloudRssField.ProxyHost)
            CloudRssToggle.RssEnabled -> CloudRssFocusTarget.Field(CloudRssField.FilterRegex)
        }
        1 -> when (current) {
            CloudRssToggle.SyncEnabled -> CloudRssFocusTarget.Action(CloudRssAction.UseActiveSource)
            CloudRssToggle.ProxyEnabled -> CloudRssFocusTarget.Action(CloudRssAction.ClearScanSource)
            CloudRssToggle.RssEnabled -> CloudRssFocusTarget.Action(CloudRssAction.SaveRss)
        }
        else -> intent.horizontalNavigationDelta()?.let { delta ->
            cloudRssHorizontalToggle(current, delta)?.let(CloudRssFocusTarget::Toggle)
        }
    }

private fun cloudRssHorizontalToggle(
    current: CloudRssToggle,
    delta: Int,
): CloudRssToggle? {
    val row = when (current) {
        CloudRssToggle.SyncEnabled,
        CloudRssToggle.ProxyEnabled,
        -> listOf(CloudRssToggle.SyncEnabled, CloudRssToggle.ProxyEnabled)
        CloudRssToggle.RssEnabled -> listOf(CloudRssToggle.RssEnabled)
    }
    val targetIndex = row.indexOf(current) + delta
    return row.getOrNull(targetIndex)
}

internal fun cloudRssFieldFocusTarget(
    current: CloudRssField,
    key: Key,
): CloudRssFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        cloudRssFieldFocusTarget(current, intent)
    }

internal fun cloudRssFieldFocusTarget(
    current: CloudRssField,
    intent: MiruPlayInputIntent,
): CloudRssFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> when (current) {
            CloudRssField.Username -> CloudRssFocusTarget.Field(CloudRssField.Endpoint)
            CloudRssField.ApiToken -> CloudRssFocusTarget.Field(CloudRssField.Username)
            CloudRssField.Password -> CloudRssFocusTarget.Field(CloudRssField.Username)
            CloudRssField.InboxPath -> CloudRssFocusTarget.Action(CloudRssAction.LoginCloudDrive)
            CloudRssField.LibraryPath -> CloudRssFocusTarget.Action(CloudRssAction.VerifyApiToken)
            CloudRssField.IntervalMinutes -> CloudRssFocusTarget.Field(CloudRssField.InboxPath)
            CloudRssField.ProxyHost,
            CloudRssField.ProxyPort,
            -> CloudRssFocusTarget.Field(CloudRssField.LibraryPath)
            CloudRssField.SubscriptionUrl -> CloudRssFocusTarget.Field(CloudRssField.SubscriptionName)
            CloudRssField.FilterRegex -> CloudRssFocusTarget.Field(CloudRssField.SubscriptionUrl)
            else -> null
        }
        1 -> when (current) {
            CloudRssField.Endpoint -> CloudRssFocusTarget.Field(CloudRssField.Username)
            CloudRssField.Username -> CloudRssFocusTarget.Field(CloudRssField.ApiToken)
            CloudRssField.ApiToken -> CloudRssFocusTarget.Action(CloudRssAction.SaveCredentials)
            CloudRssField.Password -> CloudRssFocusTarget.Action(CloudRssAction.ClearCredentials)
            CloudRssField.InboxPath -> CloudRssFocusTarget.Action(CloudRssAction.PickInboxPath)
            CloudRssField.LibraryPath -> CloudRssFocusTarget.Action(CloudRssAction.PickLibraryPath)
            CloudRssField.IntervalMinutes -> CloudRssFocusTarget.Toggle(CloudRssToggle.SyncEnabled)
            CloudRssField.ProxyHost,
            CloudRssField.ProxyPort,
            -> CloudRssFocusTarget.Toggle(CloudRssToggle.ProxyEnabled)
            CloudRssField.SubscriptionName -> CloudRssFocusTarget.Field(CloudRssField.SubscriptionUrl)
            CloudRssField.SubscriptionUrl -> CloudRssFocusTarget.Field(CloudRssField.FilterRegex)
            CloudRssField.FilterRegex -> CloudRssFocusTarget.Toggle(CloudRssToggle.RssEnabled)
        }
        else -> intent.horizontalNavigationDelta()?.let { delta ->
            cloudRssHorizontalField(current, delta)?.let(CloudRssFocusTarget::Field)
        }
    }

private fun cloudRssHorizontalField(
    current: CloudRssField,
    delta: Int,
): CloudRssField? {
    val row = when (current) {
        CloudRssField.Endpoint -> listOf(CloudRssField.Endpoint)
        CloudRssField.Username -> listOf(CloudRssField.Username)
        CloudRssField.ApiToken,
        CloudRssField.Password,
        -> listOf(CloudRssField.ApiToken, CloudRssField.Password)
        CloudRssField.InboxPath,
        CloudRssField.LibraryPath,
        -> listOf(CloudRssField.InboxPath, CloudRssField.LibraryPath)
        CloudRssField.IntervalMinutes,
        CloudRssField.ProxyHost,
        CloudRssField.ProxyPort,
        -> listOf(CloudRssField.IntervalMinutes, CloudRssField.ProxyHost, CloudRssField.ProxyPort)
        CloudRssField.SubscriptionName -> listOf(CloudRssField.SubscriptionName)
        CloudRssField.SubscriptionUrl -> listOf(CloudRssField.SubscriptionUrl)
        CloudRssField.FilterRegex -> listOf(CloudRssField.FilterRegex)
    }
    val targetIndex = row.indexOf(current) + delta
    return row.getOrNull(targetIndex)
}

internal fun cloudRssActionFocusTarget(
    current: CloudRssAction,
    key: Key,
    subscriptionCount: Int,
): CloudRssFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        cloudRssActionFocusTarget(current, intent, subscriptionCount)
    }

internal fun cloudRssActionFocusTarget(
    current: CloudRssAction,
    intent: MiruPlayInputIntent,
    subscriptionCount: Int,
): CloudRssFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> cloudRssActionUpTarget(current, subscriptionCount)
        1 -> cloudRssActionDownTarget(current, subscriptionCount)
        else -> intent.horizontalNavigationDelta()?.let { delta ->
            cloudRssHorizontalAction(current, delta)?.let(CloudRssFocusTarget::Action)
        }
    }

internal fun cloudRssSubscriptionFocusTarget(
    currentIndex: Int,
    itemCount: Int,
    key: Key,
): CloudRssFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        cloudRssSubscriptionFocusTarget(currentIndex, itemCount, intent)
    }

internal fun cloudRssSubscriptionFocusTarget(
    currentIndex: Int,
    itemCount: Int,
    intent: MiruPlayInputIntent,
): CloudRssFocusTarget? {
    if (itemCount <= 0) return null
    return when (intent.verticalNavigationDelta()) {
        -1 -> {
            if (currentIndex <= 0) {
                CloudRssFocusTarget.Action(CloudRssAction.SaveRss)
            } else {
                CloudRssFocusTarget.Subscription(currentIndex - 1)
            }
        }
        1 -> {
            if (currentIndex < 0) {
                CloudRssFocusTarget.Subscription(0)
            } else if (currentIndex >= itemCount - 1) {
                CloudRssFocusTarget.Action(CloudRssAction.StartScheduler)
            } else {
                CloudRssFocusTarget.Subscription(currentIndex + 1)
            }
        }
        else -> null
    }
}

internal fun cloudRssSubscriptionEmptyFocusTarget(key: Key): CloudRssFocusTarget? =
    key.toMiruPlayInputIntent()?.let(::cloudRssSubscriptionEmptyFocusTarget)

internal fun cloudRssSubscriptionEmptyFocusTarget(intent: MiruPlayInputIntent): CloudRssFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> CloudRssFocusTarget.Action(CloudRssAction.SaveRss)
        1 -> CloudRssFocusTarget.Action(CloudRssAction.StartScheduler)
        else -> null
    }

internal fun cloudRssSubscriptionPageStartForIndex(
    index: Int,
    itemCount: Int,
    pageSize: Int = CLOUD_RSS_SUBSCRIPTION_PAGE_SIZE,
): Int = pagedListPageStartForIndex(index, itemCount, pageSize)

internal fun cloudRssSubscriptionCoercedPageStart(
    pageStart: Int,
    itemCount: Int,
    pageSize: Int = CLOUD_RSS_SUBSCRIPTION_PAGE_SIZE,
): Int = pagedListCoercedPageStart(pageStart, itemCount, pageSize)

internal fun cloudRssSubscriptionPageSummary(
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
): String? {
    val safeStart = cloudRssSubscriptionCoercedPageStart(pageStart, itemCount)
    return pagedListPageSummary(
        pageStart = safeStart,
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = CLOUD_RSS_SUBSCRIPTION_PAGE_SIZE,
        unitLabel = rssSubscriptionPageUnitLabel(),
    )
}

internal enum class CloudDriveDirectoryAction {
    UseCurrent,
    Parent,
    Close,
}

internal sealed interface CloudDriveDirectoryFocusTarget {
    data class Action(val action: CloudDriveDirectoryAction) : CloudDriveDirectoryFocusTarget
    data class Row(val index: Int) : CloudDriveDirectoryFocusTarget
    data object EmptyState : CloudDriveDirectoryFocusTarget
}

private fun Modifier.cloudDriveDirectoryActionNavigation(
    action: CloudDriveDirectoryAction,
    focusRequester: FocusRequester,
    itemCount: Int,
    hasEmptyState: Boolean,
    onMove: (CloudDriveDirectoryFocusTarget?) -> Boolean,
): Modifier =
    focusRequester(focusRequester)
        .desktopNavigationKeyHandler { key ->
            onMove(cloudDriveDirectoryActionFocusTarget(action, itemCount, key, hasEmptyState))
        }

internal fun cloudDriveDirectoryActionFocusTarget(
    current: CloudDriveDirectoryAction,
    itemCount: Int,
    key: Key,
    hasEmptyState: Boolean = false,
): CloudDriveDirectoryFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        cloudDriveDirectoryActionFocusTarget(current, itemCount, intent, hasEmptyState)
    }

internal fun cloudDriveDirectoryActionFocusTarget(
    current: CloudDriveDirectoryAction,
    itemCount: Int,
    intent: MiruPlayInputIntent,
    hasEmptyState: Boolean = false,
): CloudDriveDirectoryFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        1 -> when {
            itemCount > 0 -> CloudDriveDirectoryFocusTarget.Row(0)
            hasEmptyState -> CloudDriveDirectoryFocusTarget.EmptyState
            else -> null
        }
        else -> intent.horizontalNavigationDelta()?.let { delta ->
            cloudDriveDirectoryHorizontalAction(current, delta)?.let(CloudDriveDirectoryFocusTarget::Action)
        }
    }

internal fun cloudDriveDirectoryEmptyFocusTarget(key: Key): CloudDriveDirectoryFocusTarget? =
    key.toMiruPlayInputIntent()?.let(::cloudDriveDirectoryEmptyFocusTarget)

internal fun cloudDriveDirectoryEmptyFocusTarget(intent: MiruPlayInputIntent): CloudDriveDirectoryFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> CloudDriveDirectoryFocusTarget.Action(CloudDriveDirectoryAction.UseCurrent)
        else -> null
    }

internal fun cloudDriveDirectoryRowFocusTarget(
    currentIndex: Int,
    itemCount: Int,
    key: Key,
): CloudDriveDirectoryFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        cloudDriveDirectoryRowFocusTarget(currentIndex, itemCount, intent)
    }

internal fun cloudDriveDirectoryRowFocusTarget(
    currentIndex: Int,
    itemCount: Int,
    intent: MiruPlayInputIntent,
): CloudDriveDirectoryFocusTarget? {
    if (itemCount <= 0) return null
    return when (intent.verticalNavigationDelta()) {
        -1 -> if (currentIndex <= 0) {
            CloudDriveDirectoryFocusTarget.Action(CloudDriveDirectoryAction.UseCurrent)
        } else {
            CloudDriveDirectoryFocusTarget.Row(currentIndex - 1)
        }
        1 -> CloudDriveDirectoryFocusTarget.Row(currentIndex + 1).takeIf { currentIndex + 1 in 0 until itemCount }
        else -> return null
    }
}

internal fun cloudDriveDirectoryPageStartForIndex(
    index: Int,
    itemCount: Int,
    pageSize: Int = CLOUD_DRIVE_DIRECTORY_PAGE_SIZE,
): Int = pagedListPageStartForIndex(index, itemCount, pageSize)

internal fun cloudDriveDirectoryCoercedPageStart(
    pageStart: Int,
    itemCount: Int,
    pageSize: Int = CLOUD_DRIVE_DIRECTORY_PAGE_SIZE,
): Int = pagedListCoercedPageStart(pageStart, itemCount, pageSize)

internal fun cloudDriveDirectoryPageSummary(
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
): String? {
    val safeStart = cloudDriveDirectoryCoercedPageStart(pageStart, itemCount)
    return pagedListPageSummary(
        pageStart = safeStart,
        visibleCount = visibleCount,
        itemCount = itemCount,
        pageSize = CLOUD_DRIVE_DIRECTORY_PAGE_SIZE,
        unitLabel = cloudDriveRssDirectoryPageUnitLabel(),
    )
}

private fun cloudDriveDirectoryHorizontalAction(
    current: CloudDriveDirectoryAction,
    delta: Int,
): CloudDriveDirectoryAction? {
    val actions = CloudDriveDirectoryAction.entries
    val targetIndex = actions.indexOf(current) + delta
    return actions.getOrNull(targetIndex)
}

private fun cloudRssHorizontalAction(
    current: CloudRssAction,
    delta: Int,
): CloudRssAction? {
    val row = when (current) {
        CloudRssAction.SaveCredentials,
        CloudRssAction.ClearCredentials,
        -> listOf(CloudRssAction.SaveCredentials, CloudRssAction.ClearCredentials)
        CloudRssAction.LoginCloudDrive,
        CloudRssAction.VerifyApiToken,
        -> listOf(CloudRssAction.LoginCloudDrive, CloudRssAction.VerifyApiToken)
        CloudRssAction.PickInboxPath,
        CloudRssAction.PickLibraryPath,
        -> listOf(CloudRssAction.PickInboxPath, CloudRssAction.PickLibraryPath)
        CloudRssAction.UseActiveSource,
        CloudRssAction.ClearScanSource,
        -> listOf(CloudRssAction.UseActiveSource, CloudRssAction.ClearScanSource)
        CloudRssAction.SaveSyncConfig,
        CloudRssAction.RunSyncNow,
        -> listOf(CloudRssAction.SaveSyncConfig, CloudRssAction.RunSyncNow)
        CloudRssAction.SaveRss,
        CloudRssAction.DeleteRss,
        -> listOf(CloudRssAction.SaveRss, CloudRssAction.DeleteRss)
        CloudRssAction.StartScheduler,
        CloudRssAction.StopScheduler,
        -> listOf(CloudRssAction.StartScheduler, CloudRssAction.StopScheduler)
    }
    val targetIndex = row.indexOf(current) + delta
    return row.getOrNull(targetIndex)
}

private fun cloudRssActionUpTarget(
    current: CloudRssAction,
    subscriptionCount: Int,
): CloudRssFocusTarget? =
    when (current) {
        CloudRssAction.SaveCredentials -> CloudRssFocusTarget.Field(CloudRssField.ApiToken)
        CloudRssAction.ClearCredentials -> CloudRssFocusTarget.Field(CloudRssField.Password)
        CloudRssAction.LoginCloudDrive -> CloudRssFocusTarget.Action(CloudRssAction.SaveCredentials)
        CloudRssAction.VerifyApiToken -> CloudRssFocusTarget.Action(CloudRssAction.ClearCredentials)
        CloudRssAction.PickInboxPath -> CloudRssFocusTarget.Field(CloudRssField.InboxPath)
        CloudRssAction.PickLibraryPath -> CloudRssFocusTarget.Field(CloudRssField.LibraryPath)
        CloudRssAction.UseActiveSource -> CloudRssFocusTarget.Toggle(CloudRssToggle.SyncEnabled)
        CloudRssAction.ClearScanSource -> CloudRssFocusTarget.Toggle(CloudRssToggle.ProxyEnabled)
        CloudRssAction.SaveRss -> CloudRssFocusTarget.Toggle(CloudRssToggle.RssEnabled)
        CloudRssAction.DeleteRss -> CloudRssFocusTarget.Toggle(CloudRssToggle.RssEnabled)
        CloudRssAction.SaveSyncConfig -> CloudRssFocusTarget.Action(CloudRssAction.UseActiveSource)
        CloudRssAction.RunSyncNow -> CloudRssFocusTarget.Action(CloudRssAction.ClearScanSource)
        CloudRssAction.StartScheduler -> if (subscriptionCount > 0) {
            CloudRssFocusTarget.Subscription(subscriptionCount - 1)
        } else {
            CloudRssFocusTarget.Action(CloudRssAction.SaveRss)
        }
        CloudRssAction.StopScheduler -> if (subscriptionCount > 0) {
            CloudRssFocusTarget.Subscription(subscriptionCount - 1)
        } else {
            CloudRssFocusTarget.Action(CloudRssAction.DeleteRss)
        }
        else -> null
    }

private fun cloudRssActionDownTarget(
    current: CloudRssAction,
    subscriptionCount: Int,
): CloudRssFocusTarget? =
    when (current) {
        CloudRssAction.SaveCredentials -> CloudRssFocusTarget.Action(CloudRssAction.LoginCloudDrive)
        CloudRssAction.ClearCredentials -> CloudRssFocusTarget.Action(CloudRssAction.VerifyApiToken)
        CloudRssAction.LoginCloudDrive -> CloudRssFocusTarget.Field(CloudRssField.InboxPath)
        CloudRssAction.VerifyApiToken -> CloudRssFocusTarget.Field(CloudRssField.LibraryPath)
        CloudRssAction.PickInboxPath -> CloudRssFocusTarget.Field(CloudRssField.IntervalMinutes)
        CloudRssAction.PickLibraryPath -> CloudRssFocusTarget.Field(CloudRssField.ProxyHost)
        CloudRssAction.UseActiveSource -> CloudRssFocusTarget.Action(CloudRssAction.SaveSyncConfig)
        CloudRssAction.ClearScanSource -> CloudRssFocusTarget.Action(CloudRssAction.RunSyncNow)
        CloudRssAction.SaveRss -> if (subscriptionCount > 0) {
            CloudRssFocusTarget.Subscription(0)
        } else {
            CloudRssFocusTarget.EmptySubscriptions
        }
        CloudRssAction.DeleteRss -> if (subscriptionCount > 0) {
            CloudRssFocusTarget.Subscription(0)
        } else {
            CloudRssFocusTarget.EmptySubscriptions
        }
        else -> null
    }

private data class SettingsQuickAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

internal fun settingsQuickActionNavigationTarget(
    currentIndex: Int,
    actionCount: Int = 0,
    key: Key,
    enabledActions: List<Boolean> = List(actionCount) { true },
): Int? =
    key.toMiruPlayInputIntent()?.let { intent ->
        settingsQuickActionNavigationTarget(currentIndex, actionCount, intent, enabledActions)
    }

internal fun settingsQuickActionNavigationTarget(
    currentIndex: Int,
    actionCount: Int = 0,
    intent: MiruPlayInputIntent,
    enabledActions: List<Boolean> = List(actionCount) { true },
): Int? {
    val count = enabledActions.size.takeIf { actionCount == 0 } ?: actionCount
    if (count <= 0 || currentIndex !in 0 until count) return null
    val delta = intent.horizontalNavigationDelta() ?: return null
    var target = currentIndex + delta
    while (target in 0 until count) {
        if (enabledActions.getOrElse(target) { true }) return target
        target += delta
    }
    return null
}

internal sealed interface SettingsQuickActionFocusTarget {
    data class Action(val index: Int) : SettingsQuickActionFocusTarget
    data object SectionMenu : SettingsQuickActionFocusTarget
}

internal fun settingsQuickActionFocusTarget(
    currentIndex: Int,
    actionCount: Int = 0,
    key: Key,
    enabledActions: List<Boolean> = List(actionCount) { true },
): SettingsQuickActionFocusTarget? =
    key.toMiruPlayInputIntent()?.let { intent ->
        settingsQuickActionFocusTarget(currentIndex, actionCount, intent, enabledActions)
    }

internal fun settingsQuickActionFocusTarget(
    currentIndex: Int,
    actionCount: Int = 0,
    intent: MiruPlayInputIntent,
    enabledActions: List<Boolean> = List(actionCount) { true },
): SettingsQuickActionFocusTarget? =
    when (intent.verticalNavigationDelta()) {
        -1 -> SettingsQuickActionFocusTarget.SectionMenu
            .takeIf {
                val count = enabledActions.size.takeIf { actionCount == 0 } ?: actionCount
                count > 0 &&
                    currentIndex in 0 until count &&
                    enabledActions.getOrElse(currentIndex) { true }
            }
        else -> settingsQuickActionNavigationTarget(
            currentIndex = currentIndex,
            actionCount = actionCount,
            intent = intent,
            enabledActions = enabledActions,
        )?.let(SettingsQuickActionFocusTarget::Action)
    }

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
        endpoint = cloudDriveRssEndpointFieldLabel(),
        username = cloudDriveRssUsernameFieldLabel(),
        apiToken = cloudDriveRssApiTokenFieldLabel(),
        password = cloudDriveRssPasswordFieldLabel(),
        saveCredentials = cloudDriveRssSaveCredentialsActionLabel(),
        clearCredentials = cloudDriveRssClearCredentialsActionLabel(),
        login = cloudDriveRssLoginActionLabel(),
        verify = cloudDriveRssVerifyApiTokenActionLabel(),
        inboxPath = cloudDriveRssInboxPathFieldLabel(),
        libraryPath = cloudDriveRssLibraryPathFieldLabel(),
        intervalMinutes = cloudDriveRssIntervalMinutesFieldLabel(),
        proxyHost = cloudDriveRssProxyHostFieldLabel(),
        proxyPort = cloudDriveRssProxyPortFieldLabel(),
        enabledToggle = cloudDriveRssEnabledToggleLabel(),
        rssProxy = cloudDriveRssProxySettingLabel(),
        useActiveSource = cloudDriveRssUseActiveSourceActionLabel(),
        clearSource = cloudDriveRssClearScanSourceActionLabel(),
        postSyncSource = cloudDriveRssPostSyncSourceLabel(),
        saveSyncConfig = cloudDriveRssSaveConfigActionLabel(),
        runSyncNow = cloudDriveRssRunNowActionLabel(),
        rssSubscriptions = rssSubscriptionsTitleLabel(),
        subscriptionName = rssSubscriptionNameFieldLabel(),
        subscriptionUrl = rssSubscriptionUrlFieldLabel(),
        filterRegex = rssSubscriptionFilterRegexFieldLabel(),
        saveRss = rssSubscriptionSaveActionLabel(),
        deleteRss = rssSubscriptionDeleteActionLabel(),
        rssEmpty = rssSubscriptionEmptyMessage(),
        rssPreviewFallback = rssSubscriptionFormPreviewFallbackLabel(),
        startScheduler = cloudDriveRssStartSchedulerActionLabel(),
        stopScheduler = cloudDriveRssStopSchedulerActionLabel(),
        endpointFallback = cloudDriveRssEndpointFallbackLabel(),
        schedulerIdle = cloudDriveRssSchedulerIdleLabel(),
        enabledBadge = cloudDriveRssEnabledBadgeLabel(true),
        disabledBadge = cloudDriveRssEnabledBadgeLabel(false),
        pathBadge = cloudDriveRssPathBadgeLabel(),
        runBadge = cloudDriveRssRunBadgeLabel(),
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
            label = cloudDriveRssTitleLabel(),
            value = settingsCloudRssOverviewValue(enabled),
            detail = cloudRssPreview(
                endpointUrl,
                fallback = cloudDriveRssUnconfiguredEndpointLabel(),
                maxLength = CLOUD_RSS_PREVIEW_LIMIT,
            ),
        ),
        SettingsSummaryTile(
            label = rssSubscriptionsTitleLabel(),
            value = settingsCloudRssSubscriptionsValue(subscriptions.size),
            detail = subscriptions.firstOrNull()?.let { rssSubscriptionPreview(it, CLOUD_RSS_PREVIEW_LIMIT) }
                ?: rssSubscriptionPreviewFallbackLabel(),
        ),
        SettingsSummaryTile(
            label = cloudDriveRssPostSyncScanSummaryLabel(),
            value = settingsCloudRssLinkedSourceValue(linkedSourceLabel),
            detail = cloudRssPreview(
                desktopCloudRssStatusText(schedulerStatus),
                fallback = cloudDriveRssSchedulerIdleLabel(),
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
    val separator = cloudDriveRssPathPairSeparator()
    val safeMaxLength = maxLength.coerceAtLeast(separator.length + 8)
    val available = safeMaxLength - separator.length
    val inboxLength = available / 2
    val libraryLength = available - inboxLength
    return cloudRssPreview(inboxPath, fallback = cloudDriveRssInboxPathFieldLabel(), maxLength = inboxLength) +
        separator +
        cloudRssPreview(libraryPath, fallback = cloudDriveRssLibraryPathFieldLabel(), maxLength = libraryLength)
}

internal fun rssSubscriptionPreview(
    subscription: RssSubscriptionInfo,
    maxLength: Int = CLOUD_RSS_WIDE_PREVIEW_LIMIT,
): String {
    val state = rssSubscriptionStateLabel(subscription.enabled)
    val filter = subscription.filterRegex?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    val label = subscription.name.ifBlank { rssSubscriptionFallbackTitleLabel() }
    return "$state · $label · ${subscription.url}$filter".compactMiddle(maxLength)
}

internal fun desktopCloudRssStatusText(status: String): String =
    localizedCloudRssStatusText(status) ?: status.trim()

@Composable
private fun SettingsSectionMenu(
    selectedSection: MiruPlaySettingsSection,
    sectionFocusRequesters: Map<MiruPlaySettingsSection, FocusRequester>,
    sourcesCount: Int,
    rssCount: Int,
    cloudEnabled: Boolean,
    metadataSummary: String,
    playbackSummary: String,
    onSectionSelected: (MiruPlaySettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(selectedSection) {
        sectionFocusRequesters[selectedSection]?.requestFocus()
    }

    TvPanel(
        modifier
            .desktopNavigationIntentHandler { intent ->
                settingsSectionNavigationTarget(selectedSection, intent)
                    ?.let(onSectionSelected) != null
            }
            .focusable(),
    ) {
        Text(settingsMenuPanelTitle(), color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(settingsMenuPanelDescription(), color = TextSecondary, fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        desktopSettingsSectionOrder.forEach { section ->
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

internal fun settingsSectionNavigationTarget(
    current: MiruPlaySettingsSection,
    key: Key,
): MiruPlaySettingsSection? =
    key.toMiruPlayInputIntent()?.let { intent ->
        settingsSectionNavigationTarget(current, intent)
    }

internal fun settingsSectionNavigationTarget(
    current: MiruPlaySettingsSection,
    intent: MiruPlayInputIntent,
): MiruPlaySettingsSection? =
    intent.verticalNavigationDelta()?.let(current::stepDesktopSettingsSection)

@Composable
private fun SettingsSectionMenuRow(
    section: MiruPlaySettingsSection,
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
            .onPreviewKeyEvent { event ->
                settingsSectionMenuRowKeyEvent(
                    key = event.key,
                    type = event.type,
                    onSelected = onClick,
                )
            }
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                section.desktopTitle,
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

internal fun settingsSectionMenuRowKeyEvent(
    key: Key,
    type: KeyEventType,
    onSelected: () -> Unit,
): Boolean =
    desktopConfirmOrNavigationKeyEvent(
        key = key,
        type = type,
        onClick = onSelected,
    )

@Composable
private fun SettingsSummaryContent(
    section: MiruPlaySettingsSection,
    tiles: List<SettingsSummaryTile>,
    status: String,
    actions: List<SettingsQuickAction>,
    onFocusSectionMenu: () -> Unit,
    modifier: Modifier = Modifier,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    val actionFocusRequesters = remember(actions.size) {
        List(actions.size) { FocusRequester() }
    }
    fun moveActionFocus(currentIndex: Int, key: Key): Boolean {
        return when (val target = settingsQuickActionFocusTarget(
            currentIndex = currentIndex,
            actionCount = actions.size,
            key = key,
            enabledActions = actions.map { it.enabled },
        )) {
            is SettingsQuickActionFocusTarget.Action -> {
                actionFocusRequesters[target.index].requestFocus()
                true
            }
            SettingsQuickActionFocusTarget.SectionMenu -> {
                onFocusSectionMenu()
                true
            }
            null -> false
        }
    }
    TvPanel(modifier.fillMaxWidth()) {
        Text(section.desktopTitle, color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(section.desktopDescription, color = TextSecondary, fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Column(verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
            tiles.chunked(3).forEach { row ->
                SettingsSummaryTileRow(row)
            }
        }
        extraContent()
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        StatusBox(status)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
            actions.forEachIndexed { index, action ->
                TvActionButton(
                    action.label,
                    onClick = action.onClick,
                    secondary = index != 0,
                    enabled = action.enabled,
                    modifier = Modifier
                        .focusRequester(actionFocusRequesters[index])
                        .desktopNavigationKeyHandler { key -> moveActionFocus(index, key) },
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
            label = settingsSourceTileLabel(),
            value = settingsCountValue(sources.size),
            detail = settingsSourceTypeBreakdown(sources),
        ),
        SettingsSummaryTile(
            label = settingsActiveSourceTileLabel(),
            value = activeSourceLabel,
            detail = settingsActiveSourceSharedDetail(),
        ),
        SettingsSummaryTile(
            label = settingsPosterWallIndexTileLabel(),
            value = settingsRecordCountValue(indexedItemCount),
            detail = settingsPosterWallIndexDetail(),
        ),
    )

internal fun playbackSettingsTiles(
    playbackSummary: String,
    recentCount: Int,
    selectedMediaTitle: String,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = settingsPlaybackModeTileLabel(),
            value = playbackSummary,
            detail = settingsPlaybackPageDetail(),
        ),
        SettingsSummaryTile(
            label = settingsRecentPlaybackTileLabel(),
            value = settingsRecordCountValue(recentCount),
            detail = settingsRecentPlaybackDetail(),
        ),
        SettingsSummaryTile(
            label = settingsSelectedMediaTileLabel(),
            value = selectedMediaTitle,
            detail = settingsSelectedMediaDetail(),
        ),
    )

internal fun desktopPlaybackSettingsStatus(): String =
    settingsPlaybackStatusMessage()

internal fun desktopMetadataSettingsStatus(bangumiTokenConfigured: Boolean = false): String =
    metadataBangumiTokenSettingsStatus(bangumiTokenConfigured)

internal fun scanSettingsTiles(
    indexedItemCount: Int,
    linkedSourceLabel: String,
    libraryStatus: String,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = settingsIndexTileLabel(),
            value = settingsRecordCountValue(indexedItemCount),
            detail = settingsIndexSharedDetail(),
        ),
        SettingsSummaryTile(
            label = settingsPostSyncSourceTileLabel(),
            value = linkedSourceLabel,
            detail = settingsCloudDriveRescanSourceDetail(),
        ),
        SettingsSummaryTile(
            label = settingsRecentScanStatusTileLabel(),
            value = desktopLibraryStatusText(libraryStatus),
            detail = settingsRecentScanStatusDetail(),
        ),
    )

internal fun metadataSettingsTiles(
    selectedMediaTitle: String,
    metadataSummary: String,
    indexedItemCount: Int,
    bangumiTokenConfigured: Boolean = false,
): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = settingsSelectedMetadataEntryTileLabel(),
            value = selectedMediaTitle,
            detail = settingsSelectedMetadataEntryDetail(),
        ),
        SettingsSummaryTile(
            label = settingsMetadataMatchStatusTileLabel(),
            value = metadataSummary,
            detail = settingsMetadataMatchStatusDetail(),
        ),
        SettingsSummaryTile(
            label = settingsMetadataCandidateScopeTileLabel(),
            value = settingsIndexedCountValue(indexedItemCount),
            detail = settingsMetadataCandidateScopeDetail(),
        ),
        SettingsSummaryTile(
            label = metadataBangumiTokenTileLabel(),
            value = settingsSavedStateValue(bangumiTokenConfigured),
            detail = metadataBangumiTokenTileDetail(),
        ),
    )

private fun desktopWebUiSettingsTiles(): List<SettingsSummaryTile> =
    listOf(
        SettingsSummaryTile(
            label = settingsWebUiTileLabel(),
            value = settingsWebUiAndroidTvValue(),
            detail = settingsWebUiTileDetail(),
        ),
        SettingsSummaryTile(
            label = settingsWebUiNativeControlTileLabel(),
            value = settingsDesktopControlTileValue(),
            detail = settingsDesktopControlTileDetail(),
        ),
        SettingsSummaryTile(
            label = settingsRemoteAutomationTileLabel(),
            value = settingsRemoteAutomationTileValue(),
            detail = settingsRemoteAutomationTileDetail(),
        ),
    )

internal fun desktopActiveSourceLabel(source: MediaSourceInfo?): String =
    settingsActiveSourceLabel(source)

internal fun desktopLinkedSourceLabel(
    sources: List<MediaSourceInfo>,
    sourceId: Long?,
): String = settingsLinkedSourceLabel(sources, sourceId)

private fun MiruPlaySettingsSection.menuSummary(
    sourcesCount: Int,
    rssCount: Int,
    cloudEnabled: Boolean,
    metadataSummary: String,
    playbackSummary: String,
): String = when (this) {
    MiruPlaySettingsSection.SOURCES -> settingsSourcesMenuSummary(sourcesCount)
    MiruPlaySettingsSection.PLAYBACK -> playbackSummary
    MiruPlaySettingsSection.CLOUD_DRIVE -> settingsCloudDriveMenuSummary(cloudEnabled, rssCount)
    MiruPlaySettingsSection.SCAN -> settingsDesktopScanMenuSummary()
    MiruPlaySettingsSection.METADATA -> metadataSummary
    MiruPlaySettingsSection.WEB_UI -> settingsDesktopWebUiMenuSummary()
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
        modifier = modifier,
        onNavigationKey = { key ->
            key.isCloudRssVerticalKey() && onNavigate(key)
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

private fun Key.isCloudRssVerticalKey(): Boolean =
    toMiruPlayInputIntent()?.verticalNavigationDelta() != null

internal fun List<RssSubscriptionInfo>.rssSubscriptionNavigationTarget(
    currentSubscriptionId: Long?,
    key: Key,
): RssSubscriptionInfo? =
    key.toMiruPlayInputIntent()?.let { intent ->
        rssSubscriptionNavigationTarget(currentSubscriptionId, intent)
    }

internal fun List<RssSubscriptionInfo>.rssSubscriptionNavigationTarget(
    currentSubscriptionId: Long?,
    intent: MiruPlayInputIntent,
): RssSubscriptionInfo? {
    if (isEmpty()) return null
    val delta = intent.verticalNavigationDelta() ?: return null
    val currentIndex = currentSubscriptionId
        ?.let { id -> indexOfFirst { subscription -> subscription.id == id } }
        ?.takeIf { it >= 0 }
        ?: when {
            delta > 0 -> -1
            delta < 0 -> size
            else -> return null
        }
    val targetIndex = currentIndex + delta
    return getOrNull(targetIndex)
}
