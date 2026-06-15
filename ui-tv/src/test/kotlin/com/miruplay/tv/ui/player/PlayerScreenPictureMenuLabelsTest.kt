package com.miruplay.tv.ui.player

import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.playbackBackendLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerScreenPictureMenuLabelsTest {
    @Test
    fun `libvlc backend label is shown in picture menu`() {
        assertEquals("实验 VLC", playbackBackendLabel(PlaybackRenderBackend.EXPERIMENTAL_LIBVLC))
    }
}
