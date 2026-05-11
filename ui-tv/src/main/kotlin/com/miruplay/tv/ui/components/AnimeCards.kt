package com.miruplay.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.miruplay.tv.model.Anime
import com.miruplay.tv.ui.theme.AnimeRed
import com.miruplay.tv.ui.theme.CardBg
import com.miruplay.tv.ui.theme.FocusBorder
import com.miruplay.tv.ui.theme.ProgressGreen
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary

@Composable
fun AnimePosterCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 180.dp,
    height: Dp = 270.dp,
    subtitle: String? = null,
    progress: Float? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.06f else 1f, label = "posterScale")

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        RemoteImage(
            url = anime.posterUrl,
            contentDescription = anime.displayTitle(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.46f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = anime.displayTitle(),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subtitleText = subtitle ?: anime.posterSubtitle()
            if (subtitleText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitleText,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (progress != null && progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(ProgressGreen)
                )
            }
        }
    }
}

@Composable
fun FeatureAnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.03f else 1f, label = "featureScale")

    Box(
        modifier = modifier
            .width(560.dp)
            .height(300.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        RemoteImage(
            url = anime.fanartUrl ?: anime.posterUrl,
            contentDescription = anime.displayTitle(),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.24f))
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            RemoteImage(
                url = anime.posterUrl,
                contentDescription = anime.displayTitle(),
                modifier = Modifier
                    .width(118.dp)
                    .height(170.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(18.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = anime.displayTitle(),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = anime.featureSubtitle(),
                    color = TextSecondary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (anime.summary.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = anime.summary,
                        color = TextPrimary.copy(alpha = 0.86f),
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AnimeRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

fun Anime.displayTitle(): String = titleCn?.takeIf { it.isNotBlank() } ?: title.ifBlank { id }

private fun Anime.posterSubtitle(): String {
    val parts = buildList {
        if (episodeCount > 0) add("${episodeCount} 集")
        if (rating > 0f) add("Bangumi ${"%.1f".format(rating)}")
    }
    return parts.joinToString(" · ")
}

private fun Anime.featureSubtitle(): String {
    val parts = buildList {
        airDate?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (episodeCount > 0) add("全 $episodeCount 话")
        if (rating > 0f) add("评分 ${"%.1f".format(rating)}")
    }
    return parts.joinToString(" · ").ifBlank { "本地媒体库" }
}
