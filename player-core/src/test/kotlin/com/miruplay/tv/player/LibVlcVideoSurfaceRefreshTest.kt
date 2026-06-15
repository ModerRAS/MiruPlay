package com.miruplay.tv.player

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.videolan.libvlc.MediaPlayer

class LibVlcVideoSurfaceRefreshTest {
    @Test
    fun `refresh helper updates libvlc video surfaces when player exists`() {
        val player = mockk<MediaPlayer>(relaxed = true)

        refreshVlcVideoSurfacesForTest(player)

        verify(exactly = 1) { player.updateVideoSurfaces() }
    }
}
