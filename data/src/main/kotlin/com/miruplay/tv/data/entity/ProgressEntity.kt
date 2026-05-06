package com.miruplay.tv.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "progress",
    indices = [Index(value = ["episode_id"], unique = true)]
)
data class ProgressEntity(
    @PrimaryKey @ColumnInfo(name = "episode_id") val episodeId: String,
    @ColumnInfo(name = "position_ms") val positionMs: Long = 0L,
    @ColumnInfo(name = "last_watched") val lastWatched: Long = 0L,
    @ColumnInfo(name = "play_count") val playCount: Int = 0
)
