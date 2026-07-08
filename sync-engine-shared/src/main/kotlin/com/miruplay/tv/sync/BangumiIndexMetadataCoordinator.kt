package com.miruplay.tv.sync

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.repository.MediaIndexEntry
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MetadataBatchMatch
import com.miruplay.tv.repository.MetadataBatchPlan
import com.miruplay.tv.repository.MetadataBatchPlanner
import com.miruplay.tv.repository.MetadataBatchUndoResult
import com.miruplay.tv.repository.MetadataBatchWriteResult
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.appliedStatus
import com.miruplay.tv.repository.applyMetadataBatchPlan
import com.miruplay.tv.repository.clearExternalMetadata
import com.miruplay.tv.repository.mediaFilesOnly
import com.miruplay.tv.repository.metadataAppliedStatus
import com.miruplay.tv.repository.metadataApplyEntryRequiredStatus
import com.miruplay.tv.repository.metadataBatchResultRequiredStatus
import com.miruplay.tv.repository.metadataBatchSearchingStatus
import com.miruplay.tv.repository.metadataClearEntryRequiredStatus
import com.miruplay.tv.repository.metadataClearedStatus
import com.miruplay.tv.repository.metadataQuery
import com.miruplay.tv.repository.metadataQueryRequiredStatus
import com.miruplay.tv.repository.metadataReviewNoMatchStatus
import com.miruplay.tv.repository.metadataSearchResultStatus
import com.miruplay.tv.repository.metadataSearchSelectionRequiredStatus
import com.miruplay.tv.repository.metadataSearchStartedStatus
import com.miruplay.tv.repository.metadataSourceRequiredStatus
import com.miruplay.tv.repository.noMetadataBatchEntriesStatus
import com.miruplay.tv.repository.noMetadataBatchPreviewStatus
import com.miruplay.tv.repository.noMetadataBatchUndoStatus
import com.miruplay.tv.repository.replaceMatch
import com.miruplay.tv.repository.restoreMetadataBatchUndo
import com.miruplay.tv.repository.restoredStatus
import com.miruplay.tv.repository.reviewAcceptedStatus
import com.miruplay.tv.repository.reviewConflictStatus
import com.miruplay.tv.repository.selectedCandidateStatus
import com.miruplay.tv.repository.selectedStatus
import com.miruplay.tv.repository.selectedMetadataStatus
import com.miruplay.tv.repository.summaryStatus
import com.miruplay.tv.repository.withExternalMetadata
import com.miruplay.tv.repository.withSelectedCandidate
import com.miruplay.tv.scraper.MetadataScraper
import com.miruplay.tv.scraper.searchPreferredResults

const val BANGUMI_METADATA_SOURCE_NAME: String = "Bangumi"
const val BANGUMI_BATCH_QUERY_LIMIT: Int = 20

data class BangumiMetadataSearchActionResult(
    val query: String,
    val results: List<ScraperResult>,
    val selectedResult: ScraperResult?,
    val status: String,
)

data class BangumiMetadataBatchPreviewActionResult(
    val matches: List<MetadataBatchMatch>,
    val plan: MetadataBatchPlan?,
    val selectedMatch: MetadataBatchMatch?,
    val status: String,
)

data class BangumiMetadataBatchApplyActionResult(
    val plan: MetadataBatchPlan?,
    val write: MetadataBatchWriteResult,
    val status: String,
)

data class BangumiMetadataBatchUndoActionResult(
    val restore: MetadataBatchUndoResult,
    val status: String,
)

data class BangumiMetadataBatchCandidateActionResult(
    val updatedMatch: MetadataBatchMatch,
    val updatedMatches: List<MetadataBatchMatch>,
    val plan: MetadataBatchPlan?,
    val selectedResult: ScraperResult,
    val status: String,
)

data class BangumiMetadataEntryActionResult(
    val updatedEntry: MediaIndexEntry?,
    val status: String,
)

