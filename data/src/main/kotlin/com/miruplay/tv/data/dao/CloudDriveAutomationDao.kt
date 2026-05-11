package com.miruplay.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miruplay.tv.data.entity.CloudDriveConfigEntity
import com.miruplay.tv.data.entity.RssDownloadTaskEntity
import com.miruplay.tv.data.entity.RssProcessedItemEntity
import com.miruplay.tv.data.entity.RssSubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudDriveAutomationDao {
    @Query("SELECT * FROM cloud_drive_config WHERE id = 1")
    suspend fun getConfig(): CloudDriveConfigEntity?

    @Query("SELECT * FROM cloud_drive_config WHERE id = 1")
    fun observeConfig(): Flow<CloudDriveConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: CloudDriveConfigEntity)

    @Query("UPDATE cloud_drive_config SET last_run_at = :lastRunAt WHERE id = 1")
    suspend fun updateLastRunAt(lastRunAt: Long)

    @Query("SELECT * FROM rss_subscription ORDER BY id DESC")
    fun observeSubscriptions(): Flow<List<RssSubscriptionEntity>>

    @Query("SELECT * FROM rss_subscription WHERE enabled = 1 ORDER BY id DESC")
    suspend fun listEnabledSubscriptions(): List<RssSubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubscription(subscription: RssSubscriptionEntity): Long

    @Query("DELETE FROM rss_subscription WHERE id = :id")
    suspend fun deleteSubscription(id: Long)

    @Query("UPDATE rss_subscription SET last_checked_at = :lastCheckedAt WHERE id = :id")
    suspend fun updateSubscriptionCheckedAt(id: Long, lastCheckedAt: Long)

    @Query(
        "SELECT COUNT(*) FROM rss_processed_item " +
            "WHERE subscription_id = :subscriptionId AND item_key = :itemKey"
    )
    suspend fun processedItemCount(subscriptionId: Long, itemKey: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProcessedItem(item: RssProcessedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownloadTask(task: RssDownloadTaskEntity): Long
}
