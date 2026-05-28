package com.miruplay.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.miruplay.tv.clouddrive.GrpcCloudDriveClient
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.design.MiruPlayPalette
import com.miruplay.tv.design.MiruPlayRouteSurface
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSourceFactory
import com.miruplay.tv.mediasource.desktop.DesktopPlaybackBridge
import com.miruplay.tv.mediasource.desktop.desktopSourceFromInfo
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.CloudDriveLibraryMode
import com.miruplay.tv.model.PLAYBACK_SEEK_BACK_SECONDS
import com.miruplay.tv.model.PLAYBACK_SEEK_FORWARD_SECONDS
import com.miruplay.tv.model.PLAYBACK_SPEED_NORMAL
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.PlaybackProgressSession
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackTimingConventions
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.RssSubscriptionFormResult
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.cloudRssScheduledSyncCompleteStatus
import com.miruplay.tv.model.cloudRssInitialStatus
import com.miruplay.tv.model.cloudRssRescanStartedStatus
import com.miruplay.tv.model.cloudRssLinkedScanSourceStatus
import com.miruplay.tv.model.cloudRssScanSourceClearedStatus
import com.miruplay.tv.model.cloudRssScanSourceMissingStatus
import com.miruplay.tv.model.cloudRssScanSourceRequiredStatus
import com.miruplay.tv.model.cloudRssSchedulerDisabledStatus
import com.miruplay.tv.model.cloudRssSchedulerStartStatus
import com.miruplay.tv.model.cloudRssSchedulerStoppedStatus
import com.miruplay.tv.model.cloudRssStatusText
import com.miruplay.tv.model.completeStatus
import com.miruplay.tv.model.coercePlaybackSpeed
import com.miruplay.tv.model.detailBangumiSyncCompleteMessage
import com.miruplay.tv.model.detailBangumiSyncStartedMessage
import com.miruplay.tv.model.desktopWindowTitleLabel
import com.miruplay.tv.model.metadataMatchSummaryLabel
import com.miruplay.tv.model.metadataNoSelectedEntryLabel
import com.miruplay.tv.model.parseCloudDriveIntervalMinutes
import com.miruplay.tv.model.parseRssProxyPort
import com.miruplay.tv.model.playbackBlankMediaMessage
import com.miruplay.tv.model.playbackCommandPreviewErrorMessage
import com.miruplay.tv.model.playbackRifeStateLabel
import com.miruplay.tv.model.recentPlaybackInitialStatus
import com.miruplay.tv.model.recentPlaybackLoadedStatus
import com.miruplay.tv.model.recentPlaybackRequiredStatus
import com.miruplay.tv.model.recentPlaybackShowingStatus
import com.miruplay.tv.model.retainedSelectionInRssSubscriptions
import com.miruplay.tv.model.rssSubscriptionRequiredStatus
import com.miruplay.tv.model.rssSubscriptionSelectedStatus
import com.miruplay.tv.model.rssSubscriptionsLoadedStatus
import com.miruplay.tv.model.rssSubscriptionsLoadFailedStatus
import com.miruplay.tv.model.rssSubscriptionsRefreshFailedStatus
import com.miruplay.tv.model.rssSubscriptionsShowingStatus
import com.miruplay.tv.model.settingsDesktopLogUploadStatusMessage
import com.miruplay.tv.model.sourcePickerTitle
import com.miruplay.tv.model.settingsActiveSourceLabel
import com.miruplay.tv.model.settingsLinkedSourceLabel
import com.miruplay.tv.player.mpv.MpvProcessPlayer
import com.miruplay.tv.player.mpv.MpvRuntimeDiscovery
import com.miruplay.tv.player.mpv.RifeBackend
import com.miruplay.tv.player.mpv.mpvExitedStatus
import com.miruplay.tv.player.mpv.mpvIdleStatus
import com.miruplay.tv.player.mpv.mpvNoActiveProcessStatus
import com.miruplay.tv.player.mpv.mpvPausedStatus
import com.miruplay.tv.player.mpv.mpvPauseToggledStatus
import com.miruplay.tv.player.mpv.mpvPlaybackCompletedStatus
import com.miruplay.tv.player.mpv.mpvPositionSyncedStatus
import com.miruplay.tv.player.mpv.mpvResumedStatus
import com.miruplay.tv.player.mpv.mpvSeekBackStatus
import com.miruplay.tv.player.mpv.mpvSeekForwardStatus
import com.miruplay.tv.player.mpv.mpvSpeedChangedStatus
import com.miruplay.tv.player.mpv.mpvStoppedStatus
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.LibraryEpisodeResolver
import com.miruplay.tv.repository.LogUploadActionCoordinator
import com.miruplay.tv.repository.LogUploadAutoScheduler
import com.miruplay.tv.repository.MediaSourceActionCoordinator
import com.miruplay.tv.repository.MetadataBatchMatch
import com.miruplay.tv.repository.MetadataBatchPlan
import com.miruplay.tv.repository.NextPlaybackSourceResolver
import com.miruplay.tv.repository.OtlpLogUploadActionSnapshot
import com.miruplay.tv.repository.ScanPreferenceActionSnapshot
import com.miruplay.tv.repository.SettingsPreferenceActionCoordinator
import com.miruplay.tv.repository.WebControlAccessActionCoordinator
import com.miruplay.tv.repository.WebControlAccessSnapshot
import com.miruplay.tv.repository.displayName
import com.miruplay.tv.repository.desktop.DesktopRepositories
import com.miruplay.tv.repository.indexClearedStatus
import com.miruplay.tv.repository.indexedSearchStatus
import com.miruplay.tv.repository.isSameCandidate
import com.miruplay.tv.repository.loadedStatus
import com.miruplay.tv.repository.loadingRemoteDirectoryStatus
import com.miruplay.tv.repository.localLibraryInitialStatus
import com.miruplay.tv.repository.localRootRequiredStatus
import com.miruplay.tv.repository.mediaDisplayName
import com.miruplay.tv.repository.mediaIndexEpisodesForPosterSelection
import com.miruplay.tv.repository.mediaFilesOnly
import com.miruplay.tv.repository.metadataApplyEntryRequiredStatus
import com.miruplay.tv.repository.metadataIndexedVideoRequiredStatus
import com.miruplay.tv.repository.metadataInitialStatus
import com.miruplay.tv.repository.metadataQuery
import com.miruplay.tv.repository.metadataQuerySetFromIndexStatus
import com.miruplay.tv.repository.openRemoteSourceBeforeBrowsingStatus
import com.miruplay.tv.repository.openSourceBeforeClearingIndexStatus
import com.miruplay.tv.repository.openSourceBeforeScanningStatus
import com.miruplay.tv.repository.openSourceBeforeSearchingStatus
import com.miruplay.tv.repository.retainedSelectionInMediaIndex
import com.miruplay.tv.repository.scanPreferencesIntervalOptionsHours
import com.miruplay.tv.repository.shouldAutoScan
import com.miruplay.tv.repository.scanningStatus
import com.miruplay.tv.repository.selectedForPlaybackStatus
import com.miruplay.tv.repository.selectedMetadataStatus
import com.miruplay.tv.repository.selectedReviewStatus
import com.miruplay.tv.repository.selectedRemoteForPlaybackStatus
import com.miruplay.tv.repository.showingRemoteDirectoryStatus
import com.miruplay.tv.repository.smbUrlRequiredStatus
import com.miruplay.tv.repository.remoteBrowserInitialStatus
import com.miruplay.tv.repository.remoteRootStatus
import com.miruplay.tv.repository.sourceRemoveRequiredStatus
import com.miruplay.tv.repository.sourceRemovedStatus
import com.miruplay.tv.repository.summaryStatus
import com.miruplay.tv.repository.syncObservedPlaybackProgress
import com.miruplay.tv.repository.upsertById
import com.miruplay.tv.repository.updatedSelectionAfterReplacingByMediaKeys
import com.miruplay.tv.repository.webDavUrlRequiredStatus
import com.miruplay.tv.repository.withRuntimeStatus
import com.miruplay.tv.repository.canRunNow
import com.miruplay.tv.repository.toConfig
import com.miruplay.tv.repository.replaceByMediaKey
import com.miruplay.tv.repository.replaceByMediaKeys
import com.miruplay.tv.scraper.desktop.DesktopBangumiScraper
import com.miruplay.tv.sync.BANGUMI_METADATA_SOURCE_NAME
import com.miruplay.tv.sync.BangumiCredentialActionCoordinator
import com.miruplay.tv.sync.BangumiIndexMetadataCoordinator
import com.miruplay.tv.sync.BangumiMetadataRefreshCore
import com.miruplay.tv.sync.BangumiSyncCore
import com.miruplay.tv.sync.rss.CloudDriveActionResult
import com.miruplay.tv.sync.rss.CloudDriveConfigActionResult
import com.miruplay.tv.sync.rss.CloudDriveRssActionCoordinator
import com.miruplay.tv.sync.rss.CloudDriveDirectoryBrowserCoordinator
import com.miruplay.tv.sync.rss.CloudDriveDirectoryBrowserState
import com.miruplay.tv.sync.rss.CloudDriveDirectoryLoadResult
import com.miruplay.tv.sync.rss.CloudDriveDirectoryOpenResult
import com.miruplay.tv.sync.rss.CloudDriveDirectoryTarget
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssScheduler
import com.miruplay.tv.sync.rss.RssSubscriptionActionResult
import com.miruplay.tv.sync.rss.schedulerStatus
import com.miruplay.tv.sync.rss.selectCloudDriveDirectory as selectSharedCloudDriveDirectory
import com.miruplay.tv.webcontrol.WebControlPlaybackCommandKind
import com.miruplay.tv.webcontrol.playbackCommandKind
import com.miruplay.tv.webcontrol.playbackSpeed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal val AnimeRed = Color(MiruPlayPalette.ANIME_RED_ARGB)
internal val DarkBg = Color(MiruPlayPalette.DARK_BG_ARGB)
internal val DarkSurface = Color(MiruPlayPalette.DARK_SURFACE_ARGB)
internal val AccentBlue = Color(MiruPlayPalette.ACCENT_BLUE_ARGB)
internal val TextPrimary = Color(MiruPlayPalette.TEXT_PRIMARY_ARGB)
internal val TextSecondary = Color(MiruPlayPalette.TEXT_SECONDARY_ARGB)
internal val CardBg = Color(MiruPlayPalette.CARD_BG_ARGB)
private const val DESKTOP_PLAYBACK_MEDIA_SOURCE_ID = "desktop-compose"
private const val PLAYBACK_EOF_POLL_INTERVAL_MS = 1_000L
private const val PLAYBACK_PROGRESS_POLL_INTERVAL_MS = 10_000L
internal typealias DesktopSection = MiruPlayRouteSurface.Section

private const val DESKTOP_START_SECTION_ENV = "MIRUPLAY_DESKTOP_START_SECTION"
private const val DESKTOP_INITIAL_LIBRARY_ROOT_ENV = "MIRUPLAY_DESKTOP_INITIAL_LIBRARY_ROOT"
private const val DESKTOP_INITIAL_MEDIA_PATH_ENV = "MIRUPLAY_DESKTOP_INITIAL_MEDIA_PATH"
private const val DESKTOP_INITIAL_WEBDAV_URL_ENV = "MIRUPLAY_DESKTOP_INITIAL_WEBDAV_URL"
private const val DESKTOP_INITIAL_WEBDAV_USERNAME_ENV = "MIRUPLAY_DESKTOP_INITIAL_WEBDAV_USERNAME"
private const val DESKTOP_INITIAL_WEBDAV_PASSWORD_ENV = "MIRUPLAY_DESKTOP_INITIAL_WEBDAV_PASSWORD"
private const val DESKTOP_INITIAL_SMB_URL_ENV = "MIRUPLAY_DESKTOP_INITIAL_SMB_URL"
private const val DESKTOP_INITIAL_SMB_DOMAIN_ENV = "MIRUPLAY_DESKTOP_INITIAL_SMB_DOMAIN"
private const val DESKTOP_INITIAL_SMB_USERNAME_ENV = "MIRUPLAY_DESKTOP_INITIAL_SMB_USERNAME"
private const val DESKTOP_INITIAL_SMB_PASSWORD_ENV = "MIRUPLAY_DESKTOP_INITIAL_SMB_PASSWORD"
internal const val DESKTOP_ENTRY_SMOKE_ARG = "--miruplay-desktop-smoke"
internal const val DESKTOP_ENTRY_SMOKE_REPORT_ARG_PREFIX = "--miruplay-desktop-smoke-report="

private val MiruPlayDesktopColorScheme = darkColorScheme(
    primary = AnimeRed,
    secondary = AccentBlue,
    background = DarkBg,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Color(MiruPlayPalette.ERROR_ARGB),
)

fun main(args: Array<String>) {
    if (runDesktopEntrySmoke(args)) return
    if (runDesktopWebControlSmoke(args)) return

    application {
        MiruPlayDesktopWindow()
    }
}

@Composable
private fun ApplicationScope.MiruPlayDesktopWindow() {
    val windowState = rememberWindowState(width = 1280.dp, height = 820.dp)
    var playerFullscreenRestorePlacement by remember { mutableStateOf<WindowPlacement?>(null) }
    fun applyPlayerFullscreen(active: Boolean) {
        val nextPlacement = desktopPlayerFullscreenPlacement(
            currentPlacement = windowState.placement,
            restorePlacement = playerFullscreenRestorePlacement,
            active = active,
        )
        windowState.placement = nextPlacement.placement
        playerFullscreenRestorePlacement = nextPlacement.restorePlacement
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = desktopWindowTitleLabel(),
        state = windowState,
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1100, 720)
        }
        MaterialTheme(colorScheme = MiruPlayDesktopColorScheme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DarkBg,
            ) {
                MiruPlayDesktopComposeApp(
                    onPlayerFullscreenActiveChange = ::applyPlayerFullscreen,
                )
            }
        }
    }
}

