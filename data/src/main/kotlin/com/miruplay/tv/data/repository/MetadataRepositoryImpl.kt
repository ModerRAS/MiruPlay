package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.dao.AnimeDao
import com.miruplay.tv.data.dao.DramaSeriesCacheDao
import com.miruplay.tv.data.dao.EpisodeDao
import com.miruplay.tv.data.entity.AnimeEntity
import com.miruplay.tv.data.entity.DramaSeriesCacheEntity
import com.miruplay.tv.data.entity.EpisodeEntity
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.DramaSeries
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.model.normalizedMetadataBinding
import com.miruplay.tv.repository.dramaSeriesCacheKey
import com.miruplay.tv.repository.toLegacyCachedDramaMetadata
import com.miruplay.tv.repository.toLegacyCachedDramaSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataRepositoryImpl @Inject constructor(
    private val animeDao: AnimeDao,
    private val episodeDao: EpisodeDao,
    private val dramaSeriesCacheDao: DramaSeriesCacheDao,
) : MetadataRepository {

    companion object {
        private const val SQLITE_BIND_PARAMETER_BATCH_SIZE = 900
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
            // Return cached data with current episode count
            val episodes = episodeDao.getByAnimeId(animeId)
            Result.success(entity.toDomain(episodes))
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    override suspend fun getCachedMetadataByBangumiId(bangumiId: Int): Result<Anime?> = withContext(Dispatchers.IO) {
        try {
            val entity = animeDao.getByBangumiId(bangumiId.toString()) ?: return@withContext Result.success(null)
            val episodes = episodeDao.getByAnimeId(entity.id)
            Result.success(entity.toDomain(episodes))
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> = withContext(Dispatchers.IO) {
        try {
            val ids = animeIds.filter { it.isNotBlank() }.distinct()
            if (ids.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val entities = ids.chunked(SQLITE_BIND_PARAMETER_BATCH_SIZE)
                .flatMap { batch -> animeDao.getByIds(batch) }
            val episodesByAnime = entities.map { it.id }
                .chunked(SQLITE_BIND_PARAMETER_BATCH_SIZE)
                .flatMap { batch -> episodeDao.getByAnimeIds(batch) }
                .groupBy { it.animeId }
            Result.success(entities.map { entity ->
                entity.toDomain(episodesByAnime[entity.id].orEmpty())
            })
        } catch (e: Exception) {
            Result.success(emptyList())
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

    override suspend fun cacheDramaSeries(seriesId: String, series: DramaSeries): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val normalized = series.normalizedMetadataBinding()
            dramaSeriesCacheDao.insert(normalized.toEntity(seriesId))
            animeDao.insert(normalized.toLegacyCachedDramaMetadata(dramaSeriesCacheKey(seriesId)).toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.SyncError.WriteFailed("cache_drama_series_$seriesId", e.message ?: "Unknown"))
        }
    }

    override suspend fun getCachedDramaSeries(seriesId: String): Result<DramaSeries?> = withContext(Dispatchers.IO) {
        try {
            val cached = dramaSeriesCacheDao.getBySeriesId(seriesId)?.toDomain()
            if (cached != null) {
                Result.success(cached)
            } else {
                val legacy = animeDao.getById(dramaSeriesCacheKey(seriesId))?.toDomain(emptyList())
                Result.success(legacy?.toLegacyCachedDramaSeries(seriesId))
            }
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    override suspend fun invalidateDramaSeriesCache(seriesId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dramaSeriesCacheDao.deleteBySeriesId(seriesId)
            animeDao.deleteById(dramaSeriesCacheKey(seriesId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(AppError.SyncError.WriteFailed("cache-invalidate-drama", e.message ?: "Unknown"))
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
        episodeCount = episodeEntities.size.takeIf { it > 0 } ?: episodeCount,
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

private fun DramaSeries.toEntity(seriesId: String): DramaSeriesCacheEntity {
    val normalized = normalizedMetadataBinding()
    return DramaSeriesCacheEntity(
        seriesId = seriesId,
        title = normalized.title,
        originalTitle = normalized.originalTitle,
        summary = normalized.summary,
        seasonCount = normalized.seasonCount,
        episodeCount = normalized.episodeCount,
        posterUrl = normalized.posterUrl,
        fanartUrl = normalized.fanartUrl,
        firstAirDate = normalized.firstAirDate,
        metadataSource = normalized.metadataProviderRef?.source,
        metadataId = normalized.metadataProviderRef?.id,
    )
}

private fun DramaSeriesCacheEntity.toDomain(): DramaSeries =
    DramaSeries(
        id = seriesId,
        title = title,
        originalTitle = originalTitle,
        summary = summary,
        seasonCount = seasonCount,
        episodeCount = episodeCount,
        posterUrl = posterUrl,
        fanartUrl = fanartUrl,
        firstAirDate = firstAirDate,
        metadataProviderRef = metadataSource
            ?.takeIf { it.isNotBlank() }
            ?.let { source ->
                metadataId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { id -> MetadataProviderRef(source = source, id = id) }
            },
    ).normalizedMetadataBinding()
