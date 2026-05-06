package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.IndexEntryEntity

@Dao
interface IndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<IndexEntryEntity>)

    @Query("SELECT * FROM index_entry WHERE source_id = :sourceId ORDER BY path ASC")
    suspend fun queryBySourceId(sourceId: Long): List<IndexEntryEntity>

    @Query("SELECT * FROM index_entry WHERE source_id = :sourceId AND anime_name = :animeName ORDER BY season_number ASC, episode_number ASC")
    suspend fun queryByAnimeName(sourceId: Long, animeName: String): List<IndexEntryEntity>

    @Query("DELETE FROM index_entry WHERE source_id = :sourceId")
    suspend fun deleteBySourceId(sourceId: Long)

    @Query("SELECT * FROM index_entry WHERE source_id = :sourceId AND (path LIKE '%' || :query || '%' OR anime_name LIKE '%' || :query || '%')")
    suspend fun search(sourceId: Long, query: String): List<IndexEntryEntity>

    @Query("SELECT DISTINCT anime_name FROM index_entry WHERE source_id = :sourceId AND anime_name IS NOT NULL ORDER BY anime_name ASC")
    suspend fun getDistinctAnimeNames(sourceId: Long): List<String>

    @Query("SELECT COUNT(*) FROM index_entry WHERE source_id = :sourceId")
    suspend fun getCount(sourceId: Long): Int
}