internal data class DesktopEntrySmokeReport(
    val status: String,
    val entryPoint: String,
    val windowTitle: String,
    val initialSection: String,
    val runtimeRoot: String,
    val mpvExecutable: String,
    val configDirectory: String,
) {
    fun toJson(): String =
        """
        {
          "status": ${status.jsonValue()},
          "entryPoint": ${entryPoint.jsonValue()},
          "windowTitle": ${windowTitle.jsonValue()},
          "initialSection": ${initialSection.jsonValue()},
          "runtimeRoot": ${runtimeRoot.jsonValue()},
          "mpvExecutable": ${mpvExecutable.jsonValue()},
          "configDirectory": ${configDirectory.jsonValue()}
        }
        """.trimIndent()
}

internal fun desktopEntrySmokeReport(): DesktopEntrySmokeReport {
    val layout = MpvRuntimeDiscovery.defaultLayout()
    return DesktopEntrySmokeReport(
        status = "ok",
        entryPoint = "com.miruplay.tv.desktop.MiruPlayDesktopComposeAppKt",
        windowTitle = desktopWindowTitleLabel(),
        initialSection = desktopInitialSectionFromEnvironment().id,
        runtimeRoot = layout.rootDirectory.toString(),
        mpvExecutable = layout.executable.toString(),
        configDirectory = layout.configDirectory.toString(),
    )
}

internal fun desktopEntrySmokeReportPath(args: Array<String>): Path? =
    args.firstOrNull { it.startsWith(DESKTOP_ENTRY_SMOKE_REPORT_ARG_PREFIX) }
        ?.removePrefix(DESKTOP_ENTRY_SMOKE_REPORT_ARG_PREFIX)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(Paths::get)

internal fun shouldRunDesktopEntrySmoke(args: Array<String>): Boolean =
    args.any { it == DESKTOP_ENTRY_SMOKE_ARG }

internal fun runDesktopEntrySmoke(args: Array<String>): Boolean {
    if (!shouldRunDesktopEntrySmoke(args)) return false

    val report = desktopEntrySmokeReport().toJson()
    desktopEntrySmokeReportPath(args)?.let { reportPath ->
        reportPath.parent?.let(Files::createDirectories)
        Files.writeString(reportPath, report)
    }
    println(report)
    return true
}

