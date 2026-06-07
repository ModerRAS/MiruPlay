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
    fun `episode playback title uses episode wording and optional title`() {
        val untitledEpisode = Episode(
            id = "ep-3",
            animeId = "series-1",
            episodeNumber = 3,
            title = "",
            filePath = "/shows/series-1/s01e03.mkv",
            fileName = "s01e03.mkv",
        )
        val titledEpisode = untitledEpisode.copy(title = "First Light")

        assertEquals("第 3 集", untitledEpisode.playbackDisplayTitle())
        assertEquals("第 3 集 · First Light", titledEpisode.playbackDisplayTitle())
    }

    @Test
    fun `shared size and playback position formatting match UI expectations`() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("2.0 MB", formatFileSize(2_097_152))
        assertEquals("01:35", formatPlaybackPosition(95_000))
        assertEquals("1:02:03", formatPlaybackPosition(3_723_000))
    }

    @Test
    fun `scraper confidence label rounds to whole percent`() {
        val result = ScraperResult(
            animeId = "431767",
            title = "Frieren",
            titleCn = "葬送的芙莉莲",
            matchedTitle = "Frieren",
            confidence = 0.945f,
            source = ScraperSource.BANGUMI,
        )

        assertEquals("95%", result.confidencePercentLabel())
    }

    @Test
    fun `local timestamp formatting skips missing values and uses stable pattern`() {
        assertEquals(null, formatLocalTimestamp(0L))
        assertEquals(19, formatLocalTimestamp(1_700_000_000_000L)?.length)
        assertEquals('-', formatLocalTimestamp(1_700_000_000_000L)?.get(4))
        assertEquals(':', formatLocalTimestamp(1_700_000_000_000L)?.get(13))

        assertEquals(null, formatShortLocalTimestamp(0L))
        assertEquals(11, formatShortLocalTimestamp(1_700_000_000_000L)?.length)
        assertEquals('-', formatShortLocalTimestamp(1_700_000_000_000L)?.get(2))
        assertEquals(':', formatShortLocalTimestamp(1_700_000_000_000L)?.get(8))
    }
}
