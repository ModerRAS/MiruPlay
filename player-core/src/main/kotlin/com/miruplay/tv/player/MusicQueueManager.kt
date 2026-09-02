package com.miruplay.tv.player

import com.miruplay.tv.model.MusicTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

enum class MusicRepeatMode { OFF, ONE, ALL }

data class MusicQueue(
    val tracks: List<MusicTrack> = emptyList(),
    val currentIndex: Int = 0,
    val shuffle: Boolean = false,
    val repeat: MusicRepeatMode = MusicRepeatMode.OFF
) {
    val currentTrack: MusicTrack? get() = tracks.getOrNull(currentIndex)
    val hasNext: Boolean get() = tracks.isNotEmpty() && (repeat == MusicRepeatMode.ALL || currentIndex < tracks.lastIndex)
    val hasPrevious: Boolean get() = tracks.isNotEmpty() && (repeat == MusicRepeatMode.ALL || currentIndex > 0)
}

@Singleton
class MusicQueueManager @Inject constructor() {
    private val _queue = MutableStateFlow(MusicQueue())
    val queue: StateFlow<MusicQueue> = _queue.asStateFlow()

    private var originalOrder: List<MusicTrack> = emptyList()

    fun setQueue(tracks: List<MusicTrack>, startTrackId: String? = null, shuffle: Boolean = false) {
        originalOrder = tracks
        val ordered = if (shuffle) tracks.shuffled(Random.Default) else tracks
        val startIdx = startTrackId?.let { id -> ordered.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
        _queue.value = MusicQueue(tracks = ordered, currentIndex = startIdx, shuffle = shuffle, repeat = _queue.value.repeat)
    }

    fun toggleShuffle() {
        val current = _queue.value
        if (current.tracks.isEmpty()) return
        val currentTrack = current.currentTrack
        if (!current.shuffle) {
            val shuffled = current.tracks.shuffled(Random.Default)
            val idx = currentTrack?.let { t -> shuffled.indexOfFirst { it.id == t.id } } ?: 0
            _queue.value = current.copy(tracks = shuffled, currentIndex = idx.coerceAtLeast(0), shuffle = true)
        } else {
            val idx = currentTrack?.let { t -> originalOrder.indexOfFirst { it.id == t.id } } ?: 0
            _queue.value = current.copy(tracks = originalOrder, currentIndex = idx.coerceAtLeast(0), shuffle = false)
        }
    }

    fun setRepeat(mode: MusicRepeatMode) {
        _queue.value = _queue.value.copy(repeat = mode)
    }

    fun next(): MusicTrack? {
        val q = _queue.value
        if (q.tracks.isEmpty()) return null
        val nextIdx = when {
            q.repeat == MusicRepeatMode.ONE -> q.currentIndex
            q.currentIndex < q.tracks.lastIndex -> q.currentIndex + 1
            q.repeat == MusicRepeatMode.ALL -> 0
            else -> return null
        }
        _queue.value = q.copy(currentIndex = nextIdx)
        return _queue.value.currentTrack
    }

    fun previous(): MusicTrack? {
        val q = _queue.value
        if (q.tracks.isEmpty()) return null
        val prevIdx = when {
            q.repeat == MusicRepeatMode.ONE -> q.currentIndex
            q.currentIndex > 0 -> q.currentIndex - 1
            q.repeat == MusicRepeatMode.ALL -> q.tracks.lastIndex
            else -> return null
        }
        _queue.value = q.copy(currentIndex = prevIdx)
        return _queue.value.currentTrack
    }

    fun jumpTo(trackId: String): MusicTrack? {
        val q = _queue.value
        val idx = q.tracks.indexOfFirst { it.id == trackId }.takeIf { it >= 0 } ?: return null
        _queue.value = q.copy(currentIndex = idx)
        return _queue.value.currentTrack
    }
}
