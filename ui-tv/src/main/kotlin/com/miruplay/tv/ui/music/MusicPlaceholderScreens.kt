package com.miruplay.tv.ui.music

import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.miruplay.tv.ui.tv.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.TvButton
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MusicLibraryPlaceholder(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    OverscanContainer(modifier = Modifier.fillMaxSize().background(DarkSurface)) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.music_library_title), style = TvTypography.title, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Text(text = stringResource(R.string.music_library_ready), style = TvTypography.body, color = TextSecondary)
            Spacer(Modifier.height(24.dp))
            TvButton(text = stringResource(R.string.music_settings), onClick = onNavigateToSettings)
            Spacer(Modifier.height(12.dp))
            TvButton(text = stringResource(R.string.music_open_demo), onClick = { onNavigateToDetail("demo-album") })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MusicAlbumDetailPlaceholder(albumId: String, onNavigateBack: () -> Unit, onPlayTrack: (String) -> Unit) {
    OverscanContainer(modifier = Modifier.fillMaxSize().background(DarkSurface)) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.music_album_detail_title, albumId), style = TvTypography.title, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            TvButton(text = stringResource(R.string.music_back), onClick = onNavigateBack)
            Spacer(Modifier.height(12.dp))
            TvButton(text = stringResource(R.string.music_play_demo), onClick = { onPlayTrack("demo-track") })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MusicPlayerPlaceholder(trackId: String, onNavigateBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(DarkSurface), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.music_player_track_title, trackId), style = TvTypography.title, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Text(text = stringResource(R.string.music_phase1_note), style = TvTypography.body, color = TextSecondary)
            Spacer(Modifier.height(24.dp))
            TvButton(text = stringResource(R.string.music_back), onClick = onNavigateBack)
        }
    }
}
