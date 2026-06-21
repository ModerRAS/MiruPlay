package com.miruplay.tv.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerViewModelOwnershipTest {
    @Test
    fun `active owner may stop playback`() {
        val owner = Any()

        assertTrue(shouldOwnerStopPlayback(owner, owner))
    }

    @Test
    fun `stale owner may not stop newer playback`() {
        val staleOwner = Any()
        val activeOwner = Any()

        assertFalse(shouldOwnerStopPlayback(activeOwner, staleOwner))
    }
}
