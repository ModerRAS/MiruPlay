package com.miruplay.tv.repository

import com.miruplay.tv.core.common.logging.MiruLogLevel
import com.miruplay.tv.core.common.logging.MiruLogRecord
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenObserveLogConventionsTest {
    @Test
    fun `parse curl command extracts OpenObserve endpoint and basic auth`() {
        val parsed = OpenObserveLogConventions.parseCurlCommand(
            "curl -u user@example.com:password -k https://openobserve.example.com/api/org/default/_json -d \"[{\\\"level\\\":\\\"info\\\",\\\"job\\\":\\\"test\\\",\\\"log\\\":\\\"test message for openobserve\\\"}]\""
        )

        requireNotNull(parsed)
        assertEquals("https://openobserve.example.com/api/org/default/_json", parsed.endpoint)
        assertEquals("user@example.com:password", parsed.token)
    }

    @Test
    fun `parse curl command accepts long user option`() {
        val parsed = OpenObserveLogConventions.parseCurlCommand(
            "curl --user='user@example.com:secret' 'https://openobserve.example.com/api/org/default/_json' --data-raw '{}'"
        )

        requireNotNull(parsed)
        assertEquals("https://openobserve.example.com/api/org/default/_json", parsed.endpoint)
        assertEquals("user@example.com:secret", parsed.token)
    }

    @Test
    fun `parse curl command ignores plain endpoints`() {
        assertEquals(null, OpenObserveLogConventions.parseCurlCommand("https://openobserve.example.com/api/default"))
    }

    @Test
    fun `normalize endpoint maps root and collector urls to json stream`() {
        assertEquals(
            "https://openobserve.example.com/api/default/miruplay/_json",
            OpenObserveLogConventions.normalizeEndpoint("https://openobserve.example.com", "miruplay"),
        )
        assertEquals(
            "https://openobserve.example.com/api/acme/default/_json",
            OpenObserveLogConventions.normalizeEndpoint("https://openobserve.example.com/api/acme/v1/logs", "default"),
        )
    }

    @Test
    fun `normalize endpoint preserves existing json path and default stream`() {
        assertEquals(
            "https://openobserve.example.com/api/acme/default/_json",
            OpenObserveLogConventions.normalizeEndpoint(
                "https://openobserve.example.com/api/acme/default/_json",
                "",
            ),
        )
        assertEquals(
            "http://192.168.1.10:5080/api/default/miruplay/_json",
            OpenObserveLogConventions.normalizeEndpoint("192.168.1.10:5080", " "),
        )
    }

    @Test
    fun `build json payload includes service context and normalized attributes`() {
        val payload = OpenObserveLogConventions.buildJsonPayload(
            records = listOf(
                MiruLogRecord(
                    id = "record-1",
                    timestampMs = 1234L,
                    level = MiruLogLevel.ERROR,
                    tag = "Crash",
                    message = "Unhandled exception",
                    attributes = mapOf("thread.name" to "main"),
                ),
            ),
            context = OpenObservePayloadContext(
                serviceName = "miruplay-windows",
                deploymentEnvironment = "windows",
            ),
        )

        val item = payload.first().jsonObject
        assertEquals("error", item.getValue("level").jsonPrimitive.content)
        assertEquals("miruplay-windows", item.getValue("service_name").jsonPrimitive.content)
        assertEquals("windows", item.getValue("deployment_environment").jsonPrimitive.content)
        assertEquals("main", item.getValue("thread_name").jsonPrimitive.content)
        assertTrue(item.containsKey("record_id"))
    }
}
