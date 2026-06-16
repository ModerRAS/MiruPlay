package com.miruplay.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupProbeFormatterTest {
    @Test
    fun `record line includes startup checkpoint`() {
        val line = StartupProbeFormatter.recordLine(
            event = "checkpoint",
            checkpoint = "provider_on_create",
            timestampMs = 1_700_000_000_000L,
            processId = 1234,
            packageName = "com.miruplay.tv",
            versionName = "0.3.462",
            versionCode = 462,
            attributes = mapOf("phase" to "pre_application"),
        )

        assertTrue(line.endsWith("\n"))
        assertTrue(line.contains("\"event\":\"checkpoint\""))
        assertTrue(line.contains("\"checkpoint\":\"provider_on_create\""))
        assertTrue(line.contains("\"packageName\":\"com.miruplay.tv\""))
        assertTrue(line.contains("\"phase\":\"pre_application\""))
    }

    @Test
    fun `record line redacts sensitive values`() {
        val line = StartupProbeFormatter.recordLine(
            event = "fatal",
            checkpoint = "provider_failed",
            timestampMs = 1_700_000_000_000L,
            processId = 1234,
            packageName = "com.miruplay.tv",
            versionName = "0.3.462",
            versionCode = 462,
            attributes = mapOf(
                "url" to "https://user:pass@example.test/path?access_token=abc123",
                "message" to "password=secret token=another",
            ),
            throwableClass = IllegalStateException::class.java.name,
            throwableMessage = "api_key=super-secret",
            stackTrace = "Authorization: Bearer token-value",
        )

        assertFalse(line.contains("abc123"))
        assertFalse(line.contains("secret"))
        assertFalse(line.contains("user:pass@"))
        assertFalse(line.contains("token-value"))
        assertTrue(line.contains("<redacted>"))
    }
}
