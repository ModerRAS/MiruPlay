package com.miruplay.tv.webcontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class WebControlServerInfoTest {
    @Test
    fun `server info maps app device port and start time`() {
        val dto = buildWebControlServerInfo(
            appName = "MiruPlay Test",
            deviceName = "Android TV",
            port = 9988,
            localIps = listOf("192.168.1.20"),
            startedAt = 123L,
        )

        assertEquals("MiruPlay Test", dto.appName)
        assertEquals("Android TV", dto.deviceName)
        assertEquals(9988, dto.port)
        assertEquals(listOf("192.168.1.20"), dto.localIps)
        assertEquals(123L, dto.startedAt)
    }

    @Test
    fun `server info trims blank and duplicate local ips`() {
        val dto = buildWebControlServerInfo(
            deviceName = "Android TV",
            port = 9978,
            localIps = listOf(" ", " 127.0.0.1 ", "127.0.0.1", "10.0.0.8"),
            startedAt = 456L,
        )

        assertEquals(listOf("127.0.0.1", "10.0.0.8"), dto.localIps)
    }
}
