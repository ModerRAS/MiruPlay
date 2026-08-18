package com.miruplay.tv.repository

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.PosterWallArrangement
import com.miruplay.tv.model.distinctSeasonEpisodeCount
import com.miruplay.tv.model.toAnimeReleaseSeason

data class MediaIndexPosterGroup(
    val title: String,
    val entries: List<MediaIndexEntry>,
    val animeId: String = entries.posterGroupAnimeId(mergeSameAnimeEnabled = false),
) {
    val episodeCount: Int = entries
        .asSequence()
        .filterNot(MediaIndexEntry::isSeriesExtra)
        .distinctBy { entry ->
            entry.episodeNumber?.let { episodeNumber ->
                (entry.seasonNumber ?: 1) to episodeNumber
            } ?: entry.path
        }
        .count()
    val primaryEntry: MediaIndexEntry =
        entries
            .filterNot(MediaIndexEntry::isSeriesExtra)
            .ifEmpty { entries }
            .sortedWith(compareBy<MediaIndexEntry> { it.episodeNumber ?: Int.MAX_VALUE }.thenBy { it.path })
            .first()
    val entryPaths: Set<String> = entries.map { it.path }.toSet()
    val lastModified: Long = entries.maxOfOrNull { it.lastModified } ?: 0L
    val releaseSeasonKey: String? = entries.firstNotNullOfOrNull { entry ->
        entry.metadataId?.takeIf { it.isNotBlank() }?.let { "metadata:$it" }
            ?: entry.metadataTitle?.takeIf { it.isNotBlank() }?.let { "title:${it.lowercase()}" }
            ?: entry.animeName?.takeIf { it.isNotBlank() }?.let { "title:${it.lowercase()}" }
    }
    val episodeCount: Int = entries
        .toCachedIndexedEpisodes(source = null, animeId = animeId)
        .distinctSeasonEpisodeCount()
    val subtitle: String = buildString {
        append(episodeCount)
        append(" episode")
        if (episodeCount != 1) append('s')
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

fun List<MediaIndexPosterGroup>.sortedForPosterWall(
    arrangement: PosterWallArrangement = PosterWallArrangement.TITLE,
    releaseSeasonsByAnimeId: Map<String, String> = emptyMap(),
): List<MediaIndexPosterGroup> =
    when (arrangement) {
        PosterWallArrangement.TITLE -> sortedBy { it.title.lowercase() }
        PosterWallArrangement.RELEASE_SEASON -> sortedWith(
            compareBy<MediaIndexPosterGroup> { if (it.releaseSeason(releaseSeasonsByAnimeId) == null) 1 else 0 }
                .thenByDescending { it.releaseSeason(releaseSeasonsByAnimeId)?.year ?: Int.MIN_VALUE }
                .thenByDescending { it.releaseSeason(releaseSeasonsByAnimeId)?.startMonth ?: Int.MIN_VALUE }
                .thenBy { it.title.lowercase() },
        )
    }

fun MediaIndexPosterGroup.toIndexedAnime(): Anime =
    Anime(
        id = animeId,
        title = title,
        episodeCount = episodeCount,
        summary = primaryEntry.plot.orEmpty(),
    )

fun MediaIndexPosterGroup.preferredMetadataCacheKey(): String =
    entries.firstNotNullOfOrNull { it.localMetadataOverrideKey()?.takeIf(String::isNotBlank) }
        ?: entries.firstNotNullOfOrNull { it.metadataId?.takeIf(String::isNotBlank) }
        ?: animeId.takeIf(String::isNotBlank)
        ?: title

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
        .filterNot(MediaIndexEntry::isSeriesExtra)
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

private fun MediaIndexPosterGroup.releaseSeason(releaseSeasonsByAnimeId: Map<String, String>) =
    sequenceOf(
        animeId,
        "metadata:$animeId",
        "title:${title.lowercase()}",
        releaseSeasonKey,
    )
        .filterNotNull()
        .firstNotNullOfOrNull { key -> releaseSeasonsByAnimeId[key]?.toAnimeReleaseSeason() }
