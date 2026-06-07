package com.miruplay.tv.ui.drama

import com.miruplay.tv.model.DramaEpisode
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.progressFraction

private const val MINUTE_MS = 60_000L

internal fun dramaEpisodeProgressIndicatorFraction(
    episode: DramaEpisode,
    progress: ProgressRecord?,
): Float =
    dramaProgressIndicatorFraction(
        exactFraction = episode.toPlaybackEpisode().progressFraction(progress),
        progress = progress,
    )

internal fun dramaProgressIndicatorFraction(
    exactFraction: Float,
    progress: ProgressRecord?,
): Float {
    val normalizedExactFraction = exactFraction.coerceIn(0f, 1f)
    if (normalizedExactFraction > 0f) {
        return normalizedExactFraction
    }

    val positionMs = progress?.positionMs?.coerceAtLeast(0L) ?: return 0f
    if (positionMs <= 0L) {
        return 0f
    }

    // Drama entries often do not have a stable duration yet.
    // Use a coarse visual bucket so the UI still shows "watched partway"
    // without pretending to know the real percentage.
    return when {
        positionMs >= 45 * MINUTE_MS -> 0.84f
        positionMs >= 25 * MINUTE_MS -> 0.68f
        positionMs >= 10 * MINUTE_MS -> 0.52f
        positionMs >= 3 * MINUTE_MS -> 0.34f
        else -> 0.18f
    }
}

private fun DramaEpisode.toPlaybackEpisode(): Episode =
    Episode(
        id = id,
        animeId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        title = title,
        filePath = filePath,
        fileName = fileName,
    )
