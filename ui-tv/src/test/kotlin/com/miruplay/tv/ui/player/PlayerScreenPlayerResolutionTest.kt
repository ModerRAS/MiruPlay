package com.miruplay.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerScreenPlayerResolutionTest {
    @Test
    fun `libvlc host path does not resolve media3 player eagerly`() {
        assertEquals(
            false,
            shouldResolveMedia3Player(shouldShowVlcVideoLayout = true),
        )
    }

    @Test
    fun `standard exo and experimental gl paths still resolve media3 player`() {
        assertEquals(
            true,
            shouldResolveMedia3Player(shouldShowVlcVideoLayout = false),
        )
    }
}
