package com.miruplay.tv.model

import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.uiText

private const val COMPLETION_RATIO = 0.90f

fun Episode.isCompleted(progress: ProgressRecord?): Boolean {
    if (bangumiCollectionType == 2) return true
    val record = progress ?: return false
    val position = coercePlaybackPosition(record.positionMs)
    if (duration > 0L) {
        return position >= completionThreshold(duration)
    }
    return record.playCount > 0
}

fun Episode.resumePosition(progress: ProgressRecord?): Long {
    val position = progress?.let { coercePlaybackPosition(it.positionMs) } ?: return 0L
    return if (isCompleted(progress)) 0L else position
}

fun Episode.progressFraction(progress: ProgressRecord?): Float {
    val record = progress ?: return 0f
    return PlaybackTimingConventions.playbackProgressFraction(record.positionMs, duration)
}

fun Episode.progressLabel(progress: ProgressRecord?): UiText {
    if (isCompleted(progress)) return uiText("progress_watched", "已看")
    val position = progress?.let { coercePlaybackPosition(it.positionMs) }
        ?: return uiText("progress_unwatched", "未看")
    if (position <= 0L) return uiText("progress_unwatched", "未看")
    return playbackProgressPositionLabel(position)
}

fun Episode.continueEpisodeProgress(progress: ProgressRecord?): Boolean {
    val position = progress?.let { coercePlaybackPosition(it.positionMs) } ?: return false
    return position > 0L && !isCompleted(progress)
}

fun Episode.coercePlaybackPosition(positionMs: Long): Long =
    PlaybackTimingConventions.coercePlaybackPositionMs(positionMs, duration)

fun List<Pair<Episode, ProgressRecord?>>.continueEpisode(): Episode? {
    val partial = continueProgressEpisode()
    if (partial != null) return partial

    return firstOrNull { (episode, progress) -> !episode.isCompleted(progress) }?.first
        ?: firstOrNull()?.first
}

fun List<Pair<Episode, ProgressRecord?>>.continueActionLabel(): UiText =
    detailContinueActionLabel(continueProgressEpisode()?.episodeNumber)

fun playbackProgressPositionLabel(positionMs: Long): UiText {
    val text = formatPlaybackPosition(positionMs.coerceAtLeast(0L))
    return UiText("progress_position", listOf(text), "看到 $text")
}

fun playbackProgressRecordLabel(progress: ProgressRecord?): UiText {
    val position = progress?.positionMs?.coerceAtLeast(0L)
        ?: return uiText("progress_unwatched", "未看")
    return if (position <= 0L) {
        uiText("progress_unwatched", "未看")
    } else {
        playbackProgressPositionLabel(position)
    }
}

fun recentPlaybackInitialStatus(): UiText =
    uiText("recent_playback_initial", "尚未载入最近播放。")

fun recentPlaybackLoadedStatus(records: List<ProgressRecord>): UiText =
    if (records.isEmpty()) {
        uiText("recent_playback_loaded_empty", "还没有最近播放记录。")
    } else {
        val safe = records.size
        UiText("recent_playback_loaded", listOf(safe), "已载入 $safe 条最近播放。")
    }

fun recentPlaybackShowingStatus(records: List<ProgressRecord>): UiText =
    if (records.isEmpty()) {
        uiText("recent_playback_showing_empty", "还没有最近播放记录。")
    } else {
        val safe = records.size
        UiText("recent_playback_showing", listOf(safe), "正在显示 $safe 条最近播放。")
    }

fun recentPlaybackRequiredStatus(): UiText =
    uiText("recent_playback_required", "请先选择一条最近播放记录。")

fun ProgressRecord?.retainedSelectionInProgressRecords(
    records: List<ProgressRecord>,
): ProgressRecord? =
    this?.let { selected ->
        records.firstOrNull { it.episodeId == selected.episodeId }
    }

fun ProgressRecord.resumeStartSecondsText(): String =
    (positionMs.coerceAtLeast(0L) / 1_000L).toString()

fun ProgressRecord.loadedPlaybackStatus(displayName: String): UiText =
    UiText("recent_playback_loaded_one", listOf(displayName), "已载入最近播放：$displayName。")

private fun completionThreshold(duration: Long): Long =
    (duration * COMPLETION_RATIO).toLong().coerceAtLeast(1L)

private fun List<Pair<Episode, ProgressRecord?>>.continueProgressEpisode(): Episode? =
    filter { (episode, progress) -> episode.continueEpisodeProgress(progress) }
        .maxByOrNull { (_, progress) -> progress?.lastWatched ?: 0L }
        ?.first
