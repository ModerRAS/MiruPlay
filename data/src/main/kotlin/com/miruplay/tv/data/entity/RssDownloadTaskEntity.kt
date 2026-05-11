package com.miruplay.tv.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rss_download_task",
    indices = [Index(value = ["subscription_id", "item_key"])]
)
data class RssDownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "subscription_id") val subscriptionId: Long,
    @ColumnInfo(name = "item_key") val itemKey: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "message") val message: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
