package com.miruplay.tv.player.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

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
}
