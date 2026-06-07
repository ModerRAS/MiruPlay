package com.miruplay.tv.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.miruplay.tv.model.MediaContentMode

@Entity(tableName = "media_source")
data class MediaSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,  // LOCAL, WEBDAV, SMB
    @ColumnInfo(name = "content_mode", defaultValue = "ANIME")
    val contentMode: String = MediaContentMode.ANIME.name,
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,  // Legacy Base64 value, migrated to encrypted preferences on read
    @ColumnInfo(name = "extra_config") val extraConfig: String? = null,  // JSON
    @ColumnInfo(name = "is_connected") val isConnected: Boolean = false,
    @ColumnInfo(name = "last_scanned") val lastScanned: Long = 0L
)
