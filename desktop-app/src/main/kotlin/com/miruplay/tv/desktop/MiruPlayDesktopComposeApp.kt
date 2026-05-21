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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.miruplay.tv.clouddrive.GrpcCloudDriveClient
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.design.MiruPlayPalette
import com.miruplay.tv.design.MiruPlayRouteSurface
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopPlaybackBridge
import com.miruplay.tv.mediasource.desktop.desktopLocalSourceFromInfo
import com.miruplay.tv.mediasource.desktop.desktopSmbSourceFromInfo
import com.miruplay.tv.mediasource.desktop.desktopSourceFromInfo
import com.miruplay.tv.mediasource.desktop.desktopWebDavSourceFromInfo
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.PlaybackProgressSession
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.buildRssSubscriptionFromForm
import com.miruplay.tv.model.connectionDomain
import com.miruplay.tv.model.connectionPassword
import com.miruplay.tv.model.connectionUsername
import com.miruplay.tv.model.loadedPlaybackStatus
import com.miruplay.tv.model.localRootPath
import com.miruplay.tv.model.parseCloudDriveIntervalMinutes
import com.miruplay.tv.model.parseRssProxyPort
import com.miruplay.tv.model.recentPlaybackInitialStatus
import com.miruplay.tv.model.recentPlaybackLoadedStatus
import com.miruplay.tv.model.recentPlaybackRequiredStatus
import com.miruplay.tv.model.recentPlaybackShowingStatus
import com.miruplay.tv.model.remoteUrl
import com.miruplay.tv.model.resumeStartSecondsText
import com.miruplay.tv.model.withAutomationFormValues
import com.miruplay.tv.player.mpv.MpvProcessPlayer
import com.miruplay.tv.player.mpv.MpvRuntimeDiscovery
import com.miruplay.tv.player.mpv.RifeBackend
import com.miruplay.tv.player.mpv.mpvIdleStatus
import com.miruplay.tv.player.mpv.mpvNoActiveProcessStatus
import com.miruplay.tv.player.mpv.mpvPauseToggledStatus
import com.miruplay.tv.player.mpv.mpvPositionSyncedStatus
import com.miruplay.tv.player.mpv.mpvSeekBackStatus
import com.miruplay.tv.player.mpv.mpvSeekForwardStatus
import com.miruplay.tv.player.mpv.mpvStoppedStatus
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MetadataBatchMatch
import com.miruplay.tv.repository.MetadataBatchPlanner
import com.miruplay.tv.repository.MetadataBatchPlan
import com.miruplay.tv.repository.appliedStatus
import com.miruplay.tv.repository.applyMetadataBatchPlan
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
import com.miruplay.tv.repository.reviewAcceptedStatus
import com.miruplay.tv.repository.reviewConflictStatus
import com.miruplay.tv.repository.savePlaybackProgressOnStop
import com.miruplay.tv.repository.scanCompleteStatus
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
import com.miruplay.tv.repository.upsertById
import com.miruplay.tv.repository.webDavUrlRequiredStatus
import com.miruplay.tv.repository.withExternalMetadata
import com.miruplay.tv.repository.readyStatus
import com.miruplay.tv.repository.replaceByMediaKey
import com.miruplay.tv.repository.replaceByMediaKeys
import com.miruplay.tv.repository.replaceMatch
import com.miruplay.tv.repository.withSelectedCandidate
import com.miruplay.tv.scanner.desktop.DesktopMediaLibraryScanner
import com.miruplay.tv.scraper.desktop.DesktopBangumiScraper
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssScheduler
import com.miruplay.tv.sync.rss.cloudDriveCredentialsClearedStatus
import com.miruplay.tv.sync.rss.cloudDriveCredentialsSavedStatus
import com.miruplay.tv.sync.rss.cloudDriveLoginRequiredStatus
import com.miruplay.tv.sync.rss.cloudDriveLoginStartedStatus
import com.miruplay.tv.sync.rss.cloudDriveLoginSucceededStatus
import com.miruplay.tv.sync.rss.cloudDriveTokenRequiredStatus
import com.miruplay.tv.sync.rss.cloudDriveTokenValidationStartedStatus
import com.miruplay.tv.sync.rss.cloudRssConfigSavedStatus
import com.miruplay.tv.sync.rss.cloudRssInitialStatus
import com.miruplay.tv.sync.rss.cloudRssRescanStartedStatus
import com.miruplay.tv.sync.rss.cloudRssRunStartedStatus
import com.miruplay.tv.sync.rss.cloudRssScanSourceClearedStatus
import com.miruplay.tv.sync.rss.cloudRssScanSourceMissingStatus
import com.miruplay.tv.sync.rss.cloudRssScanSourceRequiredStatus
import com.miruplay.tv.sync.rss.cloudRssSchedulerDisabledStatus
import com.miruplay.tv.sync.rss.cloudRssSchedulerStartStatus
import com.miruplay.tv.sync.rss.cloudRssSchedulerStoppedStatus
import com.miruplay.tv.sync.rss.completeStatus
import com.miruplay.tv.sync.rss.linkedCloudRssScanSourceStatus
import com.miruplay.tv.sync.rss.loadedStatus as rssLoadedStatus
import com.miruplay.tv.sync.rss.rssSubscriptionDeletedStatus
import com.miruplay.tv.sync.rss.rssSubscriptionRequiredStatus
import com.miruplay.tv.sync.rss.rssSubscriptionsLoadFailedStatus
import com.miruplay.tv.sync.rss.rssSubscriptionsRefreshFailedStatus
import com.miruplay.tv.sync.rss.rssUrlRequiredStatus
import com.miruplay.tv.sync.rss.schedulerStatus
import com.miruplay.tv.sync.rss.savedStatus as rssSavedStatus
import com.miruplay.tv.sync.rss.selectedStatus as rssSelectedStatus
import com.miruplay.tv.sync.rss.showingStatus as rssShowingStatus
import com.miruplay.tv.sync.rss.verifiedStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.nio.file.Paths
import kotlin.math.roundToLong

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
private const val DESKTOP_BLANK_MEDIA_MESSAGE = "请先选择媒体，再启动 mpv。"
private const val MPV_COMMAND_PREVIEW_ERROR_MESSAGE = "无法生成 mpv 命令。"
private const val PLAYBACK_PROGRESS_POLL_INTERVAL_MS = 10_000L
internal typealias DesktopSection = MiruPlayRouteSurface.Section

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

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 820.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = desktopWindowTitle(),
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
                MiruPlayDesktopComposeApp()
            }
        }
    }
}

