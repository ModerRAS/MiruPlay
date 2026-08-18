package com.miruplay.tv.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationLanguagesTest {

    @Test
    fun `supported list has about 10 languages with zh-Hans first as default`() {
        assertTrue(SUPPORTED_TARGET_LANGUAGES.size >= 10)
        assertEquals("zh-Hans", SUPPORTED_TARGET_LANGUAGES.first().code)
        assertEquals("中文", SUPPORTED_TARGET_LANGUAGES.first().displayName)
        assertTrue(SUPPORTED_TARGET_LANGUAGES.any { it.code == "zh-Hans" })
        assertTrue(SUPPORTED_TARGET_LANGUAGES.map { it.code }.distinct().size == SUPPORTED_TARGET_LANGUAGES.size)
    }

    @Test
    fun `every provider mapping works for every supported language without throwing`() {
        SUPPORTED_TARGET_LANGUAGES.forEach { language ->
            val google = googleLanguageCode(language.code)
            val bing = bingLanguageCode(language.code)
            val deepSeek = deepSeekLanguageInstruction(language.code)
            assertFalse("google mapping blank for ${language.code}", google.isBlank())
            assertFalse("bing mapping blank for ${language.code}", bing.isBlank())
            assertFalse("deepseek mapping blank for ${language.code}", deepSeek.isBlank())
        }
    }

    @Test
    fun `google maps zh variants to zh-CN and zh-TW`() {
        assertEquals("zh-CN", googleLanguageCode("zh-Hans"))
        assertEquals("zh-TW", googleLanguageCode("zh-Hant"))
        assertEquals("en", googleLanguageCode("en"))
    }

    @Test
    fun `bing accepts zh-Hans and zh-Hant directly`() {
        assertEquals("zh-Hans", bingLanguageCode("zh-Hans"))
        assertEquals("zh-Hant", bingLanguageCode("zh-Hant"))
    }

    @Test
    fun `deepseek instruction is a natural language target`() {
        assertEquals("简体中文", deepSeekLanguageInstruction("zh-Hans"))
        assertEquals("繁體中文", deepSeekLanguageInstruction("zh-Hant"))
        assertEquals("日语", deepSeekLanguageInstruction("ja"))
    }

    @Test
    fun `unknown codes fall back to the code itself`() {
        assertEquals("xx-YY", googleLanguageCode("xx-YY"))
        assertEquals("xx-YY", bingLanguageCode("xx-YY"))
        assertEquals("xx-YY", deepSeekLanguageInstruction("xx-YY"))
    }
}
