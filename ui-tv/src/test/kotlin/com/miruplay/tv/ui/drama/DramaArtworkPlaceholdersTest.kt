package com.miruplay.tv.ui.drama

import org.junit.Assert.assertEquals
import org.junit.Test

class DramaArtworkPlaceholdersTest {
    @Test
    fun `monogram prefers first two cjk characters`() {
        assertEquals("医馆", dramaArtworkMonogram("医馆笑传"))
        assertEquals("迷糊", dramaArtworkMonogram("WWW.迷糊餐厅"))
    }

    @Test
    fun `monogram falls back to latin initials and tv default`() {
        assertEquals("HM", dramaArtworkMonogram("House M.D."))
        assertEquals("TV", dramaArtworkMonogram("   "))
    }
}
