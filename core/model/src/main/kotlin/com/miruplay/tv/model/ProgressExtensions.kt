package com.miruplay.tv.model

private const val COMPLETION_RATIO = 0.90f

fun Episode.isCompleted(progress: ProgressRecord?): Boolean {
    if (bangumiCollectionType == 2) return true
    val record = progress ?: return false
    val position = record.positionMs.coerceAtLeast(0L)
    if (duration > 0L) {
        return position >= completionThreshold(duration)
    }
    return record.playCount > 0
}

fun Episode.resumePosition(progress: ProgressRecord?): Long {
    val position = progress?.positionMs?.coerceAtLeast(0L) ?: return 0L
    return if (isCompleted(progress)) 0L else position
}

fun Episode.progressFraction(progress: ProgressRecord?): Float {
    val record = progress ?: return 0f
    val total = duration.takeIf { it > 0L } ?: return 0f
    return (record.positionMs.coerceAtLeast(0L).toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

fun Episode.progressLabel(progress: ProgressRecord?): String {
    if (isCompleted(progress)) return "已看"
    val position = progress?.positionMs?.coerceAtLeast(0L) ?: return "未看"
    if (position <= 0L) return "未看"
    return "看到 ${formatPlaybackPosition(position)}"
}

fun Episode.continueEpisodeProgress(progress: ProgressRecord?): Boolean {
    val position = progress?.positionMs?.coerceAtLeast(0L) ?: return false
    return position > 0L && !isCompleted(progress)
}

fun recentPlaybackInitialStatus(): String =
    "No recent playback loaded."

fun recentPlaybackLoadedStatus(records: List<ProgressRecord>): String =
    if (records.isEmpty()) {
        "No recent playback yet."
    } else {
        "Loaded ${records.size} recent item(s)."
    }

fun recentPlaybackShowingStatus(records: List<ProgressRecord>): String =
    if (records.isEmpty()) {
        "No recent playback yet."
    } else {
        "Showing ${records.size} recent item(s)."
    }

fun recentPlaybackRequiredStatus(): String =
    "Select a recent item first."

fun ProgressRecord.resumeStartSecondsText(): String =
    (positionMs.coerceAtLeast(0L) / 1_000L).toString()

fun ProgressRecord.loadedPlaybackStatus(displayName: String): String =
    "Loaded recent playback: $displayName."

private fun completionThreshold(duration: Long): Long =
    (duration * COMPLETION_RATIO).toLong().coerceAtLeast(1L)
