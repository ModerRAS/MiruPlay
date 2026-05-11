package com.miruplay.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.repository.ProgressRepository
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.player.AudioTrack
import com.miruplay.tv.player.PlaybackController
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.sync.BangumiSyncEngine
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val progressRepository: ProgressRepository,
    private val bangumiSyncEngine: BangumiSyncEngine
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

    /** Expose Media3 Player for PlayerView rendering */
    fun getPlayer(): Player? = playbackController.getPlayer()

    private var progressSaveJob: Job? = null
    private var positionPollJob: Job? = null
    private var finishObserverJob: Job? = null
    private var activeSource: PlaybackSource? = null

    fun play(source: PlaybackSource) {
        viewModelScope.launch {
            _errorMessage.value = null
            activeSource = source
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
        viewModelScope.launch {
            playbackController.seekTo(positionMs)
        }
    }

    fun skipForward() {
        viewModelScope.launch {
            val pos = playbackController.getCurrentPosition()
            playbackController.seekTo(pos + 30_000)
        }
    }

    fun skipBackward() {
        viewModelScope.launch {
            val pos = playbackController.getCurrentPosition()
            playbackController.seekTo((pos - 10_000).coerceAtLeast(0))
        }
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
                _currentPosition.value = playbackController.getCurrentPosition()
                _duration.value = playbackController.getDuration()
                refreshTracks()
                delay(500)
            }
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
            playbackState.collect { state ->
                if (state is PlaybackState.Ended) {
                    saveProgressSnapshot(
                        source = source,
                        positionMs = playbackController.getDuration().coerceAtLeast(0L),
                        incrementPlayCount = true
                    )
                    bangumiSyncEngine.markEpisodeWatched(source.episodeId ?: extractEpisodeId(source.uri))
                    finishObserverJob?.cancel()
                }
            }
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
            onSaved()
        }
    }

    private suspend fun saveProgressSnapshot(
        source: PlaybackSource? = activeSource,
        positionMs: Long? = null,
        incrementPlayCount: Boolean = false
    ) {
        val currentSource = source ?: return
        val episodeId = currentSource.episodeId ?: extractEpisodeId(currentSource.uri)
        val position = (positionMs ?: playbackController.getCurrentPosition()).coerceAtLeast(0L)
        progressRepository.saveProgress(
            episodeId = episodeId,
            positionMs = position,
            lastWatched = System.currentTimeMillis(),
            incrementPlayCount = incrementPlayCount
        )
    }

    private fun extractEpisodeId(uri: String): String {
        return uri.substringAfterLast("/").substringBeforeLast(".")
    }

    override fun onCleared() {
        super.onCleared()
        positionPollJob?.cancel()
        progressSaveJob?.cancel()
        finishObserverJob?.cancel()
        viewModelScope.launch {
            playbackController.stop()
        }
    }
}
