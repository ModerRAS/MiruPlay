package com.miruplay.tv.webcontrol

import android.os.Build
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.player.PlaybackController
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.scanner.ScanCoordinator
import com.miruplay.tv.sync.rss.CloudDriveRssActionCoordinator
import com.miruplay.tv.sync.rss.CloudDriveRssAutomationEngine
import com.miruplay.tv.sync.rss.CloudDriveRssScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebControlService @Inject constructor(
    mediaRepository: MediaSourceRepository,
    metadataRepository: MetadataRepository,
    indexRepository: MediaIndexRepository,
    private val progressRepository: PlaybackProgressRepository,
    scanPreferences: ScanPreferencesRepository,
    cloudDriveRepository: CloudDriveAutomationRepository,
    securePreferences: AppCredentialStore,
    cloudDriveClient: CloudDriveClient,
    cloudDriveEngine: CloudDriveRssAutomationEngine,
    private val scanCoordinator: ScanCoordinator,
    mediaSourceFactory: MediaSourceFactory,
    private val playbackController: PlaybackController,
    private val navigator: WebControlNavigator
) : SharedWebControlEndpointService(
    mediaSourceRepository = mediaRepository,
    metadataRepository = metadataRepository,
    indexRepository = indexRepository,
    progressRepository = progressRepository,
    scanPreferencesRepository = scanPreferences,
    mediaSourceFactory = mediaSourceFactory,
    cloudDriveRepository = cloudDriveRepository,
    credentials = securePreferences,
    cloudDriveClient = cloudDriveClient,
    cloudDriveActions = CloudDriveRssActionCoordinator(
        repository = cloudDriveRepository,
        credentials = securePreferences,
        runner = cloudDriveEngine,
    ),
    deviceNameProvider = { Build.MODEL ?: "Android TV" },
) {
    override suspend fun <T> runOnIo(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { block() }

    override suspend fun scanSourceResultFor(source: MediaSourceInfo): Result<ScanResult> =
        scanCoordinator.scanSource(source.id)

    suspend fun getLogUpload(): LogUploadDto {
        val tokenConfigured = logUploadRepository.isTokenConfigured()
        return LogUploadDto(
            config = OtlpLogUploadConfigDto.from(logUploadRepository.getConfig()),
            status = LogUploadStatusDto.from(logUploadRepository.status.first(), tokenConfigured),
            tokenConfigured = tokenConfigured
        )
    }

    suspend fun saveLogUploadConfig(request: LogUploadConfigRequest): LogUploadDto {
        if (request.enabled && request.endpoint.isBlank()) {
            throw IllegalArgumentException("请填写 OpenObserve API 地址")
        }
        logUploadRepository.saveConfig(
            enabled = request.enabled,
            endpoint = request.endpoint.trim(),
            streamName = request.streamName.trim().ifBlank { "miruplay" }
        )
        return getLogUpload()
    }

    suspend fun saveLogUploadToken(request: LogUploadTokenRequest): LogUploadDto {
        if (request.token.isBlank()) {
            throw IllegalArgumentException("请填写 OpenObserve Token")
        }
        logUploadRepository.saveToken(request.token.trim())
        return getLogUpload()
    }

    suspend fun clearLogUploadToken(): LogUploadDto {
        logUploadRepository.clearToken()
        return getLogUpload()
    }

    suspend fun uploadPendingLogs(): LogUploadDto {
        logUploadRepository.uploadPendingLogs()
        return getLogUpload()
    }

    fun getMetadataSettings(): MetadataSettingsDto =
        MetadataSettingsDto(
            bangumiTokenConfigured = !securePreferences.bangumiAccessToken.isNullOrBlank()
        )

    fun saveBangumiToken(request: BangumiTokenRequest): MetadataSettingsDto {
        val token = request.token.trim()
        if (token.isBlank()) {
            throw IllegalArgumentException("请填写 Bangumi Token")
        }
        securePreferences.bangumiAccessToken = token
        return getMetadataSettings()
    }

    fun clearBangumiToken(): MetadataSettingsDto {
        securePreferences.clearBangumiToken()
        return getMetadataSettings()
    }

    suspend fun getLibrary(): LibraryDto {
        return loadLibrary()
    }

    override suspend fun playEpisodeResolved(
        request: PlayEpisodeRequest,
        episode: Episode,
    ): PlaybackStatusDto {
        val progress = progressRepository.getProgress(episode.id).getOrNull()
        val source = request.toWebControlPlaybackSource(episode, progress)
        navigator.openPlayer(source.toWebPlaybackSource())
        return playbackStatusResolved()
    }

    override suspend fun playbackCommandResolved(request: PlaybackCommandRequest): PlaybackStatusDto {
        request.executeWebControlPlaybackCommand(androidWebControlPlaybackCommandTarget(playbackController))
        return playbackStatusResolved()
    }

    override suspend fun playbackStatusResolved(): PlaybackStatusDto {
        val state = playbackController.state.value
        val currentPosition = runCatching { playbackController.getCurrentPosition() }.getOrDefault(0L)
            .coerceAtLeast(0L)
        val duration = runCatching { playbackController.getDuration() }.getOrDefault(0L)
            .coerceAtLeast(0L)
        return state.toWebControlPlaybackStatus(
            currentPositionMs = currentPosition,
            durationMs = duration,
        )
    }
}

private fun androidWebControlPlaybackCommandTarget(
    playbackController: PlaybackController,
): WebControlPlaybackCommandTarget =
    webControlPlaybackCommandTarget(
        pause = { playbackController.pause() },
        resume = { playbackController.resume() },
        toggle = {
            if (playbackController.isPlaying()) {
                playbackController.pause()
            } else {
                playbackController.resume()
            }
        },
        stop = { playbackController.stop() },
        seekTo = { positionMs -> playbackController.seekTo(positionMs) },
        setPlaybackSpeed = { speed -> playbackController.setPlaybackSpeed(speed) },
        currentPositionMs = { playbackController.getCurrentPosition() },
        durationMs = { playbackController.getDuration() },
    )
