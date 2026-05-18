package com.miruplay.tv.desktop

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.metadataQuery
import com.miruplay.tv.repository.isSameCandidate
import com.miruplay.tv.repository.replaceByMediaKey
import com.miruplay.tv.repository.replaceByMediaKeys
import com.miruplay.tv.repository.replaceMatch
import com.miruplay.tv.repository.selectedCandidateLabel as sharedSelectedCandidateLabel
import com.miruplay.tv.repository.statusFor
import com.miruplay.tv.repository.withSelectedCandidate as sharedWithSelectedCandidate

internal fun bangumiQueryFor(entry: MediaIndexEntry?): String? =
    entry?.metadataQuery()

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
    replaceByMediaKey(updated)

internal fun List<MediaIndexEntry>.replaceEntries(updatedEntries: List<MediaIndexEntry>): List<MediaIndexEntry> =
    replaceByMediaKeys(updatedEntries)
