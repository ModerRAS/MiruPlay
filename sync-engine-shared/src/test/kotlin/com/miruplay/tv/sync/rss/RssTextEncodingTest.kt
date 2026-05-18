package com.miruplay.tv.sync.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class RssTextEncodingTest {
    @Test
    fun `sha1 hex is lowercase and stable`() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", RssTextEncoding.sha1Hex("abc"))
    }

    @Test
    fun `query value uses percent spaces`() {
        assertEquals("Test%20Episode%2001", RssTextEncoding.queryValue("Test Episode 01"))
        assertEquals("http%3A%2F%2Ftracker", RssTextEncoding.queryValue("http://tracker"))
    }
}
