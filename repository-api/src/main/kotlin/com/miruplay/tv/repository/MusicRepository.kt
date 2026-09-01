package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MusicAlbum
import com.miruplay.tv.model.MusicTrack
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun getAlbums(): Result<List<MusicAlbum>>
    suspend fun getAlbumsBySource(sourceId: Long): Result<List<MusicAlbum>>
    suspend fun getAlbumById(albumId: String): Result<MusicAlbum>
    suspend fun getTracksByAlbum(albumId: String): Result<List<MusicTrack>>
    suspend fun getTrackById(trackId: String): Result<MusicTrack>
    suspend fun observeAlbums(): Flow<List<MusicAlbum>>
    suspend fun observeTracksByAlbum(albumId: String): Flow<List<MusicTrack>>
    suspend fun replaceForSource(sourceId: Long, albums: List<MusicAlbum>, tracks: List<MusicTrack>): Result<Unit>
    suspend fun clearForSource(sourceId: Long): Result<Unit>
}
