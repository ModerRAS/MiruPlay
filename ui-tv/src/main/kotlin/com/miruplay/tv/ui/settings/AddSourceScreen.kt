package com.miruplay.tv.ui.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.data.preferences.PlaybackEndAction
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.MiruPlaySettingsSection
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.androidTvSettingsSectionOrder
import com.miruplay.tv.model.connectionDisplayName
import com.miruplay.tv.model.connectionUsername
import com.miruplay.tv.model.defaultSourceName
import com.miruplay.tv.model.sourceLocation
import com.miruplay.tv.model.tvDisplayName
import com.miruplay.tv.model.tvDisplayStatusLabel
import com.miruplay.tv.model.tvLabel
import com.miruplay.tv.model.tvLocationLabel
import com.miruplay.tv.model.tvSourceHint
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.TvButton
import com.miruplay.tv.ui.components.TvTextField
import com.miruplay.tv.ui.theme.AccentBlue
import com.miruplay.tv.ui.theme.AnimeRed
import com.miruplay.tv.ui.theme.CardBg
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.FocusBorder
import com.miruplay.tv.ui.theme.ProgressGreen
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography
import com.miruplay.tv.ui.theme.WarningYellow
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DEFAULT_LOCAL_PATH = "/storage/emulated/0/Download"
private const val QR_CODE_MATRIX_SIZE = 96

