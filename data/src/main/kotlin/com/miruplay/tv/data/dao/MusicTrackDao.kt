package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.MusicTrackEntity
import kotlinx.coroutines.flow.Flow

private const val MUSIC_TRACK_COLUMNS = "id, album_id, source_id, file_path, file_name, title, artist, album_artist, album_title, track_number, disc_number, duration, cue_path, cue_track_index, cue_start_ms, cue_end_ms, is_cue_virtual, cover_path, last_modified"

@Dao
interface MusicTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<MusicTrackEntity>)

    @Query("SELECT $MUSIC_TRACK_COLUMNS FROM music_track WHERE album_id = :albumId ORDER BY disc_number ASC, track_number ASC, title ASC")
    suspend fun getByAlbumId(albumId: String): List<MusicTrackEntity>

    @Query("SELECT $MUSIC_TRACK_COLUMNS FROM music_track WHERE source_id = :sourceId ORDER BY album_id ASC, disc_number ASC, track_number ASC")
    suspend fun getBySourceId(sourceId: Long): List<MusicTrackEntity>

    @Query("SELECT $MUSIC_TRACK_COLUMNS FROM music_track WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MusicTrackEntity?

    @Query("SELECT $MUSIC_TRACK_COLUMNS FROM music_track WHERE file_path = :filePath ORDER BY cue_track_index ASC")
    suspend fun getByFilePath(filePath: String): List<MusicTrackEntity>

    @Query("DELETE FROM music_track WHERE album_id = :albumId")
    suspend fun deleteByAlbumId(albumId: String)

    @Query("DELETE FROM music_track WHERE source_id = :sourceId")
    suspend fun deleteBySourceId(sourceId: Long)

    @Query("SELECT $MUSIC_TRACK_COLUMNS FROM music_track WHERE album_id = :albumId ORDER BY track_number ASC")
    fun observeByAlbumId(albumId: String): Flow<List<MusicTrackEntity>>

    @Query("SELECT $MUSIC_TRACK_COLUMNS FROM music_track ORDER BY album_id ASC, track_number ASC")
    fun observeAll(): Flow<List<MusicTrackEntity>>

    @Query("SELECT COUNT(*) FROM music_track WHERE source_id = :sourceId")
    suspend fun getCount(sourceId: Long): Int

    @Query("SELECT COUNT(*) FROM music_track WHERE album_id = :albumId")
    suspend fun getCountByAlbum(albumId: String): Int
}
