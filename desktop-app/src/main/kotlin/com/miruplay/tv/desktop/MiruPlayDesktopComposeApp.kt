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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.miruplay.tv.clouddrive.GrpcCloudDriveClient
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.design.MiruPlayPalette
import com.miruplay.tv.design.MiruPlayRouteSurface
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssScheduler
import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopSmbMediaSource
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.buildRssSubscriptionFromForm
import com.miruplay.tv.model.parseCloudDriveIntervalMinutes
import com.miruplay.tv.model.parseRssProxyPort
import com.miruplay.tv.model.withAutomationFormValues
import com.miruplay.tv.player.mpv.MpvProcessPlayer
import com.miruplay.tv.player.mpv.RifeBackend
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MetadataBatchPlanner
import com.miruplay.tv.repository.appliedStatus
import com.miruplay.tv.repository.applyMetadataBatchPlan
import com.miruplay.tv.repository.clearExternalMetadata
import com.miruplay.tv.repository.displayName
import com.miruplay.tv.repository.desktop.DesktopRepositories
import com.miruplay.tv.repository.metadataBatchSearchingStatus
import com.miruplay.tv.repository.noMetadataBatchEntriesStatus
import com.miruplay.tv.repository.noMetadataBatchPreviewStatus
import com.miruplay.tv.repository.noMetadataBatchUndoStatus
import com.miruplay.tv.repository.restoreMetadataBatchUndo
import com.miruplay.tv.repository.restoredStatus
import com.miruplay.tv.repository.reviewAcceptedStatus
import com.miruplay.tv.repository.selectedCandidateStatus
import com.miruplay.tv.repository.summaryStatus
import com.miruplay.tv.repository.withExternalMetadata
import com.miruplay.tv.scanner.desktop.DesktopMediaLibraryScanner
import com.miruplay.tv.scraper.desktop.DesktopBangumiScraper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
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
        title = "MiruPlay Desktop",
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

