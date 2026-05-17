package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode

interface MetadataRepository {
    suspend fun cacheMetadata(anime: Anime): Result<Unit>
    suspend fun getCachedMetadata(animeId: String): Result<Anime?>
    suspend fun getCachedEpisode(episodeId: String): Result<Episode?>
    suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>>
    suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit>
    suspend fun invalidateCache(animeId: String): Result<Unit>
}
