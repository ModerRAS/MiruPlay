package com.miruplay.tv.player

import androidx.media3.common.Format
import java.util.zip.Deflater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibassSubtitleSampleTest {

    @Test
    fun `media3 matroska sample becomes an absolute ass event`() {
        val sample = media3Prefix(duration = "0:00:02:50") + EVENT_BODY

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
        val sample = media3Prefix(duration = "0:00:02:50") + EVENT_BODY

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
                    "{\\pos(300,200)\\fs34\\bord3\\c&H33AAFF&}LV999, 村民",
            ),
            payload,
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
        val prefix = media3Prefix(duration = "0:00:02:50").toByteArray()
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
        val sample = media3Prefix(start = "0:00:03:00", duration = "0:00:02:50") + EVENT_BODY

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

    private fun media3Prefix(
        start: String = "0:00:00:00",
        duration: String,
    ): String = "Dialogue: $start,$duration,"

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
        const val EVENT_BODY =
            "17,4,Sign,Actor,0000,0000,0000,," +
                "{\\pos(300,200)\\fs34\\bord3\\c&H33AAFF&}LV999, \u6751\u6c11"
    }
}
