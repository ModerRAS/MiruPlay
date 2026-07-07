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
        recognitionMode: MediaRecognitionMode = MediaRecognitionMode.DIRECTORY,
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
        if (recognitionMode != MediaRecognitionMode.DIRECTORY) {
            put(CONNECTION_RECOGNITION_MODE, recognitionMode.name)
        }
    }

    fun sourceConnectionInfoFromPersistence(
        type: MediaSourceType,
        location: String?,
        username: String?,
        password: String?,
        extraConnectionInfo: Map<String, String> = emptyMap(),
    ): Map<String, String> = buildMap {
        location?.let { persistedLocation ->
            put(CONNECTION_URL, persistedLocation)
            if (type == MediaSourceType.LOCAL) {
                put(CONNECTION_PATH, persistedLocation)
                if (persistedLocation.startsWith(CONTENT_SCHEME, ignoreCase = true)) {
                    put(CONNECTION_URI, persistedLocation)
                }
            }
        }
        username?.let { put(CONNECTION_USERNAME, it) }
        password?.let { put(CONNECTION_PASSWORD, it) }
        extraConnectionInfo.forEach { (key, value) -> put(key, value) }
    }

    fun defaultSourceLocation(
        type: MediaSourceType,
        localPath: String,
    ): String =
        when (type) {
            MediaSourceType.LOCAL -> localPath
            MediaSourceType.WEBDAV -> ""
            MediaSourceType.SMB -> SMB_SCHEME
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
    const val CONNECTION_RECOGNITION_MODE = "recognitionMode"
    val PERSISTED_CONNECTION_KEYS: Set<String> = setOf(
        CONNECTION_URL,
        CONNECTION_PATH,
        CONNECTION_USERNAME,
        CONNECTION_PASSWORD,
        CONNECTION_RECOGNITION_MODE,
    )

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

fun MediaSourceInfo.persistenceLocation(): String? =
    remoteUrl() ?: localRootPath()

fun MediaSourceType.defaultSourceLocation(localPath: String): String =
    MediaSourceInfoConventions.defaultSourceLocation(this, localPath)

fun MediaSourceInfo.connectionUsername(): String =
    connectionInfo[MediaSourceInfoConventions.CONNECTION_USERNAME].orEmpty()

fun MediaSourceInfo.connectionPassword(): String =
    connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD].orEmpty()

fun MediaSourceInfo.connectionPasswordOrNull(): String? =
    connectionInfo[MediaSourceInfoConventions.CONNECTION_PASSWORD]

fun MediaSourceInfo.hasConnectionPassword(): Boolean =
    MediaSourceInfoConventions.CONNECTION_PASSWORD in connectionInfo

fun MediaSourceInfo.connectionDomain(): String =
    connectionInfo[MediaSourceInfoConventions.CONNECTION_DOMAIN].orEmpty()

fun MediaSourceInfo.connectionDisplayName(): String =
    connectionInfo[MediaSourceInfoConventions.CONNECTION_DISPLAY_NAME].orEmpty()

fun MediaSourceInfo.recognitionMode(): MediaRecognitionMode =
    connectionInfo[MediaSourceInfoConventions.CONNECTION_RECOGNITION_MODE]
        ?.trim()
        ?.let { value ->
            MediaRecognitionMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
        ?: MediaRecognitionMode.DIRECTORY

fun MediaSourceInfo.withRecognitionMode(mode: MediaRecognitionMode): MediaSourceInfo =
    copy(
        connectionInfo = if (mode == MediaRecognitionMode.DIRECTORY) {
            connectionInfo - MediaSourceInfoConventions.CONNECTION_RECOGNITION_MODE
        } else {
            connectionInfo + (MediaSourceInfoConventions.CONNECTION_RECOGNITION_MODE to mode.name)
        },
    )

fun MediaSourceInfo.sourceLocation(): String? =
    localRootPath() ?: remoteUrl()
