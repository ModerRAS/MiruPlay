package com.miruplay.tv

import org.junit.Assert.assertTrue
import org.junit.Test

class AppRestartTimingTest {
    @Test
    fun `restart launch delay stays after shutdown delay`() {
        assertTrue(APP_RESTART_LAUNCH_DELAY_MS > APP_SHUTDOWN_DELAY_MS)
    }
}
