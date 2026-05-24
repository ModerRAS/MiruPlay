package com.miruplay.tv.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebControlConfigTest {
    @Test
    fun `web control access urls include port and encoded token`() {
        assertEquals(
            listOf(
                "http://192.168.1.20:9978/?token=token%20with%20space",
                "http://10.0.0.8:9978/?token=token%20with%20space",
            ),
            buildWebControlAccessUrls(
                accessToken = "token with space",
                localIps = listOf("192.168.1.20", "10.0.0.8"),
            ),
        )
    }

    @Test
    fun `web control access urls trim blank and duplicate addresses`() {
        assertEquals(
            listOf("http://127.0.0.1:9988/?token=abc%2F123"),
            buildWebControlAccessUrls(
                accessToken = "abc/123",
                port = 9988,
                localIps = listOf("", " 127.0.0.1 ", "127.0.0.1"),
            ),
        )
        assertTrue(buildWebControlAccessUrls(accessToken = " ", localIps = listOf("127.0.0.1")).isEmpty())
    }
}
