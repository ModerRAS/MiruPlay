package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceTest {
    @Test
    fun `playbackSourceFromInputs trims media path parses seconds and subtitles`() {
        val source = playbackSourceFromInputs(
            mediaPath = " D:/Anime/Episode 01.mkv ",
            subtitlePath = "D:/Anime/Episode 01.ass",
            startSeconds = "12.345",
            mediaSourceId = "library",
            episodeId = "episode-1",
        )

        assertEquals("D:/Anime/Episode 01.mkv", source.uri)
        assertEquals("library", source.mediaSourceId)
        assertEquals("episode-1", source.episodeId)
        assertEquals(12_345L, source.startPosition)
        assertEquals(listOf("D:/Anime/Episode 01.ass"), source.subtitleTracks.map { it.path })
    }

    @Test
    fun `playbackSourceFromInputs defaults episode id to media uri`() {
        val source = playbackSourceFromInputs(
            mediaPath = "video.mkv",
            subtitlePath = "",
            startSeconds = "",
            mediaSourceId = "desktop",
        )

        assertEquals("video.mkv", source.episodeId)
    }

    @Test
    fun `playbackSourceFromInputs clamps invalid or negative starts to zero`() {
        assertEquals(
            0L,
            playbackSourceFromInputs("video.mkv", "", "abc", mediaSourceId = "desktop").startPosition,
        )
        assertEquals(
            0L,
            playbackSourceFromInputs("video.mkv", "", "-3", mediaSourceId = "desktop").startPosition,
        )
    }

    @Test
    fun `playbackSourceFromInputs requires media path`() {
        val error = runCatching {
            playbackSourceFromInputs(
                mediaPath = "   ",
                subtitlePath = "",
                startSeconds = "",
                mediaSourceId = "desktop",
                blankMediaMessage = "Choose media first.",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Choose media first.", error?.message)
    }
}
