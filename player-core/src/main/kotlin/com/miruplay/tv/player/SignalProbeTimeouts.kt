package com.miruplay.tv.player

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

internal const val INITIAL_CONTAINER_SIGNAL_PROBE_TIMEOUT_MS = 150L
internal const val INITIAL_RUNTIME_SIGNAL_PROBE_TIMEOUT_MS = 220L

internal data class TimedSignalProbeResult<T>(
    val value: T?,
    val completedWithinBudget: Boolean,
)

internal data class InitialSignalProbeResult<C, R>(
    val containerValue: C?,
    val runtimeValue: R?,
    val containerCompletedWithinBudget: Boolean,
    val runtimeCompletedWithinBudget: Boolean,
) {
    val requiresBackgroundCompletion: Boolean
        get() = !containerCompletedWithinBudget || !runtimeCompletedWithinBudget
}

internal suspend fun <T> runTimedSignalProbe(
    timeoutMs: Long,
    probe: suspend () -> T?,
): TimedSignalProbeResult<T> {
    var completedWithinBudget = true
    val value = withTimeoutOrNull(timeoutMs) {
        probe()
    } ?: run {
        completedWithinBudget = false
        null
    }
    return TimedSignalProbeResult(
        value = value,
        completedWithinBudget = completedWithinBudget,
    )
}

internal suspend fun <C, R> runInitialSignalProbe(
    containerTimeoutMs: Long,
    runtimeTimeoutMs: Long,
    containerProbe: suspend () -> C?,
    runtimeProbe: suspend () -> R?,
): InitialSignalProbeResult<C, R> = coroutineScope {
    val containerDeferred = async {
        runTimedSignalProbe(
            timeoutMs = containerTimeoutMs,
            probe = containerProbe,
        )
    }
    val runtimeDeferred = async {
        runTimedSignalProbe(
            timeoutMs = runtimeTimeoutMs,
            probe = runtimeProbe,
        )
    }
    val containerResult = containerDeferred.await()
    val runtimeResult = runtimeDeferred.await()
    InitialSignalProbeResult(
        containerValue = containerResult.value,
        runtimeValue = runtimeResult.value,
        containerCompletedWithinBudget = containerResult.completedWithinBudget,
        runtimeCompletedWithinBudget = runtimeResult.completedWithinBudget,
    )
}
