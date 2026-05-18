package com.miruplay.tv.desktop

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.confidencePercentLabel
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MetadataBatchMatch
import com.miruplay.tv.repository.MetadataBatchPlan
import com.miruplay.tv.repository.metadataQuery
import com.miruplay.tv.repository.isSameCandidate
import com.miruplay.tv.repository.metadataAppliedStatus
import com.miruplay.tv.repository.metadataApplyEntryRequiredStatus
import com.miruplay.tv.repository.metadataBatchResultRequiredStatus
import com.miruplay.tv.repository.metadataClearEntryRequiredStatus
import com.miruplay.tv.repository.metadataClearedStatus
import com.miruplay.tv.repository.metadataIndexedVideoRequiredStatus
import com.miruplay.tv.repository.metadataInitialStatus
import com.miruplay.tv.repository.metadataQueryRequiredStatus
import com.miruplay.tv.repository.metadataQuerySetFromIndexStatus
import com.miruplay.tv.repository.metadataReviewNoMatchStatus
import com.miruplay.tv.repository.metadataSearchResultStatus
import com.miruplay.tv.repository.metadataSearchSelectionRequiredStatus
import com.miruplay.tv.repository.metadataSearchStartedStatus
import com.miruplay.tv.repository.metadataSourceRequiredStatus
import com.miruplay.tv.repository.replaceByMediaKey
import com.miruplay.tv.repository.replaceByMediaKeys
import com.miruplay.tv.repository.replaceMatch
import com.miruplay.tv.repository.reviewConflictStatus
import com.miruplay.tv.repository.selectedCandidateLabel as sharedSelectedCandidateLabel
import com.miruplay.tv.repository.selectedMetadataStatus
import com.miruplay.tv.repository.selectedReviewStatus as sharedSelectedReviewStatus
import com.miruplay.tv.repository.statusFor
import com.miruplay.tv.repository.withSelectedCandidate as sharedWithSelectedCandidate

internal fun bangumiQueryFor(entry: MediaIndexEntry?): String? =
    entry?.metadataQuery()

internal fun bangumiDisplayTitle(result: ScraperResult): String =
    result.displayTitle()

internal fun bangumiConfidenceLabel(result: ScraperResult): String =
    result.confidencePercentLabel()

internal fun bangumiInitialStatus(): String =
    metadataInitialStatus("Bangumi")

internal fun bangumiIndexedVideoRequiredStatus(): String =
    metadataIndexedVideoRequiredStatus()

internal fun bangumiQuerySetFromIndexStatus(): String =
    metadataQuerySetFromIndexStatus()

internal fun bangumiQueryRequiredStatus(): String =
    metadataQueryRequiredStatus("Bangumi")

internal fun bangumiSearchStartedStatus(query: String): String =
    metadataSearchStartedStatus(query, "Bangumi")

internal fun bangumiSearchResultStatus(query: String, resultCount: Int): String =
    metadataSearchResultStatus(query, resultCount, "Bangumi")

internal fun bangumiSourceRequiredStatus(): String =
    metadataSourceRequiredStatus()

internal fun bangumiBatchResultRequiredStatus(): String =
    metadataBatchResultRequiredStatus("Bangumi")

internal fun MetadataBatchPlan.bangumiReviewConflictStatus(): String =
    reviewConflictStatus()

internal fun bangumiReviewNoMatchStatus(): String =
    metadataReviewNoMatchStatus()

internal fun ScraperResult.selectedBangumiStatus(): String =
    selectedMetadataStatus()

internal fun bangumiApplyEntryRequiredStatus(): String =
    metadataApplyEntryRequiredStatus("Bangumi")

internal fun bangumiSearchSelectionRequiredStatus(): String =
    metadataSearchSelectionRequiredStatus("Bangumi")

internal fun MediaIndexEntry.bangumiAppliedStatus(): String =
    metadataAppliedStatus("Bangumi")

internal fun bangumiClearEntryRequiredStatus(): String =
    metadataClearEntryRequiredStatus()

internal fun MediaIndexEntry.bangumiClearedStatus(): String =
    metadataClearedStatus()

internal fun MetadataBatchPlan?.batchStatusFor(match: MetadataBatchMatch): String =
    statusFor(match)

internal fun MetadataBatchMatch.withSelectedCandidate(candidate: ScraperResult): MetadataBatchMatch =
    sharedWithSelectedCandidate(candidate)

internal fun MetadataBatchMatch.selectedCandidateLabel(): String =
    sharedSelectedCandidateLabel()

internal fun MetadataBatchMatch.selectedReviewStatus(): String =
    sharedSelectedReviewStatus()

internal fun List<MetadataBatchMatch>.replaceBatchMatch(updated: MetadataBatchMatch): List<MetadataBatchMatch> =
    replaceMatch(updated)

internal fun ScraperResult.isSameBangumiCandidate(other: ScraperResult?): Boolean =
    isSameCandidate(other)

internal fun List<MediaIndexEntry>.replaceEntry(updated: MediaIndexEntry): List<MediaIndexEntry> =
    replaceByMediaKey(updated)

internal fun List<MediaIndexEntry>.replaceEntries(updatedEntries: List<MediaIndexEntry>): List<MediaIndexEntry> =
    replaceByMediaKeys(updatedEntries)
