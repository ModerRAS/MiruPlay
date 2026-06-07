package com.miruplay.tv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.data.preferences.AppModePreferencesManager
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.persistenceLocation
import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.AppModeSelectionState
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.EpisodePlaybackSourceResolver
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.scanner.ScanCoordinator
import com.miruplay.tv.navigation.NavRoutes
import com.miruplay.tv.ui.detail.AnimeDetailScreen
import com.miruplay.tv.ui.detail.DramaDetailScreen
import com.miruplay.tv.ui.library.LibraryScreen
import com.miruplay.tv.ui.mode.AppModeSelectionScreen
import com.miruplay.tv.ui.mode.DramaLibraryScreen
import com.miruplay.tv.ui.player.PlayerScreen
import com.miruplay.tv.ui.settings.AddSourceScreen
import com.miruplay.tv.ui.theme.MiruPlayTheme
import com.miruplay.tv.webcontrol.WebControlNavigator
import com.miruplay.tv.webcontrol.WebPlaybackSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var mediaRepository: MediaSourceRepository
    @Inject lateinit var progressRepository: PlaybackProgressRepository
    @Inject lateinit var appModePreferencesManager: AppModePreferencesManager
    @Inject lateinit var appCredentials: AppCredentialStore
    @Inject lateinit var webControlNavigator: WebControlNavigator
    @Inject lateinit var scanCoordinator: ScanCoordinator

    private var restoreLaunchTmdbOverrides: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchIntentSnapshot = captureLaunchIntentSnapshot(intent)
        MiruLog.i(
            "MainActivity",
            "Main activity created",
            mapOf(
                "has_saved_state" to (savedInstanceState != null).toString(),
                "intent_action" to intent?.action.orEmpty(),
                "has_test_source" to launchIntentSnapshot.hasTestSourceIntent().toString(),
            )
        )
        logLaunchIntentSummary(launchIntentSnapshot)
        lifecycleScope.launch {
            val selectionState = appModePreferencesManager.getSelectionState()
            applyLaunchTestTmdbOverrides(launchIntentSnapshot.tmdbOverrides)
            resolveLaunchTestSourceRequest(
                legacyLocalPath = launchIntentSnapshot.legacyLocalPath,
                legacyLocalName = launchIntentSnapshot.legacyLocalName,
                rawType = launchIntentSnapshot.rawType,
                rawLocation = launchIntentSnapshot.rawLocation,
                rawName = launchIntentSnapshot.rawName,
                rawDisplayName = launchIntentSnapshot.rawDisplayName,
                rawUsername = launchIntentSnapshot.rawUsername,
                rawPassword = launchIntentSnapshot.rawPassword,
                rawContentMode = launchIntentSnapshot.rawContentMode,
                disableOnlineMetadata = launchIntentSnapshot.disableOnlineMetadata,
                scanAfterAdd = launchIntentSnapshot.scanAfterAdd,
                fallbackMode = selectionState.currentAppMode,
            )?.let { request ->
                addLaunchTestSource(request)
            }
            renderContent(selectionState)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val launchIntentSnapshot = captureLaunchIntentSnapshot(intent)
        logLaunchIntentSummary(launchIntentSnapshot)
        lifecycleScope.launch {
            val selectionState = appModePreferencesManager.getSelectionState()
            applyLaunchTestTmdbOverrides(launchIntentSnapshot.tmdbOverrides)
            resolveLaunchTestSourceRequest(
                legacyLocalPath = launchIntentSnapshot.legacyLocalPath,
                legacyLocalName = launchIntentSnapshot.legacyLocalName,
                rawType = launchIntentSnapshot.rawType,
                rawLocation = launchIntentSnapshot.rawLocation,
                rawName = launchIntentSnapshot.rawName,
                rawDisplayName = launchIntentSnapshot.rawDisplayName,
                rawUsername = launchIntentSnapshot.rawUsername,
                rawPassword = launchIntentSnapshot.rawPassword,
                rawContentMode = launchIntentSnapshot.rawContentMode,
                disableOnlineMetadata = launchIntentSnapshot.disableOnlineMetadata,
                scanAfterAdd = launchIntentSnapshot.scanAfterAdd,
                fallbackMode = selectionState.currentAppMode,
            )?.let { request ->
                addLaunchTestSource(request)
            }
        }
    }

    private fun applyLaunchTestTmdbOverrides(
        overrides: LaunchTestTmdbOverrides,
    ) {
        restoreLaunchTmdbOverrides?.invoke()
        restoreLaunchTmdbOverrides = null
        if (!BuildConfig.DEBUG) return
        val token = overrides.token
        val baseUrlOverride = overrides.baseUrlOverride
        if (token == null && baseUrlOverride == null) return
        MiruLog.i(
            "MainActivity",
            "Applying launch TMDB overrides",
            mapOf(
                "has_token_override" to (token != null).toString(),
                "has_base_url_override" to (baseUrlOverride != null).toString(),
            ),
        )
        Log.i(
            "MainActivity",
            "Applying launch TMDB overrides (token=${token != null}, baseUrl=${baseUrlOverride != null})",
        )
        val previousToken = appCredentials.tmdbAccessToken
        val previousBaseUrlOverride = appCredentials.tmdbApiBaseUrlOverride
        restoreLaunchTmdbOverrides = {
            appCredentials.tmdbAccessToken = previousToken
            appCredentials.tmdbApiBaseUrlOverride = previousBaseUrlOverride
        }
        token?.let { appCredentials.tmdbAccessToken = it }
        baseUrlOverride?.let { appCredentials.tmdbApiBaseUrlOverride = it }
    }

    override fun onDestroy() {
        if (restoreLaunchTmdbOverrides != null) {
            MiruLog.i("MainActivity", "Restoring launch TMDB overrides")
            Log.i("MainActivity", "Restoring launch TMDB overrides")
        }
        restoreLaunchTmdbOverrides?.invoke()
        restoreLaunchTmdbOverrides = null
        super.onDestroy()
    }

    private suspend fun addLaunchTestSource(
        request: LaunchTestSourceRequest,
    ) {
        val normalizedLocation = normalizeLaunchTestSourceLocation(request.type, request.location)
        MiruLog.i(
            "MainActivity",
            "Adding test source from launch extra",
            mapOf(
                "source_name" to request.name,
                "source_type" to request.type.name,
                "content_mode" to request.contentMode.name,
                "scan_after_add" to request.scanAfterAdd.toString(),
            )
        )
        Log.i("MainActivity", "Adding test source from launch extra")
        val source = MediaSourceInfo(
            name = request.name,
            type = request.type,
            contentMode = request.contentMode,
            connectionInfo = MediaSourceInfoConventions.sourceConnectionInfo(
                type = request.type,
                location = normalizedLocation,
                displayName = request.displayName,
                username = request.username,
                password = request.password,
            ).let { connectionInfo ->
                if (request.disableOnlineMetadata) {
                    connectionInfo + ("disableOnlineMetadata" to "true")
                } else {
                    connectionInfo
                }
            },
        )
        val existingSource = mediaRepository.getSources()
            .getOrNull()
            .orEmpty()
            .firstOrNull { existing ->
                existing.type == request.type &&
                    existing.persistenceLocation() == normalizedLocation
            }
        val persistedSourceId = if (existingSource != null) {
            val updateResult = mediaRepository.updateSource(
                source.copy(
                    id = existingSource.id,
                    isConnected = existingSource.isConnected,
                    lastScanned = existingSource.lastScanned,
                )
            )
            updateResult.onSuccess {
                MiruLog.i(
                    "MainActivity",
                    "Test source updated",
                    mapOf(
                        "source_id" to existingSource.id.toString(),
                        "source_name" to request.name,
                    )
                )
                Log.i("MainActivity", "Test source updated")
            }.onError { error ->
                MiruLog.w(
                    "MainActivity",
                    "Test source update failed",
                    attributes = mapOf(
                        "source_name" to request.name,
                        "error" to error.toUserMessage(),
                    )
                )
                Log.w("MainActivity", "Test source update failed: ${error.toUserMessage()}")
            }
            updateResult.getOrNull()?.let { existingSource.id }
        } else {
            val addResult = mediaRepository.addSource(source)
            addResult.onSuccess { id ->
                MiruLog.i(
                    "MainActivity",
                    "Test source added",
                    mapOf(
                        "source_id" to id.toString(),
                        "source_name" to request.name,
                    )
                )
                Log.i("MainActivity", "Test source added")
            }.onError { error ->
                MiruLog.w(
                    "MainActivity",
                    "Test source add failed",
                    attributes = mapOf(
                        "source_name" to request.name,
                        "error" to error.toUserMessage(),
                    )
                )
                Log.w("MainActivity", "Test source add failed: ${error.toUserMessage()}")
            }
            addResult.getOrNull()
        }

        if (request.scanAfterAdd && persistedSourceId != null) {
            scanCoordinator.scanSource(persistedSourceId)
                .onSuccess { result ->
                    MiruLog.i(
                        "MainActivity",
                        "Test source scan completed",
                        mapOf(
                            "source_id" to persistedSourceId.toString(),
                            "episodes_found" to result.episodesFound.toString(),
                            "new_episodes" to result.newEpisodes.toString(),
                        )
                    )
                    Log.i("MainActivity", "Test source scan completed")
                }
                .onError { error ->
                    MiruLog.w(
                        "MainActivity",
                        "Test source scan failed",
                        attributes = mapOf(
                            "source_id" to persistedSourceId.toString(),
                            "error" to error.toUserMessage(),
                        )
                    )
                    Log.w("MainActivity", "Test source scan failed: ${error.toUserMessage()}")
                }
        }
    }

    private fun renderContent(initialAppModeSelectionState: AppModeSelectionState) {
        setContent {
            MiruPlayTheme {
                MiruPlayNavigation(
                    initialAppModeSelectionState = initialAppModeSelectionState,
                    appModePreferencesManager = appModePreferencesManager,
                    mediaRepository = mediaRepository,
                    progressRepository = progressRepository,
                    webControlNavigator = webControlNavigator
                )
            }
        }
    }

    private fun logLaunchIntentSummary(snapshot: LaunchIntentSnapshot) {
        if (!BuildConfig.DEBUG) return
        if (!snapshot.hasAnyLaunchTestData()) return
        MiruLog.i(
            "MainActivity",
            "Launch test extras captured",
            mapOf(
                "has_test_source" to snapshot.hasTestSourceIntent().toString(),
                "has_tmdb_token_extra" to snapshot.tmdbOverrides.hasTokenExtra.toString(),
                "has_tmdb_token_value" to (snapshot.tmdbOverrides.token != null).toString(),
                "has_tmdb_base_url_extra" to snapshot.tmdbOverrides.hasBaseUrlExtra.toString(),
                "has_tmdb_base_url_value" to (snapshot.tmdbOverrides.baseUrlOverride != null).toString(),
                "scan_after_add" to snapshot.scanAfterAdd.toString(),
            ),
        )
        Log.i(
            "MainActivity",
            "Launch extras summary (testSource=${snapshot.hasTestSourceIntent()}, tmdbTokenExtra=${snapshot.tmdbOverrides.hasTokenExtra}, tmdbTokenValue=${snapshot.tmdbOverrides.token != null}, tmdbBaseUrlExtra=${snapshot.tmdbOverrides.hasBaseUrlExtra}, tmdbBaseUrlValue=${snapshot.tmdbOverrides.baseUrlOverride != null}, scanAfterAdd=${snapshot.scanAfterAdd})",
        )
    }
}

