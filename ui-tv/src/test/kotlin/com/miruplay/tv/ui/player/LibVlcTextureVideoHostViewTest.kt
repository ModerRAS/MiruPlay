package com.miruplay.tv.ui.player

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.mockk
import io.mockk.unmockkStatic
import io.mockk.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibVlcTextureVideoHostViewTest {
    @Test
    fun `host keeps debug overlay texture detached from libvlc managed texture binding`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostView = LibVlcTextureVideoHostView(context)

        val textureView = hostView.debugOverlayTextureViewForTest()

        assertNotNull(textureView)
        assertFalse(textureView is SurfaceView)
        assertNull(textureView.surfaceTextureListener)
        assertNull(hostView.managedVideoTextureViewForTest())
    }

    @Test
    fun `disabling direct texture keeps overlay texture view prewarmed but transparent`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostView = LibVlcTextureVideoHostView(context)

        hostView.setLibVlcDirectTextureEnabled(false)

        assertTrue(hostView.debugOverlayTextureViewForTest().visibility == View.VISIBLE)
        assertEquals(0f, hostView.debugOverlayTextureViewForTest().alpha, 0f)
    }

    @Test
    fun `enabling output callbacks exposes dedicated image reader host and keeps direct texture transparent`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostView = LibVlcTextureVideoHostView(context)

        hostView.setLibVlcOutputCallbackEnabled(true)

        assertTrue(hostView.debugOverlayTextureViewForTest().visibility == View.VISIBLE)
        assertEquals(0f, hostView.debugOverlayTextureViewForTest().alpha, 0f)
        assertTrue(hostView.glVideoSurfaceViewForTest().visibility == View.INVISIBLE)
        assertTrue(hostView.outputCallbackSurfaceViewForTest().visibility == View.VISIBLE)
        assertEquals(hostView.outputCallbackSurfaceViewForTest(), hostView.libVlcOutputCallbackView())
    }

    @Test
    fun `enabling vmem stream hides debug overlay and exposes dedicated vmem surface`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostView = LibVlcTextureVideoHostView(context)

        hostView.setLibVlcVmemStreamEnabled(true)

        assertTrue(hostView.debugOverlayTextureViewForTest().visibility == View.GONE)
        assertTrue(hostView.vmemVideoSurfaceViewForTest().visibility == View.VISIBLE)
    }

    @Test
    fun `enabling vmem stream collapses managed libvlc texture carrier offscreen`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostView = LibVlcTextureVideoHostView(context)
        val carrier = TextureView(context).apply {
            id = requireVideolanId("texture_video")
        }

        hostView.addView(
            carrier,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        hostView.setLibVlcVmemStreamEnabled(true)

        val layoutParams = carrier.layoutParams
        assertEquals(1, layoutParams.width)
        assertEquals(1, layoutParams.height)
        assertEquals(0f, carrier.alpha, 0f)
        assertTrue(carrier.translationX < 0f)
        assertTrue(carrier.translationY < 0f)
    }

    @Test
    fun `output callbacks capture queues frame on dedicated image reader host instead of gl surface host`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostView = LibVlcTextureVideoHostView(context)

        hostView.setLibVlcOutputCallbackEnabled(true)

        assertTrue(hostView.captureCurrentFrame("output_callbacks_capture"))
        assertEquals(
            "output_callbacks_capture",
            hostView.outputCallbackSurfaceViewForTest().pendingCaptureLabelForTest(),
        )
        assertEquals(
            null,
            hostView.glVideoSurfaceViewForTest().pendingCaptureLabelForTest(),
        )
    }

    @Test
    fun `gl surface host stays laid out while disabled`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostView = LibVlcTextureVideoHostView(context)

        assertTrue(hostView.glVideoSurfaceViewForTest().visibility == View.INVISIBLE)

        hostView.setLibVlcVideoSurfaceEnabled(false)

        assertTrue(hostView.glVideoSurfaceViewForTest().visibility == View.INVISIBLE)
    }

    @Test
    fun `enabling gl surface host makes dedicated gl view visible`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostView = LibVlcTextureVideoHostView(context)

        hostView.setLibVlcVideoSurfaceEnabled(true)

        assertTrue(hostView.glVideoSurfaceViewForTest().visibility == View.VISIBLE)
    }

    @Test
    fun `captureCurrentFrame swallows pixel copy exceptions from invalid surfaces`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostView = LibVlcTextureVideoHostView(context)
        val surfaceView = SurfaceView(context)

        hostView.addView(
            surfaceView,
            FrameLayout.LayoutParams(640, 360),
        )
        hostView.measure(
            ViewMeasureSpecs.exactly(640),
            ViewMeasureSpecs.exactly(360),
        )
        hostView.layout(0, 0, 640, 360)
        surfaceView.layout(0, 0, 640, 360)

        mockkStatic(PixelCopy::class)
        try {
            every {
                PixelCopy.request(
                    any<SurfaceView>(),
                    any<Bitmap>(),
                    any(),
                    any<Handler>(),
                )
            } throws IllegalArgumentException("Surface isn't valid")

            assertFalse(hostView.captureCurrentFrame("invalid_surface"))
        } finally {
            unmockkStatic(PixelCopy::class)
        }
    }

    @Test
    fun `analyzeDebugCaptureBitmap reports all-black captures as blank`() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }

        val stats = analyzeDebugCaptureBitmap(bitmap)

        assertTrue(stats.isAllBlack)
        assertTrue(stats.nonBlackSamples == 0)
    }

    @Test
    fun `analyzeDebugCaptureBitmap detects visible pixels`() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(12, 8, 4))
        }

        val stats = analyzeDebugCaptureBitmap(bitmap)

        assertFalse(stats.isAllBlack)
        assertTrue(stats.nonBlackSamples > 0)
    }

    @Test
    fun `resolveWindowCaptureBounds returns sized bounds for a laid out host`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = FrameLayout(context)
        val hostView = LibVlcTextureVideoHostView(context)

        root.addView(
            hostView,
            FrameLayout.LayoutParams(640, 360),
        )
        root.measure(
            ViewMeasureSpecs.exactly(1280),
            ViewMeasureSpecs.exactly(720),
        )
        root.layout(0, 0, 1280, 720)
        hostView.layout(120, 80, 760, 440)

        val bounds = resolveWindowCaptureBounds(hostView)

        assertNotNull(bounds)
        assertTrue(bounds!!.width() == 640)
        assertTrue(bounds.height() == 360)
    }

    @Test
    fun `resolveWindowCaptureBounds skips zero sized views`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostView = LibVlcTextureVideoHostView(context)

        val bounds = resolveWindowCaptureBounds(hostView)

        assertTrue(bounds == null)
    }

    @Test
    fun `surface capture priority favors video surface over subtitles surface`() {
        assertTrue(surfaceCapturePriorityByEntryName("surface_video") > surfaceCapturePriorityByEntryName("surface_subtitles"))
        assertTrue(surfaceCapturePriorityByEntryName(null) > surfaceCapturePriorityByEntryName("surface_subtitles"))
    }

    @Test
    fun `sortSurfaceViewsForCapture prefers vlc video surface before subtitle surface`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val subtitleSurface = SurfaceView(context).apply {
            id = requireVideolanId("surface_subtitles")
            layout(0, 0, 640, 360)
        }
        val unnamedSurface = SurfaceView(context).apply {
            id = View.generateViewId()
            layout(0, 0, 640, 360)
        }
        val videoSurface = SurfaceView(context).apply {
            id = requireVideolanId("surface_video")
            layout(0, 0, 640, 360)
        }

        val sorted = sortSurfaceViewsForCapture(listOf(subtitleSurface, unnamedSurface, videoSurface))

        assertEquals(videoSurface, sorted.first())
        assertEquals(subtitleSurface, sorted.last())
    }

    @Test
    fun `window fallback capture source does not count as verified video frame`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hostView = LibVlcTextureVideoHostView(context)
        var capturedLabel: String? = null
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(16, 16, 16))
        }

        hostView.setOnFrameCaptured { label ->
            capturedLabel = label
        }

        val saveTextureBitmap = LibVlcTextureVideoHostView::class.java.getDeclaredMethod(
            "saveTextureBitmap",
            Bitmap::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
            DebugCaptureBitmapStats::class.java,
        ).apply {
            isAccessible = true
        }

        saveTextureBitmap.invoke(
            hostView,
            bitmap,
            "window_fallback_case",
            32,
            32,
            "surface_video_window_fallback_result_3",
            DebugCaptureBitmapStats(
                sampleCount = 4,
                nonBlackSamples = 4,
                maxChannelValue = 16,
            ),
        )

        assertNull(capturedLabel)
        assertTrue(
            File(context.filesDir, "MiruPlayLibVlcCaptures/window_fallback_case.png").isFile,
        )
    }
}

private object ViewMeasureSpecs {
    fun exactly(size: Int): Int =
        android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY)
}

private fun requireVideolanId(name: String): Int =
    Class.forName("org.videolan.R\$id")
        .getField(name)
        .getInt(null)
