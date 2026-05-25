package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.RssSubscriptionInfo

interface WebControlEndpointService {
    suspend fun getServerInfo(port: Int): ServerInfoDto
    suspend fun listSources(): List<MediaSourceInfo>
    suspend fun browseLocalDirectories(path: String): LocalDirectoryDto
    suspend fun browseCloudDriveDirectories(endpointUrl: String, path: String): CloudDriveDirectoryDto
    suspend fun addSource(request: SourceRequest): MediaSourceInfo
    suspend fun updateSource(sourceId: Long, request: SourceRequest): MediaSourceInfo
    suspend fun removeSource(sourceId: Long)
    suspend fun testSource(request: SourceTestRequest): SourceTestResponse
    suspend fun scanSource(sourceId: Long): SourceScanResponse
    suspend fun scanAllSources(): List<SourceScanResponse>
    suspend fun getCloudDriveAutomation(): CloudDriveAutomationDto
    suspend fun saveCloudDriveConfig(request: CloudDriveConfigRequest): CloudDriveAutomationDto
    suspend fun loginCloudDrive(request: CloudDriveLoginRequest): CloudDriveAutomationDto
    suspend fun saveCloudDriveToken(request: CloudDriveTokenRequest): CloudDriveTokenResponse
    suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse
    suspend fun saveRssSubscription(request: RssSubscriptionRequest): RssSubscriptionInfo
    suspend fun updateRssSubscription(id: Long, request: RssSubscriptionRequest): RssSubscriptionInfo
    suspend fun deleteRssSubscription(id: Long)
    suspend fun searchLibrary(query: String): LibraryDto
    suspend fun getAnimeDetail(animeId: String): AnimeDetailDto
    suspend fun playEpisode(request: PlayEpisodeRequest): PlaybackStatusDto
    suspend fun playbackCommand(request: PlaybackCommandRequest): PlaybackStatusDto
    suspend fun playbackStatus(): PlaybackStatusDto
}

fun interface WebControlStaticAssets {
    fun read(path: String): ByteArray?
}