internal data class LaunchTestSourceRequest(
    val name: String,
    val type: MediaSourceType,
    val location: String,
    val displayName: String = "",
    val username: String = "",
    val password: String = "",
    val contentMode: MediaContentMode,
    val disableOnlineMetadata: Boolean,
    val scanAfterAdd: Boolean,
)

internal data class LaunchIntentSnapshot(
    val legacyLocalPath: String?,
    val legacyLocalName: String?,
    val rawType: String?,
    val rawLocation: String?,
    val rawName: String?,
    val rawDisplayName: String?,
    val rawUsername: String?,
    val rawPassword: String?,
    val rawContentMode: String?,
    val disableOnlineMetadata: Boolean,
    val scanAfterAdd: Boolean,
    val tmdbOverrides: LaunchTestTmdbOverrides,
)

internal data class LaunchTestTmdbOverrides(
    val token: String?,
    val baseUrlOverride: String?,
    val hasTokenExtra: Boolean,
    val hasBaseUrlExtra: Boolean,
)

internal fun captureLaunchIntentSnapshot(intent: Intent?): LaunchIntentSnapshot {
    val extras = intent?.extras
    return LaunchIntentSnapshot(
        legacyLocalPath = intent?.getStringExtra("test_local_path"),
        legacyLocalName = intent?.getStringExtra("test_local_name"),
        rawType = intent?.getStringExtra("test_source_type"),
        rawLocation = intent?.getStringExtra("test_source_location"),
        rawName = intent?.getStringExtra("test_source_name"),
        rawDisplayName = intent?.getStringExtra("test_source_display_name"),
        rawUsername = intent?.getStringExtra("test_source_username"),
        rawPassword = intent?.getStringExtra("test_source_password"),
        rawContentMode = intent?.getStringExtra("test_content_mode"),
        disableOnlineMetadata = intent?.getBooleanExtra("test_disable_online_metadata", false) == true,
        scanAfterAdd = intent?.getBooleanExtra("test_scan_after_add", false) == true,
        tmdbOverrides = resolveLaunchTestTmdbOverrides(
            rawToken = intent?.getStringExtra("test_tmdb_token"),
            rawBaseUrlOverride = intent?.getStringExtra("test_tmdb_base_url"),
            hasTokenExtra = extras?.containsKey("test_tmdb_token") == true,
            hasBaseUrlExtra = extras?.containsKey("test_tmdb_base_url") == true,
        ),
    )
}

