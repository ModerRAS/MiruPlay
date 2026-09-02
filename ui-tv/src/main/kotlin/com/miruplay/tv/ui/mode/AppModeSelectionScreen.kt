package com.miruplay.tv.ui.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.ui.components.OverscanContainer
import com.miruplay.tv.ui.components.TvButton
import com.miruplay.tv.ui.components.rememberInitialFocusHandle
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary
import com.miruplay.tv.ui.theme.TvTypography

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppModeSelectionScreen(
    onSelectMode: (AppMode) -> Unit,
) {
    val primaryFocus = rememberInitialFocusHandle(
        key = AppMode.ANIME,
    )

    OverscanContainer(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSurface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "选择首页模式",
                style = TvTypography.title,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "第一次打开时，先选 MiruPlay 默认首页：动漫、电视剧或音乐。",
                style = TvTypography.body,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
            Spacer(Modifier.height(28.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvButton(
                    text = "动漫",
                    onClick = { onSelectMode(AppMode.ANIME) },
                    modifier = Modifier
                        .width(180.dp)
                        .then(primaryFocus.modifier())
                )
                Spacer(Modifier.width(16.dp))
                TvButton(
                    text = "电视剧",
                    onClick = { onSelectMode(AppMode.DRAMA) },
                    modifier = Modifier.width(180.dp)
                )
                Spacer(Modifier.width(16.dp))
                TvButton(
                    text = "音乐",
                    onClick = { onSelectMode(AppMode.MUSIC) },
                    modifier = Modifier.width(180.dp)
                )
            }
        }
    }
}
