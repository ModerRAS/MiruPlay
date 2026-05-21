package com.miruplay.tv.player.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class MpvRuntimeDiscoveryTest {
    @Test
    fun `find bundled runtime under application home`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        val workingDir = Files.createTempDirectory("miruplay-working")
        try {
            val runtime = tempDir.resolve("runtime").resolve("mpv")
            Files.createDirectories(runtime.resolve("portable_config").resolve("vs"))
            Files.createFile(runtime.resolve("mpv.exe"))

            val layout = MpvRuntimeDiscovery.findBundledRuntime(
                appHome = tempDir,
                workingDirectory = workingDir
            )

            assertEquals(runtime.toAbsolutePath().normalize(), layout?.rootDirectory)
            assertEquals(runtime.resolve("mpv.exe").toAbsolutePath().normalize(), layout?.executable)
        } finally {
            tempDir.toFile().deleteRecursively()
            workingDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `find bundled runtime returns null when mpv executable is absent`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        val workingDir = Files.createTempDirectory("miruplay-working")
        try {
            Files.createDirectories(tempDir.resolve("runtime").resolve("mpv").resolve("portable_config"))

            val layout = MpvRuntimeDiscovery.findBundledRuntime(
                appHome = tempDir,
                workingDirectory = workingDir
            )

            assertNull(layout)
        } finally {
            tempDir.toFile().deleteRecursively()
            workingDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `candidate roots include jpackage app image runtime beside launcher`() {
        val appHome = Paths.get("D:/MiruPlay")
        val workingDir = Paths.get("D:/Work")
        val roots = MpvRuntimeDiscovery.candidateRoots(
            appHome = appHome,
            workingDirectory = workingDir,
        )

        assertTrue(appHome.resolve("runtime").resolve("mpv").toAbsolutePath().normalize() in roots)
        assertTrue(appHome.resolve("app").resolve("runtime").resolve("mpv").toAbsolutePath().normalize() in roots)
    }

    @Test
    fun `default layout uses bundled runtime when available`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        val workingDir = Files.createTempDirectory("miruplay-working")
        try {
            val runtime = tempDir.resolve("runtime").resolve("mpv")
            Files.createDirectories(runtime.resolve("portable_config"))
            Files.createFile(runtime.resolve("mpv.exe"))

            val layout = MpvRuntimeDiscovery.defaultLayout(
                appHome = tempDir,
                workingDirectory = workingDir,
            )

            assertEquals(runtime.toAbsolutePath().normalize(), layout.rootDirectory)
            assertEquals(runtime.resolve("portable_config").toAbsolutePath().normalize(), layout.configDirectory)
        } finally {
            tempDir.toFile().deleteRecursively()
            workingDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `infer root from config directory prefers portable config parent`() {
        assertEquals(
            Paths.get("D:/MiruPlay/runtime/mpv"),
            MpvRuntimeDiscovery.inferRootFromInputs(
                mpvPath = "D:/Other/mpv.exe",
                configDir = "D:/MiruPlay/runtime/mpv/portable_config",
            ),
        )
    }

    @Test
    fun `infer root from mpv path when config directory is blank`() {
        assertEquals(
            Paths.get("D:/MiruPlay/runtime/mpv"),
            MpvRuntimeDiscovery.inferRootFromInputs(
                mpvPath = "D:/MiruPlay/runtime/mpv/mpv.exe",
                configDir = "",
            ),
        )
    }
}
