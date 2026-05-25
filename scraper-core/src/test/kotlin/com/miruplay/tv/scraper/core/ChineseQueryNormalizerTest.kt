package com.miruplay.tv.scraper.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseQueryNormalizerTest {
    @Test
    fun `traditional Chinese Bangumi query normalizes to simplified`() {
        assertEquals("葬送的芙莉莲", "葬送的芙莉蓮".toSimplifiedChineseQuery())
    }

    @Test
    fun `already simplified query remains unchanged`() {
        assertEquals("葬送的芙莉莲", "葬送的芙莉莲".toSimplifiedChineseQuery())
    }
}
