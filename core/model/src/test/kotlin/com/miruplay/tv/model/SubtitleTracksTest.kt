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
        assertEquals("und", track.language)
        assertTrue(track.isExternal)
    }

    @Test
    fun `external subtitle parses common language suffixes`() {
        assertEquals("zh-Hans", externalSubtitleTrackFromPath("Episode.zh-Hans.ass").language)
        assertEquals("zh-CN", externalSubtitleTrackFromPath("Episode.zh-cn.srt").language)
        assertEquals("zh-Hant", externalSubtitleTrackFromPath("Episode_cht.srt").language)
        assertEquals("en", externalSubtitleTrackFromPath("Episode.eng.vtt").language)
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

    @Test
    fun `same stem subtitle and language suffixes attach in stable order`() {
        val video = "/Anime/Show/Episode 01.mkv"

        assertEquals(
            listOf(
                "/Anime/Show/Episode 01.ass",
                "/Anime/Show/Episode 01.zh-CN.srt",
                "/Anime/Show/Episode 01_cht.vtt",
            ),
            matchingExternalSubtitlePaths(
                video,
                listOf(
                    video,
                    "/Anime/Show/Episode 01_cht.vtt",
                    "/Anime/Show/Episode 01.zh-CN.srt",
                    "/Anime/Show/Episode 01.ass",
                ),
            ),
        )
    }

    @Test
    fun `subtitle matching does not confuse adjacent episodes or unsupported sub files`() {
        assertTrue(
            matchingExternalSubtitlePaths(
                "/Anime/Episode 01.mkv",
                listOf(
                    "/Anime/Episode 010.ass",
                    "/Anime/Episode 02.srt",
                    "/Anime/Episode 01.sub",
                ),
            ).isEmpty(),
        )
    }
}
