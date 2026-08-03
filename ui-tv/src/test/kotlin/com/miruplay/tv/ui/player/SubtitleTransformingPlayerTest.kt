@file:Suppress("UnsafeOptInUsageError", "DEPRECATION")

package com.miruplay.tv.ui.player

import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubtitleTransformingPlayerTest {

    private fun cue(text: String): Cue = Cue.Builder().setText(text).build()

    @Test
    fun `current cues are transformed while presentation time is preserved`() {
        val delegate = mockk<Player>(relaxed = true)
        every { delegate.currentCues } returns CueGroup(
            listOf(cue("JP"), cue("JP"), cue("CN")),
            123_456L,
        )

        val transformed = SubtitleTransformingPlayer(delegate).currentCues

        assertEquals(123_456L, transformed.presentationTimeUs)
        assertEquals("JP\nCN", transformed.cues.single().text.toString())
    }

    @Test
    fun `both cue callbacks are transformed and listener removal is stable`() {
        val delegateListeners = linkedSetOf<Player.Listener>()
        val delegate = mockk<Player>(relaxed = true)
        every { delegate.addListener(any()) } answers {
            delegateListeners += firstArg<Player.Listener>()
        }
        every { delegate.removeListener(any()) } answers {
            delegateListeners -= firstArg<Player.Listener>()
        }
        val listCallbacks = mutableListOf<List<Cue>>()
        val groupCallbacks = mutableListOf<CueGroup>()
        val listener = object : Player.Listener {
            override fun onCues(cues: List<Cue>) {
                listCallbacks += cues
            }

            override fun onCues(cueGroup: CueGroup) {
                groupCallbacks += cueGroup
            }
        }
        val player = SubtitleTransformingPlayer(delegate)

        player.addListener(listener)
        val forwardingListener = delegateListeners.single()
        forwardingListener.onCues(listOf(cue("JP"), cue("JP"), cue("CN")))
        forwardingListener.onCues(
            CueGroup(listOf(cue("line1"), cue("line2")), 789L),
        )

        assertEquals("JP\nCN", listCallbacks.single().single().text.toString())
        assertEquals(789L, groupCallbacks.single().presentationTimeUs)
        assertEquals("line1\nline2", groupCallbacks.single().cues.single().text.toString())

        player.removeListener(listener)
        assertTrue(delegateListeners.isEmpty())
    }

    @Test
    fun `removed listener cannot deliver cues to its replacement`() {
        val delegateListeners = linkedSetOf<Player.Listener>()
        val delegate = mockk<Player>(relaxed = true)
        every { delegate.addListener(any()) } answers {
            delegateListeners += firstArg<Player.Listener>()
        }
        every { delegate.removeListener(any()) } answers {
            delegateListeners -= firstArg<Player.Listener>()
        }
        var oldText = ""
        var replacementText = ""
        val oldListener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                oldText = cueGroup.cues.single().text.toString()
            }
        }
        val replacementListener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                replacementText = cueGroup.cues.single().text.toString()
            }
        }
        val player = SubtitleTransformingPlayer(delegate)

        player.addListener(oldListener)
        val removedForwarder = delegateListeners.single()
        player.removeListener(oldListener)
        player.addListener(replacementListener)

        removedForwarder.onCues(CueGroup(listOf(cue("old")), 1L))

        assertEquals("old", oldText)
        assertEquals("", replacementText)
        assertEquals(1, delegateListeners.size)
    }
}
