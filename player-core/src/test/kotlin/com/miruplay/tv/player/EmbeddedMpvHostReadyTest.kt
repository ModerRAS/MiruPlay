package com.miruplay.tv.player

import android.view.ViewGroup
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedMpvHostReadyTest {
    @Test
    fun `host is ready only when attached and sized`() {
        val host = mockk<ViewGroup>(relaxed = true)
        every { host.isAttachedToWindow } returns true
        every { host.width } returns 1920
        every { host.height } returns 1080

        assertTrue(isEmbeddedMpvHostReady(host))
    }

    @Test
    fun `host is not ready when detached`() {
        val host = mockk<ViewGroup>(relaxed = true)
        every { host.isAttachedToWindow } returns false
        every { host.width } returns 1920
        every { host.height } returns 1080

        assertFalse(isEmbeddedMpvHostReady(host))
    }

    @Test
    fun `host is not ready when size is zero`() {
        val host = mockk<ViewGroup>(relaxed = true)
        every { host.isAttachedToWindow } returns true
        every { host.width } returns 0
        every { host.height } returns 1080

        assertFalse(isEmbeddedMpvHostReady(host))
    }
}