class BangumiIndexMetadataCoordinator(
    private val indexRepository: MediaIndexRepository,
    private val metadataRepository: MetadataRepository,
    private val bangumiScraper: MetadataScraper,
    private val queryLimit: Int = BANGUMI_BATCH_QUERY_LIMIT,
    private val sourceName: String = BANGUMI_METADATA_SOURCE_NAME,
) {
    private val metadataRefreshCore = BangumiMetadataRefreshCore(
        metadataRepository = metadataRepository,
        bangumiScraper = bangumiScraper,
    )

    suspend fun search(
        query: String,
        selectedEntry: MediaIndexEntry?,
        onSearchStarted: suspend (String) -> Unit = {},
    ): Result<BangumiMetadataSearchActionResult> {
        val resolvedQuery = query.trim().ifBlank {
            selectedEntry?.metadataQuery().orEmpty()
        }
        if (resolvedQuery.isBlank()) {
            return Result.success(
                BangumiMetadataSearchActionResult(
                    query = resolvedQuery,
                    results = emptyList(),
                    selectedResult = null,
                    status = metadataQueryRequiredStatus(sourceName),
                )
            )
        }

        onSearchStarted(metadataSearchStartedStatus(resolvedQuery, sourceName))
        return when (
            val result = bangumiScraper.searchPreferredResults(
                query = resolvedQuery,
                candidates = listOfNotNull(
                    selectedEntry?.metadataTitle?.takeIf { it.isNotBlank() },
                    selectedEntry?.metadataQuery(),
                    selectedEntry?.metadataId?.takeIf { it.isNotBlank() },
                ).distinct(),
            )
        ) {
            is Result.Success -> Result.success(
                BangumiMetadataSearchActionResult(
                    query = resolvedQuery,
                    results = result.data,
                    selectedResult = result.data.firstOrNull(),
                    status = metadataSearchResultStatus(resolvedQuery, result.data.size, sourceName),
                )
            )
            is Result.Error -> result
        }
    }

    suspend fun previewBatch(
        sourceId: Long?,
        onSearchStarted: suspend (String) -> Unit = {},
    ): Result<BangumiMetadataBatchPreviewActionResult> {
        if (sourceId == null) {
            return Result.success(emptyBatchPreviewResult(metadataSourceRequiredStatus()))
        }

        return when (val entriesResult = indexRepository.queryIndex(sourceId, "")) {
            is Result.Error -> entriesResult
            is Result.Success -> {
                val entries = entriesResult.data
                val queryCount = MetadataBatchPlanner.previewQueryCount(
                    entries = entries,
                    queryLimit = queryLimit,
                )
                if (queryCount == 0) {
                    return Result.success(emptyBatchPreviewResult(noMetadataBatchEntriesStatus(sourceName)))
                }

                onSearchStarted(metadataBatchSearchingStatus(queryCount, sourceName))
                val preview = MetadataBatchPlanner.previewFor(
                    entries = entries,
                    queryLimit = queryLimit,
                    searchCandidates = { query, candidates ->
                        bangumiScraper.searchPreferredResults(
                            query = query,
                            candidates = candidates,
                        ).getOrNull().orEmpty()
                    },
                )
                Result.success(
                    BangumiMetadataBatchPreviewActionResult(
                        matches = preview.matches,
                        plan = preview.plan,
                        selectedMatch = preview.selectedMatch,
                        status = preview.summaryStatus(),
                    )
                )
            }
        }
    }

    suspend fun applyBatch(
        sourceId: Long?,
        matches: List<MetadataBatchMatch>,
    ): Result<BangumiMetadataBatchApplyActionResult> {
        if (sourceId == null) {
            return Result.success(emptyBatchApplyResult(status = metadataSourceRequiredStatus()))
        }
        if (matches.isEmpty()) {
            return Result.success(emptyBatchApplyResult(status = noMetadataBatchPreviewStatus()))
        }

        return when (val entriesResult = indexRepository.queryIndex(sourceId, "")) {
            is Result.Error -> entriesResult
            is Result.Success -> {
                val entries = entriesResult.data.mediaFilesOnly()
                val plan = MetadataBatchPlanner.planFor(entries, matches)
                if (plan.readyUpdates.isEmpty()) {
                    return Result.success(
                        emptyBatchApplyResult(
                            plan = plan,
                            status = MetadataBatchPlanner.displayPlanSummary(plan),
                        )
                    )
                }

                val write = indexRepository.applyMetadataBatchPlan(sourceId, plan)
                Result.success(
                    BangumiMetadataBatchApplyActionResult(
                        plan = plan,
                        write = write,
                        status = write.appliedStatus(plan.conflicts.size),
                    )
                )
            }
        }
    }

    suspend fun undoBatch(
        sourceId: Long?,
        rollbackEntries: List<MediaIndexEntry>,
    ): Result<BangumiMetadataBatchUndoActionResult> {
        if (sourceId == null) {
            return Result.success(emptyBatchUndoResult(status = metadataSourceRequiredStatus()))
        }

        return when (val restore = indexRepository.restoreMetadataBatchUndo(sourceId, rollbackEntries)) {
            is Result.Error -> restore
            is Result.Success -> {
                val data = restore.data
                Result.success(
                    BangumiMetadataBatchUndoActionResult(
                        restore = data,
                        status = if (data.rollbackEntries.isEmpty()) {
                            noMetadataBatchUndoStatus()
                        } else {
                            data.restoredStatus()
                        },
                    )
                )
            }
        }
    }

    suspend fun selectBatchCandidate(
        sourceId: Long?,
        matches: List<MetadataBatchMatch>,
        match: MetadataBatchMatch,
        candidate: ScraperResult,
    ): Result<BangumiMetadataBatchCandidateActionResult> {
        if (sourceId == null) {
            val updatedMatch = match.withSelectedCandidate(candidate)
            return Result.success(
                BangumiMetadataBatchCandidateActionResult(
                    updatedMatch = updatedMatch,
                    updatedMatches = matches.replaceMatch(updatedMatch),
                    plan = null,
                    selectedResult = candidate,
                    status = updatedMatch.selectedCandidateStatus(),
                )
            )
        }

        return when (val entriesResult = indexRepository.queryIndex(sourceId, "")) {
            is Result.Error -> entriesResult
            is Result.Success -> {
                val selection = MetadataBatchPlanner.selectCandidate(
                    entries = entriesResult.data,
                    matches = matches,
                    match = match,
                    candidate = candidate,
                )
                Result.success(
                    BangumiMetadataBatchCandidateActionResult(
                        updatedMatch = selection.updatedMatch,
                        updatedMatches = selection.updatedMatches,
                        plan = selection.plan,
                        selectedResult = candidate,
                        status = selection.selectedStatus(),
                    )
                )
            }
        }
    }

    suspend fun acceptBatchReview(
        sourceId: Long?,
        match: MetadataBatchMatch?,
    ): Result<BangumiMetadataBatchApplyActionResult> {
        val result = match?.result
        if (sourceId == null) {
            return Result.success(emptyBatchApplyResult(status = metadataSourceRequiredStatus()))
        }
        if (match == null || result == null) {
            return Result.success(emptyBatchApplyResult(status = metadataBatchResultRequiredStatus(sourceName)))
        }

        return when (val entriesResult = indexRepository.queryIndex(sourceId, "")) {
            is Result.Error -> entriesResult
            is Result.Success -> {
                val entries = entriesResult.data.mediaFilesOnly()
                val reviewed = match.copy(result = result.copy(confidence = 1f))
                val plan = MetadataBatchPlanner.planFor(entries, listOf(reviewed))
                when {
                    plan.conflicts.isNotEmpty() -> Result.success(
                        emptyBatchApplyResult(
                            plan = plan,
                            status = plan.reviewConflictStatus(),
                        )
                    )
                    plan.readyUpdates.isEmpty() -> Result.success(
                        emptyBatchApplyResult(
                            plan = plan,
                            status = metadataReviewNoMatchStatus(),
                        )
                    )
                    else -> {
                        val write = indexRepository.applyMetadataBatchPlan(sourceId, plan)
                        Result.success(
                            BangumiMetadataBatchApplyActionResult(
                                plan = plan,
                                write = write,
                                status = write.reviewAcceptedStatus(),
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun applyEntryMetadata(
        sourceId: Long?,
        entry: MediaIndexEntry?,
        match: ScraperResult?,
        relatedEntries: List<MediaIndexEntry>,
    ): Result<BangumiMetadataEntryActionResult> {
        if (sourceId == null) {
            return Result.success(BangumiMetadataEntryActionResult(null, metadataSourceRequiredStatus()))
        }
        if (entry == null || entry.isDirectory) {
            return Result.success(BangumiMetadataEntryActionResult(null, metadataApplyEntryRequiredStatus(sourceName)))
        }
        if (match == null) {
            return Result.success(BangumiMetadataEntryActionResult(null, metadataSearchSelectionRequiredStatus(sourceName)))
        }

        val relatedFiles = relatedEntries
            .filterNot { it.isDirectory }
            .ifEmpty { listOf(entry) }
            .distinctBy { it.path }
        val updatedEntries = relatedFiles.map { related -> related.withExternalMetadata(match, sourceId = sourceId) }
        val updated = updatedEntries.firstOrNull { it.path == entry.path }
            ?: entry.withExternalMetadata(match, sourceId = sourceId)

        for (updatedEntry in updatedEntries) {
            when (val result = indexRepository.upsertEntry(sourceId, updatedEntry)) {
                is Result.Error -> return result
                is Result.Success -> Unit
            }
        }

        metadataRefreshCore.cacheMatchedIndexMetadata(
            entry = updated,
            relatedEntries = updatedEntries,
            match = match,
        )
        return Result.success(
            BangumiMetadataEntryActionResult(
                updatedEntry = updated,
                status = updated.metadataAppliedStatus(sourceName),
            )
        )
    }

    suspend fun clearEntryMetadata(
        sourceId: Long?,
        entry: MediaIndexEntry?,
    ): Result<BangumiMetadataEntryActionResult> {
        if (sourceId == null) {
            return Result.success(BangumiMetadataEntryActionResult(null, metadataSourceRequiredStatus()))
        }
        if (entry == null || entry.isDirectory) {
            return Result.success(BangumiMetadataEntryActionResult(null, metadataClearEntryRequiredStatus()))
        }

        val updated = entry.clearExternalMetadata(sourceId = sourceId)
        return when (val result = indexRepository.upsertEntry(sourceId, updated)) {
            is Result.Error -> result
            is Result.Success -> {
                metadataRepository.invalidateCache(entry.bangumiMetadataCacheId())
                Result.success(
                    BangumiMetadataEntryActionResult(
                        updatedEntry = updated,
                        status = updated.metadataClearedStatus(),
                    )
                )
            }
        }
    }

    private fun emptyBatchPreviewResult(status: String): BangumiMetadataBatchPreviewActionResult =
        BangumiMetadataBatchPreviewActionResult(
            matches = emptyList(),
            plan = null,
            selectedMatch = null,
            status = status,
        )

    private fun emptyBatchApplyResult(
        plan: MetadataBatchPlan? = null,
        status: String,
    ): BangumiMetadataBatchApplyActionResult =
        BangumiMetadataBatchApplyActionResult(
            plan = plan,
            write = MetadataBatchWriteResult(
                updatedEntries = emptyList(),
                rollbackEntries = emptyList(),
            ),
            status = status,
        )

    private fun emptyBatchUndoResult(status: String): BangumiMetadataBatchUndoActionResult =
        BangumiMetadataBatchUndoActionResult(
            restore = MetadataBatchUndoResult(
                rollbackEntries = emptyList(),
                restoredCount = 0,
            ),
            status = status,
        )
}
