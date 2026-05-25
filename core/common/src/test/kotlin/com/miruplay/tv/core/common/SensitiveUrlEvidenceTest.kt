package com.miruplay.tv.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveUrlEvidenceTest {
    @Test
    fun `redactSensitiveUrl redacts credentials and keeps host port for http`() {
        assertEquals(
            "https://tracker.example:8443/...",
            redactSensitiveUrl("https://user:pass@tracker.example:8443/rss?passkey=secret"),
        )
    }

    @Test
    fun `redactSensitiveUrl supports scheme overrides`() {
        val overrides = mapOf(
            "file" to "file:///<redacted>",
            "magnet" to "magnet:?<redacted>",
        )

        assertEquals("file:///<redacted>", redactSensitiveUrl("file:///D:/feeds/private.xml", overrides))
        assertEquals("magnet:?<redacted>", redactSensitiveUrl("magnet:?xt=urn:btih:abc", overrides))
        assertEquals("ftp:<redacted>", redactSensitiveUrl("ftp://example.test/private.torrent", overrides))
    }

    @Test
    fun `sensitiveUrlEvidence returns redacted url normalized scheme host and sha`() {
        val evidence = sensitiveUrlEvidence("http://127.0.0.1:19798/cloud?token=secret")

        assertEquals("http://127.0.0.1:19798/...", evidence.redacted)
        assertEquals("http", evidence.scheme)
        assertEquals("127.0.0.1", evidence.host)
        assertEquals(64, evidence.sha256.length)
        assertTrue(evidence.sha256.matches(Regex("^[a-f0-9]{64}$")))
    }
}
