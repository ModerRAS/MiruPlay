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
    return "看到 ${formatPlaybackDuration(position)}"
}

fun Episode.continueEpisodeProgress(progress: ProgressRecord?): Boolean {
    val position = progress?.positionMs?.coerceAtLeast(0L) ?: return false
    return position > 0L && !isCompleted(progress)
}

private fun completionThreshold(duration: Long): Long =
    (duration * COMPLETION_RATIO).toLong().coerceAtLeast(1L)

private fun formatPlaybackDuration(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
