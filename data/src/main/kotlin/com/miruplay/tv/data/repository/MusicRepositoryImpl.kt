package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.dao.MusicAlbumDao
import com.miruplay.tv.data.dao.MusicTrackDao
import com.miruplay.tv.data.entity.MusicAlbumEntity
import com.miruplay.tv.data.entity.MusicTrackEntity
import com.miruplay.tv.model.MusicAlbum
import com.miruplay.tv.model.MusicTrack
import com.miruplay.tv.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val albumDao: MusicAlbumDao,
    private val trackDao: MusicTrackDao
) : MusicRepository {

    override suspend fun getAlbums(): Result<List<MusicAlbum>> = try {
        Result.success(albumDao.getAll().map { it.toModel() })
    } catch (e: Exception) {
        Result.failure(AppError.LibraryIndexError.ReadFailed(e.message ?: "getAlbums failed"))
    }

    override suspend fun getAlbumsBySource(sourceId: Long): Result<List<MusicAlbum>> = try {
        Result.success(albumDao.getBySourceId(sourceId).map { it.toModel() })
    } catch (e: Exception) {
        Result.failure(AppError.LibraryIndexError.ReadFailed(e.message ?: "getAlbumsBySource failed"))
    }

    override suspend fun getAlbumById(albumId: String): Result<MusicAlbum> = try {
        val e = albumDao.getById(albumId) ?: return Result.failure(AppError.MediaSourceError.NotFound("album $albumId"))
        Result.success(e.toModel())
    } catch (e: Exception) {
        Result.failure(AppError.LibraryIndexError.ReadFailed(e.message ?: "getAlbumById failed"))
    }

    override suspend fun getTracksByAlbum(albumId: String): Result<List<MusicTrack>> = try {
        Result.success(trackDao.getByAlbumId(albumId).map { it.toModel() })
    } catch (e: Exception) {
        Result.failure(AppError.LibraryIndexError.ReadFailed(e.message ?: "getTracksByAlbum failed"))
    }

    override suspend fun getTrackById(trackId: String): Result<MusicTrack> = try {
        val e = trackDao.getById(trackId) ?: return Result.failure(AppError.MediaSourceError.NotFound("track $trackId"))
        Result.success(e.toModel())
    } catch (e: Exception) {
        Result.failure(AppError.LibraryIndexError.ReadFailed(e.message ?: "getTrackById failed"))
    }

    override suspend fun observeAlbums(): Flow<List<MusicAlbum>> =
        albumDao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun observeTracksByAlbum(albumId: String): Flow<List<MusicTrack>> =
        trackDao.observeByAlbumId(albumId).map { list -> list.map { it.toModel() } }

    override suspend fun replaceForSource(sourceId: Long, albums: List<MusicAlbum>, tracks: List<MusicTrack>): Result<Unit> = try {
        albumDao.deleteBySourceId(sourceId)
        trackDao.deleteBySourceId(sourceId)
        if (albums.isNotEmpty()) albumDao.insertAll(albums.map { it.toEntity() })
        if (tracks.isNotEmpty()) trackDao.insertAll(tracks.map { it.toEntity() })
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(AppError.LibraryIndexError.ReadFailed(e.message ?: "replaceForSource failed"))
    }

    override suspend fun clearForSource(sourceId: Long): Result<Unit> = try {
        albumDao.deleteBySourceId(sourceId)
        trackDao.deleteBySourceId(sourceId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(AppError.LibraryIndexError.ReadFailed(e.message ?: "clearForSource failed"))
    }

    private fun MusicAlbumEntity.toModel() = MusicAlbum(
        id = id, title = title, artist = artist, coverUrl = coverUrl, trackCount = trackCount, sourceId = sourceId, lastUpdated = lastUpdated
    )
    private fun MusicAlbum.toEntity() = MusicAlbumEntity(
        id = id, title = title, artist = artist, coverUrl = coverUrl, trackCount = trackCount, sourceId = sourceId, lastUpdated = lastUpdated
    )
    private fun MusicTrackEntity.toModel() = MusicTrack(
        id = id, albumId = albumId, sourceId = sourceId, filePath = filePath, fileName = fileName, title = title, artist = artist, albumArtist = albumArtist, albumTitle = albumTitle, trackNumber = trackNumber, discNumber = discNumber, duration = duration, cuePath = cuePath, cueTrackIndex = cueTrackIndex, cueStartMs = cueStartMs, cueEndMs = cueEndMs, isCueVirtual = isCueVirtual, coverPath = coverPath
    )
    private fun MusicTrack.toEntity() = MusicTrackEntity(
        id = id, albumId = albumId, sourceId = sourceId, filePath = filePath, fileName = fileName, title = title, artist = artist, albumArtist = albumArtist, albumTitle = albumTitle, trackNumber = trackNumber, discNumber = discNumber, duration = duration, cuePath = cuePath, cueTrackIndex = cueTrackIndex, cueStartMs = cueStartMs, cueEndMs = cueEndMs, isCueVirtual = isCueVirtual, coverPath = coverPath, lastModified = 0L
    )
}
