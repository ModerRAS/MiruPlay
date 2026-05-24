package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord

fun Episode.toWebControlEpisodeWithProgress(progress: ProgressRecord?): EpisodeWithProgressDto =
    EpisodeWithProgressDto(
        episode = this,
        progressMs = progress?.positionMs ?: 0L,
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
        positionMs = positionMs,
        lastWatched = lastWatched,
        playCount = playCount,
        episode = episode,
        anime = anime,
    )
