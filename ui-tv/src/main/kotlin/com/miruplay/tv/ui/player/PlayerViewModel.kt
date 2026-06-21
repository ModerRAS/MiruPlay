package com.miruplay.tv.ui.player

import android.util.Log
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.PLAYBACK_SEEK_BACK_SECONDS
import com.miruplay.tv.model.PLAYBACK_SEEK_FORWARD_SECONDS
import com.miruplay.tv.model.PlaybackProgressSession
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.PlaybackTimingConventions
import com.miruplay.tv.model.ToneMappingProfilePreset
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.adjustForSession
import com.miruplay.tv.model.buildToneMappingPreset
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.playbackDisplayTitle
import com.miruplay.tv.model.toApproximatePreset
import com.miruplay.tv.player.AudioTrack
import com.miruplay.tv.player.LibVlcVoutMode
import com.miruplay.tv.player.PlaybackController
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.model.toPlaybackSource
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.NextPlaybackSourceResolver
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.savePlaybackProgressOnCompletion
import com.miruplay.tv.repository.savePlaybackProgressSnapshot
import com.miruplay.tv.sync.BangumiSyncEngine
import androidx.media3.common.Player
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val progressRepository: PlaybackProgressRepository,
    private val metadataRepository: Lazy<MetadataRepository>,
    private val mediaRepository: Lazy<MediaSourceRepository>,
    private val bangumiSyncEngine: Lazy<BangumiSyncEngine>,
    private val playbackPreferences: PlaybackPreferencesRepository
) : ViewModel() {
    val playbackState: StateFlow<PlaybackState> = playbackController.state
    val currentVideoSignalDescriptor: StateFlow<VideoSignalDescriptor?> = playbackController.currentVideoSignalDescriptor
    val currentRenderRuleKey: StateFlow<VideoRenderRuleKey> = playbackController.currentRenderRuleKey
    val currentToneMappingRuleSet: StateFlow<ToneMappingRuleSet> = playbackController.currentToneMappingRuleSet
    val currentRequestedBackend: StateFlow<PlaybackRenderBackend> = playbackController.requestedRenderBackend
    val currentActiveBackend: StateFlow<PlaybackRenderBackend> = playbackController.activeRenderBackend
    val fallbackReason: StateFlow<String?> = playbackController.fallbackReason
    val sessionRuleOverrides: StateFlow<Map<VideoRenderRuleKey, ToneMappingRuleSet>> = playbackController.sessionRuleOverrides

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _availableSubtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val availableSubtitles: StateFlow<List<SubtitleTrack>> = _availableSubtitles.asStateFlow()

    private val _availableAudioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val availableAudioTracks: StateFlow<List<AudioTrack>> = _availableAudioTracks.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _controlsInteractionToken = MutableStateFlow(0)
    val controlsInteractionToken: StateFlow<Int> = _controlsInteractionToken.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _activePlaybackSource = MutableStateFlow<PlaybackSource?>(null)
    val activePlaybackSource: StateFlow<PlaybackSource?> = _activePlaybackSource.asStateFlow()

    private val _displayTitle = MutableStateFlow("")
    val displayTitle: StateFlow<String> = _displayTitle.asStateFlow()

    private val _displaySubtitle = MutableStateFlow("")
    val displaySubtitle: StateFlow<String> = _displaySubtitle.asStateFlow()

    private val _finishEvents = MutableSharedFlow<PlaybackFinishEvent>(extraBufferCapacity = 1)
    val finishEvents: SharedFlow<PlaybackFinishEvent> = _finishEvents.asSharedFlow()
    private val _formatAwarePreferences = MutableStateFlow(FormatAwareToneMappingPreferences())
    val formatAwarePreferences: StateFlow<FormatAwareToneMappingPreferences> = _formatAwarePreferences.asStateFlow()

    init {
        viewModelScope.launch {
            refreshFormatAwarePreferences()
        }
        viewModelScope.launch {
            playbackState.collect { state ->
                _errorMessage.value = (state as? PlaybackState.Error)?.error
            }
        }
    }

    /** Expose Media3 Player for PlayerView rendering */
    fun getPlayer(): Player? = playbackController.getPlayer()

    fun usesVlcVideoLayout(): Boolean = playbackController.usesVlcVideoLayout()

    fun bindVlcVideoHost(hostView: View) {
        Log.i(
            "PlayerViewModel",
            "bindVlcVideoHost host=${hostView.javaClass.simpleName} controller=${playbackController.javaClass.simpleName}",
        )
        playbackController.bindVlcVideoHost(hostView)
    }

    fun unbindVlcVideoHost() {
        Log.i(
            "PlayerViewModel",
            "unbindVlcVideoHost controller=${playbackController.javaClass.simpleName}",
        )
        playbackController.unbindVlcVideoHost()
    }

    private var progressSaveJob: Job? = null
    private var positionPollJob: Job? = null
    private var finishObserverJob: Job? = null
    private var presentationJob: Job? = null
    private var activeSource: PlaybackSource? = null
    private var pendingSeekPositionMs: Long? = null
    private val nextPlaybackSourceResolver by lazy {
        NextPlaybackSourceResolver(
            metadata = metadataRepository.get(),
            progress = progressRepository,
            mediaSources = mediaRepository.get(),
        )
    }

    fun play(source: PlaybackSource) {
        viewModelScope.launch {
            _errorMessage.value = null
            pendingSeekPositionMs = null
            _currentPosition.value = source.startPosition.coerceAtLeast(0L)
            activeSource = source
            _activePlaybackSource.value = source
            startPresentationResolution(source)
            refreshFormatAwarePreferences()
            playbackController.play(source).also {
                _duration.value = playbackController.getDuration()
                refreshTracks()
                startPositionPolling()
                startProgressSaving(source)
                startFinishObserver(source)
            }
        }
    }

    fun pause() {
        viewModelScope.launch {
            playbackController.pause()
            saveCurrentProgress()
        }
    }

    fun resume() {
        viewModelScope.launch {
            playbackController.resume()
        }
    }

    fun togglePlayback() {
        viewModelScope.launch {
            if (playbackController.isPlaying()) {
                playbackController.pause()
                saveCurrentProgress()
            } else {
                playbackController.resume()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        seekFromControls(positionMs)
    }

    fun skipForward() {
        seekFromControls(seekBasePosition() + PLAYBACK_SEEK_FORWARD_SECONDS * 1_000L)
    }

    fun skipBackward() {
        seekFromControls(seekBasePosition() - PLAYBACK_SEEK_BACK_SECONDS * 1_000L)
    }

    fun toggleControls() {
        if (_controlsVisible.value) {
            hideControls()
        } else {
            showControls()
        }
    }

    fun showControls() {
        _controlsVisible.value = true
        _controlsInteractionToken.value += 1
    }

    fun hideControls() {
        _controlsVisible.value = false
    }

    fun selectSubtitle(index: Int) {
        viewModelScope.launch {
            playbackController.setSubtitleTrack(index)
        }
    }

    fun selectAudioTrack(index: Int) {
        viewModelScope.launch {
            playbackController.setAudioTrack(index)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            playbackController.setPlaybackSpeed(speed)
            _playbackSpeed.value = speed
        }
    }

    fun setToneMappingBackendForSession(backend: PlaybackRenderBackend?) {
        viewModelScope.launch {
            playbackController.setRequestedRenderBackend(backend)
        }
    }

    fun applyToneMappingPresetForCurrentFormat(preset: ToneMappingProfilePreset) {
        viewModelScope.launch {
            val ruleKey = currentRenderRuleKey.value
            playbackController.setSessionRuleOverride(
                ruleKey = ruleKey,
                ruleSet = buildToneMappingPreset(ruleKey, preset),
            )
        }
    }

    fun clearToneMappingSessionOverride() {
        viewModelScope.launch {
            playbackController.clearSessionRuleOverrides()
        }
    }

    fun saveCurrentToneMappingRuleAsDefault() {
        viewModelScope.launch {
            val ruleKey = currentRenderRuleKey.value
            val updated = _formatAwarePreferences.value.normalized().copy(
                rules = _formatAwarePreferences.value.normalized().rules + (
                    ruleKey to currentToneMappingRuleSet.value
                )
            )
            playbackPreferences.setFormatAwareToneMappingPreferences(updated)
            _formatAwarePreferences.value = updated.normalized()
        }
    }

    fun adjustCurrentToneMappingTargetSdrNits(delta: Int) {
        adjustCurrentToneMappingRule { it.adjustForSession(targetSdrNitsDelta = delta) }
    }

    fun adjustCurrentToneMappingContrastRecovery(delta: Int) {
        adjustCurrentToneMappingRule { it.adjustForSession(contrastRecoveryDelta = delta) }
    }

    fun adjustCurrentToneMappingSaturationRecovery(delta: Int) {
        adjustCurrentToneMappingRule { it.adjustForSession(saturationRecoveryDelta = delta) }
    }

    fun adjustCurrentToneMappingHighlightCompression(delta: Int) {
        adjustCurrentToneMappingRule { it.adjustForSession(highlightCompressionDelta = delta) }
    }

    fun resetCurrentToneMappingToDefault() {
        viewModelScope.launch {
            val ruleKey = currentRenderRuleKey.value
            playbackController.setSessionRuleOverride(
                ruleKey = ruleKey,
                ruleSet = _formatAwarePreferences.value.normalized().rules.getValue(ruleKey),
            )
        }
    }

    fun setDefaultToneMappingPreset(ruleKey: VideoRenderRuleKey, preset: ToneMappingProfilePreset) {
        viewModelScope.launch {
            val updated = _formatAwarePreferences.value.normalized().copy(
                rules = _formatAwarePreferences.value.normalized().rules + (
                    ruleKey to buildToneMappingPreset(ruleKey, preset)
                )
            )
            playbackPreferences.setFormatAwareToneMappingPreferences(updated)
            _formatAwarePreferences.value = updated.normalized()
        }
    }

    fun setDefaultRenderBackend(backend: PlaybackRenderBackend) {
        viewModelScope.launch {
            val updated = _formatAwarePreferences.value.normalized().copy(
                defaultBackend = backend
            )
            playbackPreferences.setFormatAwareToneMappingPreferences(updated)
            _formatAwarePreferences.value = updated.normalized()
        }
    }

    fun currentToneMappingPreset(): ToneMappingProfilePreset =
        currentToneMappingRuleSet.value.toApproximatePreset()

    private fun adjustCurrentToneMappingRule(
        transform: (ToneMappingRuleSet) -> ToneMappingRuleSet,
    ) {
        viewModelScope.launch {
            val ruleKey = currentRenderRuleKey.value
            playbackController.setSessionRuleOverride(
                ruleKey = ruleKey,
                ruleSet = transform(currentToneMappingRuleSet.value),
            )
        }
    }

    fun pendingGlFrameCaptureLabel(): String? =
        playbackController.pendingGlFrameCaptureLabel()

    fun pendingLibVlcNativeSnapshotLabel(): String? =
        playbackController.pendingLibVlcNativeSnapshotLabel()

    fun requestLibVlcNativeSnapshot(label: String) {
        playbackController.requestLibVlcNativeSnapshot(label)
    }

    fun currentLibVlcVoutMode(): LibVlcVoutMode? =
        playbackController.currentLibVlcVoutMode()

    fun clearPendingGlFrameCaptureLabel(label: String) {
        playbackController.clearPendingGlFrameCaptureLabel(label)
    }

    fun clearPendingLibVlcNativeSnapshotLabel(label: String) {
        playbackController.clearPendingLibVlcNativeSnapshotLabel(label)
    }

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = viewModelScope.launch {
            while (true) {
                pendingSeekPositionMs?.let {
                    _currentPosition.value = it
                } ?: run {
                    _currentPosition.value = playbackController.getCurrentPosition()
                }
                _duration.value = playbackController.getDuration()
                refreshTracks()
                delay(500)
            }
        }
    }

    private fun seekFromControls(positionMs: Long) {
        val target = coerceSeekPosition(positionMs)
        pendingSeekPositionMs = target
        _currentPosition.value = target
        viewModelScope.launch {
            playbackController.seekTo(target)
            if (pendingSeekPositionMs == target) {
                pendingSeekPositionMs = null
            }
        }
    }

    private fun seekBasePosition(): Long =
        pendingSeekPositionMs ?: _currentPosition.value

    private fun coerceSeekPosition(positionMs: Long): Long {
        return PlaybackTimingConventions.coercePlaybackPositionMs(positionMs, _duration.value)
    }

    private fun refreshTracks() {
        _availableSubtitles.value = playbackController.getAvailableSubtitles()
        _availableAudioTracks.value = playbackController.getAvailableAudioTracks()
    }

    private fun startProgressSaving(source: PlaybackSource) {
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            while (true) {
                delay(15_000) // Every 15 seconds
                saveProgressSnapshot(source)
            }
        }
    }

    private fun startFinishObserver(source: PlaybackSource) {
        finishObserverJob?.cancel()
        finishObserverJob = viewModelScope.launch {
            playbackState
                .filterIsInstance<PlaybackState.Ended>()
                .first { it.source == source }
            handlePlaybackEnded(source)
        }
    }

    fun saveCurrentProgress() {
        viewModelScope.launch {
            saveProgressSnapshot(activeSource)
        }
    }

    fun saveCurrentProgressAndNavigate(onSaved: () -> Unit) {
        viewModelScope.launch {
            saveProgressSnapshot(activeSource)
            stopPlayback()
            onSaved()
        }
    }

    fun stopPlaybackWhenLeaving() {
        viewModelScope.launch {
            saveProgressSnapshot(activeSource)
            stopPlayback()
        }
    }

    private suspend fun saveProgressSnapshot(
        source: PlaybackSource? = activeSource,
        positionMs: Long? = null,
        incrementPlayCount: Boolean = false
    ) {
        val currentSource = source ?: return
        val episodeId = currentSource.episodeId ?: extractEpisodeId(currentSource.uri)
        val position = positionMs ?: playbackController.getCurrentPosition()
        savePlaybackProgressSnapshot(
            episodeId = episodeId,
            positionMs = position,
            incrementPlayCount = incrementPlayCount,
            saveProgress = progressRepository::saveProgress,
        )
    }

    private suspend fun handlePlaybackEnded(source: PlaybackSource) {
        val episodeId = source.episodeId ?: extractEpisodeId(source.uri)
        savePlaybackProgressOnCompletion(
            session = PlaybackProgressSession(
                episodeId = episodeId,
                startPositionMs = source.startPosition,
            ),
            queryDurationMs = { Result.success(playbackController.getDuration()) },
            queryPositionMs = { Result.success(playbackController.getCurrentPosition()) },
            saveProgress = progressRepository::saveProgress,
        )

        val nextSource = if (playbackPreferences.getEndAction() == PlaybackEndAction.PLAY_NEXT_EPISODE) {
            buildNextPlaybackSource(source)
        } else {
            null
        }

        if (nextSource != null) {
            play(nextSource)
            viewModelScope.launch {
                bangumiSyncEngine.get().markEpisodeWatched(episodeId)
            }
        } else {
            bangumiSyncEngine.get().markEpisodeWatched(episodeId)
            _finishEvents.emit(PlaybackFinishEvent.NavigateBack)
        }
    }

    private suspend fun buildNextPlaybackSource(source: PlaybackSource): PlaybackSource? {
        return nextPlaybackSourceResolver.build(source)
    }

    private fun startPresentationResolution(source: PlaybackSource) {
        presentationJob?.cancel()
        _displayTitle.value = source.displayTitle()
        _displaySubtitle.value = source.mediaSourceId
        presentationJob = viewModelScope.launch {
            val episodeId = source.episodeId ?: return@launch
            val metadata = metadataRepository.get()
            val episode = metadata.getCachedEpisode(episodeId).getOrNull() ?: return@launch
            if (activeSource != source) return@launch

            _displayTitle.value = episode.playbackDisplayTitle()
            _displaySubtitle.value = metadata.getCachedMetadata(episode.animeId)
                .getOrNull()
                ?.displayTitle()
                ?.takeIf { it.isNotBlank() }
                ?: source.mediaSourceId.ifBlank { episode.animeId }
        }
    }

    private suspend fun refreshFormatAwarePreferences() {
        _formatAwarePreferences.value = playbackPreferences
            .getFormatAwareToneMappingPreferences()
            .normalized()
    }

    private fun extractEpisodeId(uri: String): String {
        return uri.substringAfterLast("/").substringBeforeLast(".")
    }

    private suspend fun stopPlayback() {
        positionPollJob?.cancel()
        progressSaveJob?.cancel()
        finishObserverJob?.cancel()
        presentationJob?.cancel()
        pendingSeekPositionMs = null
        playbackController.stop()
        activeSource = null
        _activePlaybackSource.value = null
        _displayTitle.value = ""
        _displaySubtitle.value = ""
        _currentPosition.value = 0L
        _duration.value = 0L
        _availableSubtitles.value = emptyList()
        _availableAudioTracks.value = emptyList()
    }

    override fun onCleared() {
        positionPollJob?.cancel()
        progressSaveJob?.cancel()
        finishObserverJob?.cancel()
        presentationJob?.cancel()
        val controller = playbackController
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            controller.stop()
        }
        super.onCleared()
    }
}

sealed interface PlaybackFinishEvent {
    data object NavigateBack : PlaybackFinishEvent
}
