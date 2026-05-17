package com.miruplay.tv.desktop

import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopRuntimeDefaultsTest {
    @Test
    fun `default mpv paths point at bundled runtime layout`() {
        assertTrue(DesktopRuntimeDefaults.mpvPath().replace('\\', '/').endsWith("runtime/mpv/mpv.exe"))
        assertTrue(DesktopRuntimeDefaults.configDirectory().replace('\\', '/').endsWith("runtime/mpv/portable_config"))
    }
}
