package com.miruplay.tv.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rss_processed_item",
    indices = [Index(value = ["subscription_id", "item_key"], unique = true)]
)
data class RssProcessedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "subscription_id") val subscriptionId: Long,
    @ColumnInfo(name = "item_key") val itemKey: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "processed_at") val processedAt: Long
)