private fun MiruPlaySettingsSection.androidTvIcon(): ImageVector =
    when (this) {
        MiruPlaySettingsSection.WEB_UI -> Icons.Filled.WifiTethering
        MiruPlaySettingsSection.SOURCES -> Icons.Filled.Storage
        MiruPlaySettingsSection.PLAYBACK -> Icons.Filled.PlayArrow
        MiruPlaySettingsSection.CLOUD_DRIVE -> Icons.Filled.Cloud
        MiruPlaySettingsSection.SCAN -> Icons.Filled.Refresh
        MiruPlaySettingsSection.METADATA -> Icons.Filled.Key
    }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AddSourceScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val savedToken by viewModel.bangumiToken.collectAsStateWithLifecycle()
    val autoScanEnabled by viewModel.autoScanEnabled.collectAsStateWithLifecycle()
    val autoScanIntervalHours by viewModel.autoScanIntervalHours.collectAsStateWithLifecycle()
    val lastScanAt by viewModel.lastScanAt.collectAsStateWithLifecycle()
    val mergeSameAnimeEnabled by viewModel.mergeSameAnimeEnabled.collectAsStateWithLifecycle()
    val playbackEndAction by viewModel.playbackEndAction.collectAsStateWithLifecycle()
    val webUiUrls by viewModel.webUiUrls.collectAsStateWithLifecycle()
    val webControlEnabled by viewModel.webControlEnabled.collectAsStateWithLifecycle()
    val webControlAccessToken by viewModel.webControlAccessToken.collectAsStateWithLifecycle()
    val cloudDriveConfig by viewModel.cloudDriveConfig.collectAsStateWithLifecycle()
    val rssSubscriptions by viewModel.rssSubscriptions.collectAsStateWithLifecycle()
    val cloudDriveTokenConfigured by viewModel.cloudDriveTokenConfigured.collectAsStateWithLifecycle()
    val cloudDriveBusy by viewModel.cloudDriveBusy.collectAsStateWithLifecycle()
    val cloudDriveActionMessage by viewModel.cloudDriveActionMessage.collectAsStateWithLifecycle()
    val cloudDriveDirectoryBrowser by viewModel.cloudDriveDirectoryBrowser.collectAsStateWithLifecycle()
    val localDirectoryBrowser by viewModel.localDirectoryBrowser.collectAsStateWithLifecycle()

    var selectedSection by remember { mutableStateOf(MiruPlaySettingsSection.WEB_UI) }
    var editingSourceId by remember { mutableStateOf<Long?>(null) }
    var selectedType by remember { mutableStateOf(MediaSourceType.LOCAL) }
    var name by remember { mutableStateOf(sourceNameOrDefault("", MediaSourceType.LOCAL)) }
    var location by remember { mutableStateOf(DEFAULT_LOCAL_PATH) }
    var locationDisplayName by remember { mutableStateOf("Download") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var tokenSaved by remember { mutableStateOf(false) }
    var selectedWebUiUrl by remember { mutableStateOf("") }
    var cloudEndpoint by remember { mutableStateOf("") }
    var cloudUsername by remember { mutableStateOf("") }
    var cloudPassword by remember { mutableStateOf("") }
    var cloudApiToken by remember { mutableStateOf("") }
    var cloudInboxPath by remember { mutableStateOf("") }
    var cloudLibraryPath by remember { mutableStateOf("") }
    var cloudIntervalMinutes by remember { mutableStateOf("30") }
    var cloudEnabled by remember { mutableStateOf(false) }
    var cloudWebDavSourceId by remember { mutableStateOf<Long?>(null) }
    var rssProxyEnabled by remember { mutableStateOf(false) }
    var rssProxyHost by remember { mutableStateOf("") }
    var rssProxyPort by remember { mutableStateOf("1080") }
    var rssName by remember { mutableStateOf("") }
    var rssUrl by remember { mutableStateOf("") }
    var rssFilterRegex by remember { mutableStateOf("") }
    var rssEnabled by remember { mutableStateOf(true) }
    var pendingDeletedSourceId by remember { mutableStateOf<Long?>(null) }

    val menuFocusRequesters = remember {
        androidTvSettingsSectionOrder.associateWith { FocusRequester() }
    }
    val firstMenuFocusRequester = menuFocusRequesters.getValue(MiruPlaySettingsSection.WEB_UI)

    LaunchedEffect(Unit) {
        firstMenuFocusRequester.requestFocus()
    }

    LaunchedEffect(tokenSaved) {
        if (tokenSaved) {
            delay(1800)
            tokenSaved = false
        }
    }

    LaunchedEffect(webUiUrls) {
        if (selectedWebUiUrl !in webUiUrls) {
            selectedWebUiUrl = webUiUrls.firstOrNull().orEmpty()
        }
    }

    LaunchedEffect(cloudDriveConfig) {
        cloudEndpoint = cloudDriveConfig.endpointUrl
        cloudUsername = cloudDriveConfig.username
        cloudInboxPath = cloudDriveConfig.inboxPath
        cloudLibraryPath = cloudDriveConfig.libraryPath
        cloudIntervalMinutes = cloudDriveConfig.intervalMinutes.toString()
        cloudEnabled = cloudDriveConfig.enabled
        cloudWebDavSourceId = cloudDriveConfig.webDavSourceId
        rssProxyEnabled = cloudDriveConfig.rssProxyEnabled
        rssProxyHost = cloudDriveConfig.rssProxyHost
        rssProxyPort = cloudDriveConfig.rssProxyPort.toString()
    }

    fun resetSourceForm(type: MediaSourceType = selectedType) {
        editingSourceId = null
        selectedType = type
        name = sourceNameOrDefault("", type)
        location = defaultLocationFor(type)
        locationDisplayName = if (type == MediaSourceType.LOCAL) "Download" else ""
        username = ""
        password = ""
        viewModel.clearTestResult()
    }

    LaunchedEffect(sources, pendingDeletedSourceId) {
        val deletedSourceId = pendingDeletedSourceId ?: return@LaunchedEffect
        if (sources.none { it.id == deletedSourceId }) {
            selectedSection = MiruPlaySettingsSection.SOURCES
            resetSourceForm()
            menuFocusRequesters.getValue(MiruPlaySettingsSection.SOURCES).requestFocus()
            pendingDeletedSourceId = null
        }
    }

    fun loadSourceForEdit(source: MediaSourceInfo) {
        editingSourceId = source.id
        selectedSection = MiruPlaySettingsSection.SOURCES
        selectedType = source.type
        name = source.name.ifBlank { sourceNameOrDefault("", source.type) }
        location = source.sourceLocation().orEmpty()
        locationDisplayName = source.connectionDisplayName().ifBlank {
            if (source.type == MediaSourceType.LOCAL) displayNameForLocation(location) else ""
        }
        username = source.connectionUsername()
        password = ""
        viewModel.clearTestResult()
    }

    fun saveSourceForm() {
        val source = MediaSourceInfo(
            id = editingSourceId ?: 0L,
            name = sourceNameOrDefault(name, selectedType),
            type = selectedType,
            connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
                type = selectedType,
                location = location,
                displayName = locationDisplayName,
                username = username,
                password = password
            )
        )
        if (editingSourceId == null) {
            viewModel.addSource(source)
        } else {
            viewModel.updateSource(source)
        }
        resetSourceForm()
    }

    OverscanContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(onNavigateBack = onNavigateBack)

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                SettingsMenuPanel(
                    selectedSection = selectedSection,
                    sourcesCount = sources.size,
                    webUiAddressCount = webUiUrls.size,
                    autoScanEnabled = autoScanEnabled,
                    mergeSameAnimeEnabled = mergeSameAnimeEnabled,
                    playbackEndAction = playbackEndAction,
                    cloudDriveEnabled = cloudEnabled,
                    rssCount = rssSubscriptions.size,
                    hasToken = savedToken.isNotBlank() || tokenSaved,
                    menuFocusRequesters = menuFocusRequesters,
                    onSectionSelected = { selectedSection = it },
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                )

                SettingsContent(
                    selectedSection = selectedSection,
                    sources = sources,
                    selectedSourceId = editingSourceId,
                    onSelectSource = ::loadSourceForEdit,
                    onDeleteSource = { sourceId ->
                        pendingDeletedSourceId = sourceId
                        selectedSection = MiruPlaySettingsSection.SOURCES
                        viewModel.removeSource(sourceId)
                        if (editingSourceId == sourceId) {
                            resetSourceForm()
                        }
                    },
                    menuFocusRequester = menuFocusRequesters.getValue(selectedSection),
                    selectedType = selectedType,
                    onTypeSelected = { type ->
                        if (type != selectedType) {
                            editingSourceId = null
                            selectedType = type
                            name = sourceNameOrDefault("", type)
                            location = defaultLocationFor(type)
                            locationDisplayName = if (type == MediaSourceType.LOCAL) "Download" else ""
                            username = ""
                            password = ""
                            viewModel.clearTestResult()
                        }
                    },
                    name = name,
                    onNameChange = { name = it },
                    location = location,
                    onLocationChange = { location = it },
                    locationDisplayName = locationDisplayName,
                    onPickLocalFolder = {
                        viewModel.openLocalDirectoryPicker(location)
                    },
                    username = username,
                    onUsernameChange = { username = it },
                    password = password,
                    onPasswordChange = { password = it },
                    testResult = testResult,
                    isEditingSource = editingSourceId != null,
                    onNewSource = { resetSourceForm() },
                    onTestConnection = {
                        viewModel.testConnection(selectedType, location, username, password)
                    },
                    onSaveSource = ::saveSourceForm,
                    autoScanEnabled = autoScanEnabled,
                    autoScanIntervalHours = autoScanIntervalHours,
                    lastScanAt = lastScanAt,
                    onToggleAutoScan = { viewModel.setAutoScanEnabled(!autoScanEnabled) },
                    onIntervalSelected = viewModel::setAutoScanIntervalHours,
                    mergeSameAnimeEnabled = mergeSameAnimeEnabled,
                    onToggleMergeSameAnime = {
                        viewModel.setMergeSameAnimeEnabled(!mergeSameAnimeEnabled)
                    },
                    playbackEndAction = playbackEndAction,
                    onPlaybackEndActionSelected = viewModel::setPlaybackEndAction,
                    savedToken = savedToken,
                    tokenInput = tokenInput,
                    tokenSaved = tokenSaved,
                    onTokenChange = { tokenInput = it },
                    onSaveToken = {
                        val token = tokenInput.trim()
                        if (token.isNotBlank()) {
                            viewModel.saveBangumiToken(token)
                            tokenInput = ""
                            tokenSaved = true
                        }
                    },
                    onClearToken = {
                        viewModel.clearBangumiToken()
                        tokenInput = ""
                        tokenSaved = false
                    },
                    webUiUrls = webUiUrls,
                    webControlEnabled = webControlEnabled,
                    webControlAccessToken = webControlAccessToken,
                    selectedWebUiUrl = selectedWebUiUrl,
                    onWebUiUrlSelected = { selectedWebUiUrl = it },
                    onToggleWebControl = {
                        viewModel.setWebControlEnabled(!webControlEnabled)
                    },
                    onRotateWebControlToken = viewModel::rotateWebControlAccessToken,
                    onRefreshWebUiUrls = viewModel::refreshWebUiUrls,
                    cloudEndpoint = cloudEndpoint,
                    onCloudEndpointChange = { cloudEndpoint = it },
                    cloudUsername = cloudUsername,
                    onCloudUsernameChange = { cloudUsername = it },
                    cloudPassword = cloudPassword,
                    onCloudPasswordChange = { cloudPassword = it },
                    cloudApiToken = cloudApiToken,
                    onCloudApiTokenChange = { cloudApiToken = it },
                    cloudInboxPath = cloudInboxPath,
                    onCloudInboxPathChange = { cloudInboxPath = it },
                    cloudLibraryPath = cloudLibraryPath,
                    onCloudLibraryPathChange = { cloudLibraryPath = it },
                    cloudIntervalMinutes = cloudIntervalMinutes,
                    onCloudIntervalMinutesChange = { cloudIntervalMinutes = it.filter(Char::isDigit).take(4) },
                    cloudEnabled = cloudEnabled,
                    onToggleCloudEnabled = { cloudEnabled = !cloudEnabled },
                    cloudWebDavSourceId = cloudWebDavSourceId,
                    onCloudWebDavSourceSelected = { cloudWebDavSourceId = it },
                    cloudDriveTokenConfigured = cloudDriveTokenConfigured,
                    cloudDriveBusy = cloudDriveBusy,
                    cloudDriveActionMessage = cloudDriveActionMessage,
                    canPickCloudDriveDirectory = cloudEndpoint.isNotBlank() && cloudDriveTokenConfigured,
                    onPickCloudInboxPath = {
                        viewModel.openCloudDriveDirectoryPicker(
                            CloudDriveDirectoryTarget.INBOX,
                            cloudEndpoint,
                            cloudInboxPath
                        )
                    },
                    onPickCloudLibraryPath = {
                        viewModel.openCloudDriveDirectoryPicker(
                            CloudDriveDirectoryTarget.LIBRARY,
                            cloudEndpoint,
                            cloudLibraryPath
                        )
                    },
                    rssProxyEnabled = rssProxyEnabled,
                    onRssProxyEnabledChange = { rssProxyEnabled = it },
                    rssProxyHost = rssProxyHost,
                    onRssProxyHostChange = { rssProxyHost = it },
                    rssProxyPort = rssProxyPort,
                    onRssProxyPortChange = { rssProxyPort = it.filter(Char::isDigit).take(5) },
                    rssSubscriptions = rssSubscriptions,
                    rssName = rssName,
                    onRssNameChange = { rssName = it },
                    rssUrl = rssUrl,
                    onRssUrlChange = { rssUrl = it },
                    rssFilterRegex = rssFilterRegex,
                    onRssFilterRegexChange = { rssFilterRegex = it },
                    rssEnabled = rssEnabled,
                    onToggleRssEnabled = { rssEnabled = !rssEnabled },
                    onSaveCloudConfig = {
                        viewModel.saveCloudDriveConfig(
                            endpointUrl = cloudEndpoint,
                            username = cloudUsername,
                            webDavSourceId = cloudWebDavSourceId,
                            inboxPath = cloudInboxPath,
                            libraryPath = cloudLibraryPath,
                            intervalMinutes = cloudIntervalMinutes.toIntOrNull() ?: 30,
                            enabled = cloudEnabled,
                            rssProxyEnabled = rssProxyEnabled,
                            rssProxyHost = rssProxyHost,
                            rssProxyPort = rssProxyPort.toIntOrNull() ?: 1080
                        )
                    },
                    onLoginCloudDrive = {
                        viewModel.loginCloudDrive(cloudEndpoint, cloudUsername, cloudPassword)
                        cloudPassword = ""
                    },
                    onSaveCloudDriveApiToken = {
                        viewModel.saveCloudDriveApiToken(cloudEndpoint, cloudApiToken)
                        cloudApiToken = ""
                    },
                    onRunCloudDriveNow = viewModel::runCloudDriveNow,
                    onAddRssSubscription = {
                        viewModel.addRssSubscription(rssName, rssUrl, rssFilterRegex, rssEnabled)
                        if (rssUrl.isNotBlank()) {
                            rssName = ""
                            rssUrl = ""
                            rssFilterRegex = ""
                            rssEnabled = true
                        }
                    },
                    onToggleRssSubscription = viewModel::setRssSubscriptionEnabled,
                    onDeleteRssSubscription = viewModel::deleteRssSubscription,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            if (cloudDriveDirectoryBrowser.open) {
                CloudDriveDirectoryPickerDialog(
                    state = cloudDriveDirectoryBrowser,
                    onDismiss = viewModel::closeCloudDriveDirectoryPicker,
                    onNavigate = viewModel::browseCloudDriveDirectory,
                    onSelectCurrent = {
                        when (cloudDriveDirectoryBrowser.target) {
                            CloudDriveDirectoryTarget.INBOX -> cloudInboxPath = it
                            CloudDriveDirectoryTarget.LIBRARY -> cloudLibraryPath = it
                        }
                        viewModel.closeCloudDriveDirectoryPicker()
                    }
                )
            }

            if (localDirectoryBrowser.open) {
                LocalDirectoryPickerDialog(
                    state = localDirectoryBrowser,
                    onDismiss = viewModel::closeLocalDirectoryPicker,
                    onNavigate = viewModel::browseLocalDirectory,
                    onSelectCurrent = {
                        location = it
                        locationDisplayName = displayNameForLocalPath(it)
                        if (name.isBlank() || name == "本地下载") {
                            name = locationDisplayName.ifBlank { "本地媒体库" }
                        }
                        viewModel.clearTestResult()
                        viewModel.closeLocalDirectoryPicker()
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "设置",
                style = TvTypography.title,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "管理媒体源、WebUI 和元数据服务",
                style = TvTypography.body,
                color = TextSecondary
            )
        }
        TvButton(text = "返回", onClick = onNavigateBack)
    }
}

