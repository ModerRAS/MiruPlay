package com.miruplay.tv.model

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun buildExternalAudioTracks(paths: List<String>): List<ExternalAudioTrack> =
    paths
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .map { path ->
            ExternalAudioTrack(
                language = sidecarLanguageFromPath(path),
                title = externalAudioFileName(path),
                path = path,
            )
        }

fun matchingExternalAudioPaths(
    videoPath: String,
    siblingPaths: Iterable<String>,
): List<String> {
    val videoStem = MediaPathConventions.stem(videoPath).lowercase()
    if (videoStem.isBlank()) return emptyList()
    return siblingPaths
        .asSequence()
        .filter { candidate -> candidate != videoPath && candidate.isSupportedExternalAudioPath() }
        .filter { candidate ->
            val audioStem = MediaPathConventions.stem(candidate).lowercase()
            audioStem == videoStem || SIDECAR_SUFFIX_SEPARATORS.any { separator ->
                audioStem.startsWith("$videoStem$separator")
            }
        }
        .distinct()
        .sortedBy(String::lowercase)
        .toList()
}

internal fun sidecarLanguageFromPath(path: String): String {
    val stem = MediaPathConventions.stem(externalAudioFileName(path)).replace('_', '-')
    val candidate = SIDECAR_LANGUAGE_SUFFIX.find(stem)
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

private fun String.isSupportedExternalAudioPath(): Boolean =
    externalAudioFileName(this).substringAfterLast('.', "").lowercase() in SUPPORTED_EXTERNAL_AUDIO_EXTENSIONS

private fun externalAudioFileName(path: String): String {
    val pathWithoutUrlSuffix = if ("://" in path) path.substringBefore('?').substringBefore('#') else path
    val encodedName = pathWithoutUrlSuffix.substringAfterLast('/').substringAfterLast('\\')
    return runCatching {
        URLDecoder.decode(encodedName.replace("+", "%2B"), StandardCharsets.UTF_8)
    }.getOrDefault(encodedName)
}

private val SUPPORTED_EXTERNAL_AUDIO_EXTENSIONS = setOf(
    "aac", "ac3", "dts", "eac3", "flac", "m4a", "mka", "mp3", "ogg", "opus", "wav",
)
private val SIDECAR_LANGUAGE_SUFFIX = Regex(
    "(?:^|[.\\s\\-\\[])([a-zA-Z]{2,3}(?:-[a-zA-Z]{2,4})?)\\]?$",
)
private val SIDECAR_SUFFIX_SEPARATORS = listOf(".", " ", "_", "-", "[")
