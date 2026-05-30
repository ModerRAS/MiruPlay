package com.miruplay.tv.model

import java.net.URI

const val DEFAULT_CLOUD_DRIVE_INTERVAL_MINUTES = 30
const val MIN_CLOUD_DRIVE_INTERVAL_MINUTES = 15
const val DEFAULT_RSS_PROXY_PORT = 1080
const val MIN_RSS_PROXY_PORT = 1
const val MAX_RSS_PROXY_PORT = 65_535

fun parseCloudDriveIntervalMinutes(value: String): Int =
    value.trim().toIntOrNull()
        ?.coerceAtLeast(MIN_CLOUD_DRIVE_INTERVAL_MINUTES)
        ?: DEFAULT_CLOUD_DRIVE_INTERVAL_MINUTES

fun parseRssProxyPort(value: String): Int =
    value.trim().toIntOrNull()
        ?.coerceIn(MIN_RSS_PROXY_PORT, MAX_RSS_PROXY_PORT)
        ?: DEFAULT_RSS_PROXY_PORT

fun CloudDriveAutomationConfig.withAutomationFormValues(
    endpointUrl: String,
    username: String,
    webDavSourceId: Long?,
    inboxPath: String,
    libraryPath: String,
    libraryMode: CloudDriveLibraryMode,
    intervalMinutes: Int,
    enabled: Boolean,
    rssProxyEnabled: Boolean = false,
    rssProxyHost: String = "",
    rssProxyPort: Int = DEFAULT_RSS_PROXY_PORT,
): CloudDriveAutomationConfig =
    copy(
        endpointUrl = endpointUrl.trim(),
        username = username.trim(),
        webDavSourceId = webDavSourceId,
        inboxPath = inboxPath.trim(),
        libraryPath = libraryPath.trim(),
        libraryMode = libraryMode,
        intervalMinutes = intervalMinutes.coerceAtLeast(MIN_CLOUD_DRIVE_INTERVAL_MINUTES),
        enabled = enabled,
        rssProxyEnabled = rssProxyEnabled,
        rssProxyHost = rssProxyHost.trim(),
        rssProxyPort = rssProxyPort.coerceIn(MIN_RSS_PROXY_PORT, MAX_RSS_PROXY_PORT),
    )

fun buildRssSubscriptionFromForm(
    name: String,
    url: String,
    filterRegex: String,
    enabled: Boolean,
    existingId: Long = 0L,
    existingLastCheckedAt: Long = 0L,
): RssSubscriptionInfo? {
    val normalizedUrl = url.trim()
    if (normalizedUrl.isBlank()) return null

    return RssSubscriptionInfo(
        id = existingId,
        name = name.trim().ifBlank { normalizedUrl },
        url = normalizedUrl,
        filterRegex = filterRegex.trim().takeIf { it.isNotBlank() },
        enabled = enabled,
        lastCheckedAt = existingLastCheckedAt,
    )
}

sealed class RssSubscriptionFormResult {
    data class Ready(val subscription: RssSubscriptionInfo) : RssSubscriptionFormResult()
    data class Invalid(val status: String) : RssSubscriptionFormResult()
}

val RssSubscriptionFormResult.shouldClearFormAfterSubmit: Boolean
    get() = this is RssSubscriptionFormResult.Ready

fun prepareRssSubscriptionForm(
    name: String,
    url: String,
    filterRegex: String,
    enabled: Boolean,
    selectedSubscription: RssSubscriptionInfo? = null,
): RssSubscriptionFormResult {
    val normalizedUrl = url.trim()
    if (normalizedUrl.isBlank()) {
        return RssSubscriptionFormResult.Invalid(rssUrlRequiredStatus())
    }
    val existingSubscription = selectedSubscription?.takeIf { it.url.trim() == normalizedUrl }
    val subscription = buildRssSubscriptionFromForm(
        name = name,
        url = normalizedUrl,
        filterRegex = filterRegex,
        enabled = enabled,
        existingId = existingSubscription?.id ?: 0L,
        existingLastCheckedAt = existingSubscription?.lastCheckedAt ?: 0L,
    ) ?: return RssSubscriptionFormResult.Invalid(rssUrlRequiredStatus())

    return RssSubscriptionFormResult.Ready(subscription)
}

data class CloudDriveLoginFormRequest(
    val endpointUrl: String,
    val username: String,
    val password: String,
)

data class CloudDriveApiTokenFormRequest(
    val endpointUrl: String,
    val token: String,
)

