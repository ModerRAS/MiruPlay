package com.miruplay.tv.ui.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.design.MiruPlayInputIntent
import com.miruplay.tv.repository.canRunNow
import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.toMediaContentMode
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.normalizeSupportedBackend
import com.miruplay.tv.model.supportedPlaybackRenderBackends
import com.miruplay.tv.model.PosterWallArrangement
import com.miruplay.tv.model.CLOUD_DRIVE_ROOT_DISPLAY_NAME
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaRecognitionMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.CloudDriveLibraryMode
import com.miruplay.tv.model.MiruPlaySettingsSection
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.SettingsSectionMenuSummaryInput
import com.miruplay.tv.model.aboutSettingsTiles
import com.miruplay.tv.model.androidTvSettingsSectionOrder
import com.miruplay.tv.model.connectionDisplayName
import com.miruplay.tv.model.connectionUsername
import com.miruplay.tv.model.recognitionMode
import com.miruplay.tv.model.cloudDriveRssApiTokenCredentialLabel
import com.miruplay.tv.model.cloudDriveRssApiTokenFieldLabel
import com.miruplay.tv.model.cloudDriveRssChooseDirectoryActionLabel
import com.miruplay.tv.model.cloudDriveRssCloseActionLabel
import com.miruplay.tv.model.cloudDriveRssCredentialsBadgeLabel
import com.miruplay.tv.model.cloudDriveRssDescriptionLabel
import com.miruplay.tv.model.cloudDriveRssEmptyDirectoryMessage
import com.miruplay.tv.model.cloudDriveRssEndpointFieldLabel
import com.miruplay.tv.model.cloudDriveRssInboxDirectoryPickerTitle
import com.miruplay.tv.model.cloudDriveRssInboxPathFieldLabel
import com.miruplay.tv.model.cloudDriveRssIntervalMinutesFieldLabel
import com.miruplay.tv.model.cloudDriveRssLibraryDirectoryPickerTitle
import com.miruplay.tv.model.cloudDriveRssLibraryPathFieldLabel
import com.miruplay.tv.model.cloudDriveRssLibraryModeOrganizedLabel
import com.miruplay.tv.model.cloudDriveRssLibraryModeSingleDirectoryLabel
import com.miruplay.tv.model.cloudDriveRssLoadingDirectoriesMessage
import com.miruplay.tv.model.cloudDriveRssLoginActionLabel
import com.miruplay.tv.model.cloudDriveRssNoScanSourceOptionLabel
import com.miruplay.tv.model.cloudDriveRssNoWebDavSourceMessage
import com.miruplay.tv.model.cloudDriveRssParentDirectoryActionLabel
import com.miruplay.tv.model.cloudDriveRssPasswordCredentialLabel
import com.miruplay.tv.model.cloudDriveRssPasswordFieldLabel
import com.miruplay.tv.model.cloudDriveRssSaveAndRunNowActionLabel
import com.miruplay.tv.model.cloudDriveRssSaveApiTokenActionLabel
import com.miruplay.tv.model.cloudDriveRssSaveConfigActionLabel
import com.miruplay.tv.model.cloudDriveRssScanSourceTitleLabel
import com.miruplay.tv.model.cloudDriveRssScheduledChipLabel
import com.miruplay.tv.model.cloudDriveRssTitleLabel
import com.miruplay.tv.model.cloudDriveRssTokenStatusMessage
import com.miruplay.tv.model.cloudDriveRssUseCurrentDirectoryActionLabel
import com.miruplay.tv.model.cloudDriveRssUsernameFieldLabel
import com.miruplay.tv.model.defaultSourceName
import com.miruplay.tv.model.defaultSourceLocation
import com.miruplay.tv.model.directoryBrowserCancelActionLabel
import com.miruplay.tv.model.directoryBrowserCloseActionLabel
import com.miruplay.tv.model.directoryBrowserEmptyMessage
import com.miruplay.tv.model.directoryBrowserLoadingMessage
import com.miruplay.tv.model.directoryBrowserParentActionLabel
import com.miruplay.tv.model.directoryBrowserRootDisplayName
import com.miruplay.tv.model.directoryBrowserTitleLabel
import com.miruplay.tv.model.directoryBrowserUseCurrentActionLabel
import com.miruplay.tv.model.mediaSourceChooseFolderActionLabel
import com.miruplay.tv.model.mediaSourceConfiguredCountLabel
import com.miruplay.tv.model.mediaSourceConnectionSuccessMessage
import com.miruplay.tv.model.mediaSourceConnectionTestingMessage
import com.miruplay.tv.model.mediaSourceContentModeHint
import com.miruplay.tv.model.mediaSourceContentModeLabel
import com.miruplay.tv.model.mediaSourceContentModeTitleLabel
import com.miruplay.tv.model.mediaSourceDisplayNameFieldLabel
import com.miruplay.tv.model.mediaSourceEmptyListMessage
import com.miruplay.tv.model.mediaSourceFormDescriptionLabel
import com.miruplay.tv.model.mediaSourceFormTitleLabel
import com.miruplay.tv.model.mediaSourceListTitleLabel
import com.miruplay.tv.model.mediaSourceLocalLibraryFallbackName
import com.miruplay.tv.model.mediaSourceLocalFolderAuthorizedLabel
import com.miruplay.tv.model.mediaSourceLocalFolderEmptyLabel
import com.miruplay.tv.model.mediaSourceLocalPathDisplayName
import com.miruplay.tv.model.mediaSourceNewActionLabel
import com.miruplay.tv.model.mediaSourcePasswordOptionalFieldLabel
import com.miruplay.tv.model.mediaSourceSaveActionLabel
import com.miruplay.tv.model.mediaSourceTestConnectionActionLabel
import com.miruplay.tv.model.mediaSourceUsernameOptionalFieldLabel
import com.miruplay.tv.model.playbackEndPlayNextEpisodeActionLabel
import com.miruplay.tv.model.playbackEndPlayNextEpisodeDetail
import com.miruplay.tv.model.playbackEndReturnToDetailActionLabel
import com.miruplay.tv.model.playbackEndReturnToDetailDetail
import com.miruplay.tv.model.playbackEndSettingsDescriptionLabel
import com.miruplay.tv.model.playbackEndSettingsTitleLabel
import com.miruplay.tv.model.playbackEndMenuSummary
import com.miruplay.tv.model.pictureSettingsDescriptionLabel
import com.miruplay.tv.model.pictureSettingsTitleLabel
import com.miruplay.tv.model.playbackBackendLabel
import com.miruplay.tv.model.metadataPanelTitleLabel
import com.miruplay.tv.model.metadataBangumiTokenFieldLabel
import com.miruplay.tv.model.metadataBangumiTokenMissingStatus
import com.miruplay.tv.model.metadataBangumiTokenOptionalHint
import com.miruplay.tv.model.metadataBangumiTokenSavedStatus
import com.miruplay.tv.model.metadataTmdbTokenFieldLabel
import com.miruplay.tv.model.metadataTmdbTokenMissingStatus
import com.miruplay.tv.model.metadataTmdbTokenOptionalHint
import com.miruplay.tv.model.metadataTmdbTokenSavedStatus
import com.miruplay.tv.model.settingsAutoScanToggleLabel
import com.miruplay.tv.model.settingsAppModeHint
import com.miruplay.tv.model.settingsAppModeOptionLabel
import com.miruplay.tv.model.settingsAppModeStatus
import com.miruplay.tv.model.settingsAppModeTitleLabel
import com.miruplay.tv.model.settingsAppUpdateCheckActionLabel
import com.miruplay.tv.model.settingsAppUpdateInstallActionLabel
import com.miruplay.tv.model.settingsAppUpdateMenuSummary
import com.miruplay.tv.model.settingsAppUpdatePanelDescription
import com.miruplay.tv.model.settingsAppUpdatePanelTitleLabel
import com.miruplay.tv.model.settingsAppUpdatePermissionActionLabel
import com.miruplay.tv.model.settingsAboutPanelDescription
import com.miruplay.tv.model.settingsAboutPanelTitleLabel
import com.miruplay.tv.model.settingsAboutPackageNameLabel
import com.miruplay.tv.model.settingsAboutStatusMessage
import com.miruplay.tv.model.settingsAboutUnknownValue
import com.miruplay.tv.model.settingsBackActionLabel
import com.miruplay.tv.model.settingsAndroidTvLogUploadMenuSummary
import com.miruplay.tv.model.settingsCurrentScanIntervalStatus
import com.miruplay.tv.model.settingsLibraryDisplayTitleLabel
import com.miruplay.tv.model.settingsMenuPanelDescriptionAndroidTv
import com.miruplay.tv.model.settingsMenuPanelTitle
import com.miruplay.tv.model.settingsMergeSameAnimeStatus
import com.miruplay.tv.model.settingsMergeSameAnimeToggleLabel
import com.miruplay.tv.model.settingsMetadataTokenMenuSummary
import com.miruplay.tv.model.settingsMenuSummary
import com.miruplay.tv.model.settingsPosterWallArrangementTitleLabel
import com.miruplay.tv.model.posterWallArrangementLabel
import com.miruplay.tv.model.posterWallArrangementStatus
import com.miruplay.tv.model.logUploadSettingsTiles
import com.miruplay.tv.model.settingsScanIntervalOptionLabel
import com.miruplay.tv.model.settingsScanPanelDescription
import com.miruplay.tv.model.settingsScanPanelTitleLabel
import com.miruplay.tv.model.settingsClearTokenActionLabel
import com.miruplay.tv.model.settingsLogUploadAutoToggleLabel
import com.miruplay.tv.model.settingsLogUploadEndpointFieldLabel
import com.miruplay.tv.model.settingsLogUploadRunNowActionLabel
import com.miruplay.tv.model.settingsLogUploadSaveSettingsActionLabel
import com.miruplay.tv.model.settingsLogUploadTokenConfiguredStatus
import com.miruplay.tv.model.settingsProxyCurrentStatus
import com.miruplay.tv.model.settingsProxyHostFieldLabel
import com.miruplay.tv.model.settingsProxyPanelDescription
import com.miruplay.tv.model.settingsProxyPanelTitleLabel
import com.miruplay.tv.model.settingsProxyPortFieldLabel
import com.miruplay.tv.model.settingsProxySaveActionLabel
import com.miruplay.tv.model.settingsProxyToggleLabel
import com.miruplay.tv.model.settingsSaveTokenActionLabel
import com.miruplay.tv.model.settingsScreenSubtitleLabel
import com.miruplay.tv.model.settingsScreenTitleLabel
import com.miruplay.tv.model.settingsWebUiAccessTokenLabel
import com.miruplay.tv.model.settingsWebUiAddressLabel
import com.miruplay.tv.model.settingsWebUiAvailableAddressesLabel
import com.miruplay.tv.model.settingsWebUiDisabledStatus
import com.miruplay.tv.model.settingsWebUiNoLanAddressStatus
import com.miruplay.tv.model.settingsWebUiPanelDescription
import com.miruplay.tv.model.settingsWebUiPanelTitleLabel
import com.miruplay.tv.model.settingsWebUiQrOpenLabel
import com.miruplay.tv.model.settingsWebUiRefreshAddressActionLabel
import com.miruplay.tv.model.settingsWebUiRotateTokenActionLabel
import com.miruplay.tv.model.settingsWebUiToggleActionLabel
import com.miruplay.tv.model.sourceLocation
import com.miruplay.tv.model.toneMappingPresetLabel
import com.miruplay.tv.model.toneMappingPresetOptions
import com.miruplay.tv.model.rssSubscriptionAddActionLabel
import com.miruplay.tv.model.rssSubscriptionEmptyMessage
import com.miruplay.tv.model.rssSubscriptionFilterRegexFieldLabel
import com.miruplay.tv.model.rssSubscriptionLastCheckedLabel
import com.miruplay.tv.model.rssSubscriptionNameFieldLabel
import com.miruplay.tv.model.rssSubscriptionNewEnabledLabel
import com.miruplay.tv.model.rssSubscriptionStateActionLabel
import com.miruplay.tv.model.rssSubscriptionUrlFieldLabel
import com.miruplay.tv.model.rssSubscriptionsTitleLabel
import com.miruplay.tv.model.prepareRssSubscriptionForm
import com.miruplay.tv.model.saveBangumiTokenFormResult
import com.miruplay.tv.model.saveTmdbTokenFormResult
import com.miruplay.tv.model.shouldClearFormAfterSubmit
import com.miruplay.tv.model.parseCloudDriveIntervalMinutes
import com.miruplay.tv.model.parseRssProxyPort
import com.miruplay.tv.model.tvDisplayName
import com.miruplay.tv.model.tvDisplayStatusLabel
import com.miruplay.tv.model.tvLabel
import com.miruplay.tv.model.tvLocationLabel
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.ToneMappingProfilePreset
import com.miruplay.tv.model.videoRenderRuleLabel
import com.miruplay.tv.model.toApproximatePreset
import com.miruplay.tv.model.tvSourceHint
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.TvButton
import com.miruplay.tv.ui.components.TvTextField
import com.miruplay.tv.ui.components.tvFocusableClickable
import com.miruplay.tv.ui.components.toMiruPlayInputIntent
import com.miruplay.tv.sync.rss.CloudDriveDirectoryBrowserState
import com.miruplay.tv.sync.rss.CloudDriveDirectoryTarget
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

