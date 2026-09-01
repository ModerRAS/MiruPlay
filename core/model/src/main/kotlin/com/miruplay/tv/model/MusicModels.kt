package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class MusicAlbum(
    val id: String,
    val title: String,
    val artist: String? = null,
    val coverUrl: String? = null,
    val trackCount: Int = 0,
    val sourceId: Long = 0L,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class MusicTrack(
    val id: String,
    val albumId: String,
    val sourceId: Long,
    val filePath: String,
    val fileName: String,
    val title: String = "",
    val artist: String? = null,
    val albumArtist: String? = null,
    val albumTitle: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val duration: Long = 0L,
    val cuePath: String? = null,
    val cueTrackIndex: Int? = null,
    val cueStartMs: Long = 0L,
    val cueEndMs: Long? = null,
    val isCueVirtual: Boolean = false,
    val coverPath: String? = null
)
