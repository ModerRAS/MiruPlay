package com.miruplay.tv.repository

import com.miruplay.tv.model.MediaContentMode
import com.miruplay.tv.model.MetadataSearchContext
import com.miruplay.tv.model.MetadataSearchIntent
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.toPreferredScraperResult
import com.miruplay.tv.model.metadataApplyEntryRequiredTvStatus
import com.miruplay.tv.model.metadataAppliedTvStatus
import com.miruplay.tv.model.metadataBatchResultRequiredTvStatus
import com.miruplay.tv.model.metadataBatchSearchingTvStatus
import com.miruplay.tv.model.metadataBatchStatusLabel
import com.miruplay.tv.model.metadataCandidateCountLabel
import com.miruplay.tv.model.metadataClearEntryRequiredTvStatus
import com.miruplay.tv.model.metadataClearedTvStatus
import com.miruplay.tv.model.metadataIndexedVideoRequiredTvStatus
import com.miruplay.tv.model.metadataInitialTvStatus
import com.miruplay.tv.model.metadataNoBatchEntriesTvStatus
import com.miruplay.tv.model.metadataNoMatchLabel
import com.miruplay.tv.model.metadataPlanSummaryTvStatus
import com.miruplay.tv.model.metadataQueryRequiredTvStatus
import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.uiText
import com.miruplay.tv.model.metadataQuerySetFromIndexTvStatus
import com.miruplay.tv.model.metadataReviewConflictTvStatus
import com.miruplay.tv.model.metadataReviewNoMatchTvStatus
import com.miruplay.tv.model.metadataSearchResultTvStatus
import com.miruplay.tv.model.metadataSearchSelectionRequiredTvStatus
import com.miruplay.tv.model.metadataSearchStartedTvStatus
import com.miruplay.tv.model.metadataSelectedBatchCandidateTvStatus
import com.miruplay.tv.model.metadataSelectedCandidateLabel
import com.miruplay.tv.model.metadataSelectedBatchReviewTvStatus
import com.miruplay.tv.model.metadataSelectedResultTvStatus
import com.miruplay.tv.model.MetadataProviderRef
import com.miruplay.tv.model.metadataSourceRequiredTvStatus

data class MetadataBatchMatch(
    val query: String,
    val result: ScraperResult? = null,
    val candidates: List<ScraperResult> = result?.let { listOf(it) }.orEmpty(),
)

data class MetadataBatchUpdate(
    val query: String,
    val original: MediaIndexEntry,
    val updated: MediaIndexEntry,
    val result: ScraperResult,
)

data class MetadataBatchConflict(
    val query: String,
    val entry: MediaIndexEntry,
)

data class MetadataBatchPlan(
    val readyUpdates: List<MetadataBatchUpdate>,
    val reviewMatches: List<MetadataBatchMatch>,
    val conflicts: List<MetadataBatchConflict>,
)

data class MetadataBatchPreview(
    val matches: List<MetadataBatchMatch>,
    val plan: MetadataBatchPlan?,
    val selectedMatch: MetadataBatchMatch?,
)

data class MetadataBatchCandidateSelection(
    val updatedMatch: MetadataBatchMatch,
    val updatedMatches: List<MetadataBatchMatch>,
    val plan: MetadataBatchPlan,
)

fun MetadataBatchPlan?.statusFor(match: MetadataBatchMatch): String =
    when {
        this == null -> "preview"
        conflicts.any { it.query == match.query } -> "conflict"
        readyUpdates.any { it.query == match.query } -> "ready"
        else -> "review"
    }

fun MetadataBatchMatch.withSelectedCandidate(candidate: ScraperResult): MetadataBatchMatch =
    copy(
        result = candidate,
        candidates = if (candidates.any { it.isSameCandidate(candidate) }) {
            candidates
        } else {
            candidates + candidate
        },
    )

fun MetadataBatchMatch.selectedCandidateLabel(): UiText {
    val selectedIndex = candidates.indexOfFirst { it.isSameCandidate(result) }
    return if (selectedIndex >= 0) {
        metadataSelectedCandidateLabel(selectedIndex, candidates.size)
    } else {
        metadataCandidateCountLabel(candidates.size)
    }
}

fun List<MetadataBatchMatch>.replaceMatch(updated: MetadataBatchMatch): List<MetadataBatchMatch> =
    map { match -> if (match.query == updated.query) updated else match }

fun ScraperResult.isSameCandidate(other: ScraperResult?): Boolean =
    other != null &&
        animeId == other.animeId &&
        source == other.source

fun MetadataBatchPreview.summaryStatus(): UiText =
    plan?.let(MetadataBatchPlanner::displayPlanSummary)
        ?: noMetadataBatchEntriesStatus()

