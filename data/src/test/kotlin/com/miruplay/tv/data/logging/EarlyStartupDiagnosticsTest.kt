package com.miruplay.tv.data.logging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EarlyStartupDiagnosticsTest {
    private lateinit var context: Context
    private lateinit var writer: CapturingStartupDiagnosticsWriter
    private lateinit var recorder: EarlyStartupDiagnosticsRecorder

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        writer = CapturingStartupDiagnosticsWriter()
        recorder = EarlyStartupDiagnosticsRecorder(
            context = context,
            writer = writer,
            clock = { 1_700_000_000_000L },
            processId = { 1234 },
            sessionId = { "session-1" },
        )
    }

    @Test
    fun `checkpoint writes public diagnostic line with runtime attributes`() {
        recorder.checkpoint(
            checkpoint = "application_on_create_before_super",
            attributes = mapOf("phase" to "pre_hilt"),
        )

        val json = Json.parseToJsonElement(writer.lines.single()).jsonObject
        assertTrue(writer.lines.single().endsWith("\n"))
        assertTrue(json["event"]?.jsonPrimitive?.content == "checkpoint")
        assertTrue(json["checkpoint"]?.jsonPrimitive?.content == "application_on_create_before_super")
        assertTrue(json["sessionId"]?.jsonPrimitive?.content == "session-1")
        assertTrue(json["processId"]?.jsonPrimitive?.content == "1234")
        assertTrue(json["attributes"]?.jsonObject?.get("phase")?.jsonPrimitive?.content == "pre_hilt")
        assertTrue(json["packageName"]?.jsonPrimitive?.content == context.packageName)
    }

    @Test
    fun `fatal record includes throwable details and redacts sensitive values`() {
        recorder.fatal(
            checkpoint = "application_super_on_create_failed",
            throwable = IllegalStateException("token=abc123 password=secret"),
            attributes = mapOf(
                "download_url" to "https://user:pass@example.test/app.apk?access_token=abc123",
            ),
        )

        val line = writer.lines.single()
        val json = Json.parseToJsonElement(line).jsonObject
        assertTrue(json["event"]?.jsonPrimitive?.content == "fatal")
        assertTrue(json["throwableClass"]?.jsonPrimitive?.content == IllegalStateException::class.java.name)
        assertTrue(json["stackTrace"]?.jsonPrimitive?.content.orEmpty().contains("IllegalStateException"))
        assertFalse(line.contains("abc123"))
        assertFalse(line.contains("secret"))
        assertFalse(line.contains("user:pass@"))
        assertTrue(line.contains("<redacted>"))
    }

    @Test
    fun `writer failures do not crash startup diagnostics`() {
        val failingRecorder = EarlyStartupDiagnosticsRecorder(
            context = context,
            writer = StartupDiagnosticsWriter { error("disk unavailable") },
            clock = { 1_700_000_000_000L },
            processId = { 1234 },
            sessionId = { "session-1" },
        )

        failingRecorder.checkpoint("safe_even_when_writer_fails")
    }

    private class CapturingStartupDiagnosticsWriter : StartupDiagnosticsWriter {
        val lines = mutableListOf<String>()

        override fun append(line: String) {
            lines += line
        }
    }
}
