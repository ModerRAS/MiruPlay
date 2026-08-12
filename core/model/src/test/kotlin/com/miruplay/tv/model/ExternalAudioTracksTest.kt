package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalAudioTracksTest {
    @Test
    fun `matching audio accepts same stem and language suffixes`() {
        val video = "/Show/Episode 01.mkv"

        assertEquals(
            listOf(
                "/Show/Episode 01.en.flac",
                "/Show/Episode 01.ja.ac3",
                "/Show/Episode 01.m4a",
                "/Show/Episode 01.zh-Hans.mka",
            ),
            matchingExternalAudioPaths(
                video,
                listOf(
                    video,
                    "/Show/Episode 01.m4a",
                    "/Show/Episode 01.ja.ac3",
                    "/Show/Episode 01.en.flac",
                    "/Show/Episode 01.zh-Hans.mka",
                    "/Show/Episode 01.en.srt",
                    "/Show/Episode 010.flac",
                    "/Show/Episode 02.flac",
                ),
            ),
        )
    }

    @Test
    fun `audio model derives language without treating title words as language`() {
        val tracks = buildExternalAudioTracks(
            listOf(
                "/Show/Episode 01.zh-Hant.flac",
                "/Show/Episode 01 Commentary.opus",
            ),
        )

        assertEquals(listOf("zh-Hant", "und"), tracks.map { it.language })
        assertEquals("Episode 01.zh-Hant.flac", tracks.first().title)
    }
}
