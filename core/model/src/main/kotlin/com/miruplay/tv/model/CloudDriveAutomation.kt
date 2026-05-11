package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class CloudDriveAutomationConfig(
    val endpointUrl: String = "",
    val username: String = "",
    val webDavSourceId: Long? = null,
    val inboxPath: String = "",
    val libraryPath: String = "",
    val intervalMinutes: Int = 30,
    val enabled: Boolean = false,
    val lastRunAt: Long = 0L
)

@Serializable
data class RssSubscriptionInfo(
    val id: Long = 0L,
    val name: String = "",
    val url: String = "",
    val filterRegex: String? = null,
    val enabled: Boolean = true,
    val lastCheckedAt: Long = 0L
)

@Serializable
data class RssProcessedItemInfo(
    val subscriptionId: Long,
    val itemKey: String,
    val title: String,
    val url: String,
    val processedAt: Long
)

@Serializable
data class RssDownloadTaskInfo(
    val id: Long = 0L,
    val subscriptionId: Long,
    val itemKey: String,
    val title: String,
    val url: String,
    val status: RssDownloadStatus = RssDownloadStatus.SUBMITTED,
    val message: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
enum class RssDownloadStatus {
    SUBMITTED,
    ORGANIZED,
    FAILED
}
