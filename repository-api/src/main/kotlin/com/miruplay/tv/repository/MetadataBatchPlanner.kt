package com.miruplay.tv.repository

import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.displayTitle

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

fun MetadataBatchMatch.selectedCandidateLabel(): String {
    val selectedIndex = candidates.indexOfFirst { it.isSameCandidate(result) }
    return if (selectedIndex >= 0) {
        "candidate ${selectedIndex + 1}/${candidates.size}"
    } else {
        "${candidates.size} candidates"
    }
}

fun List<MetadataBatchMatch>.replaceMatch(updated: MetadataBatchMatch): List<MetadataBatchMatch> =
    map { match -> if (match.query == updated.query) updated else match }

fun ScraperResult.isSameCandidate(other: ScraperResult?): Boolean =
    other != null &&
        animeId == other.animeId &&
        source == other.source

fun MetadataBatchPreview.summaryStatus(): String =
    plan?.let(MetadataBatchPlanner::displayPlanSummary)
        ?: noMetadataBatchEntriesStatus()

fun MetadataBatchCandidateSelection.selectedStatus(): String =
    updatedMatch.selectedCandidateStatus()

fun MetadataBatchMatch.selectedCandidateStatus(): String =
    "Selected batch candidate for $query: ${result?.displayTitle().orEmpty()}."

fun metadataBatchSearchingStatus(
    queryCount: Int,
    sourceName: String = "metadata",
): String =
    "Searching $sourceName for $queryCount indexed title(s)..."

fun noMetadataBatchEntriesStatus(sourceName: String = "metadata"): String =
    "No indexed entries are available for $sourceName batch matching."

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

    suspend fun previewFor(
        entries: List<MediaIndexEntry>,
        queryLimit: Int,
        searchCandidates: suspend (String) -> List<ScraperResult>,
    ): MetadataBatchPreview {
        val mediaEntries = entries.filterNot { it.isDirectory }
        val queries = previewQueriesFor(mediaEntries, queryLimit)
        if (queries.isEmpty()) {
            return MetadataBatchPreview(
                matches = emptyList(),
                plan = null,
                selectedMatch = null,
            )
        }

        val matches = queries.map { query ->
            val candidates = searchCandidates(query)
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
        val mediaEntries = entries.filterNot { it.isDirectory }
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

    fun planFor(
        entries: List<MediaIndexEntry>,
        matches: List<MetadataBatchMatch>,
    ): MetadataBatchPlan {
        val readyUpdates = mutableListOf<MetadataBatchUpdate>()
        val reviewMatches = mutableListOf<MetadataBatchMatch>()
        val conflicts = mutableListOf<MetadataBatchConflict>()
        matches.forEach { match ->
            val result = match.result
            if (result == null || result.confidence < READY_CONFIDENCE) {
                reviewMatches += match
                return@forEach
            }
            val matchingEntries = entries.filter { it.metadataQuery() == match.query }
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
            append(result?.let(::displayCandidate) ?: "No match")
            append(" [$status]")
            if (match.candidates.size > 1) append(" candidates=${match.candidates.size}")
            appendLine()
        }
    }

    fun displayPlanSummary(plan: MetadataBatchPlan): String =
        "${plan.readyUpdates.size} ready, ${plan.reviewMatches.size} review, ${plan.conflicts.size} conflicts"

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
        queriesFor(entries.filterNot { it.isDirectory })
            .take(queryLimit.coerceAtLeast(0))
}
