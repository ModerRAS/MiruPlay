package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.PlaybackTimingConventions
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.coercePlaybackPosition

fun Episode.toWebControlEpisodeWithProgress(progress: ProgressRecord?): EpisodeWithProgressDto =
    EpisodeWithProgressDto(
        episode = this,
        progressMs = progress?.let { coercePlaybackPosition(it.positionMs) } ?: 0L,
        lastWatched = progress?.lastWatched ?: 0L,
        playCount = progress?.playCount ?: 0,
    )

suspend fun Anime.toWebControlAnimeDetail(
    episodes: List<Episode>,
    progressForEpisode: suspend (Episode) -> ProgressRecord?,
): AnimeDetailDto =
    AnimeDetailDto(
        anime = this,
        episodes = episodes.map { episode ->
            episode.toWebControlEpisodeWithProgress(progressForEpisode(episode))
        },
    )

fun ProgressRecord.toWebControlContinueWatching(
    episode: Episode?,
    anime: Anime?,
): ContinueWatchingDto =
    ContinueWatchingDto(
        progressEpisodeId = episodeId,
        positionMs = episode?.coercePlaybackPosition(positionMs)
            ?: PlaybackTimingConventions.coercePlaybackPositionMs(positionMs),
        lastWatched = lastWatched,
        playCount = playCount,
        episode = episode,
        anime = anime,
    )