internal fun resolveLaunchTestTmdbOverrides(
    rawToken: String?,
    rawBaseUrlOverride: String?,
    hasTokenExtra: Boolean,
    hasBaseUrlExtra: Boolean,
): LaunchTestTmdbOverrides =
    LaunchTestTmdbOverrides(
        token = normalizeLaunchTmdbToken(rawToken),
        baseUrlOverride = normalizeLaunchTmdbOverride(rawBaseUrlOverride),
        hasTokenExtra = hasTokenExtra,
        hasBaseUrlExtra = hasBaseUrlExtra,
    )

internal fun LaunchIntentSnapshot.hasTestSourceIntent(): Boolean =
    hasLaunchTestSourceIntent(
        legacyLocalPath = legacyLocalPath,
        sourceLocation = rawLocation,
    )

internal fun LaunchIntentSnapshot.hasAnyLaunchTestData(): Boolean =
    hasTestSourceIntent() ||
        rawContentMode?.isNotBlank() == true ||
        disableOnlineMetadata ||
        scanAfterAdd ||
        tmdbOverrides.hasTokenExtra ||
        tmdbOverrides.hasBaseUrlExtra

internal fun resolveLaunchTestSourceContentMode(
    rawValue: String?,
    fallbackMode: AppMode?,
): MediaContentMode {
    val normalized = rawValue?.trim()?.uppercase()
    return when (normalized) {
        MediaContentMode.DRAMA.name -> MediaContentMode.DRAMA
        MediaContentMode.ANIME.name -> MediaContentMode.ANIME
        else -> when (fallbackMode) {
            AppMode.DRAMA -> MediaContentMode.DRAMA
            else -> MediaContentMode.ANIME
        }
    }
}

