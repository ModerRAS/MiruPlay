package com.miruplay.tv.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashEpisodePatternTest {
    private val detector = DefaultEpisodeDetector()

    @Test
    fun `dash pattern wins over standard for bracketed web-dl names`() {
        val match = detector.detectEpisode(
            "[ANi] 黑色五葉草 Season 2 - 173 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4",
        )!!
        assertEquals(173, match.episodeNumber)
        assertEquals(2, match.seasonNumber)
        assertEquals("黑色五葉草", match.animeName)
    }

    @Test
    fun `dash pattern parses plain sequel names`() {
        val match = detector.detectEpisode("[ANi] 野生的大魔王出現了！ - 16 [1080P][Baha].mp4")!!
        assertEquals(16, match.episodeNumber)
        assertEquals(1, match.seasonNumber)
        assertEquals("野生的大魔王出現了！", match.animeName)
    }

    @Test
    fun `plain dash episode without brackets still uses standard path`() {
        val match = detector.detectEpisode("Cached Show - 01.mkv")!!
        assertEquals(1, match.episodeNumber)
    }

    @Test
    fun `detectSeason reads chinese season markers`() {
        assertEquals(2, detector.detectSeason("[ANi] 夜櫻家大作戰 第二季 - 41 [1080P].mp4"))
        assertEquals(3, detector.detectSeason("转生贵族 第三季 - 27 [1080P].mp4"))
        assertEquals(2, detector.detectSeason("[ANi] 佐佐木與文鳥小嗶 Season 2 - 02 [1080P].mp4"))
    }
}
