package com.miruplay.tv.repository

import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ProgressRecord

fun MediaSourceInfo.displayLabel(): String =
    "$name · ${type.name}"

fun List<MediaSourceInfo>.upsertById(source: MediaSourceInfo): List<MediaSourceInfo> =
    map { if (it.id == source.id) source else it }.let { updated ->
        if (updated.none { it.id == source.id }) updated + source else updated
    }

fun ProgressRecord.mediaDisplayName(): String =
    MediaPathConventions.fileName(episodeId).ifBlank { episodeId }
