package com.miruplay.tv.desktop

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.isSameCandidate
import com.miruplay.tv.repository.replaceMatch
import com.miruplay.tv.repository.selectedCandidateLabel as sharedSelectedCandidateLabel
import com.miruplay.tv.repository.statusFor
import com.miruplay.tv.repository.withSelectedCandidate as sharedWithSelectedCandidate

internal fun bangumiQueryFor(entry: MediaIndexEntry?): String? {
    entry?.animeName?.takeIf { it.isNotBlank() }?.let { return it }
    val path = entry?.path ?: return null
    return MediaPathConventions.stem(path).takeIf { it.isNotBlank() }
}

internal fun bangumiDisplayTitle(result: ScraperResult): String =
    result.displayTitle()

internal fun DesktopBangumiBatchPlan?.batchStatusFor(match: DesktopBangumiBatchMatch): String =
    statusFor(match)

internal fun DesktopBangumiBatchMatch.withSelectedCandidate(candidate: ScraperResult): DesktopBangumiBatchMatch =
    sharedWithSelectedCandidate(candidate)

internal fun DesktopBangumiBatchMatch.selectedCandidateLabel(): String =
    sharedSelectedCandidateLabel()

internal fun List<DesktopBangumiBatchMatch>.replaceBatchMatch(updated: DesktopBangumiBatchMatch): List<DesktopBangumiBatchMatch> =
    replaceMatch(updated)

internal fun ScraperResult.isSameBangumiCandidate(other: ScraperResult?): Boolean =
    isSameCandidate(other)

internal fun List<MediaIndexEntry>.replaceEntry(updated: MediaIndexEntry): List<MediaIndexEntry> =
    map { entry ->
        if (entry.sourceId == updated.sourceId && entry.path == updated.path) updated else entry
    }

internal fun List<MediaIndexEntry>.replaceEntries(updatedEntries: List<MediaIndexEntry>): List<MediaIndexEntry> {
    if (updatedEntries.isEmpty()) return this
    val byKey = updatedEntries.associateBy { it.sourceId to it.path }
    return map { entry -> byKey[entry.sourceId to entry.path] ?: entry }
}
