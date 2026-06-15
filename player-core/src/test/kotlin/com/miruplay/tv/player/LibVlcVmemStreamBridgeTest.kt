package com.miruplay.tv.player

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibVlcVmemStreamBridgeTest {
    @Test
    fun `create stream keeps decoder native chroma by default`() {
        val nativeInvoker = FakeVmemStreamInvoker()
        val bridge = LibVlcVmemStreamBridge(nativeInvoker = nativeInvoker)

        val result = bridge.createStream()

        assertTrue(result.success)
        assertEquals(null, nativeInvoker.lastCreatePreferredOutputChroma)
    }

    @Test
    fun `create stream normalizes explicit chroma override`() {
        val nativeInvoker = FakeVmemStreamInvoker()
        val bridge = LibVlcVmemStreamBridge(nativeInvoker = nativeInvoker)

        val result = bridge.createStream(" p010 ")

        assertTrue(result.success)
        assertEquals("P010", nativeInvoker.lastCreatePreferredOutputChroma)
    }

    @Test
    fun `default vmem bridge constants stay aligned with native override expectations`() {
        assertEquals("vmem", LibVlcVmemStreamBridge.DEFAULT_VIDEO_OUTPUT_MODULE)
        assertEquals("wextern", LibVlcVmemStreamBridge.DEFAULT_WINDOW_MODULE)
        assertEquals("none", LibVlcVmemStreamBridge.DEFAULT_DECODER_DEVICE)
    }

    @Test
    fun `attach stream fails fast when player instance is invalid`() {
        val bridge = LibVlcVmemStreamBridge(nativeInvoker = FakeVmemStreamInvoker())

        val result = bridge.attachStream(
            playerInstance = 0L,
            session = LibVlcVmemStreamSession(streamHandle = 42L),
            windowWidth = 1920,
            windowHeight = 1080,
        )

        assertFalse(result.success)
        assertEquals(LibVlcVmemStreamBridge.INVALID_PLAYER_INSTANCE, result.resultCode)
    }

    @Test
    fun `read state decodes native payload`() {
        val bridge = LibVlcVmemStreamBridge(
            nativeInvoker = FakeVmemStreamInvoker(
                state = longArrayOf(
                    1L,
                    7L,
                    packFourccToLong("I0AL"),
                    1920L,
                    1088L,
                    1920L,
                    1080L,
                    3L,
                    3840L,
                    1920L,
                    1920L,
                    0L,
                    1088L,
                    544L,
                    544L,
                    0L,
                    6_266_880L,
                ),
            ),
        )

        val state = bridge.readState(LibVlcVmemStreamSession(streamHandle = 5L))

        assertTrue(state.configured)
        assertEquals(7L, state.frameVersion)
        assertEquals("I0AL", state.chroma)
        assertEquals(1920, state.width)
        assertEquals(1088, state.height)
        assertEquals(1920, state.visibleWidth)
        assertEquals(1080, state.visibleHeight)
        assertEquals(3, state.planeCount)
        assertEquals(3840, state.pitch)
        assertEquals(1920, state.pitch1)
        assertEquals(1920, state.pitch2)
        assertEquals(1088, state.line0)
        assertEquals(544, state.line1)
        assertEquals(544, state.line2)
        assertEquals(6_266_880, state.totalBytes)
    }

    @Test
    fun `attach stream forwards host window bounds to the native invoker`() {
        val nativeInvoker = FakeVmemStreamInvoker()
        val bridge = LibVlcVmemStreamBridge(nativeInvoker = nativeInvoker)

        val result = bridge.attachStream(
            playerInstance = 9L,
            session = LibVlcVmemStreamSession(streamHandle = 5L),
            windowWidth = 1920,
            windowHeight = 1080,
        )

        assertTrue(result.success)
        assertEquals(9L, nativeInvoker.lastAttachPlayerInstance)
        assertEquals(5L, nativeInvoker.lastAttachStreamHandle)
        assertEquals(1920, nativeInvoker.lastAttachWindowWidth)
        assertEquals(1080, nativeInvoker.lastAttachWindowHeight)
    }

    @Test
    fun `copy latest frame rgba forwards request to native invoker`() {
        val nativeInvoker = FakeVmemStreamInvoker(copiedRgbaVersion = 33L)
        val bridge = LibVlcVmemStreamBridge(nativeInvoker = nativeInvoker)
        val target = ByteBuffer.allocateDirect(16)

        val version = bridge.copyLatestFrameRgba(
            session = LibVlcVmemStreamSession(streamHandle = 7L),
            target = target,
            lastFrameVersion = 12L,
        )

        assertEquals(33L, version)
        assertEquals(7L, nativeInvoker.lastCopyRgbaStreamHandle)
        assertEquals(16, nativeInvoker.lastCopyRgbaTargetCapacity)
        assertEquals(12L, nativeInvoker.lastCopyRgbaLastFrameVersion)
    }

    @Test
    fun `attach stream clamps empty host window bounds before calling native invoker`() {
        val nativeInvoker = FakeVmemStreamInvoker()
        val bridge = LibVlcVmemStreamBridge(nativeInvoker = nativeInvoker)

        val result = bridge.attachStream(
            playerInstance = 9L,
            session = LibVlcVmemStreamSession(streamHandle = 5L),
            windowWidth = 0,
            windowHeight = -4,
        )

        assertTrue(result.success)
        assertEquals(1, nativeInvoker.lastAttachWindowWidth)
        assertEquals(1, nativeInvoker.lastAttachWindowHeight)
    }
}

