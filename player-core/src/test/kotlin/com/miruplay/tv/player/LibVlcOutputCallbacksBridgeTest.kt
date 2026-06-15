package com.miruplay.tv.player

import android.view.Surface
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibVlcOutputCallbacksBridgeTest {
    @Test
    fun `attachOutput returns invalid player result when player pointer is zero`() {
        val bridge = LibVlcOutputCallbacksBridge(nativeInvoker = FakeOutputCallbacksInvoker())

        val result = bridge.attachOutput(
            playerInstance = 0L,
            surface = mockk<Surface>(relaxed = true),
            width = 1280,
            height = 720,
        )

        assertFalse(result.success)
        assertEquals(LibVlcOutputCallbacksBridge.INVALID_PLAYER_INSTANCE, result.resultCode)
        assertNull(result.session)
    }

    @Test
    fun `attachOutput exposes native session handle on success`() {
        val invoker = FakeOutputCallbacksInvoker().apply {
            attachResult = 99L
        }
        val bridge = LibVlcOutputCallbacksBridge(nativeInvoker = invoker)

        val result = bridge.attachOutput(
            playerInstance = 42L,
            surface = mockk<Surface>(relaxed = true),
            width = 1920,
            height = 1080,
        )

        assertTrue(result.success)
        assertEquals(99L, result.session?.bridgeHandle)
        assertEquals(42L, result.session?.playerInstance)
    }

    @Test
    fun `updateOutputWindow and releaseOutput forward to native invoker`() {
        val invoker = FakeOutputCallbacksInvoker().apply {
            attachResult = 77L
        }
        val bridge = LibVlcOutputCallbacksBridge(nativeInvoker = invoker)
        val session = bridge.attachOutput(
            playerInstance = 51L,
            surface = mockk<Surface>(relaxed = true),
            width = 640,
            height = 360,
        ).session!!

        bridge.updateOutputWindow(session, width = 854, height = 480)
        bridge.releaseOutput(session)

        assertEquals(77L, invoker.updatedBridgeHandle)
        assertEquals(854, invoker.updatedWidth)
        assertEquals(480, invoker.updatedHeight)
        assertEquals(51L, invoker.releasedPlayerInstance)
        assertEquals(77L, invoker.releasedBridgeHandle)
    }
}

private class FakeOutputCallbacksInvoker : LibVlcOutputCallbacksInvoker {
    var attachResult: Long = 0L
    var updatedBridgeHandle: Long = 0L
    var updatedWidth: Int = 0
    var updatedHeight: Int = 0
    var releasedPlayerInstance: Long = 0L
    var releasedBridgeHandle: Long = 0L

    override fun attachOutput(
        playerInstance: Long,
        surface: Surface,
        width: Int,
        height: Int,
    ): Long = attachResult

    override fun updateOutputWindow(
        bridgeHandle: Long,
        width: Int,
        height: Int,
    ) {
        updatedBridgeHandle = bridgeHandle
        updatedWidth = width
        updatedHeight = height
    }

    override fun releaseOutput(
        playerInstance: Long,
        bridgeHandle: Long,
    ) {
        releasedPlayerInstance = playerInstance
        releasedBridgeHandle = bridgeHandle
    }
}
