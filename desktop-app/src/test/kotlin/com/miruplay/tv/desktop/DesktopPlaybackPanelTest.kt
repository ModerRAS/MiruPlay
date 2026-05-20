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
    fun `desktop player controls use TV facing labels`() {
        val labels = desktopPlaybackUiLabels()

        assertEquals("媒体 URI 或文件路径", labels.mediaPath)
        assertEquals("起播秒数", labels.startSeconds)
        assertEquals("外挂字幕路径", labels.subtitlePath)
        assertEquals("全屏", labels.fullscreen)
        assertEquals("播完保留窗口", labels.keepOpen)
        assertEquals("RIFE", labels.rife)
    }

    @Test
    fun `desktop player stage uses localized mpv chips`() {
        assertEquals("mpv 待命", desktopPlaybackStatusChip(isPlayerActive = false))
        assertEquals("mpv 播放中", desktopPlaybackStatusChip(isPlayerActive = true))
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
    fun `desktop player status text localizes backend statuses`() {
        assertEquals("mpv 待命。", desktopPlaybackStatusText("mpv is idle."))
        assertEquals("mpv 已启动：pid 1234", desktopPlaybackStatusText("mpv launched: pid 1234"))
        assertEquals("已后退 10 秒。", desktopPlaybackStatusText("mpv seeked back 10s."))
        assertEquals("已快进 30 秒。", desktopPlaybackStatusText("mpv seeked forward 30s."))
        assertEquals("播放进度已同步至 02:03。", desktopPlaybackStatusText("mpv position synced at 02:03."))
        assertEquals("custom status", desktopPlaybackStatusText("custom status"))
    }

    @Test
    fun `desktop player start position formats seconds for the timeline`() {
        assertEquals("00:00", desktopPlaybackStartPositionLabel(""))
        assertEquals("01:30", desktopPlaybackStartPositionLabel("90"))
    }
}
