package com.miruplay.tv.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miruplay.tv.model.MusicTrack
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.player.MusicQueueManager
import com.miruplay.tv.player.MusicRepeatMode
import com.miruplay.tv.player.PlaybackController
import com.miruplay.tv.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val musicRepository: MusicRepository,
    private val queueManager: MusicQueueManager
) : ViewModel() {
    val playbackState: StateFlow<PlaybackState> = playbackController.state
    val queue = queueManager.queue

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var positionJob: Job? = null
    private var currentTrack: MusicTrack? = null

    init {
        viewModelScope.launch {
            playbackState.collect { state ->
                _isPlaying.value = state is PlaybackState.Playing
                if (state is PlaybackState.Error) {
                    // auto next on error? keep
                }
            }
        }
    }

    fun playAlbum(albumId: String, startTrackId: String? = null) {
        viewModelScope.launch {
            val tracks = musicRepository.getTracksByAlbum(albumId).getOrNull().orEmpty()
            if (tracks.isEmpty()) return@launch
            queueManager.setQueue(tracks, startTrackId)
            queueManager.queue.value.currentTrack?.let { playTrack(it) }
        }
    }

    fun playTrackById(trackId: String) {
        viewModelScope.launch {
            val trackRes = musicRepository.getTrackById(trackId)
            val track = (trackRes as? com.miruplay.tv.core.common.Result.Success)?.data ?: return@launch
            val albumTracks = musicRepository.getTracksByAlbum(track.albumId).getOrNull().orEmpty()
            if (albumTracks.isNotEmpty()) {
                queueManager.setQueue(albumTracks, trackId)
                playTrack(track)
            } else {
                queueManager.setQueue(listOf(track), trackId)
                playTrack(track)
            }
        }
    }

    fun playTrack(track: MusicTrack) {
        currentTrack = track
        val source = track.toPlaybackSource()
        viewModelScope.launch {
            playbackController.play(source)
            val cueDur = track.cueEndMs?.let { end -> end - track.cueStartMs }
            _duration.value = if (track.isCueVirtual && cueDur != null) cueDur else track.duration
            startPositionPolling()
        }
    }

    private fun MusicTrack.toPlaybackSource(): PlaybackSource {
        val start = if (isCueVirtual) cueStartMs else 0L
        val end = if (isCueVirtual) cueEndMs else null
        return PlaybackSource(
            uri = filePath,
            mediaSourceId = sourceId.toString(),
            startPosition = 0L,
            episodeId = id,
            progressId = id,
            cueStartMs = start,
            cueEndMs = end
        )
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            if (playbackController.isPlaying()) playbackController.pause() else playbackController.resume()
        }
    }

    fun next() {
        queueManager.next()?.let { playTrack(it) }
    }

    fun previous() {
        queueManager.previous()?.let { playTrack(it) }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            playbackController.seekTo(positionMs)
            _currentPosition.value = positionMs
        }
    }

    fun toggleShuffle() = queueManager.toggleShuffle()
    fun setRepeat(mode: MusicRepeatMode) = queueManager.setRepeat(mode)

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val pos = playbackController.getCurrentPosition()
                val dur = playbackController.getDuration()
                // With ClippingConfiguration, pos is already 0-based for cue virtual tracks
                _currentPosition.value = pos.coerceAtLeast(0L)
                if (dur > 0) {
                    _duration.value = dur
                }
                // auto next when completed
                if (playbackController.state.value is PlaybackState.Ended) {
                    next()
                    break
                }
            }
        }
    }

    override fun onCleared() {
        positionJob?.cancel()
        super.onCleared()
    }
}
