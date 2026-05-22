package com.miruplay.tv.model

object MediaSourceInfoConventions {
    fun local(
        name: String,
        rootPath: String,
        displayName: String = "",
        isConnected: Boolean = true,
    ): MediaSourceInfo =
        MediaSourceInfo(
            name = name,
            type = MediaSourceType.LOCAL,
            connectionInfo = sourceConnectionInfo(
                type = MediaSourceType.LOCAL,
                location = rootPath,
                displayName = displayName,
            ),
            isConnected = isConnected,
        )

    fun webDav(
        url: String,
        username: String = "",
        password: String = "",
        isConnected: Boolean = false,
        name: String = webDavDisplayName(url),
    ): MediaSourceInfo =
        MediaSourceInfo(
            name = name,
            type = MediaSourceType.WEBDAV,
            connectionInfo = buildMap {
                put(CONNECTION_URL, url)
                putIfNotBlank(CONNECTION_USERNAME, username)
                putIfNotBlank(CONNECTION_PASSWORD, password)
            },
            isConnected = isConnected,
        )

    fun smb(
        url: String,
        domain: String = "",
        username: String = "",
        password: String = "",
        isConnected: Boolean = false,
        name: String = smbDisplayName(url),
    ): MediaSourceInfo {
        val normalizedUrl = normalizeSmbRoot(url)
        return MediaSourceInfo(
            name = name,
            type = MediaSourceType.SMB,
            connectionInfo = buildMap {
                put(CONNECTION_URL, normalizedUrl)
                putIfNotBlank(CONNECTION_DOMAIN, domain)
                putIfNotBlank(CONNECTION_USERNAME, username)
                putIfNotBlank(CONNECTION_PASSWORD, password)
            },
            isConnected = isConnected,
        )
    }

    fun sourceConnectionInfo(
        type: MediaSourceType,
        location: String,
        displayName: String = "",
        username: String = "",
        password: String = "",
        domain: String = "",
    ): Map<String, String> = buildMap {
        val trimmedLocation = location.trim()
        put(CONNECTION_URL, trimmedLocation)
        if (type == MediaSourceType.LOCAL) {
            put(CONNECTION_PATH, trimmedLocation)
            if (trimmedLocation.startsWith(CONTENT_SCHEME, ignoreCase = true)) {
                put(CONNECTION_URI, trimmedLocation)
            }
            putIfNotBlank(CONNECTION_DISPLAY_NAME, displayName.trim())
        }
        putIfNotBlank(CONNECTION_DOMAIN, domain.trim())
        putIfNotBlank(CONNECTION_USERNAME, username.trim())
        putIfNotBlank(CONNECTION_PASSWORD, password)
    }

    fun normalizeSmbRoot(rawUrl: String): String {
        val normalized = rawUrl.trim().replace('\\', '/').trimEnd('/')
        val withScheme = when {
            normalized.startsWith(SMB_SCHEME, ignoreCase = true) -> normalized
            normalized.startsWith("//") -> "smb:$normalized"
            else -> "$SMB_SCHEME$normalized"
        }
        return withScheme.trimEnd('/')
    }

    fun webDavDisplayName(url: String): String =
        url.substringAfter("://", url).trim('/').ifBlank { "WebDAV" }

    fun smbDisplayName(url: String): String =
        normalizeSmbRoot(url)
            .removePrefixIgnoreCase(SMB_SCHEME)
            .trim('/')
            .ifBlank { "SMB" }

    fun shouldBridgeForPlayback(sourceType: MediaSourceType?, path: String): Boolean {
        val trimmed = path.trim()
        if (trimmed.startsWith(HTTP_SCHEME, ignoreCase = true) ||
            trimmed.startsWith(HTTPS_SCHEME, ignoreCase = true)
        ) {
            return false
        }
        return when (sourceType) {
            MediaSourceType.WEBDAV -> trimmed.startsWith("/")
            MediaSourceType.SMB -> trimmed.startsWith(SMB_SCHEME, ignoreCase = true)
            MediaSourceType.LOCAL,
            null -> false
        }
    }

    private fun MutableMap<String, String>.putIfNotBlank(key: String, value: String) {
        if (value.isNotBlank()) put(key, value)
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

    const val CONNECTION_PATH = "path"
    const val CONNECTION_URL = "url"
    const val CONNECTION_USERNAME = "username"
    const val CONNECTION_PASSWORD = "password"
    const val CONNECTION_DOMAIN = "domain"
    const val CONNECTION_DISPLAY_NAME = "displayName"
    const val CONNECTION_URI = "uri"

    private const val SMB_SCHEME = "smb://"
    private const val HTTP_SCHEME = "http://"
    private const val HTTPS_SCHEME = "https://"
    private const val CONTENT_SCHEME = "content://"
}

fun MediaSourceInfo.localRootPath(): String? =
    connectionInfo[MediaSourceInfoConventions.CONNECTION_PATH]
        ?: connectionInfo[MediaSourceInfoConventions.CONNECTION_URI]
        ?: connectionInfo[MediaSourceInfoConventions.CONNECTION_URL]

fun MediaSourceInfo.remoteUrl(): String? =
    connectionInfo[MediaSourceInfoConventions.CONNECTION_URL]

fun MediaSourceInfo.connectionUsername(): String =
    connectionInfo[MediaSourceInfoConventions.CONNECTION_USERNAME].orEmpty()

fun MediaSourceInfo.connectionPassword(): String =
    connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD].orEmpty()

fun MediaSourceInfo.connectionDomain(): String =
    connectionInfo[MediaSourceInfoConventions.CONNECTION_DOMAIN].orEmpty()

fun MediaSourceInfo.connectionDisplayName(): String =
    connectionInfo[MediaSourceInfoConventions.CONNECTION_DISPLAY_NAME].orEmpty()

fun MediaSourceInfo.sourceLocation(): String? =
    localRootPath() ?: remoteUrl()
