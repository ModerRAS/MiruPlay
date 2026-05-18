package com.miruplay.tv.model

object MediaSourceInfoConventions {
    fun local(
        name: String,
        rootPath: String,
        isConnected: Boolean = true,
    ): MediaSourceInfo =
        MediaSourceInfo(
            name = name,
            type = MediaSourceType.LOCAL,
            connectionInfo = mapOf(CONNECTION_PATH to rootPath),
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

    private const val SMB_SCHEME = "smb://"
}
