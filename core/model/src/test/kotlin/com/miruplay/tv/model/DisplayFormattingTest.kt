package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayFormattingTest {
    @Test
    fun `anime title prefers Chinese title then original then id`() {
        assertEquals("孤独摇滚！", Anime(id = "1", title = "Bocchi", titleCn = "孤独摇滚！").displayTitle())
        assertEquals("Bocchi", Anime(id = "1", title = "Bocchi").displayTitle())
        assertEquals("1", Anime(id = "1", title = "").displayTitle())
    }

    @Test
    fun `playback source title decodes URI file name`() {
        val source = PlaybackSource(
            uri = "http://127.0.0.1/video/Bocchi%20the%20Rock%2001.mkv?token=abc",
            mediaSourceId = "media",
        )

        assertEquals("Bocchi the Rock 01", source.displayTitle())
    }

    @Test
    fun `shared size and playback position formatting match UI expectations`() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("2.0 MB", formatFileSize(2_097_152))
        assertEquals("01:35", formatPlaybackPosition(95_000))
        assertEquals("1:02:03", formatPlaybackPosition(3_723_000))
    }
}
