package com.miruplay.tv.model

fun buildExternalSubtitleTracks(value: String): List<SubtitleTrack> =
    value
        .split(';', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map(::externalSubtitleTrackFromPath)

fun externalSubtitleTrackFromPath(path: String): SubtitleTrack {
    val normalized = path.trim()
    return SubtitleTrack(
        title = normalized.substringAfterLast('/').substringAfterLast('\\'),
        isExternal = true,
        path = normalized,
        format = subtitleFormatFromPath(normalized),
    )
}

fun subtitleFormatFromPath(path: String): SubtitleFormat =
    when (path.substringAfterLast('.', "").lowercase()) {
        "ass" -> SubtitleFormat.ASS
        "ssa" -> SubtitleFormat.SSA
        "vtt" -> SubtitleFormat.VTT
        else -> SubtitleFormat.SRT
    }