fun MetadataBatchCandidateSelection.selectedStatus(): UiText =
    updatedMatch.selectedCandidateStatus()

fun MetadataBatchMatch.selectedCandidateStatus(): UiText =
    metadataSelectedBatchCandidateTvStatus(query, result?.displayTitle().orEmpty())

fun metadataInitialStatus(sourceName: String = "metadata"): UiText =
    metadataInitialTvStatus(sourceName)

fun metadataIndexedVideoRequiredStatus(): UiText =
    metadataIndexedVideoRequiredTvStatus()

fun metadataQuerySetFromIndexStatus(): UiText =
    metadataQuerySetFromIndexTvStatus()

fun metadataQueryRequiredStatus(sourceName: String = "metadata"): UiText =
    metadataQueryRequiredTvStatus(sourceName)

fun metadataSearchStartedStatus(query: String, sourceName: String = "metadata"): UiText =
    metadataSearchStartedTvStatus(query, sourceName)

fun metadataSearchResultStatus(
    query: String,
    resultCount: Int,
    sourceName: String = "metadata",
): UiText =
    metadataSearchResultTvStatus(query, resultCount, sourceName)

fun metadataSourceRequiredStatus(): UiText =
    metadataSourceRequiredTvStatus()

fun mlipMetadataReadOnlyStatus(): UiText =
    uiText("mlip_metadata_read_only", "MLIP 元数据由 library.db 管理，请在远端修正后重新扫描。")

fun MetadataBatchMatch.selectedReviewStatus(): UiText =
    metadataSelectedBatchReviewTvStatus(query)

fun metadataBatchResultRequiredStatus(sourceName: String = "metadata"): UiText =
    metadataBatchResultRequiredTvStatus(sourceName)

fun MetadataBatchPlan.reviewConflictStatus(): UiText =
    metadataReviewConflictTvStatus(conflicts.size)

fun metadataReviewNoMatchStatus(): UiText =
    metadataReviewNoMatchTvStatus()

fun ScraperResult.selectedMetadataStatus(): UiText =
    metadataSelectedResultTvStatus(displayTitle())

fun metadataApplyEntryRequiredStatus(sourceName: String = "metadata"): UiText =
    metadataApplyEntryRequiredTvStatus(sourceName)

fun metadataSearchSelectionRequiredStatus(sourceName: String = "metadata"): UiText =
    metadataSearchSelectionRequiredTvStatus(sourceName)

fun MediaIndexEntry.metadataAppliedStatus(sourceName: String = "metadata"): UiText =
    metadataAppliedTvStatus(sourceName, path)

fun metadataClearEntryRequiredStatus(): UiText =
    metadataClearEntryRequiredTvStatus()

fun MediaIndexEntry.metadataClearedStatus(): UiText =
    metadataClearedTvStatus(path)

fun metadataBatchSearchingStatus(
    queryCount: Int,
    sourceName: String = "metadata",
): UiText =
    metadataBatchSearchingTvStatus(queryCount, sourceName)

fun noMetadataBatchEntriesStatus(sourceName: String = "metadata"): UiText =
    metadataNoBatchEntriesTvStatus(sourceName)

object MetadataBatchPlanner {
    private const val READY_CONFIDENCE = 0.85f

    fun queriesFor(entries: List<MediaIndexEntry>): List<String> =
        entries
            .mapNotNull(MediaIndexEntry::metadataQuery)
            .distinct()

    fun previewQueryCount(
        entries: List<MediaIndexEntry>,
        queryLimit: Int,
    ): Int =
        previewQueriesFor(entries, queryLimit).size

    fun aggregatedSearchCandidates(
        aggregator: AnimeMetadataSearchAggregator,
    ): suspend (String, List<String>) -> List<ScraperResult> = { query, candidates ->
        aggregator.search(
            MetadataSearchContext(
                contentMode = MediaContentMode.ANIME,
                intent = MetadataSearchIntent.BATCH_PREVIEW,
                title = query,
                aliases = candidates,
                manualQuery = query,
            ),
        ).candidates.mapNotNull { candidate ->
            candidate.toPreferredScraperResult(preferredSources = listOf("Bangumi"))
        }
    }

    suspend fun previewFor(
        entries: List<MediaIndexEntry>,
        queryLimit: Int,
        searchCandidates: suspend (String, List<String>) -> List<ScraperResult>,
    ): MetadataBatchPreview {
        val mediaEntries = entries.mediaFilesOnly()
        val queries = previewQueriesFor(mediaEntries, queryLimit)
        if (queries.isEmpty()) {
            return MetadataBatchPreview(
                matches = emptyList(),
                plan = null,
                selectedMatch = null,
            )
        }

        val matches = queries.map { query ->
            val candidates = searchCandidates(query, candidatesForQuery(query, mediaEntries))
            MetadataBatchMatch(
                query = query,
                result = candidates.firstOrNull(),
                candidates = candidates,
            )
        }
        val plan = planFor(mediaEntries, matches)
        return MetadataBatchPreview(
            matches = matches,
            plan = plan,
            selectedMatch = plan.reviewMatches.firstOrNull { it.result != null }
                ?: matches.firstOrNull(),
        )
    }

