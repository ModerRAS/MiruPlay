package com.miruplay.tv.scanner

import com.miruplay.tv.model.FileEntry

/**
 * Episode pattern detector interface
 */
interface EpisodeDetector {
    /**
     * Detect episode information from file name
     * @return EpisodeMatch if pattern matches, null otherwise
     */
    fun detectEpisode(fileName: String): EpisodeMatch?
    
    /**
     * Detect season number from file name
     */
    fun detectSeason(fileName: String): Int?
    
    /**
     * Extract anime name from file path
     */
    fun extractAnimeName(filePath: String): String?
}

/**
 * Detected episode match result
 */
data class EpisodeMatch(
    val animeName: String?,
    val seasonNumber: Int = 1,
    val episodeNumber: Int,
    val episodeTitle: String? = null,
    val isSpecial: Boolean = false,
    val isMultiPart: Boolean = false,
    val partLabel: String? = null,  // e.g., "a", "b" for "03a.mkv"
    val rawPattern: String,
    val confidence: Float = 1.0f
)

/**
 * Season group - collection of episodes for one season
 */
data class SeasonGroup(
    val seasonNumber: Int,
    val animeName: String,
    val episodes: List<FileEntry>,
    val totalEpisodes: Int,
    val hasSpecials: Boolean = false
)