private data class AppAboutInfo(
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
)

@Composable
private fun rememberAppAboutInfo(): AppAboutInfo {
    val context = LocalContext.current
    return remember(context) {
        val packageName = context.packageName
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
        }.getOrNull()
        val appName = runCatching {
            context.applicationInfo.loadLabel(context.packageManager).toString()
        }.getOrDefault("MiruPlay")

        AppAboutInfo(
            appName = appName.ifBlank { "MiruPlay" },
            versionName = packageInfo?.versionName.orEmpty(),
            versionCode = packageInfo?.longVersionCode ?: 0L,
            packageName = packageName,
        )
    }
}

private fun MiruPlaySettingsSection.androidTvIcon(): ImageVector =
    when (this) {
        MiruPlaySettingsSection.WEB_UI -> Icons.Filled.WifiTethering
        MiruPlaySettingsSection.SOURCES -> Icons.Filled.Storage
        MiruPlaySettingsSection.PLAYBACK -> Icons.Filled.PlayArrow
        MiruPlaySettingsSection.CLOUD_DRIVE -> Icons.Filled.Cloud
        MiruPlaySettingsSection.PROXY -> Icons.Filled.Dns
        MiruPlaySettingsSection.SCAN -> Icons.Filled.Refresh
        MiruPlaySettingsSection.LOG_UPLOAD -> Icons.Filled.Upload
        MiruPlaySettingsSection.APP_UPDATE -> Icons.Filled.Refresh
        MiruPlaySettingsSection.METADATA -> Icons.Filled.Key
        MiruPlaySettingsSection.ABOUT -> Icons.Filled.Info
    }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AddSourceScreen(
    onNavigateBack: () -> Unit,
    onRestartApp: () -> Unit = {},
    onExitApp: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val savedToken by viewModel.bangumiToken.collectAsStateWithLifecycle()
    val autoScanEnabled by viewModel.autoScanEnabled.collectAsStateWithLifecycle()
    val autoScanIntervalHours by viewModel.autoScanIntervalHours.collectAsStateWithLifecycle()
    val lastScanAt by viewModel.lastScanAt.collectAsStateWithLifecycle()
    val mergeSameAnimeEnabled by viewModel.mergeSameAnimeEnabled.collectAsStateWithLifecycle()
    val posterWallArrangement by viewModel.posterWallArrangement.collectAsStateWithLifecycle()
    val currentAppMode by viewModel.currentAppMode.collectAsStateWithLifecycle()
    val playbackEndAction by viewModel.playbackEndAction.collectAsStateWithLifecycle()
    val formatAwareToneMappingPreferences by viewModel.formatAwareToneMappingPreferences.collectAsStateWithLifecycle()
    val savedTmdbToken by viewModel.tmdbToken.collectAsStateWithLifecycle()
    val webUiUrls by viewModel.webUiUrls.collectAsStateWithLifecycle()
    val webControlEnabled by viewModel.webControlEnabled.collectAsStateWithLifecycle()
    val webControlAccessToken by viewModel.webControlAccessToken.collectAsStateWithLifecycle()
    val cloudDriveConfig by viewModel.cloudDriveConfig.collectAsStateWithLifecycle()
    val rssSubscriptions by viewModel.rssSubscriptions.collectAsStateWithLifecycle()
    val cloudDriveTokenConfigured by viewModel.cloudDriveTokenConfigured.collectAsStateWithLifecycle()
    val cloudDrivePasswordConfigured by viewModel.cloudDrivePasswordConfigured.collectAsStateWithLifecycle()
    val cloudDriveBusy by viewModel.cloudDriveBusy.collectAsStateWithLifecycle()
    val cloudDriveActionMessage by viewModel.cloudDriveActionMessage.collectAsStateWithLifecycle()
    val cloudDriveDirectoryBrowser by viewModel.cloudDriveDirectoryBrowser.collectAsStateWithLifecycle()
    val localDirectoryBrowser by viewModel.localDirectoryBrowser.collectAsStateWithLifecycle()
    val logUploadSnapshot by viewModel.logUploadSnapshot.collectAsStateWithLifecycle()
    val logUploadStatusMessage by viewModel.logUploadStatusMessage.collectAsStateWithLifecycle()
    val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val bangumiArchiveState by viewModel.bangumiArchiveState.collectAsStateWithLifecycle()
    val proxyStatusMessage by viewModel.proxyStatusMessage.collectAsStateWithLifecycle()
    val appAboutInfo = rememberAppAboutInfo()

    var selectedSection by remember { mutableStateOf(MiruPlaySettingsSection.WEB_UI) }
    var editingSourceId by remember { mutableStateOf<Long?>(null) }
    var selectedType by remember { mutableStateOf(MediaSourceType.LOCAL) }
    var selectedContentMode by remember { mutableStateOf(MediaContentMode.ANIME) }
    var selectedRecognitionMode by remember { mutableStateOf(MediaRecognitionMode.DIRECTORY) }
    var name by remember { mutableStateOf(sourceNameOrDefault("", MediaSourceType.LOCAL)) }
    var location by remember { mutableStateOf(DEFAULT_LOCAL_PATH) }
    var locationDisplayName by remember { mutableStateOf(mediaSourceLocalPathDisplayName(DEFAULT_LOCAL_PATH)) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var tokenSaved by remember { mutableStateOf(false) }
    var tmdbTokenInput by remember { mutableStateOf("") }
    var tmdbTokenSaved by remember { mutableStateOf(false) }
    var selectedWebUiUrl by remember { mutableStateOf("") }
    var cloudEndpoint by remember { mutableStateOf("") }
    var cloudUsername by remember { mutableStateOf("") }
    var cloudPassword by remember { mutableStateOf("") }
    var cloudApiToken by remember { mutableStateOf("") }
    var cloudInboxPath by remember { mutableStateOf("") }
    var cloudLibraryPath by remember { mutableStateOf("") }
    var cloudLibraryMode by remember { mutableStateOf(CloudDriveLibraryMode.ORGANIZED_LIBRARY) }
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

    LaunchedEffect(tmdbTokenSaved) {
        if (tmdbTokenSaved) {
            delay(1800)
            tmdbTokenSaved = false
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
        cloudLibraryMode = cloudDriveConfig.libraryMode
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
        selectedContentMode = currentAppMode.toMediaContentMode()
        selectedRecognitionMode = MediaRecognitionMode.DIRECTORY
        name = sourceNameOrDefault("", type)
        location = type.defaultSourceLocation(DEFAULT_LOCAL_PATH)
        locationDisplayName = if (type == MediaSourceType.LOCAL) mediaSourceLocalPathDisplayName(location) else ""
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
        selectedContentMode = source.contentMode
        selectedRecognitionMode = source.recognitionMode()
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
        val effectiveRecognitionMode = if (
            selectedType == MediaSourceType.WEBDAV && selectedContentMode == MediaContentMode.ANIME
        ) {
            selectedRecognitionMode
        } else {
            MediaRecognitionMode.DIRECTORY
        }
        val source = MediaSourceInfo(
            id = editingSourceId ?: 0L,
            name = sourceNameOrDefault(name, selectedType),
            type = selectedType,
            contentMode = selectedContentMode,
            connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
                type = selectedType,
                location = location,
                displayName = locationDisplayName,
                username = username,
                password = password,
                recognitionMode = effectiveRecognitionMode,
            )
        )
        if (editingSourceId == null) {
            viewModel.addSource(source)
        } else {
            viewModel.updateSource(source)
        }
        resetSourceForm()
    }

    fun saveProxyDraft(enabled: Boolean = rssProxyEnabled) {
        viewModel.saveProxyConfig(
            enabled = enabled,
            host = rssProxyHost,
            port = parseRssProxyPort(rssProxyPort)
        )
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
                    posterWallArrangement = posterWallArrangement,
                    playbackEndAction = playbackEndAction,
                    formatAwareToneMappingPreferences = formatAwareToneMappingPreferences,
                    cloudDriveEnabled = cloudEnabled,
                    rssCount = rssSubscriptions.size,
                    proxyEnabled = rssProxyEnabled,
                    proxyHost = rssProxyHost,
                    proxyPort = parseRssProxyPort(rssProxyPort),
                    logUploadEnabled = logUploadSnapshot.enabled,
                    logUploadTokenConfigured = logUploadSnapshot.tokenConfigured,
                    logUploadUploading = logUploadSnapshot.isUploading,
                    appUpdateBusy = appUpdateState.isBusy,
                    appUpdateAvailable = appUpdateState.updateAvailable,
                    hasBangumiToken = savedToken.isNotBlank() || tokenSaved,
                    hasTmdbToken = savedTmdbToken.isNotBlank() || tmdbTokenSaved,
                    appVersionName = appAboutInfo.versionName,
                    appMode = currentAppMode.storageValue,
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
                            selectedContentMode = currentAppMode.toMediaContentMode()
                            selectedRecognitionMode = MediaRecognitionMode.DIRECTORY
                            name = sourceNameOrDefault("", type)
                            location = type.defaultSourceLocation(DEFAULT_LOCAL_PATH)
                            locationDisplayName = if (type == MediaSourceType.LOCAL) mediaSourceLocalPathDisplayName(location) else ""
                            username = ""
                            password = ""
                            viewModel.clearTestResult()
                        }
                    },
                    name = name,
                    onNameChange = { name = it },
                    selectedContentMode = selectedContentMode,
                    onContentModeSelected = {
                        selectedContentMode = it
                        if (it != MediaContentMode.ANIME) selectedRecognitionMode = MediaRecognitionMode.DIRECTORY
                    },
                    selectedRecognitionMode = selectedRecognitionMode,
                    onRecognitionModeSelected = { selectedRecognitionMode = it },
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
                    posterWallArrangement = posterWallArrangement,
                    onPosterWallArrangementSelected = viewModel::setPosterWallArrangement,
                    currentAppMode = currentAppMode,
                    onAppModeSelected = viewModel::setCurrentAppMode,
                    playbackEndAction = playbackEndAction,
                    onPlaybackEndActionSelected = viewModel::setPlaybackEndAction,
                    formatAwareToneMappingPreferences = formatAwareToneMappingPreferences,
                    onPlaybackBackendSelected = viewModel::setDefaultPlaybackBackend,
                    onToneMappingPresetSelected = viewModel::setToneMappingPreset,
                    savedToken = savedToken,
                    tokenInput = tokenInput,
                    tokenSaved = tokenSaved,
                    onTokenChange = { tokenInput = it },
                    onSaveToken = {
                        val result = saveBangumiTokenFormResult(
                            input = tokenInput,
                            existingToken = savedToken,
                        )
                        if (result.shouldPersistTokenInput) {
                            viewModel.saveBangumiToken(tokenInput)
                            tokenInput = ""
                            tokenSaved = result.configured
                        }
                    },
                    onClearToken = {
                        viewModel.clearBangumiToken()
                        tokenInput = ""
                        tokenSaved = false
                    },
                    savedTmdbToken = savedTmdbToken,
                    tmdbTokenInput = tmdbTokenInput,
                    tmdbTokenSaved = tmdbTokenSaved,
                    onTmdbTokenChange = { tmdbTokenInput = it },
                    onSaveTmdbToken = {
                        val result = saveTmdbTokenFormResult(
                            input = tmdbTokenInput,
                            existingToken = savedTmdbToken,
                        )
                        if (result.shouldPersistTokenInput) {
                            viewModel.saveTmdbToken(tmdbTokenInput)
                            tmdbTokenInput = ""
                            tmdbTokenSaved = result.configured
                        }
                    },
                    onClearTmdbToken = {
                        viewModel.clearTmdbToken()
                        tmdbTokenInput = ""
                        tmdbTokenSaved = false
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
                    cloudLibraryMode = cloudLibraryMode,
                    onCloudLibraryModeChange = { cloudLibraryMode = it },
                    cloudIntervalMinutes = cloudIntervalMinutes,
                    onCloudIntervalMinutesChange = { cloudIntervalMinutes = it.filter(Char::isDigit).take(4) },
                    cloudEnabled = cloudEnabled,
                    onToggleCloudEnabled = { cloudEnabled = !cloudEnabled },
                    cloudWebDavSourceId = cloudWebDavSourceId,
                    onCloudWebDavSourceSelected = { cloudWebDavSourceId = it },
                    cloudDriveTokenConfigured = cloudDriveTokenConfigured,
                    cloudDrivePasswordConfigured = cloudDrivePasswordConfigured,
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
                    onRssProxyEnabledChange = { enabled ->
                        rssProxyEnabled = enabled
                        saveProxyDraft(enabled)
                    },
                    rssProxyHost = rssProxyHost,
                    onRssProxyHostChange = { rssProxyHost = it },
                    rssProxyPort = rssProxyPort,
                    onRssProxyPortChange = { rssProxyPort = it.filter(Char::isDigit).take(5) },
                    proxyStatusMessage = proxyStatusMessage,
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
                            libraryMode = cloudLibraryMode,
                            intervalMinutes = parseCloudDriveIntervalMinutes(cloudIntervalMinutes),
                            enabled = cloudEnabled,
                            rssProxyEnabled = rssProxyEnabled,
                            rssProxyHost = rssProxyHost,
                            rssProxyPort = parseRssProxyPort(rssProxyPort)
                        )
                    },
                    onSaveProxyConfig = { saveProxyDraft() },
                    onLoginCloudDrive = {
                        viewModel.loginCloudDrive(cloudEndpoint, cloudUsername, cloudPassword)
                        cloudPassword = ""
                    },
                    onSaveCloudDriveApiToken = {
                        viewModel.saveCloudDriveApiToken(cloudEndpoint, cloudApiToken)
                        cloudApiToken = ""
                    },
                    onRunCloudDriveNow = {
                        viewModel.saveAndRunCloudDriveNow(
                            endpointUrl = cloudEndpoint,
                            username = cloudUsername,
                            password = cloudPassword,
                            webDavSourceId = cloudWebDavSourceId,
                            inboxPath = cloudInboxPath,
                            libraryPath = cloudLibraryPath,
                            libraryMode = cloudLibraryMode,
                            intervalMinutes = parseCloudDriveIntervalMinutes(cloudIntervalMinutes),
                            enabled = cloudEnabled,
                            rssProxyEnabled = rssProxyEnabled,
                            rssProxyHost = rssProxyHost,
                            rssProxyPort = parseRssProxyPort(rssProxyPort)
                        )
                        cloudPassword = ""
                    },
                    onAddRssSubscription = {
                        val formResult = prepareRssSubscriptionForm(rssName, rssUrl, rssFilterRegex, rssEnabled)
                        viewModel.addRssSubscription(rssName, rssUrl, rssFilterRegex, rssEnabled)
                        if (formResult.shouldClearFormAfterSubmit) {
                            rssName = ""
                            rssUrl = ""
                            rssFilterRegex = ""
                            rssEnabled = true
                        }
                    },
                    onToggleRssSubscription = viewModel::setRssSubscriptionEnabled,
                    onDeleteRssSubscription = viewModel::deleteRssSubscription,
                    logUploadEnabled = logUploadSnapshot.enabled,
                    onLogUploadEnabledChange = viewModel::setLogUploadEnabled,
                    logUploadEndpoint = logUploadSnapshot.endpoint,
                    onLogUploadEndpointChange = viewModel::setLogUploadEndpoint,
                    logUploadTokenConfigured = logUploadSnapshot.tokenConfigured,
                    logUploadStatusMessage = logUploadStatusMessage,
                    onSaveLogUploadSettings = {
                        viewModel.saveLogUploadSettings("")
                    },
                    onClearLogUploadToken = viewModel::clearLogUploadToken,
                    onRunLogUploadNow = {
                        viewModel.runLogUploadNow("")
                    },
                    canRunLogUploadNow = logUploadSnapshot.enabled &&
                        logUploadSnapshot.endpoint.isNotBlank() &&
                        !logUploadSnapshot.isUploading,
                    appUpdateState = appUpdateState,
                    onCheckAppUpdate = viewModel::checkAppUpdate,
                    onDownloadAndInstallAppUpdate = viewModel::downloadAndInstallAppUpdate,
                    onOpenAppUpdateInstallPermission = viewModel::openAppUpdateInstallPermissionSettings,
                    bangumiArchiveState = bangumiArchiveState,
                    onRefreshBangumiArchive = viewModel::refreshBangumiArchive,
                    onDownloadBangumiArchive = viewModel::downloadBangumiArchive,
                    appAboutInfo = appAboutInfo,
                    onRestartApp = onRestartApp,
                    onExitApp = onExitApp,
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
                        locationDisplayName = mediaSourceLocalPathDisplayName(it)
                        if (name.isBlank() || name == "本地下载") {
                            name = locationDisplayName.ifBlank { mediaSourceLocalLibraryFallbackName() }
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
                text = settingsScreenTitleLabel(),
                style = TvTypography.title,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = settingsScreenSubtitleLabel(),
                style = TvTypography.body,
                color = TextSecondary
            )
        }
        TvButton(
            text = settingsBackActionLabel(),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onNavigateBack,
            modifier = Modifier.width(180.dp)
        )
    }
}

