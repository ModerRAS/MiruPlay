package com.miruplay.tv

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.repository.MediaRepository
import com.miruplay.tv.data.repository.ProgressRepository
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.resumePosition
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
import java.net.URLEncoder
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var mediaRepository: MediaRepository
    @Inject lateinit var progressRepository: ProgressRepository
    @Inject lateinit var webControlNavigator: WebControlNavigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Test hook: automatically add a Local source when launched with extra
        val testSourcePath = intent.getStringExtra("test_local_path")

        if (testSourcePath != null) {
            lifecycleScope.launchWhenStarted {
                val source = MediaSourceInfo(
                    name = "Test Local",
                    type = MediaSourceType.LOCAL,
                    connectionInfo = mapOf("path" to testSourcePath, "url" to testSourcePath)
                )
                mediaRepository.addSource(source)
            }
        }

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
    mediaRepository: MediaRepository,
    progressRepository: ProgressRepository,
    webControlNavigator: WebControlNavigator
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(webControlNavigator, navController) {
        webControlNavigator.commands.collect { command ->
            val payload = command.payload
            if (command.type == WebControlNavigator.TYPE_OPEN_PLAYER && payload != null) {
                val source = Json.decodeFromJsonElement<WebPlaybackSource>(payload)
                val encodedPath = Uri.encode(source.uri)
                val encodedSource = Uri.encode(source.mediaSourceId)
                val encodedEpisode = Uri.encode(source.episodeId ?: "")
                navController.navigate(
                    "player/$encodedPath?mediaSourceId=$encodedSource&startPosition=${source.startPositionMs}&episodeId=$encodedEpisode"
                ) {
                    launchSingleTop = true
                }
            }
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = "library"
    ) {
        composable("library") {
            LibraryScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToDetail = { animeId ->
                    navController.navigate("anime/$animeId")
                }
            )
        }

        composable("settings") {
            AddSourceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "anime/{animeId}",
            arguments = listOf(navArgument("animeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val animeId = backStackEntry.arguments?.getString("animeId") ?: return@composable
            AnimeDetailScreen(
                animeId = animeId,
                onNavigateBack = { navController.popBackStack() },
                onPlayEpisode = { episode ->
                    scope.launch {
                        val playableUri = resolvePlayableUri(
                            path = episode.filePath,
                            episodeId = episode.id,
                            mediaRepository = mediaRepository
                        )
                        val encodedPath = Uri.encode(playableUri)
                        val encodedSource = Uri.encode(episode.animeId)
                        val encodedEpisode = Uri.encode(episode.id)
                        val startPosition = resumePositionFor(episode, progressRepository)
                        navController.navigate(
                            "player/$encodedPath?mediaSourceId=$encodedSource&startPosition=$startPosition&episodeId=$encodedEpisode"
                        )
                    }
                }
            )
        }

        composable(
            route = "player/{uri}?mediaSourceId={mediaSourceId}&startPosition={startPosition}&episodeId={episodeId}",
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

private suspend fun resumePositionFor(
    episode: Episode,
    progressRepository: ProgressRepository
): Long {
    val progress = progressRepository.getProgress(episode.id).getOrNull()
    return episode.resumePosition(progress)
}

private suspend fun resolvePlayableUri(
    path: String,
    episodeId: String,
    mediaRepository: MediaRepository
): String {
    if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("content://")) {
        return path
    }

    val sources = when (val result = mediaRepository.getSources()) {
        is Result.Success -> result.data
        is Result.Error -> emptyList()
    }

    val sourceId = episodeId.substringBefore(':').toLongOrNull()
    val source = if (sourceId != null) {
        sources.firstOrNull { it.id == sourceId }
    } else {
        sources.firstOrNull { source ->
            source.matchesPath(path)
        }
    }

    return if (source?.type == MediaSourceType.WEBDAV) {
        joinRemoteUrl(source.connectionInfo["url"].orEmpty(), path)
    } else {
        path
    }
}

private fun MediaSourceInfo.matchesPath(path: String): Boolean {
    return when (type) {
        MediaSourceType.LOCAL -> {
            val root = connectionInfo["path"] ?: connectionInfo["url"] ?: return false
            path == root || path.startsWith("${root.trimEnd('/')}/")
        }
        MediaSourceType.WEBDAV -> path.startsWith("/")
        MediaSourceType.SMB -> path.startsWith("smb://")
    }
}

private fun joinRemoteUrl(baseUrl: String, path: String): String {
    val base = baseUrl.trimEnd('/')
    if (base.isBlank()) return path
    if (path.startsWith(base)) return path
    val encodedPath = path
        .trimStart('/')
        .split('/')
        .joinToString("/") { segment ->
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
    }
    return "$base/$encodedPath"
}
