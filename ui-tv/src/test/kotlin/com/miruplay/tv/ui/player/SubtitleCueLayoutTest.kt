@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.ui.player

import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.media3.common.text.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubtitleCueLayoutTest {

    private fun cue(text: String): Cue = Cue.Builder().setText(text).build()

    private fun positionedDialogue(
        text: String,
        position: Float,
        positionAnchor: Int,
        line: Float,
        lineAnchor: Int,
    ): Cue = Cue.Builder()
        .setText(text)
        .setPosition(position)
        .setPositionAnchor(positionAnchor)
        .setLine(line, Cue.LINE_TYPE_FRACTION)
        .setLineAnchor(lineAnchor)
        .build()

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
    fun `two bilingual cues merge into one measured block`() {
        val result = restackSubtitleCues(listOf(cue("JP"), cue("CN")))

        assertEquals(1, result.size)
        assertEquals("JP\nCN", result[0].text.toString())
        assertEquals(-1f, result[0].line, 0.001f)
    }

    @Test
    fun `duplicate text layers are shown once within the same dialogue group`() {
        val result = restackSubtitleCues(listOf(cue("JP"), cue("JP"), cue("CN")))

        assertEquals(1, result.size)
        assertEquals("JP\nCN", result.single().text.toString())
    }

    @Test
    fun `dialogue groups remain separate by vertical and horizontal region`() {
        val result = restackSubtitleCues(
            listOf(
                positionedDialogue("top left", 0.05f, Cue.ANCHOR_TYPE_START, 0.05f, Cue.ANCHOR_TYPE_START),
                positionedDialogue("top left 2", 0.05f, Cue.ANCHOR_TYPE_START, 0.05f, Cue.ANCHOR_TYPE_START),
                positionedDialogue("bottom centre", 0.5f, Cue.ANCHOR_TYPE_MIDDLE, 0.95f, Cue.ANCHOR_TYPE_END),
                positionedDialogue("bottom right", 0.95f, Cue.ANCHOR_TYPE_END, 0.95f, Cue.ANCHOR_TYPE_END),
            ),
        )

        assertEquals(3, result.size)
        assertEquals("top left\ntop left 2", result[0].text.toString())
        assertEquals(0.05f, result[0].position, 0.001f)
        assertEquals(Cue.ANCHOR_TYPE_START, result[0].positionAnchor)
        assertEquals("bottom centre", result[1].text.toString())
        assertEquals(-1f, result[1].line, 0.001f)
        assertEquals("bottom right", result[2].text.toString())
        assertEquals(0.95f, result[2].position, 0.001f)
        assertEquals(Cue.ANCHOR_TYPE_END, result[2].positionAnchor)
    }

    @Test
    fun `multiline cue and sibling cue cannot occupy separate overlapping bands`() {
        val result = restackSubtitleCues(listOf(cue("JP line 1\nJP line 2"), cue("CN")))

        assertEquals(1, result.size)
        assertEquals("JP line 1\nJP line 2\nCN", result[0].text.toString())
    }

    @Test
    fun `cues beyond the cap are dropped keeping the most recent`() {
        val cues = (1..6).map { cue("line$it") }

        val result = restackSubtitleCues(cues)

        assertEquals(1, result.size)
        assertEquals("line3\nline4\nline5\nline6", result[0].text.toString())
        assertEquals(-1f, result[0].line, 0.001f)
    }

    @Test
    fun `explicitly positioned text sign is preserved beside merged dialogue`() {
        val positioned = Cue.Builder()
            .setText("sign")
            .setPosition(0.2f)
            .setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
            .setLine(0.3f, Cue.LINE_TYPE_FRACTION)
            .setLineAnchor(Cue.ANCHOR_TYPE_MIDDLE)
            .setSize(0.2f)
            .build()

        val result = restackSubtitleCues(listOf(cue("JP"), positioned, cue("CN")))

        assertEquals(2, result.size)
        assertEquals("JP\nCN", result[0].text.toString())
        assertSame(positioned, result[1])
    }

    @Test
    fun `merge preserves styled text and bitmap cue order`() {
        val styledText = SpannableString("JP").apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val textCue = Cue.Builder().setText(styledText).build()
        val firstBitmapCue = Cue.Builder()
            .setBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            .build()
        val secondBitmapCue = Cue.Builder()
            .setBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            .build()

        val result = restackSubtitleCues(listOf(textCue, firstBitmapCue, cue("CN"), secondBitmapCue))

        assertEquals(3, result.size)
        assertEquals("JP\nCN", result[0].text.toString())
        assertEquals(Typeface.BOLD, (result[0].text as Spanned).getSpans(0, 2, StyleSpan::class.java).single().style)
        assertSame(firstBitmapCue, result[1])
        assertSame(secondBitmapCue, result[2])
    }
}
