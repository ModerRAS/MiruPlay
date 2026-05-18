package com.miruplay.tv.player.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

class MpvRuntimeConfigTest {
    @Test
    fun `mpvRuntimeConfigFromInputs trims paths and enables RIFE only when requested`() {
        val withoutRife = mpvRuntimeConfigFromInputs(
            mpvPath = " D:/MiruPlay/runtime/mpv/mpv.exe ",
            configDir = " ",
            fullscreen = true,
            keepOpen = false,
            rifeEnabled = false,
            rifeBackend = RifeBackend.DIRECTML,
        )
        val withRife = mpvRuntimeConfigFromInputs(
            mpvPath = "D:/MiruPlay/runtime/mpv/mpv.exe",
            configDir = "D:/MiruPlay/runtime/mpv/portable_config",
            fullscreen = false,
            keepOpen = true,
            rifeEnabled = true,
            rifeBackend = RifeBackend.NVIDIA,
        )

        assertEquals(Paths.get("D:/MiruPlay/runtime/mpv/mpv.exe"), withoutRife.mpvExecutable)
        assertNull(withoutRife.configDirectory)
        assertTrue(withoutRife.startFullscreen)
        assertNull(withoutRife.rife)
        assertEquals(Paths.get("D:/MiruPlay/runtime/mpv/portable_config"), withRife.configDirectory)
        assertEquals(RifeBackend.NVIDIA, withRife.rife?.backend)
        assertTrue(withRife.keepOpen)
    }
}
