package com.miruplay.tv.model

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
        title = subtitleFileName(normalized),
        isExternal = true,
        path = normalized,
        format = subtitleFormatFromPath(normalized),
    )
}

fun subtitleFormatFromPath(path: String): SubtitleFormat =
    when (subtitleFileName(path).substringAfterLast('.', "").lowercase()) {
        "ass" -> SubtitleFormat.ASS
        "ssa" -> SubtitleFormat.SSA
        "vtt" -> SubtitleFormat.VTT
        else -> SubtitleFormat.SRT
    }

private fun subtitleLanguageFromPath(path: String): String {
    val stem = MediaPathConventions.stem(subtitleFileName(path)).replace('_', '-')
    val candidate = SUBTITLE_LANGUAGE_SUFFIX.find(stem)
        ?.groupValues
        ?.get(1)
        ?.lowercase()
        ?: return "und"
    return when (candidate) {
        "chs", "sc" -> "zh-Hans"
        "cht", "tc" -> "zh-Hant"
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

private fun subtitleFileName(path: String): String {
    val pathWithoutUrlSuffix = if ("://" in path) path.substringBefore('?').substringBefore('#') else path
    val encodedName = pathWithoutUrlSuffix.substringAfterLast('/').substringAfterLast('\\')
    return runCatching {
        URLDecoder.decode(encodedName.replace("+", "%2B"), StandardCharsets.UTF_8)
    }.getOrDefault(encodedName)
}

private fun String.isSupportedExternalSubtitlePath(): Boolean =
    subtitleFileName(this).substringAfterLast('.', "").lowercase() in SUPPORTED_EXTERNAL_SUBTITLE_EXTENSIONS

private val SUBTITLE_LANGUAGE_SUFFIX = Regex(
    "(?:^|[.\\s\\-\\[])([a-zA-Z]{2,3}(?:-[a-zA-Z]{2,4})?)\\]?$",
)
private val SUPPORTED_EXTERNAL_SUBTITLE_EXTENSIONS = setOf("ass", "ssa", "srt", "vtt")
private val EXTERNAL_SUBTITLE_SUFFIX_SEPARATORS = listOf(".", " ", "_", "-", "[")
