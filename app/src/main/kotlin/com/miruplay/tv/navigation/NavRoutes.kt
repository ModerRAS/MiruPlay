package com.miruplay.tv.navigation

import com.miruplay.tv.design.MiruPlayRouteSurface
import com.miruplay.tv.model.MediaPathConventions

/**
 * Navigation route constants
 */
object NavRoutes {
    const val LIBRARY = MiruPlayRouteSurface.LIBRARY_ROUTE
    const val SETTINGS = MiruPlayRouteSurface.SETTINGS_ROUTE
    const val ANIME_DETAIL = "${MiruPlayRouteSurface.ANIME_ROUTE_PREFIX}/{animeId}"
    const val PLAYER = "${MiruPlayRouteSurface.PLAYER_ROUTE_PREFIX}/{uri}"
    const val PLAYER_WITH_OPTIONS = "$PLAYER?mediaSourceId={mediaSourceId}&startPosition={startPosition}&episodeId={episodeId}"
    
    fun animeDetail(animeId: String) =
        "${MiruPlayRouteSurface.ANIME_ROUTE_PREFIX}/${MediaPathConventions.encodePathSegment(animeId)}"

    fun player(
        uri: String,
        mediaSourceId: String = "media",
        startPosition: Long = 0L,
        episodeId: String = "",
    ) = "${MiruPlayRouteSurface.PLAYER_ROUTE_PREFIX}/$uri" +
        "?mediaSourceId=$mediaSourceId&startPosition=$startPosition&episodeId=$episodeId"
}
