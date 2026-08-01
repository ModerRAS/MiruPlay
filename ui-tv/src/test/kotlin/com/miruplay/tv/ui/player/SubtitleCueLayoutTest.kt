@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.ui.player

import androidx.media3.common.text.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCueLayoutTest {

    private fun cue(text: String): Cue = Cue.Builder().setText(text).build()

    @Test
    fun `empty cues returns empty list`() {
        assertTrue(restackSubtitleCues(emptyList()).isEmpty())
    }

    @Test
    fun `single cue is placed one line above the bottom`() {
        val result = restackSubtitleCues(listOf(cue("JP")))

        assertEquals(1, result.size)
        assertEquals("JP", result[0].text.toString())
        assertEquals(-1f, result[0].line, 0.001f)
        assertEquals(Cue.LINE_TYPE_NUMBER, result[0].lineType)
        assertEquals(Cue.ANCHOR_TYPE_END, result[0].lineAnchor)
        // Horizontal position/size cleared so the cue centres and never overflows.
        assertEquals(Cue.DIMEN_UNSET, result[0].position, 0.001f)
        assertEquals(Cue.TYPE_UNSET, result[0].positionAnchor)
        assertEquals(Cue.DIMEN_UNSET, result[0].size, 0.001f)
    }

    @Test
    fun `two bilingual cues stack vertically without overlapping`() {
        val result = restackSubtitleCues(listOf(cue("JP"), cue("CN")))

        assertEquals(2, result.size)
        // First cue sits at the bottom (-1), second cue directly above it (-2).
        assertEquals(-1f, result[0].line, 0.001f)
        assertEquals(-2f, result[1].line, 0.001f)
        assertEquals("JP", result[0].text.toString())
        assertEquals("CN", result[1].text.toString())
    }

    @Test
    fun `cues beyond the cap are dropped keeping the most recent`() {
        val cues = (1..6).map { cue("line$it") }

        val result = restackSubtitleCues(cues)

        assertEquals(MAX_STACKED_SUBTITLE_CUES, result.size)
        // The last 4 cues are retained and stacked bottom-up.
        assertEquals("line3", result[0].text.toString())
        assertEquals("line6", result[3].text.toString())
        assertEquals(-1f, result[0].line, 0.001f)
        assertEquals(-4f, result[3].line, 0.001f)
    }

    @Test
    fun `embedded off-screen positions are cleared to prevent overflow`() {
        // A cue positioned beyond the right edge (position > 1) would render off-screen.
        val positioned = Cue.Builder()
            .setText("sign")
            .setPosition(1.5f)
            .setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
            .setSize(0.2f)
            .build()

        val result = restackSubtitleCues(listOf(positioned))

        assertEquals(1, result.size)
        assertEquals(Cue.DIMEN_UNSET, result[0].position, 0.001f)
        assertEquals(Cue.TYPE_UNSET, result[0].positionAnchor)
        assertEquals(Cue.DIMEN_UNSET, result[0].size, 0.001f)
    }

    @Test
    fun `original text is preserved for every cue`() {
        val cues = listOf(cue("你好"), cue("こんにちは"))

        val result = restackSubtitleCues(cues)

        assertEquals(listOf("你好", "こんにちは"), result.map { it.text.toString() })
    }
}
