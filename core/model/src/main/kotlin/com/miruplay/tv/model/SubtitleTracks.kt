package com.miruplay.tv.model

fun buildExternalSubtitleTracks(value: String): List<SubtitleTrack> =
    buildExternalSubtitleTracks(value.split(';', '\n'))

fun buildExternalSubtitleTracks(paths: List<String>): List<SubtitleTrack> =
    paths
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .map(::externalSubtitleTrackFromPath)

fun matchingExternalSubtitlePaths(
    videoPath: String,
    siblingPaths: Iterable<String>,
): List<String> {
    val videoFileName = MediaPathConventions.fileName(videoPath)
    val videoStem = MediaPathConventions.stem(videoFileName).lowercase()
    if (videoStem.isBlank()) return emptyList()

    return siblingPaths
        .asSequence()
        .filter { candidate -> candidate != videoPath && candidate.isSupportedExternalSubtitlePath() }
        .filter { candidate ->
            val subtitleStem = MediaPathConventions.stem(MediaPathConventions.fileName(candidate)).lowercase()
            subtitleStem == videoStem || EXTERNAL_SUBTITLE_SUFFIX_SEPARATORS.any { separator ->
                subtitleStem.startsWith("$videoStem$separator")
            }
        }
        .distinct()
        .sortedBy { it.lowercase() }
        .toList()
}

fun externalSubtitleTrackFromPath(path: String): SubtitleTrack {
    val normalized = path.trim()
    return SubtitleTrack(
        language = subtitleLanguageFromPath(normalized),
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

private fun subtitleLanguageFromPath(path: String): String {
    val stem = MediaPathConventions.stem(MediaPathConventions.fileName(path))
    val candidate = listOf('.', '_')
        .map { separator -> stem.substringAfterLast(separator, "") }
        .firstOrNull(::isSubtitleLanguageCode)
        ?.lowercase()
        ?: return "und"
    return when (candidate) {
        "chs" -> "zh-Hans"
        "cht" -> "zh-Hant"
        "chi", "zho" -> "zh"
        "eng" -> "en"
        "jpn" -> "ja"
        else -> candidate.split('-', limit = 2).let { parts ->
            if (parts.size == 1) parts[0]
            else parts[0] + "-" + if (parts[1].length == 2) parts[1].uppercase()
            else parts[1].replaceFirstChar(Char::uppercase)
        }
    }
}

private fun isSubtitleLanguageCode(value: String): Boolean {
    val parts = value.split('-')
    return parts.size in 1..2 && parts.withIndex().all { (index, part) ->
        part.length in (if (index == 0) 2..3 else 2..4) && part.all(Char::isLetter)
    }
}

private fun String.isSupportedExternalSubtitlePath(): Boolean =
    substringAfterLast('.', "").lowercase() in SUPPORTED_EXTERNAL_SUBTITLE_EXTENSIONS

private val SUPPORTED_EXTERNAL_SUBTITLE_EXTENSIONS = setOf("ass", "ssa", "srt", "vtt")
private val EXTERNAL_SUBTITLE_SUFFIX_SEPARATORS = listOf(".", " ", "_", "-", "[")
