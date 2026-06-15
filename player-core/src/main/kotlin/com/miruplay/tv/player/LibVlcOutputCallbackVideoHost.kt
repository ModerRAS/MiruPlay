package com.miruplay.tv.player

import android.view.Surface
import android.view.View

/**
 * Optional host capability for libVLC render containers backed by custom output callbacks.
 */
interface LibVlcOutputCallbackVideoHost {
    fun libVlcOutputCallbackSurface(): Surface?

    fun libVlcOutputCallbackView(): View? = null

    fun libVlcOutputCallbackWidth(): Int = libVlcOutputCallbackView()?.width ?: 0

    fun libVlcOutputCallbackHeight(): Int = libVlcOutputCallbackView()?.height ?: 0

    fun setOnLibVlcOutputCallbackReadyChanged(listener: ((Boolean) -> Unit)?) = Unit

    fun setLibVlcOutputCallbackEnabled(enabled: Boolean) = Unit
}
