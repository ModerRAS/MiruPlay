package com.miruplay.tv.player

import android.view.TextureView

/**
 * Optional host capability for libVLC render containers that expose a direct TextureView.
 */
interface LibVlcDirectVideoHost {
    fun libVlcDirectVideoTextureView(): TextureView?

    fun setLibVlcDirectTextureEnabled(enabled: Boolean) = Unit
}
