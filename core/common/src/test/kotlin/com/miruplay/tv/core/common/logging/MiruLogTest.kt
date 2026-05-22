package com.miruplay.tv.core.common.logging

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
}
