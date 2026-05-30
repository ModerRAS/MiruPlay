package com.miruplay.tv.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.miruplay.tv.model.DEFAULT_CLOUD_DRIVE_ENDPOINT_URL

@Entity(tableName = "cloud_drive_config")
data class CloudDriveConfigEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "endpoint_url") val endpointUrl: String = DEFAULT_CLOUD_DRIVE_ENDPOINT_URL,
    @ColumnInfo(name = "username") val username: String = "",
    @ColumnInfo(name = "webdav_source_id") val webDavSourceId: Long? = null,
    @ColumnInfo(name = "inbox_path") val inboxPath: String = "",
    @ColumnInfo(name = "library_path") val libraryPath: String = "",
    @ColumnInfo(name = "library_mode") val libraryMode: String = "ORGANIZED_LIBRARY",
    @ColumnInfo(name = "interval_minutes") val intervalMinutes: Int = 30,
    @ColumnInfo(name = "enabled") val enabled: Boolean = false,
    @ColumnInfo(name = "last_run_at") val lastRunAt: Long = 0L,
    @ColumnInfo(name = "rss_proxy_enabled") val rssProxyEnabled: Boolean = false,
    @ColumnInfo(name = "rss_proxy_host") val rssProxyHost: String = "",
    @ColumnInfo(name = "rss_proxy_port") val rssProxyPort: Int = 1080
)
