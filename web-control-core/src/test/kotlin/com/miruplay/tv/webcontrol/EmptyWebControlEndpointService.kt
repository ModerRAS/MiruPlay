package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.RssSubscriptionInfo
import java.io.InputStream

internal open class EmptyWebControlEndpointService : WebControlEndpointService {
    override suspend fun getServerInfo(port: Int): ServerInfoDto = unused()
    override suspend fun listSources(): List<MediaSourceInfo> = unused()
    override suspend fun browseLocalDirectories(path: String): LocalDirectoryDto = unused()
    override suspend fun browseCloudDriveDirectories(endpointUrl: String, path: String): CloudDriveDirectoryDto = unused()
    override suspend fun addSource(request: SourceRequest): MediaSourceInfo = unused()
    override suspend fun updateSource(sourceId: Long, request: SourceRequest): MediaSourceInfo = unused()
    override suspend fun removeSource(sourceId: Long): Unit = unused()
    override suspend fun testSource(request: SourceTestRequest): SourceTestResponse = unused()
    override suspend fun scanSource(sourceId: Long): SourceScanResponse = unused()
    override suspend fun scanAllSources(): List<SourceScanResponse> = unused()
    override suspend fun getCloudDriveAutomation(): CloudDriveAutomationDto = unused()
    override suspend fun saveCloudDriveConfig(request: CloudDriveConfigRequest): CloudDriveAutomationDto = unused()
    override suspend fun loginCloudDrive(request: CloudDriveLoginRequest): CloudDriveAutomationDto = unused()
    override suspend fun saveCloudDriveToken(request: CloudDriveTokenRequest): CloudDriveTokenResponse = unused()
    override suspend fun runCloudDriveAutomationNow(): CloudDriveRunResponse = unused()
    override suspend fun saveRssSubscription(request: RssSubscriptionRequest): RssSubscriptionInfo = unused()
    override suspend fun updateRssSubscription(id: Long, request: RssSubscriptionRequest): RssSubscriptionInfo = unused()
    override suspend fun deleteRssSubscription(id: Long): Unit = unused()
    override suspend fun getLogUpload(): LogUploadDto = unused()
    override suspend fun saveLogUploadConfig(request: LogUploadConfigRequest): LogUploadDto = unused()
    override suspend fun saveLogUploadToken(request: LogUploadTokenRequest): LogUploadDto = unused()
    override suspend fun clearLogUploadToken(): LogUploadDto = unused()
    override suspend fun uploadPendingLogs(): LogUploadDto = unused()
    override suspend fun downloadStartupDiagnostics(name: String): LocalLogDownload = unused()
    override suspend fun getMetadataSettings(): MetadataSettingsDto = unused()
    override suspend fun uploadBangumiArchive(
        input: InputStream,
        originalName: String,
        contentLength: Long,
    ): BangumiArchiveDto = unused()
    override suspend fun saveBangumiToken(request: BangumiTokenRequest): MetadataSettingsDto = unused()
    override suspend fun clearBangumiToken(): MetadataSettingsDto = unused()
    override suspend fun saveTmdbToken(request: TmdbTokenRequest): MetadataSettingsDto = unused()
    override suspend fun clearTmdbToken(): MetadataSettingsDto = unused()
    override suspend fun getScanSettings(): ScanSettingsDto = unused()
    override suspend fun saveScanSettings(request: ScanSettingsRequest): ScanSettingsDto = unused()
    override suspend fun getPlaybackSettings(): PlaybackSettingsDto = unused()
    override suspend fun savePlaybackSettings(request: PlaybackSettingsRequest): PlaybackSettingsDto = unused()
    override suspend fun getWebControlAccess(): WebControlAccessDto = unused()
    override suspend fun saveWebControlAccess(request: WebControlAccessRequest): WebControlAccessDto = unused()
    override suspend fun rotateWebControlAccessToken(): WebControlAccessDto = unused()
    override suspend fun getAppUpdate(): AppUpdateDto = unused()
    override suspend fun checkAppUpdate(): AppUpdateDto = unused()
    override suspend fun downloadAppUpdate(): AppUpdateDownloadResponse = unused()
    override suspend fun openInstallPermissionSettings(): AppUpdateDto = unused()
    override suspend fun appControl(request: AppControlRequest): AppControlDto = unused()
    override suspend fun searchLibrary(query: String): LibraryDto = unused()
    override suspend fun getAnimeDetail(animeId: String): AnimeDetailDto = unused()
    override suspend fun playEpisode(request: PlayEpisodeRequest): PlaybackStatusDto = unused()
    override suspend fun playbackCommand(request: PlaybackCommandRequest): PlaybackStatusDto = unused()
    override suspend fun playbackStatus(): PlaybackStatusDto = unused()
    override suspend fun getPlaybackClockSamples(limit: Int): PlaybackClockSamplesDto = unused()
    override suspend fun getPlaybackNativeDiagnostics(logLimit: Int): PlaybackNativeDiagnosticsDto = unused()
    override suspend fun capturePlaybackProfile(request: PlaybackProfileRequest): PlaybackProfileReportDto = unused()

    private fun unused(): Nothing = error("unused")
}
