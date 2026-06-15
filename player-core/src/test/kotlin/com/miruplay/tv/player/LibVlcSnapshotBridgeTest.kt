package com.miruplay.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LibVlcSnapshotBridgeTest {
    @Test
    fun `takeSnapshot invokes native bridge and returns success when file is written`() {
        val tempDir = createTempDir(prefix = "libvlc-snapshot-")
        val outputFile = File(tempDir, "frame.png")
        val invoker = FakeLibVlcSnapshotInvoker { _, _, _, _ ->
            outputFile.writeText("png")
            0
        }
        val bridge = LibVlcSnapshotBridge(invoker)

        val result = bridge.takeSnapshot(
            playerInstance = 42L,
            outputFile = outputFile,
        )

        assertTrue(result.success)
        assertEquals(0, result.resultCode)
        assertEquals(outputFile.absolutePath, invoker.lastOutputPath)
        assertEquals(42L, invoker.lastPlayerInstance)
    }

    @Test
    fun `takeSnapshot rejects invalid player instances`() {
        val tempDir = createTempDir(prefix = "libvlc-snapshot-")
        val outputFile = File(tempDir, "frame.png")
        val invoker = FakeLibVlcSnapshotInvoker { _, _, _, _ -> 0 }
        val bridge = LibVlcSnapshotBridge(invoker)

        val result = bridge.takeSnapshot(
            playerInstance = 0L,
            outputFile = outputFile,
        )

        assertFalse(result.success)
        assertEquals(LibVlcSnapshotBridge.INVALID_PLAYER_INSTANCE, result.resultCode)
        assertEquals(0, invoker.invocationCount)
    }

    @Test
    fun `takeSnapshot accepts signed non zero native pointer values`() {
        val tempDir = createTempDir(prefix = "libvlc-snapshot-")
        val outputFile = File(tempDir, "frame.png")
        val invoker = FakeLibVlcSnapshotInvoker { _, _, _, _ ->
            outputFile.writeText("png")
            0
        }
        val bridge = LibVlcSnapshotBridge(invoker)

        val result = bridge.takeSnapshot(
            playerInstance = Long.MIN_VALUE + 42L,
            outputFile = outputFile,
        )

        assertTrue(result.success)
        assertEquals(Long.MIN_VALUE + 42L, invoker.lastPlayerInstance)
    }

    @Test
    fun `takeSnapshot returns failure when native bridge reports an error`() {
        val tempDir = createTempDir(prefix = "libvlc-snapshot-")
        val outputFile = File(tempDir, "frame.png")
        val invoker = FakeLibVlcSnapshotInvoker { _, _, _, _ -> -7 }
        val bridge = LibVlcSnapshotBridge(invoker)

        val result = bridge.takeSnapshot(
            playerInstance = 7L,
            outputFile = outputFile,
        )

        assertFalse(result.success)
        assertEquals(-7, result.resultCode)
    }
}

private class FakeLibVlcSnapshotInvoker(
    private val onInvoke: (Long, String, Int, Int) -> Int,
) : LibVlcSnapshotInvoker {
    var invocationCount: Int = 0
        private set
    var lastPlayerInstance: Long? = null
        private set
    var lastOutputPath: String? = null
        private set

    override fun takeSnapshot(
        playerInstance: Long,
        outputPath: String,
        width: Int,
        height: Int,
    ): Int {
        invocationCount += 1
        lastPlayerInstance = playerInstance
        lastOutputPath = outputPath
        return onInvoke(playerInstance, outputPath, width, height)
    }
}
