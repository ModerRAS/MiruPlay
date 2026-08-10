package com.miruplay.tv.player

import androidx.media3.common.Player
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LibassSubtitleRegistryTest {
    @Test
    fun `players retain distinct sessions until release`() {
        val firstPlayer = mockk<Player>()
        val secondPlayer = mockk<Player>()
        val firstSession = LibassSubtitleSession()
        val secondSession = LibassSubtitleSession()

        try {
            LibassSubtitleRegistry.register(firstPlayer, firstSession)
            LibassSubtitleRegistry.register(secondPlayer, secondSession)

            assertSame(firstSession, LibassSubtitleRegistry.sessionFor(firstPlayer))
            assertSame(secondSession, LibassSubtitleRegistry.sessionFor(secondPlayer))
            assertSame(firstSession, LibassSubtitleRegistry.release(firstPlayer))
            assertNull(LibassSubtitleRegistry.sessionFor(firstPlayer))
            assertSame(secondSession, LibassSubtitleRegistry.sessionFor(secondPlayer))
        } finally {
            LibassSubtitleRegistry.release(firstPlayer)?.close()
            LibassSubtitleRegistry.release(secondPlayer)?.close()
            firstSession.close()
            secondSession.close()
        }
    }

    @Test
    fun `replacing a player session closes the old session`() {
        val player = mockk<Player>()
        val oldSession = LibassSubtitleSession()
        val replacement = LibassSubtitleSession()
        assertEquals(1L, oldSession.beginMedia())

        try {
            LibassSubtitleRegistry.register(player, oldSession)
            LibassSubtitleRegistry.register(player, replacement)

            assertEquals(1L, oldSession.beginMedia())
            assertSame(replacement, LibassSubtitleRegistry.sessionFor(player))
        } finally {
            LibassSubtitleRegistry.release(player)?.close()
            oldSession.close()
            replacement.close()
        }
    }
}
