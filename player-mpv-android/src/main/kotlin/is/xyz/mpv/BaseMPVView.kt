package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

abstract class BaseMPVView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    private var surfaceAttached = false
    fun initialize(configDir: String, cacheDir: String) {
        MPVLib.create(context)
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir)
        arrayOf("gpu-shader-cache-dir", "icc-cache-dir").forEach { option ->
            MPVLib.setOptionString(option, cacheDir)
        }
        initOptions()
        MPVLib.init()
        postInitOptions()
        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("idle", "once")
        holder.addCallback(this)
        observeProperties()
    }

    fun releasePlayer() {
        holder.removeCallback(this)
        MPVLib.destroy()
    }

    protected abstract fun initOptions()
    protected abstract fun postInitOptions()
    protected abstract fun observeProperties()

    private var filePath: String? = null
    private var voInUse: String = "gpu"

    fun playFile(filePath: String) {
        this.filePath = filePath
    }

    fun setVo(vo: String) {
        updateVoInUse(vo)
        MPVLib.setOptionString("vo", vo)
    }

    protected fun updateVoInUse(vo: String) {
        voInUse = vo
    }

    protected fun isPlaybackSurfaceAttached(): Boolean = surfaceAttached

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.w(TAG, "attaching surface")
        surfaceAttached = true
        MPVLib.attachSurface(holder.surface)
        MPVLib.setOptionString("force-window", "yes")
        val pendingFile = filePath
        if (pendingFile != null) {
            MPVLib.command(arrayOf("loadfile", pendingFile))
            filePath = null
        } else {
            MPVLib.setPropertyString("vo", voInUse)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.w(TAG, "detaching surface")
        surfaceAttached = false
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        MPVLib.detachSurface()
    }

    companion object {
        private const val TAG = "mpv"
    }
}

internal fun shouldLoadMpvFileImmediately(surfaceAttached: Boolean): Boolean = surfaceAttached

internal fun shouldApplyPendingStartSeek(startPositionMs: Long?): Boolean =
    (startPositionMs ?: 0L) > 0L