    fun selectCandidate(
        entries: List<MediaIndexEntry>,
        matches: List<MetadataBatchMatch>,
        match: MetadataBatchMatch,
        candidate: ScraperResult,
    ): MetadataBatchCandidateSelection {
        val mediaEntries = entries.mediaFilesOnly()
        val updatedMatch = match.withSelectedCandidate(candidate)
        val updatedMatches = matches.replaceMatch(updatedMatch)
        return MetadataBatchCandidateSelection(
            updatedMatch = updatedMatch,
            updatedMatches = updatedMatches,
            plan = planFor(mediaEntries, updatedMatches),
        )
    }

    fun acceptedMatches(matches: List<MetadataBatchMatch>): List<MetadataBatchMatch> =
        matches.filter { (it.result?.confidence ?: 0f) >= READY_CONFIDENCE }

    fun candidatesForQuery(
        query: String,
        entries: List<MediaIndexEntry>,
    ): List<String> {
        val trimmedQuery = query.trim()
        val candidates = entries
            .mediaFilesOnly()
            .filter { it.metadataQuery() == query }
            .flatMap { entry ->
                listOfNotNull(
                    entry.animeName,
                    entry.metadataTitle,
                    entry.metadataSource
                        ?.takeIf { it.isNotBlank() }
                        ?.let { source ->
                            entry.metadataId
                                ?.takeIf { it.isNotBlank() }
                                ?.let { id -> metadataProviderRefHintText(MetadataProviderRef(source = source, id = id)) }
                        },
                    entry.metadataId,
                )
            }
        return (listOf(trimmedQuery) + candidates)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun planFor(
        entries: List<MediaIndexEntry>,
        matches: List<MetadataBatchMatch>,
    ): MetadataBatchPlan {
        val mediaEntries = entries.mediaFilesOnly()
        val readyUpdates = mutableListOf<MetadataBatchUpdate>()
        val reviewMatches = mutableListOf<MetadataBatchMatch>()
        val conflicts = mutableListOf<MetadataBatchConflict>()
        matches.forEach { match ->
            val result = match.result
            if (result == null || result.confidence < READY_CONFIDENCE) {
                reviewMatches += match
                return@forEach
            }
            val matchingEntries = mediaEntries.filter { it.metadataQuery() == match.query }
            if (matchingEntries.any(::hasExternalMetadata)) {
                conflicts += matchingEntries.map { MetadataBatchConflict(match.query, it) }
                return@forEach
            }
            readyUpdates += matchingEntries.map { entry ->
                MetadataBatchUpdate(
                    query = match.query,
                    original = entry,
                    updated = entry.withExternalMetadata(result),
                    result = result,
                )
            }
        }
        return MetadataBatchPlan(
            readyUpdates = readyUpdates,
            reviewMatches = reviewMatches,
            conflicts = conflicts,
        )
    }

    fun displayPreview(matches: List<MetadataBatchMatch>): String = buildString {
        matches.forEach { match ->
            val result = match.result
            val status = if ((result?.confidence ?: 0f) >= READY_CONFIDENCE) "ready" else "review"
            append(match.query)
            append(": ")
            append(result?.let(::displayCandidate) ?: metadataNoMatchLabel().fallback)
            append(" [")
            append(metadataBatchStatusLabel(status).fallback)
            append("]")
            if (match.candidates.size > 1) {
                append(" ")
                append(metadataCandidateCountLabel(match.candidates.size).fallback)
            }
            appendLine()
        }
    }

    fun displayPlanSummary(plan: MetadataBatchPlan): UiText =
        metadataPlanSummaryTvStatus(
            readyCount = plan.readyUpdates.size,
            reviewCount = plan.reviewMatches.size,
            conflictCount = plan.conflicts.size,
        )

    private fun displayCandidate(result: ScraperResult): String =
        result.title + result.titleCn?.takeIf { it.isNotBlank() }?.let { " / $it" }.orEmpty()

    private fun hasExternalMetadata(entry: MediaIndexEntry): Boolean =
        !entry.metadataSource.isNullOrBlank() ||
            !entry.metadataId.isNullOrBlank() ||
            !entry.metadataTitle.isNullOrBlank()

    private fun previewQueriesFor(
        entries: List<MediaIndexEntry>,
        queryLimit: Int,
    ): List<String> =
        queriesFor(entries.mediaFilesOnly())
            .take(queryLimit.coerceAtLeast(0))
}
