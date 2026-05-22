package com.miruplay.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.miruplay.tv.design.MiruPlayPalette

// Anime-themed colors
val AnimeRed = Color(MiruPlayPalette.ANIME_RED_ARGB)
val AnimeRedDark = Color(MiruPlayPalette.ANIME_RED_DARK_ARGB)
val DarkBg = Color(MiruPlayPalette.DARK_BG_ARGB)
val DarkSurface = Color(MiruPlayPalette.DARK_SURFACE_ARGB)
val AccentBlue = Color(MiruPlayPalette.ACCENT_BLUE_ARGB)
val TextPrimary = Color(MiruPlayPalette.TEXT_PRIMARY_ARGB)
val TextSecondary = Color(MiruPlayPalette.TEXT_SECONDARY_ARGB)
val FocusBorder = Color(MiruPlayPalette.FOCUS_BORDER_ARGB)
val CardBg = Color(MiruPlayPalette.CARD_BG_ARGB)
val ProgressGreen = Color(MiruPlayPalette.PROGRESS_GREEN_ARGB)
val WarningYellow = Color(MiruPlayPalette.WARNING_YELLOW_ARGB)

private val MiruPlayDarkColorScheme: ColorScheme = darkColorScheme(
    primary = AnimeRed,
    secondary = AccentBlue,
    background = DarkBg,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Color(MiruPlayPalette.ERROR_ARGB),
)

private val MiruPlayLightColorScheme: ColorScheme = lightColorScheme(
    primary = AnimeRedDark,
    secondary = AccentBlue,
    background = Color(MiruPlayPalette.LIGHT_BACKGROUND_ARGB),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = DarkBg,
    onSurface = DarkBg,
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
