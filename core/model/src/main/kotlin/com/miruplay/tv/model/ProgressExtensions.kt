package com.miruplay.tv.model

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
    val total = duration.takeIf { it > 0L } ?: return 0f
    return (coercePlaybackPosition(record.positionMs).toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

fun Episode.progressLabel(progress: ProgressRecord?): String {
    if (isCompleted(progress)) return "已看"
    val position = progress?.let { coercePlaybackPosition(it.positionMs) } ?: return "未看"
    if (position <= 0L) return "未看"
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

fun List<Pair<Episode, ProgressRecord?>>.continueActionLabel(): String =
    detailContinueActionLabel(continueProgressEpisode()?.episodeNumber)

fun playbackProgressPositionLabel(positionMs: Long): String =
    "看到 ${formatPlaybackPosition(positionMs.coerceAtLeast(0L))}"

fun playbackProgressRecordLabel(progress: ProgressRecord?): String {
    val position = progress?.positionMs?.coerceAtLeast(0L) ?: return "未看"
    return if (position <= 0L) "未看" else playbackProgressPositionLabel(position)
}

fun recentPlaybackInitialStatus(): String =
    "尚未载入最近播放。"

fun recentPlaybackLoadedStatus(records: List<ProgressRecord>): String =
    if (records.isEmpty()) {
        "还没有最近播放记录。"
    } else {
        "已载入 ${records.size} 条最近播放。"
    }

fun recentPlaybackShowingStatus(records: List<ProgressRecord>): String =
    if (records.isEmpty()) {
        "还没有最近播放记录。"
    } else {
        "正在显示 ${records.size} 条最近播放。"
    }

fun recentPlaybackRequiredStatus(): String =
    "请先选择一条最近播放记录。"

fun ProgressRecord?.retainedSelectionInProgressRecords(
    records: List<ProgressRecord>,
): ProgressRecord? =
    this?.let { selected ->
        records.firstOrNull { it.episodeId == selected.episodeId }
    }

fun ProgressRecord.resumeStartSecondsText(): String =
    (positionMs.coerceAtLeast(0L) / 1_000L).toString()

fun ProgressRecord.loadedPlaybackStatus(displayName: String): String =
    "已载入最近播放：$displayName。"

private fun completionThreshold(duration: Long): Long =
    (duration * COMPLETION_RATIO).toLong().coerceAtLeast(1L)

private fun List<Pair<Episode, ProgressRecord?>>.continueProgressEpisode(): Episode? =
    filter { (episode, progress) -> episode.continueEpisodeProgress(progress) }
        .maxByOrNull { (_, progress) -> progress?.lastWatched ?: 0L }
        ?.first
