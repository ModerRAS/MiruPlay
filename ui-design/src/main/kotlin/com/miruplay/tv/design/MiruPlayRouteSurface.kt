package com.miruplay.tv.design

object MiruPlayRouteSurface {
    const val LIBRARY_ID = "library"
    const val DETAILS_ID = "details"
    const val PLAYER_ID = "player"
    const val SETTINGS_ID = "settings"

    const val LIBRARY_ROUTE = LIBRARY_ID
    const val SETTINGS_ROUTE = SETTINGS_ID
    const val ANIME_ROUTE_PREFIX = "anime"
    const val PLAYER_ROUTE_PREFIX = PLAYER_ID

    val library = Section(
        id = LIBRARY_ID,
        menuLabel = "Library",
        title = "Explore",
        subtitle = "Local media library - Bangumi metadata",
        summary = "Sources and indexed episodes",
    )

    val details = Section(
        id = DETAILS_ID,
        menuLabel = "Details",
        title = "Details",
        subtitle = "Bangumi matching and index metadata",
        summary = "Search, batch review, undo",
    )

    val player = Section(
        id = PLAYER_ID,
        menuLabel = "Player",
        title = "Player",
        subtitle = "mpv playback controls - RIFE interpolation",
        summary = "Launch, seek, runtime",
    )

    val settings = Section(
        id = SETTINGS_ID,
        menuLabel = "Settings",
        title = "Settings",
        subtitle = "Automation, RSS, and desktop services",
        summary = "CloudDrive2 and RSS",
    )

    val desktopSectionOrder = listOf(library, details, player, settings)

    data class Section(
        val id: String,
        val menuLabel: String,
        val title: String,
        val subtitle: String,
        val summary: String,
    )
}
