@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.ui.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerViewSubtitleLifecycleTest {

    @Test
    fun `releasing old view detaches player without clearing replacement reference`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val oldView = PlayerView(context)
        val replacement = PlayerView(context)
        val player = ExoPlayer.Builder(context).build()

        try {
            oldView.player = player

            val current = releasePlayerView(oldView, replacement)

            assertNull(oldView.player)
            assertSame(replacement, current)
        } finally {
            oldView.player = null
            replacement.player = null
            player.release()
        }
    }

    @Test
    fun `releasing current view clears its reference`() {
        val current = PlayerView(ApplicationProvider.getApplicationContext())

        assertNull(releasePlayerView(current, current))
    }

    @Test
    fun `subtitle padding follows control visibility`() {
        assertEquals(
            SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION,
            subtitleBottomPaddingFraction(controlsVisible = false),
            0f,
        )
        assertEquals(
            0.20f,
            subtitleBottomPaddingFraction(controlsVisible = true),
            0f,
        )
    }
}
