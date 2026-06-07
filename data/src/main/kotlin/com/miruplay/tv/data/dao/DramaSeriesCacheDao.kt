package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.DramaSeriesCacheEntity

private const val DRAMA_SERIES_CACHE_COLUMNS =
    "series_id, title, original_title, summary, season_count, episode_count, poster_url, fanart_url, first_air_date, metadata_source, metadata_id, last_updated"

@Dao
interface DramaSeriesCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(series: DramaSeriesCacheEntity)

    @Query("SELECT $DRAMA_SERIES_CACHE_COLUMNS FROM drama_series_cache WHERE series_id = :seriesId")
    suspend fun getBySeriesId(seriesId: String): DramaSeriesCacheEntity?

    @Query("DELETE FROM drama_series_cache WHERE series_id = :seriesId")
    suspend fun deleteBySeriesId(seriesId: String)
}
