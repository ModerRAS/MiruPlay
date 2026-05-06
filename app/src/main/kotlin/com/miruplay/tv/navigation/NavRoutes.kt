package com.miruplay.tv.navigation

/**
 * Navigation route constants
 */
object NavRoutes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ANIME_DETAIL = "anime/{animeId}"
    const val PLAYER = "player/{uri}"
    
    fun animeDetail(animeId: String) = "anime/$animeId"
    fun player(uri: String) = "player/$uri"
}