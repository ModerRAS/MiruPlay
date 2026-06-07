package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FilenameParseResult
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.DramaEpisode
import com.miruplay.tv.model.DramaEpisodeMetadata
import com.miruplay.tv.model.DramaSeason
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.DramaSeriesMetadata
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.sanitizeRecognizedText

data class LibraryIndexedDramaGroup(
    val source: MediaSourceInfo,
    val group: MediaIndexPosterGroup,
) {
    val seriesId: String = group.animeId
    val entries: List<MediaIndexEntry> = group.entries
}

data class LibraryDramaDetail(
    val sourceId: Long,
    val indexEntries: List<MediaIndexEntry>,
    val series: DramaSeries,
    val episodes: List<DramaEpisode>,
    val metadataMessage: String? = null,
    val resolvedMetadata: DramaSeriesMetadata? = null,
)

class LibraryDramaResolver(
    private val mediaSources: MediaSourceRepository,
    private val index: MediaIndexRepository,
    private val metadata: DramaMetadataRepository? = null,
    private val metadataCache: MetadataRepository? = null,
) {
    suspend fun loadSeries(): List<DramaSeries> =
        loadIndexedGroups().map { group ->
            group.toDramaSeries().merge(group.cachedSeriesMetadata())
        }

    suspend fun loadLocalSeriesDetails(): List<LibraryDramaDetail> =
        loadIndexedGroups().map { group ->
            group.toDramaDetail(cachedSeries = group.cachedSeriesMetadata())
        }

    suspend fun loadSeriesDetail(
        seriesId: String,
        includeOnlineMetadata: Boolean = true,
    ): LibraryDramaDetail? {
        val group = loadIndexedGroups().firstOrNull { it.seriesId == seriesId } ?: return null
        val cachedSeries = group.cachedSeriesMetadata()
        val localTitle = group.localTitle()
        if (!includeOnlineMetadata || metadata == null) {
            return group.toDramaDetail(cachedSeries = cachedSeries)
        }

        val episodes = group.entries.toDramaEpisodes(group.source, group.seriesId)
        val seasonNumbers = episodes.map { it.seasonNumber }.distinct().sorted()
        val metadataBinding = group.entries.preferredStoredDramaMetadataBinding(localTitle)
        val metadataResult = when {
            metadataBinding != null -> metadata.fetchSeriesMetadataById(
                tmdbId = metadataBinding.tmdbId,
                seasonNumbers = seasonNumbers,
            )
            else -> metadata.fetchSeriesMetadata(
                title = localTitle,
                seasonHint = seasonNumbers.minOrNull(),
                seasonNumbers = seasonNumbers,
            )
        }
        val rawMetadataBundle = metadataResult?.getOrNull()
        val metadataBundle = rawMetadataBundle?.takeIf { group.acceptsResolvedMetadata(it.series) }
        val metadataMessage = when {
            rawMetadataBundle != null && metadataBundle == null ->
                "TMDB 返回结果和本地剧名差太大，已忽略这次自动匹配。"
            else -> (metadataResult as? Result.Error)?.error?.toUserMessage()
        }
        return group.toDramaDetail(
            cachedSeries = cachedSeries,
            metadataBundle = metadataBundle,
            metadataMessage = metadataMessage,
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

    suspend fun getCachedSeriesMetadata(seriesId: String): DramaSeries? =
        metadataCache?.getCachedDramaSeries(seriesId)?.getOrNull()

    suspend fun cacheSeriesMetadata(seriesId: String, series: DramaSeries): Result<Unit> =
        metadataCache?.cacheDramaSeries(seriesId, series) ?: Result.success(Unit)

    private suspend fun LibraryIndexedDramaGroup.cachedSeriesMetadata(): DramaSeries? =
        getCachedSeriesMetadata(seriesId)?.takeIf { isReasonableDramaMetadataMatch(localTitle(), it) }

    private fun LibraryIndexedDramaGroup.localTitle(): String =
        entries.preferredLocalDramaTitle().ifBlank { group.title }

    private fun LibraryIndexedDramaGroup.acceptsResolvedMetadata(series: DramaSeries): Boolean =
        isReasonableDramaMetadataMatch(localTitle(), series)
}

fun LibraryIndexedDramaGroup.toDramaSeries(): DramaSeries =
    entries.toDramaSeries(
        seriesId = seriesId,
        fallbackTitle = group.title,
    )

private fun LibraryIndexedDramaGroup.toDramaDetail(
    cachedSeries: DramaSeries? = null,
    metadataBundle: DramaSeriesMetadata? = null,
    metadataMessage: String? = null,
): LibraryDramaDetail {
    val episodes = entries.toDramaEpisodes(source, seriesId)
    val mergedEpisodes = episodes.merge(metadataBundle)
    val localEpisodeCount = mergedEpisodes.size
    val localSeasonCount = mergedEpisodes.map { it.seasonNumber }.distinct().size
    return LibraryDramaDetail(
        sourceId = source.id,
        indexEntries = entries,
        series = toDramaSeries()
            .merge(cachedSeries)
            .merge(metadataBundle?.series)
            .copy(
                episodeCount = localEpisodeCount,
                seasonCount = localSeasonCount,
            ),
        episodes = mergedEpisodes,
        metadataMessage = metadataMessage,
        resolvedMetadata = metadataBundle,
    )
}

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

private data class StoredDramaMetadataBinding(
    val tmdbId: Int,
    val title: String,
)

private fun List<MediaIndexEntry>.toDramaSeries(
    seriesId: String,
    fallbackTitle: String,
): DramaSeries {
    val localTitle = preferredLocalDramaTitle().ifBlank { fallbackTitle }
    val metadataBinding = preferredStoredDramaMetadataBinding(localTitle)
    val resolvedTitle = metadataBinding?.title ?: localTitle
    val shouldTrustStoredSummary = metadataBinding != null || none { it.hasStoredDramaMetadata() }
    return DramaSeries(
        id = seriesId,
        title = resolvedTitle,
        originalTitle = localTitle.takeIf { it.isNotBlank() && it != resolvedTitle }.orEmpty(),
        summary = if (shouldTrustStoredSummary) {
            firstNotNullOfOrNull { it.plot?.takeIf(String::isNotBlank) }.orEmpty()
        } else {
            ""
        },
        episodeCount = size,
        seasonCount = mapNotNull { it.seasonNumber }.distinct().ifEmpty { listOf(1) }.size,
        tmdbId = metadataBinding?.tmdbId,
    )
}

private fun List<MediaIndexEntry>.preferredLocalDramaTitle(): String =
    asSequence()
        .mapNotNull { entry ->
            sanitizeDramaTitleCandidate(
                entry.animeName?.takeIf { it.isNotBlank() }
                    ?: entry.path.substringAfterLast('/').substringAfterLast('\\'),
            )
        }
        .firstOrNull()
        .orEmpty()

private fun List<MediaIndexEntry>.preferredStoredDramaMetadataBinding(
    localTitle: String,
): StoredDramaMetadataBinding? {
    val bindingById = asSequence()
        .mapNotNull { entry ->
            val tmdbId = entry.metadataId
                ?.takeIf { entry.metadataSource.equals("TMDB", ignoreCase = true) }
                ?.toIntOrNull()
                ?: return@mapNotNull null
            val metadataTitle = sanitizeDramaTitleCandidate(entry.metadataTitle) ?: return@mapNotNull null
            StoredDramaMetadataBinding(
                tmdbId = tmdbId,
                title = metadataTitle,
            )
        }
        .groupBy(StoredDramaMetadataBinding::tmdbId)
        .maxByOrNull { it.value.size }
        ?.value
        .orEmpty()
    val candidate = bindingById
        .groupingBy { it.title }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?.let { title ->
            StoredDramaMetadataBinding(
                tmdbId = bindingById.first().tmdbId,
                title = title,
            )
        }
        ?: return null
    return candidate.takeIf { isReasonableDramaTitleMatch(localTitle, it.title) }
}

private fun MediaIndexEntry.hasStoredDramaMetadata(): Boolean =
    metadataSource.equals("TMDB", ignoreCase = true) &&
        metadataId?.isNotBlank() == true

private fun sanitizeDramaTitleCandidate(title: String?): String? =
    FilenameParseResult(title = title)
        .sanitizeRecognizedText()
        .title
        ?.replace(dramaWatermarkRegex, " ")
        ?.replace(dramaWatermarkPhraseRegex, " ")
        ?.replace(multiWhitespaceRegex, " ")
        ?.trim()
        ?.trim('-', '_', '.', '/', '\\', ' ')
        ?.takeIf { it.isNotBlank() }

private fun isReasonableDramaMetadataMatch(
    localTitle: String,
    series: DramaSeries,
): Boolean =
    isReasonableDramaTitleMatch(localTitle, series.title) ||
        isReasonableDramaTitleMatch(localTitle, series.originalTitle)

private fun isReasonableDramaTitleMatch(
    localTitle: String,
    candidateTitle: String,
): Boolean {
    val local = comparableDramaTitle(localTitle) ?: return false
    val candidate = comparableDramaTitle(candidateTitle) ?: return false
    if (local == candidate) return true
    if (candidateTitle.containsDomainLikeWatermark()) return false
    if (local.containsCjk() && !candidate.containsCjk()) return false
    if (local.containsCjk() && candidate.containsCjk()) {
        return longestCommonSubstringLength(local, candidate) >= 2
    }
    return local in candidate || candidate in local
}

private fun comparableDramaTitle(title: String): String? =
    sanitizeDramaTitleCandidate(title)
        ?.lowercase()
        ?.replace(dramaSeasonSuffixRegex, "")
        ?.replace(dramaComparableNoiseRegex, "")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun String.containsDomainLikeWatermark(): Boolean =
    dramaWatermarkRegex.containsMatchIn(this)

private fun String.containsCjk(): Boolean =
    any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

private fun longestCommonSubstringLength(left: String, right: String): Int {
    if (left.isEmpty() || right.isEmpty()) return 0
    val dp = IntArray(right.length + 1)
    var longest = 0
    for (i in left.indices) {
        for (j in right.length - 1 downTo 0) {
            dp[j + 1] = if (left[i] == right[j]) {
                dp[j] + 1
            } else {
                0
            }
            if (dp[j + 1] > longest) {
                longest = dp[j + 1]
            }
        }
    }
    return longest
}

private val dramaWatermarkRegex = Regex("""(?i)\b(?:www\.)?[a-z0-9-]+(?:\.[a-z0-9-]+)+\b""")
private val dramaWatermarkPhraseRegex = Regex("""更多(?:电视剧集|剧集)(?:下载|打包下载)?请访问""")
private val dramaSeasonSuffixRegex = Regex("""(?i)(?:第\s*\d+\s*季|season\s*\d+|s\s*\d+)$""")
private val dramaComparableNoiseRegex = Regex("""[\s\p{Punct}·_]+""")
private val multiWhitespaceRegex = Regex("""\s+""")

fun dramaSeriesCacheKey(seriesId: String): String =
    "drama-series:$seriesId"

fun DramaSeries.toCachedDramaMetadata(cacheKey: String = dramaSeriesCacheKey(id)): Anime =
    Anime(
        id = cacheKey,
        title = title,
        titleCn = originalTitle.ifBlank { null },
        summary = summary,
        episodeCount = episodeCount,
        airDate = firstAirDate,
        tmdbId = tmdbId,
        posterUrl = posterUrl,
        fanartUrl = fanartUrl,
    )

fun Anime.toCachedDramaSeries(seriesId: String): DramaSeries =
    DramaSeries(
        id = seriesId,
        title = title,
        originalTitle = titleCn.orEmpty(),
        summary = summary,
        episodeCount = episodeCount,
        firstAirDate = airDate,
        tmdbId = tmdbId,
        posterUrl = posterUrl,
        fanartUrl = fanartUrl,
    )

suspend fun MetadataRepository.getCachedDramaSeries(seriesId: String): Result<DramaSeries?> =
    getCachedMetadata(dramaSeriesCacheKey(seriesId))
        .map { cached -> cached?.toCachedDramaSeries(seriesId) }

suspend fun MetadataRepository.cacheDramaSeries(
    seriesId: String,
    series: DramaSeries,
): Result<Unit> =
    cacheMetadata(series.toCachedDramaMetadata(dramaSeriesCacheKey(seriesId)))
