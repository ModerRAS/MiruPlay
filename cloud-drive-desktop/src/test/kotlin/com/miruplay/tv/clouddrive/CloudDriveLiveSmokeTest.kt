package com.miruplay.tv.clouddrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudDriveLiveSmokeTest {
    @Test
    fun `parseCloudDriveLiveSmokeOptions accepts endpoint token and path`() {
        val options = parseCloudDriveLiveSmokeOptions(
            arrayOf(
                "--endpoint",
                "http://127.0.0.1:19798",
                "--token",
                "secret",
                "--path",
                "/Downloads",
            )
        )

        assertEquals("http://127.0.0.1:19798", options.endpoint)
        assertEquals("secret", options.token)
        assertEquals("/Downloads", options.path)
    }

    @Test
    fun `parseCloudDriveLiveSmokeOptions defaults path to root`() {
        val options = parseCloudDriveLiveSmokeOptions(
            arrayOf(
                "--endpoint",
                "http://127.0.0.1:19798",
                "--token",
                "secret",
            )
        )

        assertEquals("/", options.path)
    }

    @Test
    fun `parseCloudDriveLiveSmokeOptions rejects missing token`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseCloudDriveLiveSmokeOptions(arrayOf("--endpoint", "http://127.0.0.1:19798"))
        }
    }
}
