package com.miruplay.tv.sync.rss

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveRssRunSummary
import com.miruplay.tv.model.CloudDriveRssSchedulerUiState
import com.miruplay.tv.model.tvStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class DesktopCloudDriveRssSchedulerState(
    val running: Boolean = false,
    val lastCheckedAt: Long = 0L,
    val lastRunCompletedAt: Long = 0L,
    val lastSummary: CloudDriveRssRunSummary? = null,
    val lastError: String? = null,
)

fun DesktopCloudDriveRssSchedulerState.toUiState(): CloudDriveRssSchedulerUiState =
    CloudDriveRssSchedulerUiState(
        running = running,
        lastCheckedAt = lastCheckedAt,
        lastSummary = lastSummary,
        lastError = lastError,
    )

fun DesktopCloudDriveRssSchedulerState.schedulerStatus(): String =
    toUiState().tvStatus()

class DesktopCloudDriveRssScheduler(
    private val dueRunner: CloudDriveRssDueRunner,
    private val scope: CoroutineScope,
    private val checkIntervalMillis: Long = DEFAULT_CHECK_INTERVAL_MILLIS,
) {
    private val _state = MutableStateFlow(DesktopCloudDriveRssSchedulerState())
    val state: StateFlow<DesktopCloudDriveRssSchedulerState> = _state

    private var job: Job? = null

    constructor(
        engine: DesktopCloudDriveRssAutomationEngine,
        scope: CoroutineScope,
        checkIntervalMillis: Long = DEFAULT_CHECK_INTERVAL_MILLIS,
    ) : this(
        dueRunner = engine,
        scope = scope,
        checkIntervalMillis = checkIntervalMillis,
    )

    fun start(): Boolean {
        if (job?.isActive == true) return false
        _state.update { it.copy(running = true, lastError = null) }
        val launchedJob = scope.launchCloudDriveRssSchedulerLoop(
            dueRunner = dueRunner,
            checkIntervalMillis = checkIntervalMillis,
        ) { checkedAt, result ->
            _state.update { current ->
                when (result) {
                    is Result.Success -> current.copy(
                        running = true,
                        lastCheckedAt = checkedAt,
                        lastRunCompletedAt = if (result.data != null) checkedAt else current.lastRunCompletedAt,
                        lastSummary = result.data ?: current.lastSummary,
                        lastError = null,
                    )
                    is Result.Error -> current.copy(
                        running = true,
                        lastCheckedAt = checkedAt,
                        lastError = result.error.toUserMessage(),
                    )
                }
            }
        }
        job = launchedJob
        launchedJob.invokeOnCompletion {
            if (job === launchedJob) {
                _state.update { state -> state.copy(running = false) }
                job = null
            }
        }
        return true
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false) }
    }

    companion object {
        private const val DEFAULT_CHECK_INTERVAL_MILLIS = 60_000L
    }
}
