package com.miruplay.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

// Anime-themed colors
val AnimeRed = Color(0xFFE94560)
val AnimeRedDark = Color(0xFFC73E54)
val DarkBg = Color(0xFF1A1A2E)
val DarkSurface = Color(0xFF16213E)
val AccentBlue = Color(0xFF0F3460)
val TextPrimary = Color(0xFFEEEEEE)
val TextSecondary = Color(0xFFAAAAAA)
val FocusBorder = Color(0xFFE94560)
val CardBg = Color(0xFF1E2A45)
val ProgressGreen = Color(0xFF4CAF50)
val WarningYellow = Color(0xFFFFC107)

private val MiruPlayDarkColorScheme: ColorScheme = darkColorScheme(
    primary = AnimeRed,
    secondary = AccentBlue,
    background = DarkBg,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Color(0xFFCF6679),
)

private val MiruPlayLightColorScheme: ColorScheme = lightColorScheme(
    primary = AnimeRedDark,
    secondary = AccentBlue,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1A2E),
    onSurface = Color(0xFF1A1A2E),
)

@Composable
fun MiruPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MiruPlayDarkColorScheme else MiruPlayLightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}