data class CloudDriveDirectoryPickerRequest(
    val endpointUrl: String,
    val token: String,
)

sealed class CloudDriveLoginFormResult {
    data class Ready(val request: CloudDriveLoginFormRequest) : CloudDriveLoginFormResult()
    data class Invalid(val status: String) : CloudDriveLoginFormResult()
}

sealed class CloudDriveApiTokenFormResult {
    data class Ready(val request: CloudDriveApiTokenFormRequest) : CloudDriveApiTokenFormResult()
    data class Invalid(val status: String) : CloudDriveApiTokenFormResult()
}

sealed class CloudDriveDirectoryPickerFormResult {
    data class Ready(val request: CloudDriveDirectoryPickerRequest) : CloudDriveDirectoryPickerFormResult()
    data class Invalid(val status: String) : CloudDriveDirectoryPickerFormResult()
}

fun validateCloudDriveLoginForm(
    endpointUrl: String,
    username: String,
    password: String,
): CloudDriveLoginFormResult {
    val endpoint = endpointUrl.trim()
    val user = username.trim()
    if (endpoint.isBlank() || user.isBlank() || password.isBlank()) {
        return CloudDriveLoginFormResult.Invalid(cloudDriveLoginRequiredStatus())
    }
    validateCloudDriveEndpoint(endpoint)?.let { status ->
        return CloudDriveLoginFormResult.Invalid(status)
    }
    return CloudDriveLoginFormResult.Ready(
        CloudDriveLoginFormRequest(
            endpointUrl = endpoint,
            username = user,
            password = password,
        ),
    )
}

fun validateCloudDriveApiTokenForm(
    endpointUrl: String,
    token: String,
    blankTokenStatus: String = cloudDriveApiTokenRequiredStatus(),
): CloudDriveApiTokenFormResult {
    val endpoint = endpointUrl.trim()
    val apiToken = token.trim()
    val invalidEndpointStatus = validateCloudDriveEndpoint(endpoint)
    return when {
        endpoint.isBlank() && apiToken.isBlank() -> CloudDriveApiTokenFormResult.Invalid(cloudDriveTokenRequiredStatus())
        endpoint.isBlank() -> CloudDriveApiTokenFormResult.Invalid(cloudDriveEndpointRequiredStatus())
        invalidEndpointStatus != null -> CloudDriveApiTokenFormResult.Invalid(invalidEndpointStatus)
        apiToken.isBlank() -> CloudDriveApiTokenFormResult.Invalid(blankTokenStatus)
        else -> CloudDriveApiTokenFormResult.Ready(
            CloudDriveApiTokenFormRequest(
                endpointUrl = endpoint,
                token = apiToken,
            ),
        )
    }
}

fun validateCloudDriveDirectoryPickerForm(
    endpointUrl: String,
    tokenInput: String,
    savedToken: String?,
): CloudDriveDirectoryPickerFormResult {
    val endpoint = endpointUrl.trim()
    if (endpoint.isBlank()) {
        return CloudDriveDirectoryPickerFormResult.Invalid(cloudDriveEndpointRequiredStatus())
    }
    validateCloudDriveEndpoint(endpoint)?.let { status ->
        return CloudDriveDirectoryPickerFormResult.Invalid(status)
    }
    val token = tokenInput.trim().ifBlank { savedToken.orEmpty() }.trim()
    if (token.isBlank()) {
        return CloudDriveDirectoryPickerFormResult.Invalid(cloudDriveTokenLoginRequiredStatus())
    }
    return CloudDriveDirectoryPickerFormResult.Ready(
        CloudDriveDirectoryPickerRequest(
            endpointUrl = endpoint,
            token = token,
        ),
    )
}

private fun validateCloudDriveEndpoint(endpointUrl: String): String? {
    val uri = runCatching { URI(endpointUrl) }.getOrNull() ?: return cloudDriveEndpointInvalidStatus()
    val scheme = uri.scheme?.lowercase().orEmpty()
    if (scheme != "http" && scheme != "https") return cloudDriveEndpointInvalidStatus()
    if (uri.host.isNullOrBlank()) return cloudDriveEndpointInvalidStatus()
    val path = uri.rawPath.orEmpty()
    if ((path.isNotBlank() && path != "/") || !uri.rawQuery.isNullOrBlank() || !uri.rawFragment.isNullOrBlank()) {
        return cloudDriveEndpointInvalidStatus()
    }
    return null
}
