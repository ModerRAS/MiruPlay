package com.miruplay.tv.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelLayoutTest {
    @Test
    fun `known 5 point 1 mask is normalized to ITU order`() {
        val layout = ChannelLayout.from(6, ChannelLayout.ANDROID_5_1_MASK)

        assertEquals(ChannelLayoutId.SURROUND_5_1, layout.id)
        assertEquals(listOf(Channel.L, Channel.R, Channel.C, Channel.LFE, Channel.LS, Channel.RS), layout.channels)
    }

    @Test
    fun `aac 5 point 1 order is remapped without changing unknown order`() {
        val samples = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f)
        val normalized = ChannelLayout.from(6, null).normalizeInterleaved(samples, InputOrder.AAC_5_1)

        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 6f, 4f, 5f), normalized, 0f)
        assertEquals(InputOrder.UNKNOWN, ChannelLayout.from(6, null).defaultInputOrder)
    }
}
