package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.MediaSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: MediaSourceEntity): Long

    @Query("UPDATE media_source SET name = :name, url = :url, username = :username, password = :password, is_connected = :isConnected WHERE id = :id")
    suspend fun update(id: Long, name: String? = null, url: String? = null, username: String? = null, password: String? = null, isConnected: Boolean? = null)

    @Query("DELETE FROM media_source WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM media_source ORDER BY last_scanned DESC")
    suspend fun getAll(): List<MediaSourceEntity>

    @Query("SELECT * FROM media_source WHERE id = :id")
    suspend fun getById(id: Long): MediaSourceEntity?

    @Query("SELECT * FROM media_source ORDER BY last_scanned DESC")
    fun observeAll(): Flow<List<MediaSourceEntity>>
}
