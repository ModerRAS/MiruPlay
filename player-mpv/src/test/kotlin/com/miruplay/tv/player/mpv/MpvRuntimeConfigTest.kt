package com.miruplay.tv.player.mpv

import com.miruplay.tv.core.common.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
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
        assertEquals(DEFAULT_MPV_IPC_SERVER, withoutRife.ipcServer)
        assertNull(withoutRife.configDirectory)
        assertTrue(withoutRife.startFullscreen)
        assertNull(withoutRife.rife)
        assertEquals(Paths.get("D:/MiruPlay/runtime/mpv/portable_config"), withRife.configDirectory)
        assertEquals(DEFAULT_MPV_IPC_SERVER, withRife.ipcServer)
        assertEquals(RifeBackend.NVIDIA, withRife.rife?.backend)
        assertTrue(withRife.keepOpen)
    }

    @Test
    fun `validateLaunchRuntime requires mpv executable before launch`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        try {
            val config = MpvRuntimeConfig(mpvExecutable = tempDir.resolve("mpv.exe"))

            val result = config.validateLaunchRuntime()

            assertTrue(result is Result.Error)
            assertEquals("播放出错：mpv executable not found: ${tempDir.resolve("mpv.exe")}", (result as Result.Error).error.toUserMessage())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `validateLaunchRuntime requires config directory for bundled rife backend`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        try {
            val mpv = tempDir.resolve("mpv.exe")
            Files.createFile(mpv)
            val config = MpvRuntimeConfig(
                mpvExecutable = mpv,
                rife = RifeInterpolationConfig(backend = RifeBackend.DIRECTML),
            )

            val result = config.validateLaunchRuntime()

            assertTrue(result is Result.Error)
            assertEquals(
                "播放出错：configDirectory is required when using a bundled RIFE backend without scriptPath",
                (result as Result.Error).error.toUserMessage(),
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `validateLaunchRuntime checks selected rife backend script`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        try {
            val layout = MpvRuntimeDiscovery.layoutFor(tempDir)
            Files.createDirectories(layout.configDirectory.resolve("vs"))
            Files.createFile(layout.executable)
            Files.createFile(layout.rifeScript(RifeBackend.NVIDIA))
            val missingDirectMl = MpvRuntimeConfig(
                mpvExecutable = layout.executable,
                configDirectory = layout.configDirectory,
                rife = RifeInterpolationConfig(backend = RifeBackend.DIRECTML),
            )
            val availableNvidia = missingDirectMl.copy(
                rife = RifeInterpolationConfig(backend = RifeBackend.NVIDIA),
            )

            val missing = missingDirectMl.validateLaunchRuntime()
            val available = availableNvidia.validateLaunchRuntime()

            assertTrue(missing is Result.Error)
            assertEquals(
                "播放出错：RIFE script not found: ${layout.rifeScript(RifeBackend.DIRECTML)}",
                (missing as Result.Error).error.toUserMessage(),
            )
            assertTrue(available is Result.Success)
            assertEquals(setOf(RifeBackend.NVIDIA), (available as Result.Success).data?.availableRifeBackends)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `validateLaunchRuntime accepts explicit rife script without portable config`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        try {
            val mpv = tempDir.resolve("mpv.exe")
            val script = tempDir.resolve("custom-rife.vpy")
            Files.createFile(mpv)
            Files.createFile(script)
            val config = MpvRuntimeConfig(
                mpvExecutable = mpv,
                rife = RifeInterpolationConfig(scriptPath = script),
            )

            val result = config.validateLaunchRuntime()

            assertTrue(result is Result.Success)
            assertNull((result as Result.Success).data)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