internal fun normalizeLaunchTmdbOverride(rawValue: String?): String? =
    rawValue?.trim()?.takeIf { it.isNotBlank() }

internal fun normalizeLaunchTmdbToken(rawValue: String?): String? =
    rawValue?.trim()?.takeIf { it.isNotBlank() }

internal fun hasLaunchTestSourceIntent(
    legacyLocalPath: String?,
    sourceLocation: String?,
): Boolean =
    !legacyLocalPath.isNullOrBlank() || !sourceLocation.isNullOrBlank()

internal fun resolveLaunchTestSourceType(
    rawValue: String?,
    location: String?,
): MediaSourceType {
    val normalized = rawValue?.trim()?.uppercase()
    return when (normalized) {
        MediaSourceType.LOCAL.name -> MediaSourceType.LOCAL
        MediaSourceType.WEBDAV.name -> MediaSourceType.WEBDAV
        MediaSourceType.SMB.name -> MediaSourceType.SMB
        else -> {
            val normalizedLocation = location?.trim().orEmpty()
            when {
                normalizedLocation.startsWith("http://", ignoreCase = true) ||
                    normalizedLocation.startsWith("https://", ignoreCase = true) -> MediaSourceType.WEBDAV
                normalizedLocation.startsWith("smb://", ignoreCase = true) ||
                    normalizedLocation.startsWith("\\\\") -> MediaSourceType.SMB
                else -> MediaSourceType.LOCAL
            }
        }
    }
}

internal fun normalizeLaunchTestSourceLocation(
    type: MediaSourceType,
    location: String,
): String =
    when (type) {
        MediaSourceType.SMB -> MediaSourceInfoConventions.normalizeSmbRoot(location)
        MediaSourceType.LOCAL,
        MediaSourceType.WEBDAV -> location.trim()
    }

