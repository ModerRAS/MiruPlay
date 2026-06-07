package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DramaModelsTest {
    @Test
    fun `displayTitle prefers title then original title then id`() {
        val titleFirst = DramaSeries(
            id = "show-1",
            title = "Drama Title",
            originalTitle = "Original Title",
        )
        val originalTitleFallback = DramaSeries(
            id = "show-2",
            title = "",
            originalTitle = "Original Title",
        )
        val idFallback = DramaSeries(
            id = "show-3",
            title = "",
            originalTitle = "",
        )

        assertEquals("Drama Title", titleFirst.displayTitle())
        assertEquals("Original Title", originalTitleFallback.displayTitle())
        assertEquals("show-3", idFallback.displayTitle())
    }

}