private class FakeVmemStreamInvoker(
    private val createHandle: Long = 1L,
    private val attachResult: Int = 0,
    private val state: LongArray = longArrayOf(
        0L,
        0L,
        0L,
        0L,
        0L,
        0L,
        0L,
        0L,
        0L,
        0L,
        0L,
        0L,
        0L,
        0L,
        0L,
        0L,
        0L,
    ),
    private val copiedVersion: Long = 0L,
    private val copiedRgbaVersion: Long = 0L,
) : LibVlcVmemStreamInvoker {
    var lastCreatePreferredOutputChroma: String? = null
    var lastAttachPlayerInstance: Long = 0L
    var lastAttachStreamHandle: Long = 0L
    var lastAttachWindowWidth: Int = 0
    var lastAttachWindowHeight: Int = 0
    var lastCopyRgbaStreamHandle: Long = 0L
    var lastCopyRgbaTargetCapacity: Int = 0
    var lastCopyRgbaLastFrameVersion: Long = 0L

    override fun createStream(preferredOutputChroma: String?): Long {
        lastCreatePreferredOutputChroma = preferredOutputChroma
        return createHandle
    }

    override fun attachStream(
        playerInstance: Long,
        streamHandle: Long,
        windowWidth: Int,
        windowHeight: Int,
    ): Int {
        lastAttachPlayerInstance = playerInstance
        lastAttachStreamHandle = streamHandle
        lastAttachWindowWidth = windowWidth
        lastAttachWindowHeight = windowHeight
        return attachResult
    }

    override fun readState(streamHandle: Long): LongArray = state

    override fun copyLatestFrame(
        streamHandle: Long,
        target: ByteBuffer,
        targetCapacity: Int,
        lastFrameVersion: Long,
    ): Long = copiedVersion

    override fun copyLatestFrameRgba(
        streamHandle: Long,
        target: ByteBuffer,
        targetCapacity: Int,
        lastFrameVersion: Long,
    ): Long {
        lastCopyRgbaStreamHandle = streamHandle
        lastCopyRgbaTargetCapacity = targetCapacity
        lastCopyRgbaLastFrameVersion = lastFrameVersion
        return copiedRgbaVersion
    }

    override fun releaseStream(playerInstance: Long, streamHandle: Long) = Unit
}

private fun packFourccToLong(value: String): Long {
    require(value.length == 4) { "fourcc must be four characters" }
    return value.foldIndexed(0L) { index, acc, char ->
        acc or ((char.code.toLong() and 0xFFL) shl (index * 8))
    }
}
