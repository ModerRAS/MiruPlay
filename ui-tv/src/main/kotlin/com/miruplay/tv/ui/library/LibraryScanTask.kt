package com.miruplay.tv.ui.library

import android.util.Log
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.repository.MediaSourceRepository
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
        val newEpisodes: Int = 0
    ) : LibraryScanState()
    data class Finished(val results: List<ScanResult>) : LibraryScanState()
    data class Failed(val message: String) : LibraryScanState()
    data object Cancelled : LibraryScanState()
}

@Singleton
class LibraryScanTask @Inject constructor(
    private val mediaRepository: MediaSourceRepository,
    private val scanCoordinator: ScanCoordinator,
    private val scanPreferences: ScanPreferencesManager
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
        if (!force && !scanPreferences.shouldAutoScan()) return

        scanJob = scope.launch {
            val sources = mediaRepository.getSources().getOrNull().orEmpty()
            if (sources.isEmpty()) {
                MiruLog.i("LibraryScanTask", "Scan skipped because no media sources are configured")
                _state.value = LibraryScanState.Idle
                return@launch
            }

            MiruLog.i(
                "LibraryScanTask",
                if (force) "Manual library scan started" else "Automatic library scan started",
                mapOf("source_count" to sources.size.toString())
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
                when (val result = scanCoordinator.scanAllSources()) {
                    is Result.Success -> {
                        scanPreferences.lastScanAt = System.currentTimeMillis()
                        MiruLog.i(
                            "LibraryScanTask",
                            "Library scan finished",
                            mapOf("result_count" to result.data.size.toString())
                        )
                        _state.value = LibraryScanState.Finished(result.data)
                    }
                    is Result.Error -> {
                        MiruLog.w(
                            "LibraryScanTask",
                            "Library scan failed with result error",
                            attributes = mapOf(
                                "error_type" to result.error::class.simpleName.orEmpty(),
                                "error_message" to result.error.toUserMessage()
                            )
                        )
                        _state.value = LibraryScanState.Failed("扫描失败：${result.error::class.simpleName}")
                    }
                }
            } catch (e: CancellationException) {
                MiruLog.i("LibraryScanTask", "Library scan cancelled")
                _state.value = LibraryScanState.Cancelled
                throw e
            } catch (e: Exception) {
                Log.w("LibraryScanTask", "Library scan failed", e)
                MiruLog.e("LibraryScanTask", "Library scan crashed", e)
                _state.value = LibraryScanState.Failed("扫描失败：${e::class.simpleName}")
            } finally {
                scanCoordinator.setProgressCallback(null)
            }
        }
    }
}
