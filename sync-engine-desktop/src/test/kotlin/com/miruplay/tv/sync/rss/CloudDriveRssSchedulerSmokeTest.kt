package com.miruplay.tv.sync.rss

import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDriveRssSchedulerSmokeTest {
    @Test
    fun `parse scheduler smoke options clamps elapsed timing values`() {
        val options = parseCloudDriveRssSchedulerSmokeOptions(
            arrayOf(
                "--duration-ms",
                "1000",
                "--check-interval-ms",
                "100",
                "--run-after-checks",
                "3",
                "--report-path",
                "build/cloud-rss-smoke/scheduler-report.json",
            )
        )

        assertEquals(1_000L, options.durationMillis)
        assertEquals(100L, options.checkIntervalMillis)
        assertEquals(3, options.runAfterChecks)
        assertEquals("build/cloud-rss-smoke/scheduler-report.json", options.reportPath)
    }

    @Test
    fun `scheduler smoke observes elapsed checks run and stopped state`() = runBlocking {
        val result = runCloudDriveRssSchedulerSmoke(
            CloudDriveRssSchedulerSmokeOptions(
                durationMillis = 350L,
                checkIntervalMillis = 50L,
                runAfterChecks = 2,
            )
        )

        assertTrue(result is Result.Success)
        val report = (result as Result.Success).data
        assertTrue(report.startReturned)
        assertFalse(report.secondStartReturned)
        assertTrue(report.elapsedMillis >= 350L)
        assertTrue(report.checksObserved >= 2)
        assertEquals(1, report.runCount)
        assertFalse(report.finalRunning)
        assertTrue(report.lastCheckedAt > 0L)
        assertTrue(report.lastRunCompletedAt > 0L)
        assertEquals(1, report.lastSummary?.submitted)
        assertEquals(1, report.lastSummary?.organized)
        assertEquals(null, report.lastError)
    }

    @Test
    fun `scheduler smoke rejects durations too short to observe due run`() = runBlocking {
        val result = runCloudDriveRssSchedulerSmoke(
            CloudDriveRssSchedulerSmokeOptions(
                durationMillis = 250L,
                checkIntervalMillis = 250L,
                runAfterChecks = 2,
            )
        )

        assertTrue(result is Result.Error)
    }

    @Test
    fun `scheduler smoke report json contains timing evidence without endpoint or token`() {
        val report = CloudDriveRssSchedulerSmokeReport(
            startedAtUtc = "2026-05-21T00:00:00Z",
            finishedAtUtc = "2026-05-21T00:00:02Z",
            elapsedMillis = 2_000L,
            checkIntervalMillis = 250L,
            requestedRunAfterChecks = 2,
            startReturned = true,
            secondStartReturned = false,
            checksObserved = 8,
            runCount = 1,
            finalRunning = false,
            lastCheckedAt = 123L,
            lastRunCompletedAt = 120L,
            lastSummary = CloudDriveRssRunSummary(
                submitted = 1,
                skipped = 1,
                failed = 0,
                organized = 1,
            ),
            lastError = null,
        )

        val json = buildCloudDriveRssSchedulerSmokeReportJson(report)

        assertFalse(json.contains("token"))
        assertFalse(json.contains("endpoint"))
        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals(2_000L, root.getValue("elapsedMillis").jsonPrimitive.content.toLong())
        assertEquals(8, root.getValue("checksObserved").jsonPrimitive.int)
        assertEquals(1, root.getValue("runCount").jsonPrimitive.int)
        assertFalse(root.getValue("finalRunning").jsonPrimitive.boolean)
        assertEquals(1, root.getValue("lastSummary").jsonObject.getValue("organized").jsonPrimitive.int)
    }
}
