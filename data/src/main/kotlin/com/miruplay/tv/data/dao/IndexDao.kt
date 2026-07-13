package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.IndexEntryEntity

private const val INDEX_COLUMNS =
    "id, source_id, path, external_subtitle_paths, anime_name, episode_title, plot, season_number, episode_number, metadata_source, metadata_id, metadata_title, scrape_status, scrape_message, scraped_at, is_directory, file_size, last_modified"

@Dao
interface IndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<IndexEntryEntity>)

    @Query("SELECT $INDEX_COLUMNS FROM index_entry WHERE source_id = :sourceId ORDER BY path ASC")
    suspend fun queryBySourceId(sourceId: Long): List<IndexEntryEntity>

    @Query("SELECT $INDEX_COLUMNS FROM index_entry WHERE source_id = :sourceId AND anime_name = :animeName ORDER BY season_number ASC, episode_number ASC")
    suspend fun queryByAnimeName(sourceId: Long, animeName: String): List<IndexEntryEntity>

    @Query("DELETE FROM index_entry WHERE source_id = :sourceId")
    suspend fun deleteBySourceId(sourceId: Long)

    @Query("SELECT $INDEX_COLUMNS FROM index_entry WHERE source_id = :sourceId AND (path LIKE '%' || :query || '%' OR anime_name LIKE '%' || :query || '%')")
    suspend fun search(sourceId: Long, query: String): List<IndexEntryEntity>

    @Query(
        """
        SELECT DISTINCT anime_key
        FROM (
            SELECT metadata_id AS anime_key FROM index_entry WHERE source_id = :sourceId
            UNION ALL
            SELECT anime_name AS anime_key FROM index_entry WHERE source_id = :sourceId
            UNION ALL
            SELECT metadata_title AS anime_key FROM index_entry WHERE source_id = :sourceId
        )
        WHERE anime_key IS NOT NULL AND TRIM(anime_key) <> ''
        ORDER BY anime_key ASC
        """
    )
    suspend fun getDistinctAnimeNames(sourceId: Long): List<String>

    @Query("SELECT COUNT(*) FROM index_entry WHERE source_id = :sourceId")
    suspend fun getCount(sourceId: Long): Int
}
