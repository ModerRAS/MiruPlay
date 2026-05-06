package com.miruplay.tv.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episode",
    indices = [Index(value = ["anime_id", "season_number"])]
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "anime_id") val animeId: String,
    @ColumnInfo(name = "season_number") val seasonNumber: Int = 1,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int,
    val title: String? = null,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_name") val fileName: String? = null,
    val duration: Long = 0L,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis()
)
