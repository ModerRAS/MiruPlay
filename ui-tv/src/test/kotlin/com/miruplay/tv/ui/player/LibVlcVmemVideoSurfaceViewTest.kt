package com.miruplay.tv.ui.player

import android.opengl.GLSurfaceView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibVlcVmemVideoSurfaceViewTest {
    @Test
    fun `vmem video surface view defaults to on demand rendering`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val view = LibVlcVmemVideoSurfaceView(context)

        assertEquals(GLSurfaceView.RENDERMODE_WHEN_DIRTY, view.renderMode)
    }
}
