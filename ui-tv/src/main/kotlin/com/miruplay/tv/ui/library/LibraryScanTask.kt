package com.miruplay.tv.ui.library

import android.content.Context
import android.util.Log
import com.miruplay.tv.background.BackgroundTaskForegroundController
import com.miruplay.tv.background.BackgroundTaskIds
import com.miruplay.tv.background.BackgroundTaskProgress
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.ScanResult
import com.miruplay.tv.model.libraryScanFailedMessage
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.repository.shouldAutoScan
import com.miruplay.tv.scanner.LibraryScanState
import com.miruplay.tv.scanner.LibraryScanStatus
import com.miruplay.tv.scanner.ScanCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Singleton
class LibraryScanTask @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaSourceRepository,
    private val scanCoordinator: ScanCoordinator,
    private val scanPreferences: ScanPreferencesRepository,
    private val backgroundTasks: BackgroundTaskForegroundController,
    private val scanStatus: LibraryScanStatus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val state: StateFlow<LibraryScanState> = scanStatus.state

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
        if (scanStatus.isScanning()) return

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
                scanStatus.idle()
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
            var foregroundStarted = false
            backgroundTasks.start(
                taskId = BackgroundTaskIds.LIBRARY_SCAN,
                title = "媒体库扫描",
                text = "正在准备扫描 ${sources.size} 个媒体源",
                progress = BackgroundTaskProgress.indeterminate(),
            )
            foregroundStarted = true

            scanCoordinator.setProgressCallback(ScanCoordinator.ScanProgressCallback { path, files, newEps ->
                val scanning = scanStatus.reportProgress(path, files, newEps)
                backgroundTasks.update(
                    taskId = BackgroundTaskIds.LIBRARY_SCAN,
                    title = "媒体库扫描",
                    text = scanProgressText(scanning),
                    progress = BackgroundTaskProgress.indeterminate(),
                )
            })

            scanStatus.start()
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
                    when (
                        val result = scanCoordinator.scanSource(
                            source.id,
                            posterCacheDirectory = posterCacheDirectory()
                        )
                    ) {
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
                            val scanning = scanStatus.completeSource(
                                result = result.data,
                                completedFiles = completedFiles,
                                completedNewEpisodes = completedNewEpisodes,
                            )
                            backgroundTasks.update(
                                taskId = BackgroundTaskIds.LIBRARY_SCAN,
                                title = "媒体库扫描",
                                text = scanProgressText(scanning),
                                progress = BackgroundTaskProgress.indeterminate(),
                            )
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
                scanStatus.finish(results)
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
                scanStatus.cancel()
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
                scanStatus.fail(libraryScanFailedMessage(e::class.simpleName))
            } finally {
                scanCoordinator.setProgressCallback(null)
                if (foregroundStarted) {
                    backgroundTasks.finish(BackgroundTaskIds.LIBRARY_SCAN)
                }
            }
        }
    }

    private fun posterCacheDirectory(): File =
        File(context.cacheDir, "miruplay_image_cache")

    private fun scanProgressText(scanState: LibraryScanState.Scanning): String {
        val currentPath = scanState.currentPath.ifBlank { "媒体源" }
        return "正在处理：$currentPath，已发现 ${scanState.filesScanned} 个条目，新剧集 ${scanState.newEpisodes} 个"
    }

    private companion object {
        private const val TAG = "LibraryScanTask"
    }
}
