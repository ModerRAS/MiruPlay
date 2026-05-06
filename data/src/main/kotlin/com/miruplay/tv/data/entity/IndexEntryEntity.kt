package com.miruplay.tv.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "index_entry",
    indices = [
        Index(value = ["source_id", "anime_name"]),
        Index(value = ["source_id", "path"], unique = true)
    ]
)
data class IndexEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    val path: String,
    @ColumnInfo(name = "anime_name") val animeName: String? = null,
    @ColumnInfo(name = "season_number") val seasonNumber: Int? = null,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int? = null,
    @ColumnInfo(name = "is_directory") val isDirectory: Boolean = false,
    @ColumnInfo(name = "file_size") val fileSize: Long = 0L,
    @ColumnInfo(name = "last_modified") val lastModified: Long = 0L
)
