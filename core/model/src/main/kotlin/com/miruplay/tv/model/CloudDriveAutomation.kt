package com.miruplay.tv.model

import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
enum class CloudDriveLibraryMode {
    ORGANIZED_LIBRARY,
    SINGLE_DIRECTORY
}

const val DEFAULT_CLOUD_DRIVE_ENDPOINT_URL = "http://localhost:19798"

fun String.isDefaultCloudDriveWebDavEndpoint(): Boolean =
    runCatching {
        URI(MediaPathConventions.canonicalizeRemoteUrl(this)).port ==
            URI(DEFAULT_CLOUD_DRIVE_ENDPOINT_URL).port
    }.getOrDefault(false)

fun webDavDirectoryWarmupChain(parentPath: String): List<String> =
    buildList {
        add("")
        var current = ""
        parentPath.replace('\\', '/').trim('/').split('/')
            .filter(String::isNotBlank)
            .forEach { segment ->
                current += "/$segment"
                add(current)
            }
    }

@Serializable
data class CloudDriveAutomationConfig(
    val endpointUrl: String = DEFAULT_CLOUD_DRIVE_ENDPOINT_URL,
    val username: String = "",
    val webDavSourceId: Long? = null,
    val inboxPath: String = "",
    val libraryPath: String = "",
    val libraryMode: CloudDriveLibraryMode = CloudDriveLibraryMode.ORGANIZED_LIBRARY,
    val intervalMinutes: Int = 30,
    val enabled: Boolean = false,
    val lastRunAt: Long = 0L,
    val rssProxyEnabled: Boolean = false,
    val rssProxyHost: String = "",
    val rssProxyPort: Int = 1080
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

@Serializable
data class CloudDriveRssRunSummary(
    val submitted: Int,
    val skipped: Int,
    val failed: Int,
    val organized: Int,
    val indexed: Int = 0,
    val scraped: Int = 0,
    val noMatch: Int = 0,
)
