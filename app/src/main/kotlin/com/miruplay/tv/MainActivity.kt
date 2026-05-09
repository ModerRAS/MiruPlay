package com.miruplay.tv

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.miruplay.tv.data.repository.MediaRepository
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.scanner.ScanCoordinator
import com.miruplay.tv.ui.detail.AnimeDetailScreen
import com.miruplay.tv.ui.library.LibraryScreen
import com.miruplay.tv.ui.player.PlayerScreen
import com.miruplay.tv.ui.settings.AddSourceScreen
import com.miruplay.tv.ui.theme.MiruPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var mediaRepository: MediaRepository

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
                MiruPlayNavigation()
            }
        }
    }
}

@Composable
fun MiruPlayNavigation() {
    val navController = rememberNavController()
    
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
                    val encodedPath = Uri.encode(episode.filePath)
                    navController.navigate("player/$encodedPath")
                }
            )
        }

        composable(
            route = "player/{uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri") ?: return@composable
            val decodedUri = Uri.decode(uri)
            val source = PlaybackSource(
                uri = decodedUri,
                mediaSourceId = "media",
                subtitleTracks = emptyList()
            )
            PlayerScreen(
                playbackSource = source,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}