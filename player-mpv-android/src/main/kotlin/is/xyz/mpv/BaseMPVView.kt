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
            // 同上：窗口型 vo 在 surface 已消失时被（重）初始化会 assert，
            // release 路径的 detachSurface/destroy 之前也要先关掉视频输出。
            MPVLib.setPropertyString("vo", VIDEO_OUTPUT_DISABLED)
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
                    MpvSurfaceDestroyAction.DISABLE_VIDEO_OUTPUT ->
                        MPVLib.setPropertyString("vo", VIDEO_OUTPUT_DISABLED)
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

/**
 * mpv 的窗口型 VO（mediacodec_embed / gpu*）在没有 Android window 时被（重）初始化会直接
 * assert 崩溃（vo_mediacodec_embed.c: WinID != 0 && WinID != -1），EOF/退场时先 detach
 * surface 就会命中。因此 detach 之前先把视频输出切到 null（音频继续播），
 * surfaceCreated() 再按 voInUse 恢复真实 vo。
 */
internal enum class MpvSurfaceDestroyAction { DISABLE_VIDEO_OUTPUT, DETACH }

internal const val VIDEO_OUTPUT_DISABLED = "null"

internal fun surfaceDestroyActions(): List<MpvSurfaceDestroyAction> =
    listOf(MpvSurfaceDestroyAction.DISABLE_VIDEO_OUTPUT, MpvSurfaceDestroyAction.DETACH)

/** 窗口型 vo 只能在 surface 已附着时重建，否则 mpv vo 线程会 assert。 */
internal fun shouldApplyRuntimeVo(surfaceAttached: Boolean): Boolean = surfaceAttached

internal fun shouldLoadMpvFileImmediately(surfaceAttached: Boolean): Boolean = surfaceAttached

internal fun shouldApplyPendingStartSeek(startPositionMs: Long?): Boolean =
    (startPositionMs ?: 0L) > 0L
