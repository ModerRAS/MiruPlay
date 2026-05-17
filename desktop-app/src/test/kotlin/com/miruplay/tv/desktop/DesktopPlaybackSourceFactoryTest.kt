package com.miruplay.tv.desktop

import com.miruplay.tv.model.SubtitleFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPlaybackSourceFactoryTest {
    @Test
    fun `blank subtitle path creates no external tracks`() {
        assertTrue(DesktopPlaybackSourceFactory.buildSubtitleTracks(" ").isEmpty())
    }

    @Test
    fun `subtitle track keeps file name and detects ass format`() {
        val track = DesktopPlaybackSourceFactory.subtitleTrackFromPath("D:/Anime/Show/Episode 01.ass")

        assertEquals("Episode 01.ass", track.title)
        assertEquals("D:/Anime/Show/Episode 01.ass", track.path)
        assertEquals(SubtitleFormat.ASS, track.format)
        assertTrue(track.isExternal)
    }

    @Test
    fun `unknown subtitle extension falls back to srt`() {
        val track = DesktopPlaybackSourceFactory.subtitleTrackFromPath("D:/Anime/Show/Episode 01.txt")

        assertEquals(SubtitleFormat.SRT, track.format)
    }
}
