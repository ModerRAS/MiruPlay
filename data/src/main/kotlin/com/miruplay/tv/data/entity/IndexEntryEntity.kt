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
    @ColumnInfo(name = "external_subtitle_paths") val externalSubtitlePaths: List<String> = emptyList(),
    @ColumnInfo(name = "anime_name") val animeName: String? = null,
    @ColumnInfo(name = "episode_title") val episodeTitle: String? = null,
    val plot: String? = null,
    @ColumnInfo(name = "season_number") val seasonNumber: Int? = null,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int? = null,
    @ColumnInfo(name = "metadata_source") val metadataSource: String? = null,
    @ColumnInfo(name = "metadata_id") val metadataId: String? = null,
    @ColumnInfo(name = "metadata_title") val metadataTitle: String? = null,
    @ColumnInfo(name = "scrape_status") val scrapeStatus: String? = null,
    @ColumnInfo(name = "scrape_message") val scrapeMessage: String? = null,
    @ColumnInfo(name = "scraped_at") val scrapedAt: Long = 0L,
    @ColumnInfo(name = "is_directory") val isDirectory: Boolean = false,
    @ColumnInfo(name = "file_size") val fileSize: Long = 0L,
    @ColumnInfo(name = "last_modified") val lastModified: Long = 0L
)
