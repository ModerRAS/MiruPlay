package com.miruplay.tv.ui.music

import androidx.compose.foundation.background
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
            Text(text = "音乐库", style = TvTypography.title, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Text(text = "MUSIC 模式已就绪，扫描后将在此展示专辑", style = TvTypography.body, color = TextSecondary)
            Spacer(Modifier.height(24.dp))
            TvButton(text = "设置", onClick = onNavigateToSettings)
            Spacer(Modifier.height(12.dp))
            TvButton(text = "打开示例专辑", onClick = { onNavigateToDetail("demo-album") })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MusicAlbumDetailPlaceholder(albumId: String, onNavigateBack: () -> Unit, onPlayTrack: (String) -> Unit) {
    OverscanContainer(modifier = Modifier.fillMaxSize().background(DarkSurface)) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "专辑详情: $albumId", style = TvTypography.title, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            TvButton(text = "返回", onClick = onNavigateBack)
            Spacer(Modifier.height(12.dp))
            TvButton(text = "播放示例音轨", onClick = { onPlayTrack("demo-track") })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MusicPlayerPlaceholder(trackId: String, onNavigateBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(DarkSurface), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "音乐播放: $trackId", style = TvTypography.title, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Text(text = "Phase 1 占位，DSP 与 SRC 三档已在设置中", style = TvTypography.body, color = TextSecondary)
            Spacer(Modifier.height(24.dp))
            TvButton(text = "返回", onClick = onNavigateBack)
        }
    }
}
