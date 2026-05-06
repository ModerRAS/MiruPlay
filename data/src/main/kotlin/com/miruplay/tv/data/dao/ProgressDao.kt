package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.ProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE episode_id = :episodeId")
    suspend fun getByEpisodeId(episodeId: String): ProgressEntity?

    @Query("SELECT * FROM progress ORDER BY last_watched DESC")
    suspend fun getAll(): List<ProgressEntity>

    @Query("DELETE FROM progress WHERE episode_id = :episodeId")
    suspend fun deleteByEpisodeId(episodeId: String)

    @Query("SELECT * FROM progress ORDER BY last_watched DESC LIMIT :limit")
    suspend fun getContinueWatching(limit: Int = 20): List<ProgressEntity>

    @Query("SELECT * FROM progress ORDER BY last_watched DESC")
    fun observeAll(): Flow<List<ProgressEntity>>
}
