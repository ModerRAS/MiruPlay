package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import kotlinx.coroutines.flow.Flow

interface CloudDriveAutomationRepository {
    fun observeConfig(): Flow<CloudDriveAutomationConfig>
    suspend fun getConfig(): Result<CloudDriveAutomationConfig>
    suspend fun saveConfig(config: CloudDriveAutomationConfig): Result<Unit>
    suspend fun updateLastRunAt(timestamp: Long): Result<Unit>

    fun observeSubscriptions(): Flow<List<RssSubscriptionInfo>>
    suspend fun listEnabledSubscriptions(): Result<List<RssSubscriptionInfo>>
    suspend fun saveSubscription(subscription: RssSubscriptionInfo): Result<Long>
    suspend fun deleteSubscription(id: Long): Result<Unit>
    suspend fun markSubscriptionChecked(id: Long, timestamp: Long): Result<Unit>

    suspend fun isItemProcessed(subscriptionId: Long, itemKey: String): Result<Boolean>
    suspend fun markItemProcessed(item: RssProcessedItemInfo): Result<Unit>
    suspend fun saveDownloadTask(task: RssDownloadTaskInfo): Result<Long>
}
