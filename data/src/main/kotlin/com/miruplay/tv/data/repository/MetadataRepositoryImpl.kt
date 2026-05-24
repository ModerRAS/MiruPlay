package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.dao.AnimeDao
import com.miruplay.tv.data.dao.EpisodeDao
import com.miruplay.tv.data.entity.AnimeEntity
import com.miruplay.tv.data.entity.EpisodeEntity
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataRepositoryImpl @Inject constructor(
    private val animeDao: AnimeDao,
    private val episodeDao: EpisodeDao
) : MetadataRepository {

    companion object {
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L  // 24 hours
    }

    override suspend fun cacheMetadata(anime: Anime): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            animeDao.insert(anime.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.SyncError.WriteFailed("cache", e.message ?: "Unknown"))
        }
    }

    override suspend fun getCachedMetadata(animeId: String): Result<Anime?> = withContext(Dispatchers.IO) {
        try {
            val entity = animeDao.getById(animeId) ?: return@withContext Result.success(null)
            // Check if cache is expired
            if (System.currentTimeMillis() - entity.lastUpdated > CACHE_DURATION_MS) {
                return@withContext Result.success(null)
            }
            // Return cached data with current episode count
            val episodes = episodeDao.getByAnimeId(animeId)
            Result.success(entity.toDomain(episodes))
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> = withContext(Dispatchers.IO) {
        try {
            val episodeEntities = episodeDao.getByAnimeId(animeId)
            Result.success(episodeEntities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> = withContext(Dispatchers.IO) {
        try {
            Result.success(episodeDao.getById(episodeId)?.toDomain())
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            episodeDao.deleteByAnimeId(animeId)
            val entities = episodes.map { it.toEntity(animeId, it.seasonNumber) }
            episodeDao.insertAll(entities)
            // Also update episode count in cached anime metadata
            animeDao.getById(animeId)?.let { animeEntity ->
                animeDao.insert(animeEntity.copy(
                    episodeCount = episodes.size,
                    lastUpdated = System.currentTimeMillis()
                ))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.SyncError.WriteFailed("cache_episodes_$animeId", e.message ?: "Unknown"))
        }
    }

    override suspend fun invalidateCache(animeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            episodeDao.deleteByAnimeId(animeId)
            animeDao.deleteById(animeId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.SyncError.WriteFailed("cache-invalidate", e.message ?: "Unknown"))
        }
    }
}

// Mapping extensions

private val json = Json { ignoreUnknownKeys = true }

private fun Anime.toEntity(): AnimeEntity = AnimeEntity(
    id = id,
    title = title,
    titleCn = titleCn,
    summary = summary.ifBlank { null },
    genres = genres.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
    studio = studio,
    director = director,
    episodeCount = episodeCount,
    airDate = airDate,
    rating = rating,
    bangumiId = bangumiId?.toString(),
    anilistId = anilistId?.toString(),
    tmdbId = tmdbId?.toString(),
    posterUrl = posterUrl,
    posterLocalPath = posterLocalPath,
    fanartUrl = fanartUrl,
    bangumiCollectionType = bangumiCollectionType,
    bangumiEpStatus = bangumiEpStatus
)

private fun AnimeEntity.toDomain(episodeEntities: List<EpisodeEntity>): Anime {
    return Anime(
        id = id,
        title = title,
        titleCn = titleCn,
        summary = summary ?: "",
        genres = genres?.let {
            try {
                json.decodeFromString<List<String>>(it)
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList(),
        studio = studio,
        director = director,
        episodeCount = episodeCount,
        airDate = airDate,
        rating = rating,
        bangumiId = bangumiId?.toIntOrNull(),
        anilistId = anilistId?.toIntOrNull(),
        tmdbId = tmdbId?.toIntOrNull(),
        posterUrl = posterUrl,
        posterLocalPath = posterLocalPath,
        fanartUrl = fanartUrl,
        bangumiCollectionType = bangumiCollectionType,
        bangumiEpStatus = bangumiEpStatus
    )
}

private fun Episode.toEntity(animeId: String, seasonNumber: Int): EpisodeEntity = EpisodeEntity(
    id = id,
    animeId = animeId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = title.ifBlank { null },
    filePath = filePath,
    fileName = fileName,
    duration = duration,
    thumbnailPath = thumbnailPath,
    bangumiEpisodeId = bangumiEpisodeId,
    bangumiCollectionType = bangumiCollectionType
)

private fun EpisodeEntity.toDomain(): Episode = Episode(
    id = id,
    animeId = animeId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = title ?: "",
    filePath = filePath,
    fileName = fileName ?: "",
    duration = duration,
    thumbnailPath = thumbnailPath,
    bangumiEpisodeId = bangumiEpisodeId,
    bangumiCollectionType = bangumiCollectionType
)
