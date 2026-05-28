package com.miruplay.tv.data.logging

import com.miruplay.tv.repository.OpenObserveLogConventions
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenObserveLogEndpointTest {
    @Test
    fun `root server address uses default organization json endpoint`() {
        assertEquals(
            "https://openobserve.example.com/api/default/miruplay/_json",
            OpenObserveLogConventions.normalizeEndpoint("https://openobserve.example.com", "miruplay")
        )
    }

    @Test
    fun `organization API endpoint appends stream json path`() {
        assertEquals(
            "https://openobserve.example.com/api/acme/default/_json",
            OpenObserveLogConventions.normalizeEndpoint("https://openobserve.example.com/api/acme", "default")
        )
    }

    @Test
    fun `organization stream endpoint appends json suffix without duplicating stream`() {
        assertEquals(
            "https://openobserve.example.com/api/acme/default/_json",
            OpenObserveLogConventions.normalizeEndpoint("https://openobserve.example.com/api/acme/default", "default")
        )
    }

    @Test
    fun `full json endpoint is preserved`() {
        assertEquals(
            "https://openobserve.example.com/api/acme/default/_json",
            OpenObserveLogConventions.normalizeEndpoint("https://openobserve.example.com/api/acme/default/_json", "other")
        )
    }

    @Test
    fun `collector v1 endpoint falls back to organization base before appending stream json path`() {
        assertEquals(
            "https://openobserve.example.com/api/acme/default/_json",
            OpenObserveLogConventions.normalizeEndpoint("https://openobserve.example.com/api/acme/v1", "default")
        )
    }

    @Test
    fun `otlp logs endpoint falls back to organization base before appending stream json path`() {
        assertEquals(
            "https://openobserve.example.com/api/acme/default/_json",
            OpenObserveLogConventions.normalizeEndpoint("https://openobserve.example.com/api/acme/v1/logs", "default")
        )
    }

    @Test
    fun `scheme defaults to http for local appliance addresses`() {
        assertEquals(
            "http://192.168.1.10:5080/api/default/miruplay/_json",
            OpenObserveLogConventions.normalizeEndpoint("192.168.1.10:5080", "")
        )
    }
}
