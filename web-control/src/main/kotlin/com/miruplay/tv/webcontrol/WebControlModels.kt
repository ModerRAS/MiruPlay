package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiEnvelope<T>(
    val ok: Boolean,
    val data: T? = null,
    val error: String? = null
)

@Serializable
data class ServerInfoDto(
    val appName: String,
    val deviceName: String,
    val port: Int,
    val localIps: List<String>,
    val startedAt: Long
)

@Serializable
data class SourceRequest(
    val id: Long = 0L,
    val name: String,
    val type: String,
    val location: String,
    val username: String? = null,
    val password: String? = null
)

@Serializable
data class SourceTestRequest(
    val type: String,
    val location: String,
    val username: String? = null,
    val password: String? = null
)

@Serializable
data class SourceTestResponse(
    val connected: Boolean,
    val message: String
)

@Serializable
data class SourceScanResponse(
    val sourceId: Long,
    val animeName: String,
    val episodesFound: Int,
    val newEpisodes: Int,
    val updatedEpisodes: Int
)

@Serializable
data class LibraryDto(
    val continueWatching: List<ContinueWatchingDto>,
    val recentlyAdded: List<Anime>,
    val allAnime: List<Anime>
)

@Serializable
data class ContinueWatchingDto(
    val progressEpisodeId: String,
    val positionMs: Long,
    val lastWatched: Long,
    val playCount: Int,
    val episode: Episode?,
    val anime: Anime?
)

@Serializable
data class AnimeDetailDto(
    val anime: Anime,
    val episodes: List<EpisodeWithProgressDto>
)

@Serializable
data class EpisodeWithProgressDto(
    val episode: Episode,
    val progressMs: Long = 0L,
    val lastWatched: Long = 0L,
    val playCount: Int = 0
)

@Serializable
data class PlayEpisodeRequest(
    val episodeId: String,
    val startPositionMs: Long? = null
)

@Serializable
data class PlaybackCommandRequest(
    val command: String,
    val positionMs: Long? = null,
    val deltaMs: Long? = null,
    val speed: Float? = null
)

@Serializable
data class PlaybackStatusDto(
    val state: String,
    val uri: String? = null,
    val mediaSourceId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val error: String? = null
)

@Serializable
data class NavigationCommand(
    val type: String,
    val payload: JsonElement? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class WebPlaybackSource(
    val uri: String,
    val mediaSourceId: String,
    val startPositionMs: Long = 0L
)

fun MediaSourceInfo.safeForApi(): MediaSourceInfo = copy(
    connectionInfo = connectionInfo.filterKeys { key ->
        !key.equals("password", ignoreCase = true)
    }
)
