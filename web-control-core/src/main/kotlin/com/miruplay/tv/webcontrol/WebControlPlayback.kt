package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.resumePosition

fun PlayEpisodeRequest.startPositionFor(
    episode: Episode,
    progress: ProgressRecord?,
): Long =
    startPositionMs ?: episode.resumePosition(progress)
