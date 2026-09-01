package com.miruplay.tv.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "music_track",
    indices = [
        Index(value = ["source_id", "file_path", "cue_track_index"], unique = true),
        Index(value = ["album_id"])
    ]
)
data class MusicTrackEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "album_id") val albumId: String,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    val title: String = "",
    val artist: String? = null,
    @ColumnInfo(name = "album_artist") val albumArtist: String? = null,
    @ColumnInfo(name = "album_title") val albumTitle: String? = null,
    @ColumnInfo(name = "track_number") val trackNumber: Int? = null,
    @ColumnInfo(name = "disc_number") val discNumber: Int? = null,
    val duration: Long = 0L,
    @ColumnInfo(name = "cue_path") val cuePath: String? = null,
    @ColumnInfo(name = "cue_track_index") val cueTrackIndex: Int? = null,
    @ColumnInfo(name = "cue_start_ms") val cueStartMs: Long = 0L,
    @ColumnInfo(name = "cue_end_ms") val cueEndMs: Long? = null,
    @ColumnInfo(name = "is_cue_virtual") val isCueVirtual: Boolean = false,
    @ColumnInfo(name = "cover_path") val coverPath: String? = null,
    @ColumnInfo(name = "last_modified") val lastModified: Long = 0L
)
