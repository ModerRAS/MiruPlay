package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class StreamRangeTest {
    @Test
    fun `range length includes both endpoints`() {
        assertEquals(4L, StreamRange(2, 5).length)
        assertEquals(null, StreamRange(2).length)
    }

    @Test
    fun `range rejects invalid bounds`() {
        assertTrue(runCatching { StreamRange(-1, 2) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { StreamRange(5, 2) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `applyRange skips start and limits length`() {
        val stream = ByteArrayInputStream("0123456789".toByteArray()).applyRange(StreamRange(2, 5))

        assertEquals("2345", stream.readBytes().decodeToString())
    }

    @Test
    fun `applyRange supports open ended range`() {
        val stream = ByteArrayInputStream("0123456789".toByteArray()).applyRange(StreamRange(7))

        assertEquals("789", stream.readBytes().decodeToString())
    }
}
