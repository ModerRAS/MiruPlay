package com.miruplay.tv.ui.player

import android.view.SurfaceView
import android.view.TextureView
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerViewFrameCaptureTest {
    @Test
    fun `findCapturableVideoView prefers nested texture view`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = FrameLayout(context)
        val content = FrameLayout(context)
        val textureView = TextureView(context)
        val surfaceView = SurfaceView(context)

        content.addView(surfaceView)
        content.addView(textureView)
        root.addView(content)

        val result = findCapturableVideoView(root)

        assertTrue(result is CapturableVideoView.Texture)
        assertEquals(textureView, (result as CapturableVideoView.Texture).view)
    }

    @Test
    fun `findCapturableVideoView falls back to nested surface view`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = FrameLayout(context)
        val content = FrameLayout(context)
        val surfaceView = SurfaceView(context)

        content.addView(surfaceView)
        root.addView(content)

        val result = findCapturableVideoView(root)

        assertTrue(result is CapturableVideoView.Surface)
        assertEquals(surfaceView, (result as CapturableVideoView.Surface).view)
    }
}
