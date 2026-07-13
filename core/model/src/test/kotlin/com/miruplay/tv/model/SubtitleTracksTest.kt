package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("zh-CN", externalSubtitleTrackFromPath("Episode.zh_cn.srt").language)
        assertEquals("zh-Hant", externalSubtitleTrackFromPath("Episode_cht.srt").language)
        assertEquals("zh-Hans", externalSubtitleTrackFromPath("Episode [SC].srt").language)
        assertEquals("en", externalSubtitleTrackFromPath("Episode.eng.vtt").language)
    }

    @Test
    fun `external subtitle decodes remote filename metadata and ignores url suffix`() {
        val url = "https://dav.example/Show/Episode%20%5BSC%5D.ass?token=abc#fragment"
        val track = externalSubtitleTrackFromPath(url)

        assertEquals("Episode [SC].ass", track.title)
        assertEquals("zh-Hans", track.language)
        assertEquals(SubtitleFormat.ASS, track.format)
        assertEquals(url, track.path)
    }

    @Test
    fun `encoded Chinese subtitle title participates in preference detection`() {
        val track = externalSubtitleTrackFromPath(
            "https://dav.example/Show/Episode.%E7%AE%80%E4%B8%AD.srt?token=abc",
        )

        assertEquals(
            0,
            preferredSubtitleTrackIndex(
                listOf(track),
                SubtitleLanguagePreference.CHINESE_SIMPLIFIED,
            ),
        )
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

    @Test
    fun `preferred subtitle recognizes common Chinese aliases`() {
        val tracks = buildExternalSubtitleTracks(
            listOf(
                "/Anime/Episode.eng.srt",
                "/Anime/Episode.zh_cn.ass",
                "/Anime/Episode.cht.srt",
            ),
        )

        assertEquals(1, preferredSubtitleTrackIndex(tracks, SubtitleLanguagePreference.CHINESE_SIMPLIFIED))
        assertEquals(2, preferredSubtitleTrackIndex(tracks, SubtitleLanguagePreference.CHINESE_TRADITIONAL))
        assertEquals(1, preferredSubtitleTrackIndex(tracks, SubtitleLanguagePreference.CHINESE))
        assertEquals(0, preferredSubtitleTrackIndex(tracks, SubtitleLanguagePreference.ENGLISH))
    }

    @Test
    fun `preferred subtitle uses title when language metadata is absent`() {
        val tracks = listOf(
            SubtitleTrack(
                language = "und",
                title = "简体中文字幕",
                isExternal = false,
                path = "",
                format = SubtitleFormat.ASS,
            ),
        )

        assertEquals(0, preferredSubtitleTrackIndex(tracks, SubtitleLanguagePreference.CHINESE_SIMPLIFIED))
        assertNull(preferredSubtitleTrackIndex(tracks, SubtitleLanguagePreference.JAPANESE))
        assertNull(preferredSubtitleTrackIndex(tracks, SubtitleLanguagePreference.AUTO))
    }

    @Test
    fun `preferred subtitle path moves the match first without dropping tracks`() {
        assertEquals(
            listOf("Episode.zh_cn.ass", "Episode.eng.srt", "Episode.jpn.srt"),
            prioritizeSubtitlePaths(
                listOf("Episode.eng.srt", "Episode.zh_cn.ass", "Episode.jpn.srt"),
                SubtitleLanguagePreference.CHINESE_SIMPLIFIED,
            ),
        )
    }

    @Test
    fun `specific Chinese preference favors exact script over generic Chinese`() {
        val tracks = listOf(
            externalSubtitleTrackFromPath("Episode.zh.srt"),
            externalSubtitleTrackFromPath("Episode.zh-Hans.ass"),
        )

        assertEquals(1, preferredSubtitleTrackIndex(tracks, SubtitleLanguagePreference.CHINESE_SIMPLIFIED))
        assertEquals(0, preferredSubtitleTrackIndex(tracks, SubtitleLanguagePreference.CHINESE))
    }
}
