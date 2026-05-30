package com.miruplay.tv.core.common.logging

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiruLogTest {
    @Test
    fun `log records redact sensitive values`() {
        var captured: MiruLogRecord? = null
        MiruLog.setSink { record -> captured = record }

        MiruLog.w(
            tag = "Test",
            message = "Failed https://user:pass@example.test/file?token=secret-token",
            throwable = IllegalStateException("Authorization: Bearer abcdef"),
            attributes = mapOf("url" to "https://example.test/path?api_key=secret-key")
        )

        val record = requireNotNull(captured)
        assertFalse(record.message.contains("secret-token"))
        assertFalse(record.message.contains("user:pass"))
        assertFalse(record.throwableMessage.orEmpty().contains("abcdef"))
        assertFalse(record.attributes.getValue("url").contains("secret-key"))
        assertTrue(record.message.contains("<redacted>"))
        MiruLog.setSink(null)
    }

    @Test
    fun `sink recording can be suppressed for internal logs`() {
        var captured: MiruLogRecord? = null
        MiruLog.setSink { record -> captured = record }

        MiruLog.withoutSinkRecording {
            MiruLog.i("Test", "Should not be captured")
        }

        assertEquals(null, captured)
        MiruLog.setSink(null)
    }

    @Test
    fun `performance log records duration and result count`() = runBlocking {
        val records = mutableListOf<MiruLogRecord>()
        MiruLog.setSink { record -> records += record }

        try {
            val result = PerformanceLog.measureSuspendResult(
                tag = "PerfTest",
                operation = "test.operation",
            ) {
                Result.success(listOf("a", "b"))
            }

            assertTrue(result is Result.Success)
            val record = records.single()
            assertEquals("PerfTest", record.tag)
            assertEquals("Performance metric", record.message)
            assertEquals("performance", record.attributes.getValue("event"))
            assertEquals("test.operation", record.attributes.getValue("operation"))
            assertEquals("success", record.attributes.getValue("status"))
            assertEquals("2", record.attributes.getValue("result_count"))
            assertTrue(record.attributes.getValue("duration_ms").toDouble() >= 0.0)
        } finally {
            MiruLog.setSink(null)
        }
    }

    @Test
    fun `performance log records result errors`() {
        val records = mutableListOf<MiruLogRecord>()
        MiruLog.setSink { record -> records += record }

        try {
            val result = PerformanceLog.measureResult(
                tag = "PerfTest",
                operation = "test.error",
            ) {
                Result.failure(AppError.ScrapingError.NoMatchFound("missing"))
            }

            assertTrue(result is Result.Error)
            val record = records.single()
            assertEquals("error", record.attributes.getValue("status"))
            assertEquals("NoMatchFound", record.attributes.getValue("error_type"))
            assertEquals("test.error", record.attributes.getValue("operation"))
        } finally {
            MiruLog.setSink(null)
        }
    }
}
