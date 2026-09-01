package com.miruplay.tv.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music_album")
data class MusicAlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String? = null,
    @ColumnInfo(name = "cover_url") val coverUrl: String? = null,
    @ColumnInfo(name = "track_count") val trackCount: Int = 0,
    @ColumnInfo(name = "source_id") val sourceId: Long = 0L,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis()
)