@Composable
internal fun MiruPlayDesktopComposeApp() {
    val scope = rememberCoroutineScope()
    val repositories = remember { DesktopRepositories.fileBacked() }
    val playbackBridge = remember { DesktopPlaybackBridge() }
    val bangumiScraper = remember { DesktopBangumiScraper() }
    val cloudRssEngine = remember {
        DesktopCloudDriveRssAutomationEngine(
            repository = repositories.cloudDriveAutomation,
            credentials = repositories.credentials,
            cloudDriveClient = GrpcCloudDriveClient(),
        )
    }
    val cloudRssScheduler = remember { DesktopCloudDriveRssScheduler(cloudRssEngine, scope) }
    val cloudRssSchedulerState by cloudRssScheduler.state.collectAsState()
    var selectedDesktopSection by remember { mutableStateOf(MiruPlayRouteSurface.library) }
    var player by remember { mutableStateOf<MpvProcessPlayer?>(null) }
    var activePlaybackSession by remember { mutableStateOf<DesktopPlaybackSession?>(null) }
    var mpvPath by remember { mutableStateOf(DesktopRuntimeDefaults.mpvPath()) }
    var configDir by remember { mutableStateOf(DesktopRuntimeDefaults.configDirectory()) }
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
    var bangumiBatchMatches by remember { mutableStateOf(emptyList<DesktopBangumiBatchMatch>()) }
    var selectedBangumiBatchMatch by remember { mutableStateOf<DesktopBangumiBatchMatch?>(null) }
    var bangumiBatchPlan by remember { mutableStateOf<DesktopBangumiBatchPlan?>(null) }
    var bangumiBatchRollback by remember { mutableStateOf(emptyList<MediaIndexEntry>()) }
    var bangumiStatus by remember { mutableStateOf(bangumiInitialStatus()) }
    var recentProgress by remember { mutableStateOf(emptyList<ProgressRecord>()) }
    var selectedRecentProgress by remember { mutableStateOf<ProgressRecord?>(null) }
    var recentStatus by remember { mutableStateOf(recentInitialStatus()) }
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
    var cloudRssStatus by remember { mutableStateOf(cloudRssInitialMessage()) }
    var mediaPath by remember { mutableStateOf("") }
    var subtitlePath by remember { mutableStateOf("") }
    var startSeconds by remember { mutableStateOf("0") }
    var fullscreen by remember { mutableStateOf(false) }
    var keepOpen by remember { mutableStateOf(false) }
    var rifeEnabled by remember { mutableStateOf(true) }
    var rifeBackend by remember { mutableStateOf(RifeBackend.NVIDIA) }
    var status by remember { mutableStateOf(runtimeStatus(mpvPath, configDir)) }
    var launchStatus by remember { mutableStateOf(playbackIdleStatus()) }
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
            buildCommandPreview(
                mpvPath = mpvPath,
                configDir = configDir,
                mediaPath = mediaPath,
                subtitlePath = subtitlePath,
                startSeconds = startSeconds,
                fullscreen = fullscreen,
                keepOpen = keepOpen,
                rifeEnabled = rifeEnabled,
                rifeBackend = rifeBackend,
            )
        }
    }

    DisposableEffect(playbackBridge, cloudRssScheduler) {
        onDispose {
            playbackBridge.close()
            cloudRssScheduler.stop()
        }
    }

    LaunchedEffect(repositories) {
        when (val sources = repositories.mediaSources.getSources()) {
            is Result.Success -> {
                savedSources = sources.data
                val local = sources.data.firstOrNull { it.type == MediaSourceType.LOCAL }
                val webDav = sources.data.firstOrNull { it.type == MediaSourceType.WEBDAV }
                val smb = sources.data.firstOrNull { it.type == MediaSourceType.SMB }
                if (local != null) {
                    val root = local.connectionInfo["path"].orEmpty()
                    libraryRoot = root
                    activeSourceId = local.id
                    val localSource = DesktopLocalMediaSource(local)
                    activeLocalSource = localSource
                    activeSource = localSource
                    libraryStatus = loadedSourceStatus(local)
                }
                if (webDav != null) {
                    webDavUrl = webDav.connectionInfo["url"].orEmpty()
                    webDavUsername = webDav.connectionInfo["username"].orEmpty()
                    webDavPassword = webDav.connectionInfo["password"].orEmpty()
                    if (local == null) {
                        activeSourceId = webDav.id
                        activeSource = desktopWebDavSourceFromInfo(webDav)
                        remoteStatus = loadedSourceStatus(webDav)
                    }
                }
                if (smb != null) {
                    smbUrl = smb.connectionInfo["url"].orEmpty()
                    smbDomain = smb.connectionInfo["domain"].orEmpty()
                    smbUsername = smb.connectionInfo["username"].orEmpty()
                    smbPassword = smb.connectionInfo["password"].orEmpty()
                    if (local == null && webDav == null) {
                        activeSourceId = smb.id
                        activeSource = DesktopSmbMediaSource(smb)
                        remoteStatus = loadedSourceStatus(smb)
                    }
                }
            }
            is Result.Error -> libraryStatus = sources.error.toUserMessage()
        }
        when (val recents = repositories.progress.getContinueWatching(limit = 12)) {
            is Result.Success -> {
                recentProgress = recents.data
                recentStatus = recentLoadedStatus(recents.data)
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
            cloudRssStatus = rssSubscriptionsLoadedMessage(subscriptions)
        }.onFailure { error ->
            cloudRssStatus = rssSubscriptionsLoadFailedMessage(error.message)
        }
    }

    suspend fun loadRemoteDirectory(source: DesktopMediaSource, path: String) {
        remoteStatus = remoteLoadingStatus(source.info, path)
        when (val result = source.listFiles(path)) {
            is Result.Success -> {
                remotePath = path
                remoteEntries = result.data
                selectedRemoteEntry = null
                remoteStatus = remoteShowingStatus(source.info, result.data)
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
                recentStatus = recentShowingStatus(recents.data)
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
                val synced = syncPlaybackProgressFromMpv(
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
                        launchStatus = playbackPositionSyncedStatus(positionMs)
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
            cloudRssStatus = rssSubscriptionsShowingMessage(subscriptions)
        }.onFailure { error ->
            cloudRssStatus = rssSubscriptionsRefreshFailedMessage(error.message)
        }
    }

    suspend fun scanCurrentSource(updateStatus: (String) -> Unit) {
        val sourceId = activeSourceId
        val source = activeSource ?: activeLocalSource
        if (sourceId == null || source == null) {
            updateStatus(openSourceBeforeScanningStatus())
            return
        }
        updateStatus(scanningSourceStatus(source.info))
        when (val scan = DesktopMediaLibraryScanner().scan(sourceId, source)) {
            is Result.Success -> {
                when (val indexed = repositories.index.rebuildIndex(sourceId, scan.data.entries)) {
                    is Result.Success -> {
                        indexedEntries = scan.data.entries.filterNot { it.isDirectory }.take(24)
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
            cloudRssStatus = cloudRssScanSourceMissingMessage()
            return null
        }

        cloudRssStatus = cloudRssRescanStartedMessage(sourceInfo, reason)
        val source = desktopSourceFromInfo(sourceInfo)
        return when (val scan = DesktopMediaLibraryScanner().scan(sourceInfo.id, source)) {
            is Result.Success -> {
                when (val indexed = repositories.index.rebuildIndex(sourceInfo.id, scan.data.entries)) {
                    is Result.Success -> {
                        if (activeSourceId == sourceInfo.id) {
                            indexedEntries = scan.data.entries.filterNot { it.isDirectory }.take(24)
                            selectedIndexEntry = null
                        }
                        val message = rescanCompleteStatus(
                            filesIndexed = scan.data.filesIndexed,
                            directoriesVisited = scan.data.directoriesVisited,
                        )
                        if (sourceInfo.type == MediaSourceType.LOCAL) {
                            libraryStatus = message
                        } else {
                            remoteStatus = message
                        }
                        message
                    }
                    is Result.Error -> {
                        val message = indexed.error.toUserMessage()
                        cloudRssStatus = message
                        null
                    }
                }
            }
            is Result.Error -> {
                val message = scan.error.toUserMessage()
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
                libraryRoot = sourceInfo.connectionInfo["path"].orEmpty()
                remoteEntries = emptyList()
                remotePath = ""
                libraryStatus = loadedSourceStatus(sourceInfo, saved = true)
            }
            MediaSourceType.WEBDAV -> {
                webDavUrl = sourceInfo.connectionInfo["url"].orEmpty()
                webDavUsername = sourceInfo.connectionInfo["username"].orEmpty()
                webDavPassword = sourceInfo.connectionInfo["password"].orEmpty()
                remotePath = ""
                remoteStatus = loadedSourceStatus(sourceInfo, saved = true)
                loadRemoteDirectory(source, "")
            }
            MediaSourceType.SMB -> {
                smbUrl = sourceInfo.connectionInfo["url"].orEmpty()
                smbDomain = sourceInfo.connectionInfo["domain"].orEmpty()
                smbUsername = sourceInfo.connectionInfo["username"].orEmpty()
                smbPassword = sourceInfo.connectionInfo["password"].orEmpty()
                remotePath = ""
                remoteStatus = loadedSourceStatus(sourceInfo, saved = true)
                loadRemoteDirectory(source, "")
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.MAIN_SECTION_GAP_DP.dp),
    ) {
        DesktopTvNavigation(
            selectedSection = selectedDesktopSection,
            onSectionSelected = { selectedDesktopSection = it },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            DesktopTvHeader(selectedSection = selectedDesktopSection)
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
                                val localSource = DesktopLocalMediaSource(stored)
                                activeLocalSource = localSource
                                activeSource = localSource
                                savedSources = savedSources.upsertSource(stored)
                                libraryStatus = readySourceStatus(stored)
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
                                indexedEntries = result.data.filterNot { it.isDirectory }.take(24)
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
                                libraryStatus = clearedIndexStatus(sourceId)
                                bangumiStatus = bangumiInitialStatus()
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
                                bangumiStatus = bangumiInitialStatus()
                            }
                            is Result.Error -> libraryStatus = result.error.toUserMessage()
                        }
                    }
                },
                onEntrySelected = { entry ->
                    selectedIndexEntry = entry
                    mediaPath = entry.path
                    launchStatus = selectedIndexEntryPlaybackStatus(entry)
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
                        val sourceInfo = webDavSourceInfo(url, webDavUsername.trim(), webDavPassword)
                        when (val result = repositories.mediaSources.addSource(sourceInfo)) {
                            is Result.Success -> {
                                val stored = sourceInfo.copy(id = result.data)
                                val source = desktopWebDavSourceFromInfo(stored)
                                activeSourceId = result.data
                                activeSource = source
                                savedSources = savedSources.upsertSource(stored)
                                remotePath = ""
                                remoteStatus = readySourceStatus(stored)
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
                        val sourceInfo = smbSourceInfo(
                            url = url,
                            domain = smbDomain.trim(),
                            username = smbUsername.trim(),
                            password = smbPassword,
                        )
                        when (val result = repositories.mediaSources.addSource(sourceInfo)) {
                            is Result.Success -> {
                                val stored = sourceInfo.copy(id = result.data)
                                val source = DesktopSmbMediaSource(stored)
                                activeSourceId = result.data
                                activeSource = source
                                savedSources = savedSources.upsertSource(stored)
                                remotePath = ""
                                remoteStatus = readySourceStatus(stored)
                                loadRemoteDirectory(source, "")
                            }
                            is Result.Error -> remoteStatus = result.error.toUserMessage()
                        }
                    }
                },
                onUp = {
                    val source = activeSource
                    val parent = remoteParent(remotePath)
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
                onEntrySelected = { entry ->
                    selectedRemoteEntry = entry
                    val source = activeSource
                    if (entry.isDirectory && source != null) {
                        scope.launch { loadRemoteDirectory(source, entry.path) }
                    } else if (entry.isDirectory) {
                        remoteStatus = openRemoteSourceBeforeBrowsingStatus()
                    } else {
                        mediaPath = entry.path
                        launchStatus = selectedRemotePlaybackStatus(entry)
                    }
                },
                    )
                }
                MiruPlayRouteSurface.details -> {
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
                    val query = bangumiQueryFor(selectedIndexEntry)
                    if (query == null) {
                        bangumiStatus = bangumiIndexedVideoRequiredStatus()
                    } else {
                        bangumiQuery = query
                        bangumiStatus = bangumiQuerySetFromIndexStatus()
                    }
                },
                onSearch = {
                    scope.launch {
                        val query = bangumiQuery.trim().ifBlank {
                            bangumiQueryFor(selectedIndexEntry).orEmpty()
                        }
                        if (query.isBlank()) {
                            bangumiStatus = bangumiQueryRequiredStatus()
                            return@launch
                        }
                        bangumiQuery = query
                        bangumiStatus = bangumiSearchStartedStatus(query)
                        when (val result = bangumiScraper.searchAnime(query)) {
                            is Result.Success -> {
                                bangumiResults = result.data
                                selectedBangumiResult = result.data.firstOrNull()
                                bangumiStatus = bangumiSearchResultStatus(query, result.data.size)
                            }
                            is Result.Error -> bangumiStatus = result.error.toUserMessage()
                        }
                    }
                },
                onBatchPreview = {
                    scope.launch {
                        val sourceId = activeSourceId
                        if (sourceId == null) {
                            bangumiStatus = bangumiSourceRequiredStatus()
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
                            bangumiStatus = bangumiSourceRequiredStatus()
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
                                indexedEntries = indexedEntries.replaceEntries(write.updatedEntries)
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
                            bangumiStatus = bangumiSourceRequiredStatus()
                            return@launch
                        }
                        when (val restore = repositories.index.restoreMetadataBatchUndo(sourceId, bangumiBatchRollback)) {
                            is Result.Success -> {
                                if (restore.data.rollbackEntries.isEmpty()) {
                                    bangumiStatus = noMetadataBatchUndoStatus()
                                    return@launch
                                }
                                indexedEntries = indexedEntries.replaceEntries(restore.data.rollbackEntries)
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
                        var updatedMatches = bangumiBatchMatches.replaceBatchMatch(updatedMatch)
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
                            bangumiStatus = bangumiSourceRequiredStatus()
                            return@launch
                        }
                        if (match == null || result == null) {
                            bangumiStatus = bangumiBatchResultRequiredStatus()
                            return@launch
                        }
                        when (val entriesResult = repositories.index.queryIndex(sourceId, "")) {
                            is Result.Success -> {
                                val entries = entriesResult.data.filterNot { it.isDirectory }
                                val reviewed = match.copy(result = result.copy(confidence = 1f))
                                val plan = MetadataBatchPlanner.planFor(entries, listOf(reviewed))
                                if (plan.conflicts.isNotEmpty()) {
                                    bangumiStatus = plan.bangumiReviewConflictStatus()
                                    return@launch
                                }
                                if (plan.readyUpdates.isEmpty()) {
                                    bangumiStatus = bangumiReviewNoMatchStatus()
                                    return@launch
                                }
                                val write = repositories.index.applyMetadataBatchPlan(sourceId, plan)
                                bangumiBatchRollback = write.rollbackEntries
                                indexedEntries = indexedEntries.replaceEntries(write.updatedEntries)
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
                    bangumiStatus = result.selectedBangumiStatus()
                },
                onApply = {
                    scope.launch {
                        val sourceId = activeSourceId
                        val entry = selectedIndexEntry
                        val bangumi = selectedBangumiResult ?: bangumiResults.firstOrNull()
                        if (sourceId == null) {
                            bangumiStatus = bangumiSourceRequiredStatus()
                            return@launch
                        }
                        if (entry == null || entry.isDirectory) {
                            bangumiStatus = bangumiApplyEntryRequiredStatus()
                            return@launch
                        }
                        if (bangumi == null) {
                            bangumiStatus = bangumiSearchSelectionRequiredStatus()
                            return@launch
                        }
                        val updated = entry.withExternalMetadata(bangumi, sourceId = sourceId)
                        when (val result = repositories.index.upsertEntry(sourceId, updated)) {
                            is Result.Success -> {
                                indexedEntries = indexedEntries.replaceEntry(updated)
                                selectedIndexEntry = updated
                                bangumiStatus = updated.bangumiAppliedStatus()
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
                            bangumiStatus = bangumiSourceRequiredStatus()
                            return@launch
                        }
                        if (entry == null || entry.isDirectory) {
                            bangumiStatus = bangumiClearEntryRequiredStatus()
                            return@launch
                        }
                        val updated = entry.clearExternalMetadata(sourceId = sourceId)
                        when (val result = repositories.index.upsertEntry(sourceId, updated)) {
                            is Result.Success -> {
                                indexedEntries = indexedEntries.replaceEntry(updated)
                                selectedIndexEntry = updated
                                bangumiStatus = updated.bangumiClearedStatus()
                            }
                            is Result.Error -> bangumiStatus = result.error.toUserMessage()
                        }
                    }
                },
            )
            RecentPlaybackPanel(
                records = recentProgress,
                selectedRecord = selectedRecentProgress,
                status = recentStatus,
                onRefresh = {
                    scope.launch {
                        refreshRecentProgress()
                    }
                },
                onRecordSelected = { record ->
                    selectedRecentProgress = record
                    mediaPath = record.episodeId
                    startSeconds = recentResumeStartSeconds(record)
                    launchStatus = recentLoadedPlaybackStatus(record)
                },
                onClearSelected = {
                    scope.launch {
                        val selected = selectedRecentProgress
                        if (selected == null) {
                            recentStatus = recentRequiredStatus()
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
                schedulerStatus = schedulerStatus(cloudRssSchedulerState),
                linkedSourceLabel = linkedSourceLabel(savedSources, cloudLinkedSourceId),
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
                                cloudRssStatus = cloudRssConfigSavedMessage()
                            }
                            is Result.Error -> cloudRssStatus = result.error.toUserMessage()
                        }
                    }
                },
                onSaveCredentials = {
                    repositories.credentials.cloudDriveToken = cloudToken.trim().takeIf { it.isNotBlank() }
                    repositories.credentials.cloudDrivePassword = cloudPassword.takeIf { it.isNotBlank() }
                    cloudRssStatus = cloudDriveCredentialsSavedMessage()
                },
                onLoginCloudDrive = {
                    scope.launch {
                        val endpoint = cloudEndpointUrl.trim()
                        val user = cloudUsername.trim()
                        val pass = cloudPassword
                        if (endpoint.isBlank() || user.isBlank() || pass.isBlank()) {
                            cloudRssStatus = cloudDriveLoginRequiredMessage()
                            return@launch
                        }
                        cloudRssStatus = cloudDriveLoginStartedMessage()
                        when (val result = cloudRssEngine.login(endpoint, user, pass)) {
                            is Result.Success -> {
                                cloudToken = repositories.credentials.cloudDriveToken.orEmpty()
                                cloudRssStatus = cloudDriveLoginSucceededMessage()
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
                            cloudRssStatus = cloudDriveTokenRequiredMessage()
                            return@launch
                        }
                        cloudRssStatus = cloudDriveTokenValidationStartedMessage()
                        when (val result = cloudRssEngine.saveApiToken(endpoint, apiToken)) {
                            is Result.Success -> {
                                cloudToken = apiToken
                                cloudRssStatus = cloudDriveTokenVerifiedMessage(result.data)
                            }
                            is Result.Error -> cloudRssStatus = result.error.toUserMessage()
                        }
                    }
                },
                onClearCredentials = {
                    repositories.credentials.clearCloudDriveCredentials()
                    cloudToken = ""
                    cloudPassword = ""
                    cloudRssStatus = cloudDriveCredentialsClearedMessage()
                },
                onRunSync = {
                    scope.launch {
                        cloudRssStatus = cloudRssRunStartedMessage()
                        when (val result = cloudRssEngine.runOnce()) {
                            is Result.Success -> {
                                val message = cloudRssRunCompleteMessage(result.data)
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
                        cloudRssStatus = cloudRssSchedulerDisabledMessage()
                    } else {
                        cloudRssStatus = cloudRssSchedulerStartMessage(cloudRssScheduler.start())
                    }
                },
                onStopScheduler = {
                    cloudRssScheduler.stop()
                    cloudRssStatus = cloudRssSchedulerStoppedMessage()
                },
                onUseActiveScanSource = {
                    val sourceId = activeSourceId
                    val sourceInfo = savedSources.firstOrNull { it.id == sourceId }
                        ?: activeSource?.info?.takeIf { it.id == sourceId }
                    if (sourceId == null || sourceInfo == null) {
                        cloudRssStatus = cloudRssScanSourceRequiredMessage()
                    } else {
                        cloudLinkedSourceId = sourceId
                        cloudRssStatus = linkedCloudRssScanSourceMessage(sourceInfo)
                    }
                },
                onClearScanSource = {
                    cloudLinkedSourceId = null
                    cloudRssStatus = cloudRssScanSourceClearedMessage()
                },
                onSaveSubscription = {
                    scope.launch {
                        val url = rssUrl.trim()
                        if (url.isBlank()) {
                            cloudRssStatus = rssUrlRequiredMessage()
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
                            cloudRssStatus = rssUrlRequiredMessage()
                            return@launch
                        }
                        when (val result = repositories.cloudDriveAutomation.saveSubscription(subscription)) {
                            is Result.Success -> {
                                cloudRssStatus = rssSubscriptionSavedMessage(subscription)
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
                    cloudRssStatus = rssSubscriptionSelectedMessage(subscription)
                },
                onDeleteSubscription = {
                    scope.launch {
                        val subscription = selectedRssSubscription
                        if (subscription == null) {
                            cloudRssStatus = rssSubscriptionRequiredMessage()
                            return@launch
                        }
                        when (val result = repositories.cloudDriveAutomation.deleteSubscription(subscription.id)) {
                            is Result.Success -> {
                                selectedRssSubscription = null
                                rssName = ""
                                rssUrl = ""
                                rssFilter = ""
                                rssEnabled = true
                                cloudRssStatus = rssSubscriptionDeletedMessage()
                                refreshRssSubscriptions()
                            }
                            is Result.Error -> cloudRssStatus = result.error.toUserMessage()
                        }
                    }
                },
                    )
                }
                MiruPlayRouteSurface.player -> {
                    Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
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
                    modifier = Modifier.weight(0.62f),
                )
                RuntimePanel(
                    mpvPath = mpvPath,
                    onMpvPathChange = { mpvPath = it },
                    configDir = configDir,
                    onConfigDirChange = { configDir = it },
                    status = status,
                    onCheckRuntime = { status = runtimeStatus(mpvPath, configDir) },
                    modifier = Modifier.weight(0.38f),
                )
            }
            CommandPanel(
                commandPreview = commandPreview,
                launchStatus = launchStatus,
                onLaunch = {
                    scope.launch {
                        runCatching {
                            val config = buildRuntimeConfig(
                                mpvPath = mpvPath,
                                configDir = configDir,
                                fullscreen = fullscreen,
                                keepOpen = keepOpen,
                                rifeEnabled = rifeEnabled,
                                rifeBackend = rifeBackend,
                            )
                            val selectedMediaPath = mediaPath.trim()
                            val source = buildPlaybackSource(
                                mediaPath = playableUriFor(activeSource, playbackBridge, selectedMediaPath),
                                subtitlePath = subtitlePath,
                                startSeconds = startSeconds,
                                mediaSourceId = activeSourceId?.toString() ?: activeSource?.info?.type?.name ?: "desktop-compose",
                                episodeId = selectedMediaPath.ifBlank { null },
                            )
                            val nextPlayer = MpvProcessPlayer(config)
                            when (val result = nextPlayer.play(source)) {
                                is Result.Success -> {
                                    player = nextPlayer
                                    activePlaybackSession = DesktopPlaybackSession(selectedMediaPath, source.startPosition)
                                    repositories.progress.saveProgress(
                                        episodeId = selectedMediaPath,
                                        positionMs = source.startPosition,
                                        lastWatched = System.currentTimeMillis(),
                                        incrementPlayCount = true,
                                    )
                                    refreshRecentProgress()
                                    launchStatus = playbackLaunchedStatus(result.data)
                                }
                                is Result.Error -> launchStatus = result.error.toUserMessage()
                            }
                        }.onFailure { error ->
                            launchStatus = playbackLaunchFailedStatus(error)
                        }
                    }
                },
                onTogglePause = {
                    scope.launch {
                        val activePlayer = player
                        if (activePlayer == null) {
                            launchStatus = playbackNoActiveProcessStatus()
                            return@launch
                        }
                        when (val result = activePlayer.togglePause()) {
                            is Result.Success -> {
                                activePlaybackSession?.togglePaused()
                                launchStatus = playbackPauseToggledStatus()
                            }
                            is Result.Error -> launchStatus = result.error.toUserMessage()
                        }
                    }
                },
                onSeekBack = {
                    scope.launch {
                        val activePlayer = player
                        if (activePlayer == null) {
                            launchStatus = playbackNoActiveProcessStatus()
                            return@launch
                        }
                        when (val result = activePlayer.seekBy(-10.0)) {
                            is Result.Success -> {
                                activePlaybackSession?.seekBy(-10.0)
                                launchStatus = playbackSeekBackStatus(seconds = 10)
                            }
                            is Result.Error -> launchStatus = result.error.toUserMessage()
                        }
                    }
                },
                onSeekForward = {
                    scope.launch {
                        val activePlayer = player
                        if (activePlayer == null) {
                            launchStatus = playbackNoActiveProcessStatus()
                            return@launch
                        }
                        when (val result = activePlayer.seekBy(30.0)) {
                            is Result.Success -> {
                                activePlaybackSession?.seekBy(30.0)
                                launchStatus = playbackSeekForwardStatus(seconds = 30)
                            }
                            is Result.Error -> launchStatus = result.error.toUserMessage()
                        }
                    }
                },
                onStop = {
                    scope.launch {
                        val activePlayer = player
                        activePlaybackSession?.let { session ->
                            saveDesktopPlaybackProgressOnStop(
                                session = session,
                                queryPositionMs = activePlayer?.let { player ->
                                    { player.queryTimePositionMs() }
                                },
                                saveProgress = { episodeId, positionMs, lastWatched ->
                                    savePlaybackProgress(episodeId, positionMs, lastWatched)
                                },
                            )
                        }
                        activePlayer?.stop()
                        player = null
                        activePlaybackSession = null
                        refreshRecentProgress()
                        launchStatus = playbackStoppedStatus()
                    }
                },
                    )
                }
            }
        }
    }
}
