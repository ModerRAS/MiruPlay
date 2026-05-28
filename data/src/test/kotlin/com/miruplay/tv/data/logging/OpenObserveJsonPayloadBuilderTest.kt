package com.miruplay.tv.data.logging

import com.miruplay.tv.core.common.logging.MiruLogLevel
import com.miruplay.tv.core.common.logging.MiruLogRecord
import com.miruplay.tv.repository.OpenObserveLogConventions
import com.miruplay.tv.repository.OpenObservePayloadContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenObserveJsonPayloadBuilderTest {
    @Test
    fun `exception fields are included in json ingestion payload`() {
        val payload = OpenObserveLogConventions.buildJsonPayload(
            records = listOf(
                MiruLogRecord(
                    id = "record-1",
                    timestampMs = 1234L,
                    level = MiruLogLevel.ERROR,
                    tag = "Crash",
                    message = "Unhandled exception crashed the app",
                    throwableClass = "java.lang.IllegalStateException",
                    throwableMessage = "boom",
                    stackTrace = "java.lang.IllegalStateException: boom\n\tat test",
                    attributes = mapOf("thread.name" to "main")
                )
            ),
            context = OpenObservePayloadContext(
                serviceName = "miruplay-android-tv",
                deploymentEnvironment = "android-tv",
            ),
        )

        val item = payload.first().jsonObject
        assertEquals("error", item.getValue("level").jsonPrimitive.content)
        assertEquals("java.lang.IllegalStateException", item.getValue("exception_type").jsonPrimitive.content)
        assertEquals("boom", item.getValue("exception_message").jsonPrimitive.content)
        assertTrue(item.getValue("exception_stacktrace").jsonPrimitive.content.contains("IllegalStateException"))
        assertEquals("main", item.getValue("thread_name").jsonPrimitive.content)
    }
}