@Composable
private fun SettingsMenuPanel(
    selectedSection: MiruPlaySettingsSection,
    sourcesCount: Int,
    webUiAddressCount: Int,
    autoScanEnabled: Boolean,
    mergeSameAnimeEnabled: Boolean,
    playbackEndAction: PlaybackEndAction,
    cloudDriveEnabled: Boolean,
    rssCount: Int,
    hasToken: Boolean,
    menuFocusRequesters: Map<MiruPlaySettingsSection, FocusRequester>,
    onSectionSelected: (MiruPlaySettingsSection) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsPanel(modifier = modifier) {
        Text(
            text = "设置菜单",
            style = TvTypography.subtitle,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "按上下切换分类，向右进入当前设置。",
            style = TvTypography.caption,
            color = TextSecondary
        )
        Spacer(Modifier.height(18.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(androidTvSettingsSectionOrder) { index, section ->
                val summary = when (section) {
                    MiruPlaySettingsSection.WEB_UI -> if (webUiAddressCount > 0) "${webUiAddressCount} 个地址" else "等待网络"
                    MiruPlaySettingsSection.SOURCES -> "${sourcesCount} 个源"
                    MiruPlaySettingsSection.PLAYBACK -> playbackEndAction.menuSummary()
                    MiruPlaySettingsSection.CLOUD_DRIVE -> if (cloudDriveEnabled) "${rssCount} 个订阅" else "未启用"
                    MiruPlaySettingsSection.SCAN -> when {
                        autoScanEnabled && mergeSameAnimeEnabled -> "定时 · 合并"
                        autoScanEnabled -> "定时已开"
                        mergeSameAnimeEnabled -> "同番合并"
                        else -> "定时关闭"
                    }
                    MiruPlaySettingsSection.METADATA -> if (hasToken) "Token 已设置" else "未设置"
                }
                SettingsMenuItem(
                    section = section,
                    summary = summary,
                    selected = section == selectedSection,
                    onClick = { onSectionSelected(section) },
                    modifier = Modifier.focusRequester(menuFocusRequesters.getValue(section))
                )
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(
    section: MiruPlaySettingsSection,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isFocused -> FocusBorder
        selected -> AnimeRed
        else -> Color.White.copy(alpha = 0.12f)
    }
    val background = when {
        isFocused -> AccentBlue
        selected -> AnimeRed.copy(alpha = 0.18f)
        else -> DarkSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(if (selected || isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { state ->
                if (state.isFocused) onClick()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = section.androidTvIcon(),
            contentDescription = null,
            tint = if (selected) AnimeRed else TextSecondary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.androidTvTitle,
                style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = summary,
                style = TvTypography.caption,
                color = if (selected) TextPrimary.copy(alpha = 0.78f) else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsContent(
    selectedSection: MiruPlaySettingsSection,
    sources: List<MediaSourceInfo>,
    selectedSourceId: Long?,
    onSelectSource: (MediaSourceInfo) -> Unit,
    onDeleteSource: (Long) -> Unit,
    menuFocusRequester: FocusRequester,
    selectedType: MediaSourceType,
    onTypeSelected: (MediaSourceType) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    locationDisplayName: String,
    onPickLocalFolder: () -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    testResult: ConnectionTestResult?,
    isEditingSource: Boolean,
    onNewSource: () -> Unit,
    onTestConnection: () -> Unit,
    onSaveSource: () -> Unit,
    autoScanEnabled: Boolean,
    autoScanIntervalHours: Int,
    lastScanAt: Long,
    onToggleAutoScan: () -> Unit,
    onIntervalSelected: (Int) -> Unit,
    mergeSameAnimeEnabled: Boolean,
    onToggleMergeSameAnime: () -> Unit,
    playbackEndAction: PlaybackEndAction,
    onPlaybackEndActionSelected: (PlaybackEndAction) -> Unit,
    savedToken: String,
    tokenInput: String,
    tokenSaved: Boolean,
    onTokenChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
    webUiUrls: List<String>,
    webControlEnabled: Boolean,
    webControlAccessToken: String,
    selectedWebUiUrl: String,
    onWebUiUrlSelected: (String) -> Unit,
    onToggleWebControl: () -> Unit,
    onRotateWebControlToken: () -> Unit,
    onRefreshWebUiUrls: () -> Unit,
    cloudEndpoint: String,
    onCloudEndpointChange: (String) -> Unit,
    cloudUsername: String,
    onCloudUsernameChange: (String) -> Unit,
    cloudPassword: String,
    onCloudPasswordChange: (String) -> Unit,
    cloudApiToken: String,
    onCloudApiTokenChange: (String) -> Unit,
    cloudInboxPath: String,
    onCloudInboxPathChange: (String) -> Unit,
    cloudLibraryPath: String,
    onCloudLibraryPathChange: (String) -> Unit,
    cloudIntervalMinutes: String,
    onCloudIntervalMinutesChange: (String) -> Unit,
    cloudEnabled: Boolean,
    onToggleCloudEnabled: () -> Unit,
    cloudWebDavSourceId: Long?,
    onCloudWebDavSourceSelected: (Long?) -> Unit,
    cloudDriveTokenConfigured: Boolean,
    cloudDriveBusy: Boolean,
    cloudDriveActionMessage: String?,
    canPickCloudDriveDirectory: Boolean,
    onPickCloudInboxPath: () -> Unit,
    onPickCloudLibraryPath: () -> Unit,
    rssProxyEnabled: Boolean,
    onRssProxyEnabledChange: (Boolean) -> Unit,
    rssProxyHost: String,
    onRssProxyHostChange: (String) -> Unit,
    rssProxyPort: String,
    onRssProxyPortChange: (String) -> Unit,
    rssSubscriptions: List<RssSubscriptionInfo>,
    rssName: String,
    onRssNameChange: (String) -> Unit,
    rssUrl: String,
    onRssUrlChange: (String) -> Unit,
    rssFilterRegex: String,
    onRssFilterRegexChange: (String) -> Unit,
    rssEnabled: Boolean,
    onToggleRssEnabled: () -> Unit,
    onSaveCloudConfig: () -> Unit,
    onLoginCloudDrive: () -> Unit,
    onSaveCloudDriveApiToken: () -> Unit,
    onRunCloudDriveNow: () -> Unit,
    onAddRssSubscription: () -> Unit,
    onToggleRssSubscription: (RssSubscriptionInfo, Boolean) -> Unit,
    onDeleteRssSubscription: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    when (selectedSection) {
        MiruPlaySettingsSection.WEB_UI -> SettingsSingleSectionPage(
            section = selectedSection,
            modifier = modifier
        ) {
            WebUiPanel(
                urls = webUiUrls,
                enabled = webControlEnabled,
                accessToken = webControlAccessToken,
                selectedUrl = selectedWebUiUrl,
                onUrlSelected = onWebUiUrlSelected,
                onToggleEnabled = onToggleWebControl,
                onRotateToken = onRotateWebControlToken,
                onRefresh = onRefreshWebUiUrls
            )
        }

        MiruPlaySettingsSection.SOURCES -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SourceListPanel(
                sources = sources,
                selectedSourceId = selectedSourceId,
                onSelect = onSelectSource,
                onDelete = onDeleteSource,
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxHeight()
                    .focusProperties { left = menuFocusRequester }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            menuFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
            )
            Column(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                SettingsSectionHeader(section = selectedSection)
                SourceFormPanel(
                    selectedType = selectedType,
                    onTypeSelected = onTypeSelected,
                    name = name,
                    onNameChange = onNameChange,
                    location = location,
                    onLocationChange = onLocationChange,
                    locationDisplayName = locationDisplayName,
                    onPickLocalFolder = onPickLocalFolder,
                    username = username,
                    onUsernameChange = onUsernameChange,
                    password = password,
                    onPasswordChange = onPasswordChange,
                    testResult = testResult,
                    isEditing = isEditingSource,
                    onNewSource = onNewSource,
                    onTestConnection = onTestConnection,
                    onSave = onSaveSource
                )
            }
        }

        MiruPlaySettingsSection.CLOUD_DRIVE -> SettingsSingleSectionPage(
            section = selectedSection,
            modifier = modifier
        ) {
            CloudDriveAutomationPanel(
                sources = sources,
                endpoint = cloudEndpoint,
                onEndpointChange = onCloudEndpointChange,
                username = cloudUsername,
                onUsernameChange = onCloudUsernameChange,
                password = cloudPassword,
                onPasswordChange = onCloudPasswordChange,
                apiToken = cloudApiToken,
                onApiTokenChange = onCloudApiTokenChange,
                inboxPath = cloudInboxPath,
                onInboxPathChange = onCloudInboxPathChange,
                libraryPath = cloudLibraryPath,
                onLibraryPathChange = onCloudLibraryPathChange,
                intervalMinutes = cloudIntervalMinutes,
                onIntervalMinutesChange = onCloudIntervalMinutesChange,
                enabled = cloudEnabled,
                onToggleEnabled = onToggleCloudEnabled,
                selectedWebDavSourceId = cloudWebDavSourceId,
                onWebDavSourceSelected = onCloudWebDavSourceSelected,
                tokenConfigured = cloudDriveTokenConfigured,
                busy = cloudDriveBusy,
                actionMessage = cloudDriveActionMessage,
                canPickCloudDriveDirectory = canPickCloudDriveDirectory,
                onPickCloudInboxPath = onPickCloudInboxPath,
                onPickCloudLibraryPath = onPickCloudLibraryPath,
                rssProxyEnabled = rssProxyEnabled,
                onRssProxyEnabledChange = onRssProxyEnabledChange,
                rssProxyHost = rssProxyHost,
                onRssProxyHostChange = onRssProxyHostChange,
                rssProxyPort = rssProxyPort,
                onRssProxyPortChange = onRssProxyPortChange,
                onSave = onSaveCloudConfig,
                onLogin = onLoginCloudDrive,
                onSaveApiToken = onSaveCloudDriveApiToken,
                onRunNow = onRunCloudDriveNow
            )
            RssSubscriptionsPanel(
                subscriptions = rssSubscriptions,
                name = rssName,
                onNameChange = onRssNameChange,
                url = rssUrl,
                onUrlChange = onRssUrlChange,
                filterRegex = rssFilterRegex,
                onFilterRegexChange = onRssFilterRegexChange,
                enabled = rssEnabled,
                onToggleEnabled = onToggleRssEnabled,
                onAdd = onAddRssSubscription,
                onToggleSubscription = onToggleRssSubscription,
                onDelete = onDeleteRssSubscription
            )
        }

        MiruPlaySettingsSection.SCAN -> SettingsSingleSectionPage(
            section = selectedSection,
            modifier = modifier
        ) {
            ScanPanel(
                autoScanEnabled = autoScanEnabled,
                autoScanIntervalHours = autoScanIntervalHours,
                lastScanAt = lastScanAt,
                onToggleAutoScan = onToggleAutoScan,
                onIntervalSelected = onIntervalSelected,
                mergeSameAnimeEnabled = mergeSameAnimeEnabled,
                onToggleMergeSameAnime = onToggleMergeSameAnime
            )
        }

        MiruPlaySettingsSection.PLAYBACK -> SettingsSingleSectionPage(
            section = selectedSection,
            modifier = modifier
        ) {
            PlaybackPanel(
                endAction = playbackEndAction,
                onEndActionSelected = onPlaybackEndActionSelected
            )
        }

        MiruPlaySettingsSection.METADATA -> SettingsSingleSectionPage(
            section = selectedSection,
            modifier = modifier
        ) {
            MetadataPanel(
                savedToken = savedToken,
                tokenInput = tokenInput,
                tokenSaved = tokenSaved,
                onTokenChange = onTokenChange,
                onSaveToken = onSaveToken,
                onClearToken = onClearToken
            )
        }
    }
}

@Composable
private fun SettingsSingleSectionPage(
    section: MiruPlaySettingsSection,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SettingsSectionHeader(section = section)
        content()
    }
}

@Composable
private fun SettingsSectionHeader(section: MiruPlaySettingsSection) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = section.androidTvIcon(),
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = section.androidTvTitle,
                style = TvTypography.title,
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = section.androidTvDescription,
            style = TvTypography.body,
            color = TextSecondary
        )
    }
}

@Composable
private fun SourceListPanel(
    sources: List<MediaSourceInfo>,
    selectedSourceId: Long?,
    onSelect: (MediaSourceInfo) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsPanel(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Storage,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "媒体源", style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (sources.isEmpty()) {
                "还没有配置媒体源"
            } else {
                "已配置 ${sources.size} 个源"
            },
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(18.dp))

        if (sources.isEmpty()) {
            EmptySourceHint()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sources, key = { it.id }) { source ->
                    SourceListItem(
                        source = source,
                        selected = source.id == selectedSourceId,
                        onSelect = { onSelect(source) },
                        onDelete = { onDelete(source.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySourceHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "先添加一个本地或网络媒体库",
                style = TvTypography.body,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "MuMu 共享文件夹通常可用默认 Download 路径。",
                style = TvTypography.caption,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SourceListItem(
    source: MediaSourceInfo,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val itemFocusRequester = remember { FocusRequester() }
    val deleteFocusRequester = remember { FocusRequester() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val location = source.sourceLocation().orEmpty()
    val background = when {
        isFocused -> AccentBlue
        selected -> AnimeRed.copy(alpha = 0.18f)
        else -> DarkSurface
    }
    val borderColor = when {
        isFocused -> FocusBorder
        selected -> AnimeRed
        else -> Color.White.copy(alpha = 0.12f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(itemFocusRequester)
            .focusProperties { right = deleteFocusRequester }
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                width = if (isFocused || selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = source.type.sourceIcon(),
            contentDescription = null,
            tint = if (source.isConnected) ProgressGreen else TextSecondary,
            modifier = Modifier.size(30.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.tvDisplayName(fallbackName = source.type.tvLabel()),
                color = TextPrimary,
                style = TvTypography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = source.tvDisplayStatusLabel(),
                color = if (source.isConnected) ProgressGreen else WarningYellow,
                style = TvTypography.caption
            )
            if (location.isNotBlank()) {
                Text(
                    text = location,
                    color = TextSecondary,
                    style = TvTypography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        SourceDeleteButton(
            onClick = onDelete,
            modifier = Modifier
                .focusRequester(deleteFocusRequester)
                .focusProperties { left = itemFocusRequester }
        )
    }
}

@Composable
private fun SourceDeleteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) AnimeRed else AnimeRed.copy(alpha = 0.72f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "删除",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun WebUiPanel(
    urls: List<String>,
    enabled: Boolean,
    accessToken: String,
    selectedUrl: String,
    onUrlSelected: (String) -> Unit,
    onToggleEnabled: () -> Unit,
    onRotateToken: () -> Unit,
    onRefresh: () -> Unit
) {
    val activeUrl = selectedUrl.ifBlank { urls.firstOrNull().orEmpty() }

    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.WifiTethering,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "WebUI 访问", style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "默认关闭。开启后，同一局域网设备需要携带访问令牌才能管理媒体源和遥控播放。",
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = if (enabled) "关闭 WebUI" else "开启 WebUI",
                icon = Icons.Filled.WifiTethering,
                onClick = onToggleEnabled,
                modifier = Modifier.width(156.dp)
            )
            TvButton(
                text = "更换令牌",
                icon = Icons.Filled.Key,
                onClick = onRotateToken,
                enabled = enabled,
                modifier = Modifier.width(150.dp)
            )
            TvButton(
                text = "刷新地址",
                icon = Icons.Filled.Refresh,
                onClick = onRefresh,
                enabled = enabled,
                modifier = Modifier.width(150.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "访问令牌：${accessToken.ifBlank { "未生成" }}",
            style = TvTypography.caption,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!enabled) {
            StatusMessage(
                icon = Icons.Filled.Close,
                text = "WebUI 当前未启用，不会监听局域网端口。",
                color = WarningYellow
            )
        } else if (urls.isEmpty()) {
            StatusMessage(
                icon = Icons.Filled.Refresh,
                text = "暂未检测到局域网地址，请确认电视已连接网络后刷新。",
                color = WarningYellow
            )
        } else {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "可用地址",
                        style = TvTypography.caption,
                        color = TextSecondary
                    )
                    urls.forEachIndexed { index, url ->
                        WebUiMenuItem(
                            url = url,
                            label = if (index == 0) "主地址" else "备用地址",
                            selected = url == activeUrl,
                            onClick = { onUrlSelected(url) }
                        )
                    }
                }

                Column(
                    modifier = Modifier.width(168.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WebUiQrCode(
                        content = activeUrl,
                        modifier = Modifier.size(156.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "扫码打开",
                        style = TvTypography.caption,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun WebUiMenuItem(
    url: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isFocused -> FocusBorder
        selected -> AnimeRed
        else -> Color.White.copy(alpha = 0.12f)
    }
    val background = when {
        selected -> AnimeRed.copy(alpha = 0.18f)
        isFocused -> AccentBlue
        else -> DarkSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(if (selected || isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.WifiTethering,
            contentDescription = null,
            tint = if (selected) AnimeRed else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = TvTypography.caption,
                color = if (selected) AnimeRed else TextSecondary,
                maxLines = 1
            )
            Text(
                text = url,
                style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WebUiQrCode(
    content: String,
    modifier: Modifier = Modifier
) {
    val matrix = remember(content) { createQrCodeMatrix(content) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (matrix != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellSize = minOf(size.width / matrix.width, size.height / matrix.height)
                val qrWidth = cellSize * matrix.width
                val qrHeight = cellSize * matrix.height
                val offsetX = (size.width - qrWidth) / 2f
                val offsetY = (size.height - qrHeight) / 2f

                for (y in 0 until matrix.height) {
                    for (x in 0 until matrix.width) {
                        if (matrix.get(x, y)) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(
                                    x = offsetX + x * cellSize,
                                    y = offsetY + y * cellSize
                                ),
                                size = Size(cellSize, cellSize)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudDriveDirectoryPickerDialog(
    state: CloudDriveDirectoryBrowserState,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onSelectCurrent: (String) -> Unit
) {
    val canSelectCurrent = state.path.isNotBlank() && state.path != "/"

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(760.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (state.target == CloudDriveDirectoryTarget.INBOX) "选择下载目录 A" else "选择整理目录 B",
                    style = TvTypography.subtitle,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                TvButton(
                    text = "关闭",
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onDismiss
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ScanOptionChip(
                    text = "上一级",
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    selected = false,
                    enabled = state.parentPath != null,
                    onClick = { state.parentPath?.let(onNavigate) },
                    modifier = Modifier.width(140.dp)
                )
                Text(
                    text = state.displayPath.ifBlank { "CloudDrive 根目录" },
                    style = TvTypography.body,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.isLoading) {
                Text(text = "正在读取目录...", color = TextSecondary, style = TvTypography.body)
            } else if (state.entries.isEmpty()) {
                Text(text = "没有可进入的子文件夹。", color = TextSecondary, style = TvTypography.body)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    items(state.entries) { entry ->
                        ScanOptionChip(
                            text = entry.name,
                            icon = Icons.Filled.Folder,
                            selected = false,
                            enabled = true,
                            onClick = { onNavigate(entry.path) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (!state.message.isNullOrBlank()) {
                Text(
                    text = state.message,
                    style = TvTypography.body,
                    color = WarningYellow,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = "取消",
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onDismiss
                )
                TvButton(
                    text = "选择当前目录",
                    icon = Icons.Filled.CheckCircle,
                    enabled = canSelectCurrent,
                    onClick = { onSelectCurrent(state.path) }
                )
            }
        }
    }
}

@Composable
private fun LocalDirectoryPickerDialog(
    state: LocalDirectoryBrowserState,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onSelectCurrent: (String) -> Unit
) {
    val canSelectCurrent = state.path.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(760.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "选择本地媒体文件夹",
                    style = TvTypography.subtitle,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                TvButton(
                    text = "关闭",
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onDismiss
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ScanOptionChip(
                    text = "上一级",
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    selected = false,
                    enabled = state.parentPath != null,
                    onClick = { state.parentPath?.let(onNavigate) },
                    modifier = Modifier.width(140.dp)
                )
                Text(
                    text = state.displayPath.ifBlank { "设备存储" },
                    style = TvTypography.body,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.isLoading) {
                Text(text = "正在读取目录...", color = TextSecondary, style = TvTypography.body)
            } else if (state.entries.isEmpty()) {
                Text(text = "没有可进入的子文件夹。", color = TextSecondary, style = TvTypography.body)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    items(state.entries) { entry ->
                        ScanOptionChip(
                            text = entry.name,
                            icon = Icons.Filled.Folder,
                            selected = false,
                            enabled = entry.canRead,
                            onClick = { onNavigate(entry.path) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (!state.message.isNullOrBlank()) {
                Text(
                    text = state.message,
                    style = TvTypography.body,
                    color = WarningYellow,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = "取消",
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onDismiss
                )
                TvButton(
                    text = "选择当前目录",
                    icon = Icons.Filled.CheckCircle,
                    enabled = canSelectCurrent,
                    onClick = { onSelectCurrent(state.path) }
                )
            }
        }
    }
}

@Composable
private fun CloudDriveAutomationPanel(
    sources: List<MediaSourceInfo>,
    endpoint: String,
    onEndpointChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    apiToken: String,
    onApiTokenChange: (String) -> Unit,
    inboxPath: String,
    onInboxPathChange: (String) -> Unit,
    libraryPath: String,
    onLibraryPathChange: (String) -> Unit,
    intervalMinutes: String,
    onIntervalMinutesChange: (String) -> Unit,
    enabled: Boolean,
    onToggleEnabled: () -> Unit,
    selectedWebDavSourceId: Long?,
    onWebDavSourceSelected: (Long?) -> Unit,
    tokenConfigured: Boolean,
    busy: Boolean,
    actionMessage: String?,
    canPickCloudDriveDirectory: Boolean,
    onPickCloudInboxPath: () -> Unit,
    onPickCloudLibraryPath: () -> Unit,
    rssProxyEnabled: Boolean,
    onRssProxyEnabledChange: (Boolean) -> Unit,
    rssProxyHost: String,
    onRssProxyHostChange: (String) -> Unit,
    rssProxyPort: String,
    onRssProxyPortChange: (String) -> Unit,
    onSave: () -> Unit,
    onLogin: () -> Unit,
    onSaveApiToken: () -> Unit,
    onRunNow: () -> Unit
) {
    val webDavSources = sources.filter { it.type == MediaSourceType.WEBDAV }

    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Cloud,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "CloudDrive2", style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "RSS 会提交到 CloudDrive2 离线下载目录，整理后触发所选 WebDAV 媒体源扫描。",
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = if (enabled) "定时已开" else "定时关闭",
                icon = Icons.Filled.Refresh,
                selected = enabled,
                enabled = true,
                onClick = onToggleEnabled,
                modifier = Modifier.width(150.dp)
            )
            ScanOptionChip(
                text = if (tokenConfigured) "已登录" else "未登录",
                icon = Icons.Filled.CheckCircle,
                selected = tokenConfigured,
                enabled = false,
                onClick = {},
                modifier = Modifier.width(130.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = endpoint,
            onValueChange = onEndpointChange,
            label = "CloudDrive2 地址",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = "账号",
                modifier = Modifier.weight(1f)
            )
            TvTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "密码",
                isPassword = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = apiToken,
            onValueChange = onApiTokenChange,
            label = "API Token / Key",
            isPassword = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CloudDrivePathSelectorField(
                value = inboxPath,
                onValueChange = onInboxPathChange,
                label = "下载目录 A",
                canPick = canPickCloudDriveDirectory,
                onPick = onPickCloudInboxPath,
                modifier = Modifier.weight(1f)
            )
            CloudDrivePathSelectorField(
                value = libraryPath,
                onValueChange = onLibraryPathChange,
                label = "整理目录 B",
                canPick = canPickCloudDriveDirectory,
                onPick = onPickCloudLibraryPath,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = intervalMinutes,
            onValueChange = onIntervalMinutesChange,
            label = "定时间隔（分钟）",
            modifier = Modifier.width(220.dp)
        )

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScanOptionChip(
                text = if (rssProxyEnabled) "RSS 代理已开" else "RSS 代理关闭",
                icon = Icons.Filled.Dns,
                selected = rssProxyEnabled,
                enabled = true,
                onClick = { onRssProxyEnabledChange(!rssProxyEnabled) },
                modifier = Modifier.width(160.dp)
            )
        }
        if (rssProxyEnabled) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvTextField(
                    value = rssProxyHost,
                    onValueChange = onRssProxyHostChange,
                    label = "代理地址",
                    modifier = Modifier.weight(1f)
                )
                TvTextField(
                    value = rssProxyPort,
                    onValueChange = onRssProxyPortChange,
                    label = "代理端口",
                    modifier = Modifier.width(160.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        CloudDriveWebDavSourceSelector(
            sources = webDavSources,
            selectedSourceId = selectedWebDavSourceId,
            onSelected = onWebDavSourceSelected
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = "保存",
                icon = Icons.Filled.Save,
                enabled = endpoint.isNotBlank(),
                onClick = onSave
            )
            TvButton(
                text = if (busy) "处理中" else "登录",
                icon = Icons.Filled.Key,
                enabled = !busy && endpoint.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                onClick = onLogin
            )
            TvButton(
                text = "保存 Key",
                icon = Icons.Filled.Key,
                enabled = apiToken.isNotBlank(),
                onClick = onSaveApiToken
            )
            TvButton(
                text = if (busy) "执行中" else "立即执行",
                icon = Icons.Filled.Refresh,
                enabled = !busy && tokenConfigured,
                onClick = onRunNow
            )
        }

        StatusMessage(
            icon = if (tokenConfigured) Icons.Filled.CheckCircle else Icons.Filled.Cloud,
            text = if (tokenConfigured) "CloudDrive2 令牌已保存在加密存储中。" else "登录后才能提交离线下载任务。",
            color = if (tokenConfigured) ProgressGreen else TextSecondary
        )
        if (!actionMessage.isNullOrBlank()) {
            StatusMessage(
                icon = Icons.Filled.Refresh,
                text = actionMessage,
                color = if ("失败" in actionMessage || "请" in actionMessage) WarningYellow else ProgressGreen
            )
        }
    }
}

@Composable
private fun CloudDrivePathSelectorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    canPick: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TvTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        ScanOptionChip(
            text = "选择目录",
            icon = Icons.Filled.FolderOpen,
            selected = false,
            enabled = canPick,
            onClick = onPick,
            modifier = Modifier.width(150.dp)
        )
    }
}

@Composable
private fun CloudDriveWebDavSourceSelector(
    sources: List<MediaSourceInfo>,
    selectedSourceId: Long?,
    onSelected: (Long?) -> Unit
) {
    Column {
        Text(
            text = "入库后扫描的 WebDAV 媒体源",
            style = TvTypography.caption,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        if (sources.isEmpty()) {
            StatusMessage(
                icon = Icons.Filled.Storage,
                text = "还没有 WebDAV 媒体源，请先在媒体源里添加 CloudDrive WebDAV 地址。",
                color = WarningYellow
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CloudDriveWebDavSourceChip(
                    text = "暂不扫描",
                    selected = selectedSourceId == null,
                    onClick = { onSelected(null) },
                    modifier = Modifier.width(130.dp)
                )
                sources.take(3).forEach { source ->
                    CloudDriveWebDavSourceChip(
                        text = source.tvDisplayName(fallbackName = source.sourceLocation().orEmpty()),
                        selected = source.id == selectedSourceId,
                        onClick = { onSelected(source.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudDriveWebDavSourceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScanOptionChip(
        text = text,
        selected = selected,
        enabled = true,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun RssSubscriptionsPanel(
    subscriptions: List<RssSubscriptionInfo>,
    name: String,
    onNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    filterRegex: String,
    onFilterRegexChange: (String) -> Unit,
    enabled: Boolean,
    onToggleEnabled: () -> Unit,
    onAdd: () -> Unit,
    onToggleSubscription: (RssSubscriptionInfo, Boolean) -> Unit,
    onDelete: (Long) -> Unit
) {
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "RSS 订阅", style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = name,
            onValueChange = onNameChange,
            label = "订阅名称",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = url,
            onValueChange = onUrlChange,
            label = "RSS 地址",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = filterRegex,
            onValueChange = onFilterRegexChange,
            label = "标题过滤正则（可选）",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = if (enabled) "新增后启用" else "新增后停用",
                selected = enabled,
                enabled = true,
                onClick = onToggleEnabled,
                modifier = Modifier.width(150.dp)
            )
            TvButton(
                text = "添加订阅",
                icon = Icons.Filled.Add,
                enabled = url.isNotBlank(),
                onClick = onAdd
            )
        }

        Spacer(Modifier.height(18.dp))
        if (subscriptions.isEmpty()) {
            Text(
                text = "还没有 RSS 订阅。",
                style = TvTypography.body,
                color = TextSecondary
            )
        } else {
            subscriptions.forEach { subscription ->
                RssSubscriptionRow(
                    subscription = subscription,
                    onToggle = { onToggleSubscription(subscription, !subscription.enabled) },
                    onDelete = { onDelete(subscription.id) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun RssSubscriptionRow(
    subscription: RssSubscriptionInfo,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val background = when {
        isFocused -> AccentBlue
        subscription.enabled -> DarkSurface
        else -> DarkSurface.copy(alpha = 0.68f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Cloud,
            contentDescription = null,
            tint = if (subscription.enabled) ProgressGreen else TextSecondary,
            modifier = Modifier.size(26.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subscription.name.ifBlank { subscription.url },
                style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subscription.url,
                style = TvTypography.caption,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (subscription.lastCheckedAt > 0) "上次检查 ${formatTimestamp(subscription.lastCheckedAt)}" else "尚未检查",
                style = TvTypography.caption,
                color = TextSecondary
            )
        }
        TvButton(
            text = if (subscription.enabled) "停用" else "启用",
            icon = Icons.Filled.Refresh,
            onClick = onToggle,
            modifier = Modifier.width(112.dp)
        )
        SourceDeleteButton(onClick = onDelete)
    }
}

@Composable
private fun SourceFormPanel(
    selectedType: MediaSourceType,
    onTypeSelected: (MediaSourceType) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    locationDisplayName: String,
    onPickLocalFolder: () -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    testResult: ConnectionTestResult?,
    isEditing: Boolean,
    onNewSource: () -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit
) {
    SettingsPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEditing) "编辑媒体源" else "添加媒体源",
                    style = TvTypography.subtitle,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isEditing) {
                        "修改媒体库位置或凭据，保存后会覆盖当前配置。"
                    } else {
                        "选择媒体库所在位置，保存后可在首页手动扫描。"
                    },
                    style = TvTypography.body,
                    color = TextSecondary
                )
            }
            if (isEditing) {
                TvButton(
                    text = "新建",
                    icon = Icons.Filled.Add,
                    onClick = onNewSource,
                    modifier = Modifier.width(128.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MediaSourceType.entries.forEach { type ->
                SourceTypeChip(
                    type = type,
                    selected = type == selectedType,
                    onClick = { onTypeSelected(type) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        TvTextField(
            value = name,
            onValueChange = onNameChange,
            label = "显示名称",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        if (selectedType == MediaSourceType.LOCAL) {
            LocalFolderPickerRow(
                displayName = locationDisplayName.ifBlank { displayNameForLocation(location) },
                location = location,
                onPickFolder = onPickLocalFolder
            )
        } else {
            TvTextField(
                value = location,
                onValueChange = onLocationChange,
                label = selectedType.tvLocationLabel(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (selectedType != MediaSourceType.LOCAL) {
            Spacer(Modifier.height(12.dp))
            TvTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = "用户名（可选）",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            TvTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = if (isEditing) "密码（留空则保留）" else "密码（可选）",
                isPassword = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = if (testResult is ConnectionTestResult.Testing) "测试中" else "测试连接",
                icon = Icons.Filled.WifiTethering,
                enabled = testResult !is ConnectionTestResult.Testing,
                onClick = onTestConnection
            )
            TvButton(
                text = if (isEditing) "更新源" else "保存源",
                icon = Icons.Filled.Save,
                enabled = location.isNotBlank(),
                onClick = onSave
            )
        }

        ConnectionStatus(result = testResult)
    }
}

@Composable
private fun LocalFolderPickerRow(
    displayName: String,
    location: String,
    onPickFolder: () -> Unit
) {
    Column {
        Text(
            text = "媒体文件夹",
            style = TvTypography.caption,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName.ifBlank { "尚未选择文件夹" },
                        style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (location.startsWith("content://")) "已授权访问" else location,
                        style = TvTypography.caption,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TvButton(
                text = "选择文件夹",
                icon = Icons.Filled.FolderOpen,
                onClick = onPickFolder,
                modifier = Modifier.width(170.dp)
            )
        }
    }
}

@Composable
private fun SourceTypeChip(
    type: MediaSourceType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isFocused -> FocusBorder
        selected -> AnimeRed
        else -> Color.White.copy(alpha = 0.12f)
    }

    Column(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AnimeRed.copy(alpha = 0.18f) else DarkSurface)
            .border(if (selected || isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = type.sourceIcon(),
            contentDescription = null,
            tint = if (selected) AnimeRed else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = type.tvLabel(),
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = type.tvSourceHint(),
            style = TvTypography.caption,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConnectionStatus(result: ConnectionTestResult?) {
    when (result) {
        is ConnectionTestResult.Success -> StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = "连接正常，可以保存并返回首页扫描。",
            color = ProgressGreen
        )
        is ConnectionTestResult.Failed -> StatusMessage(
            icon = Icons.Filled.Refresh,
            text = result.message,
            color = WarningYellow
        )
        is ConnectionTestResult.Testing -> StatusMessage(
            icon = Icons.Filled.WifiTethering,
            text = "正在验证连接...",
            color = TextSecondary
        )
        null -> Unit
    }
}

@Composable
private fun ScanPanel(
    autoScanEnabled: Boolean,
    autoScanIntervalHours: Int,
    lastScanAt: Long,
    onToggleAutoScan: () -> Unit,
    onIntervalSelected: (Int) -> Unit,
    mergeSameAnimeEnabled: Boolean,
    onToggleMergeSameAnime: () -> Unit
) {
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "媒体库扫描", style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "首页的扫描按钮会立即执行；定时扫描只会在到达间隔后回到首页时触发。",
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = if (autoScanEnabled) "定时已开" else "定时关闭",
                icon = Icons.Filled.Refresh,
                selected = autoScanEnabled,
                enabled = true,
                onClick = onToggleAutoScan,
                modifier = Modifier.width(150.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanPreferencesManager.INTERVAL_OPTIONS_HOURS.forEach { hours ->
                ScanOptionChip(
                    text = "${hours}小时",
                    selected = autoScanEnabled && hours == autoScanIntervalHours,
                    enabled = autoScanEnabled,
                    onClick = { onIntervalSelected(hours) },
                    modifier = Modifier.width(112.dp)
                )
            }
        }

        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = "当前间隔 ${autoScanIntervalHours} 小时 · ${formatLastScanAt(lastScanAt)}",
            color = if (autoScanEnabled) ProgressGreen else TextSecondary
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = "媒体库显示",
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = if (mergeSameAnimeEnabled) "同番合并" else "目录分开",
                icon = Icons.Filled.Dns,
                selected = mergeSameAnimeEnabled,
                enabled = true,
                onClick = onToggleMergeSameAnime,
                modifier = Modifier.width(150.dp)
            )
        }
        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = if (mergeSameAnimeEnabled) {
                "首页和详情会按 Bangumi ID 或标题合并同一番。"
            } else {
                "首页按扫描出的目录条目分别显示。"
            },
            color = if (mergeSameAnimeEnabled) ProgressGreen else TextSecondary
        )
    }
}

@Composable
private fun PlaybackPanel(
    endAction: PlaybackEndAction,
    onEndActionSelected: (PlaybackEndAction) -> Unit
) {
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "播放结束", style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "选定剧集播完后，可以直接回到详情页，也可以自动切到下一集。",
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = "返回详情",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                selected = endAction == PlaybackEndAction.RETURN_TO_DETAIL,
                enabled = true,
                onClick = { onEndActionSelected(PlaybackEndAction.RETURN_TO_DETAIL) },
                modifier = Modifier.width(160.dp)
            )
            ScanOptionChip(
                text = "继续下一集",
                icon = Icons.Filled.PlayArrow,
                selected = endAction == PlaybackEndAction.PLAY_NEXT_EPISODE,
                enabled = true,
                onClick = { onEndActionSelected(PlaybackEndAction.PLAY_NEXT_EPISODE) },
                modifier = Modifier.width(170.dp)
            )
        }

        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = when (endAction) {
                PlaybackEndAction.RETURN_TO_DETAIL -> "播完后会停在详情页，方便手动挑下一集。"
                PlaybackEndAction.PLAY_NEXT_EPISODE -> "播完后会自动开始下一集，没有下一集时会回到详情页。"
            },
            color = if (endAction == PlaybackEndAction.PLAY_NEXT_EPISODE) ProgressGreen else TextSecondary
        )
    }
}

@Composable
private fun ScanOptionChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isFocused -> FocusBorder
        selected -> AnimeRed
        else -> Color.White.copy(alpha = 0.18f)
    }
    val background = when {
        !enabled -> DarkSurface
        selected -> AnimeRed.copy(alpha = 0.28f)
        isFocused -> AccentBlue
        else -> DarkSurface
    }
    val contentColor = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.55f)

    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(if (selected || isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            maxLines = 1
        )
    }
}

private fun PlaybackEndAction.menuSummary(): String = when (this) {
    PlaybackEndAction.RETURN_TO_DETAIL -> "播完返回"
    PlaybackEndAction.PLAY_NEXT_EPISODE -> "自动下一集"
}

@Composable
private fun MetadataPanel(
    savedToken: String,
    tokenInput: String,
    tokenSaved: Boolean,
    onTokenChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit
) {
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "元数据", style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Bangumi Token 是可选项，只用于更完整的在线元数据。",
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = tokenInput,
            onValueChange = onTokenChange,
            label = "Bangumi Access Token",
            isPassword = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = "保存",
                icon = Icons.Filled.Save,
                enabled = tokenInput.isNotBlank(),
                onClick = onSaveToken
            )
            TvButton(
                text = "清除",
                icon = Icons.Filled.Delete,
                enabled = savedToken.isNotBlank(),
                onClick = onClearToken
            )
        }

        val hasToken = savedToken.isNotBlank() || tokenSaved
        StatusMessage(
            icon = if (hasToken) Icons.Filled.CheckCircle else Icons.Filled.Key,
            text = if (hasToken) "Token 已保存在加密存储中。" else "当前未设置 Token。",
            color = if (hasToken) ProgressGreen else TextSecondary
        )
    }
}

@Composable
private fun StatusMessage(
    icon: ImageVector,
    text: String,
    color: Color
) {
    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = TvTypography.body, color = TextPrimary)
    }
}

@Composable
private fun SettingsPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(22.dp),
        content = content
    )
}

private fun sourceNameOrDefault(name: String, type: MediaSourceType): String =
    name.ifBlank { type.defaultSourceName() }

private fun defaultLocationFor(type: MediaSourceType): String = when (type) {
    MediaSourceType.LOCAL -> DEFAULT_LOCAL_PATH
    MediaSourceType.WEBDAV -> ""
    MediaSourceType.SMB -> "smb://"
}

private fun createQrCodeMatrix(content: String): BitMatrix? {
    if (content.isBlank()) return null
    return runCatching {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            QR_CODE_MATRIX_SIZE,
            QR_CODE_MATRIX_SIZE,
            mapOf<EncodeHintType, Any>(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
        )
    }.getOrNull()
}

private fun formatLastScanAt(lastScanAt: Long): String {
    if (lastScanAt <= 0L) return "还没有扫描记录"
    return "上次扫描 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(lastScanAt))
}

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun MediaSourceType.sourceIcon(): ImageVector = when (this) {
    MediaSourceType.LOCAL -> Icons.Filled.Folder
    MediaSourceType.WEBDAV -> Icons.Filled.Cloud
    MediaSourceType.SMB -> Icons.Filled.Dns
}

private fun displayNameForTreeUri(uri: Uri): String {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
    val name = documentId
        ?.substringAfter(':', "")
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
    return name ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: "本地媒体库"
}

private fun displayNameForLocation(location: String): String =
    if (location.startsWith("content://")) {
        displayNameForTreeUri(Uri.parse(location))
    } else {
        displayNameForLocalPath(location)
    }

private fun displayNameForLocalPath(path: String): String =
    path.trim()
        .replace('\\', '/')
        .trimEnd('/')
        .substringAfterLast('/')
        .ifBlank { "本地媒体库" }
