package com.miruplay.tv.player

import android.view.View

/**
 * Optional host capability for libVLC render containers backed by VMEM frame streaming.
 */
interface LibVlcVmemVideoHost {
    fun libVlcVmemVideoView(): View? = null

    fun setLibVlcVmemStreamEnabled(enabled: Boolean) = Unit

    fun bindLibVlcVmemStream(
        bridge: LibVlcVmemStreamBridge?,
        session: LibVlcVmemStreamSession?,
    ) = Unit
}
