package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import com.miruplay.tv.repository.DEFAULT_LOCAL_LOG_READ_LIMIT

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
    suspend fun startCloudDriveAutomationRun(): CloudDriveRunStatusDto =
        CloudDriveRunStatusDto(
            status = CloudDriveRunStatusDto.SUCCEEDED,
            running = false,
            summary = runCloudDriveAutomationNow(),
        )
    suspend fun getCloudDriveAutomationRunStatus(): CloudDriveRunStatusDto =
        CloudDriveRunStatusDto.idle()
    suspend fun saveRssSubscription(request: RssSubscriptionRequest): RssSubscriptionInfo
    suspend fun updateRssSubscription(id: Long, request: RssSubscriptionRequest): RssSubscriptionInfo
    suspend fun deleteRssSubscription(id: Long)
    suspend fun getNetworkProxy(): NetworkProxyDto = NetworkProxyDto()
    suspend fun saveNetworkProxy(request: NetworkProxyRequest): NetworkProxyDto =
        NetworkProxyDto(enabled = request.enabled, host = request.host.trim(), port = request.port.coerceIn(1, 65_535))
    suspend fun getLogUpload(): LogUploadDto
    suspend fun saveLogUploadConfig(request: LogUploadConfigRequest): LogUploadDto
    suspend fun saveLogUploadToken(request: LogUploadTokenRequest): LogUploadDto
    suspend fun clearLogUploadToken(): LogUploadDto
    suspend fun uploadPendingLogs(): LogUploadDto
    suspend fun getLocalLogs(limit: Int = DEFAULT_LOCAL_LOG_READ_LIMIT): LocalLogsDto =
        LocalLogsDto(totalCount = 0, returnedCount = 0, truncatedCount = 0, records = emptyList())
    suspend fun downloadLocalLogs(sinceTimestampMs: Long? = null): LocalLogDownload =
        LocalLogDownload(
            fileName = "miruplay-logs.jsonl",
            contentType = "application/x-ndjson; charset=utf-8",
            content = ByteArray(0),
        )
    suspend fun getMetadataSettings(): MetadataSettingsDto
    suspend fun getBangumiArchive(): BangumiArchiveDto =
        BangumiArchiveDto(available = false, hasSubjectData = false, lastError = "Bangumi Archive 下载在当前运行环境不可用")
    suspend fun downloadBangumiArchive(): BangumiArchiveDto =
        getBangumiArchive()
    suspend fun saveBangumiToken(request: BangumiTokenRequest): MetadataSettingsDto
    suspend fun clearBangumiToken(): MetadataSettingsDto
    suspend fun searchLibrary(query: String): LibraryDto
    suspend fun getAnimeDetail(animeId: String): AnimeDetailDto
    suspend fun playEpisode(request: PlayEpisodeRequest): PlaybackStatusDto
    suspend fun playbackCommand(request: PlaybackCommandRequest): PlaybackStatusDto
    suspend fun playbackStatus(): PlaybackStatusDto
}

fun interface WebControlStaticAssets {
    fun read(path: String): ByteArray?
}
