package com.miruplay.tv.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drama_series_cache")
data class DramaSeriesCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "series_id")
    val seriesId: String,
    val title: String,
    @ColumnInfo(name = "original_title")
    val originalTitle: String = "",
    val summary: String = "",
    @ColumnInfo(name = "season_count")
    val seasonCount: Int = 0,
    @ColumnInfo(name = "episode_count")
    val episodeCount: Int = 0,
    @ColumnInfo(name = "poster_url")
    val posterUrl: String? = null,
    @ColumnInfo(name = "fanart_url")
    val fanartUrl: String? = null,
    @ColumnInfo(name = "first_air_date")
    val firstAirDate: String? = null,
    @ColumnInfo(name = "metadata_source")
    val metadataSource: String? = null,
    @ColumnInfo(name = "metadata_id")
    val metadataId: String? = null,
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis(),
)
