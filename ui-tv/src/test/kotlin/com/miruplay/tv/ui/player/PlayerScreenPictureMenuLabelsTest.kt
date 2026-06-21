package com.miruplay.tv.ui.player

import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.playbackBackendLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerScreenPictureMenuLabelsTest {
    @Test
    fun `legacy libvlc backend label falls back to standard exo`() {
        assertEquals("标准 Exo", playbackBackendLabel(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC))
    }

    @Test
    fun `tone mapping adjust row labels stay stable`() {
        assertEquals("目标亮度 120 nit", "目标亮度 120 nit")
        assertEquals("对比恢复 8", "对比恢复 8")
        assertEquals("饱和恢复 10", "饱和恢复 10")
        assertEquals("高光压缩 18", "高光压缩 18")
        assertEquals("重置本次播放", "重置本次播放")
    }
}
