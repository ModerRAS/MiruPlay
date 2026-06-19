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
}
