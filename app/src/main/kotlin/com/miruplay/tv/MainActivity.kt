package com.miruplay.tv

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.repository.EpisodePlaybackSourceResolver
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.navigation.NavRoutes
import com.miruplay.tv.ui.detail.AnimeDetailScreen
import com.miruplay.tv.ui.library.LibraryScreen
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
    @Inject lateinit var webControlNavigator: WebControlNavigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MiruLog.i(
            "MainActivity",
            "Main activity created",
            mapOf(
                "has_saved_state" to (savedInstanceState != null).toString(),
                "intent_action" to intent?.action.orEmpty(),
                "has_test_source" to (intent.getStringExtra("test_local_path") != null).toString(),
            )
        )

        // Test hook: automatically add a Local source when launched with extra.
        val testSourcePath = intent.getStringExtra("test_local_path")
        val testSourceName = intent.getStringExtra("test_local_name")
            ?.takeIf { it.isNotBlank() }
            ?: "Test Local"

        if (testSourcePath != null) {
            lifecycleScope.launch {
                addLaunchTestSource(
                    path = testSourcePath,
                    name = testSourceName,
                )
                renderContent()
            }
        } else {
            renderContent()
        }
    }

    private suspend fun addLaunchTestSource(path: String, name: String) {
        MiruLog.i(
            "MainActivity",
            "Adding test local source from launch extra",
            mapOf("source_name" to name)
        )
        Log.i("MainActivity", "Adding test local source from launch extra")
        val source = MediaSourceInfo(
            name = name,
            type = MediaSourceType.LOCAL,
            connectionInfo = mapOf(
                "path" to path,
                "url" to path,
                "disableOnlineMetadata" to "true"
            )
        )
        mediaRepository.addSource(source)
            .onSuccess { id ->
                MiruLog.i(
                    "MainActivity",
                    "Test local source added",
                    mapOf(
                        "source_id" to id.toString(),
                        "source_name" to name,
                    )
                )
                Log.i("MainActivity", "Test local source added")
            }
            .onError { error ->
                MiruLog.w(
                    "MainActivity",
                    "Test local source add failed",
                    attributes = mapOf(
                        "source_name" to name,
                        "error" to error.toUserMessage(),
                    )
                )
                Log.w("MainActivity", "Test local source add failed: ${error.toUserMessage()}")
            }
    }

    private fun renderContent() {
        setContent {
            MiruPlayTheme {
                MiruPlayNavigation(
                    mediaRepository = mediaRepository,
                    progressRepository = progressRepository,
                    webControlNavigator = webControlNavigator
                )
            }
        }
    }
}

@Composable
fun MiruPlayNavigation(
    mediaRepository: MediaSourceRepository,
    progressRepository: PlaybackProgressRepository,
    webControlNavigator: WebControlNavigator
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
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
        startDestination = NavRoutes.LIBRARY
    ) {
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
