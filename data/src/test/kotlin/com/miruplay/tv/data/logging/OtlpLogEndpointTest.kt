package com.miruplay.tv.data.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class OtlpLogEndpointTest {
    @Test
    fun `root server address uses default organization logs endpoint`() {
        assertEquals(
            "https://openobserve.example.com/api/default/v1/logs",
            OtlpLogEndpoint.normalize("https://openobserve.example.com")
        )
    }

    @Test
    fun `organization API endpoint appends logs path`() {
        assertEquals(
            "https://openobserve.example.com/api/miruplay/v1/logs",
            OtlpLogEndpoint.normalize("https://openobserve.example.com/api/miruplay")
        )
    }

    @Test
    fun `full logs endpoint is preserved`() {
        assertEquals(
            "https://openobserve.example.com/api/miruplay/v1/logs",
            OtlpLogEndpoint.normalize("https://openobserve.example.com/api/miruplay/v1/logs")
        )
    }

    @Test
    fun `collector base endpoint appends logs signal`() {
        assertEquals(
            "https://openobserve.example.com/api/miruplay/v1/logs",
            OtlpLogEndpoint.normalize("https://openobserve.example.com/api/miruplay/v1")
        )
    }

    @Test
    fun `scheme defaults to http for local appliance addresses`() {
        assertEquals(
            "http://192.168.1.10:5080/api/default/v1/logs",
            OtlpLogEndpoint.normalize("192.168.1.10:5080")
        )
    }
}
