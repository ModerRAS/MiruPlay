package com.miruplay.tv.data.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.dao.CloudDriveAutomationDao
import com.miruplay.tv.data.entity.CloudDriveConfigEntity
import com.miruplay.tv.data.entity.RssDownloadTaskEntity
import com.miruplay.tv.data.entity.RssProcessedItemEntity
import com.miruplay.tv.data.entity.RssSubscriptionEntity
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.CloudDriveLibraryMode
import com.miruplay.tv.model.DEFAULT_CLOUD_DRIVE_ENDPOINT_URL
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudDriveAutomationRepositoryImpl @Inject constructor(
    private val dao: CloudDriveAutomationDao
) : CloudDriveAutomationRepository {
    override fun observeConfig(): Flow<CloudDriveAutomationConfig> =
        dao.observeConfig().map { it?.toDomain() ?: CloudDriveAutomationConfig() }

    override suspend fun getConfig(): Result<CloudDriveAutomationConfig> = withIo {
        Result.success(dao.getConfig()?.toDomain() ?: CloudDriveAutomationConfig())
    }

    override suspend fun saveConfig(config: CloudDriveAutomationConfig): Result<Unit> = withIo {
        dao.upsertConfig(config.toEntity())
        Result.success(Unit)
    }

    override suspend fun updateLastRunAt(timestamp: Long): Result<Unit> = withIo {
        dao.updateLastRunAt(timestamp)
        Result.success(Unit)
    }

    override fun observeSubscriptions(): Flow<List<RssSubscriptionInfo>> =
        dao.observeSubscriptions().map { subscriptions -> subscriptions.map { it.toDomain() } }

    override suspend fun listEnabledSubscriptions(): Result<List<RssSubscriptionInfo>> = withIo {
        Result.success(dao.listEnabledSubscriptions().map { it.toDomain() })
    }

    override suspend fun saveSubscription(subscription: RssSubscriptionInfo): Result<Long> = withIo {
        Result.success(dao.upsertSubscription(subscription.toEntity()))
    }

    override suspend fun deleteSubscription(id: Long): Result<Unit> = withIo {
        dao.deleteSubscription(id)
        Result.success(Unit)
    }

    override suspend fun markSubscriptionChecked(id: Long, timestamp: Long): Result<Unit> = withIo {
        dao.updateSubscriptionCheckedAt(id, timestamp)
        Result.success(Unit)
    }

    override suspend fun isItemProcessed(subscriptionId: Long, itemKey: String): Result<Boolean> = withIo {
        Result.success(dao.processedItemCount(subscriptionId, itemKey) > 0)
    }

    override suspend fun markItemProcessed(item: RssProcessedItemInfo): Result<Unit> = withIo {
        dao.insertProcessedItem(item.toEntity())
        Result.success(Unit)
    }

    override suspend fun saveDownloadTask(task: RssDownloadTaskInfo): Result<Long> = withIo {
        Result.success(dao.upsertDownloadTask(task.toEntity()))
    }

    private suspend fun <T> withIo(block: suspend () -> Result<T>): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                Result.failure(AppError.SyncError.WriteFailed("cloud_drive_automation", e.message ?: "数据库写入失败"))
            }
        }
}

private fun CloudDriveConfigEntity.toDomain(): CloudDriveAutomationConfig =
    CloudDriveAutomationConfig(
        endpointUrl = endpointUrl.ifBlank { DEFAULT_CLOUD_DRIVE_ENDPOINT_URL },
        username = username,
        webDavSourceId = webDavSourceId,
        inboxPath = inboxPath,
        libraryPath = libraryPath,
        libraryMode = libraryMode.toLibraryMode(),
        intervalMinutes = intervalMinutes,
        enabled = enabled,
        lastRunAt = lastRunAt,
        rssProxyEnabled = rssProxyEnabled,
        rssProxyHost = rssProxyHost,
        rssProxyPort = rssProxyPort
    )

private fun CloudDriveAutomationConfig.toEntity(): CloudDriveConfigEntity =
    CloudDriveConfigEntity(
        endpointUrl = endpointUrl,
        username = username,
        webDavSourceId = webDavSourceId,
        inboxPath = inboxPath,
        libraryPath = libraryPath,
        libraryMode = libraryMode.name,
        intervalMinutes = intervalMinutes,
        enabled = enabled,
        lastRunAt = lastRunAt,
        rssProxyEnabled = rssProxyEnabled,
        rssProxyHost = rssProxyHost,
        rssProxyPort = rssProxyPort
    )

private fun String.toLibraryMode(): CloudDriveLibraryMode =
    runCatching { CloudDriveLibraryMode.valueOf(this) }
        .getOrDefault(CloudDriveLibraryMode.ORGANIZED_LIBRARY)

private fun RssSubscriptionEntity.toDomain(): RssSubscriptionInfo =
    RssSubscriptionInfo(
        id = id,
        name = name,
        url = url,
        filterRegex = filterRegex,
        enabled = enabled,
        lastCheckedAt = lastCheckedAt
    )

private fun RssSubscriptionInfo.toEntity(): RssSubscriptionEntity =
    RssSubscriptionEntity(
        id = id,
        name = name.ifBlank { url },
        url = url,
        filterRegex = filterRegex?.takeIf { it.isNotBlank() },
        enabled = enabled,
        lastCheckedAt = lastCheckedAt
    )

private fun RssProcessedItemInfo.toEntity(): RssProcessedItemEntity =
    RssProcessedItemEntity(
        subscriptionId = subscriptionId,
        itemKey = itemKey,
        title = title,
        url = url,
        processedAt = processedAt
    )

private fun RssDownloadTaskInfo.toEntity(): RssDownloadTaskEntity =
    RssDownloadTaskEntity(
        id = id,
        subscriptionId = subscriptionId,
        itemKey = itemKey,
        title = title,
        url = url,
        status = status.name,
        message = message,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
