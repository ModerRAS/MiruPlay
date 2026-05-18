package com.miruplay.tv.player.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class MpvRuntimeVerifierTest {
    @Test
    fun `verify reports complete runtime with all RIFE scripts`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        try {
            val layout = MpvRuntimeDiscovery.layoutFor(tempDir)
            Files.createDirectories(layout.configDirectory.resolve("vs"))
            Files.createFile(layout.executable)
            RifeBackend.entries.forEach { backend ->
                Files.createFile(layout.rifeScript(backend))
            }

            val verification = MpvRuntimeVerifier.verify(layout)

            assertTrue(verification.isPlayable)
            assertTrue(verification.isComplete)
            assertEquals(RifeBackend.entries.toSet(), verification.availableRifeBackends)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `verify allows playable runtime without RIFE scripts`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        try {
            val layout = MpvRuntimeDiscovery.layoutFor(tempDir)
            Files.createDirectories(layout.configDirectory)
            Files.createFile(layout.executable)

            val verification = MpvRuntimeVerifier.verify(layout)

            assertTrue(verification.isPlayable)
            assertFalse(verification.hasRife)
            assertTrue("portable_config/vs/" in verification.missing)
            assertTrue("portable_config/vs/MEMC_RIFE_NV.vpy" in verification.missing)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `verify supports explicit executable and config paths`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        try {
            val executable = tempDir.resolve("custom").resolve("mpv.exe")
            val configDirectory = tempDir.resolve("config").resolve("portable_config")
            Files.createDirectories(executable.parent)
            Files.createDirectories(configDirectory.resolve("vs"))
            Files.createFile(executable)
            Files.createFile(configDirectory.resolve("vs").resolve(RifeBackend.NVIDIA.scriptName))

            val verification = MpvRuntimeVerifier.verify(
                MpvRuntimeLayout(
                    rootDirectory = tempDir,
                    executable = executable,
                    configDirectory = configDirectory,
                )
            )

            assertTrue(verification.isPlayable)
            assertTrue(RifeBackend.NVIDIA in verification.availableRifeBackends)
            assertFalse(RifeBackend.DIRECTML in verification.availableRifeBackends)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `verify reads prepared runtime manifest when present`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        try {
            val layout = MpvRuntimeDiscovery.layoutFor(tempDir)
            Files.createDirectories(layout.configDirectory.resolve("vs"))
            Files.createFile(layout.executable)
            Files.createFile(layout.rifeScript(RifeBackend.NVIDIA))
            Files.writeString(
                tempDir.resolve("runtime-manifest.json"),
                """
                {
                  "source": "D:/Downloads/mpv-lazy-20260510.exe",
                  "overlaySource": "D:/Downloads/mpv-lazy-20260510-vsNV.7z.001",
                  "runtimeRoot": "D:/WorkSpace/Android/MiruPlay/runtime/mpv",
                  "requiredRifeBackends": ["NVIDIA"],
                  "verifiedAt": "2026-05-15T00:00:00.0000000+08:00",
                  "files": ["mpv.exe", "portable_config/", "portable_config/vs/MEMC_RIFE_NV.vpy"]
                }
                """.trimIndent(),
            )

            val verification = MpvRuntimeVerifier.verify(layout)

            assertTrue(verification.isComplete)
            assertEquals("D:/Downloads/mpv-lazy-20260510.exe", verification.manifest?.source)
            assertEquals("D:/Downloads/mpv-lazy-20260510-vsNV.7z.001", verification.manifest?.overlaySource)
            assertEquals(listOf("NVIDIA"), verification.manifest?.requiredRifeBackends)
            assertTrue(verification.message().contains("Manifest: present"))
            assertTrue(verification.detailMessage().contains("Required RIFE: NVIDIA"))
            assertTrue(verification.detailMessage().contains("Overlay source: D:/Downloads/mpv-lazy-20260510-vsNV.7z.001"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `verify treats manifest required RIFE backends as the completeness gate`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        try {
            val layout = MpvRuntimeDiscovery.layoutFor(tempDir)
            Files.createDirectories(layout.configDirectory.resolve("vs"))
            Files.createFile(layout.executable)
            Files.createFile(layout.rifeScript(RifeBackend.NVIDIA))
            Files.createFile(layout.rifeScript(RifeBackend.DIRECTML))
            Files.writeString(
                tempDir.resolve("runtime-manifest.json"),
                """
                {
                  "requiredRifeBackends": ["NVIDIA", "DIRECTML"],
                  "files": [
                    "mpv.exe",
                    "portable_config/",
                    "portable_config/vs/MEMC_RIFE_NV.vpy",
                    "portable_config/vs/MEMC_RIFE_DML.vpy"
                  ]
                }
                """.trimIndent(),
            )

            val verification = MpvRuntimeVerifier.verify(layout)

            assertTrue(verification.isComplete)
            assertFalse("portable_config/vs/MEMC_RIFE_STD.vpy" in verification.missing)
            assertEquals(setOf(RifeBackend.NVIDIA, RifeBackend.DIRECTML), verification.availableRifeBackends)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `verify ignores malformed runtime manifest`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        try {
            val layout = MpvRuntimeDiscovery.layoutFor(tempDir)
            Files.createDirectories(layout.configDirectory)
            Files.createFile(layout.executable)
            Files.writeString(tempDir.resolve("runtime-manifest.json"), "{not-json")

            val verification = MpvRuntimeVerifier.verify(layout)

            assertEquals(null, verification.manifest)
            assertFalse(verification.message().contains("Manifest: present"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `statusFromInputs verifies inferred runtime root`() {
        val tempDir = Files.createTempDirectory("miruplay-runtime")
        try {
            val layout = MpvRuntimeDiscovery.layoutFor(tempDir)
            Files.createDirectories(layout.configDirectory.resolve("vs"))
            Files.createFile(layout.executable)
            Files.createFile(layout.rifeScript(RifeBackend.NVIDIA))
            Files.createFile(layout.rifeScript(RifeBackend.DIRECTML))
            Files.createFile(layout.rifeScript(RifeBackend.STANDARD))

            val status = MpvRuntimeVerifier.statusFromInputs(
                mpvPath = layout.executable.toString(),
                configDir = layout.configDirectory.toString(),
            )

            assertTrue(status.contains("Bundled mpv runtime is ready"))
            assertTrue(status.contains("NVIDIA"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