internal fun resolveLaunchTestSourceRequest(
    legacyLocalPath: String?,
    legacyLocalName: String?,
    rawType: String?,
    rawLocation: String?,
    rawName: String?,
    rawDisplayName: String?,
    rawUsername: String?,
    rawPassword: String?,
    rawContentMode: String?,
    disableOnlineMetadata: Boolean,
    scanAfterAdd: Boolean,
    fallbackMode: AppMode?,
): LaunchTestSourceRequest? {
    val legacyPath = legacyLocalPath?.trim()?.takeIf { it.isNotBlank() }
    if (legacyPath != null) {
        return LaunchTestSourceRequest(
            name = legacyLocalName?.trim()?.takeIf { it.isNotBlank() } ?: "Test Local",
            type = MediaSourceType.LOCAL,
            location = legacyPath,
            contentMode = resolveLaunchTestSourceContentMode(rawContentMode, fallbackMode),
            disableOnlineMetadata = true,
            scanAfterAdd = scanAfterAdd,
        )
    }

    val location = rawLocation?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val sourceType = resolveLaunchTestSourceType(rawType, location)
    return LaunchTestSourceRequest(
        name = rawName?.trim()?.takeIf { it.isNotBlank() } ?: when (sourceType) {
            MediaSourceType.LOCAL -> "ADB Test Local"
            MediaSourceType.WEBDAV -> "ADB Test WebDAV"
            MediaSourceType.SMB -> "ADB Test SMB"
        },
        type = sourceType,
        location = location,
        displayName = rawDisplayName?.trim().orEmpty(),
        username = rawUsername?.trim().orEmpty(),
        password = rawPassword.orEmpty(),
        contentMode = resolveLaunchTestSourceContentMode(rawContentMode, fallbackMode),
        disableOnlineMetadata = disableOnlineMetadata,
        scanAfterAdd = scanAfterAdd,
    )
}

