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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.miruplay.tv.clouddrive.GrpcCloudDriveClient
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.buildWebControlAccessUrls
import com.miruplay.tv.design.MiruPlayPalette
import com.miruplay.tv.design.MiruPlayRouteSurface
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopPlaybackBridge
import com.miruplay.tv.mediasource.desktop.desktopSourceFromInfo
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.PLAYBACK_SEEK_BACK_SECONDS
import com.miruplay.tv.model.PLAYBACK_SEEK_FORWARD_SECONDS
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.PlaybackProgressSession
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackTimingConventions
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.cloudDriveCredentialsClearedStatus
import com.miruplay.tv.model.cloudDriveCredentialsSavedStatus
import com.miruplay.tv.model.cloudRssScheduledSyncCompleteStatus
import com.miruplay.tv.model.cloudRssConfigSavedStatus
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
import com.miruplay.tv.model.detailBangumiSyncCompleteMessage
import com.miruplay.tv.model.detailBangumiSyncStartedMessage
import com.miruplay.tv.model.desktopWindowTitleLabel
import com.miruplay.tv.model.loadedPlaybackStatus
import com.miruplay.tv.model.metadataBangumiTokenClearedMessage
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
import com.miruplay.tv.model.resumeStartSecondsText
import com.miruplay.tv.model.retainedSelectionInProgressRecords
import com.miruplay.tv.model.retainedSelectionInRssSubscriptions
import com.miruplay.tv.model.rssSubscriptionRequiredStatus
import com.miruplay.tv.model.rssSubscriptionSelectedStatus
import com.miruplay.tv.model.rssSubscriptionsLoadedStatus
import com.miruplay.tv.model.rssSubscriptionsLoadFailedStatus
import com.miruplay.tv.model.rssSubscriptionsRefreshFailedStatus
import com.miruplay.tv.model.rssSubscriptionsShowingStatus
import com.miruplay.tv.model.saveBangumiTokenFormResult
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
import com.miruplay.tv.player.mpv.mpvStoppedStatus
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MetadataBatchMatch
import com.miruplay.tv.repository.MetadataBatchPlanner
import com.miruplay.tv.repository.MetadataBatchPlan
import com.miruplay.tv.repository.appliedStatus
import com.miruplay.tv.repository.applyMetadataBatchPlan
import com.miruplay.tv.repository.buildNextPlaybackSource
import com.miruplay.tv.repository.clearExternalMetadata
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
import com.miruplay.tv.repository.metadataAppliedStatus
import com.miruplay.tv.repository.metadataApplyEntryRequiredStatus
import com.miruplay.tv.repository.metadataBatchResultRequiredStatus
import com.miruplay.tv.repository.metadataBatchSearchingStatus
import com.miruplay.tv.repository.metadataClearEntryRequiredStatus
import com.miruplay.tv.repository.metadataClearedStatus
import com.miruplay.tv.repository.metadataIndexedVideoRequiredStatus
import com.miruplay.tv.repository.metadataInitialStatus
import com.miruplay.tv.repository.metadataQuery
import com.miruplay.tv.repository.metadataQueryRequiredStatus
import com.miruplay.tv.repository.metadataQuerySetFromIndexStatus
import com.miruplay.tv.repository.metadataReviewNoMatchStatus
import com.miruplay.tv.repository.metadataSearchResultStatus
import com.miruplay.tv.repository.metadataSearchSelectionRequiredStatus
import com.miruplay.tv.repository.metadataSearchStartedStatus
import com.miruplay.tv.repository.metadataSourceRequiredStatus
import com.miruplay.tv.repository.noMetadataBatchEntriesStatus
import com.miruplay.tv.repository.noMetadataBatchPreviewStatus
import com.miruplay.tv.repository.noMetadataBatchUndoStatus
import com.miruplay.tv.repository.openRemoteSourceBeforeBrowsingStatus
import com.miruplay.tv.repository.openSourceBeforeClearingIndexStatus
import com.miruplay.tv.repository.openSourceBeforeScanningStatus
import com.miruplay.tv.repository.openSourceBeforeSearchingStatus
import com.miruplay.tv.repository.restoreMetadataBatchUndo
import com.miruplay.tv.repository.restoredStatus
import com.miruplay.tv.repository.retainedSelectionInMediaIndex
import com.miruplay.tv.repository.scanPreferencesIntervalOptionsHours
import com.miruplay.tv.repository.reviewAcceptedStatus
import com.miruplay.tv.repository.shouldAutoScan
import com.miruplay.tv.repository.reviewConflictStatus
import com.miruplay.tv.repository.scanningStatus
import com.miruplay.tv.repository.selectedCandidateStatus
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
import com.miruplay.tv.repository.toScanIntervalHours
import com.miruplay.tv.repository.toScanIntervalMillis
import com.miruplay.tv.repository.upsertById
import com.miruplay.tv.repository.updatedSelectionAfterReplacingByMediaKeys
import com.miruplay.tv.repository.webDavUrlRequiredStatus
import com.miruplay.tv.repository.withExternalMetadata
import com.miruplay.tv.repository.replaceByMediaKey
import com.miruplay.tv.repository.replaceByMediaKeys
import com.miruplay.tv.repository.replaceMatch
import com.miruplay.tv.repository.withSelectedCandidate
import com.miruplay.tv.scraper.searchPreferredResults
import com.miruplay.tv.scraper.desktop.DesktopBangumiScraper
import com.miruplay.tv.sync.BangumiMetadataRefreshCore
import com.miruplay.tv.sync.BangumiSyncCore
import com.miruplay.tv.sync.bangumiMetadataCacheId
import com.miruplay.tv.sync.rss.CloudDriveActionResult
import com.miruplay.tv.sync.rss.CloudDriveRunActionResult
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
private const val COMPOSE_BATCH_BANGUMI_QUERY_LIMIT = 20
private const val BANGUMI_METADATA_SOURCE_NAME = "Bangumi"
private const val DESKTOP_PLAYBACK_MEDIA_SOURCE_ID = "desktop-compose"
private const val PLAYBACK_EOF_POLL_INTERVAL_MS = 1_000L
private const val PLAYBACK_PROGRESS_POLL_INTERVAL_MS = 10_000L
internal typealias DesktopSection = MiruPlayRouteSurface.Section

