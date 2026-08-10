@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.ui.player

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import com.miruplay.tv.player.LibassSubtitleSession
import com.miruplay.tv.player.LibassSubtitleSurfaceView
import com.miruplay.tv.ui.tv.R
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibassSubtitleOverlayLifecycleTest {
    @Test
    fun `player content frame owns one overlay and keeps Media3 subtitles`() {
        val view = inflatePlayerView()

        val first = installLibassSubtitleOverlay(view)
        val second = installLibassSubtitleOverlay(view)
        val content = view.findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_content_frame)

        assertSame(first, second)
        assertEquals(
            1,
            (0 until content.childCount)
                .map(content::getChildAt)
                .count { it is LibassSubtitleSurfaceView },
        )
        assertNotNull(view.subtitleView)
    }

    @Test
    fun `releasing player view unbinds and removes overlay`() {
        val view = inflatePlayerView()
        val session = mockk<LibassSubtitleSession>(relaxed = true)
        installLibassSubtitleOverlay(view).bind(session)

        releasePlayerView(view, view)

        verify(exactly = 1) { session.unbindSurface(null) }
        val content = view.findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_content_frame)
        assertEquals(
            0,
            (0 until content.childCount)
                .map(content::getChildAt)
                .count { it is LibassSubtitleSurfaceView },
        )
        assertNotNull(view.subtitleView)
    }

    private fun inflatePlayerView(): PlayerView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return LayoutInflater.from(context)
            .inflate(R.layout.player_view_surface, null, false) as PlayerView
    }
}