@Composable
private fun SettingsMenuPanel(
    selectedSection: MiruPlaySettingsSection,
    sourcesCount: Int,
    webUiAddressCount: Int,
    autoScanEnabled: Boolean,
    mergeSameAnimeEnabled: Boolean,
    posterWallArrangement: PosterWallArrangement,
    playbackEndAction: PlaybackEndAction,
    formatAwareToneMappingPreferences: FormatAwareToneMappingPreferences,
    cloudDriveEnabled: Boolean,
    rssCount: Int,
    proxyEnabled: Boolean,
    proxyHost: String,
    proxyPort: Int,
    logUploadEnabled: Boolean,
    logUploadTokenConfigured: Boolean,
    logUploadUploading: Boolean,
    appUpdateBusy: Boolean,
    appUpdateAvailable: Boolean,
    hasBangumiToken: Boolean,
    hasTmdbToken: Boolean,
    appVersionName: String,
    appMode: String,
    menuFocusRequesters: Map<MiruPlaySettingsSection, FocusRequester>,
    onSectionSelected: (MiruPlaySettingsSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val menuSummaryInput = SettingsSectionMenuSummaryInput(
        webUiAddressCount = webUiAddressCount,
        sourceCount = sourcesCount,
        playbackSummary = "${playbackEndAction.playbackEndMenuSummary()} · ${playbackBackendLabel(formatAwareToneMappingPreferences.defaultBackend)}",
        cloudDriveEnabled = cloudDriveEnabled,
        rssCount = rssCount,
        proxyEnabled = proxyEnabled,
        proxyHost = proxyHost,
        proxyPort = proxyPort,
        autoScanEnabled = autoScanEnabled,
        mergeSameAnimeEnabled = mergeSameAnimeEnabled,
        posterWallArrangement = posterWallArrangement,
        appMode = appMode,
        metadataSummary = settingsMetadataTokenMenuSummary(
            hasBangumiToken = hasBangumiToken,
            hasTmdbToken = hasTmdbToken,
        ),
        appVersionName = appVersionName,
        logUploadSummary = settingsAndroidTvLogUploadMenuSummary(
            enabled = logUploadEnabled,
            tokenConfigured = logUploadTokenConfigured,
            isUploading = logUploadUploading,
        ),
        appUpdateSummary = when {
            appUpdateBusy -> "处理中"
            appUpdateAvailable -> "可更新"
            else -> settingsAppUpdateMenuSummary()
        },
    )

    SettingsPanel(modifier = modifier) {
        Text(
            text = settingsMenuPanelTitle(),
            style = TvTypography.subtitle,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = settingsMenuPanelDescriptionAndroidTv(),
            style = TvTypography.caption,
            color = TextSecondary
        )
        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            androidTvSettingsSectionOrder.forEach { section ->
                SettingsMenuItem(
                    section = section,
                    summary = section.settingsMenuSummary(menuSummaryInput),
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
            .tvFocusableClickable(
                interactionSource = interactionSource,
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
    selectedContentMode: MediaContentMode,
    onContentModeSelected: (MediaContentMode) -> Unit,
    selectedRecognitionMode: MediaRecognitionMode,
    onRecognitionModeSelected: (MediaRecognitionMode) -> Unit,
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
    posterWallArrangement: PosterWallArrangement,
    onPosterWallArrangementSelected: (PosterWallArrangement) -> Unit,
    currentAppMode: AppMode,
    onAppModeSelected: (AppMode) -> Unit,
    playbackEndAction: PlaybackEndAction,
    onPlaybackEndActionSelected: (PlaybackEndAction) -> Unit,
    formatAwareToneMappingPreferences: FormatAwareToneMappingPreferences,
    onPlaybackBackendSelected: (PlaybackRenderBackend) -> Unit,
    onToneMappingPresetSelected: (VideoRenderRuleKey, ToneMappingProfilePreset) -> Unit,
    savedToken: String,
    tokenInput: String,
    tokenSaved: Boolean,
    onTokenChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
    savedTmdbToken: String,
    tmdbTokenInput: String,
    tmdbTokenSaved: Boolean,
    onTmdbTokenChange: (String) -> Unit,
    onSaveTmdbToken: () -> Unit,
    onClearTmdbToken: () -> Unit,
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
    cloudLibraryMode: CloudDriveLibraryMode,
    onCloudLibraryModeChange: (CloudDriveLibraryMode) -> Unit,
    cloudIntervalMinutes: String,
    onCloudIntervalMinutesChange: (String) -> Unit,
    cloudEnabled: Boolean,
    onToggleCloudEnabled: () -> Unit,
    cloudWebDavSourceId: Long?,
    onCloudWebDavSourceSelected: (Long?) -> Unit,
    cloudDriveTokenConfigured: Boolean,
    cloudDrivePasswordConfigured: Boolean,
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
    proxyStatusMessage: String,
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
    onSaveProxyConfig: () -> Unit,
    onLoginCloudDrive: () -> Unit,
    onSaveCloudDriveApiToken: () -> Unit,
    onRunCloudDriveNow: () -> Unit,
    onAddRssSubscription: () -> Unit,
    onToggleRssSubscription: (RssSubscriptionInfo, Boolean) -> Unit,
    onDeleteRssSubscription: (Long) -> Unit,
    logUploadEnabled: Boolean,
    onLogUploadEnabledChange: (Boolean) -> Unit,
    logUploadEndpoint: String,
    onLogUploadEndpointChange: (String) -> Unit,
    logUploadTokenConfigured: Boolean,
    logUploadStatusMessage: String,
    onSaveLogUploadSettings: () -> Unit,
    onClearLogUploadToken: () -> Unit,
    onRunLogUploadNow: () -> Unit,
    canRunLogUploadNow: Boolean,
    appUpdateState: AppUpdateUiState,
    onCheckAppUpdate: () -> Unit,
    onDownloadAndInstallAppUpdate: () -> Unit,
    onOpenAppUpdateInstallPermission: () -> Unit,
    bangumiArchiveState: BangumiArchiveUiState,
    onRefreshBangumiArchive: () -> Unit,
    onDownloadBangumiArchive: () -> Unit,
    appAboutInfo: AppAboutInfo,
    onRestartApp: () -> Unit,
    onExitApp: () -> Unit,
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
                        if (
                            event.type == KeyEventType.KeyDown &&
                            event.key.toMiruPlayInputIntent() == MiruPlayInputIntent.DirectionLeft
                        ) {
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
                    selectedContentMode = selectedContentMode,
                    onContentModeSelected = onContentModeSelected,
                    selectedRecognitionMode = selectedRecognitionMode,
                    onRecognitionModeSelected = onRecognitionModeSelected,
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
                cloudLibraryMode = cloudLibraryMode,
                onCloudLibraryModeChange = onCloudLibraryModeChange,
                intervalMinutes = cloudIntervalMinutes,
                onIntervalMinutesChange = onCloudIntervalMinutesChange,
                enabled = cloudEnabled,
                onToggleEnabled = onToggleCloudEnabled,
                selectedWebDavSourceId = cloudWebDavSourceId,
                onWebDavSourceSelected = onCloudWebDavSourceSelected,
                tokenConfigured = cloudDriveTokenConfigured,
                passwordConfigured = cloudDrivePasswordConfigured,
                busy = cloudDriveBusy,
                actionMessage = cloudDriveActionMessage,
                canPickCloudDriveDirectory = canPickCloudDriveDirectory,
                onPickCloudInboxPath = onPickCloudInboxPath,
                onPickCloudLibraryPath = onPickCloudLibraryPath,
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

        MiruPlaySettingsSection.PROXY -> SettingsSingleSectionPage(
            section = selectedSection,
            modifier = modifier
        ) {
            ProxySettingsPanel(
                enabled = rssProxyEnabled,
                onEnabledChange = onRssProxyEnabledChange,
                host = rssProxyHost,
                onHostChange = onRssProxyHostChange,
                port = rssProxyPort,
                onPortChange = onRssProxyPortChange,
                statusMessage = proxyStatusMessage,
                onSave = onSaveProxyConfig,
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
                onToggleMergeSameAnime = onToggleMergeSameAnime,
                posterWallArrangement = posterWallArrangement,
                onPosterWallArrangementSelected = onPosterWallArrangementSelected,
                currentAppMode = currentAppMode,
                onAppModeSelected = onAppModeSelected,
            )
        }

        MiruPlaySettingsSection.PLAYBACK -> SettingsSingleSectionPage(
            section = selectedSection,
            modifier = modifier
        ) {
            PlaybackPanel(
                endAction = playbackEndAction,
                onEndActionSelected = onPlaybackEndActionSelected,
                formatAwareToneMappingPreferences = formatAwareToneMappingPreferences,
                onPlaybackBackendSelected = onPlaybackBackendSelected,
                onToneMappingPresetSelected = onToneMappingPresetSelected,
            )
        }

        MiruPlaySettingsSection.LOG_UPLOAD -> SettingsSingleSectionPage(
            section = selectedSection,
            modifier = modifier
        ) {
            LogUploadPanel(
                enabled = logUploadEnabled,
                onEnabledChange = onLogUploadEnabledChange,
                endpoint = logUploadEndpoint,
                onEndpointChange = onLogUploadEndpointChange,
                tokenConfigured = logUploadTokenConfigured,
                statusMessage = logUploadStatusMessage,
                onSaveSettings = onSaveLogUploadSettings,
                onClearToken = onClearLogUploadToken,
                onRunNow = onRunLogUploadNow,
                canRunNow = canRunLogUploadNow,
            )
        }

        MiruPlaySettingsSection.APP_UPDATE -> SettingsSingleSectionPage(
            section = selectedSection,
            modifier = modifier
        ) {
            AppUpdatePanel(
                state = appUpdateState,
                onCheck = onCheckAppUpdate,
                onDownloadAndInstall = onDownloadAndInstallAppUpdate,
                onOpenInstallPermission = onOpenAppUpdateInstallPermission
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
                onClearToken = onClearToken,
                savedTmdbToken = savedTmdbToken,
                tmdbTokenInput = tmdbTokenInput,
                tmdbTokenSaved = tmdbTokenSaved,
                onTmdbTokenChange = onTmdbTokenChange,
                onSaveTmdbToken = onSaveTmdbToken,
                onClearTmdbToken = onClearTmdbToken,
                archiveState = bangumiArchiveState,
                onRefreshArchive = onRefreshBangumiArchive,
                onDownloadArchive = onDownloadBangumiArchive,
            )
        }

        MiruPlaySettingsSection.ABOUT -> SettingsSingleSectionPage(
            section = selectedSection,
            modifier = modifier
        ) {
            AboutPanel(
                info = appAboutInfo,
                onRestartApp = onRestartApp,
                onExitApp = onExitApp,
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
            Text(text = mediaSourceListTitleLabel(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (sources.isEmpty()) {
                mediaSourceEmptyListMessage()
            } else {
                mediaSourceConfiguredCountLabel(sources.size)
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
            .tvFocusableClickable(
                interactionSource = interactionSource,
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
            .tvFocusableClickable(
                interactionSource = interactionSource,
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
            Text(text = settingsWebUiPanelTitleLabel(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = settingsWebUiPanelDescription(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = settingsWebUiToggleActionLabel(enabled),
                icon = Icons.Filled.WifiTethering,
                onClick = onToggleEnabled,
                modifier = Modifier.width(156.dp)
            )
            TvButton(
                text = settingsWebUiRotateTokenActionLabel(),
                icon = Icons.Filled.Key,
                onClick = onRotateToken,
                enabled = enabled,
                modifier = Modifier.width(150.dp)
            )
            TvButton(
                text = settingsWebUiRefreshAddressActionLabel(),
                icon = Icons.Filled.Refresh,
                onClick = onRefresh,
                enabled = enabled,
                modifier = Modifier.width(150.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = settingsWebUiAccessTokenLabel(accessToken),
            style = TvTypography.caption,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!enabled) {
            StatusMessage(
                icon = Icons.Filled.Close,
                text = settingsWebUiDisabledStatus(),
                color = WarningYellow
            )
        } else if (urls.isEmpty()) {
            StatusMessage(
                icon = Icons.Filled.Refresh,
                text = settingsWebUiNoLanAddressStatus(),
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
                        text = settingsWebUiAvailableAddressesLabel(),
                        style = TvTypography.caption,
                        color = TextSecondary
                    )
                    urls.forEachIndexed { index, url ->
                        WebUiMenuItem(
                            url = url,
                            label = settingsWebUiAddressLabel(index),
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
                        text = settingsWebUiQrOpenLabel(),
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
            .tvFocusableClickable(
                interactionSource = interactionSource,
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
                    text = if (state.target == CloudDriveDirectoryTarget.INBOX) {
                        cloudDriveRssInboxDirectoryPickerTitle()
                    } else {
                        cloudDriveRssLibraryDirectoryPickerTitle()
                    },
                    style = TvTypography.subtitle,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                TvButton(
                    text = cloudDriveRssCloseActionLabel(),
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onDismiss
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ScanOptionChip(
                    text = cloudDriveRssParentDirectoryActionLabel(),
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    selected = false,
                    enabled = state.parentPath != null,
                    onClick = { state.parentPath?.let(onNavigate) },
                    modifier = Modifier.width(140.dp)
                )
                Text(
                    text = state.displayPath.ifBlank { CLOUD_DRIVE_ROOT_DISPLAY_NAME },
                    style = TvTypography.body,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.isLoading) {
                Text(text = cloudDriveRssLoadingDirectoriesMessage(), color = TextSecondary, style = TvTypography.body)
            } else if (state.entries.isEmpty()) {
                Text(text = cloudDriveRssEmptyDirectoryMessage(), color = TextSecondary, style = TvTypography.body)
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

            val cloudDirectoryMessage = state.message
            if (!cloudDirectoryMessage.isNullOrBlank()) {
                Text(
                    text = cloudDirectoryMessage,
                    style = TvTypography.body,
                    color = WarningYellow,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = cloudDriveRssCloseActionLabel(),
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onDismiss
                )
                TvButton(
                    text = cloudDriveRssUseCurrentDirectoryActionLabel(),
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
                    text = directoryBrowserTitleLabel(isLocal = true),
                    style = TvTypography.subtitle,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                TvButton(
                    text = directoryBrowserCloseActionLabel(),
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onDismiss
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ScanOptionChip(
                    text = directoryBrowserParentActionLabel(isLocal = true),
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    selected = false,
                    enabled = state.parentPath != null,
                    onClick = { state.parentPath?.let(onNavigate) },
                    modifier = Modifier.width(140.dp)
                )
                Text(
                    text = state.displayPath.ifBlank { directoryBrowserRootDisplayName(isLocal = true) },
                    style = TvTypography.body,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.isLoading) {
                Text(text = directoryBrowserLoadingMessage(isLocal = true), color = TextSecondary, style = TvTypography.body)
            } else if (state.entries.isEmpty()) {
                Text(text = directoryBrowserEmptyMessage(isLocal = true), color = TextSecondary, style = TvTypography.body)
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

            val localDirectoryMessage = state.message
            if (!localDirectoryMessage.isNullOrBlank()) {
                Text(
                    text = localDirectoryMessage,
                    style = TvTypography.body,
                    color = WarningYellow,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = directoryBrowserCancelActionLabel(),
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onDismiss
                )
                TvButton(
                    text = directoryBrowserUseCurrentActionLabel(isLocal = true),
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
    cloudLibraryMode: CloudDriveLibraryMode,
    onCloudLibraryModeChange: (CloudDriveLibraryMode) -> Unit,
    intervalMinutes: String,
    onIntervalMinutesChange: (String) -> Unit,
    enabled: Boolean,
    onToggleEnabled: () -> Unit,
    selectedWebDavSourceId: Long?,
    onWebDavSourceSelected: (Long?) -> Unit,
    tokenConfigured: Boolean,
    passwordConfigured: Boolean,
    busy: Boolean,
    actionMessage: String?,
    canPickCloudDriveDirectory: Boolean,
    onPickCloudInboxPath: () -> Unit,
    onPickCloudLibraryPath: () -> Unit,
    onSave: () -> Unit,
    onLogin: () -> Unit,
    onSaveApiToken: () -> Unit,
    onRunNow: () -> Unit
) {
    val webDavSources = sources.filter { it.type == MediaSourceType.WEBDAV }
    val credentialConfigured = tokenConfigured || passwordConfigured
    val runnableCredentials = credentialConfigured || (username.isNotBlank() && password.isNotBlank())

    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Cloud,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = cloudDriveRssTitleLabel(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = cloudDriveRssDescriptionLabel(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = cloudDriveRssScheduledChipLabel(enabled),
                icon = Icons.Filled.Refresh,
                selected = enabled,
                enabled = true,
                onClick = onToggleEnabled,
                modifier = Modifier.width(150.dp)
            )
            ScanOptionChip(
                text = cloudDriveRssCredentialsBadgeLabel(tokenConfigured, passwordConfigured),
                icon = Icons.Filled.CheckCircle,
                selected = credentialConfigured,
                enabled = false,
                onClick = {},
                modifier = Modifier.width(190.dp)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = cloudDriveRssPasswordCredentialLabel(passwordConfigured),
                icon = Icons.Filled.Key,
                selected = passwordConfigured,
                enabled = false,
                onClick = {},
                modifier = Modifier.weight(1f)
            )
            ScanOptionChip(
                text = cloudDriveRssApiTokenCredentialLabel(tokenConfigured),
                icon = Icons.Filled.Key,
                selected = tokenConfigured,
                enabled = false,
                onClick = {},
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = endpoint,
            onValueChange = onEndpointChange,
            label = cloudDriveRssEndpointFieldLabel(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = cloudDriveRssUsernameFieldLabel(),
                modifier = Modifier.weight(1f)
            )
            TvTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = cloudDriveRssPasswordFieldLabel(),
                isPassword = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = apiToken,
            onValueChange = onApiTokenChange,
            label = cloudDriveRssApiTokenFieldLabel(),
            isPassword = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = cloudDriveRssLibraryModeOrganizedLabel(),
                icon = Icons.Filled.Folder,
                selected = cloudLibraryMode == CloudDriveLibraryMode.ORGANIZED_LIBRARY,
                enabled = true,
                onClick = { onCloudLibraryModeChange(CloudDriveLibraryMode.ORGANIZED_LIBRARY) },
                modifier = Modifier.weight(1f)
            )
            ScanOptionChip(
                text = cloudDriveRssLibraryModeSingleDirectoryLabel(),
                icon = Icons.Filled.Storage,
                selected = cloudLibraryMode == CloudDriveLibraryMode.SINGLE_DIRECTORY,
                enabled = true,
                onClick = { onCloudLibraryModeChange(CloudDriveLibraryMode.SINGLE_DIRECTORY) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        CloudDrivePathSelectorField(
            value = inboxPath,
            onValueChange = onInboxPathChange,
            label = cloudDriveRssInboxPathFieldLabel(),
            canPick = canPickCloudDriveDirectory,
            onPick = onPickCloudInboxPath,
            modifier = Modifier.fillMaxWidth()
        )
        if (cloudLibraryMode == CloudDriveLibraryMode.ORGANIZED_LIBRARY) {
            Spacer(Modifier.height(12.dp))
            CloudDrivePathSelectorField(
                value = libraryPath,
                onValueChange = onLibraryPathChange,
                label = cloudDriveRssLibraryPathFieldLabel(),
                canPick = canPickCloudDriveDirectory,
                onPick = onPickCloudLibraryPath,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = intervalMinutes,
            onValueChange = onIntervalMinutesChange,
            label = cloudDriveRssIntervalMinutesFieldLabel(),
            modifier = Modifier.width(220.dp)
        )

        Spacer(Modifier.height(16.dp))
        CloudDriveWebDavSourceSelector(
            sources = webDavSources,
            selectedSourceId = selectedWebDavSourceId,
            onSelected = onWebDavSourceSelected
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = cloudDriveRssSaveConfigActionLabel(),
                icon = Icons.Filled.Save,
                enabled = endpoint.isNotBlank(),
                onClick = onSave
            )
            TvButton(
                text = cloudDriveRssLoginActionLabel(busy),
                icon = Icons.Filled.Key,
                enabled = !busy && endpoint.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                onClick = onLogin
            )
            TvButton(
                text = cloudDriveRssSaveApiTokenActionLabel(),
                icon = Icons.Filled.Key,
                enabled = apiToken.isNotBlank(),
                onClick = onSaveApiToken
            )
            TvButton(
                text = cloudDriveRssSaveAndRunNowActionLabel(busy),
                icon = Icons.Filled.Refresh,
                enabled = !busy && endpoint.isNotBlank() && runnableCredentials,
                onClick = onRunNow
            )
        }

        StatusMessage(
            icon = if (credentialConfigured) Icons.Filled.CheckCircle else Icons.Filled.Cloud,
            text = cloudDriveRssTokenStatusMessage(tokenConfigured, passwordConfigured),
            color = if (credentialConfigured) ProgressGreen else TextSecondary
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
            text = cloudDriveRssChooseDirectoryActionLabel(),
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
            text = cloudDriveRssScanSourceTitleLabel(),
            style = TvTypography.caption,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        if (sources.isEmpty()) {
            StatusMessage(
                icon = Icons.Filled.Storage,
                text = cloudDriveRssNoWebDavSourceMessage(),
                color = WarningYellow
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CloudDriveWebDavSourceChip(
                    text = cloudDriveRssNoScanSourceOptionLabel(),
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
            Text(text = rssSubscriptionsTitleLabel(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = name,
            onValueChange = onNameChange,
            label = rssSubscriptionNameFieldLabel(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = url,
            onValueChange = onUrlChange,
            label = rssSubscriptionUrlFieldLabel(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = filterRegex,
            onValueChange = onFilterRegexChange,
            label = rssSubscriptionFilterRegexFieldLabel(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = rssSubscriptionNewEnabledLabel(enabled),
                selected = enabled,
                enabled = true,
                onClick = onToggleEnabled,
                modifier = Modifier.width(150.dp)
            )
            TvButton(
                text = rssSubscriptionAddActionLabel(),
                icon = Icons.Filled.Add,
                enabled = url.isNotBlank(),
                onClick = onAdd
            )
        }

        Spacer(Modifier.height(18.dp))
        if (subscriptions.isEmpty()) {
            Text(
                text = rssSubscriptionEmptyMessage(),
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
                text = rssSubscriptionLastCheckedLabel(
                    subscription.lastCheckedAt.takeIf { it > 0 }?.let(::formatTimestamp)
                ),
                style = TvTypography.caption,
                color = TextSecondary
            )
        }
        TvButton(
            text = rssSubscriptionStateActionLabel(subscription.enabled),
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
    selectedContentMode: MediaContentMode,
    onContentModeSelected: (MediaContentMode) -> Unit,
    selectedRecognitionMode: MediaRecognitionMode,
    onRecognitionModeSelected: (MediaRecognitionMode) -> Unit,
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
                    text = mediaSourceFormTitleLabel(isEditing),
                    style = TvTypography.subtitle,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = mediaSourceFormDescriptionLabel(isEditing),
                    style = TvTypography.body,
                    color = TextSecondary
                )
            }
            if (isEditing) {
                TvButton(
                    text = mediaSourceNewActionLabel(),
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
            label = mediaSourceDisplayNameFieldLabel(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = mediaSourceContentModeTitleLabel(),
            style = TvTypography.caption,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaContentMode.entries.forEach { mode ->
                ScanOptionChip(
                    text = mediaSourceContentModeLabel(mode),
                    icon = Icons.Filled.Storage,
                    selected = selectedContentMode == mode,
                    enabled = true,
                    onClick = { onContentModeSelected(mode) },
                    modifier = Modifier.width(150.dp)
                )
            }
        }
        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = mediaSourceContentModeHint(selectedContentMode),
            color = ProgressGreen
        )

        if (selectedType == MediaSourceType.WEBDAV && selectedContentMode == MediaContentMode.ANIME) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "识别来源",
                style = TvTypography.caption,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScanOptionChip(
                    text = "目录扫描",
                    icon = Icons.Filled.Storage,
                    selected = selectedRecognitionMode == MediaRecognitionMode.DIRECTORY,
                    enabled = true,
                    onClick = { onRecognitionModeSelected(MediaRecognitionMode.DIRECTORY) },
                    modifier = Modifier.width(150.dp)
                )
                ScanOptionChip(
                    text = "MLIP library.db",
                    icon = Icons.Filled.Dns,
                    selected = selectedRecognitionMode == MediaRecognitionMode.MLIP,
                    enabled = true,
                    onClick = { onRecognitionModeSelected(MediaRecognitionMode.MLIP) },
                    modifier = Modifier.width(190.dp)
                )
            }
            StatusMessage(
                icon = Icons.Filled.CheckCircle,
                text = if (selectedRecognitionMode == MediaRecognitionMode.MLIP) {
                    "扫描时读取 WebDAV 根目录 library.db，不使用在线刮削。"
                } else {
                    "扫描目录并按文件名识别，可继续使用在线元数据。"
                },
                color = if (selectedRecognitionMode == MediaRecognitionMode.MLIP) ProgressGreen else TextSecondary
            )
        }

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
                label = mediaSourceUsernameOptionalFieldLabel(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            TvTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = mediaSourcePasswordOptionalFieldLabel(isEditing),
                isPassword = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = mediaSourceTestConnectionActionLabel(testResult is ConnectionTestResult.Testing),
                icon = Icons.Filled.WifiTethering,
                enabled = testResult !is ConnectionTestResult.Testing,
                onClick = onTestConnection
            )
            TvButton(
                text = mediaSourceSaveActionLabel(isEditing),
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
            text = MediaSourceType.LOCAL.tvLocationLabel(),
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
                        text = displayName.ifBlank { mediaSourceLocalFolderEmptyLabel() },
                        style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (location.startsWith("content://")) mediaSourceLocalFolderAuthorizedLabel() else location,
                        style = TvTypography.caption,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TvButton(
                text = mediaSourceChooseFolderActionLabel(),
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
            .tvFocusableClickable(
                interactionSource = interactionSource,
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
            text = mediaSourceConnectionSuccessMessage(),
            color = ProgressGreen
        )
        is ConnectionTestResult.Failed -> StatusMessage(
            icon = Icons.Filled.Refresh,
            text = result.message,
            color = WarningYellow
        )
        is ConnectionTestResult.Testing -> StatusMessage(
            icon = Icons.Filled.WifiTethering,
            text = mediaSourceConnectionTestingMessage(),
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
    onToggleMergeSameAnime: () -> Unit,
    posterWallArrangement: PosterWallArrangement,
    onPosterWallArrangementSelected: (PosterWallArrangement) -> Unit,
    currentAppMode: AppMode,
    onAppModeSelected: (AppMode) -> Unit,
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
            Text(text = settingsScanPanelTitleLabel(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = settingsScanPanelDescription(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = settingsAppModeTitleLabel(),
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppMode.entries.forEach { mode ->
                ScanOptionChip(
                    text = settingsAppModeOptionLabel(mode.storageValue),
                    icon = Icons.Filled.Storage,
                    selected = currentAppMode == mode,
                    enabled = true,
                    onClick = { onAppModeSelected(mode) },
                    modifier = Modifier.width(150.dp)
                )
            }
        }
        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = settingsAppModeStatus(currentAppMode.storageValue),
            color = ProgressGreen
        )
        StatusMessage(
            icon = Icons.Filled.Info,
            text = settingsAppModeHint(),
            color = TextSecondary
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = settingsAutoScanToggleLabel(autoScanEnabled),
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
                    text = settingsScanIntervalOptionLabel(hours),
                    selected = autoScanEnabled && hours == autoScanIntervalHours,
                    enabled = autoScanEnabled,
                    onClick = { onIntervalSelected(hours) },
                    modifier = Modifier.width(112.dp)
                )
            }
        }

        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = settingsCurrentScanIntervalStatus(autoScanIntervalHours, formatLastScanAt(lastScanAt)),
            color = if (autoScanEnabled) ProgressGreen else TextSecondary
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = settingsLibraryDisplayTitleLabel(),
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = settingsPosterWallArrangementTitleLabel(),
            style = TvTypography.caption,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosterWallArrangement.entries.forEach { arrangement ->
                ScanOptionChip(
                    text = posterWallArrangementLabel(arrangement),
                    icon = Icons.Filled.Storage,
                    selected = posterWallArrangement == arrangement,
                    enabled = true,
                    onClick = { onPosterWallArrangementSelected(arrangement) },
                    modifier = Modifier.width(150.dp)
                )
            }
        }
        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = posterWallArrangementStatus(posterWallArrangement),
            color = if (posterWallArrangement == PosterWallArrangement.RELEASE_SEASON) ProgressGreen else TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = settingsMergeSameAnimeToggleLabel(mergeSameAnimeEnabled),
                icon = Icons.Filled.Dns,
                selected = mergeSameAnimeEnabled,
                enabled = true,
                onClick = onToggleMergeSameAnime,
                modifier = Modifier.width(150.dp)
            )
        }
        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = settingsMergeSameAnimeStatus(mergeSameAnimeEnabled),
            color = if (mergeSameAnimeEnabled) ProgressGreen else TextSecondary
        )
    }
}

@Composable
private fun PlaybackPanel(
    endAction: PlaybackEndAction,
    onEndActionSelected: (PlaybackEndAction) -> Unit,
    formatAwareToneMappingPreferences: FormatAwareToneMappingPreferences,
    onPlaybackBackendSelected: (PlaybackRenderBackend) -> Unit,
    onToneMappingPresetSelected: (VideoRenderRuleKey, ToneMappingProfilePreset) -> Unit,
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
            Text(text = playbackEndSettingsTitleLabel(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = playbackEndSettingsDescriptionLabel(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = playbackEndReturnToDetailActionLabel(),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                selected = endAction == PlaybackEndAction.RETURN_TO_DETAIL,
                enabled = true,
                onClick = { onEndActionSelected(PlaybackEndAction.RETURN_TO_DETAIL) },
                modifier = Modifier.width(160.dp)
            )
            ScanOptionChip(
                text = playbackEndPlayNextEpisodeActionLabel(),
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
                PlaybackEndAction.RETURN_TO_DETAIL -> playbackEndReturnToDetailDetail()
                PlaybackEndAction.PLAY_NEXT_EPISODE -> playbackEndPlayNextEpisodeDetail()
            },
            color = if (endAction == PlaybackEndAction.PLAY_NEXT_EPISODE) ProgressGreen else TextSecondary
        )

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.PhotoFilter,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = pictureSettingsTitleLabel(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = pictureSettingsDescriptionLabel(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "默认后端",
            style = TvTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            supportedPlaybackRenderBackends().forEach { backend ->
                ScanOptionChip(
                    text = playbackBackendLabel(backend),
                    icon = if (backend == PlaybackRenderBackend.EXPERIMENTAL_GL) Icons.Filled.PhotoFilter else Icons.Filled.PlayArrow,
                    selected = formatAwareToneMappingPreferences.defaultBackend.normalizeSupportedBackend() == backend,
                    enabled = true,
                    onClick = { onPlaybackBackendSelected(backend) },
                    modifier = Modifier.width(170.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        VideoRenderRuleKey.entries.forEach { ruleKey ->
            val ruleSet = formatAwareToneMappingPreferences.rules.getValue(ruleKey)
            Text(
                text = videoRenderRuleLabel(ruleKey),
                style = TvTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                toneMappingPresetOptions().forEach { preset ->
                    ScanOptionChip(
                        text = toneMappingPresetLabel(preset),
                        selected = ruleSet.toApproximatePreset() == preset,
                        enabled = true,
                        onClick = { onToneMappingPresetSelected(ruleKey, preset) },
                        modifier = Modifier.width(120.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            StatusMessage(
                icon = Icons.Filled.CheckCircle,
                text = ruleSet.summaryLabel(),
                color = if (ruleSet.enabled) ProgressGreen else TextSecondary
            )
            Spacer(Modifier.height(14.dp))
        }
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
            .tvFocusableClickable(
                interactionSource = interactionSource,
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

@Composable
private fun AppUpdatePanel(
    state: AppUpdateUiState,
    onCheck: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onOpenInstallPermission: () -> Unit,
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
            Text(text = settingsAppUpdatePanelTitleLabel(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = settingsAppUpdatePanelDescription(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvButton(
                text = settingsAppUpdateCheckActionLabel(),
                icon = Icons.Filled.Refresh,
                enabled = !state.isBusy,
                onClick = onCheck,
                modifier = Modifier.fillMaxWidth()
            )
            TvButton(
                text = settingsAppUpdateInstallActionLabel(),
                icon = Icons.Filled.Save,
                enabled = !state.isBusy && state.latest != null,
                onClick = onDownloadAndInstall,
                modifier = Modifier.fillMaxWidth()
            )
            TvButton(
                text = settingsAppUpdatePermissionActionLabel(),
                icon = Icons.Filled.Key,
                enabled = !state.isBusy,
                onClick = onOpenInstallPermission,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val latest = state.latest
        if (latest != null) {
            StatusMessage(
                icon = if (state.updateAvailable) Icons.Filled.Refresh else Icons.Filled.CheckCircle,
                text = "${latest.releaseName} · ${latest.assetName} · ${formatByteSize(latest.assetSizeBytes)}",
                color = if (state.updateAvailable) WarningYellow else ProgressGreen
            )
        }
        StatusMessage(
            icon = when {
                state.isBusy -> Icons.Filled.Refresh
                state.updateAvailable -> Icons.Filled.Refresh
                else -> Icons.Filled.CheckCircle
            },
            text = state.statusMessage,
            color = when {
                state.statusMessage.contains("失败") || state.statusMessage.contains("无法") -> WarningYellow
                state.updateAvailable -> WarningYellow
                else -> ProgressGreen
            }
        )
    }
}

@Composable
private fun AboutPanel(
    info: AppAboutInfo,
    onRestartApp: () -> Unit,
    onExitApp: () -> Unit,
) {
    val tiles = aboutSettingsTiles(
        appName = info.appName,
        versionName = info.versionName,
        versionCode = info.versionCode,
        packageName = info.packageName,
    )

    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = settingsAboutPanelTitleLabel(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = settingsAboutPanelDescription(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.forEach { tile ->
                AboutInfoRow(label = tile.label, value = tile.value)
            }
            AboutInfoRow(
                label = settingsAboutPackageNameLabel(),
                value = info.packageName.ifBlank { settingsAboutUnknownValue() },
            )
        }

        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvButton(
                text = "重启应用",
                icon = Icons.Filled.Refresh,
                onClick = onRestartApp,
                modifier = Modifier.fillMaxWidth()
            )
            TvButton(
                text = "退出应用",
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                onClick = onExitApp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = settingsAboutStatusMessage(info.versionName, info.versionCode),
            color = ProgressGreen
        )
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TvTypography.caption,
            color = TextSecondary,
            modifier = Modifier.width(112.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value.ifBlank { settingsAboutUnknownValue() },
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProxySettingsPanel(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    statusMessage: String,
    onSave: () -> Unit,
) {
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Dns,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = settingsProxyPanelTitleLabel(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = settingsProxyPanelDescription(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        ScanOptionChip(
            text = settingsProxyToggleLabel(enabled),
            icon = Icons.Filled.Dns,
            selected = enabled,
            enabled = true,
            onClick = { onEnabledChange(!enabled) },
            modifier = Modifier.width(180.dp)
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvTextField(
                value = host,
                onValueChange = onHostChange,
                label = settingsProxyHostFieldLabel(),
                modifier = Modifier.weight(1f)
            )
            TvTextField(
                value = port,
                onValueChange = onPortChange,
                label = settingsProxyPortFieldLabel(),
                modifier = Modifier.width(160.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        TvButton(
            text = settingsProxySaveActionLabel(),
            icon = Icons.Filled.Save,
            enabled = !enabled || host.isNotBlank(),
            onClick = onSave
        )

        StatusMessage(
            icon = if (enabled) Icons.Filled.CheckCircle else Icons.Filled.Dns,
            text = settingsProxyCurrentStatus(enabled, host, parseRssProxyPort(port)),
            color = if (enabled && host.isBlank()) WarningYellow else TextSecondary
        )
        StatusMessage(
            icon = Icons.Filled.Refresh,
            text = statusMessage,
            color = if ("失败" in statusMessage || "请" in statusMessage) WarningYellow else ProgressGreen
        )
    }
}

@Composable
private fun MetadataPanel(
    savedToken: String,
    tokenInput: String,
    tokenSaved: Boolean,
    onTokenChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
    savedTmdbToken: String,
    tmdbTokenInput: String,
    tmdbTokenSaved: Boolean,
    onTmdbTokenChange: (String) -> Unit,
    onSaveTmdbToken: () -> Unit,
    onClearTmdbToken: () -> Unit,
    archiveState: BangumiArchiveUiState,
    onRefreshArchive: () -> Unit,
    onDownloadArchive: () -> Unit,
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
            Text(text = metadataPanelTitleLabel(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = metadataBangumiTokenOptionalHint(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = tokenInput,
            onValueChange = onTokenChange,
            label = metadataBangumiTokenFieldLabel(),
            isPassword = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = settingsSaveTokenActionLabel(),
                icon = Icons.Filled.Save,
                enabled = tokenInput.isNotBlank(),
                onClick = onSaveToken
            )
            TvButton(
                text = settingsClearTokenActionLabel(),
                icon = Icons.Filled.Delete,
                enabled = savedToken.isNotBlank(),
                onClick = onClearToken
            )
        }

        val hasToken = savedToken.isNotBlank() || tokenSaved
        StatusMessage(
            icon = if (hasToken) Icons.Filled.CheckCircle else Icons.Filled.Key,
            text = if (hasToken) metadataBangumiTokenSavedStatus() else metadataBangumiTokenMissingStatus(),
            color = if (hasToken) ProgressGreen else TextSecondary
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = metadataTmdbTokenOptionalHint(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = tmdbTokenInput,
            onValueChange = onTmdbTokenChange,
            label = metadataTmdbTokenFieldLabel(),
            isPassword = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = settingsSaveTokenActionLabel(),
                icon = Icons.Filled.Save,
                enabled = tmdbTokenInput.isNotBlank(),
                onClick = onSaveTmdbToken
            )
            TvButton(
                text = settingsClearTokenActionLabel(),
                icon = Icons.Filled.Delete,
                enabled = savedTmdbToken.isNotBlank(),
                onClick = onClearTmdbToken
            )
        }

        val hasTmdbToken = savedTmdbToken.isNotBlank() || tmdbTokenSaved
        StatusMessage(
            icon = if (hasTmdbToken) Icons.Filled.CheckCircle else Icons.Filled.Key,
            text = if (hasTmdbToken) metadataTmdbTokenSavedStatus() else metadataTmdbTokenMissingStatus(),
            color = if (hasTmdbToken) ProgressGreen else TextSecondary
        )
    }

    BangumiArchivePanel(
        state = archiveState,
        onRefresh = onRefreshArchive,
        onDownload = onDownloadArchive,
    )
}

@Composable
private fun BangumiArchivePanel(
    state: BangumiArchiveUiState,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
) {
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "Bangumi Archive", style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "离线索引用于本地快速搜索 Bangumi 条目；系统会每 ${state.autoUpdateIntervalDays} 天自动更新一次。",
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = if (state.hasSubjectData) "离线搜索可用" else "未下载",
                icon = Icons.Filled.CheckCircle,
                selected = state.hasSubjectData,
                enabled = false,
                onClick = {},
                modifier = Modifier.width(170.dp)
            )
            ScanOptionChip(
                text = if (state.autoUpdateEnabled) "每周自动更新" else "自动更新关闭",
                icon = Icons.Filled.Refresh,
                selected = state.autoUpdateEnabled,
                enabled = false,
                onClick = {},
                modifier = Modifier.width(180.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        StatusMessage(
            icon = Icons.Filled.Storage,
            text = "版本 ${state.latestName ?: "未知"} · ${formatByteSize(state.subjectFileSizeBytes)} · ${formatArchiveTimestamp(state.latestUpdatedAt ?: state.latestCreatedAt)}",
            color = TextSecondary
        )
        if (state.isDownloading) {
            StatusMessage(
                icon = Icons.Filled.Refresh,
                text = archiveProgressLabel(state),
                color = TextSecondary
            )
        }
        if (!state.lastError.isNullOrBlank()) {
            StatusMessage(
                icon = Icons.Filled.Close,
                text = state.lastError,
                color = WarningYellow
            )
        }
        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = state.statusMessage,
            color = if (state.statusMessage.contains("失败")) WarningYellow else ProgressGreen
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = if (state.isDownloading) "下载中" else if (state.hasSubjectData) "更新 Archive" else "下载 Archive",
                icon = Icons.Filled.Download,
                enabled = state.available && !state.isDownloading,
                onClick = onDownload
            )
            TvButton(
                text = "刷新状态",
                icon = Icons.Filled.Refresh,
                enabled = !state.isDownloading,
                onClick = onRefresh
            )
        }
    }
}

@Composable
private fun LogUploadPanel(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    endpoint: String,
    onEndpointChange: (String) -> Unit,
    tokenConfigured: Boolean,
    statusMessage: String,
    onSaveSettings: () -> Unit,
    onClearToken: () -> Unit,
    onRunNow: () -> Unit,
    canRunNow: Boolean,
) {
    val tiles = logUploadSettingsTiles()
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Upload,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = MiruPlaySettingsSection.LOG_UPLOAD.androidTvTitle, style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = MiruPlaySettingsSection.LOG_UPLOAD.androidTvDescription,
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = settingsLogUploadAutoToggleLabel(),
                icon = Icons.Filled.Refresh,
                selected = enabled,
                enabled = true,
                onClick = { onEnabledChange(!enabled) },
                modifier = Modifier.width(170.dp)
            )
            ScanOptionChip(
                text = settingsLogUploadTokenConfiguredStatus(tokenConfigured),
                icon = Icons.Filled.CheckCircle,
                selected = tokenConfigured,
                enabled = false,
                onClick = {},
                modifier = Modifier.width(170.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = endpoint,
            onValueChange = onEndpointChange,
            label = settingsLogUploadEndpointFieldLabel(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TvButton(
                    text = settingsLogUploadSaveSettingsActionLabel(),
                    icon = Icons.Filled.Save,
                    enabled = endpoint.isNotBlank(),
                    onClick = onSaveSettings,
                    modifier = Modifier.weight(1f)
                )
                TvButton(
                    text = settingsLogUploadRunNowActionLabel(),
                    icon = Icons.Filled.Upload,
                    enabled = canRunNow,
                    onClick = onRunNow,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TvButton(
                    text = settingsClearTokenActionLabel(),
                    icon = Icons.Filled.Delete,
                    enabled = tokenConfigured,
                    onClick = onClearToken,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        StatusMessage(
            icon = if (tokenConfigured) Icons.Filled.CheckCircle else Icons.Filled.Key,
            text = settingsLogUploadTokenConfiguredStatus(tokenConfigured),
            color = if (tokenConfigured) ProgressGreen else TextSecondary
        )
        StatusMessage(
            icon = if ("失败" in statusMessage) Icons.Filled.Close else Icons.Filled.Upload,
            text = statusMessage,
            color = if ("失败" in statusMessage) WarningYellow else ProgressGreen
        )
        StatusMessage(
            icon = Icons.Filled.Dns,
            text = tiles.firstOrNull()?.detail.orEmpty(),
            color = TextSecondary
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

private fun formatByteSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "未知大小"
    val mib = sizeBytes.toDouble() / 1024.0 / 1024.0
    return String.format(Locale.US, "%.1f MB", mib)
}

private fun formatArchiveTimestamp(value: String?): String =
    value?.takeIf { it.isNotBlank() }
        ?.take(16)
        ?.replace('T', ' ')
        ?: "未知时间"

private fun archiveProgressLabel(state: BangumiArchiveUiState): String {
    val downloaded = formatByteSize(state.downloadedBytes)
    val total = state.totalBytes.takeIf { it > 0L }?.let(::formatByteSize)
    return if (total == null) "正在下载：$downloaded" else "正在下载：$downloaded / $total"
}

internal fun settingsSourceListMenuBridgeIntent(
    intent: MiruPlayInputIntent,
    type: KeyEventType,
    onFocusMenu: () -> Unit,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    if (intent != MiruPlayInputIntent.DirectionLeft) return false
    onFocusMenu()
    return true
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
    return name ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
        ?: mediaSourceLocalLibraryFallbackName()
}

private fun displayNameForLocation(location: String): String =
    if (location.startsWith("content://")) {
        displayNameForTreeUri(Uri.parse(location))
    } else {
        mediaSourceLocalPathDisplayName(location)
    }
