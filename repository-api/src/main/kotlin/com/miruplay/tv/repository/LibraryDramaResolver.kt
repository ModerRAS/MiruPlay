package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.DramaEpisode
import com.miruplay.tv.model.DramaEpisodeMetadata
import com.miruplay.tv.model.DramaSeason
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceInfo

data class LibraryIndexedDramaGroup(
    val source: MediaSourceInfo,
    val group: MediaIndexPosterGroup,
) {
    val seriesId: String = group.animeId
    val entries: List<MediaIndexEntry> = group.entries
}

data class LibraryDramaDetail(
    val series: DramaSeries,
    val episodes: List<DramaEpisode>,
    val metadataMessage: String? = null,
)

class LibraryDramaResolver(
    private val mediaSources: MediaSourceRepository,
    private val index: MediaIndexRepository,
    private val metadata: DramaMetadataRepository? = null,
) {
    suspend fun loadSeries(): List<DramaSeries> =
        loadIndexedGroups().map { it.toDramaSeries() }

    suspend fun loadSeriesDetail(seriesId: String): LibraryDramaDetail? {
        val group = loadIndexedGroups().firstOrNull { it.seriesId == seriesId } ?: return null
        val episodes = group.entries.toDramaEpisodes(group.source, group.seriesId)
        val seasonNumbers = episodes.map { it.seasonNumber }.distinct().sorted()
        val metadataResult = metadata?.fetchSeriesMetadata(
            title = group.group.title,
            seasonHint = seasonNumbers.minOrNull(),
            seasonNumbers = seasonNumbers,
        )
        val metadataBundle = metadataResult?.getOrNull()
        val mergedEpisodes = episodes.merge(metadataBundle)
        val localEpisodeCount = mergedEpisodes.size
        val localSeasonCount = mergedEpisodes.map { it.seasonNumber }.distinct().size
        return LibraryDramaDetail(
            series = group.toDramaSeries()
                .merge(metadataBundle?.series)
                .copy(
                    episodeCount = localEpisodeCount,
                    seasonCount = localSeasonCount,
                ),
            episodes = mergedEpisodes,
            metadataMessage = (metadataResult as? Result.Error)?.error?.toUserMessage(),
        )
    }

    private suspend fun loadIndexedGroups(): List<LibraryIndexedDramaGroup> {
        val sources = mediaSources.getSources()
            .getOrNull()
            .orEmpty()
            .filter { it.contentMode == MediaContentMode.DRAMA }
        return sources.flatMap { source ->
            index.queryIndex(source.id, "")
                .getOrNull()
                .orEmpty()
                .toMediaIndexPosterGroups(mergeSameAnimeEnabled = false)
                .map { group ->
                    LibraryIndexedDramaGroup(
                        source = source,
                        group = group,
                    )
                }
        }
    }
}

fun LibraryIndexedDramaGroup.toDramaSeries(): DramaSeries =
    DramaSeries(
        id = seriesId,
        title = group.title,
        summary = group.primaryEntry.plot.orEmpty(),
        episodeCount = entries.size,
        seasonCount = entries.mapNotNull { it.seasonNumber }.distinct().ifEmpty { listOf(1) }.size,
    )

fun DramaSeries.merge(other: DramaSeries?): DramaSeries {
    if (other == null) return this
    return copy(
        title = other.title.ifBlank { title },
        originalTitle = other.originalTitle.ifBlank { originalTitle },
        summary = other.summary.ifBlank { summary },
        posterUrl = other.posterUrl ?: posterUrl,
        fanartUrl = other.fanartUrl ?: fanartUrl,
        firstAirDate = other.firstAirDate ?: firstAirDate,
        tmdbId = other.tmdbId ?: tmdbId,
    )
}

fun List<DramaEpisode>.merge(metadata: DramaSeriesMetadata?): List<DramaEpisode> {
    if (metadata == null) return this
    val metadataBySeasonAndEpisode = metadata.seasons
        .flatMap { season -> season.episodes }
        .associateBy { it.seasonNumber to it.episodeNumber }
    return map { episode ->
        episode.merge(metadataBySeasonAndEpisode[episode.seasonNumber to episode.episodeNumber])
    }
}

fun DramaEpisode.merge(other: DramaEpisodeMetadata?): DramaEpisode {
    if (other == null) return this
    return copy(
        title = other.title.ifBlank { title },
        summary = other.summary.ifBlank { summary },
    )
}

fun List<MediaIndexEntry>.toDramaEpisodes(
    source: MediaSourceInfo?,
    seriesId: String,
): List<DramaEpisode> =
    toIndexedEpisodes(source, seriesId)
        .map { it.toDramaEpisode(seriesId) }

fun Episode.toDramaEpisode(seriesIdOverride: String = animeId): DramaEpisode =
    DramaEpisode(
        id = id,
        seriesId = seriesIdOverride,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        title = title,
        summary = "",
        filePath = filePath,
        fileName = fileName,
    )

fun List<DramaEpisode>.toDramaSeasons(): List<DramaSeason> =
    groupBy { it.seasonNumber }
        .toSortedMap()
        .map { (seasonNumber, episodes) ->
            DramaSeason(
                seasonNumber = seasonNumber,
                episodeCount = episodes.size,
            )
        }
