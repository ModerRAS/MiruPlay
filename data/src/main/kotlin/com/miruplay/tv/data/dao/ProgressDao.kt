package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.ProgressEntity
import kotlinx.coroutines.flow.Flow

private const val PROGRESS_COLUMNS = "episode_id, position_ms, last_watched, play_count"

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT $PROGRESS_COLUMNS FROM progress WHERE episode_id = :episodeId")
    suspend fun getByEpisodeId(episodeId: String): ProgressEntity?

    @Query("SELECT $PROGRESS_COLUMNS FROM progress ORDER BY last_watched DESC")
    suspend fun getAll(): List<ProgressEntity>

    @Query("DELETE FROM progress WHERE episode_id = :episodeId")
    suspend fun deleteByEpisodeId(episodeId: String)

    @Query("SELECT $PROGRESS_COLUMNS FROM progress ORDER BY last_watched DESC LIMIT :limit")
    suspend fun getContinueWatching(limit: Int = 20): List<ProgressEntity>

    @Query("SELECT $PROGRESS_COLUMNS FROM progress ORDER BY last_watched DESC")
    fun observeAll(): Flow<List<ProgressEntity>>
}
