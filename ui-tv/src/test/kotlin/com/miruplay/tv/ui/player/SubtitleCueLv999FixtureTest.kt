@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.ui.player

import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.ssa.SsaParser
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubtitleCueLv999FixtureTest {

    @Test
    fun `LV999 dialogue keeps all unique lines and font ratios while positioned sign keeps ASS placement`() {
        val frames = parseFixture()

        val fourDialogueCues = frameAt(frames, 47_190_000)
        assertEquals(4, fourDialogueCues.size)
        assertEquals(listOf(0.5f, 0.5f, 0.5f, 0.5f), fourDialogueCues.map(Cue::position))
        assertEquals(listOf(0.95f, 0.95f, 0.95f, 0.95f), fourDialogueCues.map(Cue::line))
        val fourDialogue = restackSubtitleCues(fourDialogueCues).single()
        assertEquals("First dialogue JP\nFirst dialogue CN\nSecond dialogue JP\nSecond dialogue CN", fourDialogue.text.toString())
        assertEquals(0.75f, (fourDialogue.text as Spanned)
            .getSpans(18, 35, RelativeSizeSpan::class.java)
            .single()
            .sizeChange, 0.0001f)

        val dialogueAndSign = frameAt(frames, 108_000_000)
        assertEquals(3, dialogueAndSign.size)
        val positionedSign = dialogueAndSign.single { it.text.toString() == "LV999 guild notice" }
        assertEquals(340f / 1920f, positionedSign.position, 0.0001f)
        assertEquals(240f / 1080f, positionedSign.line, 0.0001f)

        val restackedWithSign = restackSubtitleCues(dialogueAndSign)
        assertEquals(2, restackedWithSign.size)
        assertEquals("Dialogue JP\nDialogue CN", restackedWithSign[0].text.toString())
        assertSame(positionedSign, restackedWithSign[1])

        val nextDialogue = restackSubtitleCues(frameAt(frames, 110_500_000))
        assertEquals(1, nextDialogue.size)
        assertEquals("Next dialogue JP\nNext dialogue CN", nextDialogue.single().text.toString())
    }

    private fun parseFixture(): List<CuesWithTiming> {
        val ass = """
            [Script Info]
            PlayResX: 1920
            PlayResY: 1080

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: DialogueJP,Arial,50,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,0,2,20,20,40,1
            Style: DialogueCN,Arial,37.5,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,0,2,20,20,40,1
            Style: Sign,Arial,36,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,0,5,20,20,20,1

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 5,0:00:46.90,0:00:47.60,DialogueJP,,40,900,40,,First dialogue JP
            Dialogue: 6,0:00:46.90,0:00:47.60,DialogueCN,,900,40,40,,First dialogue CN
            Dialogue: 5,0:00:47.00,0:00:47.50,DialogueJP,,60,880,80,,Second dialogue JP
            Dialogue: 6,0:00:47.00,0:00:47.50,DialogueCN,,880,60,80,,Second dialogue CN
            Dialogue: 5,0:01:47.90,0:01:48.30,DialogueJP,,40,900,40,,Dialogue JP
            Dialogue: 6,0:01:47.90,0:01:48.30,DialogueCN,,900,40,40,,Dialogue CN
            Dialogue: 5,0:01:47.50,0:01:48.60,Sign,,0,0,0,,{\pos(340,240)}LV999 guild notice
            Dialogue: 5,0:01:50.40,0:01:50.80,DialogueJP,,40,900,40,,Next dialogue JP
            Dialogue: 6,0:01:50.40,0:01:50.80,DialogueCN,,900,40,40,,Next dialogue CN
        """.trimIndent().toByteArray()
        return buildList {
            SsaParser().parse(ass, SubtitleParser.OutputOptions.allCues()) { add(it) }
        }
    }

    private fun frameAt(frames: List<CuesWithTiming>, positionUs: Long): List<Cue> =
        frames.single { positionUs >= it.startTimeUs && positionUs < it.endTimeUs }.cues
}
