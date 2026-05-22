package com.miruplay.tv.sync.rss

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.CloudDriveRssRunSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.time.Instant

data class CloudDriveRssSchedulerSmokeOptions(
    val durationMillis: Long = 2_000L,
    val checkIntervalMillis: Long = 250L,
    val runAfterChecks: Int = 1,
    val reportPath: String? = null,
)

data class CloudDriveRssSchedulerSmokeReport(
    val startedAtUtc: String,
    val finishedAtUtc: String,
    val elapsedMillis: Long,
    val checkIntervalMillis: Long,
    val requestedRunAfterChecks: Int,
    val startReturned: Boolean,
    val secondStartReturned: Boolean,
    val checksObserved: Int,
    val runCount: Int,
    val finalRunning: Boolean,
    val lastCheckedAt: Long,
    val lastRunCompletedAt: Long,
    val lastSummary: CloudDriveRssRunSummary?,
    val lastError: String?,
)

fun parseCloudDriveRssSchedulerSmokeOptions(args: Array<String>): CloudDriveRssSchedulerSmokeOptions {
    val values = mutableMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val key = args[index]
        if (!key.startsWith("--")) {
            throw IllegalArgumentException("Unexpected argument: $key")
        }
        val value = args.getOrNull(index + 1)
            ?: throw IllegalArgumentException("Missing value for $key")
        values[key.removePrefix("--")] = value
        index += 2
    }
    return CloudDriveRssSchedulerSmokeOptions(
        durationMillis = values["duration-ms"]
            ?.toLongOrNull()
            ?.coerceIn(250L, 60_000L)
            ?: 2_000L,
        checkIntervalMillis = values["check-interval-ms"]
            ?.toLongOrNull()
            ?.coerceIn(50L, 10_000L)
            ?: 250L,
        runAfterChecks = values["run-after-checks"]
            ?.toIntOrNull()
            ?.coerceIn(1, 100)
            ?: 1,
        reportPath = values["report-path"]?.takeIf { it.isNotBlank() },
    )
}

suspend fun runCloudDriveRssSchedulerSmoke(
    options: CloudDriveRssSchedulerSmokeOptions,
): Result<CloudDriveRssSchedulerSmokeReport> {
    if (options.durationMillis < options.checkIntervalMillis * options.runAfterChecks) {
        return Result.failure(
            AppError.SyncError.WriteFailed(
                "Cloud/RSS scheduler smoke",
                "duration must be long enough to observe the requested due check",
            )
        )
    }

    val startedAtUtc = Instant.now().toString()
    val startedAtMillis = System.currentTimeMillis()
    val scope = CoroutineScope(SupervisorJob())
    val dueRunner = CountingDueRunner(runAfterChecks = options.runAfterChecks)
    val scheduler = DesktopCloudDriveRssScheduler(
        dueRunner = dueRunner,
        scope = scope,
        checkIntervalMillis = options.checkIntervalMillis,
    )

    return try {
        val startReturned = scheduler.start()
        val secondStartReturned = scheduler.start()
        delay(options.durationMillis)
        scheduler.stop()
        delay(50L)
        val finalState = scheduler.state.value
        val elapsedMillis = System.currentTimeMillis() - startedAtMillis
        val report = CloudDriveRssSchedulerSmokeReport(
            startedAtUtc = startedAtUtc,
            finishedAtUtc = Instant.now().toString(),
            elapsedMillis = elapsedMillis,
            checkIntervalMillis = options.checkIntervalMillis,
            requestedRunAfterChecks = options.runAfterChecks,
            startReturned = startReturned,
            secondStartReturned = secondStartReturned,
            checksObserved = dueRunner.checkCount,
            runCount = dueRunner.runCount,
            finalRunning = finalState.running,
            lastCheckedAt = finalState.lastCheckedAt,
            lastRunCompletedAt = finalState.lastRunCompletedAt,
            lastSummary = finalState.lastSummary,
            lastError = finalState.lastError,
        )
        if (!report.startReturned ||
            report.secondStartReturned ||
            report.checksObserved < options.runAfterChecks ||
            report.runCount < 1 ||
            report.finalRunning ||
            report.lastRunCompletedAt <= 0L ||
            report.lastSummary == null ||
            report.lastError != null
        ) {
            return Result.failure(
                AppError.SyncError.WriteFailed(
                    "Cloud/RSS scheduler smoke",
                    "scheduler did not produce the expected elapsed-time state",
                )
            )
        }
        Result.success(report)
    } finally {
        scope.cancel()
    }
}

