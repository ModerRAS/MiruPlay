package com.miruplay.tv.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.connectionPassword

sealed class MediaSourceAddActionResult {
    data class Saved(val source: MediaSourceInfo) : MediaSourceAddActionResult()
    data class Failed(
        val error: AppError,
        val phase: MediaSourceAddFailurePhase,
    ) : MediaSourceAddActionResult()
}

enum class MediaSourceAddFailurePhase {
    AddSource,
    UpdateConnectionState,
}

class MediaSourceActionCoordinator(
    private val repository: MediaSourceRepository,
) {
    suspend fun addSource(
        source: MediaSourceInfo,
        testConnection: suspend (MediaSourceInfo) -> Result<Boolean>,
    ): MediaSourceAddActionResult =
        when (val added = repository.addSource(source)) {
            is Result.Success -> {
                val persisted = source.copy(id = added.data)
                val connected = testConnection(persisted).getOrNull() ?: false
                val saved = persisted.copy(isConnected = connected)
                when (val updated = repository.updateSource(saved)) {
                    is Result.Success -> MediaSourceAddActionResult.Saved(saved)
                    is Result.Error -> MediaSourceAddActionResult.Failed(
                        error = updated.error,
                        phase = MediaSourceAddFailurePhase.UpdateConnectionState,
                    )
                }
            }
            is Result.Error -> MediaSourceAddActionResult.Failed(
                error = added.error,
                phase = MediaSourceAddFailurePhase.AddSource,
            )
        }

    suspend fun updateSource(source: MediaSourceInfo): Result<MediaSourceInfo> =
        when (val existing = repository.getSourceById(source.id)) {
            is Result.Success -> {
                val merged = source.withPreservedSourceState(existing.data)
                when (val updated = repository.updateSource(merged)) {
                    is Result.Success -> Result.success(merged)
                    is Result.Error -> updated
                }
            }
            is Result.Error -> existing
        }

    suspend fun removeSource(sourceId: Long): Result<Unit> =
        repository.removeSource(sourceId)
}

fun MediaSourceInfo.withPreservedSourceState(existing: MediaSourceInfo): MediaSourceInfo =
    copy(
        connectionInfo = connectionInfo.withPreservedPassword(existing),
        isConnected = existing.isConnected,
        lastScanned = existing.lastScanned,
    )

private fun Map<String, String>.withPreservedPassword(existing: MediaSourceInfo): Map<String, String> =
    if (
        MediaSourceInfoConventions.CONNECTION_PASSWORD !in this &&
        existing.connectionPassword().isNotBlank()
    ) {
        this + (MediaSourceInfoConventions.CONNECTION_PASSWORD to existing.connectionPassword())
    } else {
        this
    }
