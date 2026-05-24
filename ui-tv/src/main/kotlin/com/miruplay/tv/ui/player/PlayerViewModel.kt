package com.miruplay.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.model.PlaybackEndAction
import com.miruplay.tv.model.PLAYBACK_SEEK_BACK_SECONDS
import com.miruplay.tv.model.PLAYBACK_SEEK_FORWARD_SECONDS
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.player.AudioTrack
import com.miruplay.tv.player.PlaybackController
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.NextPlaybackSourceResolver
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.savePlaybackProgressSnapshot
import com.miruplay.tv.sync.BangumiSyncEngine
import androidx.media3.common.Player
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
    private val metadataRepository: MetadataRepository,
    private val mediaRepository: MediaSourceRepository,
    private val bangumiSyncEngine: BangumiSyncEngine,
    private val playbackPreferences: PlaybackPreferencesRepository
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackController.state

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

    private val _finishEvents = MutableSharedFlow<PlaybackFinishEvent>(extraBufferCapacity = 1)
    val finishEvents: SharedFlow<PlaybackFinishEvent> = _finishEvents.asSharedFlow()

    /** Expose Media3 Player for PlayerView rendering */
    fun getPlayer(): Player? = playbackController.getPlayer()

    private var progressSaveJob: Job? = null
    private var positionPollJob: Job? = null
    private var finishObserverJob: Job? = null
    private var activeSource: PlaybackSource? = null
    private var pendingSeekPositionMs: Long? = null
    private val nextPlaybackSourceResolver = NextPlaybackSourceResolver(
        metadata = metadataRepository,
        progress = progressRepository,
        mediaSources = mediaRepository,
    )

    fun play(source: PlaybackSource) {
        viewModelScope.launch {
            _errorMessage.value = null
            pendingSeekPositionMs = null
            _currentPosition.value = source.startPosition.coerceAtLeast(0L)
            activeSource = source
            _activePlaybackSource.value = source
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
        val durationMs = _duration.value
        return if (durationMs > 0L) {
            positionMs.coerceIn(0L, durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
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
        saveProgressSnapshot(
            source = source,
            positionMs = playbackController.getDuration().coerceAtLeast(0L),
            incrementPlayCount = true
        )
        val episodeId = source.episodeId ?: extractEpisodeId(source.uri)

        val nextSource = if (playbackPreferences.getEndAction() == PlaybackEndAction.PLAY_NEXT_EPISODE) {
            buildNextPlaybackSource(source)
        } else {
            null
        }

        if (nextSource != null) {
            play(nextSource)
            viewModelScope.launch {
                bangumiSyncEngine.markEpisodeWatched(episodeId)
            }
        } else {
            bangumiSyncEngine.markEpisodeWatched(episodeId)
            _finishEvents.emit(PlaybackFinishEvent.NavigateBack)
        }
    }

    private suspend fun buildNextPlaybackSource(source: PlaybackSource): PlaybackSource? {
        return nextPlaybackSourceResolver.build(source)
    }

    private fun extractEpisodeId(uri: String): String {
        return uri.substringAfterLast("/").substringBeforeLast(".")
    }

    private suspend fun stopPlayback() {
        positionPollJob?.cancel()
        progressSaveJob?.cancel()
        finishObserverJob?.cancel()
        pendingSeekPositionMs = null
        playbackController.stop()
        activeSource = null
        _activePlaybackSource.value = null
        _currentPosition.value = 0L
        _duration.value = 0L
        _availableSubtitles.value = emptyList()
        _availableAudioTracks.value = emptyList()
    }

    override fun onCleared() {
        positionPollJob?.cancel()
        progressSaveJob?.cancel()
        finishObserverJob?.cancel()
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
