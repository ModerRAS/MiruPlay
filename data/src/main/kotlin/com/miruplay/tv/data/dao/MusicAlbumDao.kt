package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.MusicAlbumEntity
import kotlinx.coroutines.flow.Flow

private const val MUSIC_ALBUM_COLUMNS = "id, title, artist, cover_url, track_count, source_id, last_updated"

@Dao
interface MusicAlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: MusicAlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<MusicAlbumEntity>)

    @Query("SELECT $MUSIC_ALBUM_COLUMNS FROM music_album WHERE id = :id")
    suspend fun getById(id: String): MusicAlbumEntity?

    @Query("SELECT $MUSIC_ALBUM_COLUMNS FROM music_album WHERE source_id = :sourceId ORDER BY title COLLATE NOCASE ASC")
    suspend fun getBySourceId(sourceId: Long): List<MusicAlbumEntity>

    @Query("SELECT $MUSIC_ALBUM_COLUMNS FROM music_album ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAll(): List<MusicAlbumEntity>

    @Query("SELECT $MUSIC_ALBUM_COLUMNS FROM music_album ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MusicAlbumEntity>>

    @Query("DELETE FROM music_album WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM music_album WHERE source_id = :sourceId")
    suspend fun deleteBySourceId(sourceId: Long)

    @Query("SELECT COUNT(*) FROM music_album WHERE source_id = :sourceId")
    suspend fun getCount(sourceId: Long): Int
}
