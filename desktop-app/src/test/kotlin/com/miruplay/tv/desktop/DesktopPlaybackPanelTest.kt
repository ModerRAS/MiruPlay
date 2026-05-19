package com.miruplay.tv.desktop

import com.miruplay.tv.player.mpv.RifeBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPlaybackPanelTest {
    @Test
    fun `desktop RIFE is opt in by default`() {
        assertFalse(DEFAULT_DESKTOP_RIFE_ENABLED)
    }

    @Test
    fun `desktop player chrome derives a TV style title from media path`() {
        assertEquals("选择媒体", desktopPlaybackTitle(""))
        assertEquals("Frieren - S01E02", desktopPlaybackTitle("D:/Anime/Frieren - S01E02.mkv"))
    }

    @Test
    fun `desktop player source line exposes mpv and RIFE state`() {
        val line = desktopPlaybackSourceLine(
            mediaPath = "https://example.test/video.mkv",
            rifeEnabled = true,
            rifeBackend = RifeBackend.DIRECTML,
            isPlayerActive = true,
        )

        assertTrue(line.contains("远程串流"))
        assertTrue(line.contains("mpv 运行中"))
        assertTrue(line.contains("RIFE DIRECTML"))
    }

    @Test
    fun `desktop player start position formats seconds for the timeline`() {
        assertEquals("00:00", desktopPlaybackStartPositionLabel(""))
        assertEquals("01:30", desktopPlaybackStartPositionLabel("90"))
    }
}