fun main(args: Array<String>) {
    runBlocking {
        val options = parseCloudDriveRssSchedulerSmokeOptions(args)
        when (val result = runCloudDriveRssSchedulerSmoke(options)) {
            is Result.Success -> {
                printCloudDriveRssSchedulerSmokeReport(result.data)
                options.reportPath?.let { reportPath ->
                    writeCloudDriveRssSchedulerSmokeReport(reportPath, result.data)
                }
            }
            is Result.Error -> error("CloudDrive RSS scheduler smoke failed: ${result.error.toUserMessage()}")
        }
    }
}

private class CountingDueRunner(
    private val runAfterChecks: Int,
) : CloudDriveRssDueRunner {
    var checkCount: Int = 0
        private set
    var runCount: Int = 0
        private set

    override suspend fun runIfDue(): Result<CloudDriveRssRunSummary?> {
        checkCount += 1
        if (checkCount < runAfterChecks || runCount > 0) {
            return Result.success(null)
        }
        runCount += 1
        return Result.success(
            CloudDriveRssRunSummary(
                submitted = 1,
                skipped = checkCount - 1,
                failed = 0,
                organized = 1,
            )
        )
    }
}

private fun printCloudDriveRssSchedulerSmokeReport(report: CloudDriveRssSchedulerSmokeReport) {
    println("CloudDrive RSS scheduler elapsed-time smoke passed.")
    println("Elapsed: ${report.elapsedMillis} ms; interval: ${report.checkIntervalMillis} ms.")
    println("Checks observed: ${report.checksObserved}; due runs: ${report.runCount}.")
    println(
        "Final state: running=${report.finalRunning}, lastCheckedAt=${report.lastCheckedAt}, " +
            "lastRunCompletedAt=${report.lastRunCompletedAt}, lastError=${report.lastError ?: "(none)"}"
    )
    report.lastSummary?.let { summary ->
        println(
            "Last summary: submitted=${summary.submitted}, skipped=${summary.skipped}, " +
                "failed=${summary.failed}, organized=${summary.organized}"
        )
    }
}

private fun writeCloudDriveRssSchedulerSmokeReport(
    reportPath: String,
    report: CloudDriveRssSchedulerSmokeReport,
) {
    val outputFile = File(reportPath).absoluteFile
    outputFile.parentFile?.mkdirs()
    outputFile.writeText(buildCloudDriveRssSchedulerSmokeReportJson(report), Charsets.UTF_8)
    println("Wrote CloudDrive RSS scheduler smoke report: ${outputFile.absolutePath}")
}

internal fun buildCloudDriveRssSchedulerSmokeReportJson(report: CloudDriveRssSchedulerSmokeReport): String {
    val payload = buildJsonObject {
        put("startedAtUtc", report.startedAtUtc)
        put("finishedAtUtc", report.finishedAtUtc)
        put("elapsedMillis", report.elapsedMillis)
        put("checkIntervalMillis", report.checkIntervalMillis)
        put("requestedRunAfterChecks", report.requestedRunAfterChecks)
        put("startReturned", report.startReturned)
        put("secondStartReturned", report.secondStartReturned)
        put("checksObserved", report.checksObserved)
        put("runCount", report.runCount)
        put("finalRunning", report.finalRunning)
        put("lastCheckedAt", report.lastCheckedAt)
        put("lastRunCompletedAt", report.lastRunCompletedAt)
        report.lastError?.let { put("lastError", it) }
        report.lastSummary?.let { summary ->
            putJsonObject("lastSummary") {
                put("submitted", summary.submitted)
                put("skipped", summary.skipped)
                put("failed", summary.failed)
                put("organized", summary.organized)
            }
        }
    }
    return payload.toString()
}
