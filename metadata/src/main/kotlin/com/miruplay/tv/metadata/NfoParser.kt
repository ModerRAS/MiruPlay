package com.miruplay.tv.metadata

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.NfoMetadata
import com.miruplay.tv.model.TvShowNfoMetadata

/**
 * NFO file parser interface
 */
interface NfoParser {
    /**
     * Parse episode NFO file
     */
    suspend fun parseEpisodeNfo(nfoPath: String): Result<NfoMetadata>

    /**
     * Parse TV show NFO file
     */
    suspend fun parseTvShowNfo(nfoPath: String): Result<TvShowNfoMetadata>

    /**
     * Detect NFO type from file content
     */
    suspend fun detectNfoType(nfoContent: String): NfoType
}

/**
 * NFO file types
 */
enum class NfoType {
    EPISODE,    // episodedetails
    TVSHOW,     // tvshow
    MOVIE,      // movie
    MUSICVIDEO, // musicvideo
    UNKNOWN
}