private const val DESKTOP_START_SECTION_ENV = "MIRUPLAY_DESKTOP_START_SECTION"
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

@Composable
internal fun MiruPlayDesktopComposeApp(
    onPlayerFullscreenActiveChange: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val repositories = remember { DesktopRepositories.fileBacked() }
    val playbackBridge = remember { DesktopPlaybackBridge() }
    val bangumiScraper = remember { DesktopBangumiScraper { repositories.credentials.bangumiAccessToken } }
    val bangumiMetadataRefreshCore = remember(bangumiScraper, repositories) {
        BangumiMetadataRefreshCore(
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
    val focusManager = LocalFocusManager.current
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
    val cloudDirectoryActions = remember { CloudDriveDirectoryBrowserCoordinator(cloudDriveClient) }
    val cloudRssScheduler = remember { DesktopCloudDriveRssScheduler(cloudRssEngine, scope) }
    val cloudRssSchedulerState by cloudRssScheduler.state.collectAsState()
    val defaultMpvLayout = remember { MpvRuntimeDiscovery.defaultLayout() }
    val playbackLauncher = remember(playbackBridge) { DesktopPlaybackLauncher(playbackBridge) }
    var selectedDesktopSection by remember { mutableStateOf(desktopInitialSectionFromEnvironment()) }
    var player by remember { mutableStateOf<MpvProcessPlayer?>(null) }
    var activePlaybackSession by remember { mutableStateOf<PlaybackProgressSession?>(null) }
    var webControlPlaybackSource by remember { mutableStateOf<DesktopMediaSource?>(null) }
    var mpvPath by remember { mutableStateOf(defaultMpvLayout.executable.toString()) }
    var configDir by remember { mutableStateOf(defaultMpvLayout.configDirectory.toString()) }
    var libraryRoot by remember { mutableStateOf("") }
    var savedSources by remember { mutableStateOf(emptyList<MediaSourceInfo>()) }
    var activeSourceId by remember { mutableStateOf<Long?>(null) }
    var activeSource by remember { mutableStateOf<DesktopMediaSource?>(null) }
    var activeLocalSource by remember { mutableStateOf<DesktopLocalMediaSource?>(null) }
    var indexQuery by remember { mutableStateOf("") }
    var indexedEntries by remember { mutableStateOf(emptyList<MediaIndexEntry>()) }
    var selectedIndexEntry by remember { mutableStateOf<MediaIndexEntry?>(null) }
    var libraryStatus by remember { mutableStateOf(localLibraryInitialStatus()) }
    var webDavUrl by remember { mutableStateOf("") }
    var webDavUsername by remember { mutableStateOf("") }
    var webDavPassword by remember { mutableStateOf("") }
    var smbUrl by remember { mutableStateOf("") }
    var smbDomain by remember { mutableStateOf("") }
    var smbUsername by remember { mutableStateOf("") }
    var smbPassword by remember { mutableStateOf("") }
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
    var recentProgress by remember { mutableStateOf(emptyList<ProgressRecord>()) }
    var selectedRecentProgress by remember { mutableStateOf<ProgressRecord?>(null) }
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
    var mediaPath by remember { mutableStateOf("") }
    var subtitlePath by remember { mutableStateOf("") }
    var startSeconds by remember { mutableStateOf("0") }
    var fullscreen by remember { mutableStateOf(false) }
    var keepOpen by remember { mutableStateOf(false) }
    var playbackEndAction by remember { mutableStateOf(PlaybackEndAction.RETURN_TO_DETAIL) }
    var rifeEnabled by remember { mutableStateOf(DEFAULT_DESKTOP_RIFE_ENABLED) }
    var rifeBackend by remember { mutableStateOf(RifeBackend.NVIDIA) }
    var status by remember { mutableStateOf(mpvRuntimeStatusFromInputs(mpvPath, configDir)) }
    var launchStatus by remember { mutableStateOf(mpvIdleStatus()) }
    val webControlPlaybackHandlers = remember { DesktopWebControlPlaybackHandlers() }
    val desktopWebControlService = remember(repositories) {
        DesktopWebControlService(
            repositories = repositories,
            cloudDriveClient = cloudDriveClient,
            cloudRssEngine = cloudRssEngine,
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
        }
    }

    suspend fun clearWebControlPlaybackSource() {
        val source = webControlPlaybackSource
        webControlPlaybackSource = null
        if (source != null && source !== activeSource && source !== activeLocalSource) {
            source.close()
        }
    }

    fun syncDesktopWebControlServer() {
        if (repositories.webControlAccess.webControlEnabled) {
            desktopWebControlServer.startIfNeeded()
        } else {
            desktopWebControlServer.stopIfRunning()
        }
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
                applySourceFormState(sources.data.desktopSourceFormState())
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
        when (val recents = repositories.progress.getContinueWatching(limit = 12)) {
            is Result.Success -> {
                recentProgress = recents.data
                recentStatus = recentPlaybackLoadedStatus(recents.data)
            }
            is Result.Error -> recentStatus = recents.error.toUserMessage()
        }
        playbackEndAction = repositories.playbackPreferences.getEndAction()
        val scanPreferences = repositories.scanPreferences.getPreferences()
        autoScanEnabled = scanPreferences.autoScanEnabled
        autoScanIntervalHours = scanPreferences.autoScanIntervalMs.toScanIntervalHours()
        lastScanAt = scanPreferences.lastScanAt
        mergeSameAnimeEnabled = scanPreferences.mergeSameAnimeEnabled
        when (val config = repositories.cloudDriveAutomation.getConfig()) {
            is Result.Success -> {
                cloudEndpointUrl = config.data.endpointUrl
                cloudUsername = config.data.username
                cloudLinkedSourceId = config.data.webDavSourceId
                cloudInboxPath = config.data.inboxPath
                cloudLibraryPath = config.data.libraryPath
                cloudIntervalMinutes = config.data.intervalMinutes.toString()
                cloudEnabled = config.data.enabled
                rssProxyEnabled = config.data.rssProxyEnabled
                rssProxyHost = config.data.rssProxyHost
                rssProxyPort = config.data.rssProxyPort.toString()
            }
            is Result.Error -> cloudRssStatus = config.error.toUserMessage()
        }
        cloudToken = repositories.credentials.cloudDriveToken.orEmpty()
        cloudPassword = repositories.credentials.cloudDrivePassword.orEmpty()
        bangumiTokenConfigured = !repositories.credentials.bangumiAccessToken.isNullOrBlank()
        webControlEnabled = repositories.webControlAccess.webControlEnabled
        webControlAccessToken = repositories.webControlAccess.accessToken
        webUiUrls = if (webControlEnabled) {
            buildWebControlAccessUrls(webControlAccessToken)
        } else {
            emptyList()
        }
        syncDesktopWebControlServer()
        runCatching {
            repositories.cloudDriveAutomation.observeSubscriptions().first()
        }.onSuccess { subscriptions ->
            rssSubscriptions = subscriptions
            cloudRssStatus = rssSubscriptionsLoadedStatus(subscriptions.size)
        }.onFailure { error ->
            cloudRssStatus = rssSubscriptionsLoadFailedStatus(error.message)
        }
    }

    suspend fun refreshRecentProgress() {
        when (val recents = repositories.progress.getContinueWatching(limit = 12)) {
            is Result.Success -> {
                recentProgress = recents.data
                selectedRecentProgress = selectedRecentProgress.retainedSelectionInProgressRecords(recents.data)
                recentStatus = recentPlaybackShowingStatus(recents.data)
            }
            is Result.Error -> recentStatus = recents.error.toUserMessage()
        }
    }

    fun refreshDesktopWebUiUrls() {
        webControlEnabled = repositories.webControlAccess.webControlEnabled
        webControlAccessToken = repositories.webControlAccess.accessToken
        webUiUrls = if (webControlEnabled) {
            buildWebControlAccessUrls(webControlAccessToken)
        } else {
            emptyList()
        }
        syncDesktopWebControlServer()
    }

    suspend fun updateSelectedMetadataCache(
        entry: MediaIndexEntry,
        match: ScraperResult,
    ): Result<Unit> =
        bangumiMetadataRefreshCore.cacheMatchedIndexMetadata(
            entry = entry,
            relatedEntries = detailEpisodes,
            match = match,
        ).map { Unit }

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
                result
            }
            is Result.Error -> {
                launchStatus = result.error.toUserMessage()
                result
            }
        }
    }

    SideEffect {
        webControlPlaybackHandlers.playEpisode = { request, episode ->
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
        webControlPlaybackHandlers.playbackCommand = { command ->
            val statusDto = desktopWebControlPlaybackCommand(
                request = command,
                player = player,
                session = activePlaybackSession,
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
                    clearWebControlPlaybackSource()
                    refreshRecentProgress()
                },
            )
            launchStatus = webControlPlaybackCommandStatus(command)
            if (!statusDto.isPlaying && command.command.equals("stop", ignoreCase = true)) {
                selectedDesktopSection = MiruPlayRouteSurface.details
            }
            statusDto
        }
    }

    suspend fun nextDesktopPlaybackSource(currentEpisodeId: String): PlaybackSource? {
        return buildNextPlaybackSource(
            currentEpisodeId = currentEpisodeId,
            loadCurrentEpisode = { episodeId -> repositories.metadata.getCachedEpisode(episodeId).getOrNull() },
            loadEpisodes = { animeId -> repositories.metadata.getCachedEpisodes(animeId).getOrNull().orEmpty() },
            loadProgress = { episodeId -> repositories.progress.getProgress(episodeId).getOrNull() },
        )
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
                                    clearWebControlPlaybackSource()
                                    launchStatus = nextLaunch.error.toUserMessage()
                                    selectedDesktopSection = MiruPlayRouteSurface.details
                                }
                            }
                        } else {
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

    suspend fun scanCurrentSource(updateStatus: (String) -> Unit) {
        val sourceId = activeSourceId
        val source = activeSource ?: activeLocalSource
        if (sourceId == null || source == null) {
            updateStatus(openSourceBeforeScanningStatus())
            return
        }
        updateStatus(source.info.scanningStatus())
        when (val scan = scanAndIndexDesktopSource(source.info.copy(id = sourceId), repositories.index)) {
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
        val sourceId = cloudLinkedSourceId ?: return null
        val sourceInfo = savedSources.firstOrNull { it.id == sourceId }
            ?: when (val sources = repositories.mediaSources.getSources()) {
                is Result.Success -> {
                    savedSources = sources.data
                    sources.data.firstOrNull { it.id == sourceId }
                }
                is Result.Error -> {
                    cloudRssStatus = sources.error.toUserMessage()
                    return null
                }
            }
        if (sourceInfo == null) {
            cloudRssStatus = cloudRssScanSourceMissingStatus()
            return null
        }

        cloudRssStatus = cloudRssRescanStartedStatus(reason, sourceInfo.sourcePickerTitle())
        return when (val rescan = rescanCloudRssLinkedSource(sourceInfo, reason, repositories.index)) {
            is Result.Success -> {
                val result = rescan.data
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
            is Result.Error -> {
                val message = rescan.error.toUserMessage()
                cloudRssStatus = message
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
                        when (val result = openDesktopSource(repositories.mediaSources, sourceInfo)) {
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
                        when (val result = repositories.mediaSources.removeSource(sourceId)) {
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
                        when (val result = openDesktopSource(repositories.mediaSources, sourceInfo)) {
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
                        when (val result = openDesktopSource(repositories.mediaSources, sourceInfo)) {
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
                            when (
                                detailHeroDownTarget(
                                    hasRelatedEpisodes = detailEpisodes.isNotEmpty(),
                                    hasRecentPlayback = recentProgress.isNotEmpty(),
                                )
                            ) {
                                DesktopDetailDownTarget.EpisodeList -> {
                                    detailEpisodeFocusVersion += 1
                                    true
                                }
                                DesktopDetailDownTarget.RecentPlayback -> {
                                    recentPlaybackFocusVersion += 1
                                    true
                                }
                                DesktopDetailDownTarget.BangumiMetadata -> {
                                    bangumiFocusVersion += 1
                                    true
                                }
                            }
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
                        recentRecords = recentProgress,
                        focusVersion = detailEpisodeFocusVersion,
                        onFocusPreviousPanel = {
                            detailHeroFocusVersion += 1
                            true
                        },
                        onFocusNextPanel = {
                            if (recentProgress.isNotEmpty()) {
                                recentPlaybackFocusVersion += 1
                            } else {
                                bangumiFocusVersion += 1
                            }
                            true
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
                        val query = bangumiQuery.trim().ifBlank {
                            selectedIndexEntry?.metadataQuery().orEmpty()
                        }
                        if (query.isBlank()) {
                            bangumiStatus = metadataQueryRequiredStatus(BANGUMI_METADATA_SOURCE_NAME)
                            return@launch
                        }
                        bangumiQuery = query
                        bangumiStatus = metadataSearchStartedStatus(query, BANGUMI_METADATA_SOURCE_NAME)
                        when (val result = bangumiScraper.searchPreferredResults(
                            query = query,
                            candidates = listOfNotNull(
                                selectedIndexEntry?.metadataTitle?.takeIf { it.isNotBlank() },
                                selectedIndexEntry?.metadataQuery(),
                                selectedIndexEntry?.metadataId?.takeIf { it.isNotBlank() },
                            ).distinct(),
                        )) {
                            is Result.Success -> {
                                bangumiResults = result.data
                                selectedBangumiResult = result.data.firstOrNull()
                                bangumiStatus = metadataSearchResultStatus(query, result.data.size, BANGUMI_METADATA_SOURCE_NAME)
                            }
                            is Result.Error -> bangumiStatus = result.error.toUserMessage()
                        }
                    }
                },
                onBatchPreview = {
                    scope.launch {
                        val sourceId = activeSourceId
                        if (sourceId == null) {
                            bangumiStatus = metadataSourceRequiredStatus()
                            return@launch
                        }
                        when (val entriesResult = repositories.index.queryIndex(sourceId, "")) {
                            is Result.Success -> {
                                val entries = entriesResult.data
                                val queryCount = MetadataBatchPlanner.previewQueryCount(
                                    entries = entries,
                                    queryLimit = COMPOSE_BATCH_BANGUMI_QUERY_LIMIT,
                                )
                                if (queryCount == 0) {
                                    bangumiBatchMatches = emptyList()
                                    selectedBangumiBatchMatch = null
                                    bangumiBatchPlan = null
                                    bangumiStatus = noMetadataBatchEntriesStatus("Bangumi")
                                    return@launch
                                }

                                bangumiStatus = metadataBatchSearchingStatus(queryCount, "Bangumi")
                                val preview = MetadataBatchPlanner.previewFor(
                                    entries = entries,
                                    queryLimit = COMPOSE_BATCH_BANGUMI_QUERY_LIMIT,
                                    searchCandidates = { query, candidates ->
                                        bangumiScraper.searchPreferredResults(
                                            query = query,
                                            candidates = candidates,
                                        ).getOrNull().orEmpty()
                                    },
                                )
                                bangumiBatchMatches = preview.matches
                                bangumiBatchPlan = preview.plan
                                selectedBangumiBatchMatch = preview.selectedMatch
                                bangumiStatus = preview.summaryStatus()
                            }
                            is Result.Error -> bangumiStatus = entriesResult.error.toUserMessage()
                        }
                    }
                },
                onBatchApply = {
                    scope.launch {
                        val sourceId = activeSourceId
                        if (sourceId == null) {
                            bangumiStatus = metadataSourceRequiredStatus()
                            return@launch
                        }
                        if (bangumiBatchMatches.isEmpty()) {
                            bangumiStatus = noMetadataBatchPreviewStatus()
                            return@launch
                        }
                        when (val entriesResult = repositories.index.queryIndex(sourceId, "")) {
                            is Result.Success -> {
                                val entries = entriesResult.data.mediaFilesOnly()
                                val plan = MetadataBatchPlanner.planFor(entries, bangumiBatchMatches)
                                bangumiBatchPlan = plan
                                if (plan.readyUpdates.isEmpty()) {
                                    bangumiStatus = MetadataBatchPlanner.displayPlanSummary(plan)
                                    return@launch
                                }
                                val write = repositories.index.applyMetadataBatchPlan(sourceId, plan)
                                bangumiBatchRollback = write.rollbackEntries
                                indexedEntries = indexedEntries.replaceByMediaKeys(write.updatedEntries)
                                selectedIndexEntry = selectedIndexEntry.updatedSelectionAfterReplacingByMediaKeys(write.updatedEntries)
                                bangumiStatus = write.appliedStatus(plan.conflicts.size)
                            }
                            is Result.Error -> bangumiStatus = entriesResult.error.toUserMessage()
                        }
                    }
                },
                onBatchUndo = {
                    scope.launch {
                        val sourceId = activeSourceId
                        if (sourceId == null) {
                            bangumiStatus = metadataSourceRequiredStatus()
                            return@launch
                        }
                        when (val restore = repositories.index.restoreMetadataBatchUndo(sourceId, bangumiBatchRollback)) {
                            is Result.Success -> {
                                if (restore.data.rollbackEntries.isEmpty()) {
                                    bangumiStatus = noMetadataBatchUndoStatus()
                                    return@launch
                                }
                                indexedEntries = indexedEntries.replaceByMediaKeys(restore.data.rollbackEntries)
                                selectedIndexEntry = selectedIndexEntry.updatedSelectionAfterReplacingByMediaKeys(restore.data.rollbackEntries)
                                bangumiBatchRollback = emptyList()
                                bangumiStatus = restore.data.restoredStatus()
                            }
                            is Result.Error -> bangumiStatus = restore.error.toUserMessage()
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
                        var updatedMatch = match.withSelectedCandidate(candidate)
                        var updatedMatches = bangumiBatchMatches.replaceMatch(updatedMatch)
                        val sourceId = activeSourceId
                        if (sourceId != null) {
                            when (val entriesResult = repositories.index.queryIndex(sourceId, "")) {
                                is Result.Success -> {
                                    val selection = MetadataBatchPlanner.selectCandidate(
                                        entries = entriesResult.data,
                                        matches = bangumiBatchMatches,
                                        match = match,
                                        candidate = candidate,
                                    )
                                    updatedMatch = selection.updatedMatch
                                    updatedMatches = selection.updatedMatches
                                    bangumiBatchPlan = selection.plan
                                }
                                is Result.Error -> {
                                    bangumiStatus = entriesResult.error.toUserMessage()
                                    return@launch
                                }
                            }
                        }
                        bangumiBatchMatches = updatedMatches
                        selectedBangumiBatchMatch = updatedMatch
                        selectedBangumiResult = candidate
                        bangumiQuery = match.query
                        bangumiStatus = updatedMatch.selectedCandidateStatus()
                    }
                },
                onBatchAcceptReview = {
                    scope.launch {
                        val sourceId = activeSourceId
                        val match = selectedBangumiBatchMatch
                        val result = match?.result
                        if (sourceId == null) {
                            bangumiStatus = metadataSourceRequiredStatus()
                            return@launch
                        }
                        if (match == null || result == null) {
                            bangumiStatus = metadataBatchResultRequiredStatus(BANGUMI_METADATA_SOURCE_NAME)
                            return@launch
                        }
                        when (val entriesResult = repositories.index.queryIndex(sourceId, "")) {
                            is Result.Success -> {
                                val entries = entriesResult.data.mediaFilesOnly()
                                val reviewed = match.copy(result = result.copy(confidence = 1f))
                                val plan = MetadataBatchPlanner.planFor(entries, listOf(reviewed))
                                if (plan.conflicts.isNotEmpty()) {
                                    bangumiStatus = plan.reviewConflictStatus()
                                    return@launch
                                }
                                if (plan.readyUpdates.isEmpty()) {
                                    bangumiStatus = metadataReviewNoMatchStatus()
                                    return@launch
                                }
                                val write = repositories.index.applyMetadataBatchPlan(sourceId, plan)
                                bangumiBatchRollback = write.rollbackEntries
                                indexedEntries = indexedEntries.replaceByMediaKeys(write.updatedEntries)
                                selectedIndexEntry = selectedIndexEntry.updatedSelectionAfterReplacingByMediaKeys(write.updatedEntries)
                                bangumiStatus = write.reviewAcceptedStatus()
                            }
                            is Result.Error -> bangumiStatus = entriesResult.error.toUserMessage()
                        }
                    }
                },
                onResultSelected = { result ->
                    selectedBangumiResult = result
                    bangumiStatus = result.selectedMetadataStatus()
                },
                onApply = {
                    scope.launch {
                        val sourceId = activeSourceId
                        val entry = selectedIndexEntry
                        val bangumi = selectedBangumiResult ?: bangumiResults.firstOrNull()
                        if (sourceId == null) {
                            bangumiStatus = metadataSourceRequiredStatus()
                            return@launch
                        }
                        if (entry == null || entry.isDirectory) {
                            bangumiStatus = metadataApplyEntryRequiredStatus(BANGUMI_METADATA_SOURCE_NAME)
                            return@launch
                        }
                        if (bangumi == null) {
                            bangumiStatus = metadataSearchSelectionRequiredStatus(BANGUMI_METADATA_SOURCE_NAME)
                            return@launch
                        }
                        val updated = entry.withExternalMetadata(bangumi, sourceId = sourceId)
                        when (val result = repositories.index.upsertEntry(sourceId, updated)) {
                            is Result.Success -> {
                                updateSelectedMetadataCache(updated, bangumi)
                                indexedEntries = indexedEntries.replaceByMediaKey(updated)
                                selectedIndexEntry = updated
                                bangumiStatus = updated.metadataAppliedStatus(BANGUMI_METADATA_SOURCE_NAME)
                            }
                            is Result.Error -> bangumiStatus = result.error.toUserMessage()
                        }
                    }
                },
                onClear = {
                    scope.launch {
                        val sourceId = activeSourceId
                        val entry = selectedIndexEntry
                        if (sourceId == null) {
                            bangumiStatus = metadataSourceRequiredStatus()
                            return@launch
                        }
                        if (entry == null || entry.isDirectory) {
                            bangumiStatus = metadataClearEntryRequiredStatus()
                            return@launch
                        }
                        val updated = entry.clearExternalMetadata(sourceId = sourceId)
                        when (val result = repositories.index.upsertEntry(sourceId, updated)) {
                            is Result.Success -> {
                                repositories.metadata.invalidateCache(entry.bangumiMetadataCacheId())
                                indexedEntries = indexedEntries.replaceByMediaKey(updated)
                                selectedIndexEntry = updated
                                bangumiStatus = updated.metadataClearedStatus()
                            }
                            is Result.Error -> bangumiStatus = result.error.toUserMessage()
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
                    detailEpisodeFocusVersion += 1
                    focusManager.moveFocus(FocusDirection.Up)
                    true
                },
                onFocusNextPanel = {
                    mediaDetailsFocusVersion += 1
                    true
                },
                focusVersion = bangumiFocusVersion,
            )
            RecentPlaybackPanel(
                records = recentProgress,
                selectedRecord = selectedRecentProgress,
                status = recentStatus,
                focusVersion = recentPlaybackFocusVersion,
                onFocusPreviousPanel = {
                    detailEpisodeFocusVersion += 1
                    true
                },
                onFocusNextPanel = {
                    bangumiFocusVersion += 1
                    true
                },
                onRefresh = {
                    scope.launch {
                        refreshRecentProgress()
                    }
                },
                onRecordSelected = { record ->
                    selectedRecentProgress = record
                    mediaPath = record.episodeId
                    startSeconds = record.resumeStartSecondsText()
                    launchStatus = record.loadedPlaybackStatus(record.mediaDisplayName())
                },
                onClearSelected = {
                    scope.launch {
                        val selected = selectedRecentProgress
                        if (selected == null) {
                            recentStatus = recentPlaybackRequiredStatus()
                            return@launch
                        }
                        when (val result = repositories.progress.deleteProgress(selected.episodeId)) {
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
                recentRecord = selectedRecentProgress,
                focusVersion = mediaDetailsFocusVersion,
                onFocusPreviousPanel = {
                    bangumiFocusVersion += 1
                    true
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
                onProxyEnabledChange = { rssProxyEnabled = it },
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
                    autoScanEnabled = !autoScanEnabled
                    scope.launch {
                        repositories.scanPreferences.setAutoScanEnabled(autoScanEnabled)
                    }
                },
                onScanIntervalSelected = { hours ->
                    autoScanIntervalHours = hours
                    scope.launch {
                        repositories.scanPreferences.setAutoScanIntervalMs(hours.toScanIntervalMillis())
                    }
                },
                onToggleMergeSameAnime = {
                    mergeSameAnimeEnabled = !mergeSameAnimeEnabled
                    scope.launch {
                        repositories.scanPreferences.setMergeSameAnimeEnabled(mergeSameAnimeEnabled)
                    }
                },
                onSaveConfig = {
                    scope.launch {
                        val interval = parseCloudDriveIntervalMinutes(cloudIntervalMinutes)
                        val proxyPort = parseRssProxyPort(rssProxyPort)
                        when (val result = cloudRssActions.saveConfig(
                            endpointUrl = cloudEndpointUrl,
                            username = cloudUsername,
                            webDavSourceId = cloudLinkedSourceId,
                            inboxPath = cloudInboxPath,
                            libraryPath = cloudLibraryPath,
                            intervalMinutes = interval,
                            enabled = cloudEnabled,
                            rssProxyEnabled = rssProxyEnabled,
                            rssProxyHost = rssProxyHost,
                            rssProxyPort = proxyPort,
                        )) {
                            is Result.Success -> {
                                cloudIntervalMinutes = interval.toString()
                                rssProxyPort = proxyPort.toString()
                                cloudRssStatus = cloudRssConfigSavedStatus()
                            }
                            is Result.Error -> cloudRssStatus = result.error.toUserMessage()
                        }
                    }
                },
                onSaveCredentials = {
                    cloudRssActions.saveCredentials(cloudToken, cloudPassword)
                    cloudRssStatus = cloudDriveCredentialsSavedStatus()
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
                    cloudRssActions.clearCredentials()
                    cloudToken = ""
                    cloudPassword = ""
                    cloudRssStatus = cloudDriveCredentialsClearedStatus()
                },
                onRunSync = {
                    scope.launch {
                        when (
                            val result = cloudRssActions.runCloudDriveOnce(
                                onStarted = { status -> cloudRssStatus = status },
                            )
                        ) {
                            is CloudDriveRunActionResult.Completed -> {
                                cloudRssStatus = result.status
                                rescanLinkedCloudSource(result.status)?.let { scanMessage ->
                                    cloudRssStatus = "${result.status} $scanMessage"
                                }
                            }
                            is CloudDriveRunActionResult.Failed -> {
                                cloudRssStatus = result.status
                            }
                        }
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
                    val saveResult = saveBangumiTokenFormResult(
                        input = bangumiTokenInput,
                        existingToken = repositories.credentials.bangumiAccessToken,
                    )
                    repositories.credentials.bangumiAccessToken = saveResult.token
                    bangumiTokenInput = ""
                    bangumiTokenConfigured = saveResult.configured
                    bangumiStatus = saveResult.status
                },
                onClearBangumiToken = {
                    repositories.credentials.clearBangumiToken()
                    bangumiTokenInput = ""
                    bangumiTokenConfigured = false
                    bangumiStatus = metadataBangumiTokenClearedMessage()
                },
                sources = savedSources,
                activeSourceLabel = settingsActiveSourceLabel(activeSource?.info),
                indexedItemCount = indexedEntries.size,
                recentCount = recentProgress.size,
                selectedMediaTitle = selectedIndexEntry?.detailTitle()
                    ?: selectedRecentProgress?.mediaDisplayName()
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
                    repositories.webControlAccess.webControlEnabled = !webControlEnabled
                    refreshDesktopWebUiUrls()
                },
                onRotateWebControlToken = {
                    webControlAccessToken = repositories.webControlAccess.rotateAccessToken()
                    refreshDesktopWebUiUrls()
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
                        playbackEndAction = action
                        scope.launch {
                            repositories.playbackPreferences.setEndAction(action)
                        }
                    },
                    isPlayerActive = player != null,
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
