package com.miruplay.tv.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleFileParserTest {

    @Test
    fun `SRT parse then translate placeholder then rewrite preserves timeline and multi-line cues`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            Hello world

            2
            00:00:05,500 --> 00:00:09,250
            Second line one
            Second line two
        """.trimIndent()

        val cues = SubtitleFileParser.parse(srt)

        assertEquals(2, cues.size)
        assertEquals("00:00:01,000", cues[0].start)
        assertEquals("00:00:04,000", cues[0].end)
        assertEquals("Hello world", cues[0].text)
        assertEquals("00:00:05,500", cues[1].start)
        assertEquals("00:00:09,250", cues[1].end)
        assertTrue(cues[1].text.contains("\n"))

        // 模拟翻译：逐条替换（占位，无网络）
        val translated = cues.map { cue ->
            cue.copy(text = cue.text.replace("Hello", "你好").replace("Second", "第二"))
        }
        val rewritten = SubtitleFileParser.toSrt(translated)

        assertTrue(rewritten.contains("1\n00:00:01,000 --> 00:00:04,000\n你好 world"))
        assertTrue(rewritten.contains("2\n00:00:05,500 --> 00:00:09,250\n第二 line one\n第二 line two"))
        assertFalse(rewritten.contains("Hello"))
    }

    @Test
    fun `ASS only parses Dialogue lines and strips override tags`() {
        val ass = """
            [Script Info]
            Title: Test

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.50,0:00:04.25,Default,,0,0,0,,Hello {\i1}world{\i0}
            Comment: 0,0:00:01.50,0:00:04.25,Default,,0,0,0,,should be ignored
            Dialogue: 1,0:00:05.00,0:00:06.00,Default,,0,0,0,,Line one\NLine two
        """.trimIndent()

        val cues = SubtitleFileParser.parse(ass)

        assertEquals(2, cues.size)
        assertEquals("00:00:01,500", cues[0].start)
        assertEquals("00:00:04,250", cues[0].end)
        assertEquals("Hello world", cues[0].text)
        assertEquals("00:00:05,000", cues[1].start)
        assertTrue(cues[1].text.contains("\n"))
        assertFalse(cues[1].text.contains("\\N"))
    }

    @Test
    fun `ASS dialogue text with commas inside override tags is kept`() {
        val ass = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\pos(100,200)}Stay, here
        """.trimIndent()

        val cues = SubtitleFileParser.parse(ass)

        assertEquals(1, cues.size)
        assertEquals("Stay, here", cues[0].text)
    }

    @Test
    fun `VTT header note and cue settings are handled and timestamps normalized`() {
        val vtt = """
            WEBVTT

            NOTE this is a comment block

            00:00:01.000 --> 00:00:04.000
            VTT line one

            00:00:05.000 --> 00:00:08.000 align:start position:50%
            VTT line two
        """.trimIndent()

        val cues = SubtitleFileParser.parse(vtt)

        assertEquals(2, cues.size)
        assertEquals("00:00:01,000", cues[0].start)
        assertEquals("00:00:04,000", cues[0].end)
        assertEquals("VTT line one", cues[0].text)
        assertEquals("00:00:05,000", cues[1].start)
        assertEquals("00:00:08,000", cues[1].end)
    }

    @Test
    fun `empty and blank cues are skipped`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            Keep me

            2
            00:00:05,000 --> 00:00:06,000
        """.trimIndent()

        val cues = SubtitleFileParser.parse(srt)

        assertEquals(1, cues.size)
        assertEquals("Keep me", cues[0].text)
    }
}
