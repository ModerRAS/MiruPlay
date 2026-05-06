package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.EpisodeEntity

@Dao
interface EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episode WHERE anime_id = :animeId ORDER BY season_number ASC, episode_number ASC")
    suspend fun getByAnimeId(animeId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episode WHERE file_path = :path LIMIT 1")
    suspend fun getByPath(path: String): EpisodeEntity?

    @Query("UPDATE episode SET file_path = :newPath WHERE file_path = :oldPath")
    suspend fun updateFilePath(oldPath: String, newPath: String)

    @Query("DELETE FROM episode WHERE anime_id = :animeId")
    suspend fun deleteByAnimeId(animeId: String)

    @Query("SELECT * FROM episode WHERE anime_id = :animeId AND season_number = :seasonNumber ORDER BY episode_number ASC")
    suspend fun getBySeason(animeId: String, seasonNumber: Int): List<EpisodeEntity>

    @Query("SELECT COUNT(*) FROM episode WHERE anime_id = :animeId")
    suspend fun getCount(animeId: String): Int
}
