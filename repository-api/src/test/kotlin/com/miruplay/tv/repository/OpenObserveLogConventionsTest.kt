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
