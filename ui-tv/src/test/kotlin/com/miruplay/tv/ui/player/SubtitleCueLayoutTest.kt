@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.ui.player

import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.media3.common.text.Cue
import androidx.media3.common.text.RubySpan
import androidx.media3.common.text.TextAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubtitleCueLayoutTest {

    private fun cue(text: String): Cue = Cue.Builder().setText(text).build()

    private fun cue(
        text: String,
        textSize: Float,
    ): Cue = Cue.Builder()
        .setText(text)
        .setTextSize(textSize, Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING)
        .build()

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
    fun `single cue leaves bottom placement to SubtitleView safe padding`() {
        val result = restackSubtitleCues(listOf(cue("JP")))

        assertEquals(1, result.size)
        assertEquals("JP", result[0].text.toString())
        assertEquals(Cue.DIMEN_UNSET, result[0].line, 0.001f)
        assertEquals(Cue.TYPE_UNSET, result[0].lineType)
        assertEquals(Cue.TYPE_UNSET, result[0].lineAnchor)
    }

    @Test
    fun `two bilingual cues merge into one measured block`() {
        val result = restackSubtitleCues(listOf(cue("JP"), cue("CN")))

        assertEquals(1, result.size)
        assertEquals("JP\nCN", result[0].text.toString())
        assertEquals(Cue.DIMEN_UNSET, result[0].line, 0.001f)
    }

    @Test
    fun `duplicate text layers are shown once within the same dialogue group`() {
        val result = restackSubtitleCues(listOf(cue("JP"), cue("JP"), cue("CN")))

        assertEquals(1, result.size)
        assertEquals("JP\nCN", result.single().text.toString())
    }

    @Test
    fun `merged dialogue retains the highest z index from duplicate layers`() {
        val result = restackSubtitleCues(
            listOf(
                Cue.Builder().setText("JP").setZIndex(2).build(),
                Cue.Builder().setText("JP").setZIndex(7).build(),
                Cue.Builder().setText("CN").setZIndex(3).build(),
            ),
        ).single()

        assertEquals("JP\nCN", result.text.toString())
        assertEquals(7, result.zIndex)
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
        assertEquals(Cue.DIMEN_UNSET, result[1].line, 0.001f)
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
    fun `duplicates beyond four do not hide earlier unique dialogue`() {
        val result = restackSubtitleCues(
            listOf(cue("A"), cue("A"), cue("B"), cue("B"), cue("C"), cue("D"), cue("E")),
        )

        assertEquals("A\nB\nC\nD\nE", result.single().text.toString())
    }

    @Test
    fun `bottom dialogue leaves line unset for SubtitleView safe padding`() {
        val result = restackSubtitleCues(listOf(cue("JP"), cue("CN"))).single()

        assertEquals(Cue.DIMEN_UNSET, result.line, 0f)
        assertEquals(Cue.TYPE_UNSET, result.lineType)
        assertEquals(Cue.TYPE_UNSET, result.lineAnchor)
    }

    @Test
    fun `explicitly positioned text sign keeps dialogue runs separate`() {
        val positioned = Cue.Builder()
            .setText("sign")
            .setPosition(0.2f)
            .setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
            .setLine(0.3f, Cue.LINE_TYPE_FRACTION)
            .setLineAnchor(Cue.ANCHOR_TYPE_MIDDLE)
            .setSize(0.2f)
            .build()

        val result = restackSubtitleCues(listOf(cue("JP"), positioned, cue("CN")))

        assertEquals(3, result.size)
        assertEquals("JP", result[0].text.toString())
        assertSame(positioned, result[1])
        assertEquals("CN", result[2].text.toString())
    }

    @Test
    fun `bitmap cue keeps styled dialogue runs separate and in order`() {
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

        assertEquals(4, result.size)
        assertEquals("JP", result[0].text.toString())
        assertEquals(Typeface.BOLD, (result[0].text as Spanned).getSpans(0, 2, StyleSpan::class.java).single().style)
        assertSame(firstBitmapCue, result[1])
        assertEquals("CN", result[2].text.toString())
        assertSame(secondBitmapCue, result[3])
    }

    @Test
    fun `merged dialogue preserves text size ratios and styled spans`() {
        val jpText = SpannableString("JP").apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val jp = Cue.Builder()
            .setText(jpText)
            .setTextSize(0.06f, Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING)
            .build()
        val cn = cue("CN", 0.045f)

        val result = restackSubtitleCues(listOf(jp, cn)).single()
        val mergedText = result.text as Spanned

        assertEquals(0.06f, result.textSize, 0f)
        assertEquals(Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING, result.textSizeType)
        assertEquals(Typeface.BOLD, mergedText.getSpans(0, 2, StyleSpan::class.java).single().style)
        assertEquals(0.75f, mergedText.getSpans(3, 5, RelativeSizeSpan::class.java).single().sizeChange, 0.0001f)
    }

    @Test
    fun `special cues retain identity`() {
        val bitmap = Cue.Builder()
            .setBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            .build()
        val positioned = positionedDialogue(
            text = "positioned",
            position = 0.2f,
            positionAnchor = Cue.ANCHOR_TYPE_MIDDLE,
            line = 0.3f,
            lineAnchor = Cue.ANCHOR_TYPE_MIDDLE,
        )
        val vertical = Cue.Builder().setText("vertical").setVerticalType(Cue.VERTICAL_TYPE_RL).build()
        val sheared = Cue.Builder().setText("sheared").setShearDegrees(12f).build()
        val windowed = Cue.Builder().setText("windowed").setWindowColor(0xFF000000.toInt()).build()

        val result = restackSubtitleCues(listOf(bitmap, positioned, vertical, sheared, windowed))

        assertSame(bitmap, result[0])
        assertSame(positioned, result[1])
        assertSame(vertical, result[2])
        assertSame(sheared, result[3])
        assertSame(windowed, result[4])
    }

    @Test
    fun `same text with different visual styling remains unchanged`() {
        val white = SpannableString("JP").apply {
            setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val yellow = SpannableString("JP").apply {
            setSpan(ForegroundColorSpan(0xFFFFFF00.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val first = Cue.Builder().setText(white).build()
        val second = Cue.Builder().setText(yellow).build()

        val result = restackSubtitleCues(listOf(first, second))

        assertEquals(2, result.size)
        assertSame(first, result[0])
        assertSame(second, result[1])
    }

    @Test
    fun `same text with different ruby annotations remains unchanged`() {
        val firstText = SpannableString("JP").apply {
            setSpan(RubySpan("first reading", TextAnnotation.POSITION_BEFORE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val secondText = SpannableString("JP").apply {
            setSpan(RubySpan("second reading", TextAnnotation.POSITION_BEFORE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val first = Cue.Builder().setText(firstText).build()
        val second = Cue.Builder().setText(secondText).build()

        val result = restackSubtitleCues(listOf(first, second))

        assertEquals(2, result.size)
        assertSame(first, result[0])
        assertSame(second, result[1])
    }
}
