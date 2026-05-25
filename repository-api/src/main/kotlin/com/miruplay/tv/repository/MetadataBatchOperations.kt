package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.metadataBatchAppliedTvStatus
import com.miruplay.tv.model.metadataBatchRestoredTvStatus
import com.miruplay.tv.model.metadataNoBatchPreviewTvStatus
import com.miruplay.tv.model.metadataNoBatchUndoTvStatus
import com.miruplay.tv.model.metadataReviewAcceptedTvStatus

data class MetadataBatchWriteResult(
    val updatedEntries: List<MediaIndexEntry>,
    val rollbackEntries: List<MediaIndexEntry>,
)

data class MetadataBatchUndoResult(
    val rollbackEntries: List<MediaIndexEntry>,
    val restoredCount: Int,
)

suspend fun MediaIndexRepository.applyMetadataBatchPlan(
    sourceId: Long,
    plan: MetadataBatchPlan,
): MetadataBatchWriteResult =
    applyMetadataBatchUpdates(sourceId, plan.readyUpdates)

suspend fun MediaIndexRepository.applyMetadataBatchUpdates(
    sourceId: Long,
    updates: List<MetadataBatchUpdate>,
): MetadataBatchWriteResult {
    if (updates.isEmpty()) {
        return MetadataBatchWriteResult(emptyList(), emptyList())
    }

    val updatedEntries = mutableListOf<MediaIndexEntry>()
    val rollbackEntries = mutableListOf<MediaIndexEntry>()

    updates.forEach { update ->
        val updated = update.updated.copy(sourceId = sourceId)
        val original = update.original.copy(sourceId = sourceId)
        when (upsertEntry(sourceId, updated)) {
            is Result.Success -> {
                updatedEntries += updated
                rollbackEntries += original
            }
            is Result.Error -> Unit
        }
    }

    val normalizedRollback = rollbackEntries.distinctBy { it.path }
    saveLastBatchUndo(sourceId, normalizedRollback)
    return MetadataBatchWriteResult(
        updatedEntries = updatedEntries,
        rollbackEntries = normalizedRollback,
    )
}

suspend fun MediaIndexRepository.restoreMetadataBatchUndo(
    sourceId: Long,
    preferredRollbackEntries: List<MediaIndexEntry> = emptyList(),
): Result<MetadataBatchUndoResult> {
    val rollbackEntries =
        if (preferredRollbackEntries.isNotEmpty()) {
            preferredRollbackEntries
        } else {
            when (val saved = getLastBatchUndo(sourceId)) {
                is Result.Success -> saved.data
                is Result.Error -> return Result.failure(saved.error)
            }
        }

    if (rollbackEntries.isEmpty()) {
        return Result.success(
            MetadataBatchUndoResult(
                rollbackEntries = emptyList(),
                restoredCount = 0,
            )
        )
    }

    var restoredCount = 0
    rollbackEntries.forEach { entry ->
        when (upsertEntry(sourceId, entry.copy(sourceId = sourceId))) {
            is Result.Success -> restoredCount += 1
            is Result.Error -> Unit
        }
    }
    clearLastBatchUndo(sourceId)
    return Result.success(
        MetadataBatchUndoResult(
            rollbackEntries = rollbackEntries.map { it.copy(sourceId = sourceId) },
            restoredCount = restoredCount,
        )
    )
}

fun MetadataBatchWriteResult.appliedStatus(conflictCount: Int): String =
    metadataBatchAppliedTvStatus(updatedEntries.size, conflictCount)

fun MetadataBatchWriteResult.reviewAcceptedStatus(): String =
    metadataReviewAcceptedTvStatus(updatedEntries.size)

fun MetadataBatchUndoResult.restoredStatus(): String =
    metadataBatchRestoredTvStatus(restoredCount)

fun noMetadataBatchPreviewStatus(): String =
    metadataNoBatchPreviewTvStatus()

fun noMetadataBatchUndoStatus(): String =
    metadataNoBatchUndoTvStatus()
