package com.miruplay.tv.scanner

import com.miruplay.tv.model.ScanResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class LibraryScanState {
    data object Idle : LibraryScanState()
    data class Scanning(
        val currentPath: String = "",
        val filesScanned: Int = 0,
        val newEpisodes: Int = 0,
        val contentVersion: Int = 0,
        val canCancel: Boolean = true,
    ) : LibraryScanState()
    data class Finished(
        val results: List<ScanResult>,
        val sourceFailures: List<String> = emptyList(),
    ) : LibraryScanState()
    data class Failed(val message: String) : LibraryScanState()
    data object Cancelled : LibraryScanState()
}

@Singleton
class LibraryScanStatus @Inject constructor() {
    private val _state = MutableStateFlow<LibraryScanState>(LibraryScanState.Idle)
    val state: StateFlow<LibraryScanState> = _state.asStateFlow()

    fun isScanning(): Boolean =
        _state.value is LibraryScanState.Scanning

    fun idle() {
        _state.value = LibraryScanState.Idle
    }

    fun start(
        currentPath: String = "",
        canCancel: Boolean = true,
    ) {
        _state.value = LibraryScanState.Scanning(
            currentPath = currentPath,
            canCancel = canCancel,
        )
    }

    @Synchronized
    fun tryStart(
        currentPath: String = "",
        canCancel: Boolean = true,
    ): Boolean {
        if (_state.value is LibraryScanState.Scanning) return false
        _state.value = LibraryScanState.Scanning(
            currentPath = currentPath,
            canCancel = canCancel,
        )
        return true
    }

    fun reportProgress(
        currentPath: String,
        filesScanned: Int,
        newEpisodes: Int,
    ): LibraryScanState.Scanning {
        var nextState = LibraryScanState.Scanning()
        _state.update { current ->
            val scanning = current as? LibraryScanState.Scanning ?: LibraryScanState.Scanning()
            scanning.copy(
                currentPath = currentPath,
                filesScanned = scanning.filesScanned + filesScanned,
                newEpisodes = scanning.newEpisodes + newEpisodes,
            ).also { nextState = it }
        }
        return nextState
    }

    fun completeSource(
        result: ScanResult,
        completedFiles: Int = result.episodesFound,
        completedNewEpisodes: Int = result.newEpisodes,
    ): LibraryScanState.Scanning {
        var nextState = LibraryScanState.Scanning()
        _state.update { current ->
            val scanning = current as? LibraryScanState.Scanning ?: LibraryScanState.Scanning()
            scanning.copy(
                currentPath = result.animeName,
                filesScanned = maxOf(scanning.filesScanned, completedFiles),
                newEpisodes = maxOf(scanning.newEpisodes, completedNewEpisodes),
                contentVersion = scanning.contentVersion + 1,
            ).also { nextState = it }
        }
        return nextState
    }

    fun finish(
        results: List<ScanResult>,
        sourceFailures: List<String> = emptyList(),
    ) {
        _state.value = LibraryScanState.Finished(
            results = results,
            sourceFailures = sourceFailures,
        )
    }

    fun fail(message: String) {
        _state.value = LibraryScanState.Failed(message)
    }

    fun cancel() {
        _state.value = LibraryScanState.Cancelled
    }
}