@Composable
fun MiruPlayNavigation(
    initialAppModeSelectionState: AppModeSelectionState,
    appModePreferencesManager: AppModePreferencesManager,
    mediaRepository: MediaSourceRepository,
    progressRepository: PlaybackProgressRepository,
    webControlNavigator: WebControlNavigator
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var appModeSelectionState by remember {
        mutableStateOf(initialAppModeSelectionState)
    }
    val episodePlaybackSourceResolver = remember(progressRepository, mediaRepository) {
        EpisodePlaybackSourceResolver(
            progress = progressRepository,
            mediaSources = mediaRepository,
        )
    }

    androidx.compose.runtime.LaunchedEffect(webControlNavigator, navController) {
        webControlNavigator.commands.collect { command ->
            val payload = command.payload
            if (command.type == WebControlNavigator.TYPE_OPEN_PLAYER && payload != null) {
                runCatching {
                    val source = Json.decodeFromJsonElement<WebPlaybackSource>(payload)
                    MiruLog.i(
                        "MiruPlayNavigation",
                        "Web control requested player navigation",
                        mapOf(
                            "media_source_id" to source.mediaSourceId,
                            "has_episode_id" to (!source.episodeId.isNullOrBlank()).toString(),
                            "start_position_ms" to source.startPositionMs.toString(),
                        )
                    )
                    val encodedPath = Uri.encode(source.uri)
                    val encodedSource = Uri.encode(source.mediaSourceId)
                    val encodedEpisode = Uri.encode(source.episodeId ?: "")
                    navController.navigate(
                        NavRoutes.player(
                            uri = encodedPath,
                            mediaSourceId = encodedSource,
                            startPosition = source.startPositionMs,
                            episodeId = encodedEpisode,
                        )
                    ) {
                        launchSingleTop = true
                    }
                }.onFailure { error ->
                    MiruLog.e(
                        "MiruPlayNavigation",
                        "Failed to handle web control navigation command",
                        error,
                        mapOf("command_type" to command.type)
                    )
                }
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = NavRoutes.launchDestinationFor(appModeSelectionState)
    ) {
        composable(NavRoutes.MODE_SELECTION) {
            AppModeSelectionScreen(
                onSelectMode = { mode ->
                    scope.launch {
                        appModePreferencesManager.completeModeSelection(mode)
                        appModeSelectionState = AppModeSelectionState(
                            currentAppMode = mode,
                            hasCompletedModeSelection = true,
                        )
                        navController.navigate(NavRoutes.homeFor(mode)) {
                            popUpTo(NavRoutes.MODE_SELECTION) {
                                inclusive = true
                            }
                        }
                    }
                }
            )
        }

        composable(NavRoutes.LIBRARY) {
            LibraryScreen(
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onNavigateToDetail = { animeId ->
                    runCatching {
                        navController.navigate(NavRoutes.animeDetail(animeId))
                    }.onFailure { error ->
                        MiruLog.e(
                            "MiruPlayNavigation",
                            "Failed to open anime detail",
                            error,
                            mapOf("anime_id" to animeId)
                        )
                    }
                }
            )
        }

        composable(NavRoutes.DRAMA_HOME) {
            DramaLibraryScreen(
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onNavigateToDetail = { seriesId ->
                    navController.navigate(NavRoutes.dramaDetail(seriesId))
                }
            )
        }

        composable(
            route = NavRoutes.DRAMA_DETAIL,
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId")
                ?.takeIf { it.isNotBlank() }
                ?: return@composable
            DramaDetailScreen(
                seriesId = seriesId,
                onNavigateBack = { navController.popBackStack() },
                onPlayEpisode = { episode ->
                    scope.launch {
                        runCatching {
                            val playbackEpisode = com.miruplay.tv.model.Episode(
                                id = episode.id,
                                animeId = episode.seriesId,
                                seasonNumber = episode.seasonNumber,
                                episodeNumber = episode.episodeNumber,
                                title = episode.title,
                                filePath = episode.filePath,
                                fileName = episode.fileName,
                            )
                            val source = episodePlaybackSourceResolver.build(playbackEpisode)
                            val encodedPath = Uri.encode(source.uri)
                            val encodedSource = Uri.encode(source.mediaSourceId)
                            val encodedEpisode = Uri.encode(source.episodeId ?: "")
                            navController.navigate(
                                NavRoutes.player(
                                    uri = encodedPath,
                                    mediaSourceId = encodedSource,
                                    startPosition = source.startPosition,
                                    episodeId = encodedEpisode,
                                )
                            )
                        }.onFailure { error ->
                            MiruLog.e(
                                "MiruPlayNavigation",
                                "Failed to open drama episode playback",
                                error,
                                mapOf(
                                    "episode_id" to episode.id,
                                    "series_id" to episode.seriesId,
                                    "episode_number" to episode.episodeNumber.toString(),
                                )
                            )
                        }
                    }
                }
            )
        }

        composable(NavRoutes.SETTINGS) {
            AddSourceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.ANIME_DETAIL,
            arguments = listOf(navArgument("animeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val animeId = backStackEntry.arguments?.getString("animeId")
                ?.takeIf { it.isNotBlank() }
                ?: return@composable
            AnimeDetailScreen(
                animeId = animeId,
                onNavigateBack = { navController.popBackStack() },
                onPlayEpisode = { episode ->
                    scope.launch {
                        runCatching {
                            MiruLog.i(
                                "MiruPlayNavigation",
                                "Episode playback requested",
                                mapOf(
                                    "episode_id" to episode.id,
                                    "anime_id" to episode.animeId,
                                    "episode_number" to episode.episodeNumber.toString(),
                                )
                            )
                            val source = episodePlaybackSourceResolver.build(episode)
                            val encodedPath = Uri.encode(source.uri)
                            val encodedSource = Uri.encode(source.mediaSourceId)
                            val encodedEpisode = Uri.encode(source.episodeId ?: "")
                            navController.navigate(
                                NavRoutes.player(
                                    uri = encodedPath,
                                    mediaSourceId = encodedSource,
                                    startPosition = source.startPosition,
                                    episodeId = encodedEpisode,
                                )
                            )
                        }.onFailure { error ->
                            MiruLog.e(
                                "MiruPlayNavigation",
                                "Failed to open episode playback",
                                error,
                                mapOf(
                                    "episode_id" to episode.id,
                                    "anime_id" to episode.animeId,
                                    "episode_number" to episode.episodeNumber.toString(),
                                )
                            )
                        }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.PLAYER_WITH_OPTIONS,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("mediaSourceId") {
                    type = NavType.StringType
                    defaultValue = "media"
                },
                navArgument("startPosition") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("episodeId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri") ?: return@composable
            val decodedUri = Uri.decode(uri)
            val mediaSourceId = backStackEntry.arguments?.getString("mediaSourceId") ?: "media"
            val startPosition = backStackEntry.arguments?.getLong("startPosition") ?: 0L
            val episodeId = backStackEntry.arguments?.getString("episodeId")
                ?.let(Uri::decode)
                ?.takeIf { it.isNotBlank() }
            val source = PlaybackSource(
                uri = decodedUri,
                mediaSourceId = mediaSourceId,
                startPosition = startPosition,
                subtitleTracks = emptyList(),
                episodeId = episodeId
            )
            PlayerScreen(
                playbackSource = source,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
