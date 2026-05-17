package com.miruplay.tv.data.repository

import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.resolvePlayableUri as resolveRepositoryPlayableUri

suspend fun resolvePlayableUri(
    path: String,
    episodeId: String,
    mediaRepository: MediaSourceRepository
): String = resolveRepositoryPlayableUri(path, episodeId, mediaRepository)
