package com.miruplay.tv.player

import android.view.Surface
import android.view.View
import android.graphics.SurfaceTexture

/**
 * Optional host capability for libVLC render containers that expose a dedicated Surface.
 */
interface LibVlcSurfaceVideoHost {
    fun libVlcVideoSurface(): Surface?

    fun libVlcVideoSurfaceTexture(): SurfaceTexture? = null

    fun libVlcVideoSurfaceView(): View? = null

    fun libVlcVideoSurfaceWidth(): Int = libVlcVideoSurfaceView()?.width ?: 0

    fun libVlcVideoSurfaceHeight(): Int = libVlcVideoSurfaceView()?.height ?: 0

    fun setOnLibVlcVideoSurfaceReadyChanged(listener: ((Boolean) -> Unit)?) = Unit

    fun setLibVlcVideoSurfaceEnabled(enabled: Boolean) = Unit
}
