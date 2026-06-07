package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.MediaSourceEntity
import kotlinx.coroutines.flow.Flow

private const val MEDIA_SOURCE_COLUMNS =
    "id, name, type, content_mode, url, username, password, extra_config, is_connected, last_scanned"

@Dao
interface MediaSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: MediaSourceEntity): Long

    @Query(
        "UPDATE media_source SET name = :name, content_mode = :contentMode, url = :url, " +
            "username = :username, password = :password, extra_config = :extraConfig, " +
            "is_connected = :isConnected, last_scanned = :lastScanned WHERE id = :id"
    )
    suspend fun update(
        id: Long,
        name: String? = null,
        contentMode: String,
        url: String? = null,
        username: String? = null,
        password: String? = null,
        extraConfig: String? = null,
        isConnected: Boolean? = null,
        lastScanned: Long = 0L
    )

    @Query("DELETE FROM media_source WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT $MEDIA_SOURCE_COLUMNS FROM media_source ORDER BY last_scanned DESC")
    suspend fun getAll(): List<MediaSourceEntity>

    @Query("SELECT $MEDIA_SOURCE_COLUMNS FROM media_source WHERE id = :id")
    suspend fun getById(id: Long): MediaSourceEntity?

    @Query("SELECT $MEDIA_SOURCE_COLUMNS FROM media_source ORDER BY last_scanned DESC")
    fun observeAll(): Flow<List<MediaSourceEntity>>
}
