package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.AnimeEntity
import kotlinx.coroutines.flow.Flow

private const val ANIME_COLUMNS = "id, title, title_cn, summary, genres, studio, director, episode_count, air_date, rating, bangumi_id, anilist_id, tmdb_id, poster_url, poster_local_path, fanart_url, bangumi_collection_type, bangumi_ep_status, last_updated"

@Dao
interface AnimeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anime: AnimeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(animeList: List<AnimeEntity>)

    @Query("UPDATE anime SET title = :title, summary = :summary, rating = :rating WHERE id = :id")
    suspend fun update(id: String, title: String? = null, summary: String? = null, rating: Float? = null)

    @Query("SELECT $ANIME_COLUMNS FROM anime WHERE id = :id")
    suspend fun getById(id: String): AnimeEntity?

    @Query("SELECT $ANIME_COLUMNS FROM anime ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAll(): List<AnimeEntity>

    @Query("SELECT $ANIME_COLUMNS FROM anime WHERE title LIKE '%' || :query || '%' OR title_cn LIKE '%' || :query || '%'")
    suspend fun searchByTitle(query: String): List<AnimeEntity>

    @Query("DELETE FROM anime WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT $ANIME_COLUMNS FROM anime ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AnimeEntity>>
}
