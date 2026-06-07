package com.miruplay.tv.repository

import com.miruplay.tv.model.MediaContentMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppModeContentModeTest {
    @Test
    fun `anime mode maps to anime source content mode`() {
        assertEquals(MediaContentMode.ANIME, AppMode.ANIME.toMediaContentMode())
    }

    @Test
    fun `drama mode maps to drama source content mode`() {
        assertEquals(MediaContentMode.DRAMA, AppMode.DRAMA.toMediaContentMode())
    }
}