private fun String.jsonValue(): String =
    buildString {
        append('"')
        this@jsonValue.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

internal fun shouldUseDesktopPlayerFullscreen(
    selectedSection: DesktopSection,
    fullscreen: Boolean,
): Boolean =
    selectedSection == MiruPlayRouteSurface.player && fullscreen

internal data class DesktopPlayerFullscreenPlacement(
    val placement: WindowPlacement,
    val restorePlacement: WindowPlacement?,
)

internal fun desktopPlayerFullscreenPlacement(
    currentPlacement: WindowPlacement,
    restorePlacement: WindowPlacement?,
    active: Boolean,
): DesktopPlayerFullscreenPlacement =
    if (active) {
        DesktopPlayerFullscreenPlacement(
            placement = WindowPlacement.Fullscreen,
            restorePlacement = if (currentPlacement == WindowPlacement.Fullscreen) {
                restorePlacement
            } else {
                currentPlacement
            },
        )
    } else {
        DesktopPlayerFullscreenPlacement(
            placement = restorePlacement ?: currentPlacement,
            restorePlacement = null,
        )
    }

internal fun desktopInitialSection(startSectionId: String?): DesktopSection =
    MiruPlayRouteSurface.sectionForId(startSectionId) ?: MiruPlayRouteSurface.library

internal fun desktopInitialSectionFromEnvironment(): DesktopSection =
    desktopInitialSection(System.getenv(DESKTOP_START_SECTION_ENV))

internal fun desktopInitialLibraryRoot(value: String?): String =
    value?.trim().orEmpty()

internal fun desktopInitialLibraryRootFromEnvironment(): String =
    desktopInitialLibraryRoot(System.getenv(DESKTOP_INITIAL_LIBRARY_ROOT_ENV))

internal fun desktopInitialMediaPath(value: String?): String =
    value?.trim().orEmpty()

internal fun desktopInitialMediaPathFromEnvironment(): String =
    desktopInitialMediaPath(System.getenv(DESKTOP_INITIAL_MEDIA_PATH_ENV))

internal fun desktopInitialSourceFormStateFromEnvironment(): DesktopSourceFormState =
    desktopSourceFormStateFromInitialValues(
        libraryRoot = System.getenv(DESKTOP_INITIAL_LIBRARY_ROOT_ENV),
        webDavUrl = System.getenv(DESKTOP_INITIAL_WEBDAV_URL_ENV),
        webDavUsername = System.getenv(DESKTOP_INITIAL_WEBDAV_USERNAME_ENV),
        webDavPassword = System.getenv(DESKTOP_INITIAL_WEBDAV_PASSWORD_ENV),
        smbUrl = System.getenv(DESKTOP_INITIAL_SMB_URL_ENV),
        smbDomain = System.getenv(DESKTOP_INITIAL_SMB_DOMAIN_ENV),
        smbUsername = System.getenv(DESKTOP_INITIAL_SMB_USERNAME_ENV),
        smbPassword = System.getenv(DESKTOP_INITIAL_SMB_PASSWORD_ENV),
    )

@Composable
internal fun MiruPlayDesktopComposeApp(
    onPlayerFullscreenActiveChange: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val repositories = remember { DesktopRepositories.fileBacked() }
    val desktopMediaSourceFactory = remember { DesktopMediaSourceFactory() }
    val mediaSourceActions = remember(repositories) { MediaSourceActionCoordinator(repositories.mediaSources) }
    val playbackBridge = remember { DesktopPlaybackBridge() }
    val bangumiScraper = remember { DesktopBangumiScraper { repositories.credentials.bangumiAccessToken } }
    val bangumiMetadataRefreshCore = remember(bangumiScraper, repositories) {
        BangumiMetadataRefreshCore(
            metadataRepository = repositories.metadata,
            bangumiScraper = bangumiScraper,
        )
    }
    val bangumiIndexMetadataCoordinator = remember(bangumiScraper, repositories) {
        BangumiIndexMetadataCoordinator(
            indexRepository = repositories.index,
            metadataRepository = repositories.metadata,
            bangumiScraper = bangumiScraper,
        )
    }
    val bangumiSyncCore = remember(bangumiScraper, repositories) {
        BangumiSyncCore(
            bangumiService = bangumiScraper,
            metadataRepository = repositories.metadata,
            progressRepository = repositories.progress,
        )
    }
    val cloudDriveClient = remember { GrpcCloudDriveClient() }
    val cloudRssEngine = remember {
        DesktopCloudDriveRssAutomationEngine(
            repository = repositories.cloudDriveAutomation,
            credentials = repositories.credentials,
            cloudDriveClient = cloudDriveClient,
        )
    }
    val cloudRssActions = remember {
        CloudDriveRssActionCoordinator(
            repository = repositories.cloudDriveAutomation,
            credentials = repositories.credentials,
            runner = cloudRssEngine,
        )
    }
    val bangumiCredentialActions = remember { BangumiCredentialActionCoordinator(repositories.credentials) }
    val cloudDirectoryActions = remember { CloudDriveDirectoryBrowserCoordinator(cloudDriveClient) }
    val cloudRssScheduler = remember { DesktopCloudDriveRssScheduler(cloudRssEngine, scope) }
    val cloudRssSchedulerState by cloudRssScheduler.state.collectAsState()
    val settingsPreferenceActions = remember {
        SettingsPreferenceActionCoordinator(
            scanPreferences = repositories.scanPreferences,
            playbackPreferences = repositories.playbackPreferences,
        )
    }
    val logUploadActions = remember(repositories) { LogUploadActionCoordinator(repositories.logUpload) }
    val logUploadAutoScheduler = remember(repositories, scope) {
        LogUploadAutoScheduler(
            repository = repositories.logUpload,
            scope = scope,
        )
    }
    val defaultMpvLayout = remember { MpvRuntimeDiscovery.defaultLayout() }
    val playbackLauncher = remember(playbackBridge) { DesktopPlaybackLauncher(playbackBridge) }
    val initialSourceFormState = remember { desktopInitialSourceFormStateFromEnvironment() }
    var selectedDesktopSection by remember { mutableStateOf(desktopInitialSectionFromEnvironment()) }
    var player by remember { mutableStateOf<MpvProcessPlayer?>(null) }
    var activePlaybackSession by remember { mutableStateOf<PlaybackProgressSession?>(null) }
    var webControlPlaybackSource by remember { mutableStateOf<DesktopMediaSource?>(null) }
    var mpvPath by remember { mutableStateOf(defaultMpvLayout.executable.toString()) }
    var configDir by remember { mutableStateOf(defaultMpvLayout.configDirectory.toString()) }
    var libraryRoot by remember { mutableStateOf(initialSourceFormState.libraryRoot) }
    var savedSources by remember { mutableStateOf(emptyList<MediaSourceInfo>()) }
    var activeSourceId by remember { mutableStateOf<Long?>(null) }
    var activeSource by remember { mutableStateOf<DesktopMediaSource?>(null) }
    var activeLocalSource by remember { mutableStateOf<DesktopLocalMediaSource?>(null) }
    var indexQuery by remember { mutableStateOf("") }
    var indexedEntries by remember { mutableStateOf(emptyList<MediaIndexEntry>()) }
    var selectedIndexEntry by remember { mutableStateOf<MediaIndexEntry?>(null) }
    var libraryStatus by remember { mutableStateOf(localLibraryInitialStatus()) }
    var webDavUrl by remember { mutableStateOf(initialSourceFormState.webDavUrl) }
    var webDavUsername by remember { mutableStateOf(initialSourceFormState.webDavUsername) }
    var webDavPassword by remember { mutableStateOf(initialSourceFormState.webDavPassword) }
    var smbUrl by remember { mutableStateOf(initialSourceFormState.smbUrl) }
    var smbDomain by remember { mutableStateOf(initialSourceFormState.smbDomain) }
    var smbUsername by remember { mutableStateOf(initialSourceFormState.smbUsername) }
    var smbPassword by remember { mutableStateOf(initialSourceFormState.smbPassword) }
    var remotePath by remember { mutableStateOf("") }
    var remoteEntries by remember { mutableStateOf(emptyList<FileEntry>()) }
    var selectedRemoteEntry by remember { mutableStateOf<FileEntry?>(null) }
    var remoteStatus by remember { mutableStateOf(remoteBrowserInitialStatus()) }
    var bangumiQuery by remember { mutableStateOf("") }
    var bangumiResults by remember { mutableStateOf(emptyList<ScraperResult>()) }
    var selectedBangumiResult by remember { mutableStateOf<ScraperResult?>(null) }
    var bangumiBatchMatches by remember { mutableStateOf(emptyList<MetadataBatchMatch>()) }
    var selectedBangumiBatchMatch by remember { mutableStateOf<MetadataBatchMatch?>(null) }
    var bangumiBatchPlan by remember { mutableStateOf<MetadataBatchPlan?>(null) }
    var bangumiBatchRollback by remember { mutableStateOf(emptyList<MediaIndexEntry>()) }
    var bangumiStatus by remember { mutableStateOf(metadataInitialStatus(BANGUMI_METADATA_SOURCE_NAME)) }
    var bangumiTokenInput by remember { mutableStateOf("") }
    var bangumiTokenConfigured by remember { mutableStateOf(false) }
    var bangumiSyncing by remember { mutableStateOf(false) }
    var webControlEnabled by remember { mutableStateOf(false) }
    var webControlAccessToken by remember { mutableStateOf("") }
    var webUiUrls by remember { mutableStateOf(emptyList<String>()) }
    var autoScanEnabled by remember { mutableStateOf(false) }
    var autoScanIntervalHours by remember { mutableStateOf(6) }
    var lastScanAt by remember { mutableStateOf(0L) }
    var mergeSameAnimeEnabled by remember { mutableStateOf(false) }
    var recentProgress by remember { mutableStateOf(emptyList<DesktopRecentPlaybackItem>()) }
    var selectedRecentProgress by remember { mutableStateOf<DesktopRecentPlaybackItem?>(null) }
    var selectedDetailEpisodeSeason by remember { mutableStateOf<Int?>(null) }
    var detailHeroFocusVersion by remember { mutableStateOf(0) }
    var detailEpisodeFocusVersion by remember { mutableStateOf(0) }
    var recentPlaybackFocusVersion by remember { mutableStateOf(0) }
    var mediaDetailsFocusVersion by remember { mutableStateOf(0) }
    var recentStatus by remember { mutableStateOf(recentPlaybackInitialStatus()) }
    var bangumiFocusVersion by remember { mutableStateOf(0) }
    var cloudEndpointUrl by remember { mutableStateOf("") }
    var cloudUsername by remember { mutableStateOf("") }
    var cloudToken by remember { mutableStateOf("") }
    var cloudPassword by remember { mutableStateOf("") }
    var cloudInboxPath by remember { mutableStateOf("") }
    var cloudLibraryPath by remember { mutableStateOf("") }
    var cloudLibraryMode by remember { mutableStateOf(CloudDriveLibraryMode.ORGANIZED_LIBRARY) }
    var cloudLinkedSourceId by remember { mutableStateOf<Long?>(null) }
    var cloudIntervalMinutes by remember { mutableStateOf("30") }
    var cloudEnabled by remember { mutableStateOf(false) }
    var rssProxyEnabled by remember { mutableStateOf(false) }
    var rssProxyHost by remember { mutableStateOf("") }
    var rssProxyPort by remember { mutableStateOf("1080") }
    var rssName by remember { mutableStateOf("") }
    var rssUrl by remember { mutableStateOf("") }
    var rssFilter by remember { mutableStateOf("") }
    var rssEnabled by remember { mutableStateOf(true) }
    var rssSubscriptions by remember { mutableStateOf(emptyList<RssSubscriptionInfo>()) }
    var selectedRssSubscription by remember { mutableStateOf<RssSubscriptionInfo?>(null) }
    var cloudRssStatus by remember { mutableStateOf(cloudRssInitialStatus()) }
    var cloudDirectoryBrowser by remember { mutableStateOf(CloudDriveDirectoryBrowserState()) }
    var logUploadSnapshot by remember { mutableStateOf(OtlpLogUploadActionSnapshot()) }
    var logUploadTokenInput by remember { mutableStateOf("") }
    var mediaPath by remember { mutableStateOf(desktopInitialMediaPathFromEnvironment()) }
    var subtitlePath by remember { mutableStateOf("") }
    var startSeconds by remember { mutableStateOf("0") }
    var fullscreen by remember { mutableStateOf(false) }
    var keepOpen by remember { mutableStateOf(false) }
    var playbackEndAction by remember { mutableStateOf(PlaybackEndAction.RETURN_TO_DETAIL) }
    var playbackSpeed by remember { mutableStateOf(PLAYBACK_SPEED_NORMAL) }
    var rifeEnabled by remember { mutableStateOf(DEFAULT_DESKTOP_RIFE_ENABLED) }
    var rifeBackend by remember { mutableStateOf(RifeBackend.NVIDIA) }
    var status by remember { mutableStateOf(mpvRuntimeStatusFromInputs(mpvPath, configDir)) }
    var launchStatus by remember { mutableStateOf(mpvIdleStatus()) }
    var playbackPositionMs by remember { mutableStateOf(0L) }
    var playbackDurationMs by remember { mutableStateOf(0L) }
    val webControlPlaybackHandlers = remember { DesktopWebControlPlaybackHandlers() }
    val desktopWebControlService = remember(repositories) {
        DesktopWebControlService(
            repositories = repositories,
            cloudDriveClient = cloudDriveClient,
            cloudRssEngine = cloudRssEngine,
            onCloudDriveConfigSaved = { config ->
                cloudRssScheduler.syncPeriodicWork(config)
            },
            onLogUploadConfigSaved = { config ->
                logUploadAutoScheduler.syncWithConfig(config)
            },
            playbackStatusProvider = {
                desktopWebControlPlaybackStatus(
                    player = player,
                    session = activePlaybackSession,
                    mediaPath = mediaPath,
                    launchStatus = launchStatus,
                )
            },
            playEpisodeHandler = { request, episode -> webControlPlaybackHandlers.playEpisode(request, episode) },
            playbackCommandHandler = { command -> webControlPlaybackHandlers.playbackCommand(command) },
        )
    }
    val desktopWebControlServer = remember(desktopWebControlService, repositories) {
        DesktopWebControlServer(
            webControlService = desktopWebControlService,
            webControlAccess = repositories.webControlAccess,
        )
    }
    val webControlActions = remember(repositories) { WebControlAccessActionCoordinator(repositories.webControlAccess) }
    val libraryEpisodeResolver = remember(repositories) {
        LibraryEpisodeResolver(
            mediaSources = repositories.mediaSources,
            metadata = repositories.metadata,
            index = repositories.index,
            progress = repositories.progress,
            mergeSameAnimeEnabled = { repositories.scanPreferences.getPreferences().mergeSameAnimeEnabled },
        )
    }
    val nextPlaybackSourceResolver = remember(repositories) {
        NextPlaybackSourceResolver(
            metadata = repositories.metadata,
            progress = repositories.progress,
            mediaSources = repositories.mediaSources,
            playbackUriForEpisode = { episode -> episode.filePath },
        )
    }
    val commandPreview by remember(
        mpvPath,
        configDir,
        mediaPath,
        subtitlePath,
        startSeconds,
        fullscreen,
        keepOpen,
        rifeEnabled,
        rifeBackend,
    ) {
        derivedStateOf {
            desktopMpvCommandPreviewFromInputs(
                mpvPath = mpvPath,
                configDir = configDir,
                mediaPath = mediaPath,
                subtitlePath = subtitlePath,
                startSeconds = startSeconds,
                fullscreen = fullscreen,
                keepOpen = keepOpen,
                rifeEnabled = rifeEnabled,
                rifeBackend = rifeBackend,
                blankMediaMessage = playbackBlankMediaMessage(),
                errorMessage = playbackCommandPreviewErrorMessage(),
            )
        }
    }

    val detailEpisodes = remember(indexedEntries, selectedIndexEntry, mergeSameAnimeEnabled) {
        indexedEntries.mediaIndexEpisodesForPosterSelection(selectedIndexEntry, mergeSameAnimeEnabled)
    }

    LaunchedEffect(selectedIndexEntry?.sourceId, selectedIndexEntry?.path, detailEpisodes.map { it.seasonNumber }) {
        selectedDetailEpisodeSeason = detailActiveEpisodeSeason(
            episodes = detailEpisodes,
            selectedEntry = selectedIndexEntry,
            requestedSeason = selectedDetailEpisodeSeason,
        )
    }

    DisposableEffect(playbackBridge, cloudRssScheduler, desktopWebControlServer) {
        onDispose {
            desktopWebControlServer.stopIfRunning()
            playbackBridge.close()
            cloudRssScheduler.stop()
            logUploadAutoScheduler.stop()
        }
    }

    suspend fun clearWebControlPlaybackSource() {
        val source = webControlPlaybackSource
        webControlPlaybackSource = null
        if (source != null && source !== activeSource && source !== activeLocalSource) {
            source.close()
        }
    }

    fun resetDesktopPlaybackTimeline() {
        playbackPositionMs = 0L
        playbackDurationMs = 0L
    }

    fun applyDesktopPlaybackStatusToTimeline(status: com.miruplay.tv.webcontrol.PlaybackStatusDto) {
        playbackPositionMs = status.positionMs.coerceAtLeast(0L)
        playbackDurationMs = status.durationMs.coerceAtLeast(0L)
    }

    suspend fun applyDesktopPlaybackSpeed(speed: Float) {
        val selectedSpeed = coercePlaybackSpeed(speed)
        playbackSpeed = selectedSpeed
        val activePlayer = player ?: return
        when (val result = activePlayer.setSpeed(selectedSpeed.toDouble())) {
            is Result.Success -> launchStatus = mpvSpeedChangedStatus(selectedSpeed)
            is Result.Error -> launchStatus = result.error.toUserMessage()
        }
    }

    fun syncDesktopWebControlServer() {
        if (repositories.webControlAccess.webControlEnabled) {
            desktopWebControlServer.startIfNeeded()
        } else {
            desktopWebControlServer.stopIfRunning()
        }
    }

    fun applyWebControlSnapshot(snapshot: WebControlAccessSnapshot) {
        webControlEnabled = snapshot.enabled
        webControlAccessToken = snapshot.accessToken
        webUiUrls = snapshot.urls
        syncDesktopWebControlServer()
    }

    fun applyScanPreferenceSnapshot(snapshot: ScanPreferenceActionSnapshot) {
        autoScanEnabled = snapshot.autoScanEnabled
        autoScanIntervalHours = snapshot.autoScanIntervalHours
        lastScanAt = snapshot.lastScanAt
        mergeSameAnimeEnabled = snapshot.mergeSameAnimeEnabled
    }

    suspend fun loadIndexedEntries(sourceId: Long, statusWhenEmpty: String) {
        when (val result = repositories.index.queryIndex(sourceId, "")) {
            is Result.Success -> {
                indexedEntries = result.data.mediaFilesOnly()
                selectedIndexEntry = selectedIndexEntry.retainedSelectionInMediaIndex(indexedEntries)
                if (indexedEntries.isNotEmpty()) {
                    libraryStatus = indexedSearchStatus(
                        query = "",
                        hasResults = true,
                        displayedResultCount = indexedEntries.size,
                    )
                } else {
                    libraryStatus = statusWhenEmpty
                }
            }
            is Result.Error -> libraryStatus = result.error.toUserMessage()
        }
    }

    fun applySourceFormState(formState: DesktopSourceFormState) {
        libraryRoot = formState.libraryRoot
        webDavUrl = formState.webDavUrl
        webDavUsername = formState.webDavUsername
        webDavPassword = formState.webDavPassword
        smbUrl = formState.smbUrl
        smbDomain = formState.smbDomain
        smbUsername = formState.smbUsername
        smbPassword = formState.smbPassword
    }

    suspend fun loadRemoteDirectory(source: DesktopMediaSource, path: String) {
        remoteStatus = source.info.loadingRemoteDirectoryStatus(path)
        when (val result = source.listFiles(path)) {
            is Result.Success -> {
                remotePath = path
                remoteEntries = result.data
                selectedRemoteEntry = null
                remoteStatus = source.info.showingRemoteDirectoryStatus(result.data)
            }
            is Result.Error -> remoteStatus = result.error.toUserMessage()
        }
    }

    suspend fun applySourceActivationState(
        activationState: DesktopSourceActivationState,
        loadIndexed: Boolean,
    ) {
        val sourceInfo = activationState.sourceInfo
        activeSourceId = sourceInfo.id
        val source = desktopSourceFromInfo(sourceInfo)
        activeSource = source
        activeLocalSource = source as? DesktopLocalMediaSource
        applySourceFormState(activationState.formState)
        indexedEntries = emptyList()
        selectedIndexEntry = null
        selectedRemoteEntry = null
        activationState.libraryStatus?.let { libraryStatus = it }
        activationState.remoteStatus?.let { remoteStatus = it }
        if (activationState.clearsRemoteBrowser) {
            remoteEntries = emptyList()
            remotePath = ""
        }
        if (activationState.loadsRemoteRoot && loadIndexed) {
            remotePath = ""
            loadRemoteDirectory(source, "")
        }
        if (loadIndexed) {
            loadIndexedEntries(sourceInfo.id, activationState.indexedEmptyStatus)
        }
    }

    LaunchedEffect(repositories) {
        var startupSource: MediaSourceInfo? = null
        when (val sources = repositories.mediaSources.getSources()) {
            is Result.Success -> {
                savedSources = sources.data
                if (sources.data.isNotEmpty() || !initialSourceFormState.hasAnyValue()) {
                    applySourceFormState(sources.data.desktopSourceFormState())
                }
                startupSource = sources.data.preferredDesktopStartupSource()
                startupSource?.let { source ->
                    applySourceActivationState(
                        activationState = source.desktopSourceActivationState(),
                        loadIndexed = false,
                    )
                }
            }
            is Result.Error -> libraryStatus = sources.error.toUserMessage()
        }
        startupSource?.let { source ->
            loadIndexedEntries(source.id, source.loadedStatus())
        }
        when (val recents = libraryEpisodeResolver.loadContinueWatchingEpisodesResult(limit = 12)) {
            is Result.Success -> {
                val items = recents.data.map { it.toDesktopRecentPlaybackItem() }
                recentProgress = items
                recentStatus = recentPlaybackLoadedStatus(items.map { it.progress })
            }
            is Result.Error -> recentStatus = recents.error.toUserMessage()
        }
        playbackEndAction = settingsPreferenceActions.currentPlaybackEndAction()
        applyScanPreferenceSnapshot(settingsPreferenceActions.currentScanPreferences())
        when (val config = repositories.cloudDriveAutomation.getConfig()) {
            is Result.Success -> {
                cloudEndpointUrl = config.data.endpointUrl
                cloudUsername = config.data.username
                cloudLinkedSourceId = config.data.webDavSourceId
                cloudInboxPath = config.data.inboxPath
                cloudLibraryPath = config.data.libraryPath
                cloudLibraryMode = config.data.libraryMode
                cloudIntervalMinutes = config.data.intervalMinutes.toString()
                cloudEnabled = config.data.enabled
                rssProxyEnabled = config.data.rssProxyEnabled
                rssProxyHost = config.data.rssProxyHost
                rssProxyPort = config.data.rssProxyPort.toString()
                cloudRssScheduler.syncPeriodicWork(config.data)
            }
            is Result.Error -> cloudRssStatus = config.error.toUserMessage()
        }
        cloudToken = repositories.credentials.cloudDriveToken.orEmpty()
        cloudPassword = repositories.credentials.cloudDrivePassword.orEmpty()
        bangumiTokenConfigured = !repositories.credentials.bangumiAccessToken.isNullOrBlank()
        logUploadSnapshot = logUploadActions.current()
        logUploadAutoScheduler.syncWithConfig(logUploadSnapshot.toConfig())
        applyWebControlSnapshot(webControlActions.current())
        runCatching {
            repositories.cloudDriveAutomation.observeSubscriptions().first()
        }.onSuccess { subscriptions ->
            rssSubscriptions = subscriptions
            cloudRssStatus = rssSubscriptionsLoadedStatus(subscriptions.size)
        }.onFailure { error ->
            cloudRssStatus = rssSubscriptionsLoadFailedStatus(error.message)
        }
    }

    LaunchedEffect(repositories, logUploadAutoScheduler) {
        repositories.logUpload.observeConfig()
            .map { it.enabled }
            .distinctUntilChanged()
            .collect {
                logUploadAutoScheduler.syncWithConfig(repositories.logUpload.getConfig())
            }
    }

    LaunchedEffect(repositories) {
        repositories.logUpload.status.collect { status ->
            logUploadSnapshot = logUploadSnapshot.withRuntimeStatus(
                status = status,
                tokenConfigured = status.tokenConfigured || repositories.logUpload.isTokenConfigured(),
            )
        }
    }

    LaunchedEffect(repositories, cloudRssScheduler) {
        repositories.cloudDriveAutomation.observeConfig()
            .collect { config ->
                cloudEndpointUrl = config.endpointUrl
                cloudUsername = config.username
                cloudLinkedSourceId = config.webDavSourceId
                cloudInboxPath = config.inboxPath
                cloudLibraryPath = config.libraryPath
                cloudLibraryMode = config.libraryMode
                cloudIntervalMinutes = config.intervalMinutes.toString()
                cloudEnabled = config.enabled
                rssProxyEnabled = config.rssProxyEnabled
                rssProxyHost = config.rssProxyHost
                rssProxyPort = config.rssProxyPort.toString()
                cloudRssScheduler.syncPeriodicWork(config)
            }
    }

    LaunchedEffect(repositories) {
        repositories.cloudDriveAutomation.observeSubscriptions()
            .collect { subscriptions ->
                rssSubscriptions = subscriptions
                selectedRssSubscription = selectedRssSubscription.retainedSelectionInRssSubscriptions(subscriptions)
                cloudRssStatus = rssSubscriptionsShowingStatus(subscriptions.size)
            }
    }

    suspend fun refreshRecentProgress() {
        when (val recents = libraryEpisodeResolver.loadContinueWatchingEpisodesResult(limit = 12)) {
            is Result.Success -> {
                val items = recents.data.map { it.toDesktopRecentPlaybackItem() }
                recentProgress = items
                selectedRecentProgress = selectedRecentProgress.retainedSelectionInRecentPlaybackItems(items)
                recentStatus = recentPlaybackShowingStatus(items.map { it.progress })
            }
            is Result.Error -> recentStatus = recents.error.toUserMessage()
        }
    }

    fun refreshDesktopWebUiUrls() {
        applyWebControlSnapshot(webControlActions.refreshUrls())
    }

    suspend fun ensureSelectedMetadataCache(entry: MediaIndexEntry): Result<String> =
        bangumiMetadataRefreshCore.ensureCachedIndexMetadata(
            entry = entry,
            relatedEntries = detailEpisodes,
        )

    suspend fun syncSelectedBangumiProgress(entry: MediaIndexEntry) {
        bangumiSyncing = true
        bangumiStatus = detailBangumiSyncStartedMessage()
        when (val animeId = ensureSelectedMetadataCache(entry)) {
            is Result.Success -> {
                when (val synced = bangumiSyncCore.syncAnime(animeId.data)) {
                    is Result.Success -> {
                        refreshRecentProgress()
                        val updatedEpisodes = repositories.metadata.getCachedEpisodes(animeId.data).getOrNull().orEmpty()
                        val updatedAnime = repositories.metadata.getCachedMetadata(animeId.data).getOrNull()
                        val collectionByPath = updatedEpisodes.associateBy { it.filePath }
                        indexedEntries = indexedEntries.map { indexed ->
                            collectionByPath[indexed.path]?.let { episode ->
                                indexed.copy(plot = indexed.plot ?: updatedAnime?.summary)
                            } ?: indexed
                        }
                        bangumiStatus = detailBangumiSyncCompleteMessage(
                            pushedEpisodes = synced.data.pushedEpisodes,
                            pulledEpisodes = synced.data.pulledEpisodes,
                        )
                    }
                    is Result.Error -> bangumiStatus = synced.error.toUserMessage()
                }
            }
            is Result.Error -> bangumiStatus = animeId.error.toUserMessage()
        }
        bangumiSyncing = false
    }

    suspend fun savePlaybackProgress(
        episodeId: String,
        positionMs: Long,
        lastWatched: Long = System.currentTimeMillis(),
        incrementPlayCount: Boolean = false,
    ): Result<Unit> =
        repositories.progress.saveProgress(
            episodeId = episodeId,
            positionMs = positionMs.coerceAtLeast(0L),
            lastWatched = lastWatched,
            incrementPlayCount = incrementPlayCount,
        )

    fun playbackLaunchRequestFor(
        path: String,
        startPositionMs: Long,
        sourceOverride: DesktopMediaSource? = null,
        sourceIdOverride: Long? = null,
        episodeId: String? = null,
    ): DesktopPlaybackLaunchRequest =
        DesktopPlaybackLaunchRequest(
            mpvPath = mpvPath,
            configDir = configDir,
            mediaPath = path,
            subtitlePath = if (path == mediaPath) subtitlePath else "",
            startSeconds = startPositionMs.coerceAtLeast(0L).let { position ->
                PlaybackTimingConventions.formatMpvStartSeconds(position)
            },
            fullscreen = fullscreen,
            keepOpen = keepOpen,
            rifeEnabled = rifeEnabled,
            rifeBackend = rifeBackend,
            activeSource = sourceOverride ?: activeSource,
            activeSourceId = sourceIdOverride ?: activeSourceId,
            blankMediaMessage = playbackBlankMediaMessage(),
            fallbackMediaSourceId = DESKTOP_PLAYBACK_MEDIA_SOURCE_ID,
            episodeId = episodeId,
        )

    fun requestDetailPanelFocus(panel: DesktopDetailFocusPanel): Boolean {
        when (panel) {
            DesktopDetailFocusPanel.Hero -> detailHeroFocusVersion += 1
            DesktopDetailFocusPanel.EpisodeList -> detailEpisodeFocusVersion += 1
            DesktopDetailFocusPanel.BangumiMetadata -> bangumiFocusVersion += 1
            DesktopDetailFocusPanel.RecentPlayback -> recentPlaybackFocusVersion += 1
            DesktopDetailFocusPanel.MediaDetails -> mediaDetailsFocusVersion += 1
        }
        return true
    }

    fun moveDetailPanelFocus(current: DesktopDetailFocusPanel, direction: Int): Boolean =
        detailPanelFocusTarget(
            current = current,
            direction = direction,
            hasRelatedEpisodes = detailEpisodes.isNotEmpty(),
            hasRecentPlayback = recentProgress.isNotEmpty(),
        )?.let(::requestDetailPanelFocus) ?: false

    suspend fun launchDesktopPlayback(
        path: String = mediaPath,
        startPositionMs: Long? = null,
        sourceOverride: DesktopMediaSource? = null,
        sourceIdOverride: Long? = null,
        episodeId: String? = null,
    ): Result<DesktopPlaybackLaunchResult> {
        val previousMediaPath = mediaPath
        val request = playbackLaunchRequestFor(
            path = path,
            startPositionMs = startPositionMs ?: PlaybackTimingConventions.parseSecondsToPositionMs(startSeconds),
            sourceOverride = sourceOverride,
            sourceIdOverride = sourceIdOverride,
            episodeId = episodeId,
        )
        return when (val result = playbackLauncher.launch(request)) {
            is Result.Success -> {
                val launchedMediaPath = path.trim()
                if (sourceOverride !== webControlPlaybackSource) {
                    clearWebControlPlaybackSource()
                }
                player = result.data.player
                activePlaybackSession = result.data.session
                playbackPositionMs = result.data.source.startPosition
                playbackDurationMs = 0L
                mediaPath = launchedMediaPath
                startSeconds = PlaybackTimingConventions.formatMpvStartSeconds(result.data.source.startPosition)
                if (path != previousMediaPath) {
                    subtitlePath = ""
                }
                saveDesktopPlaybackStartProgress(
                    session = result.data.session,
                    source = result.data.source,
                    saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                        savePlaybackProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                    },
                )
                indexedEntries.firstOrNull { it.path == launchedMediaPath }?.let { entry ->
                    selectedIndexEntry = entry
                }
                selectedIndexEntry?.takeIf { it.path == launchedMediaPath }?.let { entry ->
                    ensureSelectedMetadataCache(entry)
                }
                refreshRecentProgress()
                launchStatus = result.data.status
                if (playbackSpeed != PLAYBACK_SPEED_NORMAL) {
                    applyDesktopPlaybackSpeed(playbackSpeed)
                }
                result
            }
            is Result.Error -> {
                launchStatus = result.error.toUserMessage()
                result
            }
        }
    }

    SideEffect {
        webControlPlaybackHandlers.updatePlayEpisode { request, episode ->
            val selection = desktopWebControlPlaybackSourceSelection(
                episode = episode,
                savedSources = savedSources,
                activeSourceId = activeSourceId,
                activeSource = activeSource,
                activeLocalSource = activeLocalSource,
                loadSourceById = { id -> repositories.mediaSources.getSourceById(id).getOrNull() },
            )
            val progress = repositories.progress.getProgress(episode.id).getOrNull()
            val playbackSource = desktopWebControlPlaybackSource(request, episode, progress)
            when (
                val launched = launchDesktopPlayback(
                    path = playbackSource.uri,
                    startPositionMs = playbackSource.startPosition,
                    sourceOverride = selection.source,
                    sourceIdOverride = selection.sourceId,
                    episodeId = playbackSource.episodeId,
                )
            ) {
                is Result.Success -> {
                    if (selection.ownsSource) {
                        webControlPlaybackSource = selection.source
                    }
                    selectedDesktopSection = MiruPlayRouteSurface.player
                    desktopWebControlPlaybackStatus(
                        player = player,
                        session = activePlaybackSession,
                        mediaPath = mediaPath,
                        launchStatus = launchStatus,
                    )
                }
                is Result.Error -> {
                    if (selection.ownsSource) {
                        selection.source?.close()
                    }
                    throw IllegalStateException(launched.error.toUserMessage())
                }
            }
        }
        webControlPlaybackHandlers.updatePlaybackCommand { command ->
            val statusDto = desktopWebControlPlaybackCommand(
                request = command,
                player = player,
                session = activePlaybackSession,
                mediaPath = mediaPath,
                launchStatus = launchStatus,
                stopPlayback = {
                    stopDesktopPlayback(
                        player = player,
                        session = activePlaybackSession,
                        saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                            savePlaybackProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                        },
                    )
                    player = null
                    activePlaybackSession = null
                    resetDesktopPlaybackTimeline()
                    clearWebControlPlaybackSource()
                    refreshRecentProgress()
                },
            )
            launchStatus = webControlPlaybackCommandStatus(command)
            if (command.playbackCommandKind() == WebControlPlaybackCommandKind.SPEED) {
                playbackSpeed = coercePlaybackSpeed(command.playbackSpeed())
            }
            if (!statusDto.isPlaying && command.command.equals("stop", ignoreCase = true)) {
                resetDesktopPlaybackTimeline()
                selectedDesktopSection = MiruPlayRouteSurface.details
            } else {
                applyDesktopPlaybackStatusToTimeline(statusDto)
            }
            statusDto
        }
    }

    suspend fun nextDesktopPlaybackSource(currentEpisodeId: String): PlaybackSource? {
        return nextPlaybackSourceResolver.build(currentEpisodeId)
    }

    LaunchedEffect(player, activePlaybackSession) {
        val activePlayer = player ?: return@LaunchedEffect
        val session = activePlaybackSession ?: return@LaunchedEffect
        var progressPollElapsedMs = 0L
        while (true) {
            delay(PLAYBACK_EOF_POLL_INTERVAL_MS)
            if (player !== activePlayer || activePlaybackSession !== session) {
                return@LaunchedEffect
            }
            activePlayer.queryTimePositionMs().getOrNull()?.let { positionMs ->
                session.syncPosition(positionMs)
                playbackPositionMs = positionMs
            } ?: run {
                playbackPositionMs = session.currentPositionMs()
            }
            activePlayer.queryDurationMs().getOrNull()?.takeIf { it > 0L }?.let { durationMs ->
                playbackDurationMs = durationMs
            }
            if (activePlayer.queryEofReached().getOrNull() == true) {
                val completed = saveDesktopPlaybackCompletionProgress(
                    session = session,
                    queryDurationMs = { activePlayer.queryDurationMs() },
                    queryPositionMs = { activePlayer.queryTimePositionMs() },
                    saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                        savePlaybackProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                    },
                )
                when (completed) {
                    is Result.Success -> {
                        val nextTarget = if (playbackEndAction == PlaybackEndAction.PLAY_NEXT_EPISODE) {
                            nextDesktopPlaybackSource(session.episodeId)
                        } else {
                            null
                        }
                        scope.launch {
                            when (val synced = bangumiSyncCore.markEpisodeWatched(session.episodeId)) {
                                is Result.Success -> Unit
                                is Result.Error -> bangumiStatus = synced.error.toUserMessage()
                            }
                        }
                        activePlayer.stop()
                        val nextPlayback = desktopWebControlNextPlaybackSource(
                            nextTarget = nextTarget,
                            currentWebControlPlaybackSource = webControlPlaybackSource,
                        )
                        player = null
                        activePlaybackSession = null
                        refreshRecentProgress()
                        if (nextTarget != null) {
                            when (
                                val nextLaunch = launchDesktopPlayback(
                                    path = nextTarget.uri,
                                    startPositionMs = nextTarget.startPosition,
                                    sourceOverride = nextPlayback.source,
                                    sourceIdOverride = nextPlayback.sourceId,
                                    episodeId = nextPlayback.episodeId,
                                )
                            ) {
                                is Result.Success -> Unit
                                is Result.Error -> {
                                    resetDesktopPlaybackTimeline()
                                    clearWebControlPlaybackSource()
                                    launchStatus = nextLaunch.error.toUserMessage()
                                    selectedDesktopSection = MiruPlayRouteSurface.details
                                }
                            }
                        } else {
                            resetDesktopPlaybackTimeline()
                            clearWebControlPlaybackSource()
                            launchStatus = mpvPlaybackCompletedStatus(completed.data)
                            selectedDesktopSection = MiruPlayRouteSurface.details
                        }
                        return@LaunchedEffect
                    }
                    is Result.Error -> {
                        launchStatus = completed.error.toUserMessage()
                        return@LaunchedEffect
                    }
                }
            }
            if (!activePlayer.isActive()) {
                stopDesktopPlayback(
                    player = null,
                    session = session,
                    saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                        savePlaybackProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                    },
                )
                player = null
                activePlaybackSession = null
                resetDesktopPlaybackTimeline()
                clearWebControlPlaybackSource()
                refreshRecentProgress()
                launchStatus = mpvExitedStatus()
                return@LaunchedEffect
            }
            progressPollElapsedMs += PLAYBACK_EOF_POLL_INTERVAL_MS
            if (progressPollElapsedMs < PLAYBACK_PROGRESS_POLL_INTERVAL_MS) {
                continue
            }
            progressPollElapsedMs = 0L
            when (
                val synced = syncObservedPlaybackProgress(
                    session = session,
                    queryPositionMs = { activePlayer.queryTimePositionMs() },
                    saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                        savePlaybackProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                    },
                )
            ) {
                is Result.Success -> {
                    val positionMs = synced.data
                    if (positionMs != null) {
                        playbackPositionMs = positionMs
                        refreshRecentProgress()
                        launchStatus = mpvPositionSyncedStatus(positionMs)
                    }
                }
                is Result.Error -> Unit
            }
        }
    }

    suspend fun refreshRssSubscriptions() {
        runCatching {
            repositories.cloudDriveAutomation.observeSubscriptions().first()
        }.onSuccess { subscriptions ->
            rssSubscriptions = subscriptions
            selectedRssSubscription = selectedRssSubscription.retainedSelectionInRssSubscriptions(subscriptions)
            cloudRssStatus = rssSubscriptionsShowingStatus(subscriptions.size)
        }.onFailure { error ->
            cloudRssStatus = rssSubscriptionsRefreshFailedStatus(error.message)
        }
    }

    suspend fun saveCloudRssConfig(
        enabledOverride: Boolean = cloudEnabled,
        proxyEnabledOverride: Boolean = rssProxyEnabled,
        proxyHostOverride: String = rssProxyHost,
        proxyPortOverride: String = rssProxyPort,
    ) {
        val interval = parseCloudDriveIntervalMinutes(cloudIntervalMinutes)
        val proxyPort = parseRssProxyPort(proxyPortOverride)
        when (val result = cloudRssActions.saveConfig(
            endpointUrl = cloudEndpointUrl,
            username = cloudUsername,
            webDavSourceId = cloudLinkedSourceId,
            inboxPath = cloudInboxPath,
            libraryPath = cloudLibraryPath,
            libraryMode = cloudLibraryMode,
            intervalMinutes = interval,
            enabled = enabledOverride,
            rssProxyEnabled = proxyEnabledOverride,
            rssProxyHost = proxyHostOverride,
            rssProxyPort = proxyPort,
        )) {
            is CloudDriveConfigActionResult.Saved -> {
                cloudIntervalMinutes = interval.toString()
                rssProxyPort = proxyPort.toString()
                cloudRssScheduler.syncPeriodicWork(result.config)
                cloudRssStatus = result.status
            }
            is CloudDriveConfigActionResult.Failed -> cloudRssStatus = result.status
        }
    }

    suspend fun scanCurrentSource(updateStatus: (String) -> Unit) {
        val sourceId = activeSourceId
        val source = activeSource ?: activeLocalSource
        if (sourceId == null || source == null) {
            updateStatus(openSourceBeforeScanningStatus())
            return
        }
        updateStatus(source.info.scanningStatus())
        when (
            val scan = scanAndIndexDesktopSource(
                sourceInfo = source.info.copy(id = sourceId),
                indexRepository = repositories.index,
                metadataRepository = repositories.metadata,
            )
        ) {
            is Result.Success -> {
                indexedEntries = scan.data.videoEntries
                selectedIndexEntry = null
                lastScanAt = System.currentTimeMillis()
                repositories.scanPreferences.setLastScanAt(lastScanAt)
                updateStatus(scan.data.completedStatus)
                libraryStatus = scan.data.completedStatus
            }
            is Result.Error -> updateStatus(scan.error.toUserMessage())
        }
    }

    LaunchedEffect(selectedDesktopSection.id, activeSourceId, autoScanEnabled, autoScanIntervalHours, lastScanAt) {
        if (selectedDesktopSection == MiruPlayRouteSurface.library &&
            activeSourceId != null &&
            repositories.scanPreferences.shouldAutoScan()
        ) {
            scanCurrentSource { libraryStatus = it }
        }
    }

    suspend fun rescanLinkedCloudSource(reason: String): String? {
        return when (
            val rescan = resolveAndRescanCloudRssLinkedSource(
                sourceId = cloudLinkedSourceId,
                reason = reason,
                savedSources = savedSources,
                mediaSources = repositories.mediaSources,
                indexRepository = repositories.index,
                metadataRepository = repositories.metadata,
                onSourcesLoaded = { loaded -> savedSources = loaded },
                onRescanStarting = { sourceInfo ->
                    cloudRssStatus = cloudRssRescanStartedStatus(reason, sourceInfo.sourcePickerTitle())
                },
            )
        ) {
            is Result.Success -> when (val selection = rescan.data) {
                DesktopCloudRssLinkedSourceRescanSelection.MissingLink -> null
                is DesktopCloudRssLinkedSourceRescanSelection.MissingSource -> {
                    cloudRssStatus = cloudRssScanSourceMissingStatus()
                    null
                }
                is DesktopCloudRssLinkedSourceRescanSelection.Ready -> {
                    val sourceInfo = selection.sourceInfo
                    val result = selection.result
                    if (activeSourceId == sourceInfo.id) {
                        indexedEntries = result.videoEntries
                        selectedIndexEntry = null
                    }
                    when (result.targetStatus) {
                        DesktopCloudRssRescanTargetStatus.LIBRARY -> libraryStatus = result.completedStatus
                        DesktopCloudRssRescanTargetStatus.REMOTE -> remoteStatus = result.completedStatus
                    }
                    result.completedStatus
                }
            }
            is Result.Error -> {
                cloudRssStatus = rescan.error.toUserMessage()
                null
            }
        }
    }

    suspend fun loadCloudDriveDirectory(path: String) {
        when (val loading = cloudDirectoryActions.loading(cloudDirectoryBrowser, path)) {
            CloudDriveDirectoryLoadResult.Ignored -> return
            is CloudDriveDirectoryLoadResult.Failed -> {
                cloudDirectoryBrowser = loading.state.copy(message = cloudRssStatusText(loading.status))
                cloudRssStatus = loading.status
            }
            is CloudDriveDirectoryLoadResult.Loading -> {
                cloudDirectoryBrowser = loading.state
                val loaded = cloudDirectoryActions.load(loading.state)
                when (val result = cloudDirectoryActions.applyLoadedIfCurrent(cloudDirectoryBrowser, loaded)) {
                    CloudDriveDirectoryLoadResult.Ignored -> Unit
                    is CloudDriveDirectoryLoadResult.Loading -> Unit
                    is CloudDriveDirectoryLoadResult.Loaded -> {
                        cloudDirectoryBrowser = result.state
                        cloudRssStatus = result.status
                    }
                    is CloudDriveDirectoryLoadResult.Failed -> {
                        cloudDirectoryBrowser = result.state
                        cloudRssStatus = result.status
                    }
                }
            }
            is CloudDriveDirectoryLoadResult.Loaded -> {
                cloudDirectoryBrowser = loading.state
                cloudRssStatus = loading.status
            }
        }
    }

    fun openCloudDriveDirectory(target: CloudDriveDirectoryTarget) {
        scope.launch {
            val initialPath = when (target) {
                CloudDriveDirectoryTarget.INBOX -> cloudInboxPath
                CloudDriveDirectoryTarget.LIBRARY -> cloudLibraryPath
            }
            when (
                val opened = cloudDirectoryActions.open(
                    target = target,
                    endpointUrl = cloudEndpointUrl,
                    tokenInput = cloudToken,
                    savedToken = repositories.credentials.cloudDriveToken,
                    initialPath = initialPath,
                )
            ) {
                is CloudDriveDirectoryOpenResult.Ready -> {
                    cloudDirectoryBrowser = opened.state
                    loadCloudDriveDirectory(opened.loadPath)
                }
                is CloudDriveDirectoryOpenResult.Invalid -> {
                    cloudRssStatus = opened.status
                }
                is CloudDriveDirectoryOpenResult.Failed -> {
                    cloudRssStatus = opened.status
                }
            }
        }
    }

    fun selectCloudDriveDirectory(
        target: CloudDriveDirectoryTarget,
        path: String,
    ) {
        val selection = selectSharedCloudDriveDirectory(target, path)
        when (selection.target) {
            CloudDriveDirectoryTarget.INBOX -> cloudInboxPath = selection.path
            CloudDriveDirectoryTarget.LIBRARY -> cloudLibraryPath = selection.path
        }
        cloudDirectoryBrowser = cloudDirectoryBrowser.copy(open = false, isLoading = false)
        cloudRssStatus = selection.status
    }

    LaunchedEffect(cloudRssSchedulerState.lastRunCompletedAt) {
        if (cloudRssSchedulerState.lastRunCompletedAt > 0L) {
            rescanLinkedCloudSource(cloudRssScheduledSyncCompleteStatus())
        }
    }

    suspend fun activateSavedSource(sourceInfo: MediaSourceInfo) {
        applySourceActivationState(
            activationState = sourceInfo.desktopSourceActivationState(saved = true),
            loadIndexed = true,
        )
    }

    val contentScrollState = rememberScrollState()
    var libraryHeaderFocusVersion by remember { mutableIntStateOf(0) }
    var libraryPanelFocusVersion by remember { mutableIntStateOf(0) }
    var playerSettingsFocusVersion by remember { mutableIntStateOf(0) }
    var playerRuntimeFocusVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedDesktopSection) {
        contentScrollState.scrollTo(0)
    }
    LaunchedEffect(selectedDesktopSection, fullscreen) {
        onPlayerFullscreenActiveChange(
            shouldUseDesktopPlayerFullscreen(
                selectedSection = selectedDesktopSection,
                fullscreen = fullscreen,
            )
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && isDesktopBackKey(event.key)) {
                    selectedDesktopSection.desktopBackTarget()?.let { target ->
                        selectedDesktopSection = target
                        true
                    } ?: false
                } else {
                    false
                }
            }
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.MAIN_SECTION_GAP_DP.dp),
    ) {
        if (selectedDesktopSection != MiruPlayRouteSurface.library && selectedDesktopSection != MiruPlayRouteSurface.player) {
            DesktopTvNavigation(
                selectedSection = selectedDesktopSection,
                onSectionSelected = { selectedDesktopSection = it },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(contentScrollState),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            when (selectedDesktopSection) {
                MiruPlayRouteSurface.library -> {
                    DesktopLibraryHeader(
                        onScan = {
                            scope.launch {
                                scanCurrentSource { libraryStatus = it }
                            }
                        },
                        onSettings = { selectedDesktopSection = MiruPlayRouteSurface.settings },
                        focusVersion = libraryHeaderFocusVersion,
                        onFocusNextPanel = {
                            libraryPanelFocusVersion += 1
                            true
                        },
                    )
                }
                MiruPlayRouteSurface.player -> Unit
                else -> DesktopTvHeader(selectedSection = selectedDesktopSection)
            }
            when (selectedDesktopSection) {
                MiruPlayRouteSurface.library -> {
                    LibraryPanel(
                libraryRoot = libraryRoot,
                onLibraryRootChange = { libraryRoot = it },
                savedSources = savedSources,
                activeSourceId = activeSourceId,
                onSavedSourceSelected = { source ->
                    scope.launch {
                        activateSavedSource(source)
                    }
                },
                indexQuery = indexQuery,
                onIndexQueryChange = { indexQuery = it },
                entries = indexedEntries,
                selectedEntry = selectedIndexEntry,
                status = libraryStatus,
                onOpenLocal = {
                    scope.launch {
                        val rootText = libraryRoot.trim()
                        if (rootText.isBlank()) {
                            libraryStatus = localRootRequiredStatus()
                            return@launch
                        }
                        val root = Paths.get(rootText).toAbsolutePath().normalize()
                        val sourceInfo = MediaSourceInfoConventions.local(
                            name = root.fileName?.toString() ?: root.toString(),
                            rootPath = root.toString(),
                            isConnected = true,
                        )
                        when (
                            val result = openDesktopSource(
                                repository = repositories.mediaSources,
                                mediaSourceFactory = desktopMediaSourceFactory,
                                sourceInfo = sourceInfo,
                            )
                        ) {
                            is Result.Success -> {
                                activeSourceId = result.data.sourceInfo.id
                                activeSource = result.data.source
                                activeLocalSource = result.data.source as? DesktopLocalMediaSource
                                applySourceFormState(result.data.formState)
                                savedSources = savedSources.upsertById(result.data.sourceInfo)
                                libraryStatus = result.data.status
                            }
                            is Result.Error -> libraryStatus = result.error.toUserMessage()
                        }
                    }
                },
                onScan = {
                    scope.launch {
                        scanCurrentSource { libraryStatus = it }
                    }
                },
                onSearch = {
                    scope.launch {
                        val sourceId = activeSourceId
                        if (sourceId == null) {
                            libraryStatus = openSourceBeforeSearchingStatus()
                            return@launch
                        }
                        when (val result = repositories.index.queryIndex(sourceId, indexQuery.trim())) {
                            is Result.Success -> {
                                indexedEntries = result.data.mediaFilesOnly()
                                libraryStatus = indexedSearchStatus(
                                    query = indexQuery,
                                    hasResults = result.data.isNotEmpty(),
                                    displayedResultCount = indexedEntries.size,
                                )
                            }
                            is Result.Error -> libraryStatus = result.error.toUserMessage()
                        }
                    }
                },
                onClearIndex = {
                    scope.launch {
                        val sourceId = activeSourceId
                        if (sourceId == null) {
                            libraryStatus = openSourceBeforeClearingIndexStatus()
                            return@launch
                        }
                        when (val result = repositories.index.clearIndex(sourceId)) {
                            is Result.Success -> {
                                indexedEntries = emptyList()
                                selectedIndexEntry = null
                                bangumiResults = emptyList()
                                selectedBangumiResult = null
                                bangumiBatchMatches = emptyList()
                                selectedBangumiBatchMatch = null
                                bangumiBatchPlan = null
                                bangumiBatchRollback = emptyList()
                                libraryStatus = indexClearedStatus(sourceId)
                                bangumiStatus = metadataInitialStatus(BANGUMI_METADATA_SOURCE_NAME)
                            }
                            is Result.Error -> libraryStatus = result.error.toUserMessage()
                        }
                    }
                },
                onRemoveSource = {
                    scope.launch {
                        val sourceId = activeSourceId
                        if (sourceId == null) {
                            libraryStatus = sourceRemoveRequiredStatus()
                            return@launch
                        }
                        when (val result = mediaSourceActions.removeSource(sourceId)) {
                            is Result.Success -> {
                                activeSourceId = null
                                activeSource = null
                                activeLocalSource = null
                                savedSources = savedSources.filterNot { it.id == sourceId }
                                libraryRoot = ""
                                webDavUrl = ""
                                webDavUsername = ""
                                webDavPassword = ""
                                smbUrl = ""
                                smbDomain = ""
                                smbUsername = ""
                                smbPassword = ""
                                remotePath = ""
                                remoteEntries = emptyList()
                                selectedRemoteEntry = null
                                indexedEntries = emptyList()
                                selectedIndexEntry = null
                                bangumiResults = emptyList()
                                selectedBangumiResult = null
                                bangumiBatchMatches = emptyList()
                                selectedBangumiBatchMatch = null
                                bangumiBatchPlan = null
                                bangumiBatchRollback = emptyList()
                                mediaPath = ""
                                libraryStatus = sourceRemovedStatus()
                                remoteStatus = remoteBrowserInitialStatus()
                                bangumiStatus = metadataInitialStatus(BANGUMI_METADATA_SOURCE_NAME)
                            }
                            is Result.Error -> libraryStatus = result.error.toUserMessage()
                        }
                    }
                },
                mergeSameAnimeEnabled = mergeSameAnimeEnabled,
                onEntryFocused = { entry ->
                    selectedIndexEntry = entry
                    mediaPath = entry.path
                    launchStatus = entry.selectedForPlaybackStatus()
                },
                onEntrySelected = { entry ->
                    selectedIndexEntry = entry
                    mediaPath = entry.path
                    launchStatus = entry.selectedForPlaybackStatus()
                    selectedDesktopSection = MiruPlayRouteSurface.details
                },
                focusVersion = libraryPanelFocusVersion,
                onFocusPreviousPanel = {
                    libraryHeaderFocusVersion += 1
                    true
                },
            )
            RemoteSourcesPanel(
                webDavUrl = webDavUrl,
                onWebDavUrlChange = { webDavUrl = it },
                webDavUsername = webDavUsername,
                onWebDavUsernameChange = { webDavUsername = it },
                webDavPassword = webDavPassword,
                onWebDavPasswordChange = { webDavPassword = it },
                smbUrl = smbUrl,
                onSmbUrlChange = { smbUrl = it },
                smbDomain = smbDomain,
                onSmbDomainChange = { smbDomain = it },
                smbUsername = smbUsername,
                onSmbUsernameChange = { smbUsername = it },
                smbPassword = smbPassword,
                onSmbPasswordChange = { smbPassword = it },
                remotePath = remotePath,
                entries = remoteEntries,
                selectedEntry = selectedRemoteEntry,
                status = remoteStatus,
                onOpenWebDav = {
                    scope.launch {
                        val url = webDavUrl.trim()
                        if (url.isBlank()) {
                            remoteStatus = webDavUrlRequiredStatus()
                            return@launch
                        }
                        val sourceInfo = MediaSourceInfoConventions.webDav(
                            url = url,
                            username = webDavUsername.trim(),
                            password = webDavPassword,
                            isConnected = true,
                        )
                        when (
                            val result = openDesktopSource(
                                repository = repositories.mediaSources,
                                mediaSourceFactory = desktopMediaSourceFactory,
                                sourceInfo = sourceInfo,
                            )
                        ) {
                            is Result.Success -> {
                                activeSourceId = result.data.sourceInfo.id
                                activeSource = result.data.source
                                activeLocalSource = null
                                applySourceFormState(result.data.formState)
                                savedSources = savedSources.upsertById(result.data.sourceInfo)
                                remotePath = ""
                                remoteStatus = result.data.status
                                if (result.data.opensRemoteRoot) {
                                    loadRemoteDirectory(result.data.source, "")
                                }
                            }
                            is Result.Error -> remoteStatus = result.error.toUserMessage()
                        }
                    }
                },
                onOpenSmb = {
                    scope.launch {
                        val url = smbUrl.trim()
                        if (url.isBlank()) {
                            remoteStatus = smbUrlRequiredStatus()
                            return@launch
                        }
                        val sourceInfo = MediaSourceInfoConventions.smb(
                            url = url,
                            domain = smbDomain.trim(),
                            username = smbUsername.trim(),
                            password = smbPassword,
                            isConnected = true,
                        )
                        when (
                            val result = openDesktopSource(
                                repository = repositories.mediaSources,
                                mediaSourceFactory = desktopMediaSourceFactory,
                                sourceInfo = sourceInfo,
                            )
                        ) {
                            is Result.Success -> {
                                activeSourceId = result.data.sourceInfo.id
                                activeSource = result.data.source
                                activeLocalSource = null
                                applySourceFormState(result.data.formState)
                                savedSources = savedSources.upsertById(result.data.sourceInfo)
                                remotePath = ""
                                remoteStatus = result.data.status
                                if (result.data.opensRemoteRoot) {
                                    loadRemoteDirectory(result.data.source, "")
                                }
                            }
                            is Result.Error -> remoteStatus = result.error.toUserMessage()
                        }
                    }
                },
                onUp = {
                    val source = activeSource
                    val parent = MediaPathConventions.remoteParent(remotePath)
                    if (source == null || parent == null) {
                        remoteStatus = remoteRootStatus()
                    } else {
                        scope.launch { loadRemoteDirectory(source, parent) }
                    }
                },
                onScan = {
                    scope.launch {
                        scanCurrentSource { remoteStatus = it }
                    }
                },
                onEntryFocused = { entry ->
                    selectedRemoteEntry = entry
                    if (!entry.isDirectory) {
                        selectedIndexEntry = null
                        mediaPath = entry.path
                        launchStatus = entry.selectedRemoteForPlaybackStatus()
                    }
                },
                onEntrySelected = { entry ->
                    selectedRemoteEntry = entry
                    val source = activeSource
                    if (entry.isDirectory && source != null) {
                        scope.launch { loadRemoteDirectory(source, entry.path) }
                    } else if (entry.isDirectory) {
                        remoteStatus = openRemoteSourceBeforeBrowsingStatus()
                    } else {
                        mediaPath = entry.path
                        launchStatus = entry.selectedRemoteForPlaybackStatus()
                    }
                },
                    )
                }
                MiruPlayRouteSurface.details -> {
                    DesktopDetailHero(
                        entry = selectedIndexEntry,
                        source = activeSource?.info,
                        episodeCount = detailEpisodes.size,
                        focusVersion = detailHeroFocusVersion,
                        onFocusRecentPlayback = {
                            moveDetailPanelFocus(DesktopDetailFocusPanel.Hero, 1)
                        },
                        onBackToLibrary = { selectedDesktopSection = MiruPlayRouteSurface.library },
                        onPlay = {
                            selectedIndexEntry?.let { entry ->
                                mediaPath = entry.path
                                launchStatus = entry.selectedForPlaybackStatus()
                                selectedDesktopSection = MiruPlayRouteSurface.player
                            }
                        },
                    )
                    DetailEpisodePanel(
                        episodes = detailEpisodes,
                        selectedEntry = selectedIndexEntry,
                        selectedSeason = selectedDetailEpisodeSeason,
                        recentRecords = recentProgress.map { it.progress },
                        focusVersion = detailEpisodeFocusVersion,
                        onFocusPreviousPanel = {
                            moveDetailPanelFocus(DesktopDetailFocusPanel.EpisodeList, -1)
                        },
                        onFocusNextPanel = {
                            moveDetailPanelFocus(DesktopDetailFocusPanel.EpisodeList, 1)
                        },
                        onSeasonSelected = { season -> selectedDetailEpisodeSeason = season },
                        onEpisodeFocused = { episode ->
                            selectedIndexEntry = episode
                            mediaPath = episode.path
                            launchStatus = episode.selectedForPlaybackStatus()
                        },
                        onEpisodeSelected = { episode ->
                            selectedIndexEntry = episode
                            mediaPath = episode.path
                            launchStatus = episode.selectedForPlaybackStatus()
                            selectedDesktopSection = MiruPlayRouteSurface.player
                        },
                    )
                    BangumiPanel(
                        query = bangumiQuery,
                        onQueryChange = { bangumiQuery = it },
                        selectedIndexEntry = selectedIndexEntry,
                        results = bangumiResults,
                        selectedResult = selectedBangumiResult,
                        batchMatches = bangumiBatchMatches,
                        selectedBatchMatch = selectedBangumiBatchMatch,
                        batchPlan = bangumiBatchPlan,
                        status = bangumiStatus,
                        isSyncingProgress = bangumiSyncing,
                        onUseSelectedEntry = {
                    val query = selectedIndexEntry?.metadataQuery()
                    if (query == null) {
                        bangumiStatus = metadataIndexedVideoRequiredStatus()
                    } else {
                        bangumiQuery = query
                        bangumiStatus = metadataQuerySetFromIndexStatus()
                    }
                },
                onSearch = {
                    scope.launch {
                        when (val result = bangumiIndexMetadataCoordinator.search(
                            query = bangumiQuery,
                            selectedEntry = selectedIndexEntry,
                            onSearchStarted = { bangumiStatus = it },
                        )) {
                            is Result.Success -> {
                                bangumiQuery = result.data.query
                                bangumiResults = result.data.results
                                selectedBangumiResult = result.data.selectedResult
                                bangumiStatus = result.data.status
                            }
                            is Result.Error -> bangumiStatus = result.error.toUserMessage()
                        }
                    }
                },
                onBatchPreview = {
                    scope.launch {
                        when (val preview = bangumiIndexMetadataCoordinator.previewBatch(
                            sourceId = activeSourceId,
                            onSearchStarted = { bangumiStatus = it },
                        )) {
                            is Result.Success -> {
                                bangumiBatchMatches = preview.data.matches
                                bangumiBatchPlan = preview.data.plan
                                selectedBangumiBatchMatch = preview.data.selectedMatch
                                bangumiStatus = preview.data.status
                            }
                            is Result.Error -> bangumiStatus = preview.error.toUserMessage()
                        }
                    }
                },
                onBatchApply = {
                    scope.launch {
                        when (val apply = bangumiIndexMetadataCoordinator.applyBatch(
                            sourceId = activeSourceId,
                            matches = bangumiBatchMatches,
                        )) {
                            is Result.Success -> {
                                bangumiBatchPlan = apply.data.plan
                                if (apply.data.plan?.readyUpdates?.isNotEmpty() == true) {
                                    bangumiBatchRollback = apply.data.write.rollbackEntries
                                }
                                indexedEntries = indexedEntries.replaceByMediaKeys(apply.data.write.updatedEntries)
                                selectedIndexEntry = selectedIndexEntry.updatedSelectionAfterReplacingByMediaKeys(
                                    apply.data.write.updatedEntries,
                                )
                                bangumiStatus = apply.data.status
                            }
                            is Result.Error -> bangumiStatus = apply.error.toUserMessage()
                        }
                    }
                },
                onBatchUndo = {
                    scope.launch {
                        when (val undo = bangumiIndexMetadataCoordinator.undoBatch(
                            sourceId = activeSourceId,
                            rollbackEntries = bangumiBatchRollback,
                        )) {
                            is Result.Success -> {
                                indexedEntries = indexedEntries.replaceByMediaKeys(undo.data.restore.rollbackEntries)
                                selectedIndexEntry = selectedIndexEntry.updatedSelectionAfterReplacingByMediaKeys(
                                    undo.data.restore.rollbackEntries,
                                )
                                if (undo.data.restore.rollbackEntries.isNotEmpty()) {
                                    bangumiBatchRollback = emptyList()
                                }
                                bangumiStatus = undo.data.status
                            }
                            is Result.Error -> bangumiStatus = undo.error.toUserMessage()
                        }
                    }
                },
                onBatchMatchSelected = { match ->
                    selectedBangumiBatchMatch = match
                    bangumiQuery = match.query
                    selectedBangumiResult = match.result
                    bangumiStatus = match.selectedReviewStatus()
                },
                onBatchCandidateSelected = { match, candidate ->
                    scope.launch {
                        when (val selection = bangumiIndexMetadataCoordinator.selectBatchCandidate(
                            sourceId = activeSourceId,
                            matches = bangumiBatchMatches,
                            match = match,
                            candidate = candidate,
                        )) {
                            is Result.Success -> {
                                bangumiBatchMatches = selection.data.updatedMatches
                                selectedBangumiBatchMatch = selection.data.updatedMatch
                                selectedBangumiResult = selection.data.selectedResult
                                bangumiBatchPlan = selection.data.plan
                                bangumiQuery = match.query
                                bangumiStatus = selection.data.status
                            }
                            is Result.Error -> bangumiStatus = selection.error.toUserMessage()
                        }
                    }
                },
                onBatchAcceptReview = {
                    scope.launch {
                        when (val review = bangumiIndexMetadataCoordinator.acceptBatchReview(
                            sourceId = activeSourceId,
                            match = selectedBangumiBatchMatch,
                        )) {
                            is Result.Success -> {
                                bangumiBatchPlan = review.data.plan
                                if (review.data.plan?.readyUpdates?.isNotEmpty() == true) {
                                    bangumiBatchRollback = review.data.write.rollbackEntries
                                }
                                indexedEntries = indexedEntries.replaceByMediaKeys(review.data.write.updatedEntries)
                                selectedIndexEntry = selectedIndexEntry.updatedSelectionAfterReplacingByMediaKeys(
                                    review.data.write.updatedEntries,
                                )
                                bangumiStatus = review.data.status
                            }
                            is Result.Error -> bangumiStatus = review.error.toUserMessage()
                        }
                    }
                },
                onResultSelected = { result ->
                    selectedBangumiResult = result
                    bangumiStatus = result.selectedMetadataStatus()
                },
                onApply = {
                    scope.launch {
                        when (val apply = bangumiIndexMetadataCoordinator.applyEntryMetadata(
                            sourceId = activeSourceId,
                            entry = selectedIndexEntry,
                            match = selectedBangumiResult ?: bangumiResults.firstOrNull(),
                            relatedEntries = detailEpisodes,
                        )) {
                            is Result.Success -> {
                                apply.data.updatedEntry?.let { updated ->
                                    indexedEntries = indexedEntries.replaceByMediaKey(updated)
                                    selectedIndexEntry = updated
                                }
                                bangumiStatus = apply.data.status
                            }
                            is Result.Error -> bangumiStatus = apply.error.toUserMessage()
                        }
                    }
                },
                onClear = {
                    scope.launch {
                        when (val clear = bangumiIndexMetadataCoordinator.clearEntryMetadata(
                            sourceId = activeSourceId,
                            entry = selectedIndexEntry,
                        )) {
                            is Result.Success -> {
                                clear.data.updatedEntry?.let { updated ->
                                    indexedEntries = indexedEntries.replaceByMediaKey(updated)
                                    selectedIndexEntry = updated
                                }
                                bangumiStatus = clear.data.status
                            }
                            is Result.Error -> bangumiStatus = clear.error.toUserMessage()
                        }
                    }
                },
                onSyncProgress = {
                    scope.launch {
                        val entry = selectedIndexEntry
                        if (entry == null || entry.isDirectory) {
                            bangumiStatus = metadataApplyEntryRequiredStatus(BANGUMI_METADATA_SOURCE_NAME)
                            return@launch
                        }
                        syncSelectedBangumiProgress(entry)
                    }
                },
                onFocusPreviousPanel = {
                    moveDetailPanelFocus(DesktopDetailFocusPanel.BangumiMetadata, -1)
                },
                onFocusNextPanel = {
                    moveDetailPanelFocus(DesktopDetailFocusPanel.BangumiMetadata, 1)
                },
                focusVersion = bangumiFocusVersion,
            )
            RecentPlaybackPanel(
                records = recentProgress,
                selectedRecord = selectedRecentProgress,
                status = recentStatus,
                focusVersion = recentPlaybackFocusVersion,
                onFocusPreviousPanel = {
                    moveDetailPanelFocus(DesktopDetailFocusPanel.RecentPlayback, -1)
                },
                onFocusNextPanel = {
                    moveDetailPanelFocus(DesktopDetailFocusPanel.RecentPlayback, 1)
                },
                onRefresh = {
                    scope.launch {
                        refreshRecentProgress()
                    }
                },
                onRecordSelected = { record ->
                    selectedRecentProgress = record
                    mediaPath = record.progress.episodeId
                    startSeconds = record.resumeStartSecondsText()
                    launchStatus = record.loadedPlaybackStatus()
                },
                onClearSelected = {
                    scope.launch {
                        val selected = selectedRecentProgress
                        if (selected == null) {
                            recentStatus = recentPlaybackRequiredStatus()
                            return@launch
                        }
                        when (val result = repositories.progress.deleteProgress(selected.progress.episodeId)) {
                            is Result.Success -> {
                                selectedRecentProgress = null
                                refreshRecentProgress()
                            }
                            is Result.Error -> recentStatus = result.error.toUserMessage()
                        }
                    }
                },
            )
                    MediaDetailsPanel(
                source = activeSource?.info,
                indexEntry = selectedIndexEntry,
                remoteEntry = selectedRemoteEntry,
                recentRecord = selectedRecentProgress?.progress,
                focusVersion = mediaDetailsFocusVersion,
                onFocusPreviousPanel = {
                    moveDetailPanelFocus(DesktopDetailFocusPanel.MediaDetails, -1)
                },
            )
                }
                MiruPlayRouteSurface.settings -> {
                    CloudRssPanel(
                endpointUrl = cloudEndpointUrl,
                onEndpointUrlChange = { cloudEndpointUrl = it },
                username = cloudUsername,
                onUsernameChange = { cloudUsername = it },
                token = cloudToken,
                onTokenChange = { cloudToken = it },
                password = cloudPassword,
                onPasswordChange = { cloudPassword = it },
                inboxPath = cloudInboxPath,
                onInboxPathChange = { cloudInboxPath = it },
                libraryPath = cloudLibraryPath,
                onLibraryPathChange = { cloudLibraryPath = it },
                libraryMode = cloudLibraryMode,
                onLibraryModeChange = { cloudLibraryMode = it },
                directoryBrowser = cloudDirectoryBrowser,
                onPickCloudDriveDirectory = ::openCloudDriveDirectory,
                onBrowseCloudDriveDirectory = { path ->
                    scope.launch {
                        loadCloudDriveDirectory(path)
                    }
                },
                onSelectCloudDriveDirectory = ::selectCloudDriveDirectory,
                onCloseCloudDriveDirectory = {
                    cloudDirectoryBrowser = cloudDirectoryBrowser.copy(open = false, isLoading = false)
                },
                intervalMinutes = cloudIntervalMinutes,
                onIntervalMinutesChange = { cloudIntervalMinutes = it },
                enabled = cloudEnabled,
                onEnabledChange = { cloudEnabled = it },
                proxyEnabled = rssProxyEnabled,
                onProxyEnabledChange = { enabled ->
                    rssProxyEnabled = enabled
                    scope.launch {
                        saveCloudRssConfig(proxyEnabledOverride = enabled)
                    }
                },
                proxyHost = rssProxyHost,
                onProxyHostChange = { rssProxyHost = it },
                proxyPort = rssProxyPort,
                onProxyPortChange = { rssProxyPort = it },
                rssName = rssName,
                onRssNameChange = { rssName = it },
                rssUrl = rssUrl,
                onRssUrlChange = { rssUrl = it },
                rssFilter = rssFilter,
                onRssFilterChange = { rssFilter = it },
                rssEnabled = rssEnabled,
                onRssEnabledChange = { rssEnabled = it },
                subscriptions = rssSubscriptions,
                selectedSubscription = selectedRssSubscription,
                status = cloudRssStatus,
                schedulerStatus = cloudRssSchedulerState.schedulerStatus(),
                bangumiToken = bangumiTokenInput,
                onBangumiTokenChange = { bangumiTokenInput = it },
                bangumiTokenConfigured = bangumiTokenConfigured,
                linkedSourceLabel = settingsLinkedSourceLabel(savedSources, cloudLinkedSourceId),
                autoScanEnabled = autoScanEnabled,
                autoScanIntervalHours = autoScanIntervalHours,
                scanIntervalOptionsHours = scanPreferencesIntervalOptionsHours,
                lastScanAt = lastScanAt,
                mergeSameAnimeEnabled = mergeSameAnimeEnabled,
                onToggleAutoScan = {
                    val enabled = !autoScanEnabled
                    scope.launch {
                        applyScanPreferenceSnapshot(settingsPreferenceActions.setAutoScanEnabled(enabled))
                    }
                },
                onScanIntervalSelected = { hours ->
                    scope.launch {
                        applyScanPreferenceSnapshot(settingsPreferenceActions.setAutoScanIntervalHours(hours))
                    }
                },
                onToggleMergeSameAnime = {
                    val enabled = !mergeSameAnimeEnabled
                    scope.launch {
                        applyScanPreferenceSnapshot(settingsPreferenceActions.setMergeSameAnimeEnabled(enabled))
                    }
                },
                onSaveConfig = {
                    scope.launch {
                        saveCloudRssConfig()
                    }
                },
                onSaveCredentials = {
                    val result = cloudRssActions.saveCredentials(cloudToken, cloudPassword)
                    cloudToken = result.token.orEmpty()
                    cloudPassword = result.password.orEmpty()
                    cloudRssStatus = result.status
                },
                onLoginCloudDrive = {
                    scope.launch {
                        when (
                            val result = cloudRssActions.loginCloudDrive(
                                endpointUrl = cloudEndpointUrl,
                                username = cloudUsername,
                                password = cloudPassword,
                                onStarted = { status -> cloudRssStatus = status },
                            )
                        ) {
                            is CloudDriveActionResult.Success -> {
                                cloudToken = result.token.orEmpty()
                                cloudRssStatus = result.status
                            }
                            is CloudDriveActionResult.Invalid -> {
                                cloudRssStatus = result.status
                            }
                            is CloudDriveActionResult.Failed -> {
                                cloudRssStatus = result.status
                            }
                        }
                    }
                },
                onVerifyApiToken = {
                    scope.launch {
                        when (
                            val result = cloudRssActions.verifyCloudDriveApiToken(
                                endpointUrl = cloudEndpointUrl,
                                token = cloudToken,
                                onStarted = { status -> cloudRssStatus = status },
                            )
                        ) {
                            is CloudDriveActionResult.Success -> {
                                cloudToken = result.token.orEmpty()
                                cloudRssStatus = result.status
                            }
                            is CloudDriveActionResult.Invalid -> {
                                cloudRssStatus = result.status
                            }
                            is CloudDriveActionResult.Failed -> {
                                cloudRssStatus = result.status
                            }
                        }
                    }
                },
                onClearCredentials = {
                    val result = cloudRssActions.clearCredentials()
                    cloudToken = ""
                    cloudPassword = ""
                    cloudRssStatus = result.status
                },
                onRunSync = {
                    scope.launch {
                        cloudRssActions.runCloudDriveOnceWithCallbacks(
                            onStarted = { status -> cloudRssStatus = status },
                            onCompleted = { completed ->
                                cloudRssStatus = completed.status
                                rescanLinkedCloudSource(completed.status)?.let { scanMessage ->
                                    cloudRssStatus = "${completed.status} $scanMessage"
                                }
                            },
                            onFailed = { failed ->
                                cloudRssStatus = failed.status
                            },
                        )
                    }
                },
                onStartScheduler = {
                    if (!cloudEnabled) {
                        cloudRssStatus = cloudRssSchedulerDisabledStatus()
                    } else {
                        cloudRssStatus = cloudRssSchedulerStartStatus(cloudRssScheduler.start())
                    }
                },
                onStopScheduler = {
                    cloudRssScheduler.stop()
                    cloudRssStatus = cloudRssSchedulerStoppedStatus()
                },
                onUseActiveScanSource = {
                    val sourceId = activeSourceId
                    val sourceInfo = savedSources.firstOrNull { it.id == sourceId }
                        ?: activeSource?.info?.takeIf { it.id == sourceId }
                    if (sourceId == null || sourceInfo == null) {
                        cloudRssStatus = cloudRssScanSourceRequiredStatus()
                    } else {
                        cloudLinkedSourceId = sourceId
                        cloudRssStatus = cloudRssLinkedScanSourceStatus(sourceInfo.sourcePickerTitle())
                    }
                },
                onClearScanSource = {
                    cloudLinkedSourceId = null
                    cloudRssStatus = cloudRssScanSourceClearedStatus()
                },
                onSaveSubscription = {
                    scope.launch {
                        when (
                            val result = cloudRssActions.saveRssSubscription(
                                name = rssName,
                                url = rssUrl,
                                filterRegex = rssFilter,
                                enabled = rssEnabled,
                                selectedSubscription = selectedRssSubscription,
                            )
                        ) {
                            is RssSubscriptionActionResult.Saved -> {
                                cloudRssStatus = result.status
                                refreshRssSubscriptions()
                            }
                            is RssSubscriptionActionResult.Invalid -> {
                                cloudRssStatus = result.status
                            }
                            is RssSubscriptionActionResult.Failed -> {
                                cloudRssStatus = result.status
                            }
                            is RssSubscriptionActionResult.Deleted -> Unit
                        }
                    }
                },
                onSubscriptionSelected = { subscription ->
                    selectedRssSubscription = subscription
                    rssName = subscription.name
                    rssUrl = subscription.url
                    rssFilter = subscription.filterRegex.orEmpty()
                    rssEnabled = subscription.enabled
                    cloudRssStatus = rssSubscriptionSelectedStatus(subscription.name)
                },
                onDeleteSubscription = {
                    scope.launch {
                        val subscription = selectedRssSubscription
                        if (subscription == null) {
                            cloudRssStatus = rssSubscriptionRequiredStatus()
                            return@launch
                        }
                        when (val result = cloudRssActions.deleteRssSubscription(subscription.id)) {
                            is RssSubscriptionActionResult.Deleted -> {
                                selectedRssSubscription = null
                                rssName = ""
                                rssUrl = ""
                                rssFilter = ""
                                rssEnabled = true
                                cloudRssStatus = result.status
                                refreshRssSubscriptions()
                            }
                            is RssSubscriptionActionResult.Failed -> {
                                cloudRssStatus = result.status
                            }
                            is RssSubscriptionActionResult.Invalid -> Unit
                            is RssSubscriptionActionResult.Saved -> Unit
                        }
                    }
                },
                onSaveBangumiToken = {
                    val result = bangumiCredentialActions.saveToken(bangumiTokenInput)
                    if (result.shouldClearInput) {
                        bangumiTokenInput = ""
                    }
                    bangumiTokenConfigured = result.configured
                    bangumiStatus = result.status
                },
                onClearBangumiToken = {
                    val result = bangumiCredentialActions.clearToken()
                    bangumiTokenInput = ""
                    bangumiTokenConfigured = false
                    bangumiStatus = result.status
                },
                sources = savedSources,
                activeSourceLabel = settingsActiveSourceLabel(activeSource?.info),
                indexedItemCount = indexedEntries.size,
                recentCount = recentProgress.size,
                selectedMediaTitle = selectedIndexEntry?.detailTitle()
                    ?: selectedRecentProgress?.displayName
                    ?: metadataNoSelectedEntryLabel(),
                playbackSummary = playbackRifeStateLabel(rifeEnabled, rifeBackend.name),
                metadataSummary = selectedIndexEntry?.let { entry ->
                    metadataMatchSummaryLabel(entry.metadataTitle)
                } ?: metadataNoSelectedEntryLabel(),
                libraryStatus = libraryStatus,
                webControlEnabled = webControlEnabled,
                webUiUrls = webUiUrls,
                webControlAccessToken = webControlAccessToken,
                onToggleWebControl = {
                    applyWebControlSnapshot(webControlActions.setEnabled(!webControlEnabled))
                },
                onRotateWebControlToken = {
                    applyWebControlSnapshot(webControlActions.rotateAccessToken())
                },
                onRefreshWebUiUrls = ::refreshDesktopWebUiUrls,
                onOpenLibrary = { selectedDesktopSection = MiruPlayRouteSurface.library },
                onOpenPlayer = { selectedDesktopSection = MiruPlayRouteSurface.player },
                onOpenDetails = { selectedDesktopSection = MiruPlayRouteSurface.details },
                onScanActiveSource = {
                    scope.launch {
                        scanCurrentSource { libraryStatus = it }
                    }
                },
                logUploadEnabled = logUploadSnapshot.enabled,
                onLogUploadEnabledChange = { enabled ->
                    logUploadSnapshot = logUploadSnapshot.copy(enabled = enabled)
                },
                logUploadEndpoint = logUploadSnapshot.endpoint,
                onLogUploadEndpointChange = { endpoint ->
                    logUploadSnapshot = logUploadSnapshot.copy(endpoint = endpoint)
                },
                logUploadStreamName = logUploadSnapshot.streamName,
                onLogUploadStreamNameChange = { streamName ->
                    logUploadSnapshot = logUploadSnapshot.copy(streamName = streamName)
                },
                logUploadToken = logUploadTokenInput,
                onLogUploadTokenChange = { token ->
                    logUploadTokenInput = token
                },
                logUploadTokenConfigured = logUploadSnapshot.tokenConfigured,
                onSaveLogUploadConfig = {
                    scope.launch {
                        logUploadSnapshot = logUploadActions.saveConfig(
                            enabled = logUploadSnapshot.enabled,
                            endpoint = logUploadSnapshot.endpoint,
                            streamName = logUploadSnapshot.streamName,
                        )
                        logUploadAutoScheduler.syncWithConfig(logUploadSnapshot.toConfig())
                    }
                },
                onSaveLogUploadToken = {
                    scope.launch {
                        val token = logUploadTokenInput.trim()
                        if (token.isNotEmpty()) {
                            logUploadSnapshot = logUploadActions.saveToken(token)
                            logUploadTokenInput = ""
                        }
                    }
                },
                onClearLogUploadToken = {
                    scope.launch {
                        logUploadSnapshot = logUploadActions.clearToken()
                        logUploadTokenInput = ""
                    }
                },
                onRunLogUploadNow = {
                    scope.launch {
                        if (logUploadTokenInput.isNotBlank()) {
                            logUploadSnapshot = logUploadActions.saveToken(logUploadTokenInput.trim())
                            logUploadTokenInput = ""
                        }
                        logUploadSnapshot = logUploadActions.runNow()
                    }
                },
                canRunLogUploadNow = logUploadSnapshot.canRunNow(logUploadTokenInput),
                logUploadStatusMessage = settingsDesktopLogUploadStatusMessage(
                    pendingCount = logUploadSnapshot.pendingCount,
                    isUploading = logUploadSnapshot.isUploading,
                    tokenConfigured = logUploadSnapshot.tokenConfigured,
                    lastUploadAt = logUploadSnapshot.lastUploadAt,
                    lastUploadStatus = logUploadSnapshot.lastUploadStatus,
                ),
            )
            }
            MiruPlayRouteSurface.player -> {
                PlaybackPanel(
                    mediaPath = mediaPath,
                    onMediaPathChange = { mediaPath = it },
                    subtitlePath = subtitlePath,
                    onSubtitlePathChange = { subtitlePath = it },
                    startSeconds = startSeconds,
                    onStartSecondsChange = { startSeconds = it },
                    fullscreen = fullscreen,
                    onFullscreenChange = { fullscreen = it },
                    keepOpen = keepOpen,
                    onKeepOpenChange = { keepOpen = it },
                    rifeEnabled = rifeEnabled,
                    onRifeEnabledChange = { rifeEnabled = it },
                    rifeBackend = rifeBackend,
                    onRifeBackendChange = { rifeBackend = it },
                    playbackEndAction = playbackEndAction,
                    onPlaybackEndActionChange = { action ->
                        scope.launch {
                            playbackEndAction = settingsPreferenceActions.setPlaybackEndAction(action)
                        }
                    },
                    playbackSpeed = playbackSpeed,
                    onPlaybackSpeedChange = { speed ->
                        scope.launch {
                            applyDesktopPlaybackSpeed(speed)
                        }
                    },
                    isPlayerActive = player != null,
                    playbackPositionMs = playbackPositionMs,
                    playbackDurationMs = playbackDurationMs,
                    launchStatus = launchStatus,
                    onBackToDetails = { selectedDesktopSection = MiruPlayRouteSurface.details },
                    onLaunch = {
                        scope.launch {
                            runCatching {
                                launchDesktopPlayback()
                            }.onFailure { error ->
                                launchStatus = playbackLauncher.launchFailureStatus(error)
                            }
                        }
                    },
                    onTogglePause = {
                        scope.launch {
                            val activePlayer = player
                            if (activePlayer == null) {
                                launchStatus = mpvNoActiveProcessStatus()
                                return@launch
                            }
                            when (val result = activePlayer.togglePause()) {
                                is Result.Success -> {
                                    activePlaybackSession?.togglePaused()
                                    playbackPositionMs = activePlaybackSession?.currentPositionMs() ?: playbackPositionMs
                                    launchStatus = mpvPauseToggledStatus()
                                }
                                is Result.Error -> launchStatus = result.error.toUserMessage()
                            }
                        }
                    },
                    onResume = {
                        scope.launch {
                            val activePlayer = player
                            if (activePlayer == null) {
                                launchStatus = mpvNoActiveProcessStatus()
                                return@launch
                            }
                            when (val result = activePlayer.setPaused(false)) {
                                is Result.Success -> {
                                    activePlaybackSession?.setPaused(false)
                                    playbackPositionMs = activePlaybackSession?.currentPositionMs() ?: playbackPositionMs
                                    launchStatus = mpvResumedStatus()
                                }
                                is Result.Error -> launchStatus = result.error.toUserMessage()
                            }
                        }
                    },
                    onPause = {
                        scope.launch {
                            val activePlayer = player
                            if (activePlayer == null) {
                                launchStatus = mpvNoActiveProcessStatus()
                                return@launch
                            }
                            when (val result = activePlayer.setPaused(true)) {
                                is Result.Success -> {
                                    activePlaybackSession?.setPaused(true)
                                    playbackPositionMs = activePlaybackSession?.currentPositionMs() ?: playbackPositionMs
                                    launchStatus = mpvPausedStatus()
                                }
                                is Result.Error -> launchStatus = result.error.toUserMessage()
                            }
                        }
                    },
                    onSeekBack = {
                        scope.launch {
                            val activePlayer = player
                            if (activePlayer == null) {
                                launchStatus = mpvNoActiveProcessStatus()
                                return@launch
                            }
                            val seekSeconds = PLAYBACK_SEEK_BACK_SECONDS.toDouble()
                            when (val result = activePlayer.seekBy(-seekSeconds)) {
                                is Result.Success -> {
                                    activePlaybackSession?.seekBy(-seekSeconds)
                                    playbackPositionMs = activePlaybackSession?.currentPositionMs() ?: playbackPositionMs
                                    launchStatus = mpvSeekBackStatus(seconds = PLAYBACK_SEEK_BACK_SECONDS)
                                }
                                is Result.Error -> launchStatus = result.error.toUserMessage()
                            }
                        }
                    },
                    onSeekForward = {
                        scope.launch {
                            val activePlayer = player
                            if (activePlayer == null) {
                                launchStatus = mpvNoActiveProcessStatus()
                                return@launch
                            }
                            val seekSeconds = PLAYBACK_SEEK_FORWARD_SECONDS.toDouble()
                            when (val result = activePlayer.seekBy(seekSeconds)) {
                                is Result.Success -> {
                                    activePlaybackSession?.seekBy(seekSeconds)
                                    playbackPositionMs = activePlaybackSession?.currentPositionMs() ?: playbackPositionMs
                                    launchStatus = mpvSeekForwardStatus(seconds = PLAYBACK_SEEK_FORWARD_SECONDS)
                                }
                                is Result.Error -> launchStatus = result.error.toUserMessage()
                            }
                        }
                    },
                    onStop = {
                        scope.launch {
                            stopDesktopPlayback(
                                player = player,
                                session = activePlaybackSession,
                                saveProgress = { episodeId, positionMs, lastWatched, incrementPlayCount ->
                                    savePlaybackProgress(episodeId, positionMs, lastWatched, incrementPlayCount)
                                },
                            )
                            player = null
                            activePlaybackSession = null
                            resetDesktopPlaybackTimeline()
                            clearWebControlPlaybackSource()
                            refreshRecentProgress()
                            launchStatus = mpvStoppedStatus()
                        }
                    },
                    requestedSettingsFocusVersion = playerSettingsFocusVersion,
                    requestedSettingsFocusTarget = PlaybackSettingFocusTarget.RifeBackend,
                    onFocusNextPanel = {
                        playerRuntimeFocusVersion += 1
                        true
                    },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RuntimePanel(
                        mpvPath = mpvPath,
                        onMpvPathChange = { mpvPath = it },
                        configDir = configDir,
                        onConfigDirChange = { configDir = it },
                        status = status,
                        onCheckRuntime = { status = mpvRuntimeStatusFromInputs(mpvPath, configDir) },
                        modifier = Modifier.weight(0.42f),
                        focusVersion = playerRuntimeFocusVersion,
                        onFocusPreviousPanel = {
                            playerSettingsFocusVersion += 1
                            true
                        },
                    )
                    CommandPanel(
                        commandPreview = commandPreview,
                        launchStatus = launchStatus,
                        modifier = Modifier.weight(0.58f),
                    )
                }
                }
            }
        }
    }
}
