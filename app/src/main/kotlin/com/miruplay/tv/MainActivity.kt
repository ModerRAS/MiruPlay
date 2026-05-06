package com.miruplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.ui.detail.AnimeDetailScreen
import com.miruplay.tv.ui.library.LibraryScreen
import com.miruplay.tv.ui.player.PlayerScreen
import com.miruplay.tv.ui.settings.AddSourceScreen
import com.miruplay.tv.ui.theme.MiruPlayTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    navController.navigate("player/${episode.filePath}")
                }
            )
        }
        
        composable(
            route = "player/{uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri") ?: return@composable
            val source = PlaybackSource(
                uri = uri,
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