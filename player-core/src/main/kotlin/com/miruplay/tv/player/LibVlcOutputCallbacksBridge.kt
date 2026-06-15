package com.miruplay.tv.player

import com.miruplay.tv.core.common.logging.MiruLog
import android.util.Log
import android.view.Surface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibVlcOutputCallbacksBridge @Inject constructor() {
    private var nativeInvoker: LibVlcOutputCallbacksInvoker = JniLibVlcOutputCallbacksInvoker

    internal constructor(nativeInvoker: LibVlcOutputCallbacksInvoker) : this() {
        this.nativeInvoker = nativeInvoker
    }

    fun attachOutput(
        playerInstance: Long,
        surface: Surface,
        width: Int,
        height: Int,
    ): LibVlcOutputCallbacksAttachResult {
        MiruLog.i(
            TAG,
            "Attaching libVLC output callbacks bridge",
            mapOf(
                "player_instance" to playerInstance.toString(),
                "surface_hash" to surface.hashCode().toString(),
                "surface_valid" to surface.isValid.toString(),
                "width" to width.toString(),
                "height" to height.toString(),
            ),
        )
        if (playerInstance == 0L) {
            return LibVlcOutputCallbacksAttachResult(
                success = false,
                resultCode = INVALID_PLAYER_INSTANCE,
            )
        }
        val bridgeHandle = runCatching {
            nativeInvoker.attachOutput(
                playerInstance = playerInstance,
                surface = surface,
                width = width.coerceAtLeast(1),
                height = height.coerceAtLeast(1),
            )
        }.getOrElse { error ->
            Log.w(TAG, "libVLC output callbacks attach failed", error)
            MiruLog.w(
                TAG,
                "libVLC output callbacks attach threw before returning a bridge handle",
                error,
                mapOf(
                    "player_instance" to playerInstance.toString(),
                    "surface_hash" to surface.hashCode().toString(),
                    "width" to width.toString(),
                    "height" to height.toString(),
                ),
            )
            return LibVlcOutputCallbacksAttachResult(
                success = false,
                resultCode = NATIVE_CALL_FAILED,
            )
        }
        if (bridgeHandle == 0L) {
            MiruLog.w(
                TAG,
                "libVLC output callbacks attach returned an empty bridge handle",
                attributes = mapOf(
                    "player_instance" to playerInstance.toString(),
                    "surface_hash" to surface.hashCode().toString(),
                    "width" to width.toString(),
                    "height" to height.toString(),
                ),
            )
            return LibVlcOutputCallbacksAttachResult(
                success = false,
                resultCode = ATTACH_FAILED,
            )
        }
        MiruLog.i(
            TAG,
            "Attached libVLC output callbacks bridge",
            mapOf(
                "player_instance" to playerInstance.toString(),
                "bridge_handle" to bridgeHandle.toString(),
                "surface_hash" to surface.hashCode().toString(),
                "width" to width.toString(),
                "height" to height.toString(),
            ),
        )
        return LibVlcOutputCallbacksAttachResult(
            success = true,
            resultCode = 0,
            session = LibVlcOutputCallbacksSession(
                playerInstance = playerInstance,
                bridgeHandle = bridgeHandle,
            ),
        )
    }

    fun updateOutputWindow(
        session: LibVlcOutputCallbacksSession,
        width: Int,
        height: Int,
    ) {
        if (session.bridgeHandle == 0L) {
            return
        }
        runCatching {
            nativeInvoker.updateOutputWindow(
                bridgeHandle = session.bridgeHandle,
                width = width.coerceAtLeast(1),
                height = height.coerceAtLeast(1),
            )
        }.onFailure { error ->
            Log.w(TAG, "libVLC output callbacks resize failed", error)
            MiruLog.w(
                TAG,
                "libVLC output callbacks resize failed",
                error,
                mapOf(
                    "player_instance" to session.playerInstance.toString(),
                    "bridge_handle" to session.bridgeHandle.toString(),
                    "width" to width.toString(),
                    "height" to height.toString(),
                ),
            )
        }.onSuccess {
            MiruLog.i(
                TAG,
                "Updated libVLC output callbacks window",
                mapOf(
                    "player_instance" to session.playerInstance.toString(),
                    "bridge_handle" to session.bridgeHandle.toString(),
                    "width" to width.toString(),
                    "height" to height.toString(),
                ),
            )
        }
    }

    fun releaseOutput(session: LibVlcOutputCallbacksSession) {
        if (session.bridgeHandle == 0L) {
            return
        }
        runCatching {
            nativeInvoker.releaseOutput(
                playerInstance = session.playerInstance,
                bridgeHandle = session.bridgeHandle,
            )
        }.onFailure { error ->
            Log.w(TAG, "libVLC output callbacks release failed", error)
            MiruLog.w(
                TAG,
                "libVLC output callbacks release failed",
                error,
                mapOf(
                    "player_instance" to session.playerInstance.toString(),
                    "bridge_handle" to session.bridgeHandle.toString(),
                ),
            )
        }.onSuccess {
            MiruLog.i(
                TAG,
                "Released libVLC output callbacks bridge",
                mapOf(
                    "player_instance" to session.playerInstance.toString(),
                    "bridge_handle" to session.bridgeHandle.toString(),
                ),
            )
        }
    }

    companion object {
        const val INVALID_PLAYER_INSTANCE: Int = -3001
        const val ATTACH_FAILED: Int = -3002
        const val NATIVE_CALL_FAILED: Int = -3003

        private const val TAG = "LibVlcOutputCallbacks"
    }
}

data class LibVlcOutputCallbacksAttachResult(
    val success: Boolean,
    val resultCode: Int,
    val session: LibVlcOutputCallbacksSession? = null,
)

data class LibVlcOutputCallbacksSession(
    val playerInstance: Long,
    val bridgeHandle: Long,
)

internal interface LibVlcOutputCallbacksInvoker {
    fun attachOutput(
        playerInstance: Long,
        surface: Surface,
        width: Int,
        height: Int,
    ): Long

    fun updateOutputWindow(
        bridgeHandle: Long,
        width: Int,
        height: Int,
    )

    fun releaseOutput(
        playerInstance: Long,
        bridgeHandle: Long,
    )
}

private object JniLibVlcOutputCallbacksInvoker : LibVlcOutputCallbacksInvoker {
    override fun attachOutput(
        playerInstance: Long,
        surface: Surface,
        width: Int,
        height: Int,
    ): Long = LibVlcNativeOutputCallbacksBindings.attachOutput(
        playerInstance = playerInstance,
        surface = surface,
        width = width,
        height = height,
    )

    override fun updateOutputWindow(
        bridgeHandle: Long,
        width: Int,
        height: Int,
    ) {
        LibVlcNativeOutputCallbacksBindings.updateOutputWindow(
            bridgeHandle = bridgeHandle,
            width = width,
            height = height,
        )
    }

    override fun releaseOutput(
        playerInstance: Long,
        bridgeHandle: Long,
    ) {
        LibVlcNativeOutputCallbacksBindings.releaseOutput(
            playerInstance = playerInstance,
            bridgeHandle = bridgeHandle,
        )
    }
}

internal object LibVlcNativeOutputCallbacksBindings {
    init {
        System.loadLibrary("miruplay_vlcbridge")
    }

    external fun attachOutput(
        playerInstance: Long,
        surface: Surface,
        width: Int,
        height: Int,
    ): Long

    external fun updateOutputWindow(
        bridgeHandle: Long,
        width: Int,
        height: Int,
    )

    external fun releaseOutput(
        playerInstance: Long,
        bridgeHandle: Long,
    )
}
