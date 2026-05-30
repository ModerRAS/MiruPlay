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
}
