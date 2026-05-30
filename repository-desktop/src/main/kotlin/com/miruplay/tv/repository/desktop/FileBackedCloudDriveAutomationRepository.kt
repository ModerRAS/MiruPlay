package com.miruplay.tv.repository.desktop

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveAutomationConfig
import com.miruplay.tv.model.DEFAULT_CLOUD_DRIVE_ENDPOINT_URL
import com.miruplay.tv.model.RssDownloadTaskInfo
import com.miruplay.tv.model.RssProcessedItemInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FileBackedCloudDriveAutomationRepository(
    private val store: DesktopRepositoryStore,
) : CloudDriveAutomationRepository {
    private val configFlow = MutableStateFlow(readConfigState())
    private val subscriptionsFlow = MutableStateFlow(readSubscriptionsState())

    override fun observeConfig(): Flow<CloudDriveAutomationConfig> =
        configFlow.asStateFlow()

    override suspend fun getConfig(): Result<CloudDriveAutomationConfig> = runCatching {
        store.read { it.cloudDriveConfig.withDefaultEndpoint() }
    }.fold(
        onSuccess = {
            configFlow.value = it
            Result.success(it)
        },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("cloud-drive-config", it.message ?: "read failed")) },
    )

    override suspend fun saveConfig(config: CloudDriveAutomationConfig): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(cloudDriveConfig = config) to Unit
        }
    }.fold(
        onSuccess = {
            configFlow.value = config
            Result.success(Unit)
        },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("cloud-drive-config", it.message ?: "save failed")) },
    )

    override suspend fun updateLastRunAt(timestamp: Long): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(cloudDriveConfig = state.cloudDriveConfig.copy(lastRunAt = timestamp)) to Unit
        }
    }.fold(
        onSuccess = {
            refreshConfigState()
            Result.success(Unit)
        },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("cloud-drive-config", it.message ?: "update failed")) },
    )

    override fun observeSubscriptions(): Flow<List<RssSubscriptionInfo>> =
        subscriptionsFlow.asStateFlow()

    override suspend fun listEnabledSubscriptions(): Result<List<RssSubscriptionInfo>> = runCatching {
        store.read { state ->
            state.rssSubscriptions
                .filter { it.enabled }
                .sortedByDescending(RssSubscriptionInfo::id)
        }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("rss-subscriptions", it.message ?: "read failed")) },
    )

    override suspend fun saveSubscription(subscription: RssSubscriptionInfo): Result<Long> = runCatching {
        store.update { state ->
            val existing = state.rssSubscriptions.firstOrNull { stored ->
                (subscription.id != 0L && stored.id == subscription.id) ||
                    (subscription.id == 0L && stored.url == subscription.url)
            }
            val id = existing?.id ?: state.nextRssSubscriptionId
            val persisted = subscription.copy(
                id = id,
                lastCheckedAt = subscription.lastCheckedAt.takeIf { it != 0L } ?: existing?.lastCheckedAt ?: 0L,
            )
            val subscriptions = state.rssSubscriptions
                .filterNot { it.id == id || it.url == subscription.url }
                .plus(persisted)
            state.copy(
                nextRssSubscriptionId = if (existing == null) id + 1L else state.nextRssSubscriptionId,
                rssSubscriptions = subscriptions,
            ) to id
        }
    }.fold(
        onSuccess = {
            refreshSubscriptionsState()
            Result.success(it)
        },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("rss-subscriptions", it.message ?: "save failed")) },
    )

    override suspend fun deleteSubscription(id: Long): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(
                rssSubscriptions = state.rssSubscriptions.filterNot { it.id == id },
                rssProcessedItems = state.rssProcessedItems.filterNot { it.subscriptionId == id },
                rssDownloadTasks = state.rssDownloadTasks.filterNot { it.subscriptionId == id },
            ) to Unit
        }
    }.fold(
        onSuccess = {
            refreshSubscriptionsState()
            Result.success(Unit)
        },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("rss-subscriptions", it.message ?: "delete failed")) },
    )

    override suspend fun markSubscriptionChecked(id: Long, timestamp: Long): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(
                rssSubscriptions = state.rssSubscriptions.map { subscription ->
                    if (subscription.id == id) subscription.copy(lastCheckedAt = timestamp) else subscription
                },
            ) to Unit
        }
    }.fold(
        onSuccess = {
            refreshSubscriptionsState()
            Result.success(Unit)
        },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("rss-subscriptions", it.message ?: "update failed")) },
    )

    override suspend fun isItemProcessed(subscriptionId: Long, itemKey: String): Result<Boolean> = runCatching {
        store.read { state ->
            state.rssProcessedItems.any { it.subscriptionId == subscriptionId && it.itemKey == itemKey }
        }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("rss-processed-items", it.message ?: "read failed")) },
    )

    override suspend fun markItemProcessed(item: RssProcessedItemInfo): Result<Unit> = runCatching {
        store.update { state ->
            state.copy(
                rssProcessedItems = state.rssProcessedItems
                    .filterNot { it.subscriptionId == item.subscriptionId && it.itemKey == item.itemKey }
                    .plus(item),
            ) to Unit
        }
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("rss-processed-items", it.message ?: "save failed")) },
    )

    override suspend fun saveDownloadTask(task: RssDownloadTaskInfo): Result<Long> = runCatching {
        store.update { state ->
            val existing = state.rssDownloadTasks.firstOrNull { stored ->
                task.id != 0L && stored.id == task.id
            }
            val id = existing?.id ?: state.nextRssDownloadTaskId
            val persisted = task.copy(id = id)
            state.copy(
                nextRssDownloadTaskId = if (existing == null) id + 1L else state.nextRssDownloadTaskId,
                rssDownloadTasks = state.rssDownloadTasks
                    .filterNot { it.id == id }
                    .plus(persisted),
            ) to id
        }
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(AppError.SyncError.WriteFailed("rss-download-tasks", it.message ?: "save failed")) },
    )

    private fun readConfigState(): CloudDriveAutomationConfig =
        kotlinx.coroutines.runBlocking {
            runCatching { store.read { it.cloudDriveConfig.withDefaultEndpoint() } }.getOrDefault(CloudDriveAutomationConfig())
        }

    private fun readSubscriptionsState(): List<RssSubscriptionInfo> =
        kotlinx.coroutines.runBlocking {
            runCatching {
                store.read { state -> state.rssSubscriptions.sortedByDescending(RssSubscriptionInfo::id) }
            }.getOrDefault(emptyList())
        }

    private suspend fun refreshConfigState() {
        runCatching { store.read { it.cloudDriveConfig.withDefaultEndpoint() } }
            .onSuccess { configFlow.value = it }
    }

    private suspend fun refreshSubscriptionsState() {
        runCatching { store.read { state -> state.rssSubscriptions.sortedByDescending(RssSubscriptionInfo::id) } }
            .onSuccess { subscriptionsFlow.value = it }
    }
}

private fun CloudDriveAutomationConfig.withDefaultEndpoint(): CloudDriveAutomationConfig =
    if (endpointUrl.isBlank()) copy(endpointUrl = DEFAULT_CLOUD_DRIVE_ENDPOINT_URL) else this