internal fun desktopWindowTitle(): String =
    "MiruPlay 桌面版"

@Composable
internal fun MiruPlayDesktopComposeApp() {
    val scope = rememberCoroutineScope()
    val repositories = remember { DesktopRepositories.fileBacked() }
    val playbackBridge = remember { DesktopPlaybackBridge() }
    val bangumiScraper = remember { DesktopBangumiScraper() }
    val focusManager = LocalFocusManager.current
    val cloudRssEngine = remember {
        DesktopCloudDriveRssAutomationEngine(
            repository = repositories.cloudDriveAutomation,
            credentials = repositories.credentials,
            cloudDriveClient = GrpcCloudDriveClient(),
        )
    }
    val cloudRssScheduler = remember { DesktopCloudDriveRssScheduler(cloudRssEngine, scope) }
    val cloudRssSchedulerState by cloudRssScheduler.state.collectAsState()
    val defaultMpvLayout = remember { MpvRuntimeDiscovery.defaultLayout() }
    val playbackLauncher = remember(playbackBridge) { DesktopPlaybackLauncher(playbackBridge) }
    var selectedDesktopSection by remember { mutableStateOf(MiruPlayRouteSurface.library) }
    var player by remember { mutableStateOf<MpvProcessPlayer?>(null) }
    var activePlaybackSession by remember { mutableStateOf<PlaybackProgressSession?>(null) }
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
    var recentProgress by remember { mutableStateOf(emptyList<ProgressRecord>()) }
    var selectedRecentProgress by remember { mutableStateOf<ProgressRecord?>(null) }
    var selectedDetailEpisodeSeason by remember { mutableStateOf<Int?>(null) }
    var detailHeroFocusVersion by remember { mutableStateOf(0) }
    var detailEpisodeFocusVersion by remember { mutableStateOf(0) }
    var recentPlaybackFocusVersion by remember { mutableStateOf(0) }
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
    var mediaPath by remember { mutableStateOf("") }
    var subtitlePath by remember { mutableStateOf("") }
    var startSeconds by remember { mutableStateOf("0") }
    var fullscreen by remember { mutableStateOf(false) }
    var keepOpen by remember { mutableStateOf(false) }
    var rifeEnabled by remember { mutableStateOf(DEFAULT_DESKTOP_RIFE_ENABLED) }
    var rifeBackend by remember { mutableStateOf(RifeBackend.NVIDIA) }
    var status by remember { mutableStateOf(mpvRuntimeStatusFromInputs(mpvPath, configDir)) }
    var launchStatus by remember { mutableStateOf(mpvIdleStatus()) }
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
                blankMediaMessage = DESKTOP_BLANK_MEDIA_MESSAGE,
                errorMessage = MPV_COMMAND_PREVIEW_ERROR_MESSAGE,
            )
        }
    }

    val detailEpisodes = remember(indexedEntries, selectedIndexEntry) {
        detailEpisodesForSelection(indexedEntries, selectedIndexEntry)
    }

    LaunchedEffect(selectedIndexEntry?.sourceId, selectedIndexEntry?.path, detailEpisodes.map { it.seasonNumber }) {
        selectedDetailEpisodeSeason = detailActiveEpisodeSeason(
            episodes = detailEpisodes,
            selectedEntry = selectedIndexEntry,
            requestedSeason = selectedDetailEpisodeSeason,
        )
    }

    DisposableEffect(playbackBridge, cloudRssScheduler) {
        onDispose {
            playbackBridge.close()
            cloudRssScheduler.stop()
        }
    }

    suspend fun loadIndexedEntries(sourceId: Long, statusWhenEmpty: String) {
        when (val result = repositories.index.queryIndex(sourceId, "")) {
            is Result.Success -> {
                indexedEntries = result.data.filterNot { it.isDirectory }
                selectedIndexEntry = selectedIndexEntry?.let { selected ->
                    indexedEntries.firstOrNull { it.sourceId == selected.sourceId && it.path == selected.path }
                }
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

    LaunchedEffect(repositories) {
        var startupSource: MediaSourceInfo? = null
        when (val sources = repositories.mediaSources.getSources()) {
            is Result.Success -> {
                savedSources = sources.data
                val local = sources.data.firstOrNull { it.type == MediaSourceType.LOCAL }
                val webDav = sources.data.firstOrNull { it.type == MediaSourceType.WEBDAV }
                val smb = sources.data.firstOrNull { it.type == MediaSourceType.SMB }
                if (local != null) {
                    val root = local.localRootPath().orEmpty()
                    libraryRoot = root
                    activeSourceId = local.id
                    val localSource = desktopLocalSourceFromInfo(local)
                    activeLocalSource = localSource
                    activeSource = localSource
                    libraryStatus = local.loadedStatus()
                    startupSource = local
                }
                if (webDav != null) {
                    webDavUrl = webDav.remoteUrl().orEmpty()
                    webDavUsername = webDav.connectionUsername()
                    webDavPassword = webDav.connectionPassword()
                    if (local == null) {
                        activeSourceId = webDav.id
                        activeSource = desktopWebDavSourceFromInfo(webDav)
                        remoteStatus = webDav.loadedStatus()
                        startupSource = webDav
                    }
                }
                if (smb != null) {
                    smbUrl = smb.remoteUrl().orEmpty()
                    smbDomain = smb.connectionDomain()
                    smbUsername = smb.connectionUsername()
                    smbPassword = smb.connectionPassword()
                    if (local == null && webDav == null) {
                        activeSourceId = smb.id
                        activeSource = desktopSmbSourceFromInfo(smb)
                        remoteStatus = smb.loadedStatus()
                        startupSource = smb
                    }
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
        runCatching {
            repositories.cloudDriveAutomation.observeSubscriptions().first()
        }.onSuccess { subscriptions ->
            rssSubscriptions = subscriptions
            cloudRssStatus = subscriptions.rssLoadedStatus()
        }.onFailure { error ->
            cloudRssStatus = rssSubscriptionsLoadFailedStatus(error.message)
        }
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

    suspend fun refreshRecentProgress() {
        when (val recents = repositories.progress.getContinueWatching(limit = 12)) {
            is Result.Success -> {
                recentProgress = recents.data
                selectedRecentProgress = selectedRecentProgress?.let { selected ->
                    recents.data.firstOrNull { it.episodeId == selected.episodeId }
                }
                recentStatus = recentPlaybackShowingStatus(recents.data)
            }
            is Result.Error -> recentStatus = recents.error.toUserMessage()
        }
    }

    suspend fun savePlaybackProgress(
        episodeId: String,
        positionMs: Long,
        lastWatched: Long = System.currentTimeMillis(),
    ): Result<Unit> =
        repositories.progress.saveProgress(
            episodeId = episodeId,
            positionMs = positionMs,
            lastWatched = lastWatched,
            incrementPlayCount = false,
        )

    LaunchedEffect(player, activePlaybackSession) {
        val activePlayer = player ?: return@LaunchedEffect
        val session = activePlaybackSession ?: return@LaunchedEffect
        while (true) {
            delay(PLAYBACK_PROGRESS_POLL_INTERVAL_MS)
            if (player !== activePlayer || activePlaybackSession !== session) {
                return@LaunchedEffect
            }
            when (
                val synced = syncObservedPlaybackProgress(
                    session = session,
                    queryPositionMs = { activePlayer.queryTimePositionMs() },
                    saveProgress = { episodeId, positionMs, lastWatched ->
                        savePlaybackProgress(episodeId, positionMs, lastWatched)
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
            selectedRssSubscription = selectedRssSubscription?.let { selected ->
                subscriptions.firstOrNull { it.id == selected.id }
            }
            cloudRssStatus = subscriptions.rssShowingStatus()
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
        when (val scan = DesktopMediaLibraryScanner().scan(sourceId, source)) {
            is Result.Success -> {
                when (val indexed = repositories.index.rebuildIndex(sourceId, scan.data.entries)) {
                    is Result.Success -> {
                        indexedEntries = scan.data.entries.filterNot { it.isDirectory }
                        selectedIndexEntry = null
                        val message = scanCompleteStatus(
                            filesIndexed = scan.data.filesIndexed,
                            directoriesVisited = scan.data.directoriesVisited,
                        )
                        updateStatus(message)
                        libraryStatus = message
                    }
                    is Result.Error -> updateStatus(indexed.error.toUserMessage())
                }
            }
            is Result.Error -> updateStatus(scan.error.toUserMessage())
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

        cloudRssStatus = sourceInfo.cloudRssRescanStartedStatus(reason)
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

    LaunchedEffect(cloudRssSchedulerState.lastRunCompletedAt) {
        if (cloudRssSchedulerState.lastRunCompletedAt > 0L) {
            rescanLinkedCloudSource("Scheduled sync complete.")
        }
    }

    suspend fun activateSavedSource(sourceInfo: MediaSourceInfo) {
        activeSourceId = sourceInfo.id
        val source = desktopSourceFromInfo(sourceInfo)
        activeSource = source
        activeLocalSource = source as? DesktopLocalMediaSource
        indexedEntries = emptyList()
        selectedIndexEntry = null
        selectedRemoteEntry = null
        when (sourceInfo.type) {
            MediaSourceType.LOCAL -> {
                libraryRoot = sourceInfo.localRootPath().orEmpty()
                remoteEntries = emptyList()
                remotePath = ""
                libraryStatus = sourceInfo.loadedStatus(saved = true)
            }
            MediaSourceType.WEBDAV -> {
                webDavUrl = sourceInfo.remoteUrl().orEmpty()
                webDavUsername = sourceInfo.connectionUsername()
                webDavPassword = sourceInfo.connectionPassword()
                remotePath = ""
                remoteStatus = sourceInfo.loadedStatus(saved = true)
                loadRemoteDirectory(source, "")
            }
            MediaSourceType.SMB -> {
                smbUrl = sourceInfo.remoteUrl().orEmpty()
                smbDomain = sourceInfo.connectionDomain()
                smbUsername = sourceInfo.connectionUsername()
                smbPassword = sourceInfo.connectionPassword()
                remotePath = ""
                remoteStatus = sourceInfo.loadedStatus(saved = true)
                loadRemoteDirectory(source, "")
            }
        }
        loadIndexedEntries(sourceInfo.id, sourceInfo.loadedStatus(saved = true))
    }

    val contentScrollState = rememberScrollState()
    LaunchedEffect(selectedDesktopSection) {
        contentScrollState.scrollTo(0)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
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
                        when (val result = repositories.mediaSources.addSource(sourceInfo)) {
                            is Result.Success -> {
                                activeSourceId = result.data
                                val stored = sourceInfo.copy(id = result.data)
                                val localSource = desktopLocalSourceFromInfo(stored)
                                activeLocalSource = localSource
                                activeSource = localSource
                                savedSources = savedSources.upsertById(stored)
                                libraryStatus = stored.readyStatus()
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
                                indexedEntries = result.data.filterNot { it.isDirectory }
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
                        when (val result = repositories.mediaSources.addSource(sourceInfo)) {
                            is Result.Success -> {
                                val stored = sourceInfo.copy(id = result.data)
                                val source = desktopWebDavSourceFromInfo(stored)
                                activeSourceId = result.data
                                activeSource = source
                                savedSources = savedSources.upsertById(stored)
                                remotePath = ""
                                remoteStatus = stored.readyStatus()
                                loadRemoteDirectory(source, "")
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
                        when (val result = repositories.mediaSources.addSource(sourceInfo)) {
                            is Result.Success -> {
                                val stored = sourceInfo.copy(id = result.data)
                                val source = desktopSmbSourceFromInfo(stored)
                                activeSourceId = result.data
                                activeSource = source
                                savedSources = savedSources.upsertById(stored)
                                remotePath = ""
                                remoteStatus = stored.readyStatus()
                                loadRemoteDirectory(source, "")
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
                        when (val result = bangumiScraper.searchAnime(query)) {
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
                                    searchCandidates = { query ->
                                        bangumiScraper.searchAnime(query).getOrNull().orEmpty()
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
                                val entries = entriesResult.data.filterNot { it.isDirectory }
                                val plan = MetadataBatchPlanner.planFor(entries, bangumiBatchMatches)
                                bangumiBatchPlan = plan
                                if (plan.readyUpdates.isEmpty()) {
                                    bangumiStatus = MetadataBatchPlanner.displayPlanSummary(plan)
                                    return@launch
                                }
                                val write = repositories.index.applyMetadataBatchPlan(sourceId, plan)
                                bangumiBatchRollback = write.rollbackEntries
                                indexedEntries = indexedEntries.replaceByMediaKeys(write.updatedEntries)
                                selectedIndexEntry = selectedIndexEntry?.let { selected ->
                                    write.updatedEntries.firstOrNull {
                                        it.path == selected.path && it.sourceId == selected.sourceId
                                    }
                                        ?: selected
                                }
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
                                selectedIndexEntry = selectedIndexEntry?.let { selected ->
                                    restore.data.rollbackEntries.firstOrNull {
                                        it.path == selected.path && it.sourceId == selected.sourceId
                                    }
                                        ?: selected
                                }
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
                                val entries = entriesResult.data.filterNot { it.isDirectory }
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
                                selectedIndexEntry = selectedIndexEntry?.let { selected ->
                                    write.updatedEntries.firstOrNull {
                                        it.path == selected.path && it.sourceId == selected.sourceId
                                    }
                                        ?: selected
                                }
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
                                indexedEntries = indexedEntries.replaceByMediaKey(updated)
                                selectedIndexEntry = updated
                                bangumiStatus = updated.metadataClearedStatus()
                            }
                            is Result.Error -> bangumiStatus = result.error.toUserMessage()
                        }
                    }
                },
                onFocusPreviousPanel = {
                    detailEpisodeFocusVersion += 1
                    focusManager.moveFocus(FocusDirection.Up)
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
                linkedSourceLabel = desktopLinkedSourceLabel(savedSources, cloudLinkedSourceId),
                onSaveConfig = {
                    scope.launch {
                        val interval = parseCloudDriveIntervalMinutes(cloudIntervalMinutes)
                        val proxyPort = parseRssProxyPort(rssProxyPort)
                        val currentConfig = when (val current = repositories.cloudDriveAutomation.getConfig()) {
                            is Result.Success -> current.data
                            is Result.Error -> {
                                cloudRssStatus = current.error.toUserMessage()
                                return@launch
                            }
                        }
                        val config = currentConfig.withAutomationFormValues(
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
                        )
                        when (val result = repositories.cloudDriveAutomation.saveConfig(config)) {
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
                    repositories.credentials.cloudDriveToken = cloudToken.trim().takeIf { it.isNotBlank() }
                    repositories.credentials.cloudDrivePassword = cloudPassword.takeIf { it.isNotBlank() }
                    cloudRssStatus = cloudDriveCredentialsSavedStatus()
                },
                onLoginCloudDrive = {
                    scope.launch {
                        val endpoint = cloudEndpointUrl.trim()
                        val user = cloudUsername.trim()
                        val pass = cloudPassword
                        if (endpoint.isBlank() || user.isBlank() || pass.isBlank()) {
                            cloudRssStatus = cloudDriveLoginRequiredStatus()
                            return@launch
                        }
                        cloudRssStatus = cloudDriveLoginStartedStatus()
                        when (val result = cloudRssEngine.login(endpoint, user, pass)) {
                            is Result.Success -> {
                                cloudToken = repositories.credentials.cloudDriveToken.orEmpty()
                                cloudRssStatus = cloudDriveLoginSucceededStatus()
                            }
                            is Result.Error -> cloudRssStatus = result.error.toUserMessage()
                        }
                    }
                },
                onVerifyApiToken = {
                    scope.launch {
                        val endpoint = cloudEndpointUrl.trim()
                        val apiToken = cloudToken.trim()
                        if (endpoint.isBlank() || apiToken.isBlank()) {
                            cloudRssStatus = cloudDriveTokenRequiredStatus()
                            return@launch
                        }
                        cloudRssStatus = cloudDriveTokenValidationStartedStatus()
                        when (val result = cloudRssEngine.saveApiToken(endpoint, apiToken)) {
                            is Result.Success -> {
                                cloudToken = apiToken
                                cloudRssStatus = result.data.verifiedStatus()
                            }
                            is Result.Error -> cloudRssStatus = result.error.toUserMessage()
                        }
                    }
                },
                onClearCredentials = {
                    repositories.credentials.clearCloudDriveCredentials()
                    cloudToken = ""
                    cloudPassword = ""
                    cloudRssStatus = cloudDriveCredentialsClearedStatus()
                },
                onRunSync = {
                    scope.launch {
                        cloudRssStatus = cloudRssRunStartedStatus()
                        when (val result = cloudRssEngine.runOnce()) {
                            is Result.Success -> {
                                val message = result.data.completeStatus()
                                cloudRssStatus = message
                                rescanLinkedCloudSource(message)?.let { scanMessage ->
                                    cloudRssStatus = "$message $scanMessage"
                                }
                            }
                            is Result.Error -> cloudRssStatus = result.error.toUserMessage()
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
                        cloudRssStatus = sourceInfo.linkedCloudRssScanSourceStatus()
                    }
                },
                onClearScanSource = {
                    cloudLinkedSourceId = null
                    cloudRssStatus = cloudRssScanSourceClearedStatus()
                },
                onSaveSubscription = {
                    scope.launch {
                        val url = rssUrl.trim()
                        if (url.isBlank()) {
                            cloudRssStatus = rssUrlRequiredStatus()
                            return@launch
                        }
                        val selectedMatchingSubscription = selectedRssSubscription?.takeIf { it.url == url }
                        val subscription = buildRssSubscriptionFromForm(
                            name = rssName,
                            url = rssUrl,
                            filterRegex = rssFilter,
                            enabled = rssEnabled,
                            existingId = selectedMatchingSubscription?.id ?: 0L,
                            existingLastCheckedAt = selectedMatchingSubscription?.lastCheckedAt ?: 0L,
                        ) ?: run {
                            cloudRssStatus = rssUrlRequiredStatus()
                            return@launch
                        }
                        when (val result = repositories.cloudDriveAutomation.saveSubscription(subscription)) {
                            is Result.Success -> {
                                cloudRssStatus = subscription.rssSavedStatus()
                                refreshRssSubscriptions()
                            }
                            is Result.Error -> cloudRssStatus = result.error.toUserMessage()
                        }
                    }
                },
                onSubscriptionSelected = { subscription ->
                    selectedRssSubscription = subscription
                    rssName = subscription.name
                    rssUrl = subscription.url
                    rssFilter = subscription.filterRegex.orEmpty()
                    rssEnabled = subscription.enabled
                    cloudRssStatus = subscription.rssSelectedStatus()
                },
                onDeleteSubscription = {
                    scope.launch {
                        val subscription = selectedRssSubscription
                        if (subscription == null) {
                            cloudRssStatus = rssSubscriptionRequiredStatus()
                            return@launch
                        }
                        when (val result = repositories.cloudDriveAutomation.deleteSubscription(subscription.id)) {
                            is Result.Success -> {
                                selectedRssSubscription = null
                                rssName = ""
                                rssUrl = ""
                                rssFilter = ""
                                rssEnabled = true
                                cloudRssStatus = rssSubscriptionDeletedStatus()
                                refreshRssSubscriptions()
                            }
                            is Result.Error -> cloudRssStatus = result.error.toUserMessage()
                        }
                    }
                },
                sources = savedSources,
                activeSourceLabel = desktopActiveSourceLabel(activeSource?.info),
                indexedItemCount = indexedEntries.size,
                recentCount = recentProgress.size,
                selectedMediaTitle = selectedIndexEntry?.detailTitle()
                    ?: selectedRecentProgress?.mediaDisplayName()
                    ?: "未选择条目",
                playbackSummary = if (rifeEnabled) {
                    "RIFE ${rifeBackend.name}"
                } else {
                    "RIFE 关闭"
                },
                metadataSummary = selectedIndexEntry?.let { entry ->
                    entry.metadataTitle?.takeIf { it.isNotBlank() }?.let { "已匹配：$it" } ?: "待匹配"
                } ?: "未选择条目",
                libraryStatus = libraryStatus,
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
                        isPlayerActive = player != null,
                        launchStatus = launchStatus,
                        onBackToDetails = { selectedDesktopSection = MiruPlayRouteSurface.details },
                        onLaunch = {
                            scope.launch {
                                runCatching {
                                    val request = DesktopPlaybackLaunchRequest(
                                        mpvPath = mpvPath,
                                        configDir = configDir,
                                        mediaPath = mediaPath,
                                        subtitlePath = subtitlePath,
                                        startSeconds = startSeconds,
                                        fullscreen = fullscreen,
                                        keepOpen = keepOpen,
                                        rifeEnabled = rifeEnabled,
                                        rifeBackend = rifeBackend,
                                        activeSource = activeSource,
                                        activeSourceId = activeSourceId,
                                        blankMediaMessage = DESKTOP_BLANK_MEDIA_MESSAGE,
                                        fallbackMediaSourceId = DESKTOP_PLAYBACK_MEDIA_SOURCE_ID,
                                    )
                                    when (val result = playbackLauncher.launch(request)) {
                                        is Result.Success -> {
                                            player = result.data.player
                                            activePlaybackSession = result.data.session
                                            repositories.progress.saveProgress(
                                                episodeId = result.data.session.episodeId,
                                                positionMs = result.data.source.startPosition,
                                                lastWatched = System.currentTimeMillis(),
                                                incrementPlayCount = true,
                                            )
                                            refreshRecentProgress()
                                            launchStatus = result.data.status
                                        }
                                        is Result.Error -> launchStatus = result.error.toUserMessage()
                                    }
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
                        onSeekBack = {
                            scope.launch {
                                val activePlayer = player
                                if (activePlayer == null) {
                                    launchStatus = mpvNoActiveProcessStatus()
                                    return@launch
                                }
                                when (val result = activePlayer.seekBy(-10.0)) {
                                    is Result.Success -> {
                                        activePlaybackSession?.seekBy(-10.0)
                                        launchStatus = mpvSeekBackStatus(seconds = 10)
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
                                when (val result = activePlayer.seekBy(30.0)) {
                                    is Result.Success -> {
                                        activePlaybackSession?.seekBy(30.0)
                                        launchStatus = mpvSeekForwardStatus(seconds = 30)
                                    }
                                    is Result.Error -> launchStatus = result.error.toUserMessage()
                                }
                            }
                        },
                        onStop = {
                            scope.launch {
                                val activePlayer = player
                                activePlaybackSession?.let { session ->
                                    val saveProgress: suspend (String, Long, Long) -> Result<Unit> = { episodeId, positionMs, lastWatched ->
                                        savePlaybackProgress(episodeId, positionMs, lastWatched)
                                    }
                                    savePlaybackProgressOnStop(
                                        session = session,
                                        queryPositionMs = null,
                                        saveProgress = saveProgress,
                                    )
                                }
                                activePlayer?.stop()
                                player = null
                                activePlaybackSession = null
                                refreshRecentProgress()
                                launchStatus = mpvStoppedStatus()
                            }
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
