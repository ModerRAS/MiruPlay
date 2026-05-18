package com.miruplay.tv.desktop

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.repository.MediaIndexEntry

internal fun bangumiQueryFor(entry: MediaIndexEntry?): String? {
    entry?.animeName?.takeIf { it.isNotBlank() }?.let { return it }
    val path = entry?.path ?: return null
    return MediaPathConventions.stem(path).takeIf { it.isNotBlank() }
}

internal fun bangumiDisplayTitle(result: ScraperResult): String =
    result.displayTitle()

internal fun DesktopBangumiBatchPlan?.batchStatusFor(match: DesktopBangumiBatchMatch): String =
    when {
        this == null -> "preview"
        conflicts.any { it.query == match.query } -> "conflict"
        readyUpdates.any { it.query == match.query } -> "ready"
        else -> "review"
    }

internal fun DesktopBangumiBatchMatch.withSelectedCandidate(candidate: ScraperResult): DesktopBangumiBatchMatch =
    copy(
        result = candidate,
        candidates = if (candidates.any { it.isSameBangumiCandidate(candidate) }) {
            candidates
        } else {
            candidates + candidate
        },
    )

internal fun DesktopBangumiBatchMatch.selectedCandidateLabel(): String {
    val selectedIndex = candidates.indexOfFirst { it.isSameBangumiCandidate(result) }
    return if (selectedIndex >= 0) {
        "candidate ${selectedIndex + 1}/${candidates.size}"
    } else {
        "${candidates.size} candidates"
    }
}

internal fun List<DesktopBangumiBatchMatch>.replaceBatchMatch(updated: DesktopBangumiBatchMatch): List<DesktopBangumiBatchMatch> =
    map { match -> if (match.query == updated.query) updated else match }

internal fun ScraperResult.isSameBangumiCandidate(other: ScraperResult?): Boolean =
    other != null &&
        animeId == other.animeId &&
        source == other.source

internal fun List<MediaIndexEntry>.replaceEntry(updated: MediaIndexEntry): List<MediaIndexEntry> =
    map { entry ->
        if (entry.sourceId == updated.sourceId && entry.path == updated.path) updated else entry
    }

internal fun List<MediaIndexEntry>.replaceEntries(updatedEntries: List<MediaIndexEntry>): List<MediaIndexEntry> {
    if (updatedEntries.isEmpty()) return this
    val byKey = updatedEntries.associateBy { it.sourceId to it.path }
    return map { entry -> byKey[entry.sourceId to entry.path] ?: entry }
}
