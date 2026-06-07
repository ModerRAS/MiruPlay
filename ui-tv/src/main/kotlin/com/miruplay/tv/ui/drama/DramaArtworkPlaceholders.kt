package com.miruplay.tv.ui.drama

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.miruplay.tv.ui.theme.AccentBlue
import com.miruplay.tv.ui.theme.AnimeRed
import com.miruplay.tv.ui.theme.CardBg
import com.miruplay.tv.ui.theme.DarkSurface
import com.miruplay.tv.ui.theme.TextPrimary
import com.miruplay.tv.ui.theme.TextSecondary

@Composable
internal fun DramaPosterArtworkPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        DarkSurface,
                        AccentBlue.copy(alpha = 0.86f),
                        AnimeRed.copy(alpha = 0.34f),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.LiveTv,
                contentDescription = null,
                tint = TextPrimary.copy(alpha = 0.72f),
                modifier = Modifier.size(24.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = dramaArtworkMonogram(title),
                color = TextPrimary.copy(alpha = 0.92f),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = "电视剧",
                color = TextSecondary.copy(alpha = 0.9f),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun DramaBackdropArtworkPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        CardBg,
                        DarkSurface,
                        AccentBlue.copy(alpha = 0.74f),
                    ),
                ),
            ),
    ) {
        Text(
            text = dramaArtworkMonogram(title),
            color = Color.White.copy(alpha = 0.14f),
            fontSize = 132.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun dramaArtworkMonogram(title: String): String {
    val normalized = title.trim()
    if (normalized.isBlank()) return "TV"

    val cjkChars = normalized.filter { it.isCjkUnifiedIdeograph() }
    if (cjkChars.isNotBlank()) {
        return cjkChars.take(2)
    }

    val tokenInitials = normalized
        .split(Regex("""[^\p{L}\p{Nd}]+"""))
        .mapNotNull { token -> token.firstOrNull()?.takeIf { it.isLetterOrDigit() } }
        .joinToString(separator = "") { it.uppercaseChar().toString() }
        .take(2)
    if (tokenInitials.isNotBlank()) {
        return tokenInitials
    }

    return normalized
        .filter { it.isLetterOrDigit() }
        .take(2)
        .uppercase()
        .ifBlank { "TV" }
}

private fun Char.isCjkUnifiedIdeograph(): Boolean {
    val code = code
    return code in 0x3400..0x4DBF ||
        code in 0x4E00..0x9FFF ||
        code in 0xF900..0xFAFF
}
