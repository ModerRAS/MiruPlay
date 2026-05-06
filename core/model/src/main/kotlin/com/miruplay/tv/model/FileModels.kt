package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,  // bytes
    val lastModified: Long = 0L,  // epoch ms
    val mimeType: String? = null,
)

@Serializable
data class FileMetadata(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String? = null,
    val duration: Long = 0L,  // ms
    val width: Int = 0,
    val height: Int = 0,
    val codecInfo: String? = null,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
)

@Serializable
enum class SubtitleFormat { ASS, SSA, SRT, VTT }

@Serializable
data class SubtitleTrack(
    val language: String = "und",
    val title: String = "",
    val isExternal: Boolean = false,
    val path: String,
    val format: SubtitleFormat = SubtitleFormat.SRT,
)

@Serializable
data class AudioTrack(
    val index: Int,
    val language: String = "und",
    val title: String = "",
    val codec: String = "",
)
