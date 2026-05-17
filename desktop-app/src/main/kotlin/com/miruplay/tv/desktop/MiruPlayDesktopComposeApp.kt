package com.miruplay.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.miruplay.tv.clouddrive.GrpcCloudDriveClient
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.design.MiruPlayPalette
import com.miruplay.tv.design.MiruPlayRouteSurface
import com.miruplay.tv.design.MiruPlayUiMetrics
import com.miruplay.tv.model.buildExternalSubtitleTracks
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssScheduler
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssSchedulerState
import com.miruplay.tv.mediasource.desktop.DesktopLocalMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopSmbMediaSource
import com.miruplay.tv.mediasource.desktop.DesktopWebDavMediaSource
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.formatFileSize
import com.miruplay.tv.model.formatPlaybackPosition
import com.miruplay.tv.player.mpv.MpvCommandBuilder
import com.miruplay.tv.player.mpv.MpvProcessPlayer
import com.miruplay.tv.player.mpv.MpvRuntimeConfig
import com.miruplay.tv.player.mpv.MpvRuntimeVerifier
import com.miruplay.tv.player.mpv.RifeBackend
import com.miruplay.tv.player.mpv.RifeInterpolationConfig
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MetadataBatchPlanner
import com.miruplay.tv.repository.displayName
import com.miruplay.tv.repository.desktop.DesktopRepositories
import com.miruplay.tv.scanner.desktop.DesktopMediaLibraryScanner
import com.miruplay.tv.scraper.desktop.DesktopBangumiScraper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.awt.Dimension
import java.nio.file.Paths
import kotlin.math.roundToLong

