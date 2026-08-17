package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class BaseMPVView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    private var surfaceAttached = false
    internal val lifecycleGate = MpvReleaseGate()

    fun initialize(configDir: String, cacheDir: String) {
        lifecycleGate.withNativeAccess {
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
    }

    protected fun releasePlayerAfterBegin() {
        holder.removeCallback(this)
        lifecycleGate.finishRelease {
            if (surfaceAttached) {
                surfaceAttached = false
                MPVLib.detachSurface()
            }
            MPVLib.destroy()
        }
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
        lifecycleGate.withNativeAccess { MPVLib.setOptionString("vo", vo) }
    }

    protected fun updateVoInUse(vo: String) {
        voInUse = vo
    }

    protected fun isPlaybackSurfaceAttached(): Boolean =
        lifecycleGate.withNativeAccess { surfaceAttached } ?: false

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        lifecycleGate.withNativeAccess {
            MPVLib.setPropertyString("android-surface-size", "${width}x$height")
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        lifecycleGate.withNativeAccess {
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
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        lifecycleGate.withNativeAccess {
            Log.w(TAG, "detaching surface")
            surfaceAttached = false
            surfaceDestroyActions().forEach { action ->
                when (action) {
                    MpvSurfaceDestroyAction.DETACH -> MPVLib.detachSurface()
                }
            }
        }
    }

    companion object {
        private const val TAG = "mpv"
    }
}

internal class MpvReleaseGate {
    internal enum class State { ACTIVE, RELEASING, RELEASED }

    private val lock = ReentrantLock()
    @Volatile
    private var state = State.ACTIVE

    fun beginRelease(): Boolean = beginReleaseIf { true }

    fun beginReleaseIf(shouldRelease: () -> Boolean): Boolean = lock.withLock {
        if (state != State.ACTIVE || !shouldRelease()) return false
        state = State.RELEASING
        true
    }

    fun <T> withNativeAccess(block: () -> T): T? = lock.withLock {
        if (state != State.ACTIVE) return null
        block()
    }

    fun <T> withReleaseNativeAccess(block: () -> T): T? = lock.withLock {
        if (state != State.RELEASING) return null
        block()
    }

    fun finishRelease(block: () -> Unit): Boolean = lock.withLock {
        if (state != State.RELEASING) return false
        try {
            block()
        } finally {
            state = State.RELEASED
        }
        true
    }

    fun isActive(): Boolean = state == State.ACTIVE
}

internal enum class MpvSurfaceDestroyAction { DETACH }

internal fun surfaceDestroyActions(): List<MpvSurfaceDestroyAction> =
    listOf(MpvSurfaceDestroyAction.DETACH)

internal fun shouldLoadMpvFileImmediately(surfaceAttached: Boolean): Boolean = surfaceAttached

internal fun shouldApplyPendingStartSeek(startPositionMs: Long?): Boolean =
    (startPositionMs ?: 0L) > 0L
