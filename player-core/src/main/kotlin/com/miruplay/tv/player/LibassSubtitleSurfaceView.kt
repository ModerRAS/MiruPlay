package com.miruplay.tv.player

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

class LibassSubtitleSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private var session: LibassSubtitleSession? = null

    init {
        setZOrderMediaOverlay(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
    }

    fun bind(newSession: LibassSubtitleSession?) {
        if (session === newSession) {
            bindCurrentSurface()
            return
        }
        unbind()
        session = newSession
        bindCurrentSurface()
    }

    fun unbind() {
        val surface: Surface? = holder.surface
        session?.unbindSurface(surface)
        session = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        bindCurrentSurface()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val surface: Surface? = holder.surface
        if (surface?.isValid == true && width > 0 && height > 0) {
            session?.bindSurface(surface, width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val surface: Surface? = holder.surface
        session?.unbindSurface(surface)
    }

    private fun bindCurrentSurface() {
        val surface: Surface? = holder.surface
        val frame = holder.surfaceFrame
        if (surface?.isValid == true && frame.width() > 0 && frame.height() > 0) {
            session?.bindSurface(surface, frame.width(), frame.height())
        }
    }
}
