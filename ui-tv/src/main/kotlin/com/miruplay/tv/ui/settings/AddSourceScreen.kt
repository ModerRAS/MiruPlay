package com.miruplay.tv.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.res.stringResource
import com.miruplay.tv.repository.AppLanguage
import com.miruplay.tv.repository.UpdateChannel
import com.miruplay.tv.repository.canRunNow
import com.miruplay.tv.core.common.rawUiText
import com.miruplay.tv.core.common.uiText
import com.miruplay.tv.ui.common.resolve
import com.miruplay.tv.ui.tv.R
import com.miruplay.tv.core.common.UiMessage
import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.model.playbackSubtitlesMenuTitle
import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.toMediaContentMode
import com.miruplay.tv.model.EpisodeVersionSelectionPolicy
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.SubtitleLanguagePreference
import com.miruplay.tv.model.normalizeSupportedBackend
import com.miruplay.tv.model.supportedPlaybackRenderBackends
import com.miruplay.tv.model.PosterWallArrangement
import com.miruplay.tv.model.CLOUD_DRIVE_ROOT_PATH
import com.miruplay.tv.model.localStorageRootDisplayName
import com.miruplay.tv.model.cloudDriveRootDisplayName
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.AudioDspChannelTarget
import com.miruplay.tv.model.AudioDspConfig
import com.miruplay.tv.model.MusicSrcBypassMode
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaRecognitionMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProxyConfig
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
import com.miruplay.tv.model.preferredSubtitleLanguageSettingsDescriptionLabel
import com.miruplay.tv.model.preferredSubtitleLanguageSettingsTitleLabel
import com.miruplay.tv.model.subtitleBackgroundSettingsDescriptionLabel
import com.miruplay.tv.model.subtitleBackgroundSettingsOptionLabel
import com.miruplay.tv.model.perfMonitorSettingsDescriptionLabel
import com.miruplay.tv.model.perfMonitorSettingsOptionLabel
import com.miruplay.tv.model.perfMonitorSettingsTitleLabel
import com.miruplay.tv.model.perfMonitorToggleLabel
import com.miruplay.tv.model.subtitleBackgroundSettingsTitleLabel
import com.miruplay.tv.model.subtitleBackgroundTransparentToggleLabel
import com.miruplay.tv.model.displayLabel
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
import com.miruplay.tv.model.settingsAppUpdateAvailableStatus
import com.miruplay.tv.model.settingsAppUpdateBusyStatus
import com.miruplay.tv.model.settingsAppUpdateChannelLabel
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
        MiruPlaySettingsSection.AUDIO_DSP -> Icons.Filled.GraphicEq
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
    val context = LocalContext.current
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
    val episodeVersionSelectionPolicy by viewModel.episodeVersionSelectionPolicy.collectAsStateWithLifecycle()
    val preferredSubtitleLanguage by viewModel.preferredSubtitleLanguage.collectAsStateWithLifecycle()
    val subtitleBackgroundTransparent by viewModel.subtitleBackgroundTransparent.collectAsStateWithLifecycle()
    val perfMonitorEnabled by viewModel.perfMonitorEnabled.collectAsStateWithLifecycle()
    val formatAwareToneMappingPreferences by viewModel.formatAwareToneMappingPreferences.collectAsStateWithLifecycle()
    val audioDspConfig by viewModel.audioDspConfig.collectAsStateWithLifecycle()
    val audioMeasure by viewModel.audioMeasure.collectAsStateWithLifecycle()
    val musicSrcBypassMode by viewModel.musicSrcBypassMode.collectAsStateWithLifecycle()
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
    val bangumiSyncState by viewModel.bangumiSyncState.collectAsStateWithLifecycle()
    val proxyStatusMessage by viewModel.proxyStatusMessage.collectAsStateWithLifecycle()
    val appAboutInfo = rememberAppAboutInfo()

    var selectedSection by remember { mutableStateOf(MiruPlaySettingsSection.SOURCES) }
    var contentHasFocus by remember { mutableStateOf(false) }
    var editingSourceId by remember { mutableStateOf<Long?>(null) }
    var selectedType by remember { mutableStateOf(MediaSourceType.LOCAL) }
    var selectedContentMode by remember { mutableStateOf(MediaContentMode.ANIME) }
    var selectedRecognitionMode by remember { mutableStateOf(MediaRecognitionMode.DIRECTORY) }
    var name by remember { mutableStateOf(sourceNameOrDefault("", MediaSourceType.LOCAL, context)) }
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
    var calibrationWizardOpen by rememberSaveable { mutableStateOf(false) }

    val menuFocusRequesters = remember {
        androidTvSettingsSectionOrder.associateWith { FocusRequester() }
    }
    val selectedMenuFocusRequester = menuFocusRequesters.getValue(selectedSection)

    LaunchedEffect(Unit) {
        selectedMenuFocusRequester.requestFocus()
    }

    BackHandler {
        if (contentHasFocus) {
            selectedMenuFocusRequester.requestFocus()
        } else {
            onNavigateBack()
        }
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
        name = sourceNameOrDefault("", type, context)
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
        name = source.name.ifBlank { sourceNameOrDefault("", source.type, context) }
        location = source.sourceLocation().orEmpty()
        locationDisplayName = source.connectionDisplayName().ifBlank {
            if (source.type == MediaSourceType.LOCAL) displayNameForLocation(location, context) else ""
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
            name = sourceNameOrDefault(name, selectedType, context),
            type = selectedType,
            contentMode = selectedContentMode,
            connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
                type = selectedType,
                location = location,
                displayName = locationDisplayName,
                username = username,
                password = password,
                recognitionMode = effectiveRecognitionMode,
            ) + buildMap {
                if (selectedType == MediaSourceType.ANI && rssProxyEnabled && rssProxyHost.isNotBlank()) {
                    val proxyPort = parseRssProxyPort(rssProxyPort)
                    if (proxyPort > 0) {
                        put(MediaSourceInfoConventions.CONNECTION_PROXY_ENABLED, "true")
                        put(MediaSourceInfoConventions.CONNECTION_PROXY_HOST, rssProxyHost.trim())
                        put(MediaSourceInfoConventions.CONNECTION_PROXY_PORT, proxyPort.toString())
                    }
                }
            }
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                    preferredSubtitleLanguage = preferredSubtitleLanguage,
                    formatAwareToneMappingPreferences = formatAwareToneMappingPreferences,
                    audioDspEnabled = audioDspConfig.enabled,
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
                    menuFocusRequester = selectedMenuFocusRequester,
                    selectedType = selectedType,
                    onTypeSelected = { type ->
                        if (type != selectedType) {
                            editingSourceId = null
                            selectedType = type
                            selectedContentMode = currentAppMode.toMediaContentMode()
                            selectedRecognitionMode = MediaRecognitionMode.DIRECTORY
                            name = sourceNameOrDefault("", type, context)
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
                        if (it != MediaContentMode.ANIME) {
                            selectedRecognitionMode = MediaRecognitionMode.DIRECTORY
                        }
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
                        val proxy = if (selectedType == MediaSourceType.ANI) {
                            ProxyConfig(
                                enabled = rssProxyEnabled,
                                host = rssProxyHost.trim(),
                                port = parseRssProxyPort(rssProxyPort),
                            )
                        } else {
                            null
                        }
                        viewModel.testConnection(selectedType, location, username, password, proxy)
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
                    episodeVersionSelectionPolicy = episodeVersionSelectionPolicy,
                    onEpisodeVersionSelectionPolicySelected = viewModel::setEpisodeVersionSelectionPolicy,
                    preferredSubtitleLanguage = preferredSubtitleLanguage,
                    onPreferredSubtitleLanguageSelected = viewModel::setPreferredSubtitleLanguage,
                    subtitleBackgroundTransparent = subtitleBackgroundTransparent,
                    onSubtitleBackgroundTransparentChange = viewModel::setSubtitleBackgroundTransparent,
                    perfMonitorEnabled = perfMonitorEnabled,
                    onPerfMonitorEnabledChange = viewModel::setPerfMonitorEnabled,
                    formatAwareToneMappingPreferences = formatAwareToneMappingPreferences,
                    onPlaybackBackendSelected = viewModel::setDefaultPlaybackBackend,
                    onToneMappingPresetSelected = viewModel::setToneMappingPreset,
                    audioDspConfig = audioDspConfig,
                    onAudioDspEnabledChange = viewModel::setAudioDspEnabled,
                    onAudioDspPresetSelected = viewModel::setAudioDspPreset,
                    audioMeasure = audioMeasure,
                    onProbeAudioMeasure = viewModel::probeAudioMeasure,
                    onStartSweepMeasurement = viewModel::startSweepMeasurement,
                    onImportWavMeasurement = viewModel::importWavMeasurement,
                    onApplyMeasuredResult = viewModel::applyMeasuredResult,
                    onClearAudioMeasureResult = viewModel::clearAudioMeasureResult,
                    onDownloadCalibration = viewModel::downloadCalibration,
                    onOpenCalibrationWizard = { calibrationWizardOpen = true },
                    musicSrcBypassMode = musicSrcBypassMode,
                    onMusicSrcBypassModeSelected = viewModel::setMusicSrcBypassMode,
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
                    onSyncAll = viewModel::syncBangumiAll,
                    syncState = bangumiSyncState,
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
                    onSetAppUpdateChannel = viewModel::setAppUpdateChannel,
                    bangumiArchiveState = bangumiArchiveState,
                    onRefreshBangumiArchive = viewModel::refreshBangumiArchive,
                    onDownloadBangumiArchive = viewModel::downloadBangumiArchive,
                    appAboutInfo = appAboutInfo,
                    onRestartApp = onRestartApp,
                    onExitApp = onExitApp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { contentHasFocus = it.hasFocus }
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
                        if (name.isBlank() || name == MediaSourceType.LOCAL.defaultSourceName().resolve(context)) {
                            name = locationDisplayName.ifBlank { mediaSourceLocalLibraryFallbackName().resolve(context) }
                        }
                        viewModel.clearTestResult()
                        viewModel.closeLocalDirectoryPicker()
                    }
                )
            }
        }
    }

    if (calibrationWizardOpen) {
        RoomCalibrationWizard(
            audioMeasure = audioMeasure,
            config = audioDspConfig,
            onProbeAudioMeasure = viewModel::probeAudioMeasure,
            onStartSweepMeasurement = viewModel::startSweepMeasurement,
            onSelectMic = viewModel::selectAudioMeasureMic,
            onApplyMeasuredResult = viewModel::applyMeasuredResult,
            onSetPinkNoise = viewModel::setPinkNoise,
            onClose = { calibrationWizardOpen = false },
        )
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
                text = settingsScreenTitleLabel().resolve(),
                style = TvTypography.title,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = settingsScreenSubtitleLabel().resolve(),
                style = TvTypography.body,
                color = TextSecondary
            )
        }
        TvButton(
            text = settingsBackActionLabel().resolve(),
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
    preferredSubtitleLanguage: SubtitleLanguagePreference,
    formatAwareToneMappingPreferences: FormatAwareToneMappingPreferences,
    audioDspEnabled: Boolean,
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
        playbackSummary = UiText(
            "settings_menu_playback_summary",
            listOf(
                playbackEndAction.playbackEndMenuSummary().resolve(),
                preferredSubtitleLanguage.displayLabel().resolve(),
                playbackSubtitlesMenuTitle().resolve(),
                playbackBackendLabel(formatAwareToneMappingPreferences.defaultBackend).resolve(),
            ),
            "${playbackEndAction.playbackEndMenuSummary().fallback} · ${preferredSubtitleLanguage.displayLabel().fallback}${playbackSubtitlesMenuTitle().fallback} · ${playbackBackendLabel(formatAwareToneMappingPreferences.defaultBackend).fallback}",
        ),
        audioDspEnabled = audioDspEnabled,
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
            appUpdateBusy -> settingsAppUpdateBusyStatus()
            appUpdateAvailable -> settingsAppUpdateAvailableStatus()
            else -> settingsAppUpdateMenuSummary()
        },
    )

    SettingsPanel(modifier = modifier) {
        Text(
            text = settingsMenuPanelTitle().resolve(),
            style = TvTypography.subtitle,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = settingsMenuPanelDescriptionAndroidTv().resolve(),
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
    summary: UiText,
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
                text = section.androidTvTitle.resolve(),
                style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = summary.resolve(),
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
    episodeVersionSelectionPolicy: EpisodeVersionSelectionPolicy,
    onEpisodeVersionSelectionPolicySelected: (EpisodeVersionSelectionPolicy) -> Unit,
    preferredSubtitleLanguage: SubtitleLanguagePreference,
    onPreferredSubtitleLanguageSelected: (SubtitleLanguagePreference) -> Unit,
    subtitleBackgroundTransparent: Boolean,
    onSubtitleBackgroundTransparentChange: (Boolean) -> Unit,
    perfMonitorEnabled: Boolean,
    onPerfMonitorEnabledChange: (Boolean) -> Unit,
    formatAwareToneMappingPreferences: FormatAwareToneMappingPreferences,
    onPlaybackBackendSelected: (PlaybackRenderBackend) -> Unit,
    onToneMappingPresetSelected: (VideoRenderRuleKey, ToneMappingProfilePreset) -> Unit,
    audioDspConfig: AudioDspConfig,
    onAudioDspEnabledChange: (Boolean) -> Unit,
    onAudioDspPresetSelected: (String) -> Unit,
    audioMeasure: SettingsViewModel.AudioMeasureUiState,
    onProbeAudioMeasure: () -> Unit,
    onStartSweepMeasurement: () -> Unit,
    onImportWavMeasurement: (Uri) -> Unit,
    onApplyMeasuredResult: (AudioDspChannelTarget) -> Unit,
    onClearAudioMeasureResult: () -> Unit,
    onDownloadCalibration: (String, String) -> Unit,
    onOpenCalibrationWizard: () -> Unit,
    musicSrcBypassMode: MusicSrcBypassMode,
    onMusicSrcBypassModeSelected: (MusicSrcBypassMode) -> Unit,
    savedToken: String,
    tokenInput: String,
    tokenSaved: Boolean,
    onTokenChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
    onSyncAll: () -> Unit,
    syncState: BangumiSyncAllUiState,
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
    cloudDriveActionMessage: UiMessage?,
    canPickCloudDriveDirectory: Boolean,
    onPickCloudInboxPath: () -> Unit,
    onPickCloudLibraryPath: () -> Unit,
    rssProxyEnabled: Boolean,
    onRssProxyEnabledChange: (Boolean) -> Unit,
    rssProxyHost: String,
    onRssProxyHostChange: (String) -> Unit,
    rssProxyPort: String,
    onRssProxyPortChange: (String) -> Unit,
    proxyStatusMessage: UiMessage,
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
    logUploadStatusMessage: UiMessage,
    onSaveLogUploadSettings: () -> Unit,
    onClearLogUploadToken: () -> Unit,
    onRunLogUploadNow: () -> Unit,
    canRunLogUploadNow: Boolean,
    appUpdateState: AppUpdateUiState,
    onCheckAppUpdate: () -> Unit,
    onDownloadAndInstallAppUpdate: () -> Unit,
    onOpenAppUpdateInstallPermission: () -> Unit,
    onSetAppUpdateChannel: (UpdateChannel) -> Unit,
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
                episodeVersionSelectionPolicy = episodeVersionSelectionPolicy,
                onEpisodeVersionSelectionPolicySelected = onEpisodeVersionSelectionPolicySelected,
                preferredSubtitleLanguage = preferredSubtitleLanguage,
                onPreferredSubtitleLanguageSelected = onPreferredSubtitleLanguageSelected,
                subtitleBackgroundTransparent = subtitleBackgroundTransparent,
                onSubtitleBackgroundTransparentChange = onSubtitleBackgroundTransparentChange,
                perfMonitorEnabled = perfMonitorEnabled,
                onPerfMonitorEnabledChange = onPerfMonitorEnabledChange,
                formatAwareToneMappingPreferences = formatAwareToneMappingPreferences,
                onPlaybackBackendSelected = onPlaybackBackendSelected,
                onToneMappingPresetSelected = onToneMappingPresetSelected,
                musicSrcBypassMode = musicSrcBypassMode,
                onMusicSrcBypassModeSelected = onMusicSrcBypassModeSelected,
            )
        }

        MiruPlaySettingsSection.AUDIO_DSP -> SettingsSingleSectionPage(
            section = selectedSection,
            modifier = modifier
        ) {
            ToppingDacControls()
            AudioDspPanel(
                config = audioDspConfig,
                onEnabledChange = onAudioDspEnabledChange,
                onPresetSelected = onAudioDspPresetSelected,
                audioMeasure = audioMeasure,
                onProbeAudioMeasure = onProbeAudioMeasure,
                onStartSweepMeasurement = onStartSweepMeasurement,
                onImportWavMeasurement = onImportWavMeasurement,
                onApplyMeasuredResult = onApplyMeasuredResult,
                onClearAudioMeasureResult = onClearAudioMeasureResult,
                onDownloadCalibration = onDownloadCalibration,
                onOpenCalibrationWizard = onOpenCalibrationWizard,
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
                onSelectChannel = onSetAppUpdateChannel,
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
                onSyncAll = onSyncAll,
                syncState = syncState,
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
                text = section.androidTvTitle.resolve(),
                style = TvTypography.title,
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = section.androidTvDescription.resolve(),
            style = TvTypography.body,
            color = TextSecondary
        )
        if (section == MiruPlaySettingsSection.SCAN || section == MiruPlaySettingsSection.PLAYBACK) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_forms_save_hint),
                style = TvTypography.caption,
                color = ProgressGreen,
            )
        }
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
            Text(text = mediaSourceListTitleLabel().resolve(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (sources.isEmpty()) {
                mediaSourceEmptyListMessage().resolve()
            } else {
                mediaSourceConfiguredCountLabel(sources.size).resolve()
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
                text = stringResource(R.string.settings_forms_add_first),
                style = TvTypography.body,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_forms_mumu_hint),
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
                text = source.tvDisplayName(fallbackName = source.type.tvLabel().resolve()),
                color = TextPrimary,
                style = TvTypography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = source.tvDisplayStatusLabel().resolve(),
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
            contentDescription = stringResource(R.string.settings_forms_delete_desc),
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
    var showAccessToken by remember(accessToken) { mutableStateOf(false) }
    val displayedAccessToken = if (showAccessToken || accessToken.isBlank()) {
        accessToken
    } else {
        "••••••••${accessToken.takeLast(4)}"
    }

    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.WifiTethering,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = settingsWebUiPanelTitleLabel().resolve(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = settingsWebUiPanelDescription().resolve(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = settingsWebUiToggleActionLabel(enabled).resolve(),
                icon = Icons.Filled.WifiTethering,
                onClick = onToggleEnabled,
                modifier = Modifier.width(156.dp)
            )
            TvButton(
                text = settingsWebUiRotateTokenActionLabel().resolve(),
                icon = Icons.Filled.Key,
                onClick = onRotateToken,
                enabled = enabled,
                modifier = Modifier.width(150.dp)
            )
            TvButton(
                text = settingsWebUiRefreshAddressActionLabel().resolve(),
                icon = Icons.Filled.Refresh,
                onClick = onRefresh,
                enabled = enabled,
                modifier = Modifier.width(150.dp)
            )
            TvButton(
                text = if (showAccessToken) stringResource(R.string.settings_forms_hide_token) else stringResource(R.string.settings_forms_show_token),
                icon = Icons.Filled.Key,
                onClick = { showAccessToken = !showAccessToken },
                enabled = accessToken.isNotBlank(),
                modifier = Modifier.width(140.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = settingsWebUiAccessTokenLabel(displayedAccessToken).resolve(),
            style = TvTypography.caption,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!enabled) {
            StatusMessage(
                icon = Icons.Filled.Close,
                text = settingsWebUiDisabledStatus().resolve(),
                color = WarningYellow
            )
        } else if (urls.isEmpty()) {
            StatusMessage(
                icon = Icons.Filled.Refresh,
                text = settingsWebUiNoLanAddressStatus().resolve(),
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
                        text = settingsWebUiAvailableAddressesLabel().resolve(),
                        style = TvTypography.caption,
                        color = TextSecondary
                    )
                    urls.forEachIndexed { index, url ->
                        WebUiMenuItem(
                            url = url,
                            label = settingsWebUiAddressLabel(index).resolve(),
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
                        text = settingsWebUiQrOpenLabel().resolve(),
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
                    text = (if (state.target == CloudDriveDirectoryTarget.INBOX) {
                        cloudDriveRssInboxDirectoryPickerTitle()
                    } else {
                        cloudDriveRssLibraryDirectoryPickerTitle()
                    }).resolve(),
                    style = TvTypography.subtitle,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                TvButton(
                    text = cloudDriveRssCloseActionLabel().resolve(),
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onDismiss
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ScanOptionChip(
                    text = cloudDriveRssParentDirectoryActionLabel().resolve(),
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    selected = false,
                    enabled = state.parentPath != null,
                    onClick = { state.parentPath?.let(onNavigate) },
                    modifier = Modifier.width(140.dp)
                )
                Text(
                    text = if (state.path.isBlank()) {
                        localStorageRootDisplayName().resolve()
                    } else {
                        state.displayPath.ifBlank { localStorageRootDisplayName().resolve() }
                    },
                    style = TvTypography.body,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.isLoading) {
                Text(text = cloudDriveRssLoadingDirectoriesMessage().resolve(), color = TextSecondary, style = TvTypography.body)
            } else if (state.entries.isEmpty()) {
                Text(text = cloudDriveRssEmptyDirectoryMessage().resolve(), color = TextSecondary, style = TvTypography.body)
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

            val cloudDirectoryMessage = state.message?.resolve()
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
                    text = cloudDriveRssCloseActionLabel().resolve(),
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onDismiss
                )
                TvButton(
                    text = cloudDriveRssUseCurrentDirectoryActionLabel().resolve(),
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
                    text = directoryBrowserTitleLabel(isLocal = true).resolve(),
                    style = TvTypography.subtitle,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                TvButton(
                    text = directoryBrowserCloseActionLabel().resolve(),
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onDismiss
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ScanOptionChip(
                    text = directoryBrowserParentActionLabel(isLocal = true).resolve(),
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    selected = false,
                    enabled = state.parentPath != null,
                    onClick = { state.parentPath?.let(onNavigate) },
                    modifier = Modifier.width(140.dp)
                )
                Text(
                    text = if (state.path == CLOUD_DRIVE_ROOT_PATH) {
                        cloudDriveRootDisplayName().resolve()
                    } else {
                        state.displayPath.ifBlank { cloudDriveRootDisplayName().resolve() }
                    },
                    style = TvTypography.body,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.isLoading) {
                Text(text = directoryBrowserLoadingMessage(isLocal = true).resolve(), color = TextSecondary, style = TvTypography.body)
            } else if (state.entries.isEmpty()) {
                Text(text = directoryBrowserEmptyMessage(isLocal = true).resolve(), color = TextSecondary, style = TvTypography.body)
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

            val localDirectoryMessage = state.message?.resolve()
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
                    text = directoryBrowserCancelActionLabel().resolve(),
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onDismiss
                )
                TvButton(
                    text = directoryBrowserUseCurrentActionLabel(isLocal = true).resolve(),
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
    actionMessage: UiMessage?,
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
            Text(text = cloudDriveRssTitleLabel().resolve(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = cloudDriveRssDescriptionLabel().resolve(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = cloudDriveRssScheduledChipLabel(enabled).resolve(),
                icon = Icons.Filled.Refresh,
                selected = enabled,
                enabled = true,
                onClick = onToggleEnabled,
                modifier = Modifier.width(150.dp)
            )
            ScanOptionChip(
                text = cloudDriveRssCredentialsBadgeLabel(tokenConfigured, passwordConfigured).resolve(),
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
                text = cloudDriveRssPasswordCredentialLabel(passwordConfigured).resolve(),
                icon = Icons.Filled.Key,
                selected = passwordConfigured,
                enabled = false,
                onClick = {},
                modifier = Modifier.weight(1f)
            )
            ScanOptionChip(
                text = cloudDriveRssApiTokenCredentialLabel(tokenConfigured).resolve(),
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
            label = cloudDriveRssEndpointFieldLabel().resolve(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = cloudDriveRssUsernameFieldLabel().resolve(),
                modifier = Modifier.weight(1f)
            )
            TvTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = cloudDriveRssPasswordFieldLabel().resolve(),
                isPassword = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = apiToken,
            onValueChange = onApiTokenChange,
            label = cloudDriveRssApiTokenFieldLabel().resolve(),
            isPassword = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = cloudDriveRssLibraryModeOrganizedLabel().resolve(),
                icon = Icons.Filled.Folder,
                selected = cloudLibraryMode == CloudDriveLibraryMode.ORGANIZED_LIBRARY,
                enabled = true,
                onClick = { onCloudLibraryModeChange(CloudDriveLibraryMode.ORGANIZED_LIBRARY) },
                modifier = Modifier.weight(1f)
            )
            ScanOptionChip(
                text = cloudDriveRssLibraryModeSingleDirectoryLabel().resolve(),
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
            label = cloudDriveRssInboxPathFieldLabel().resolve(),
            canPick = canPickCloudDriveDirectory,
            onPick = onPickCloudInboxPath,
            modifier = Modifier.fillMaxWidth()
        )
        if (cloudLibraryMode == CloudDriveLibraryMode.ORGANIZED_LIBRARY) {
            Spacer(Modifier.height(12.dp))
            CloudDrivePathSelectorField(
                value = libraryPath,
                onValueChange = onLibraryPathChange,
                label = cloudDriveRssLibraryPathFieldLabel().resolve(),
                canPick = canPickCloudDriveDirectory,
                onPick = onPickCloudLibraryPath,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = intervalMinutes,
            onValueChange = onIntervalMinutesChange,
            label = cloudDriveRssIntervalMinutesFieldLabel().resolve(),
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
                text = cloudDriveRssSaveConfigActionLabel().resolve(),
                icon = Icons.Filled.Save,
                enabled = endpoint.isNotBlank(),
                onClick = onSave
            )
            TvButton(
                text = cloudDriveRssLoginActionLabel(busy).resolve(),
                icon = Icons.Filled.Key,
                enabled = !busy && endpoint.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                onClick = onLogin
            )
            TvButton(
                text = cloudDriveRssSaveApiTokenActionLabel().resolve(),
                icon = Icons.Filled.Key,
                enabled = apiToken.isNotBlank(),
                onClick = onSaveApiToken
            )
            TvButton(
                text = cloudDriveRssSaveAndRunNowActionLabel(busy).resolve(),
                icon = Icons.Filled.Refresh,
                enabled = !busy && endpoint.isNotBlank() && runnableCredentials,
                onClick = onRunNow
            )
        }

        StatusMessage(
            icon = if (credentialConfigured) Icons.Filled.CheckCircle else Icons.Filled.Cloud,
            text = cloudDriveRssTokenStatusMessage(tokenConfigured, passwordConfigured).resolve(),
            color = if (credentialConfigured) ProgressGreen else TextSecondary
        )
        actionMessage?.let { message ->
            StatusMessage(
                icon = Icons.Filled.Refresh,
                text = message.text.resolve(),
                color = if (message is UiMessage.Error) WarningYellow else ProgressGreen
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
            text = cloudDriveRssChooseDirectoryActionLabel().resolve(),
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
            text = cloudDriveRssScanSourceTitleLabel().resolve(),
            style = TvTypography.caption,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        if (sources.isEmpty()) {
            StatusMessage(
                icon = Icons.Filled.Storage,
                text = cloudDriveRssNoWebDavSourceMessage().resolve(),
                color = WarningYellow
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CloudDriveWebDavSourceChip(
                    text = cloudDriveRssNoScanSourceOptionLabel().resolve(),
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
            Text(text = rssSubscriptionsTitleLabel().resolve(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = name,
            onValueChange = onNameChange,
            label = rssSubscriptionNameFieldLabel().resolve(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = url,
            onValueChange = onUrlChange,
            label = rssSubscriptionUrlFieldLabel().resolve(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        TvTextField(
            value = filterRegex,
            onValueChange = onFilterRegexChange,
            label = rssSubscriptionFilterRegexFieldLabel().resolve(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = rssSubscriptionNewEnabledLabel(enabled).resolve(),
                selected = enabled,
                enabled = true,
                onClick = onToggleEnabled,
                modifier = Modifier.width(150.dp)
            )
            TvButton(
                text = rssSubscriptionAddActionLabel().resolve(),
                icon = Icons.Filled.Add,
                enabled = url.isNotBlank(),
                onClick = onAdd
            )
        }

        Spacer(Modifier.height(18.dp))
        if (subscriptions.isEmpty()) {
            Text(
                text = rssSubscriptionEmptyMessage().resolve(),
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
                ).resolve(),
                style = TvTypography.caption,
                color = TextSecondary
            )
        }
        TvButton(
            text = rssSubscriptionStateActionLabel(subscription.enabled).resolve(),
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
    val context = LocalContext.current
    SettingsPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaSourceFormTitleLabel(isEditing).resolve(),
                    style = TvTypography.subtitle,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = mediaSourceFormDescriptionLabel(isEditing).resolve(),
                    style = TvTypography.body,
                    color = TextSecondary
                )
            }
            if (isEditing) {
                TvButton(
                    text = mediaSourceNewActionLabel().resolve(),
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
            label = mediaSourceDisplayNameFieldLabel().resolve(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = mediaSourceContentModeTitleLabel().resolve(),
            style = TvTypography.caption,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaContentMode.entries.forEach { mode ->
                ScanOptionChip(
                    text = mediaSourceContentModeLabel(mode).resolve(),
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
            text = mediaSourceContentModeHint(selectedContentMode).resolve(),
            color = ProgressGreen
        )

        if (selectedType == MediaSourceType.WEBDAV && selectedContentMode == MediaContentMode.ANIME) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_forms_recognition_title),
                style = TvTypography.caption,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScanOptionChip(
                    text = stringResource(R.string.settings_forms_dir_scan),
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
                    stringResource(R.string.settings_forms_mlip_desc)
                } else {
                    stringResource(R.string.settings_forms_dir_desc)
                },
                color = if (selectedRecognitionMode == MediaRecognitionMode.MLIP) ProgressGreen else TextSecondary
            )
            if (selectedRecognitionMode == MediaRecognitionMode.MLIP) {
                StatusMessage(
                    icon = Icons.Filled.Info,
                    text = stringResource(R.string.settings_forms_mlip_readonly),
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (selectedType == MediaSourceType.LOCAL) {
            LocalFolderPickerRow(
                displayName = locationDisplayName.ifBlank { displayNameForLocation(location, context) },
                location = location,
                onPickFolder = onPickLocalFolder
            )
        } else {
            TvTextField(
                value = location,
                onValueChange = onLocationChange,
                label = selectedType.tvLocationLabel().resolve(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (selectedType != MediaSourceType.LOCAL) {
            Spacer(Modifier.height(12.dp))
            TvTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = mediaSourceUsernameOptionalFieldLabel().resolve(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            TvTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = mediaSourcePasswordOptionalFieldLabel(isEditing).resolve(),
                isPassword = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = mediaSourceTestConnectionActionLabel(testResult is ConnectionTestResult.Testing).resolve(),
                icon = Icons.Filled.WifiTethering,
                enabled = testResult !is ConnectionTestResult.Testing,
                onClick = onTestConnection,
                modifier = Modifier.weight(1f)
            )
            TvButton(
                text = mediaSourceSaveActionLabel(isEditing).resolve(),
                icon = Icons.Filled.Save,
                enabled = location.isNotBlank(),
                onClick = onSave,
                modifier = Modifier.weight(1f)
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
            text = MediaSourceType.LOCAL.tvLocationLabel().resolve(),
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
                        text = displayName.ifBlank { mediaSourceLocalFolderEmptyLabel().resolve() },
                        style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (location.startsWith("content://")) mediaSourceLocalFolderAuthorizedLabel().resolve() else location,
                        style = TvTypography.caption,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TvButton(
                text = mediaSourceChooseFolderActionLabel().resolve(),
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
            text = type.tvLabel().resolve(),
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = type.tvSourceHint().resolve(),
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
            text = mediaSourceConnectionSuccessMessage().resolve(),
            color = ProgressGreen
        )
        is ConnectionTestResult.Failed -> StatusMessage(
            icon = Icons.Filled.Refresh,
            text = result.message.resolve(),
            color = WarningYellow
        )
        is ConnectionTestResult.Testing -> StatusMessage(
            icon = Icons.Filled.WifiTethering,
            text = mediaSourceConnectionTestingMessage().resolve(),
            color = TextSecondary
        )
        null -> Unit
    }
}

@Composable
private fun AppLanguageSection(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val current by viewModel.appLanguage.collectAsStateWithLifecycle()
    Spacer(Modifier.height(18.dp))
    Text(
        text = stringResource(R.string.settings_app_language_title),
        style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
        color = TextPrimary
    )
    Spacer(Modifier.height(10.dp))
    AppLanguage.entries.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            row.forEach { language ->
                ScanOptionChip(
                    text = language.autonym.ifBlank { stringResource(R.string.settings_app_language_follow_system) },
                    icon = Icons.Filled.Language,
                    selected = current == language,
                    enabled = true,
                    onClick = { viewModel.setAppLanguage(language) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    StatusMessage(
        icon = Icons.Filled.Info,
        text = stringResource(R.string.settings_app_language_hint),
        color = TextSecondary
    )
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
            Text(text = settingsScanPanelTitleLabel().resolve(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = settingsScanPanelDescription().resolve(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = settingsAppModeTitleLabel().resolve(),
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppMode.entries.forEach { mode ->
                ScanOptionChip(
                    text = settingsAppModeOptionLabel(mode.storageValue).resolve(),
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
            text = settingsAppModeStatus(currentAppMode.storageValue).resolve(),
            color = ProgressGreen
        )
        StatusMessage(
            icon = Icons.Filled.Info,
            text = settingsAppModeHint().resolve(),
            color = TextSecondary
        )

        AppLanguageSection()

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = settingsAutoScanToggleLabel(autoScanEnabled).resolve(),
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
                    text = settingsScanIntervalOptionLabel(hours).resolve(),
                    selected = autoScanEnabled && hours == autoScanIntervalHours,
                    enabled = autoScanEnabled,
                    onClick = { onIntervalSelected(hours) },
                    modifier = Modifier.width(112.dp)
                )
            }
        }

        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = settingsCurrentScanIntervalStatus(autoScanIntervalHours, lastScanAt).resolve(),
            color = if (autoScanEnabled) ProgressGreen else TextSecondary
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = settingsLibraryDisplayTitleLabel().resolve(),
            style = TvTypography.body.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = settingsPosterWallArrangementTitleLabel().resolve(),
            style = TvTypography.caption,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosterWallArrangement.entries.forEach { arrangement ->
                ScanOptionChip(
                    text = posterWallArrangementLabel(arrangement).resolve(),
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
            text = posterWallArrangementStatus(posterWallArrangement).resolve(),
            color = if (posterWallArrangement == PosterWallArrangement.RELEASE_SEASON) ProgressGreen else TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = settingsMergeSameAnimeToggleLabel(mergeSameAnimeEnabled).resolve(),
                icon = Icons.Filled.Dns,
                selected = mergeSameAnimeEnabled,
                enabled = true,
                onClick = onToggleMergeSameAnime,
                modifier = Modifier.width(150.dp)
            )
        }
        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = settingsMergeSameAnimeStatus(mergeSameAnimeEnabled).resolve(),
            color = if (mergeSameAnimeEnabled) ProgressGreen else TextSecondary
        )
    }
}

@Composable
private fun PlaybackPanel(
    endAction: PlaybackEndAction,
    onEndActionSelected: (PlaybackEndAction) -> Unit,
    episodeVersionSelectionPolicy: EpisodeVersionSelectionPolicy,
    onEpisodeVersionSelectionPolicySelected: (EpisodeVersionSelectionPolicy) -> Unit,
    preferredSubtitleLanguage: SubtitleLanguagePreference,
    onPreferredSubtitleLanguageSelected: (SubtitleLanguagePreference) -> Unit,
    subtitleBackgroundTransparent: Boolean,
    onSubtitleBackgroundTransparentChange: (Boolean) -> Unit,
    perfMonitorEnabled: Boolean,
    onPerfMonitorEnabledChange: (Boolean) -> Unit,
    formatAwareToneMappingPreferences: FormatAwareToneMappingPreferences,
    onPlaybackBackendSelected: (PlaybackRenderBackend) -> Unit,
    onToneMappingPresetSelected: (VideoRenderRuleKey, ToneMappingProfilePreset) -> Unit,
    musicSrcBypassMode: MusicSrcBypassMode,
    onMusicSrcBypassModeSelected: (MusicSrcBypassMode) -> Unit,
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
            Text(text = playbackEndSettingsTitleLabel().resolve(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = playbackEndSettingsDescriptionLabel().resolve(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = playbackEndReturnToDetailActionLabel().resolve(),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                selected = endAction == PlaybackEndAction.RETURN_TO_DETAIL,
                enabled = true,
                onClick = { onEndActionSelected(PlaybackEndAction.RETURN_TO_DETAIL) },
                modifier = Modifier.width(160.dp)
            )
            ScanOptionChip(
                text = playbackEndPlayNextEpisodeActionLabel().resolve(),
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
                PlaybackEndAction.RETURN_TO_DETAIL -> playbackEndReturnToDetailDetail().resolve()
                PlaybackEndAction.PLAY_NEXT_EPISODE -> playbackEndPlayNextEpisodeDetail().resolve()
            },
            color = if (endAction == PlaybackEndAction.PLAY_NEXT_EPISODE) ProgressGreen else TextSecondary
        )

        MusicSrcBypassTvControls(
            mode = musicSrcBypassMode,
            onModeSelected = onMusicSrcBypassModeSelected,
        )

        Spacer(Modifier.height(24.dp))
        Text(text = stringResource(R.string.settings_version_policy_title), style = TvTypography.subtitle, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_version_policy_desc),
            style = TvTypography.body,
            color = TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = stringResource(R.string.settings_version_policy_auto),
                icon = Icons.Filled.PlayArrow,
                selected = episodeVersionSelectionPolicy == EpisodeVersionSelectionPolicy.AUTO_NEAREST,
                enabled = true,
                onClick = {
                    onEpisodeVersionSelectionPolicySelected(EpisodeVersionSelectionPolicy.AUTO_NEAREST)
                },
                modifier = Modifier.width(220.dp),
            )
            ScanOptionChip(
                text = stringResource(R.string.settings_version_policy_manual),
                icon = Icons.Filled.FolderOpen,
                selected = episodeVersionSelectionPolicy == EpisodeVersionSelectionPolicy.MANUAL,
                enabled = true,
                onClick = { onEpisodeVersionSelectionPolicySelected(EpisodeVersionSelectionPolicy.MANUAL) },
                modifier = Modifier.width(220.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = preferredSubtitleLanguageSettingsTitleLabel().resolve(),
                style = TvTypography.subtitle,
                color = TextPrimary,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = preferredSubtitleLanguageSettingsDescriptionLabel().resolve(),
            style = TvTypography.body,
            color = TextSecondary,
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SubtitleLanguagePreference.entries.forEach { preference ->
                ScanOptionChip(
                    text = preference.displayLabel().resolve(),
                    icon = Icons.Filled.Language,
                    selected = preferredSubtitleLanguage == preference,
                    enabled = true,
                    onClick = { onPreferredSubtitleLanguageSelected(preference) },
                    modifier = Modifier.width(120.dp),
                )
            }
        }

        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = UiText("subtitle_language_current_priority", listOf(preferredSubtitleLanguage.displayLabel()), "当前优先：${preferredSubtitleLanguage.displayLabel().fallback}").resolve(),
            color = if (preferredSubtitleLanguage == SubtitleLanguagePreference.AUTO) TextSecondary else ProgressGreen,
        )

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Subtitles,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = subtitleBackgroundSettingsTitleLabel().resolve(),
                style = TvTypography.subtitle,
                color = TextPrimary,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitleBackgroundSettingsDescriptionLabel().resolve(),
            style = TvTypography.body,
            color = TextSecondary,
        )

        Spacer(Modifier.height(14.dp))
        ScanOptionChip(
            text = subtitleBackgroundTransparentToggleLabel().resolve(),
            icon = Icons.Filled.Subtitles,
            selected = subtitleBackgroundTransparent,
            enabled = true,
            onClick = { onSubtitleBackgroundTransparentChange(!subtitleBackgroundTransparent) },
            modifier = Modifier.width(150.dp),
        )

        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = UiText("subtitle_background_current", listOf(subtitleBackgroundSettingsOptionLabel(subtitleBackgroundTransparent)), "当前：${subtitleBackgroundSettingsOptionLabel(subtitleBackgroundTransparent).fallback}").resolve(),
            color = if (subtitleBackgroundTransparent) ProgressGreen else TextSecondary,
        )

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = perfMonitorSettingsTitleLabel().resolve(),
                style = TvTypography.subtitle,
                color = TextPrimary,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = perfMonitorSettingsDescriptionLabel().resolve(),
            style = TvTypography.body,
            color = TextSecondary,
        )

        Spacer(Modifier.height(14.dp))
        ScanOptionChip(
            text = perfMonitorToggleLabel().resolve(),
            icon = Icons.Filled.Tune,
            selected = perfMonitorEnabled,
            enabled = true,
            onClick = { onPerfMonitorEnabledChange(!perfMonitorEnabled) },
            modifier = Modifier.width(150.dp),
        )

        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = UiText("perf_monitor_current", listOf(perfMonitorSettingsOptionLabel(perfMonitorEnabled)), "当前：${perfMonitorSettingsOptionLabel(perfMonitorEnabled).fallback}").resolve(),
            color = if (perfMonitorEnabled) ProgressGreen else TextSecondary,
        )

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = perfMonitorSettingsTitleLabel().resolve(),
                style = TvTypography.subtitle,
                color = TextPrimary,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = perfMonitorSettingsDescriptionLabel().resolve(),
            style = TvTypography.body,
            color = TextSecondary,
        )

        Spacer(Modifier.height(14.dp))
        ScanOptionChip(
            text = perfMonitorToggleLabel().resolve(),
            icon = Icons.Filled.Tune,
            selected = perfMonitorEnabled,
            enabled = true,
            onClick = { onPerfMonitorEnabledChange(!perfMonitorEnabled) },
            modifier = Modifier.width(150.dp),
        )

        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = UiText("perf_monitor_current", listOf(perfMonitorSettingsOptionLabel(perfMonitorEnabled)), "当前：${perfMonitorSettingsOptionLabel(perfMonitorEnabled).fallback}").resolve(),
            color = if (perfMonitorEnabled) ProgressGreen else TextSecondary,
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
            Text(text = pictureSettingsTitleLabel().resolve(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = pictureSettingsDescriptionLabel().resolve(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.picture_default_backend),
            style = TvTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            supportedPlaybackRenderBackends().forEach { backend ->
                ScanOptionChip(
                    text = playbackBackendLabel(backend).resolve(),
                    icon = Icons.Filled.PlayArrow,
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
                text = videoRenderRuleLabel(ruleKey).resolve(),
                style = TvTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                toneMappingPresetOptions().forEach { preset ->
                    ScanOptionChip(
                        text = toneMappingPresetLabel(preset).resolve(),
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
                text = ruleSet.summaryLabel().resolve(),
                color = if (ruleSet.enabled) ProgressGreen else TextSecondary
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun AudioDspPanel(
    config: AudioDspConfig,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit,
    audioMeasure: SettingsViewModel.AudioMeasureUiState,
    onProbeAudioMeasure: () -> Unit,
    onStartSweepMeasurement: () -> Unit,
    onImportWavMeasurement: (Uri) -> Unit,
    onApplyMeasuredResult: (AudioDspChannelTarget) -> Unit,
    onClearAudioMeasureResult: () -> Unit,
    onDownloadCalibration: (String, String) -> Unit,
    onOpenCalibrationWizard: () -> Unit,
) {
    SettingsPanel {
        AudioDspTvControls(
            config = config,
            onEnabledChange = onEnabledChange,
            onPresetSelected = onPresetSelected,
            audioMeasure = audioMeasure,
            onProbeAudioMeasure = onProbeAudioMeasure,
            onStartSweepMeasurement = onStartSweepMeasurement,
            onImportWavMeasurement = onImportWavMeasurement,
            onApplyMeasuredResult = onApplyMeasuredResult,
            onClearAudioMeasureResult = onClearAudioMeasureResult,
            onDownloadCalibration = onDownloadCalibration,
            onOpenCalibrationWizard = onOpenCalibrationWizard,
        )
    }
}

@Composable
private fun AudioDspTvControls(
    config: AudioDspConfig,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit,
    audioMeasure: SettingsViewModel.AudioMeasureUiState,
    onProbeAudioMeasure: () -> Unit,
    onStartSweepMeasurement: () -> Unit,
    onImportWavMeasurement: (Uri) -> Unit,
    onApplyMeasuredResult: (AudioDspChannelTarget) -> Unit,
    onClearAudioMeasureResult: () -> Unit,
    onDownloadCalibration: (String, String) -> Unit,
    onOpenCalibrationWizard: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = stringResource(R.string.settings_audio_dsp_title), style = TvTypography.subtitle, color = TextPrimary)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.settings_audio_dsp_desc),
        style = TvTypography.body,
        color = TextSecondary,
    )
    Spacer(Modifier.height(14.dp))
    ScanOptionChip(
        text = if (config.enabled) stringResource(R.string.settings_audio_dsp_on) else stringResource(R.string.settings_audio_dsp_off),
        icon = Icons.Filled.GraphicEq,
        selected = config.enabled,
        enabled = true,
        onClick = { onEnabledChange(!config.enabled) },
        modifier = Modifier.width(190.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.settings_audio_dsp_preset),
        style = TvTypography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = TextSecondary,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        config.presets.forEach { preset ->
            ScanOptionChip(
                text = preset.name,
                icon = Icons.Filled.Tune,
                selected = config.selectedPresetId == preset.id,
                enabled = true,
                onClick = { onPresetSelected(preset.id) },
                modifier = Modifier.width(150.dp),
            )
        }
    }
    StatusMessage(
        icon = Icons.Filled.CheckCircle,
        text = if (config.enabled) {
            stringResource(R.string.settings_audio_dsp_current, config.presets.firstOrNull { it.id == config.selectedPresetId }?.name ?: config.selectedPresetId)
        } else {
            stringResource(R.string.settings_audio_dsp_bypass)
        },
        color = if (config.enabled) ProgressGreen else TextSecondary,
    )
    Spacer(Modifier.height(18.dp))
    Text(
        text = stringResource(R.string.measure_sweep_title),
        style = TvTypography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = TextSecondary,
    )
    Spacer(Modifier.height(8.dp))
    val context = LocalContext.current
    var micPermissionDenied by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            micPermissionDenied = false
            onProbeAudioMeasure()
            onStartSweepMeasurement()
        } else {
            micPermissionDenied = true
        }
    }
    val startSweepWithPermission = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionDenied = false
            onProbeAudioMeasure()
            onStartSweepMeasurement()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScanOptionChip(
            text = if (audioMeasure.measuring) audioMeasure.progress?.resolve() ?: stringResource(R.string.measure_in_progress) else stringResource(R.string.measure_start_sweep),
            icon = Icons.Filled.GraphicEq,
            selected = false,
            enabled = !audioMeasure.measuring,
            onClick = startSweepWithPermission,
            modifier = Modifier.width(220.dp),
        )
        ScanOptionChip(
            text = stringResource(R.string.measure_wizard_title),
            icon = Icons.Filled.Settings,
            selected = false,
            enabled = true,
            onClick = onOpenCalibrationWizard,
            modifier = Modifier.width(190.dp),
        )
        val wavPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(onImportWavMeasurement)
        }
        ScanOptionChip(
            text = stringResource(R.string.measure_import_wav),
            icon = Icons.Filled.FileDownload,
            selected = false,
            enabled = !audioMeasure.measuring,
            onClick = {
                wavPicker.launch(arrayOf("audio/wav", "audio/x-wav", "application/octet-stream"))
            },
            modifier = Modifier.width(180.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    var serialInput by rememberSaveable { mutableStateOf("") }
    var downloadIncidence by rememberSaveable { mutableStateOf("0deg") }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        TvTextField(
            value = serialInput,
            onValueChange = { serialInput = it },
            label = stringResource(R.string.measure_umik_serial_label),
            isPassword = false,
            modifier = Modifier.width(280.dp),
        )
        ScanOptionChip(
            text = if (downloadIncidence == "0deg") stringResource(R.string.measure_incidence_0) else stringResource(R.string.measure_incidence_90),
            icon = Icons.Filled.Tune,
            selected = downloadIncidence == "90deg",
            enabled = true,
            onClick = { downloadIncidence = if (downloadIncidence == "0deg") "90deg" else "0deg" },
            modifier = Modifier.width(140.dp),
        )
        ScanOptionChip(
            text = if (audioMeasure.downloadingCalibration) stringResource(R.string.measure_downloading) else stringResource(R.string.measure_download_minidsp),
            icon = Icons.Filled.Download,
            selected = false,
            enabled = !audioMeasure.downloadingCalibration && serialInput.isNotBlank(),
            onClick = { onDownloadCalibration(serialInput, downloadIncidence) },
            modifier = Modifier.width(200.dp),
        )
    }
    StatusMessage(
        icon = if (audioMeasure.calibrationName != null) Icons.Filled.CheckCircle else Icons.Filled.Info,
        text = if (audioMeasure.calibrationName != null) {
            stringResource(R.string.measure_calibration_current, audioMeasure.calibrationName, audioMeasure.calibrationCount)
        } else {
            stringResource(R.string.measure_calibration_missing)
        },
        color = if (audioMeasure.calibrationName != null) ProgressGreen else TextSecondary,
    )
    if (micPermissionDenied) {
        Spacer(Modifier.height(8.dp))
        StatusMessage(
            icon = Icons.Filled.Error,
            text = stringResource(R.string.measure_mic_permission_denied),
            color = Color(0xFFE57373),
        )
    }
    audioMeasure.error?.let { error ->
        Spacer(Modifier.height(8.dp))
        StatusMessage(
            icon = Icons.Filled.Error,
            text = error.text.resolve(),
            color = Color(0xFFE57373),
        )
    }
    audioMeasure.capabilities?.let { caps ->
        if (!caps.available) {
            Spacer(Modifier.height(8.dp))
            StatusMessage(
                icon = Icons.Filled.Info,
                text = stringResource(R.string.measure_mic_unavailable, caps.reason ?: stringResource(R.string.measure_unknown_reason)),
                color = TextSecondary,
            )
        } else if (caps.inputDeviceName != null) {
            Spacer(Modifier.height(8.dp))
            StatusMessage(
                icon = Icons.Filled.Info,
                text = stringResource(R.string.measure_input_device, caps.inputDeviceName ?: ""),
                color = TextSecondary,
            )
        }
    }
    audioMeasure.result?.let { result ->
        Spacer(Modifier.height(10.dp))
        if (result.valid) {
            StatusMessage(
                icon = Icons.Filled.CheckCircle,
                text = stringResource(
                    R.string.measure_result_valid,
                    "%.1f".format(result.estimatedPpm),
                    result.bands.size,
                    result.matchLoHz.toInt(),
                    result.matchHiHz.toInt(),
                    "%.2f".format(result.peakAfterDb),
                ),
                color = ProgressGreen,
            )
            Spacer(Modifier.height(8.dp))
            result.bands.sortedBy { it.frequencyHz }.forEach { band ->
                Text(
                    text = String.format(
                        "%.1f Hz   %+.2f dB   Q %.2f",
                        band.frequencyHz, band.gainDb, band.q,
                    ),
                    style = TvTypography.caption,
                    color = TextPrimary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScanOptionChip(
                    text = stringResource(R.string.measure_apply_preset),
                    icon = Icons.Filled.CheckCircle,
                    selected = false,
                    enabled = true,
                    onClick = { onApplyMeasuredResult(AudioDspChannelTarget.ALL) },
                    modifier = Modifier.width(240.dp),
                )
                ScanOptionChip(
                    text = stringResource(R.string.measure_discard),
                    icon = Icons.Filled.Close,
                    selected = false,
                    enabled = true,
                    onClick = onClearAudioMeasureResult,
                    modifier = Modifier.width(140.dp),
                )
            }
        } else {
            StatusMessage(
                icon = Icons.Filled.Error,
                text = stringResource(R.string.measure_result_invalid, result.invalidReason ?: stringResource(R.string.measure_unknown_reason)),
                color = Color(0xFFE57373),
            )
        }
    }
}

@Composable
private fun MusicSrcBypassTvControls(
    mode: MusicSrcBypassMode,
    onModeSelected: (MusicSrcBypassMode) -> Unit,
) {
    Spacer(Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Audiotrack,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = stringResource(R.string.settings_music_src_title), style = TvTypography.subtitle, color = TextPrimary)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.settings_music_src_desc),
        style = TvTypography.body,
        color = TextSecondary,
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScanOptionChip(
            text = stringResource(R.string.settings_music_src_system),
            icon = Icons.Filled.Settings,
            selected = mode == MusicSrcBypassMode.SYSTEM,
            enabled = true,
            onClick = { onModeSelected(MusicSrcBypassMode.SYSTEM) },
            modifier = Modifier.width(130.dp),
        )
        ScanOptionChip(
            text = stringResource(R.string.settings_music_src_software),
            icon = Icons.Filled.GraphicEq,
            selected = mode == MusicSrcBypassMode.SOFTWARE,
            enabled = true,
            onClick = { onModeSelected(MusicSrcBypassMode.SOFTWARE) },
            modifier = Modifier.width(150.dp),
        )
        ScanOptionChip(
            text = stringResource(R.string.settings_music_src_direct),
            icon = Icons.Filled.Audiotrack,
            selected = mode == MusicSrcBypassMode.DIRECT,
            enabled = true,
            onClick = { onModeSelected(MusicSrcBypassMode.DIRECT) },
            modifier = Modifier.width(110.dp),
        )
    }
    StatusMessage(
        icon = Icons.Filled.CheckCircle,
        text = when (mode) {
            MusicSrcBypassMode.SYSTEM -> stringResource(R.string.settings_music_src_system_desc)
            MusicSrcBypassMode.SOFTWARE -> stringResource(R.string.settings_music_src_software_desc)
            MusicSrcBypassMode.DIRECT -> stringResource(R.string.settings_music_src_direct_desc)
        },
        color = if (mode == MusicSrcBypassMode.SOFTWARE) ProgressGreen else TextSecondary,
    )
}

@Composable
private fun ToppingDacControls(viewModel: SettingsViewModel = hiltViewModel()) {
    val topping by viewModel.topping.collectAsStateWithLifecycle()
    val audioDspConfig by viewModel.audioDspConfig.collectAsStateWithLifecycle()
    val status = topping.status
    SettingsPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Speaker,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(text = stringResource(R.string.settings_topping_title), style = TvTypography.subtitle, color = TextPrimary)
        }
        Spacer(Modifier.height(6.dp))
        when {
            status == null -> Text(
                text = stringResource(R.string.settings_topping_probing),
                style = TvTypography.body,
                color = TextSecondary,
            )
            !status.attached -> Text(
                text = status.reason ?: stringResource(R.string.settings_topping_not_found),
                style = TvTypography.body,
                color = TextSecondary,
            )
            else -> Column {
                Text(
                    text = stringResource(R.string.settings_topping_connected, status.model ?: "", if (!status.confirmed) stringResource(R.string.settings_topping_unconfirmed) else ""),
                    style = TvTypography.body,
                    color = if (status.confirmed) TextPrimary else TextSecondary,
                )
                if (!status.usbPermission) {
                    Spacer(Modifier.height(8.dp))
                    ScanOptionChip(
                        text = stringResource(R.string.settings_topping_grant_usb),
                        icon = Icons.Filled.Usb,
                        selected = false,
                        enabled = !topping.busy,
                        onClick = { viewModel.requestToppingUsbPermission() },
                        modifier = Modifier.width(190.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val volumeInput = rememberSaveable { mutableStateOf("-30") }
        var confirmLoud by rememberSaveable { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvTextField(
                value = volumeInput.value,
                onValueChange = { volumeInput.value = it },
                label = stringResource(R.string.settings_topping_volume_label),
                isPassword = false,
                modifier = Modifier.width(200.dp),
            )
            ScanOptionChip(
                text = stringResource(R.string.settings_topping_set_volume),
                icon = Icons.Filled.VolumeUp,
                selected = false,
                enabled = !topping.busy && status?.confirmed == true && status.hidVolume,
                onClick = {
                    volumeInput.value.toDoubleOrNull()?.let { viewModel.setToppingVolume(it, confirmLoud) }
                },
                modifier = Modifier.width(150.dp),
            )
            ScanOptionChip(
                text = stringResource(R.string.settings_topping_allow_loud),
                icon = Icons.Filled.Warning,
                selected = confirmLoud,
                enabled = true,
                onClick = { confirmLoud = !confirmLoud },
                modifier = Modifier.width(170.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        val preampInput = rememberSaveable { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvTextField(
                value = preampInput.value,
                onValueChange = { preampInput.value = it },
                label = stringResource(R.string.settings_topping_preamp_label),
                isPassword = false,
                modifier = Modifier.width(200.dp),
            )
            ScanOptionChip(
                text = stringResource(R.string.settings_topping_set_preamp),
                icon = Icons.Filled.Tune,
                selected = false,
                enabled = !topping.busy && status?.confirmed == true,
                onClick = {
                    preampInput.value.toDoubleOrNull()?.let { viewModel.setToppingPreamp(it) }
                },
                modifier = Modifier.width(160.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_topping_push_desc),
            style = TvTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            audioDspConfig.presets.forEach { preset ->
                ScanOptionChip(
                    text = stringResource(R.string.settings_topping_push, preset.name),
                    icon = Icons.Filled.Send,
                    selected = false,
                    enabled = !topping.busy && status?.confirmed == true,
                    onClick = { viewModel.pushToppingPreset(preset.id) },
                    modifier = Modifier.width(170.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = stringResource(R.string.settings_topping_flat),
                icon = Icons.Filled.Clear,
                selected = false,
                enabled = !topping.busy && status?.confirmed == true,
                onClick = { viewModel.flatTopping() },
                modifier = Modifier.width(160.dp),
            )
            ScanOptionChip(
                text = if (status?.gainOn == true) stringResource(R.string.settings_topping_gain_on) else stringResource(R.string.settings_topping_gain_off),
                icon = Icons.Filled.GraphicEq,
                selected = false,
                enabled = !topping.busy && status?.confirmed == true,
                onClick = { viewModel.setToppingGain(status?.gainOn != true) },
                modifier = Modifier.width(140.dp),
            )
            ScanOptionChip(
                text = stringResource(R.string.settings_topping_power),
                icon = Icons.Filled.PowerSettingsNew,
                selected = false,
                enabled = !topping.busy && status?.confirmed == true,
                onClick = { viewModel.setToppingPower(true) },
                modifier = Modifier.width(180.dp),
            )
        }
        val errorText = topping.error
        val messageText = topping.message
        if (errorText != null) {
            StatusMessage(icon = Icons.Filled.Warning, text = errorText.text.resolve(), color = AnimeRed)
        } else if (messageText != null) {
            StatusMessage(icon = Icons.Filled.CheckCircle, text = messageText.text.resolve(), color = ProgressGreen)
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
    onSelectChannel: (UpdateChannel) -> Unit,
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
            Text(text = settingsAppUpdatePanelTitleLabel().resolve(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = settingsAppUpdatePanelDescription().resolve(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        Text(text = settingsAppUpdateChannelLabel().resolve(), style = TvTypography.body, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UpdateChannel.entries.forEach { channel ->
                TvButton(
                    text = channel.id,
                    enabled = !state.isBusy,
                    secondary = state.channel != channel,
                    onClick = { onSelectChannel(channel) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvButton(
                text = settingsAppUpdateCheckActionLabel().resolve(),
                icon = Icons.Filled.Refresh,
                enabled = !state.isBusy,
                onClick = onCheck,
                modifier = Modifier.fillMaxWidth()
            )
            TvButton(
                text = settingsAppUpdateInstallActionLabel().resolve(),
                icon = Icons.Filled.Save,
                enabled = !state.isBusy && state.latest != null,
                onClick = onDownloadAndInstall,
                modifier = Modifier.fillMaxWidth()
            )
            TvButton(
                text = settingsAppUpdatePermissionActionLabel().resolve(),
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
                text = "${latest.releaseName} · ${latest.assetName} · ${formatByteSize(latest.assetSizeBytes).resolve()}",
                color = if (state.updateAvailable) WarningYellow else ProgressGreen
            )
        }
        StatusMessage(
            icon = when {
                state.isBusy -> Icons.Filled.Refresh
                state.updateAvailable -> Icons.Filled.Refresh
                else -> Icons.Filled.CheckCircle
            },
            text = state.statusMessage.text.resolve(),
            color = when {
                state.statusMessage is UiMessage.Error -> WarningYellow
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
            Text(text = settingsAboutPanelTitleLabel().resolve(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = settingsAboutPanelDescription().resolve(),
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
                value = info.packageName.ifBlank { "" }.ifBlank { settingsAboutUnknownValue().fallback }.let { rawUiText(it) },
            )
        }

        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvButton(
                text = stringResource(R.string.settings_restart_app),
                icon = Icons.Filled.Refresh,
                onClick = onRestartApp,
                modifier = Modifier.fillMaxWidth()
            )
            TvButton(
                text = stringResource(R.string.settings_exit_app),
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                onClick = onExitApp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = settingsAboutStatusMessage(info.versionName, info.versionCode).resolve(),
            color = ProgressGreen
        )
    }
}

@Composable
private fun AboutInfoRow(
    label: UiText,
    value: UiText,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.resolve(),
            style = TvTypography.caption,
            color = TextSecondary,
            modifier = Modifier.width(112.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value.resolve(),
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
    statusMessage: UiMessage,
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
            Text(text = settingsProxyPanelTitleLabel().resolve(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = settingsProxyPanelDescription().resolve(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        ScanOptionChip(
            text = settingsProxyToggleLabel(enabled).resolve(),
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
                label = settingsProxyHostFieldLabel().resolve(),
                modifier = Modifier.weight(1f)
            )
            TvTextField(
                value = port,
                onValueChange = onPortChange,
                label = settingsProxyPortFieldLabel().resolve(),
                modifier = Modifier.width(160.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        TvButton(
            text = settingsProxySaveActionLabel().resolve(),
            icon = Icons.Filled.Save,
            enabled = !enabled || host.isNotBlank(),
            onClick = onSave
        )

        StatusMessage(
            icon = if (enabled) Icons.Filled.CheckCircle else Icons.Filled.Dns,
            text = settingsProxyCurrentStatus(enabled, host, parseRssProxyPort(port)).resolve(),
            color = if (enabled && host.isBlank()) WarningYellow else TextSecondary
        )
        StatusMessage(
            icon = Icons.Filled.Refresh,
            text = statusMessage.text.resolve(),
            color = if (statusMessage is UiMessage.Error) WarningYellow else ProgressGreen
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
    onSyncAll: () -> Unit,
    syncState: BangumiSyncAllUiState,
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
            Text(text = metadataPanelTitleLabel().resolve(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = metadataBangumiTokenOptionalHint().resolve(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = tokenInput,
            onValueChange = onTokenChange,
            label = metadataBangumiTokenFieldLabel().resolve(),
            isPassword = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = settingsSaveTokenActionLabel().resolve(),
                icon = Icons.Filled.Save,
                enabled = tokenInput.isNotBlank(),
                onClick = onSaveToken
            )
            TvButton(
                text = settingsClearTokenActionLabel().resolve(),
                icon = Icons.Filled.Delete,
                enabled = savedToken.isNotBlank(),
                onClick = onClearToken
            )
        }

        val hasToken = savedToken.isNotBlank() || tokenSaved
        StatusMessage(
            icon = if (hasToken) Icons.Filled.CheckCircle else Icons.Filled.Key,
            text = if (hasToken) metadataBangumiTokenSavedStatus().resolve() else metadataBangumiTokenMissingStatus().resolve(),
            color = if (hasToken) ProgressGreen else TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = if (syncState.isRunning) stringResource(R.string.bangumi_sync_running) else stringResource(R.string.bangumi_sync_all),
                icon = Icons.Filled.Sync,
                enabled = hasToken && !syncState.isRunning,
                onClick = onSyncAll
            )
        }
        if (!syncState.isRunning && (syncState.animeCount > 0 || syncState.errorMessage != null)) {
            Spacer(Modifier.height(8.dp))
            StatusMessage(
                icon = if (syncState.errorMessage != null) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                text = syncState.errorMessage?.text?.resolve() ?: stringResource(
                    R.string.bangumi_sync_summary,
                    syncState.syncedCount,
                    syncState.failedCount,
                    syncState.totalPushedEpisodes,
                    syncState.totalPulledEpisodes,
                ),
                color = if (syncState.errorMessage != null || syncState.failedCount > 0) WarningYellow else ProgressGreen
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = metadataTmdbTokenOptionalHint().resolve(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        TvTextField(
            value = tmdbTokenInput,
            onValueChange = onTmdbTokenChange,
            label = metadataTmdbTokenFieldLabel().resolve(),
            isPassword = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = settingsSaveTokenActionLabel().resolve(),
                icon = Icons.Filled.Save,
                enabled = tmdbTokenInput.isNotBlank(),
                onClick = onSaveTmdbToken
            )
            TvButton(
                text = settingsClearTokenActionLabel().resolve(),
                icon = Icons.Filled.Delete,
                enabled = savedTmdbToken.isNotBlank(),
                onClick = onClearTmdbToken
            )
        }

        val hasTmdbToken = savedTmdbToken.isNotBlank() || tmdbTokenSaved
        StatusMessage(
            icon = if (hasTmdbToken) Icons.Filled.CheckCircle else Icons.Filled.Key,
            text = if (hasTmdbToken) metadataTmdbTokenSavedStatus().resolve() else metadataTmdbTokenMissingStatus().resolve(),
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
            text = stringResource(R.string.archive_index_desc, state.autoUpdateIntervalDays),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = if (state.hasSubjectData) stringResource(R.string.archive_index_available) else stringResource(R.string.archive_index_missing),
                icon = Icons.Filled.CheckCircle,
                selected = state.hasSubjectData,
                enabled = false,
                onClick = {},
                modifier = Modifier.width(170.dp)
            )
            ScanOptionChip(
                text = if (state.autoUpdateEnabled) stringResource(R.string.archive_auto_update_on) else stringResource(R.string.archive_auto_update_off),
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
            text = UiText("archive_version_line", listOf(state.latestName?.takeIf { it.isNotBlank() }?.let { rawUiText(it) } ?: settingsAboutUnknownValue(), formatByteSize(state.subjectFileSizeBytes), formatArchiveTimestamp(state.latestUpdatedAt ?: state.latestCreatedAt)), "版本 ${state.latestName ?: settingsAboutUnknownValue().fallback} · ${formatByteSize(state.subjectFileSizeBytes).fallback} · ${formatArchiveTimestamp(state.latestUpdatedAt ?: state.latestCreatedAt).fallback}").resolve(),
            color = TextSecondary
        )
        if (state.isDownloading) {
            StatusMessage(
                icon = Icons.Filled.Refresh,
                text = archiveProgressLabel(state).resolve(),
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
            text = state.statusMessage.resolve(),
            color = if (state.lastError != null) WarningYellow else ProgressGreen
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
                text = if (state.isDownloading) stringResource(R.string.archive_downloading) else if (state.hasSubjectData) stringResource(R.string.archive_update) else stringResource(R.string.archive_download),
                icon = Icons.Filled.Download,
                enabled = state.available && !state.isDownloading,
                onClick = onDownload
            )
            TvButton(
                text = stringResource(R.string.archive_refresh_status),
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
    statusMessage: UiMessage,
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
            Text(text = MiruPlaySettingsSection.LOG_UPLOAD.androidTvTitle.resolve(), style = TvTypography.subtitle, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = MiruPlaySettingsSection.LOG_UPLOAD.androidTvDescription.resolve(),
            style = TvTypography.body,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = settingsLogUploadAutoToggleLabel().resolve(),
                icon = Icons.Filled.Refresh,
                selected = enabled,
                enabled = true,
                onClick = { onEnabledChange(!enabled) },
                modifier = Modifier.width(170.dp)
            )
            ScanOptionChip(
                text = settingsLogUploadTokenConfiguredStatus(tokenConfigured).resolve(),
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
            label = settingsLogUploadEndpointFieldLabel().resolve(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TvButton(
                    text = settingsLogUploadSaveSettingsActionLabel().resolve(),
                    icon = Icons.Filled.Save,
                    enabled = endpoint.isNotBlank(),
                    onClick = onSaveSettings,
                    modifier = Modifier.weight(1f)
                )
                TvButton(
                    text = settingsLogUploadRunNowActionLabel().resolve(),
                    icon = Icons.Filled.Upload,
                    enabled = canRunNow,
                    onClick = onRunNow,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TvButton(
                    text = settingsClearTokenActionLabel().resolve(),
                    icon = Icons.Filled.Delete,
                    enabled = tokenConfigured,
                    onClick = onClearToken,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        StatusMessage(
            icon = if (tokenConfigured) Icons.Filled.CheckCircle else Icons.Filled.Key,
            text = settingsLogUploadTokenConfiguredStatus(tokenConfigured).resolve(),
            color = if (tokenConfigured) ProgressGreen else TextSecondary
        )
        StatusMessage(
            icon = if (statusMessage is UiMessage.Error) Icons.Filled.Close else Icons.Filled.Upload,
            text = statusMessage.text.resolve(),
            color = if (statusMessage is UiMessage.Error) WarningYellow else ProgressGreen
        )
        StatusMessage(
            icon = Icons.Filled.Dns,
            text = tiles.firstOrNull()?.detail?.resolve().orEmpty(),
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

private fun sourceNameOrDefault(name: String, type: MediaSourceType, context: Context): String =
    name.ifBlank { type.defaultSourceName().resolve(context) }

private fun formatByteSize(sizeBytes: Long): UiText {
    if (sizeBytes <= 0L) return uiText("format_byte_size_unknown", "未知大小")
    val mib = sizeBytes.toDouble() / 1024.0 / 1024.0
    val text = String.format(Locale.US, "%.1f MB", mib)
    return UiText("format_byte_size", listOf(text), text)
}

private fun formatArchiveTimestamp(value: String?): UiText {
    val text = value?.takeIf { it.isNotBlank() }
        ?.take(16)
        ?.replace('T', ' ')
        ?: return uiText("format_archive_time_unknown", "未知时间")
    return UiText("format_archive_time", listOf(text), text)
}

private fun archiveProgressLabel(state: BangumiArchiveUiState): UiText {
    val downloaded = formatByteSize(state.downloadedBytes)
    val total = state.totalBytes.takeIf { it > 0L }?.let(::formatByteSize)
    return if (total == null) {
        UiText("archive_progress_downloading", listOf(downloaded), "正在下载：${downloaded.fallback}")
    } else {
        UiText(
            "archive_progress_downloading_total",
            listOf(downloaded, total),
            "正在下载：${downloaded.fallback} / ${total.fallback}",
        )
    }
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



private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun MediaSourceType.sourceIcon(): ImageVector = when (this) {
    MediaSourceType.LOCAL -> Icons.Filled.Folder
    MediaSourceType.WEBDAV -> Icons.Filled.Cloud
    MediaSourceType.SMB -> Icons.Filled.Dns
    MediaSourceType.ANI -> Icons.Filled.LiveTv
}

private fun displayNameForTreeUri(uri: Uri, context: Context): String {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
    val name = documentId
        ?.substringAfter(':', "")
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
    return name ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
        ?: mediaSourceLocalLibraryFallbackName().resolve(context)
}

private fun displayNameForLocation(location: String, context: Context): String =
    if (location.startsWith("content://")) {
        displayNameForTreeUri(Uri.parse(location), context)
    } else {
        mediaSourceLocalPathDisplayName(location)
    }

// ---------------------------------------------------------------------------
// 房间校准向导（全屏，遥控器友好）：检查设备 → 粉红噪音对音量 → 对数扫频 →
// 频响曲线 + 一键校准 → 保存为自动命名的校准预设。复用 SettingsViewModel 的
// 测量流（startSweepMeasurement），大规模运算阶段用进行中界面告知等待。
// ---------------------------------------------------------------------------

private enum class CalibrationStep { DEVICE_CHECK, SWEEPING, RESULT, DONE }

@Composable
private fun RoomCalibrationWizard(
    audioMeasure: SettingsViewModel.AudioMeasureUiState,
    config: AudioDspConfig,
    onProbeAudioMeasure: () -> Unit,
    onStartSweepMeasurement: () -> Unit,
    onSelectMic: (Int) -> Unit,
    onApplyMeasuredResult: (AudioDspChannelTarget) -> Unit,
    onSetPinkNoise: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    // 遥控器焦点修复：overlay 打开时把焦点抓进向导（否则 D-pad 仍操作被遮挡的旧界面）
    val wizardFocusRequester = remember { FocusRequester() }
    var step by rememberSaveable {
        // 已有测量结果（如刚在别处导入/测量过）时直接展示结果页。
        mutableStateOf(
            if (audioMeasure.result != null) CalibrationStep.RESULT.name else CalibrationStep.DEVICE_CHECK.name,
        )
    }
    val currentStep = CalibrationStep.valueOf(step)

    fun close() {
        onSetPinkNoise(false)
        onClose()
    }

    BackHandler { close() }
    DisposableEffect(Unit) {
        onDispose { onSetPinkNoise(false) }
    }

    // 设备检查页进来自动探测麦克风/输出
    LaunchedEffect(currentStep) {
        if (currentStep == CalibrationStep.DEVICE_CHECK) onProbeAudioMeasure()
    }
    LaunchedEffect(Unit) {
        kotlin.runCatching { wizardFocusRequester.requestFocus() }
    }
    // 测量结束自动进入结果页
    LaunchedEffect(audioMeasure.measuring, audioMeasure.result) {
        if (currentStep == CalibrationStep.SWEEPING &&
            !audioMeasure.measuring &&
            audioMeasure.result != null
        ) {
            step = CalibrationStep.RESULT.name
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1016))
            .focusRequester(wizardFocusRequester)
            .focusable(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp, vertical = 40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = AnimeRed,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.measure_wizard_title),
                    style = TvTypography.title,
                    color = TextPrimary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (currentStep) {
                    CalibrationStep.DEVICE_CHECK -> stringResource(R.string.wizard_step_devices)
                    CalibrationStep.SWEEPING -> stringResource(R.string.wizard_step_sweep)
                    CalibrationStep.RESULT -> stringResource(R.string.wizard_step_result)
                    CalibrationStep.DONE -> stringResource(R.string.wizard_step_done)
                },
                style = TvTypography.subtitle,
                color = TextSecondary,
            )
            Spacer(Modifier.height(26.dp))

            when (currentStep) {
                CalibrationStep.DEVICE_CHECK -> DeviceCheckStep(
                    audioMeasure = audioMeasure,
                    onProbeAudioMeasure = onProbeAudioMeasure,
                    onStartSweep = { step = CalibrationStep.SWEEPING.name },
                    onStartSweepMeasurement = onStartSweepMeasurement,
                    onSelectMic = onSelectMic,
                    onSetPinkNoise = onSetPinkNoise,
                    onClose = { close() },
                )

                CalibrationStep.SWEEPING -> SweepingStep(
                    audioMeasure = audioMeasure,
                )

                CalibrationStep.RESULT -> ResultStep(
                    audioMeasure = audioMeasure,
                    onApplyMeasuredResult = {
                        onApplyMeasuredResult(AudioDspChannelTarget.ALL)
                        step = CalibrationStep.DONE.name
                    },
                    onRestart = { step = CalibrationStep.DEVICE_CHECK.name },
                    onClose = { close() },
                )

                CalibrationStep.DONE -> DoneStep(
                    config = config,
                    onClose = { close() },
                )
            }
        }
    }
}

@Composable
private fun DeviceCheckStep(
    audioMeasure: SettingsViewModel.AudioMeasureUiState,
    onProbeAudioMeasure: () -> Unit,
    onStartSweep: () -> Unit,
    onStartSweepMeasurement: () -> Unit,
    onSelectMic: (Int) -> Unit,
    onSetPinkNoise: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val caps = audioMeasure.capabilities
    val micAvailable = caps?.available == true
    // 状态行优先显示用户手选的麦克风（caps.inputDeviceName 只是控制器默认）
    val shownMicName = audioMeasure.selectedMicId
        ?.let { sel -> caps?.mics?.firstOrNull { it.id == sel }?.name }
        ?: caps?.inputDeviceName
    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onProbeAudioMeasure()
            onStartSweep()
            onStartSweepMeasurement()
        }
        // 拒绝：probe 会报告缺少权限原因，用户可重试或改用 WebUI 导入 WAV
    }
    val startSweepWithPermission = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onStartSweep()
            onStartSweepMeasurement()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    StatusMessage(
        icon = if (audioMeasure.outputDeviceName != null) Icons.Filled.CheckCircle else Icons.Filled.Info,
        text = audioMeasure.outputDeviceName?.let { stringResource(R.string.wizard_output_device, it) } ?: stringResource(R.string.wizard_output_unknown),
        color = if (audioMeasure.outputDeviceName != null) ProgressGreen else TextSecondary,
    )
    StatusMessage(
        icon = if (micAvailable) Icons.Filled.CheckCircle else Icons.Filled.Error,
        text = when {
            micAvailable -> stringResource(R.string.wizard_mic_ready, shownMicName ?: "")
            caps?.reason != null -> stringResource(R.string.wizard_mic_unavailable, caps.reason ?: "")
            else -> stringResource(R.string.wizard_mic_missing)
        },
        color = if (micAvailable) ProgressGreen else AccentBlue,
    )
    // 麦克风选择：DAC 自带 mic-in 与 UMIK 等同时在线时，默认枚举顺序可能选错，让用户直接改
    if (caps?.mics?.isNotEmpty() == true && micAvailable) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.wizard_mic_picker_title),
            style = TvTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            caps.mics.forEach { mic ->
                val isChosen = audioMeasure.selectedMicId == mic.id
                ScanOptionChip(
                    text = (if (isChosen) "✓ " else "") + mic.name,
                    icon = Icons.Filled.Audiotrack,
                    selected = isChosen,
                    enabled = true,
                    onClick = { onSelectMic(mic.id) },
                    modifier = Modifier.width(210.dp),
                )
            }
        }
    }
    StatusMessage(
        icon = Icons.Filled.Info,
        text = if (audioMeasure.calibrationName != null) {
            stringResource(R.string.wizard_mic_calibration, audioMeasure.calibrationName ?: "")
        } else {
            stringResource(R.string.wizard_mic_calibration_missing)
        },
        color = if (audioMeasure.calibrationName != null) ProgressGreen else TextSecondary,
    )

    Spacer(Modifier.height(22.dp))
    Text(
        text = stringResource(R.string.wizard_volume_align),
        style = TvTypography.body,
        color = TextSecondary,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScanOptionChip(
            text = if (audioMeasure.noisePlaying) stringResource(R.string.wizard_stop_noise) else stringResource(R.string.wizard_play_noise),
            icon = if (audioMeasure.noisePlaying) Icons.Filled.Close else Icons.Filled.PlayArrow,
            selected = audioMeasure.noisePlaying,
            enabled = true,
            onClick = { onSetPinkNoise(!audioMeasure.noisePlaying) },
            modifier = Modifier.width(210.dp),
        )
        ScanOptionChip(
            text = stringResource(R.string.wizard_redetect),
            icon = Icons.Filled.Refresh,
            selected = false,
            enabled = true,
            onClick = onProbeAudioMeasure,
            modifier = Modifier.width(150.dp),
        )
    }

    Spacer(Modifier.height(28.dp))
    Text(
        text = stringResource(R.string.wizard_mic_placement),
        style = TvTypography.body,
        color = TextSecondary,
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScanOptionChip(
            text = stringResource(R.string.wizard_start_sweep),
            icon = Icons.Filled.GraphicEq,
            selected = false,
            enabled = micAvailable,
            onClick = startSweepWithPermission,
            modifier = Modifier.width(210.dp),
        )
        ScanOptionChip(
            text = stringResource(R.string.wizard_cancel),
            icon = Icons.Filled.Close,
            selected = false,
            enabled = true,
            onClick = onClose,
            modifier = Modifier.width(130.dp),
        )
    }
    if (!micAvailable && caps?.reason != null) {
        Spacer(Modifier.height(10.dp))
        StatusMessage(
            icon = Icons.Filled.Error,
            text = stringResource(R.string.wizard_mic_issue),
            color = AccentBlue,
        )
    }
}

@Composable
private fun SweepingStep(audioMeasure: SettingsViewModel.AudioMeasureUiState) {
    // 不用 indeterminate 进度条：每帧重绘在这台设备上会把主线程拉满。
    // 1 Hz 驱动、与双扫频时间线同步的分段进度，也让“分析阶段”有真实的等待预期。
    var elapsedS by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            elapsedS += 1
        }
    }
    val e = elapsedS.toFloat()
    @StringRes val stageRes = when {
        e < 4f -> R.string.wizard_stage_sweep1
        e < 5f -> R.string.wizard_stage_tail
        e < 9.2f -> R.string.wizard_stage_sweep2
        e < 10.2f -> R.string.wizard_stage_tail
        else -> R.string.wizard_stage_analyze
    }
    val pct = ((e / 12f * 80f).coerceIn(0f, 80f) + if (e > 10.2f) ((e - 10.2f)).coerceIn(0f, 12f) else 0f)
        .coerceAtMost(92f).toInt()
    val serverStage = audioMeasure.progress
    Text(
        text = stringResource(R.string.wizard_stay_quiet),
        style = TvTypography.subtitle,
        color = TextPrimary,
    )
    Spacer(Modifier.height(18.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(DarkSurface, RoundedCornerShape(5.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(pct / 100f)
                .fillMaxHeight()
                .background(AnimeRed, RoundedCornerShape(5.dp)),
        )
    }
    Spacer(Modifier.height(18.dp))
    Text(
        text = if (e < 10.2f) stringResource(stageRes) else (serverStage?.resolve() ?: stringResource(stageRes)),
        style = TvTypography.body,
        color = TextSecondary,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.wizard_analyze_note),
        style = TvTypography.caption,
        color = TextSecondary,
    )
}

@Composable
private fun ResultStep(
    audioMeasure: SettingsViewModel.AudioMeasureUiState,
    onApplyMeasuredResult: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    val result = audioMeasure.result
    if (result == null) {
        Text(stringResource(R.string.measure_result_empty), style = TvTypography.body, color = TextSecondary)
        return
    }
    if (result.valid) {
        StatusMessage(
            icon = Icons.Filled.CheckCircle,
            text = stringResource(
                R.string.measure_result_valid_short,
                "%.1f".format(result.estimatedPpm),
                result.bands.size,
                "%.0f".format(result.matchLoHz),
                "%.0f".format(result.matchHiHz),
            ),
            color = ProgressGreen,
        )
        Spacer(Modifier.height(16.dp))
        if (result.freqs.isNotEmpty() && result.smoothedDb.size == result.freqs.size) {
            ResponseCurveChart(
                freqs = result.freqs,
                smoothedDb = result.smoothedDb,
                targetDb = result.targetDb,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.measure_curve_legend),
                style = TvTypography.caption,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (result.bands.isNotEmpty()) {
            Text(
                text = result.bands.joinToString("  ") {
                    "${"%.0f".format(it.frequencyHz)}Hz ${if (it.gainDb >= 0) "+" else ""}${"%.1f".format(it.gainDb)}dB Q${"%.1f".format(it.q)}"
                },
                style = TvTypography.caption,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = stringResource(R.string.measure_autocal_note),
            style = TvTypography.body,
            color = TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = stringResource(R.string.measure_autocal),
                icon = Icons.Filled.Save,
                selected = false,
                enabled = true,
                onClick = onApplyMeasuredResult,
                modifier = Modifier.width(260.dp),
            )
            ScanOptionChip(
                text = stringResource(R.string.measure_retry),
                icon = Icons.Filled.Refresh,
                selected = false,
                enabled = true,
                onClick = onRestart,
                modifier = Modifier.width(160.dp),
            )
        }
    } else {
        StatusMessage(
            icon = Icons.Filled.Error,
            text = stringResource(R.string.measure_result_invalid_reason, measureInvalidReasonHint(result.invalidReason).resolve()),
            color = AnimeRed,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanOptionChip(
                text = stringResource(R.string.measure_retry),
                icon = Icons.Filled.Refresh,
                selected = false,
                enabled = true,
                onClick = onRestart,
                modifier = Modifier.width(160.dp),
            )
            ScanOptionChip(
                text = stringResource(R.string.measure_close),
                icon = Icons.Filled.Close,
                selected = false,
                enabled = true,
                onClick = onClose,
                modifier = Modifier.width(130.dp),
            )
        }
    }
}

@Composable
private fun DoneStep(config: AudioDspConfig, onClose: () -> Unit) {
    val presetName = config.presets.firstOrNull { it.id == config.selectedPresetId }?.name
    StatusMessage(
        icon = Icons.Filled.CheckCircle,
        text = stringResource(R.string.measure_cal_saved, presetName ?: stringResource(R.string.measure_cal_default_name)),
        color = ProgressGreen,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.measure_cal_saved_note),
        style = TvTypography.body,
        color = TextSecondary,
    )
    Spacer(Modifier.height(20.dp))
    ScanOptionChip(
        text = stringResource(R.string.measure_done),
        icon = Icons.Filled.CheckCircle,
        selected = false,
        enabled = true,
        onClick = onClose,
        modifier = Modifier.width(160.dp),
    )
}

/** REW 风格对数频率轴频响曲线：平滑后响应 + 目标曲线。 */
@Composable
private fun ResponseCurveChart(
    freqs: DoubleArray,
    smoothedDb: DoubleArray,
    targetDb: DoubleArray,
    modifier: Modifier = Modifier,
) {
    val loF = 20.0
    val hiF = 20_000.0
    val loDb = -30.0
    val hiDb = 30.0
    val spanLog = kotlin.math.log10(hiF / loF).toFloat()
    Canvas(modifier = modifier) {
        fun xOf(f: Double): Float {
            val t = kotlin.math.log10(f / loF).toFloat() / spanLog
            return size.width * t.coerceIn(0f, 1f)
        }

        fun yOf(db: Double): Float {
            val t = ((db - loDb) / (hiDb - loDb)).toFloat()
            return size.height * (1f - t.coerceIn(0f, 1f))
        }

        // 0 dB 基线
        drawLine(
            color = Color.White.copy(alpha = 0.18f),
            start = androidx.compose.ui.geometry.Offset(0f, yOf(0.0)),
            end = androidx.compose.ui.geometry.Offset(size.width, yOf(0.0)),
            strokeWidth = 2f,
        )

        fun polyline(values: DoubleArray, color: Color, strokeWidth: Float) {
            val path = Path()
            var started = false
            freqs.forEachIndexed { i, f ->
                if (f in loF..hiF) {
                    val x = xOf(f)
                    val y = yOf(values[i])
                    if (started) path.lineTo(x, y) else {
                        path.moveTo(x, y)
                        started = true
                    }
                }
            }
            drawPath(path, color = color, style = Stroke(width = strokeWidth))
        }

        polyline(targetDb, Color.White.copy(alpha = 0.35f), 3f)
        polyline(smoothedDb, AnimeRed, 5f)
    }
}
/** 管线内部英文无效原因 → 人话提示（与 WebUI measureInvalidHint 保持一致语义）。 */
private fun measureInvalidReasonHint(reason: String?): UiText {
    if (reason.isNullOrBlank()) return uiText("measure_invalid_unknown", "未知原因")
    return when {
        reason.contains("single sweep") -> uiText("measure_invalid_single_sweep", "单次扫频无法验证时钟漂移（内部错误，需要双扫频）")
        reason.contains("boundary") ->
            uiText("measure_invalid_boundary", "输出与麦克风的时钟漂移超出可估计范围（±15 ppm）。内置麦克风通常无法达标，建议使用 USB 校准麦克风（如 UMIK-1），或改用共享时钟环境（环回）录制后导入 WAV")
        reason.contains("clock-drift") ->
            uiText("measure_invalid_clock_drift", "USB 麦克风与电视输出时钟漂移过大或两次估计不一致。请重试 1–2 次；仍失败则改用共享时钟环境（环回）录制后导入 WAV")
        reason.contains("tails") -> uiText("measure_invalid_tails", "两次测量的房间尾音不一致，结果不可信。请确认环境安静后重试")
        reason.contains("sharpness") -> uiText("measure_invalid_sharpness", "脉冲响应不锐利：检查麦克风位置是否在听音位、环境噪声是否过大")
        else -> rawUiText(reason)
    }
}