private val AnimeRed = Color(MiruPlayPalette.ANIME_RED_ARGB)
private val DarkBg = Color(MiruPlayPalette.DARK_BG_ARGB)
private val DarkSurface = Color(MiruPlayPalette.DARK_SURFACE_ARGB)
private val AccentBlue = Color(MiruPlayPalette.ACCENT_BLUE_ARGB)
private val TextPrimary = Color(MiruPlayPalette.TEXT_PRIMARY_ARGB)
private val TextSecondary = Color(MiruPlayPalette.TEXT_SECONDARY_ARGB)
private val CardBg = Color(MiruPlayPalette.CARD_BG_ARGB)
private const val COMPOSE_BATCH_BANGUMI_QUERY_LIMIT = 20
private const val PLAYBACK_PROGRESS_POLL_INTERVAL_MS = 10_000L
private typealias DesktopSection = MiruPlayRouteSurface.Section

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
    var libraryStatus by remember { mutableStateOf("Add a local library source or load an existing one.") }
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
    var remoteStatus by remember { mutableStateOf("Open a WebDAV or SMB source to browse it.") }
    var bangumiQuery by remember { mutableStateOf("") }
    var bangumiResults by remember { mutableStateOf(emptyList<ScraperResult>()) }
    var selectedBangumiResult by remember { mutableStateOf<ScraperResult?>(null) }
    var bangumiBatchMatches by remember { mutableStateOf(emptyList<DesktopBangumiBatchMatch>()) }
    var selectedBangumiBatchMatch by remember { mutableStateOf<DesktopBangumiBatchMatch?>(null) }
    var bangumiBatchPlan by remember { mutableStateOf<DesktopBangumiBatchPlan?>(null) }
    var bangumiBatchRollback by remember { mutableStateOf(emptyList<MediaIndexEntry>()) }
    var bangumiStatus by remember { mutableStateOf("Select an indexed video, then search Bangumi.") }
    var recentProgress by remember { mutableStateOf(emptyList<ProgressRecord>()) }
    var selectedRecentProgress by remember { mutableStateOf<ProgressRecord?>(null) }
    var recentStatus by remember { mutableStateOf("No recent playback loaded.") }
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
    var cloudRssStatus by remember { mutableStateOf("Load or save Cloud/RSS automation settings.") }
    var mediaPath by remember { mutableStateOf("") }
    var subtitlePath by remember { mutableStateOf("") }
    var startSeconds by remember { mutableStateOf("0") }
    var fullscreen by remember { mutableStateOf(false) }
    var keepOpen by remember { mutableStateOf(false) }
    var rifeEnabled by remember { mutableStateOf(true) }
    var rifeBackend by remember { mutableStateOf(RifeBackend.NVIDIA) }
    var status by remember { mutableStateOf(runtimeStatus(mpvPath, configDir)) }
    var launchStatus by remember { mutableStateOf("mpv is idle.") }
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
                    libraryStatus = "Loaded local source: ${local.name}"
                }
                if (webDav != null) {
                    webDavUrl = webDav.connectionInfo["url"].orEmpty()
                    webDavUsername = webDav.connectionInfo["username"].orEmpty()
                    webDavPassword = webDav.connectionInfo["password"].orEmpty()
                    if (local == null) {
                        activeSourceId = webDav.id
                        activeSource = desktopWebDavSourceFromInfo(webDav)
                        remoteStatus = "Loaded WebDAV source: ${webDav.name}"
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
                        remoteStatus = "Loaded SMB source: ${smb.name}"
                    }
                }
            }
            is Result.Error -> libraryStatus = sources.error.toUserMessage()
        }
        when (val recents = repositories.progress.getContinueWatching(limit = 12)) {
            is Result.Success -> {
                recentProgress = recents.data
                recentStatus = if (recents.data.isEmpty()) {
                    "No recent playback yet."
                } else {
                    "Loaded ${recents.data.size} recent item(s)."
                }
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
            cloudRssStatus = if (subscriptions.isEmpty()) {
                "No RSS subscriptions configured."
            } else {
                "Loaded ${subscriptions.size} RSS subscription(s)."
            }
        }.onFailure { error ->
            cloudRssStatus = error.message ?: "Failed to load RSS subscriptions."
        }
    }

    suspend fun loadRemoteDirectory(source: DesktopMediaSource, path: String) {
        remoteStatus = "Loading ${source.info.type.name} ${path.ifBlank { "/" }}..."
        when (val result = source.listFiles(path)) {
            is Result.Success -> {
                remotePath = path
                remoteEntries = result.data
                selectedRemoteEntry = null
                remoteStatus = "Showing ${result.data.size} item(s) from ${source.info.name}."
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
                recentStatus = if (recents.data.isEmpty()) {
                    "No recent playback yet."
                } else {
                    "Showing ${recents.data.size} recent item(s)."
                }
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
                        launchStatus = "mpv position synced at ${formatPlaybackPosition(positionMs)}."
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
            cloudRssStatus = if (subscriptions.isEmpty()) {
                "No RSS subscriptions configured."
            } else {
                "Showing ${subscriptions.size} RSS subscription(s)."
            }
        }.onFailure { error ->
            cloudRssStatus = error.message ?: "Failed to refresh RSS subscriptions."
        }
    }

    suspend fun scanCurrentSource(updateStatus: (String) -> Unit) {
        val sourceId = activeSourceId
        val source = activeSource ?: activeLocalSource
        if (sourceId == null || source == null) {
            updateStatus("Open a source before scanning.")
            return
        }
        updateStatus("Scanning ${source.info.name}...")
        when (val scan = DesktopMediaLibraryScanner().scan(sourceId, source)) {
            is Result.Success -> {
                when (val indexed = repositories.index.rebuildIndex(sourceId, scan.data.entries)) {
                    is Result.Success -> {
                        indexedEntries = scan.data.entries.filterNot { it.isDirectory }.take(24)
                        selectedIndexEntry = null
                        val message = "Scan complete: ${scan.data.filesIndexed} videos, ${scan.data.directoriesVisited} directories."
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
            cloudRssStatus = "Linked scan source was not found. Clear or relink the Cloud/RSS scan source."
            return null
        }

        cloudRssStatus = "$reason Rescanning ${sourceInfo.name}..."
        val source = desktopSourceFromInfo(sourceInfo)
        return when (val scan = DesktopMediaLibraryScanner().scan(sourceInfo.id, source)) {
            is Result.Success -> {
                when (val indexed = repositories.index.rebuildIndex(sourceInfo.id, scan.data.entries)) {
                    is Result.Success -> {
                        if (activeSourceId == sourceInfo.id) {
                            indexedEntries = scan.data.entries.filterNot { it.isDirectory }.take(24)
                            selectedIndexEntry = null
                        }
                        val message = "Rescan complete: ${scan.data.filesIndexed} videos, ${scan.data.directoriesVisited} directories."
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
                libraryStatus = "Loaded saved local source: ${sourceInfo.name}"
            }
            MediaSourceType.WEBDAV -> {
                webDavUrl = sourceInfo.connectionInfo["url"].orEmpty()
                webDavUsername = sourceInfo.connectionInfo["username"].orEmpty()
                webDavPassword = sourceInfo.connectionInfo["password"].orEmpty()
                remotePath = ""
                remoteStatus = "Loaded saved WebDAV source: ${sourceInfo.name}"
                loadRemoteDirectory(source, "")
            }
            MediaSourceType.SMB -> {
                smbUrl = sourceInfo.connectionInfo["url"].orEmpty()
                smbDomain = sourceInfo.connectionInfo["domain"].orEmpty()
                smbUsername = sourceInfo.connectionInfo["username"].orEmpty()
                smbPassword = sourceInfo.connectionInfo["password"].orEmpty()
                remotePath = ""
                remoteStatus = "Loaded saved SMB source: ${sourceInfo.name}"
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
                            libraryStatus = "Enter a local library root first."
                            return@launch
                        }
                        val root = Paths.get(rootText).toAbsolutePath().normalize()
                        val sourceInfo = MediaSourceInfo(
                            name = root.fileName?.toString() ?: root.toString(),
                            type = MediaSourceType.LOCAL,
                            connectionInfo = mapOf("path" to root.toString()),
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
                                libraryStatus = "Local source ready: ${stored.name}"
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
                            libraryStatus = "Open or scan a source before searching."
                            return@launch
                        }
                        when (val result = repositories.index.queryIndex(sourceId, indexQuery.trim())) {
                            is Result.Success -> {
                                indexedEntries = result.data.filterNot { it.isDirectory }.take(24)
                                libraryStatus = if (result.data.isEmpty()) {
                                    "No indexed media matched \"${indexQuery.trim()}\"."
                                } else {
                                    "Showing ${indexedEntries.size} indexed video result(s)."
                                }
                            }
                            is Result.Error -> libraryStatus = result.error.toUserMessage()
                        }
                    }
                },
                onClearIndex = {
                    scope.launch {
                        val sourceId = activeSourceId
                        if (sourceId == null) {
                            libraryStatus = "Open or scan a source before clearing its index."
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
                                libraryStatus = "Index cleared for source id: $sourceId."
                                bangumiStatus = "Select an indexed video, then search Bangumi."
                            }
                            is Result.Error -> libraryStatus = result.error.toUserMessage()
                        }
                    }
                },
                onRemoveSource = {
                    scope.launch {
                        val sourceId = activeSourceId
                        if (sourceId == null) {
                            libraryStatus = "Open a source before removing it."
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
                                libraryStatus = "Source removed. Associated index entries were cleared."
                                remoteStatus = "Open a WebDAV or SMB source to browse it."
                                bangumiStatus = "Select an indexed video, then search Bangumi."
                            }
                            is Result.Error -> libraryStatus = result.error.toUserMessage()
                        }
                    }
                },
                onEntrySelected = { entry ->
                    selectedIndexEntry = entry
                    mediaPath = entry.path
                    launchStatus = "Selected ${entry.displayName()} for playback."
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
                            remoteStatus = "Enter a WebDAV URL first."
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
                                remoteStatus = "WebDAV source ready: ${stored.name}"
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
                            remoteStatus = "Enter an SMB URL first."
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
                                remoteStatus = "SMB source ready: ${stored.name}"
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
                        remoteStatus = "Already at the source root."
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
                        remoteStatus = "Open a remote source before browsing."
                    } else {
                        mediaPath = entry.path
                        launchStatus = "Selected remote media: ${entry.name}. mpv will stream through the local bridge."
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
                        bangumiStatus = "Select an indexed video first."
                    } else {
                        bangumiQuery = query
                        bangumiStatus = "Query set from selected index entry."
                    }
                },
                onSearch = {
                    scope.launch {
                        val query = bangumiQuery.trim().ifBlank {
                            bangumiQueryFor(selectedIndexEntry).orEmpty()
                        }
                        if (query.isBlank()) {
                            bangumiStatus = "Enter a Bangumi query or select an indexed video."
                            return@launch
                        }
                        bangumiQuery = query
                        bangumiStatus = "Searching Bangumi for \"$query\"..."
                        when (val result = bangumiScraper.searchAnime(query)) {
                            is Result.Success -> {
                                bangumiResults = result.data
                                selectedBangumiResult = result.data.firstOrNull()
                                bangumiStatus = if (result.data.isEmpty()) {
                                    "No Bangumi metadata matched \"$query\"."
                                } else {
                                    "Found ${result.data.size} Bangumi match(es)."
                                }
                            }
                            is Result.Error -> bangumiStatus = result.error.toUserMessage()
                        }
                    }
                },
                onBatchPreview = {
                    scope.launch {
                        val sourceId = activeSourceId
                        if (sourceId == null) {
                            bangumiStatus = "Open or scan a source first."
                            return@launch
                        }
                        when (val entriesResult = repositories.index.queryIndex(sourceId, "")) {
                            is Result.Success -> {
                                val entries = entriesResult.data.filterNot { it.isDirectory }
                                val queries = MetadataBatchPlanner.queriesFor(entries)
                                    .take(COMPOSE_BATCH_BANGUMI_QUERY_LIMIT)
                                if (queries.isEmpty()) {
                                    bangumiBatchMatches = emptyList()
                                    selectedBangumiBatchMatch = null
                                    bangumiBatchPlan = null
                                    bangumiStatus = "No indexed entries are available for Bangumi batch matching."
                                    return@launch
                                }

                                bangumiStatus = "Searching Bangumi for ${queries.size} indexed title(s)..."
                                val matches = queries.map { query ->
                                    val candidates = bangumiScraper.searchAnime(query).getOrNull().orEmpty()
                                    DesktopBangumiBatchMatch(
                                        query = query,
                                        result = candidates.firstOrNull(),
                                        candidates = candidates,
                                    )
                                }
                                bangumiBatchMatches = matches
                                val plan = MetadataBatchPlanner.planFor(entries, matches)
                                bangumiBatchPlan = plan
                                selectedBangumiBatchMatch = plan.reviewMatches.firstOrNull { it.result != null }
                                    ?: matches.firstOrNull()
                                bangumiStatus = MetadataBatchPlanner.displayPlanSummary(plan)
                            }
                            is Result.Error -> bangumiStatus = entriesResult.error.toUserMessage()
                        }
                    }
                },
                onBatchApply = {
                    scope.launch {
                        val sourceId = activeSourceId
                        if (sourceId == null) {
                            bangumiStatus = "Open or scan a source first."
                            return@launch
                        }
                        if (bangumiBatchMatches.isEmpty()) {
                            bangumiStatus = "Run Batch preview first; no high-confidence matches are ready."
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
                                val updatedEntries = mutableListOf<MediaIndexEntry>()
                                val rollback = mutableListOf<MediaIndexEntry>()
                                plan.readyUpdates.forEach { update ->
                                    when (repositories.index.upsertEntry(sourceId, update.updated.copy(sourceId = sourceId))) {
                                        is Result.Success -> {
                                            rollback += update.original.copy(sourceId = sourceId)
                                            updatedEntries += update.updated.copy(sourceId = sourceId)
                                        }
                                        is Result.Error -> Unit
                                    }
                                }
                                val rollbackEntries = rollback.distinctBy { it.path }
                                bangumiBatchRollback = rollbackEntries
                                repositories.index.saveLastBatchUndo(sourceId, rollbackEntries)
                                indexedEntries = indexedEntries.replaceEntries(updatedEntries)
                                selectedIndexEntry = selectedIndexEntry?.let { selected ->
                                    updatedEntries.firstOrNull { it.path == selected.path && it.sourceId == selected.sourceId }
                                        ?: selected
                                }
                                bangumiStatus = "Applied Bangumi batch metadata to ${updatedEntries.size} index entr${if (updatedEntries.size == 1) "y" else "ies"}; ${plan.conflicts.size} conflict${if (plan.conflicts.size == 1) "" else "s"} skipped."
                            }
                            is Result.Error -> bangumiStatus = entriesResult.error.toUserMessage()
                        }
                    }
                },
                onBatchUndo = {
                    scope.launch {
                        val sourceId = activeSourceId
                        if (sourceId == null) {
                            bangumiStatus = "Open or scan a source first."
                            return@launch
                        }
                        val rollback = if (bangumiBatchRollback.isNotEmpty()) {
                            bangumiBatchRollback
                        } else {
                            when (val saved = repositories.index.getLastBatchUndo(sourceId)) {
                                is Result.Success -> saved.data
                                is Result.Error -> {
                                    bangumiStatus = saved.error.toUserMessage()
                                    return@launch
                                }
                            }
                        }
                        if (rollback.isEmpty()) {
                            bangumiStatus = "No batch Bangumi changes are available to undo."
                            return@launch
                        }
                        var restoredCount = 0
                        rollback.forEach { entry ->
                            when (repositories.index.upsertEntry(sourceId, entry.copy(sourceId = sourceId))) {
                                is Result.Success -> restoredCount += 1
                                is Result.Error -> Unit
                            }
                        }
                        indexedEntries = indexedEntries.replaceEntries(rollback)
                        selectedIndexEntry = selectedIndexEntry?.let { selected ->
                            rollback.firstOrNull { it.path == selected.path && it.sourceId == selected.sourceId }
                                ?: selected
                        }
                        bangumiBatchRollback = emptyList()
                        repositories.index.clearLastBatchUndo(sourceId)
                        bangumiStatus = "Restored $restoredCount index entr${if (restoredCount == 1) "y" else "ies"} from the previous Bangumi batch."
                    }
                },
                onBatchMatchSelected = { match ->
                    selectedBangumiBatchMatch = match
                    bangumiQuery = match.query
                    selectedBangumiResult = match.result
                    bangumiStatus = "Selected batch review: ${match.query}."
                },
                onBatchCandidateSelected = { match, candidate ->
                    scope.launch {
                        val updatedMatch = match.withSelectedCandidate(candidate)
                        val updatedMatches = bangumiBatchMatches.replaceBatchMatch(updatedMatch)
                        bangumiBatchMatches = updatedMatches
                        selectedBangumiBatchMatch = updatedMatch
                        selectedBangumiResult = candidate
                        bangumiQuery = match.query

                        val sourceId = activeSourceId
                        if (sourceId != null) {
                            when (val entriesResult = repositories.index.queryIndex(sourceId, "")) {
                                is Result.Success -> {
                                    bangumiBatchPlan = MetadataBatchPlanner.planFor(
                                        entries = entriesResult.data.filterNot { it.isDirectory },
                                        matches = updatedMatches,
                                    )
                                }
                                is Result.Error -> {
                                    bangumiStatus = entriesResult.error.toUserMessage()
                                    return@launch
                                }
                            }
                        }
                        bangumiStatus = "Selected batch candidate for ${match.query}: ${bangumiDisplayTitle(candidate)}."
                    }
                },
                onBatchAcceptReview = {
                    scope.launch {
                        val sourceId = activeSourceId
                        val match = selectedBangumiBatchMatch
                        val result = match?.result
                        if (sourceId == null) {
                            bangumiStatus = "Open or scan a source first."
                            return@launch
                        }
                        if (match == null || result == null) {
                            bangumiStatus = "Select a batch match with a Bangumi result first."
                            return@launch
                        }
                        when (val entriesResult = repositories.index.queryIndex(sourceId, "")) {
                            is Result.Success -> {
                                val entries = entriesResult.data.filterNot { it.isDirectory }
                                val reviewed = match.copy(result = result.copy(confidence = 1f))
                                val plan = MetadataBatchPlanner.planFor(entries, listOf(reviewed))
                                if (plan.conflicts.isNotEmpty()) {
                                    bangumiStatus = "Selected review has ${plan.conflicts.size} metadata conflict${if (plan.conflicts.size == 1) "" else "s"}; nothing was overwritten."
                                    return@launch
                                }
                                if (plan.readyUpdates.isEmpty()) {
                                    bangumiStatus = "Selected review has no matching indexed entries."
                                    return@launch
                                }
                                val rollback = mutableListOf<MediaIndexEntry>()
                                val updatedEntries = mutableListOf<MediaIndexEntry>()
                                plan.readyUpdates.forEach { update ->
                                    when (repositories.index.upsertEntry(sourceId, update.updated.copy(sourceId = sourceId))) {
                                        is Result.Success -> {
                                            rollback += update.original.copy(sourceId = sourceId)
                                            updatedEntries += update.updated.copy(sourceId = sourceId)
                                        }
                                        is Result.Error -> Unit
                                    }
                                }
                                val rollbackEntries = rollback.distinctBy { it.path }
                                bangumiBatchRollback = rollbackEntries
                                repositories.index.saveLastBatchUndo(sourceId, rollbackEntries)
                                indexedEntries = indexedEntries.replaceEntries(updatedEntries)
                                selectedIndexEntry = selectedIndexEntry?.let { selected ->
                                    updatedEntries.firstOrNull { it.path == selected.path && it.sourceId == selected.sourceId }
                                        ?: selected
                                }
                                bangumiStatus = "Accepted reviewed Bangumi match for ${updatedEntries.size} index entr${if (updatedEntries.size == 1) "y" else "ies"}."
                            }
                            is Result.Error -> bangumiStatus = entriesResult.error.toUserMessage()
                        }
                    }
                },
                onResultSelected = { result ->
                    selectedBangumiResult = result
                    bangumiStatus = "Selected ${bangumiDisplayTitle(result)}."
                },
                onApply = {
                    scope.launch {
                        val sourceId = activeSourceId
                        val entry = selectedIndexEntry
                        val bangumi = selectedBangumiResult ?: bangumiResults.firstOrNull()
                        if (sourceId == null) {
                            bangumiStatus = "Open or scan a source first."
                            return@launch
                        }
                        if (entry == null || entry.isDirectory) {
                            bangumiStatus = "Select an indexed video before applying Bangumi metadata."
                            return@launch
                        }
                        if (bangumi == null) {
                            bangumiStatus = "Search Bangumi and select a match first."
                            return@launch
                        }
                        val updated = entry.copy(
                            sourceId = sourceId,
                            animeName = bangumiDisplayTitle(bangumi),
                            metadataSource = bangumi.source.name,
                            metadataId = bangumi.animeId,
                            metadataTitle = bangumiDisplayTitle(bangumi),
                        )
                        when (val result = repositories.index.upsertEntry(sourceId, updated)) {
                            is Result.Success -> {
                                indexedEntries = indexedEntries.replaceEntry(updated)
                                selectedIndexEntry = updated
                                bangumiStatus = "Applied Bangumi metadata to ${updated.path}."
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
                            bangumiStatus = "Open or scan a source first."
                            return@launch
                        }
                        if (entry == null || entry.isDirectory) {
                            bangumiStatus = "Select an indexed video before clearing metadata."
                            return@launch
                        }
                        val updated = entry.copy(
                            sourceId = sourceId,
                            metadataSource = null,
                            metadataId = null,
                            metadataTitle = null,
                        )
                        when (val result = repositories.index.upsertEntry(sourceId, updated)) {
                            is Result.Success -> {
                                indexedEntries = indexedEntries.replaceEntry(updated)
                                selectedIndexEntry = updated
                                bangumiStatus = "Cleared external metadata for ${updated.path}."
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
                    startSeconds = (record.positionMs / 1_000L).toString()
                    launchStatus = "Loaded recent playback: ${recentDisplayName(record)}."
                },
                onClearSelected = {
                    scope.launch {
                        val selected = selectedRecentProgress
                        if (selected == null) {
                            recentStatus = "Select a recent item first."
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
                        val interval = cloudIntervalMinutes.toIntOrNull()?.coerceAtLeast(1) ?: 30
                        val proxyPort = rssProxyPort.toIntOrNull()?.coerceIn(1, 65_535) ?: 1080
                        val config = CloudDriveAutomationConfig(
                            endpointUrl = cloudEndpointUrl.trim(),
                            username = cloudUsername.trim(),
                            webDavSourceId = cloudLinkedSourceId,
                            inboxPath = cloudInboxPath.trim(),
                            libraryPath = cloudLibraryPath.trim(),
                            intervalMinutes = interval,
                            enabled = cloudEnabled,
                            rssProxyEnabled = rssProxyEnabled,
                            rssProxyHost = rssProxyHost.trim(),
                            rssProxyPort = proxyPort,
                        )
                        when (val result = repositories.cloudDriveAutomation.saveConfig(config)) {
                            is Result.Success -> {
                                cloudIntervalMinutes = interval.toString()
                                rssProxyPort = proxyPort.toString()
                                cloudRssStatus = "Cloud/RSS automation settings saved."
                            }
                            is Result.Error -> cloudRssStatus = result.error.toUserMessage()
                        }
                    }
                },
                onSaveCredentials = {
                    repositories.credentials.cloudDriveToken = cloudToken.trim().takeIf { it.isNotBlank() }
                    repositories.credentials.cloudDrivePassword = cloudPassword.takeIf { it.isNotBlank() }
                    cloudRssStatus = "CloudDrive credentials saved."
                },
                onLoginCloudDrive = {
                    scope.launch {
                        val endpoint = cloudEndpointUrl.trim()
                        val user = cloudUsername.trim()
                        val pass = cloudPassword
                        if (endpoint.isBlank() || user.isBlank() || pass.isBlank()) {
                            cloudRssStatus = "Enter CloudDrive2 endpoint, username, and password first."
                            return@launch
                        }
                        cloudRssStatus = "Logging into CloudDrive2..."
                        when (val result = cloudRssEngine.login(endpoint, user, pass)) {
                            is Result.Success -> {
                                cloudToken = repositories.credentials.cloudDriveToken.orEmpty()
                                cloudRssStatus = "CloudDrive2 login succeeded; token saved."
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
                            cloudRssStatus = "Enter CloudDrive2 endpoint and API token first."
                            return@launch
                        }
                        cloudRssStatus = "Validating CloudDrive2 API token..."
                        when (val result = cloudRssEngine.saveApiToken(endpoint, apiToken)) {
                            is Result.Success -> {
                                cloudToken = apiToken
                                val label = result.data.friendlyName.takeIf { it.isNotBlank() }
                                    ?: result.data.rootDir.ifBlank { "CloudDrive2" }
                                cloudRssStatus = "CloudDrive2 API token verified and saved: $label."
                            }
                            is Result.Error -> cloudRssStatus = result.error.toUserMessage()
                        }
                    }
                },
                onClearCredentials = {
                    repositories.credentials.clearCloudDriveCredentials()
                    cloudToken = ""
                    cloudPassword = ""
                    cloudRssStatus = "CloudDrive credentials cleared."
                },
                onRunSync = {
                    scope.launch {
                        cloudRssStatus = "Running Cloud/RSS sync..."
                        when (val result = cloudRssEngine.runOnce()) {
                            is Result.Success -> {
                                val summary = result.data
                                val message = "Sync complete: ${summary.submitted} submitted, ${summary.skipped} skipped, ${summary.failed} failed, ${summary.organized} organized."
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
                        cloudRssStatus = "Enable and save Cloud/RSS sync before starting the scheduler."
                    } else if (cloudRssScheduler.start()) {
                        cloudRssStatus = "Cloud/RSS scheduler started."
                    } else {
                        cloudRssStatus = "Cloud/RSS scheduler is already running."
                    }
                },
                onStopScheduler = {
                    cloudRssScheduler.stop()
                    cloudRssStatus = "Cloud/RSS scheduler stopped."
                },
                onUseActiveScanSource = {
                    val sourceId = activeSourceId
                    val sourceInfo = savedSources.firstOrNull { it.id == sourceId }
                        ?: activeSource?.info?.takeIf { it.id == sourceId }
                    if (sourceId == null || sourceInfo == null) {
                        cloudRssStatus = "Open a saved media source before linking Cloud/RSS scanning."
                    } else {
                        cloudLinkedSourceId = sourceId
                        cloudRssStatus = "Linked Cloud/RSS post-sync scan source: ${sourceInfo.name}. Save sync config to persist it."
                    }
                },
                onClearScanSource = {
                    cloudLinkedSourceId = null
                    cloudRssStatus = "Cloud/RSS post-sync scan source cleared. Save sync config to persist it."
                },
                onSaveSubscription = {
                    scope.launch {
                        val url = rssUrl.trim()
                        if (url.isBlank()) {
                            cloudRssStatus = "Enter an RSS URL first."
                            return@launch
                        }
                        val subscription = RssSubscriptionInfo(
                            id = selectedRssSubscription?.takeIf { it.url == url }?.id ?: 0L,
                            name = rssName.trim().ifBlank { url },
                            url = url,
                            filterRegex = rssFilter.trim().takeIf { it.isNotBlank() },
                            enabled = rssEnabled,
                            lastCheckedAt = selectedRssSubscription?.takeIf { it.url == url }?.lastCheckedAt ?: 0L,
                        )
                        when (val result = repositories.cloudDriveAutomation.saveSubscription(subscription)) {
                            is Result.Success -> {
                                cloudRssStatus = "RSS subscription saved: ${subscription.name}"
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
                    cloudRssStatus = "Selected RSS subscription: ${subscription.name}"
                },
                onDeleteSubscription = {
                    scope.launch {
                        val subscription = selectedRssSubscription
                        if (subscription == null) {
                            cloudRssStatus = "Select an RSS subscription first."
                            return@launch
                        }
                        when (val result = repositories.cloudDriveAutomation.deleteSubscription(subscription.id)) {
                            is Result.Success -> {
                                selectedRssSubscription = null
                                rssName = ""
                                rssUrl = ""
                                rssFilter = ""
                                rssEnabled = true
                                cloudRssStatus = "RSS subscription deleted."
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
                                    launchStatus = "mpv launched: pid ${result.data.pid}"
                                }
                                is Result.Error -> launchStatus = result.error.toUserMessage()
                            }
                        }.onFailure { error ->
                            launchStatus = error.message ?: "Unable to launch mpv."
                        }
                    }
                },
                onTogglePause = {
                    scope.launch {
                        val activePlayer = player
                        if (activePlayer == null) {
                            launchStatus = "No mpv process is active."
                            return@launch
                        }
                        when (val result = activePlayer.togglePause()) {
                            is Result.Success -> {
                                activePlaybackSession?.togglePaused()
                                launchStatus = "mpv pause toggled."
                            }
                            is Result.Error -> launchStatus = result.error.toUserMessage()
                        }
                    }
                },
                onSeekBack = {
                    scope.launch {
                        val activePlayer = player
                        if (activePlayer == null) {
                            launchStatus = "No mpv process is active."
                            return@launch
                        }
                        when (val result = activePlayer.seekBy(-10.0)) {
                            is Result.Success -> {
                                activePlaybackSession?.seekBy(-10.0)
                                launchStatus = "mpv seeked back 10s."
                            }
                            is Result.Error -> launchStatus = result.error.toUserMessage()
                        }
                    }
                },
                onSeekForward = {
                    scope.launch {
                        val activePlayer = player
                        if (activePlayer == null) {
                            launchStatus = "No mpv process is active."
                            return@launch
                        }
                        when (val result = activePlayer.seekBy(30.0)) {
                            is Result.Success -> {
                                activePlaybackSession?.seekBy(30.0)
                                launchStatus = "mpv seeked forward 30s."
                            }
                            is Result.Error -> launchStatus = result.error.toUserMessage()
                        }
                    }
                },
                onStop = {
                    scope.launch {
                        val activePlayer = player
                        activePlaybackSession?.let { session ->
                            val syncedPosition = activePlayer?.let { player ->
                                when (
                                    val synced = syncPlaybackProgressFromMpv(
                                        session = session,
                                        queryPositionMs = { player.queryTimePositionMs() },
                                        saveProgress = { episodeId, positionMs, lastWatched ->
                                            savePlaybackProgress(episodeId, positionMs, lastWatched)
                                        },
                                    )
                                ) {
                                    is Result.Success -> synced.data
                                    is Result.Error -> null
                                }
                            }
                            if (syncedPosition == null) {
                                savePlaybackProgress(
                                    episodeId = session.episodeId,
                                    positionMs = session.currentPositionMs(),
                                )
                            }
                        }
                        activePlayer?.stop()
                        player = null
                        activePlaybackSession = null
                        refreshRecentProgress()
                        launchStatus = "mpv stopped."
                    }
                },
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopTvNavigation(
    selectedSection: DesktopSection,
    onSectionSelected: (DesktopSection) -> Unit,
) {
    TvPanel(
        modifier = Modifier
            .width(MiruPlayUiMetrics.NAV_RAIL_WIDTH_DP.dp)
            .fillMaxHeight(),
    ) {
        Text("MiruPlay", color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_TITLE_SP.sp, fontWeight = FontWeight.Bold)
        Text("Desktop", color = TextSecondary, fontSize = MiruPlayUiMetrics.SECTION_LEAD_SP.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height((MiruPlayUiMetrics.NAV_ITEM_GAP_DP * 1.5f).dp))
        MiruPlayRouteSurface.desktopSectionOrder.forEach { section ->
            val selected = section == selectedSection
            Button(
                onClick = { onSectionSelected(section) },
                shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) AnimeRed else Color.Transparent,
                    contentColor = if (selected) Color.White else TextPrimary,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, focusedElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MiruPlayUiMetrics.NAV_ITEM_HEIGHT_DP.dp)
                    .border(
                        width = 1.dp,
                        color = if (selected) AnimeRed else Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA),
                        shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
                    ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        section.menuLabel,
                        color = if (selected) Color.White else TextPrimary,
                        fontSize = MiruPlayUiMetrics.ACTION_BUTTON_TEXT_SP.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                    Text(
                        section.summary,
                        color = if (selected) Color.White.copy(alpha = 0.76f) else TextSecondary,
                        fontSize = MiruPlayUiMetrics.SECTION_SMALL_SP.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(MiruPlayUiMetrics.NAV_ITEM_GAP_DP.dp))
        }
    }
}

@Composable
private fun DesktopTvHeader(selectedSection: DesktopSection) {
    Column {
        Text(selectedSection.title, color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_TITLE_SP.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            selectedSection.subtitle,
            color = TextSecondary,
            fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp,
        )
    }
}

@Composable
private fun LibraryPanel(
    libraryRoot: String,
    onLibraryRootChange: (String) -> Unit,
    savedSources: List<MediaSourceInfo>,
    activeSourceId: Long?,
    onSavedSourceSelected: (MediaSourceInfo) -> Unit,
    indexQuery: String,
    onIndexQueryChange: (String) -> Unit,
    entries: List<MediaIndexEntry>,
    selectedEntry: MediaIndexEntry?,
    status: String,
    onOpenLocal: () -> Unit,
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onClearIndex: () -> Unit,
    onRemoveSource: () -> Unit,
    onEntrySelected: (MediaIndexEntry) -> Unit,
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.38f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                Text("Library", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Local sources flow into the same index used by playback.",
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp,
                    lineHeight = 20.sp,
                )
                LabeledTextField("Local library root", libraryRoot, onValueChange = onLibraryRootChange)
                SavedSourcePicker(
                    sources = savedSources,
                    activeSourceId = activeSourceId,
                    onSelected = onSavedSourceSelected,
                )
                LabeledTextField("Index query", indexQuery, onValueChange = onIndexQueryChange)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    TvActionButton("Open local", onClick = onOpenLocal, modifier = Modifier.weight(1f))
                    TvActionButton("Scan", onClick = onScan, secondary = true, modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    TvActionButton("Search", onClick = onSearch, secondary = true, modifier = Modifier.weight(1f))
                    TvActionButton("Clear index", onClick = onClearIndex, secondary = true, modifier = Modifier.weight(1f))
                }
                TvActionButton(
                    "Remove source",
                    onClick = onRemoveSource,
                    secondary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                StatusBox(status)
            }
            Column(
                modifier = Modifier.weight(0.62f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                Text("Indexed videos", color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                if (entries.isEmpty()) {
                    DesktopEmptyState("Scan or search to show indexed episodes.")
                } else {
                    entries.take(8).forEach { entry ->
                        IndexedMediaRow(
                            entry = entry,
                            selected = selectedEntry?.path == entry.path,
                            onClick = { onEntrySelected(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexedMediaRow(
    entry: MediaIndexEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DesktopSelectableRow(selected = selected, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                entry.displayName(),
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
private fun RemoteSourcesPanel(
    webDavUrl: String,
    onWebDavUrlChange: (String) -> Unit,
    webDavUsername: String,
    onWebDavUsernameChange: (String) -> Unit,
    webDavPassword: String,
    onWebDavPasswordChange: (String) -> Unit,
    smbUrl: String,
    onSmbUrlChange: (String) -> Unit,
    smbDomain: String,
    onSmbDomainChange: (String) -> Unit,
    smbUsername: String,
    onSmbUsernameChange: (String) -> Unit,
    smbPassword: String,
    onSmbPasswordChange: (String) -> Unit,
    remotePath: String,
    entries: List<FileEntry>,
    selectedEntry: FileEntry?,
    status: String,
    onOpenWebDav: () -> Unit,
    onOpenSmb: () -> Unit,
    onUp: () -> Unit,
    onScan: () -> Unit,
    onEntrySelected: (FileEntry) -> Unit,
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.42f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                Text("Remote sources", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                LabeledTextField("WebDAV URL", webDavUrl, onValueChange = onWebDavUrlChange)
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                    LabeledTextField(
                        "WebDAV user",
                        webDavUsername,
                        onValueChange = onWebDavUsernameChange,
                        modifier = Modifier.weight(1f),
                    )
                    LabeledTextField(
                        "WebDAV password",
                        webDavPassword,
                        onValueChange = onWebDavPasswordChange,
                        modifier = Modifier.weight(1f),
                    )
                }
                TvActionButton("Open WebDAV", onClick = onOpenWebDav)
                Spacer(Modifier.height(MiruPlayUiMetrics.TINY_GAP_DP.dp))
                LabeledTextField("SMB URL", smbUrl, onValueChange = onSmbUrlChange)
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp)) {
                    LabeledTextField(
                        "SMB domain",
                        smbDomain,
                        onValueChange = onSmbDomainChange,
                        modifier = Modifier.weight(1f),
                    )
                    LabeledTextField(
                        "SMB user",
                        smbUsername,
                        onValueChange = onSmbUsernameChange,
                        modifier = Modifier.weight(1f),
                    )
                    LabeledTextField(
                        "SMB password",
                        smbPassword,
                        onValueChange = onSmbPasswordChange,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                    TvActionButton("Open SMB", onClick = onOpenSmb)
                    TvActionButton("Scan source", onClick = onScan, secondary = true)
                }
                StatusBox(status)
            }
            Column(
                modifier = Modifier.weight(0.58f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Remote browser", color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            remotePath.ifBlank { "/" },
                            color = TextSecondary,
                            fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TvActionButton("Up", onClick = onUp, secondary = true, modifier = Modifier.width(MiruPlayUiMetrics.CONTROL_BUTTON_WIDTH_DP.dp))
                }
                if (entries.isEmpty()) {
                    DesktopEmptyState(
                        text = "Open a remote source to list files.",
                        heightDp = MiruPlayUiMetrics.REMOTE_EMPTY_STATE_HEIGHT_DP,
                    )
                } else {
                    entries.take(8).forEach { entry ->
                        RemoteFileRow(
                            entry = entry,
                            selected = selectedEntry?.path == entry.path,
                            onClick = { onEntrySelected(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteFileRow(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DesktopSelectableRow(selected = selected, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (entry.isDirectory) "DIR" else "VID",
                color = if (entry.isDirectory) AnimeRed else TextSecondary,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(MiruPlayUiMetrics.TYPE_TAG_WIDTH_DP.dp),
            )
            Column(Modifier.weight(1f)) {
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
            if (!entry.isDirectory && entry.size > 0L) {
                Text(formatFileSize(entry.size), color = TextSecondary, fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp)
            }
        }
    }
}

@Composable
private fun BangumiPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedIndexEntry: MediaIndexEntry?,
    results: List<ScraperResult>,
    selectedResult: ScraperResult?,
    batchMatches: List<DesktopBangumiBatchMatch>,
    selectedBatchMatch: DesktopBangumiBatchMatch?,
    batchPlan: DesktopBangumiBatchPlan?,
    status: String,
    onUseSelectedEntry: () -> Unit,
    onSearch: () -> Unit,
    onBatchPreview: () -> Unit,
    onBatchApply: () -> Unit,
    onBatchUndo: () -> Unit,
    onBatchMatchSelected: (DesktopBangumiBatchMatch) -> Unit,
    onBatchCandidateSelected: (DesktopBangumiBatchMatch, ScraperResult) -> Unit,
    onBatchAcceptReview: () -> Unit,
    onResultSelected: (ScraperResult) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.42f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                Text("Bangumi metadata", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                LabeledTextField("Bangumi query", query, onValueChange = onQueryChange)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    TvActionButton("Use selected", onClick = onUseSelectedEntry, secondary = true, modifier = Modifier.weight(1f))
                    TvActionButton("Search", onClick = onSearch, modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    TvActionButton("Apply match", onClick = onApply, modifier = Modifier.weight(1f))
                    TvActionButton("Clear metadata", onClick = onClear, secondary = true, modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    TvActionButton("Batch preview", onClick = onBatchPreview, secondary = true, modifier = Modifier.weight(1f))
                    TvActionButton("Apply batch", onClick = onBatchApply, secondary = true, modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
                ) {
                    TvActionButton("Undo batch", onClick = onBatchUndo, secondary = true, modifier = Modifier.weight(1f))
                    TvActionButton("Accept review", onClick = onBatchAcceptReview, secondary = true, modifier = Modifier.weight(1f))
                }
                StatusBox(status)
            }
            Column(
                modifier = Modifier.weight(0.58f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                Text("Selected index", color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                SelectedIndexSummary(selectedIndexEntry)
                Text("Bangumi matches", color = TextPrimary, fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                if (batchMatches.isNotEmpty()) {
                    Text(
                        "Batch: ${batchMatches.size} quer${if (batchMatches.size == 1) "y" else "ies"} previewed",
                        color = TextSecondary,
                        fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                    )
                    batchMatches.take(4).forEach { match ->
                        BangumiBatchMatchRow(
                            match = match,
                            selected = selectedBatchMatch?.query == match.query,
                            status = batchPlan.batchStatusFor(match),
                            onClick = { onBatchMatchSelected(match) },
                        )
                    }
                }
                selectedBatchMatch?.takeIf { it.candidates.size > 1 }?.let { match ->
                    Text(
                        "Batch candidates",
                        color = TextPrimary,
                        fontSize = MiruPlayUiMetrics.SECTION_SUBTITLE_SP.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    match.candidates.take(4).forEach { candidate ->
                        BangumiResultRow(
                            result = candidate,
                            selected = candidate.isSameBangumiCandidate(match.result),
                            onClick = { onBatchCandidateSelected(match, candidate) },
                        )
                    }
                }
                if (results.isEmpty()) {
                    DesktopEmptyState("Search to show Bangumi matches.")
                } else {
                    results.take(6).forEach { result ->
                        BangumiResultRow(
                            result = result,
                            selected = selectedResult?.animeId == result.animeId,
                            onClick = { onResultSelected(result) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedIndexSummary(entry: MediaIndexEntry?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(CardBg.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .padding(MiruPlayUiMetrics.STACK_GAP_DP.dp),
    ) {
        if (entry == null) {
            Text("No indexed video selected.", color = TextSecondary, fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.TINY_GAP_DP.dp)) {
                Text(
                    entry.displayName(),
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.metadataTitle?.let { "Bangumi: $it" } ?: "Bangumi: not linked",
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
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
}

@Composable
private fun BangumiResultRow(
    result: ScraperResult,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DesktopSelectableRow(selected = selected, onClick = onClick) { active ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${(result.confidence * 100).roundToLong()}%",
                color = if (active) AnimeRed else TextSecondary,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(MiruPlayUiMetrics.BATCH_SCORE_WIDTH_DP.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    bangumiDisplayTitle(result),
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "ID ${result.animeId} / ${result.title}",
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BangumiBatchMatchRow(
    match: DesktopBangumiBatchMatch,
    selected: Boolean,
    status: String,
    onClick: () -> Unit,
) {
    val result = match.result
    DesktopSelectableRow(
        selected = selected,
        onClick = onClick,
        heightDp = MiruPlayUiMetrics.LIST_ROW_COMPACT_HEIGHT_DP,
        inactiveAlpha = 0.44f,
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                status,
                color = if (status == "conflict") AnimeRed else TextSecondary,
                fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(MiruPlayUiMetrics.BATCH_STATUS_WIDTH_DP.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    match.query,
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    result?.let {
                        val candidateSuffix = if (match.candidates.size > 1) {
                            " / ${match.selectedCandidateLabel()}"
                        } else {
                            ""
                        }
                        "${bangumiDisplayTitle(it)} / ${(it.confidence * 100).roundToLong()}%$candidateSuffix"
                    } ?: "No match",
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecentPlaybackPanel(
    records: List<ProgressRecord>,
    selectedRecord: ProgressRecord?,
    status: String,
    onRefresh: () -> Unit,
    onRecordSelected: (ProgressRecord) -> Unit,
    onClearSelected: () -> Unit,
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.32f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                Text("Continue watching", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
                    TvActionButton("Refresh", onClick = onRefresh, secondary = true)
                    TvActionButton("Clear item", onClick = onClearSelected, secondary = true)
                }
                StatusBox(status)
            }
            Column(
                modifier = Modifier.weight(0.68f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
            ) {
                if (records.isEmpty()) {
                    DesktopEmptyState("Launch playback to create recent items.")
                } else {
                    records.take(6).forEach { record ->
                        RecentProgressRow(
                            record = record,
                            selected = selectedRecord?.episodeId == record.episodeId,
                            onClick = { onRecordSelected(record) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentProgressRow(
    record: ProgressRecord,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DesktopSelectableRow(selected = selected, onClick = onClick) { active ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatPlaybackPosition(record.positionMs),
                color = if (active) AnimeRed else TextSecondary,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    recentDisplayName(record),
                    color = TextPrimary,
                    fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    record.episodeId,
                    color = TextSecondary,
                    fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("x${record.playCount}", color = TextSecondary, fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp)
        }
    }
}

@Composable
private fun MediaDetailsPanel(
    source: MediaSourceInfo?,
    indexEntry: MediaIndexEntry?,
    remoteEntry: FileEntry?,
    recentRecord: ProgressRecord?,
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Text("Media details", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        if (source == null && indexEntry == null && remoteEntry == null && recentRecord == null) {
            DesktopEmptyState(
                text = "Select media to show details.",
                heightDp = MiruPlayUiMetrics.DETAIL_PREVIEW_HEIGHT_DP,
            )
            return@TvPanel
        }

        val rows = DesktopMediaDetailRows.build(
            source = source,
            indexEntry = indexEntry,
            remoteEntry = remoteEntry,
            recentRecord = recentRecord,
        )
        val splitIndex = (rows.size + 1) / 2

        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
            Column(
                modifier = Modifier.weight(0.5f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SMALL_GAP_DP.dp),
            ) {
                rows.take(splitIndex).forEach { row ->
                    DetailLine(row.label, row.value)
                }
            }
            Column(
                modifier = Modifier.weight(0.5f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SMALL_GAP_DP.dp),
            ) {
                rows.drop(splitIndex).forEach { row ->
                    DetailLine(row.label, row.value)
                }
            }
        }
    }
}

@Composable
private fun CloudRssPanel(
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
) {
    TvPanel(Modifier.fillMaxWidth()) {
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

@Composable
private fun DetailLine(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(CardBg.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .padding(MiruPlayUiMetrics.COMPACT_STACK_GAP_DP.dp),
    ) {
        Text(label, color = TextSecondary, fontSize = MiruPlayUiMetrics.CAPTION_TEXT_SP.sp, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            color = TextPrimary,
            fontSize = MiruPlayUiMetrics.PANEL_BODY_SP.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaybackPanel(
    mediaPath: String,
    onMediaPathChange: (String) -> Unit,
    subtitlePath: String,
    onSubtitlePathChange: (String) -> Unit,
    startSeconds: String,
    onStartSecondsChange: (String) -> Unit,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    keepOpen: Boolean,
    onKeepOpenChange: (Boolean) -> Unit,
    rifeEnabled: Boolean,
    onRifeEnabledChange: (Boolean) -> Unit,
    rifeBackend: RifeBackend,
    onRifeBackendChange: (RifeBackend) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvPanel(modifier) {
        Text("Featured playback", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.SECTION_GAP_DP.dp)) {
            PosterPlaceholder()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
            ) {
                LabeledTextField("Media URI or path", mediaPath, onValueChange = onMediaPathChange)
                LabeledTextField("Subtitle path", subtitlePath, onValueChange = onSubtitlePathChange)
                LabeledTextField("Start seconds", startSeconds, onValueChange = onStartSecondsChange)
            }
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.SECTION_GAP_DP.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp),
        ) {
            ToggleRow("Fullscreen", fullscreen, onFullscreenChange)
            ToggleRow("Keep open", keepOpen, onKeepOpenChange)
            ToggleRow("RIFE", rifeEnabled, onRifeEnabledChange)
            RifeBackendPicker(rifeBackend, onSelected = onRifeBackendChange)
        }
    }
}

@Composable
private fun RuntimePanel(
    mpvPath: String,
    onMpvPathChange: (String) -> Unit,
    configDir: String,
    onConfigDirChange: (String) -> Unit,
    status: String,
    onCheckRuntime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvPanel(modifier) {
        Text("Runtime", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        LabeledTextField("mpv.exe", mpvPath, onValueChange = onMpvPathChange)
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        LabeledTextField("portable_config", configDir, onValueChange = onConfigDirChange)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        StatusBox(status)
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        TvActionButton("Check runtime", onClick = onCheckRuntime)
    }
}

@Composable
private fun CommandPanel(
    commandPreview: String,
    launchStatus: String,
    onLaunch: () -> Unit,
    onTogglePause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onStop: () -> Unit,
) {
    TvPanel(Modifier.fillMaxWidth()) {
        Text("mpv command", color = TextPrimary, fontSize = MiruPlayUiMetrics.PANEL_TITLE_SP.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .border(1.dp, Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
                .padding(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
        ) {
            Text(
                commandPreview,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.MEDIUM_GAP_DP.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(MiruPlayUiMetrics.STACK_GAP_DP.dp)) {
            TvActionButton("Launch mpv", onClick = onLaunch)
            TvActionButton("Stop", onClick = onStop, secondary = true)
            TvActionButton("Pause", onClick = onTogglePause, secondary = true)
            TvActionButton("-10s", onClick = onSeekBack, secondary = true, modifier = Modifier.width(110.dp))
            TvActionButton("+30s", onClick = onSeekForward, secondary = true, modifier = Modifier.width(110.dp))
        }
        Spacer(Modifier.height(MiruPlayUiMetrics.STACK_GAP_DP.dp))
        Text(launchStatus, color = TextSecondary, fontSize = MiruPlayUiMetrics.SECTION_BODY_SP.sp)
    }
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = MiruPlayUiMetrics.FIELD_TEXT_SP.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = AnimeRed,
            unfocusedLabelColor = TextSecondary,
            cursorColor = AnimeRed,
            focusedBorderColor = AnimeRed,
            unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
            focusedContainerColor = CardBg.copy(alpha = 0.44f),
            unfocusedContainerColor = CardBg.copy(alpha = 0.32f),
        ),
    )
}

@Composable
private fun SavedSourcePicker(
    sources: List<MediaSourceInfo>,
    activeSourceId: Long?,
    onSelected: (MediaSourceInfo) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = sources.firstOrNull { it.id == activeSourceId }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selected?.let(::sourceLabel) ?: "Saved sources")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (sources.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No saved sources") },
                    onClick = { expanded = false },
                )
            } else {
                sources.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(sourceLabel(source)) },
                        onClick = {
                            onSelected(source)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextPrimary, fontSize = MiruPlayUiMetrics.ITEM_TITLE_SP.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(MiruPlayUiMetrics.SMALL_GAP_DP.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun sourceLabel(source: MediaSourceInfo): String = buildString {
    append(source.name)
    append(" · ")
    append(source.type.name)
}

@Composable
private fun RifeBackendPicker(
    selected: RifeBackend,
    onSelected: (RifeBackend) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .width(MiruPlayUiMetrics.BACKEND_PICKER_WIDTH_DP.dp)
                .height(MiruPlayUiMetrics.BACKEND_PICKER_HEIGHT_DP.dp),
        ) {
            Text(selected.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RifeBackend.entries.forEach { backend ->
                DropdownMenuItem(
                    text = { Text(backend.name) },
                    onClick = {
                        onSelected(backend)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TvPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(DarkSurface)
            .border(1.dp, Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .padding(MiruPlayUiMetrics.PANEL_PADDING_DP.dp),
        content = content,
    )
}

@Composable
private fun DesktopEmptyState(
    text: String,
    heightDp: Int = MiruPlayUiMetrics.EMPTY_STATE_HEIGHT_DP,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(CardBg.copy(alpha = 0.48f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA),
                shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextSecondary, fontSize = MiruPlayUiMetrics.SECTION_BODY_SP.sp)
    }
}

@Composable
private fun DesktopSelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int = MiruPlayUiMetrics.LIST_ROW_HEIGHT_DP,
    inactiveAlpha: Float = 0.55f,
    content: @Composable (active: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val active = selected || isFocused
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) CardBg else CardBg.copy(alpha = inactiveAlpha),
            contentColor = TextPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, focusedElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) AnimeRed else Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA),
                shape = RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp),
            ),
    ) {
        content(active)
    }
}

@Composable
private fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    secondary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val background = when {
        secondary -> Color.Transparent
        isFocused -> AnimeRed
        else -> AnimeRed.copy(alpha = 0.82f)
    }
    val border = when {
        isFocused -> Color.White
        secondary -> Color.White.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(MiruPlayUiMetrics.ACTION_BUTTON_RADIUS_DP.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, focusedElevation = 0.dp),
        modifier = modifier
            .defaultMinSize(minWidth = MiruPlayUiMetrics.ACTION_BUTTON_MIN_WIDTH_DP.dp)
            .height(MiruPlayUiMetrics.ACTION_BUTTON_HEIGHT_DP.dp)
            .border(2.dp, border, RoundedCornerShape(MiruPlayUiMetrics.ACTION_BUTTON_RADIUS_DP.dp))
            .focusable(),
    ) {
        Text(text, fontSize = MiruPlayUiMetrics.ACTION_BUTTON_TEXT_SP.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PosterPlaceholder() {
    Box(
        modifier = Modifier
            .width(MiruPlayUiMetrics.POSTER_WIDTH_DP.dp)
            .height(MiruPlayUiMetrics.POSTER_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(
                Brush.verticalGradient(
                    listOf(AccentBlue, CardBg, Color.Black.copy(alpha = 0.86f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(MiruPlayUiMetrics.BADGE_PADDING_DP.dp)
                .size(MiruPlayUiMetrics.BADGE_SIZE_DP.dp)
                .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
                .background(AnimeRed.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("RIFE", color = Color.White, fontSize = MiruPlayUiMetrics.BADGE_TEXT_SP.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(MiruPlayUiMetrics.DETAIL_MEDIA_PADDING_DP.dp),
        ) {
            Text("mpv", color = Color.White, fontSize = MiruPlayUiMetrics.HERO_TITLE_SP.sp, fontWeight = FontWeight.Bold)
            Text(
                "Windows runtime",
                color = TextSecondary,
                fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusBox(status: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .background(CardBg.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = MiruPlayUiMetrics.PANEL_BORDER_ALPHA), RoundedCornerShape(MiruPlayUiMetrics.PANEL_RADIUS_DP.dp))
            .padding(MiruPlayUiMetrics.STATUS_BOX_PADDING_DP.dp),
    ) {
        Text(
            status,
            color = TextPrimary,
            fontSize = MiruPlayUiMetrics.DETAIL_TEXT_SP.sp,
            lineHeight = 18.sp,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun schedulerStatus(state: DesktopCloudDriveRssSchedulerState): String {
    val prefix = if (state.running) "Scheduler running." else "Scheduler idle."
    val error = state.lastError
    if (!error.isNullOrBlank()) return "$prefix Last check failed: $error"
    val summary = state.lastSummary
    if (summary != null) {
        return "$prefix Last run: ${summary.submitted} submitted, ${summary.skipped} skipped, ${summary.failed} failed, ${summary.organized} organized."
    }
    return if (state.lastCheckedAt > 0L) {
        "$prefix Last check found no due sync."
    } else {
        "$prefix No checks yet."
    }
}

private fun linkedSourceLabel(
    sources: List<MediaSourceInfo>,
    sourceId: Long?,
): String {
    if (sourceId == null) return "None"
    val source = sources.firstOrNull { it.id == sourceId }
    return source?.let { "${it.name} (${it.type.name})" } ?: "Missing source #$sourceId"
}

private fun runtimeStatus(mpvPath: String, configDir: String): String =
    runCatching {
        val verification = MpvRuntimeVerifier.verify(DesktopRuntimeDefaults.runtimeRoot(mpvPath, configDir))
        verification.detailMessage()
    }.getOrElse { error ->
        "Runtime check failed: ${error.message ?: error::class.simpleName}"
    }

private fun buildCommandPreview(
    mpvPath: String,
    configDir: String,
    mediaPath: String,
    subtitlePath: String,
    startSeconds: String,
    fullscreen: Boolean,
    keepOpen: Boolean,
    rifeEnabled: Boolean,
    rifeBackend: RifeBackend,
): String =
    runCatching {
        val source = buildPlaybackSource(mediaPath, subtitlePath, startSeconds)
        val config = buildRuntimeConfig(mpvPath, configDir, fullscreen, keepOpen, rifeEnabled, rifeBackend)
        MpvCommandBuilder(config).build(source).joinToString(" ") { it.quoteForPreview() }
    }.getOrElse { error ->
        error.message ?: "Unable to build mpv command."
    }

private fun buildRuntimeConfig(
    mpvPath: String,
    configDir: String,
    fullscreen: Boolean,
    keepOpen: Boolean,
    rifeEnabled: Boolean,
    rifeBackend: RifeBackend,
): MpvRuntimeConfig =
    MpvRuntimeConfig(
        mpvExecutable = Paths.get(mpvPath.trim()),
        configDirectory = configDir.trim().takeIf { it.isNotBlank() }?.let(Paths::get),
        startFullscreen = fullscreen,
        keepOpen = keepOpen,
        rife = if (rifeEnabled) RifeInterpolationConfig(backend = rifeBackend) else null,
    )

private fun buildPlaybackSource(
    mediaPath: String,
    subtitlePath: String,
    startSeconds: String,
    mediaSourceId: String = "desktop-compose",
    episodeId: String? = null,
): PlaybackSource {
    val media = requireNotNull(mediaPath.trim().takeIf { it.isNotBlank() }) {
        "Choose a media URI or file path before launching mpv."
    }
    val startMs = startSeconds.trim()
        .takeIf { it.isNotBlank() }
        ?.toDoubleOrNull()
        ?.let { (it * 1_000.0).roundToLong().coerceAtLeast(0L) }
        ?: 0L
    return PlaybackSource(
        uri = media,
        mediaSourceId = mediaSourceId,
        startPosition = startMs,
        subtitleTracks = buildExternalSubtitleTracks(subtitlePath.trim()),
        episodeId = episodeId ?: media,
    )
}

private fun webDavSourceInfo(
    url: String,
    username: String,
    password: String,
): MediaSourceInfo =
    MediaSourceInfo(
        name = url.substringAfter("://", url).trim('/').ifBlank { "WebDAV" },
        type = MediaSourceType.WEBDAV,
        connectionInfo = buildMap {
            put("url", url)
            if (username.isNotBlank()) put("username", username)
            if (password.isNotBlank()) put("password", password)
        },
        isConnected = true,
    )

private fun smbSourceInfo(
    url: String,
    domain: String,
    username: String,
    password: String,
): MediaSourceInfo =
    MediaSourceInfo(
        name = url.removePrefix("smb://").trim('\\', '/').ifBlank { "SMB" },
        type = MediaSourceType.SMB,
        connectionInfo = buildMap {
            put("url", DesktopSmbMediaSource.normalizeRoot(url))
            if (domain.isNotBlank()) put("domain", domain)
            if (username.isNotBlank()) put("username", username)
            if (password.isNotBlank()) put("password", password)
        },
        isConnected = true,
    )

private fun desktopSourceFromInfo(info: MediaSourceInfo): DesktopMediaSource =
    when (info.type) {
        MediaSourceType.LOCAL -> DesktopLocalMediaSource(info)
        MediaSourceType.WEBDAV -> desktopWebDavSourceFromInfo(info)
        MediaSourceType.SMB -> DesktopSmbMediaSource(info)
    }

private fun desktopWebDavSourceFromInfo(info: MediaSourceInfo): DesktopWebDavMediaSource =
    DesktopWebDavMediaSource.create(
        name = info.name,
        url = requireNotNull(info.connectionInfo["url"]) { "WebDAV source requires connectionInfo[url]" },
        username = info.connectionInfo["username"].orEmpty(),
        password = info.connectionInfo["password"].orEmpty(),
    )

private fun playableUriFor(
    source: DesktopMediaSource?,
    bridge: DesktopPlaybackBridge,
    mediaPath: String,
): String {
    val path = mediaPath.trim()
    if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
        return path
    }
    return when (source?.info?.type) {
        MediaSourceType.WEBDAV -> if (path.startsWith("/")) bridge.playableUri(source, path) else path
        MediaSourceType.SMB -> if (path.startsWith("smb://", ignoreCase = true)) bridge.playableUri(source, path) else path
        MediaSourceType.LOCAL,
        null -> path
    }
}

private fun remoteParent(path: String): String? {
    val clean = path.trimEnd('/')
    if (clean.isBlank() || clean == "/") return null
    if (clean.startsWith("smb://", ignoreCase = true)) {
        val segments = clean.removePrefix("smb://").split('/').filter { it.isNotBlank() }
        if (segments.size <= 2) return null
        return "smb://${segments.dropLast(1).joinToString("/")}"
    }

    val parent = clean.trim('/').substringBeforeLast('/', "")
    return if (parent.isBlank()) "" else "/$parent"
}

private fun List<MediaSourceInfo>.upsertSource(source: MediaSourceInfo): List<MediaSourceInfo> =
    map { if (it.id == source.id) source else it }.let { updated ->
        if (updated.none { it.id == source.id }) updated + source else updated
    }

private fun bangumiQueryFor(entry: MediaIndexEntry?): String? {
    entry?.animeName?.takeIf { it.isNotBlank() }?.let { return it }
    val fileName = entry?.path
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?: return null
    return fileName.substringBeforeLast('.', fileName).takeIf { it.isNotBlank() }
}

private fun bangumiDisplayTitle(result: ScraperResult): String =
    result.displayTitle()

private fun DesktopBangumiBatchPlan?.batchStatusFor(match: DesktopBangumiBatchMatch): String =
    when {
        this == null -> "preview"
        conflicts.any { it.query == match.query } -> "conflict"
        readyUpdates.any { it.query == match.query } -> "ready"
        else -> "review"
    }

private fun DesktopBangumiBatchMatch.withSelectedCandidate(candidate: ScraperResult): DesktopBangumiBatchMatch =
    copy(
        result = candidate,
        candidates = if (candidates.any { it.isSameBangumiCandidate(candidate) }) {
            candidates
        } else {
            candidates + candidate
        },
    )

private fun DesktopBangumiBatchMatch.selectedCandidateLabel(): String {
    val selectedIndex = candidates.indexOfFirst { it.isSameBangumiCandidate(result) }
    return if (selectedIndex >= 0) {
        "candidate ${selectedIndex + 1}/${candidates.size}"
    } else {
        "${candidates.size} candidates"
    }
}

private fun List<DesktopBangumiBatchMatch>.replaceBatchMatch(updated: DesktopBangumiBatchMatch): List<DesktopBangumiBatchMatch> =
    map { match -> if (match.query == updated.query) updated else match }

private fun ScraperResult.isSameBangumiCandidate(other: ScraperResult?): Boolean =
    other != null &&
        animeId == other.animeId &&
        source == other.source

private fun List<MediaIndexEntry>.replaceEntry(updated: MediaIndexEntry): List<MediaIndexEntry> =
    map { entry ->
        if (entry.sourceId == updated.sourceId && entry.path == updated.path) updated else entry
    }

private fun List<MediaIndexEntry>.replaceEntries(updatedEntries: List<MediaIndexEntry>): List<MediaIndexEntry> {
    if (updatedEntries.isEmpty()) return this
    val byKey = updatedEntries.associateBy { it.sourceId to it.path }
    return map { entry -> byKey[entry.sourceId to entry.path] ?: entry }
}

private fun recentDisplayName(record: ProgressRecord): String =
    record.episodeId.substringAfterLast('\\').substringAfterLast('/').ifBlank { record.episodeId }

private fun String.quoteForPreview(): String =
    if (any { it.isWhitespace() }) "\"${replace("\"", "\\\"")}\"" else this
