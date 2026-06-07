package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilenameMetadataParserTest {
    @Test
    fun `sanitizeRecognizedText trims path separators from field boundaries`() {
        val result = FilenameParseResult(
            title = "/百鬼夜行抄/",
            group = "/ANi/",
            source = "\\WEB-DL\\",
        ).sanitizeRecognizedText()

        assertEquals("百鬼夜行抄", result.title)
        assertEquals("ANi", result.group)
        assertEquals("WEB-DL", result.source)
    }

    @Test
    fun `sanitizeRecognizedText removes leaked path context from title`() {
        val result = FilenameParseResult(
            title = "/storage/emulated/0/Download/葬送的芙莉莲 第2季/03.mp4",
            episode = 3,
        ).sanitizeRecognizedText()

        assertEquals("葬送的芙莉莲 第2季", result.title)
        assertEquals(3, result.episode)
    }

    @Test
    fun `sanitizeRecognizedText drops titles made only from path context`() {
        val result = FilenameParseResult(
            title = "/sdcard/ /MiruPlayPathParser-20260526-090430",
        ).sanitizeRecognizedText()

        assertNull(result.title)
    }

    @Test
    fun `sanitizeRecognizedText keeps title slashes that are not path context`() {
        val result = FilenameParseResult(
            title = "/Fate/Grand Order/",
        ).sanitizeRecognizedText()

        assertEquals("Fate/Grand Order", result.title)
    }

    @Test
    fun `sanitizeRecognizedText strips drama root folders from title`() {
        val result = FilenameParseResult(
            title = "/dav/115open/影音/电视剧/你好/",
        ).sanitizeRecognizedText()

        assertEquals("你好", result.title)
    }

    @Test
    fun `sanitizeRecognizedText strips simple drama root prefix`() {
        val result = FilenameParseResult(
            title = "电视剧/逐玉",
        ).sanitizeRecognizedText()

        assertEquals("逐玉", result.title)
    }

    @Test
    fun `sanitizeRecognizedText collapses repeated drama path segments`() {
        val result = FilenameParseResult(
            title = "电视剧/偏偏遇见你/偏偏遇见你",
        ).sanitizeRecognizedText()

        assertEquals("偏偏遇见你", result.title)
    }

    @Test
    fun `sanitizeRecognizedText collapses promo clip path back to series title`() {
        val result = FilenameParseResult(
            title = "/dav/115open/影音/电视剧/白日提灯/[片头尾]/片头《初醒》",
        ).sanitizeRecognizedText()

        assertEquals("白日提灯", result.title)
    }
}
