package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpByteRangeTest {
    @Test
    fun `parse accepts standard byte range header`() {
        val request = HttpByteRangeRequest.parse("bytes=2-5")

        assertEquals(HttpByteRangeRequest(start = 2L, endInclusive = 5L), request)
    }

    @Test
    fun `parse accepts suffix ranges and ignores extra ranges`() {
        assertEquals(
            HttpByteRangeRequest(start = null, endInclusive = 4L),
            HttpByteRangeRequest.parse("bytes=-4"),
        )
        assertEquals(
            HttpByteRangeRequest(start = 2L, endInclusive = 5L),
            HttpByteRangeRequest.parse("bytes=2-5,8-9"),
        )
    }

    @Test
    fun `parse rejects unsupported range units and blank ranges`() {
        assertNull(HttpByteRangeRequest.parse("items=2-5"))
        assertNull(HttpByteRangeRequest.parse("bytes=-"))
    }

    @Test
    fun `resolve creates stream range and content range header`() {
        val resolved = HttpByteRangeRequest(2L, 5L).resolve(10L)

        assertTrue(resolved is HttpByteRange.Resolved)
        resolved as HttpByteRange.Resolved
        assertEquals(StreamRange(2L, 5L), resolved.toStreamRange())
        assertEquals(4L, resolved.length)
        assertEquals("bytes 2-5/10", resolved.contentRangeHeader)
    }

    @Test
    fun `resolve handles suffix range`() {
        val resolved = HttpByteRangeRequest(null, 4L).resolve(10L) as HttpByteRange.Resolved

        assertEquals(StreamRange(6L, 9L), resolved.toStreamRange())
        assertEquals("bytes 6-9/10", resolved.contentRangeHeader)
    }

    @Test
    fun `resolve reports invalid range with total length`() {
        val invalid = HttpByteRangeRequest(20L, 30L).resolve(10L)

        assertTrue(invalid is HttpByteRange.Invalid)
        assertEquals("bytes */10", (invalid as HttpByteRange.Invalid).contentRangeHeader)
    }

    @Test
    fun `resolve without total length remains unresolved`() {
        assertEquals(HttpByteRange.Unresolved, HttpByteRangeRequest(2L, 5L).resolve(null))
        assertEquals(HttpByteRange.Unresolved, HttpByteRangeRequest(2L, 5L).resolve(0L))
    }
}
