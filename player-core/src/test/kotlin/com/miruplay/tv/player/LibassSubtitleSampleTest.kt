package com.miruplay.tv.player

import androidx.media3.common.Format
import java.util.zip.Deflater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibassSubtitleSampleTest {

    // Media3 1.8 MatroskaExtractor emits every ASS event as an 11-field Dialogue line
    // (SSA_DIALOGUE_FORMAT "Format: Start, End, ReadOrder, Layer, Style, Name, MarginL,
    // MarginR, MarginV, Effect, Text"): the extractor prepends "Dialogue: <Start>,<End>,"
    // to the mkvmerge block content "<ReadOrder>,<Layer>,<Style>,<Name>,<MarginL>,<MarginR>,
    // <MarginV>,<Effect>,<Text>". Start is the block placeholder 0:00:00:00 and End carries
    // the block duration. Text may contain commas.

    @Test
    fun `standard media3 11-field event becomes an absolute ass event`() {
        val sample = media3Sample(
            readOrder = "17",
            layer = "4",
            style = "Sign",
            name = "Actor",
            marginL = "0000",
            marginR = "0000",
            marginV = "0000",
            text = "{\\pos(300,200)\\fs34\\bord3\\c&H33AAFF&}LV999, \u6751\u6c11",
        )

        val payload = decodeLibassPayload(sample.toByteArray(), sampleTimeUs = 7_070_000L)

        assertEquals(
            LibassPayload.Event(
                "Dialogue: 4,0:00:07.07,0:00:09.57,Sign,Actor,0000,0000,0000,," +
                    "{\\pos(300,200)\\fs34\\bord3\\c&H33AAFF&}LV999, \u6751\u6c11",
            ),
            payload,
        )
    }

    @Test
    fun `media3 stream offset is removed before decoding event time`() {
        val sample = media3Sample(
            readOrder = "17",
            layer = "4",
            style = "Sign",
            name = "Actor",
            marginL = "0000",
            marginR = "0000",
            marginV = "0000",
            text = "{\\pos(300,200)\\fs34\\bord3\\c&H33AAFF&}LV999, \u6751\u6c11",
        )

        val payload = decodeLibassPayload(
            sample.toByteArray(),
            relativeLibassSampleTimeUs(
                sampleTimeUs = 1_000_007_070_000L,
                streamOffsetUs = 1_000_000_000_000L,
            ),
        )

        assertEquals(
            LibassPayload.Event(
                "Dialogue: 4,0:00:07.07,0:00:09.57,Sign,Actor,0000,0000,0000,," +
                    "{\\pos(300,200)\\fs34\\bord3\\c&H33AAFF&}LV999, \u6751\u6c11",
            ),
            payload,
        )
    }

    @Test
    fun `text field containing commas is never truncated`() {
        val text = "LV999, \u6751\u6c11 \u3068\u5263, \u9b54\u6cd5\u306e\u4e16\u754c \u2014\u2014 \u53cc\u8bed\u6587\u6848, \u542b\u9017\u53f7"
        val sample = media3Sample(
            readOrder = "1",
            layer = "6",
            style = "Text CN",
            name = "Narrator",
            text = text,
        )

        val payload = decodeLibassPayload(sample.toByteArray(), sampleTimeUs = 7_070_000L)

        assertEquals(
            LibassPayload.Event(
                "Dialogue: 6,0:00:07.07,0:00:09.57,Text CN,Narrator,0,0,0,,$text",
            ),
            payload,
        )
    }

    @Test
    fun `marked variant without readorder column is decoded`() {
        // SSA v4 "Marked" / legacy blocks omit the ReadOrder column: the body starts at the
        // Marked-or-Layer value (10 fields instead of 11). This was rejected before the fix.
        val sample =
            "Dialogue: 0:00:00:00,0:00:02:50,Marked=0,Text CN,Narrator,0,0,0,,\u5263\u4e0e\u9b54\u6cd5\u7684\u4e16\u754c\u300a\u963f\u65af\u514b\u5229\u4e9a\u300b"

        val payload = decodeLibassPayload(sample.toByteArray(), sampleTimeUs = 7_070_000L)

        assertEquals(
            LibassPayload.Event(
                "Dialogue: Marked=0,0:00:07.07,0:00:09.57,Text CN,Narrator,0,0,0,," +
                    "\u5263\u4e0e\u9b54\u6cd5\u7684\u4e16\u754c\u300a\u963f\u65af\u514b\u5229\u4e9a\u300b",
            ),
            payload,
        )
    }

    @Test
    fun `bilingual layer 5 and 6 events keep their layer ordering`() {
        // Real LV999 bilingual track: the same window carries a layer-6 Chinese line and a
        // layer-5 Japanese line. Both must survive with their Layer value intact so libass
        // can z-order them.
        val sampleTimeUs = 1_610_000L

        val cn = decodeLibassPayload(
            media3Sample(
                readOrder = "1",
                layer = "6",
                style = "Text CN",
                name = "Narrator",
                end = "0:00:03:50",
                text = "\u5263\u4e0e\u9b54\u6cd5\u7684\u4e16\u754c\u300a\u963f\u65af\u514b\u5229\u4e9a\u300b",
            ).toByteArray(),
            sampleTimeUs = sampleTimeUs,
        )
        val jp = decodeLibassPayload(
            media3Sample(
                readOrder = "2",
                layer = "5",
                style = "Text JP",
                name = "Narrator",
                end = "0:00:03:50",
                text = "\u5263\u3068\u9b54\u6cd5\u306e\u4e16\u754c \u300e\u30a2\u30fc\u30b9\u30af\u30ea\u30a2\u300f",
            ).toByteArray(),
            sampleTimeUs = sampleTimeUs,
        )

        assertEquals(
            LibassPayload.Event(
                "Dialogue: 6,0:00:01.61,0:00:05.11,Text CN,Narrator,0,0,0,," +
                    "\u5263\u4e0e\u9b54\u6cd5\u7684\u4e16\u754c\u300a\u963f\u65af\u514b\u5229\u4e9a\u300b",
            ),
            cn,
        )
        assertEquals(
            LibassPayload.Event(
                "Dialogue: 5,0:00:01.61,0:00:05.11,Text JP,Narrator,0,0,0,," +
                    "\u5263\u3068\u9b54\u6cd5\u306e\u4e16\u754c \u300e\u30a2\u30fc\u30b9\u30af\u30ea\u30a2\u300f",
            ),
            jp,
        )
    }

    @Test
    fun `full ass document is preserved byte for byte`() {
        val document = """
            [Script Info]
            ScriptType: v4.00+
            PlayResX: 1920
            PlayResY: 1080

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\fs38}Hello
        """.trimIndent().toByteArray()

        val payload = decodeLibassPayload(document, sampleTimeUs = 0L)

        assertTrue(payload is LibassPayload.Document)
        assertArrayEquals(document, (payload as LibassPayload.Document).bytes)
    }

    @Test
    fun `protected zlib event keeps authored ass fields`() {
        val prefix = "Dialogue: 0:00:00:00,0:00:02:50,".toByteArray()
        val protected = protectSubtitleZlibBytes(deflate(EVENT_BODY.toByteArray()))

        val payload = decodeLibassPayload(prefix + protected, sampleTimeUs = 7_070_000L)

        assertEquals(
            LibassPayload.Event(
                "Dialogue: 4,0:00:07.07,0:00:09.57,Sign,Actor,0000,0000,0000,," +
                    "{\\pos(300,200)\\fs34\\bord3\\c&H33AAFF&}LV999, \u6751\u6c11",
            ),
            payload,
        )
    }

    @Test
    fun `malformed media3 event is rejected`() {
        assertNull(
            decodeLibassPayload(
                "Dialogue: 0:00:00:00,0:00:02:50,too,few,fields".toByteArray(),
                sampleTimeUs = 7_070_000L,
            ),
        )
    }

    @Test
    fun `negative relative duration is rejected`() {
        val sample = media3Sample(start = "0:00:03:00", end = "0:00:02:50")

        assertNull(decodeLibassPayload(sample.toByteArray(), sampleTimeUs = 7_070_000L))
    }

    @Test
    fun `codec private header comes from media3 initialization data`() {
        val dialogueFormat = "Format: Start, End, Style, Text".toByteArray()
        val header = "[Script Info]\nScriptType: v4.00+\n[V4+ Styles]\n".toByteArray()
        val format = Format.Builder()
            .setInitializationData(listOf(dialogueFormat, header))
            .build()

        assertArrayEquals(
            header + (
                "[Events]\n" +
                    "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
                ).toByteArray(),
            assHeaderFrom(format),
        )
    }

    private fun media3Sample(
        start: String = "0:00:00:00",
        end: String = "0:00:02:50",
        readOrder: String = "1",
        layer: String = "0",
        style: String = "Default",
        name: String = "",
        marginL: String = "0",
        marginR: String = "0",
        marginV: String = "0",
        effect: String = "",
        text: String = "",
    ): String = "Dialogue: $start,$end,$readOrder,$layer,$style,$name,$marginL,$marginR,$marginV,$effect,$text"

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater()
        return try {
            deflater.setInput(input)
            deflater.finish()
            val output = ByteArray(input.size + 64)
            output.copyOf(deflater.deflate(output))
        } finally {
            deflater.end()
        }
    }

    private companion object {
        // mkvmerge-style block content: ReadOrder, Layer, Style, Name, MarginL, MarginR,
        // MarginV, Effect, Text.
        const val EVENT_BODY =
            "17,4,Sign,Actor,0000,0000,0000,," +
                "{\\pos(300,200)\\fs34\\bord3\\c&H33AAFF&}LV999, \u6751\u6c11"
    }
}
