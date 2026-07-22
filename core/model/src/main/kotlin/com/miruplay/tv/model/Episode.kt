package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class EpisodeVersion(
    val episodeId: String,
    val filePath: String,
    val fileName: String,
    val duration: Long = 0L,
)

@Serializable
data class Episode(
    val id: String,
    val animeId: String,
    val seasonNumber: Int = 1,
    val episodeNumber: Int,
    val title: String = "",
    val filePath: String,
    val fileName: String,
    val duration: Long = 0L,  // ms
    val watchedPosition: Long = 0L,  // ms
    val lastWatchedTimestamp: Long = 0L,  // epoch ms
    val playCount: Int = 0,
    val thumbnailPath: String? = null,
    val bangumiEpisodeId: Int? = null,
    val bangumiCollectionType: Int? = null,
    val progressId: String = id,
    val versions: List<EpisodeVersion> = emptyList(),
)

fun Episode.availableVersions(): List<EpisodeVersion> =
    versions.ifEmpty {
        listOf(
            EpisodeVersion(
                episodeId = id,
                filePath = filePath,
                fileName = fileName,
                duration = duration,
            ),
        )
    }

fun Episode.withVersion(version: EpisodeVersion): Episode =
    copy(
        id = version.episodeId,
        filePath = version.filePath,
        fileName = version.fileName,
        duration = version.duration,
    )

fun List<Episode>.groupEpisodeVersions(logicalAnimeId: String? = null): List<Episode> =
    groupBy { it.seasonNumber to it.episodeNumber }
        .map { (key, candidates) ->
            val (seasonNumber, episodeNumber) = key
            val animeId = logicalAnimeId ?: candidates.first().animeId
            val versions = candidates
                .flatMap(Episode::availableVersions)
                .distinctBy { it.episodeId to it.filePath }
                .sortedBy(EpisodeVersion::filePath)
            val representative = candidates.maxWithOrNull(
                compareBy<Episode> { it.title.isNotBlank() }
                    .thenBy { it.bangumiEpisodeId != null }
                    .thenBy { it.duration },
            ) ?: candidates.first()
            representative.withVersion(versions.first()).copy(
                animeId = animeId,
                progressId = logicalEpisodeProgressId(animeId, seasonNumber, episodeNumber),
                versions = versions,
            )
        }
        .sortedForPlaybackQueue()

fun logicalEpisodeProgressId(animeId: String, seasonNumber: Int, episodeNumber: Int): String =
    "$animeId#S${seasonNumber}E$episodeNumber"

fun List<EpisodeVersion>.nearestTo(currentPath: String): EpisodeVersion? =
    maxWithOrNull(
        compareBy<EpisodeVersion> { commonPathSegmentCount(currentPath, it.filePath) }
            .thenBy { currentPath.commonPrefixWith(it.filePath, ignoreCase = true).length }
            .thenByDescending { kotlin.math.abs(pathSegments(currentPath).size - pathSegments(it.filePath).size) }
            .thenByDescending(EpisodeVersion::filePath),
    )

private fun commonPathSegmentCount(left: String, right: String): Int =
    pathSegments(left).zip(pathSegments(right)).takeWhile { (a, b) -> a.equals(b, ignoreCase = true) }.size

private fun pathSegments(path: String): List<String> =
    path.replace('\\', '/').substringBefore('?').split('/').filter(String::isNotBlank)
