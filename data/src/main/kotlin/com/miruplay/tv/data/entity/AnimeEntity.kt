package com.miruplay.tv.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "anime")
data class AnimeEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "title_cn") val titleCn: String? = null,
    val summary: String? = null,
    val genres: String? = null,  // JSON array from TypeConverter
    val studio: String? = null,
    val director: String? = null,
    @ColumnInfo(name = "episode_count") val episodeCount: Int = 0,
    @ColumnInfo(name = "air_date") val airDate: String? = null,
    val rating: Float = 0f,
    @ColumnInfo(name = "bangumi_id") val bangumiId: String? = null,
    @ColumnInfo(name = "anilist_id") val anilistId: String? = null,
    @ColumnInfo(name = "tmdb_id") val tmdbId: String? = null,
    @ColumnInfo(name = "poster_url") val posterUrl: String? = null,
    @ColumnInfo(name = "poster_local_path") val posterLocalPath: String? = null,
    @ColumnInfo(name = "fanart_url") val fanartUrl: String? = null,
    @ColumnInfo(name = "bangumi_collection_type") val bangumiCollectionType: Int? = null,
    @ColumnInfo(name = "bangumi_ep_status") val bangumiEpStatus: Int = 0,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis()
)
