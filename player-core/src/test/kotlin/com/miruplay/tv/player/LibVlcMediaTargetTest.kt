package com.miruplay.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibVlcMediaTargetTest {
    @Test
    fun `resolver uses local path target for bare absolute android path`() {
        val target = resolveLibVlcMediaTarget("/sdcard/Movies/MiruPlayHdrTest/probe_30s_1080p_hdr.mp4")

        assertTrue(target is LibVlcMediaTarget.LocalPath)
        assertEquals(
            "/sdcard/Movies/MiruPlayHdrTest/probe_30s_1080p_hdr.mp4",
            (target as LibVlcMediaTarget.LocalPath).path,
        )
    }

    @Test
    fun `resolver uses local path target for file uri`() {
        val target = resolveLibVlcMediaTarget("file:///sdcard/Movies/MiruPlayHdrTest/probe_30s_1080p_hdr.mp4")

        assertTrue(target is LibVlcMediaTarget.LocalPath)
        assertEquals(
            "/sdcard/Movies/MiruPlayHdrTest/probe_30s_1080p_hdr.mp4",
            (target as LibVlcMediaTarget.LocalPath).path,
        )
    }

    @Test
    fun `resolver keeps http uri as location target`() {
        val target = resolveLibVlcMediaTarget("http://10.137.32.158:19798/dav/library/1.mp4")

        assertTrue(target is LibVlcMediaTarget.Location)
        assertEquals(
            "http://10.137.32.158:19798/dav/library/1.mp4",
            (target as LibVlcMediaTarget.Location).uri,
        )
    }
}
