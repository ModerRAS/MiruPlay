package com.miruplay.tv.repository

import com.miruplay.tv.model.MediaPathConventions

data class MediaIndexPosterGroup(
    val title: String,
    val entries: List<MediaIndexEntry>,
    val animeId: String = entries.posterGroupAnimeId(mergeSameAnimeEnabled = false),
) {
    val primaryEntry: MediaIndexEntry =
        entries
            .sortedWith(compareBy<MediaIndexEntry> { it.episodeNumber ?: Int.MAX_VALUE }.thenBy { it.path })
            .first()
    val entryPaths: Set<String> = entries.map { it.path }.toSet()
    val lastModified: Long = entries.maxOfOrNull { it.lastModified } ?: 0L
    val subtitle: String = buildString {
        append(entries.size)
        append(" episode")
        if (entries.size != 1) append('s')
        primaryEntry.seasonNumber?.let { append(" · S").append(it) }
        primaryEntry.metadataSource?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }
}

fun List<MediaIndexEntry>.toMediaIndexPosterGroups(
    mergeSameAnimeEnabled: Boolean = false,
): List<MediaIndexPosterGroup> =
    mediaFilesOnly()
        .groupBy { it.posterGroupingKey(mergeSameAnimeEnabled) }
        .map { (_, groupEntries) ->
            MediaIndexPosterGroup(
                title = groupEntries.posterGroupTitle(),
                entries = groupEntries,
                animeId = groupEntries.posterGroupAnimeId(mergeSameAnimeEnabled),
            )
        }
        .sortedBy { it.title.lowercase() }

fun MediaIndexEntry.posterTitle(): String =
    metadataTitle?.takeIf { it.isNotBlank() }
        ?: animeName?.takeIf { it.isNotBlank() }
        ?: MediaPathConventions.stem(path).takeIf { it.isNotBlank() }
        ?: path.substringAfterLast('/').substringAfterLast('\\')

fun MediaIndexEntry.posterGroupingKey(mergeSameAnimeEnabled: Boolean): String =
    if (mergeSameAnimeEnabled) {
        metadataId?.takeIf { it.isNotBlank() }?.let { id -> "metadata:$id" }
            ?: metadataTitle?.takeIf { it.isNotBlank() }?.let { title -> "title:${title.lowercase()}" }
            ?: scannedPosterTitle()
    } else {
        scannedPosterTitle()
    }

fun MediaIndexEntry.belongsToPosterGroup(
    selected: MediaIndexEntry,
    mergeSameAnimeEnabled: Boolean,
): Boolean =
    sourceId == selected.sourceId &&
        posterGroupingKey(mergeSameAnimeEnabled) == selected.posterGroupingKey(mergeSameAnimeEnabled)

fun MediaIndexEntry.mediaIndexPosterAnimeId(mergeSameAnimeEnabled: Boolean): String =
    if (mergeSameAnimeEnabled) {
        metadataId?.takeIf { it.isNotBlank() }
            ?: metadataTitle?.takeIf { it.isNotBlank() }
            ?: scannedPosterTitle()
    } else {
        scannedPosterTitle()
    }

fun List<MediaIndexEntry>.mediaIndexEpisodesForPosterSelection(
    selectedEntry: MediaIndexEntry?,
    mergeSameAnimeEnabled: Boolean = false,
): List<MediaIndexEntry> {
    val selected = selectedEntry?.takeUnless { it.isDirectory } ?: return emptyList()
    return mediaFilesOnly()
        .asSequence()
        .filter { it.belongsToPosterGroup(selected, mergeSameAnimeEnabled) }
        .sortedWith(mediaIndexEpisodeComparator)
        .toList()
}

fun List<MediaIndexEntry>.sortedByMediaIndexEpisodeOrder(): List<MediaIndexEntry> =
    sortedWith(mediaIndexEpisodeComparator)

private fun List<MediaIndexEntry>.posterGroupTitle(): String =
    firstNotNullOfOrNull { it.metadataTitle?.takeIf(String::isNotBlank) }
        ?: firstNotNullOfOrNull { it.animeName?.takeIf(String::isNotBlank) }
        ?: firstOrNull()?.posterTitle()
        ?: ""

private fun List<MediaIndexEntry>.posterGroupAnimeId(mergeSameAnimeEnabled: Boolean): String =
    asSequence()
        .map { it.mediaIndexPosterAnimeId(mergeSameAnimeEnabled) }
        .firstOrNull { it.isNotBlank() }
        ?: firstOrNull()?.path.orEmpty()

private fun MediaIndexEntry.scannedPosterTitle(): String =
    animeName?.takeIf { it.isNotBlank() }
        ?: MediaPathConventions.stem(path).takeIf { it.isNotBlank() }
        ?: path.substringAfterLast('/').substringAfterLast('\\')

private val mediaIndexEpisodeComparator =
    compareBy<MediaIndexEntry>(
        { it.seasonNumber ?: Int.MAX_VALUE },
        { it.episodeNumber ?: Int.MAX_VALUE },
        { it.path.lowercase() },
    )
