package com.miruplay.tv.navigation

import com.miruplay.tv.design.MiruPlayRouteSurface
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.repository.AppMode
import com.miruplay.tv.repository.AppModeSelectionState

/**
 * Navigation route constants
 */
object NavRoutes {
    const val LIBRARY = MiruPlayRouteSurface.LIBRARY_ROUTE
    const val MODE_SELECTION = "mode-selection"
    const val DRAMA_HOME = "drama-home"
    const val DRAMA_DETAIL = "drama/{seriesId}"
    const val SETTINGS = MiruPlayRouteSurface.SETTINGS_ROUTE
    const val ANIME_DETAIL = "${MiruPlayRouteSurface.ANIME_ROUTE_PREFIX}/{animeId}"
    const val PLAYER = "${MiruPlayRouteSurface.PLAYER_ROUTE_PREFIX}/{uri}"
    const val PLAYER_WITH_OPTIONS = "$PLAYER?mediaSourceId={mediaSourceId}&startPosition={startPosition}&episodeId={episodeId}"
    
    fun animeDetail(animeId: String) =
        "${MiruPlayRouteSurface.ANIME_ROUTE_PREFIX}/${MediaPathConventions.encodePathSegment(animeId)}"

    fun dramaDetail(seriesId: String) =
        "drama/${MediaPathConventions.encodePathSegment(seriesId)}"

    fun player(
        uri: String,
        mediaSourceId: String = "media",
        startPosition: Long = 0L,
        episodeId: String = "",
    ) = "${MiruPlayRouteSurface.PLAYER_ROUTE_PREFIX}/$uri" +
        "?mediaSourceId=$mediaSourceId&startPosition=$startPosition&episodeId=$episodeId"

    fun homeFor(mode: AppMode): String =
        when (mode) {
            AppMode.ANIME -> LIBRARY
            AppMode.DRAMA -> DRAMA_HOME
        }

    fun launchDestinationFor(selectionState: AppModeSelectionState): String {
        val selectedMode = selectionState.currentAppMode
        if (!selectionState.hasCompletedModeSelection || selectedMode == null) {
            return MODE_SELECTION
        }
        return homeFor(selectedMode)
    }
}
