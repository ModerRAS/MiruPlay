package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaSourceType { LOCAL, WEBDAV, SMB }

@Serializable
enum class MediaContentMode { ANIME, DRAMA }

@Serializable
enum class MediaRecognitionMode { DIRECTORY, MLIP }

@Serializable
enum class MlipMetadataMode { LIBRARY_DB_LOCAL_PRIORITY, FILES_ONLY }

@Serializable
data class MediaSourceInfo(
    val id: Long = 0,
    val name: String,
    val type: MediaSourceType,
    val contentMode: MediaContentMode = MediaContentMode.ANIME,
    val connectionInfo: Map<String, String> = emptyMap(),
    val isConnected: Boolean = false,
    val lastScanned: Long = 0L,  // epoch ms
)

@Serializable
data class MediaCapabilities(
    val seekable: Boolean = true,
    val supportsRange: Boolean = false,
    val supportsList: Boolean = true,
    val supportsWrite: Boolean = false,
)
