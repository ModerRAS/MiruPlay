package com.miruplay.tv.ui.library

import android.util.Log
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.model.libraryScanFailedMessage
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.repository.shouldAutoScan
import com.miruplay.tv.scanner.ScanCoordinator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class LibraryScanState {
    data object Idle : LibraryScanState()
    data class Scanning(
        val currentPath: String = "",
        val filesScanned: Int = 0,
        val newEpisodes: Int = 0,
        val contentVersion: Int = 0
    ) : LibraryScanState()
    data class Finished(val results: List<ScanResult>) : LibraryScanState()
    data class Failed(val message: String) : LibraryScanState()
    data object Cancelled : LibraryScanState()
}

@Singleton
class LibraryScanTask @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val scanCoordinator: ScanCoordinator,
    private val scanPreferences: ScanPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<LibraryScanState>(LibraryScanState.Idle)
    val state: StateFlow<LibraryScanState> = _state.asStateFlow()

    private var scanJob: kotlinx.coroutines.Job? = null

    fun startManualScan() {
        startScan(force = true)
    }

    fun startAutoScanIfDue() {
        startScan(force = false)
    }

    fun cancel() {
        scanJob?.cancel()
    }

    private fun startScan(force: Boolean) {
        if (scanJob?.isActive == true) return

        scanJob = scope.launch {
            if (!force && !scanPreferences.shouldAutoScan()) {
                MiruLog.d(
                    tag = TAG,
                    message = "Library scan skipped",
                    attributes = mapOf(
                        "scan_task_phase" to "skipped",
                        "reason" to "auto_scan_not_due",
                    )
                )
                return@launch
            }
            val sources = mediaRepository.getSources().getOrNull().orEmpty()
            if (sources.isEmpty()) {
                MiruLog.i(
                    tag = TAG,
                    message = "Library scan skipped",
                    attributes = mapOf(
                        "scan_task_phase" to "skipped",
                        "reason" to "no_sources",
                        "force" to force.toString(),
                    )
                )
                _state.value = LibraryScanState.Idle
                return@launch
            }
            MiruLog.i(
                tag = TAG,
                message = "Library scan started",
                attributes = mapOf(
                    "scan_task_phase" to "started",
                    "force" to force.toString(),
                    "source_count" to sources.size.toString(),
                )
            )

            scanCoordinator.setProgressCallback(ScanCoordinator.ScanProgressCallback { path, files, newEps ->
                _state.update { current ->
                    val scanning = current as? LibraryScanState.Scanning ?: LibraryScanState.Scanning()
                    scanning.copy(
                        currentPath = path,
                        filesScanned = scanning.filesScanned + files,
                        newEpisodes = scanning.newEpisodes + newEps
                    )
                }
            })

            _state.value = LibraryScanState.Scanning()
            try {
                val results = mutableListOf<ScanResult>()
                for (source in sources) {
                    MiruLog.i(
                        tag = TAG,
                        message = "Library source scan started",
                        attributes = mapOf(
                            "scan_task_phase" to "source_started",
                            "source_id" to source.id.toString(),
                            "source_name" to source.name,
                            "source_type" to source.type.name,
                        )
                    )
                    when (val result = scanCoordinator.scanSource(source.id)) {
                        is Result.Success -> {
                            results.add(result.data)
                            MiruLog.i(
                                tag = TAG,
                                message = "Library source scan completed",
                                attributes = mapOf(
                                    "scan_task_phase" to "source_completed",
                                    "source_id" to source.id.toString(),
                                    "source_name" to source.name,
                                    "source_type" to source.type.name,
                                    "episodes_found" to result.data.episodesFound.toString(),
                                    "new_episodes" to result.data.newEpisodes.toString(),
                                )
                            )
                            val completedFiles = results.sumOf { it.episodesFound }
                            val completedNewEpisodes = results.sumOf { it.newEpisodes }
                            _state.update { current ->
                                val scanning = current as? LibraryScanState.Scanning ?: LibraryScanState.Scanning()
                                scanning.copy(
                                    currentPath = result.data.animeName,
                                    filesScanned = maxOf(scanning.filesScanned, completedFiles),
                                    newEpisodes = maxOf(scanning.newEpisodes, completedNewEpisodes),
                                    contentVersion = scanning.contentVersion + 1
                                )
                            }
                        }
                        is Result.Error -> {
                            Log.w("LibraryScanTask", "Skipping failed source ${source.id}: ${result.error}")
                            MiruLog.w(
                                tag = TAG,
                                message = "Library source scan failed",
                                attributes = mapOf(
                                    "scan_task_phase" to "source_failed",
                                    "source_id" to source.id.toString(),
                                    "source_name" to source.name,
                                    "source_type" to source.type.name,
                                    "error_type" to result.error::class.java.simpleName,
                                    "error_message" to result.error.toString(),
                                )
                            )
                        }
                    }
                }
                scanPreferences.setLastScanAt(System.currentTimeMillis())
                _state.value = LibraryScanState.Finished(results)
                MiruLog.i(
                    tag = TAG,
                    message = "Library scan completed",
                    attributes = mapOf(
                        "scan_task_phase" to "completed",
                        "source_count" to sources.size.toString(),
                        "completed_source_count" to results.size.toString(),
                        "episodes_found" to results.sumOf { it.episodesFound }.toString(),
                        "new_episodes" to results.sumOf { it.newEpisodes }.toString(),
                    )
                )
            } catch (e: CancellationException) {
                _state.value = LibraryScanState.Cancelled
                MiruLog.i(
                    tag = TAG,
                    message = "Library scan cancelled",
                    attributes = mapOf("scan_task_phase" to "cancelled")
                )
                throw e
            } catch (e: Exception) {
                Log.w("LibraryScanTask", "Library scan failed", e)
                MiruLog.e(
                    tag = TAG,
                    message = "Library scan failed",
                    throwable = e,
                    attributes = mapOf("scan_task_phase" to "failed")
                )
                _state.value = LibraryScanState.Failed(libraryScanFailedMessage(e::class.simpleName))
            } finally {
                scanCoordinator.setProgressCallback(null)
            }
        }
    }

    private companion object {
        private const val TAG = "LibraryScanTask"
    }
}
