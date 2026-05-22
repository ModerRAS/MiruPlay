package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTracksTest {
    @Test
    fun `blank subtitle path creates no tracks`() {
        assertTrue(buildExternalSubtitleTracks(" \n ").isEmpty())
    }

    @Test
    fun `external subtitle keeps file name and detects format`() {
        val track = externalSubtitleTrackFromPath("D:/Anime/Show/Episode 01.ass")

        assertEquals("Episode 01.ass", track.title)
        assertEquals("D:/Anime/Show/Episode 01.ass", track.path)
        assertEquals(SubtitleFormat.ASS, track.format)
        assertTrue(track.isExternal)
    }

    @Test
    fun `subtitle list accepts semicolon and newline separated paths`() {
        val tracks = buildExternalSubtitleTracks("D:/Anime/01.ass; D:/Anime/01.vtt\nD:/Anime/01.ssa")

        assertEquals(listOf(SubtitleFormat.ASS, SubtitleFormat.VTT, SubtitleFormat.SSA), tracks.map { it.format })
    }

    @Test
    fun `unknown subtitle extension falls back to srt`() {
        assertEquals(SubtitleFormat.SRT, subtitleFormatFromPath("D:/Anime/Show/Episode 01.txt"))
    }
}
