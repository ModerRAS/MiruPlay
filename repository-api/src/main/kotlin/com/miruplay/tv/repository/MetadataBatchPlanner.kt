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

object MetadataBatchPlanner {
    private const val READY_CONFIDENCE = 0.85f

    fun queriesFor(entries: List<MediaIndexEntry>): List<String> =
        entries
            .mapNotNull(MediaIndexEntry::metadataQuery)
            .distinct()

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
                    updated = entry.copy(
                        animeName = result.displayTitle(),
                        metadataSource = result.source.name,
                        metadataId = result.animeId,
                        metadataTitle = result.displayTitle(),
                    ),
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
}
