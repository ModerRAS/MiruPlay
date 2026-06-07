package com.miruplay.tv.model

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Anime.displayTitle(): String =
    titleCn?.takeIf { it.isNotBlank() } ?: title.ifBlank { id }

fun Anime.posterSubtitle(): String {
    val parts = buildList {
        if (episodeCount > 0) add("${episodeCount} 集")
        if (rating > 0f) add("Bangumi ${"%.1f".format(rating)}")
    }
    return parts.joinToString(" · ")
}

fun Anime.featureSubtitle(): String {
    val parts = buildList {
        airDate?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (episodeCount > 0) add("全 $episodeCount 话")
        if (rating > 0f) add("评分 ${"%.1f".format(rating)}")
    }
    return parts.joinToString(" · ").ifBlank { mediaSourceLocalLibraryFallbackName() }
}

fun PlaybackSource.displayTitle(): String {
    val name = uri
        .substringAfterLast("/")
        .substringAfterLast("\\")
        .substringBefore("?")
        .substringBeforeLast(".")
    return runCatching {
        URLDecoder.decode(name, StandardCharsets.UTF_8.name())
    }.getOrDefault(name).ifBlank { mediaSourceId }
}

fun Episode.playbackDisplayTitle(): String =
    detailEpisodeTitleLabel(episodeNumber, title)

fun ScraperResult.displayTitle(): String =
    titleCn?.takeIf { it.isNotBlank() } ?: title

fun formatFileSize(bytes: Long): String {
    if (bytes < 1_024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1_024.0
    var unitIndex = 0
    while (value >= 1_024.0 && unitIndex < units.lastIndex) {
        value /= 1_024.0
        unitIndex++
    }
    return "%.${if (value >= 10.0) 0 else 1}f %s".format(value, units[unitIndex])
}

fun formatPlaybackPosition(positionMs: Long): String {
    val totalSeconds = (positionMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun formatLocalTimestamp(epochMillis: Long): String? =
    epochMillis
        .takeIf { it > 0L }
        ?.let { LOCAL_TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(it)) }

fun formatShortLocalTimestamp(epochMillis: Long): String? =
    epochMillis
        .takeIf { it > 0L }
        ?.let { SHORT_LOCAL_TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(it)) }

private val LOCAL_TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

private val SHORT_LOCAL_TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
