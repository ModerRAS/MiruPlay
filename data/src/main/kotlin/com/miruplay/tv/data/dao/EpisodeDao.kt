package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.EpisodeEntity

private const val EPISODE_COLUMNS = "id, anime_id, season_number, episode_number, title, file_path, file_name, duration, thumbnail_path, bangumi_episode_id, bangumi_collection_type, last_updated"

@Dao
interface EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<EpisodeEntity>)

    @Query("SELECT $EPISODE_COLUMNS FROM episode WHERE anime_id = :animeId ORDER BY season_number ASC, episode_number ASC")
    suspend fun getByAnimeId(animeId: String): List<EpisodeEntity>

    @Query("SELECT $EPISODE_COLUMNS FROM episode WHERE file_path = :path LIMIT 1")
    suspend fun getByPath(path: String): EpisodeEntity?

    @Query("SELECT $EPISODE_COLUMNS FROM episode WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EpisodeEntity?

    @Query("UPDATE episode SET file_path = :newPath WHERE file_path = :oldPath")
    suspend fun updateFilePath(oldPath: String, newPath: String)

    @Query("DELETE FROM episode WHERE anime_id = :animeId")
    suspend fun deleteByAnimeId(animeId: String)

    @Query("SELECT $EPISODE_COLUMNS FROM episode WHERE anime_id = :animeId AND season_number = :seasonNumber ORDER BY episode_number ASC")
    suspend fun getBySeason(animeId: String, seasonNumber: Int): List<EpisodeEntity>

    @Query("SELECT COUNT(*) FROM episode WHERE anime_id = :animeId")
    suspend fun getCount(animeId: String): Int
}
