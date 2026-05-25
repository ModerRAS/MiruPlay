package com.miruplay.tv.repository.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.repository.MetadataRepository

internal class FileBackedMetadataRepository(
    private val store: DesktopRepositoryStore,
) : MetadataRepository {
    override suspend fun cacheMetadata(anime: Anime): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(
                animeMetadata = state.animeMetadata.filterNot { it.id == anime.id } + anime,
            ) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("metadata_${anime.id}", it.message ?: "cache failed")) },
    )

    override suspend fun getCachedMetadata(animeId: String): Result<Anime?> = runCatching {
        store.read { state -> state.animeMetadata.firstOrNull { it.id == animeId } }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.success(null) },
    )

    override suspend fun getCachedMetadata(animeIds: Collection<String>): Result<List<Anime>> = runCatching {
        val ids = animeIds.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) {
            emptyList()
        } else {
            store.read { state -> state.animeMetadata.filter { it.id in ids } }
        }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.success(emptyList()) },
    )

    override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> = runCatching {
        store.read { state -> state.episodes.firstOrNull { it.id == episodeId } }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.success(null) },
    )

    override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> = runCatching {
        store.read { state ->
            state.episodes
                .filter { it.animeId == animeId }
                .sortedWith(compareBy<Episode>({ it.seasonNumber }, { it.episodeNumber }, { it.filePath }))
        }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.success(emptyList()) },
    )

    override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(
                episodes = state.episodes.filterNot { it.animeId == animeId } + episodes.map { it.copy(animeId = animeId) },
                animeMetadata = state.animeMetadata.map { anime ->
                    if (anime.id == animeId) {
                        anime.copy(episodeCount = maxOf(anime.episodeCount, episodes.size))
                    } else {
                        anime
                    }
                },
            ) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("episodes_$animeId", it.message ?: "cache failed")) },
    )

    override suspend fun invalidateCache(animeId: String): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(
                animeMetadata = state.animeMetadata.filterNot { it.id == animeId },
                episodes = state.episodes.filterNot { it.animeId == animeId },
            ) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("metadata_invalidate_$animeId", it.message ?: "invalidate failed")) },
    )
}
