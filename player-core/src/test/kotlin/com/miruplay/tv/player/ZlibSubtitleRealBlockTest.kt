package com.miruplay.tv.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ZlibSubtitleRealBlockTest {

    @Test
    fun `escaper protects real truncated lv999 wipe block`() {
        val block = REAL_WIPE_BLOCK.hexBytes()

        val protected = protectSubtitleZlibBytes(block)

        // Equal length (container offsets intact) and no NUL bytes left.
        assertEquals(block.size, protected.size)
        assertEquals(-1, protected.indexOfFirst { it == 0.toByte() })
    }

    @Test
    fun `protected real wipe block inflates through sample path`() {
        val block = REAL_WIPE_BLOCK.hexBytes()
        val protected = protectSubtitleZlibBytes(block)
        val prefix = "Dialogue: 0:00:00:00,0:00:00:30,".toByteArray()

        val inflated = inflateSubtitleSampleIfNeeded(prefix + protected)

        val text = inflated.toString(Charsets.UTF_8)
        assertEquals(
            "Dialogue: 1,0:00:29.97,0:00:30.27,OPJP,,0,0,0,fx," +
                "{\\an5\\pos(960,39)\\clip(741,0,741,1080)" +
                "\\t(20,240,\\clip(741,0,1179,1080))}Not a Hero \u305d\u308c\u3067\u3082",
            decodeLibassPayload(inflated, sampleTimeUs = 29_970_000L)
                .let { (it as LibassPayload.Event).dialogueLine },
        )
        assertEquals(prefix.size + 116, inflated.size)
    }

    @Test
    fun `block holding all four sentinels documents the residual skip`() {
        // S01E02 @56.41s CN wipe: its deflate stream already holds FF/FE/FD/FC, the only
        // four FDICT=0 flag mappings, so no equal-length NUL escape exists. The escaper
        // must leave it untouched (never corrupt) and the sample path skips it.
        val block = REAL_ALL_SENTINEL_BLOCK.hexBytes()

        assertArrayEquals(block, protectSubtitleZlibBytes(block))
        val prefix = "Dialogue: 0:00:00:00,0:00:00:30,".toByteArray()
        val truncated = prefix + block.copyOfRange(0, block.indexOfFirst { it == 0.toByte() })
        assertArrayEquals(truncated, inflateSubtitleSampleIfNeeded(truncated))
        assertEquals(null, decodeLibassPayload(truncated, sampleTimeUs = 56_410_000L))
    }

    @Test
    fun `truncated real wipe block documents the nul skip`() {
        // What Media3 delivers today: prefix + body cut at the first NUL (offset 15).
        val block = REAL_WIPE_BLOCK.hexBytes()
        val prefix = "Dialogue: 0:00:00:00,0:00:00:30,".toByteArray()
        val truncated = prefix + block.copyOfRange(0, 15)

        // Truncated stream cannot inflate: returned unchanged, then rejected as malformed.
        assertArrayEquals(truncated, inflateSubtitleSampleIfNeeded(truncated))
        assertEquals(null, decodeLibassPayload(truncated, sampleTimeUs = 29_970_000L))
    }

    private fun String.hexBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        // Raw Matroska block (track 3, pts 56410ms) whose deflate stream already holds
        // FF/FE/FD/FC. Inflated: "763,1,OPCN,,0,0,0,fx,{\an5\pos(960,1031)
        // \clip(603,0,603,1080)\t(20,240,\clip(603,0,1318,1080))}基本靠着连不可能都不放棄的信念"
        // (143 bytes).
        const val REAL_ALL_SENTINEL_BLOCK =
            "78da333733d631d4f10f70f6d3d13100c3b40a9dea98c43cd39882fc620d4b33031d43036343cd98e49ccc020d330363a00a1069686061a01953a26164a0636462a0832c6d686c680191d7ac7d3a7fd7b3396b5ece5df07c6ec38bfdf39eece87ddabffe45f3de97cd7b9fef9e0be43e9bb2efe99ee6e7b35a9eec5ff874ff560039763a42"

        // Raw Matroska block bytes (track 3, pts 29970ms) from LV999 S01E02 ASSx2.
        // Inflated: "725,1,OPJP,,0,0,0,fx,{\an5\pos(960,39)\clip(741,0,741,1080)
        //   \t(20,240,\clip(741,0,1179,1080))}Not a Hero それでも" (116 bytes).
        const val REAL_WIPE_BLOCK =
            "78da333732d531d4f10ff00ad0d13100c3b40a9dea98c43cd39882fc620d4b33031d634bcd98e49ccc020d731343a03c883434b030d08c29d13032d0313231d04196363434b784c86bd6fae59728242a78a416e52b3c6e9cfbb8a9e771e3f2c74d4d00e3f72284"
    }
}
