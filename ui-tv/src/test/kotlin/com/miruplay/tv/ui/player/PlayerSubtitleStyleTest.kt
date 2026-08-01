package com.miruplay.tv.ui.player

import android.graphics.Color
import androidx.media3.ui.CaptionStyleCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlayerSubtitleStyleTest {
    @Test
    fun `transparent subtitle style only clears background`() {
        val base = CaptionStyleCompat(
            Color.YELLOW,
            Color.BLACK,
            Color.BLUE,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            Color.RED,
            null,
        )

        val transparent = subtitleCaptionStyle(base, transparentBackground = true)

        assertSame(base, subtitleCaptionStyle(base, transparentBackground = false))
        assertEquals(base.foregroundColor, transparent.foregroundColor)
        assertEquals(Color.TRANSPARENT, transparent.backgroundColor)
        assertEquals(base.windowColor, transparent.windowColor)
        assertEquals(base.edgeType, transparent.edgeType)
        assertEquals(base.edgeColor, transparent.edgeColor)
        assertSame(base.typeface, transparent.typeface)
    }
}
