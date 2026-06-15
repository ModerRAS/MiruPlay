package com.miruplay.tv.player

import android.util.Log
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibVlcVmemStreamBridge @Inject constructor() {
    private var nativeInvoker: LibVlcVmemStreamInvoker = JniLibVlcVmemStreamInvoker

    internal constructor(nativeInvoker: LibVlcVmemStreamInvoker) : this() {
        this.nativeInvoker = nativeInvoker
    }

    fun createStream(
        preferredOutputChroma: String? = null,
    ): LibVlcVmemStreamCreateResult {
        val normalizedChroma = normalizePreferredOutputChroma(preferredOutputChroma)
        val streamHandle = runCatching {
            nativeInvoker.createStream(normalizedChroma)
        }.getOrElse { error ->
            Log.w(TAG, "libVLC VMEM stream creation failed", error)
            return LibVlcVmemStreamCreateResult(
                success = false,
                resultCode = NATIVE_CALL_FAILED,
            )
        }
        if (streamHandle == 0L) {
            return LibVlcVmemStreamCreateResult(
                success = false,
                resultCode = STREAM_CREATION_FAILED,
            )
        }
        return LibVlcVmemStreamCreateResult(
            success = true,
            resultCode = 0,
            session = LibVlcVmemStreamSession(streamHandle = streamHandle),
        )
    }

    fun attachStream(
        playerInstance: Long,
        session: LibVlcVmemStreamSession,
        windowWidth: Int,
        windowHeight: Int,
    ): LibVlcVmemStreamAttachResult {
        if (playerInstance == 0L) {
            return LibVlcVmemStreamAttachResult(
                success = false,
                resultCode = INVALID_PLAYER_INSTANCE,
            )
        }
        if (session.streamHandle == 0L) {
            return LibVlcVmemStreamAttachResult(
                success = false,
                resultCode = INVALID_STREAM_HANDLE,
            )
        }
        val attachCode = runCatching {
            nativeInvoker.attachStream(
                playerInstance = playerInstance,
                streamHandle = session.streamHandle,
                windowWidth = windowWidth.coerceAtLeast(1),
                windowHeight = windowHeight.coerceAtLeast(1),
            )
        }.getOrElse { error ->
            Log.w(TAG, "libVLC VMEM stream attach failed", error)
            return LibVlcVmemStreamAttachResult(
                success = false,
                resultCode = NATIVE_CALL_FAILED,
            )
        }
        if (attachCode != 0) {
            return LibVlcVmemStreamAttachResult(
                success = false,
                resultCode = attachCode,
            )
        }
        return LibVlcVmemStreamAttachResult(
            success = true,
            resultCode = 0,
            session = session.copy(playerInstance = playerInstance),
        )
    }

    fun readState(
        session: LibVlcVmemStreamSession,
    ): LibVlcVmemStreamState {
        if (session.streamHandle == 0L) {
            return LibVlcVmemStreamState()
        }
        val rawState = runCatching {
            nativeInvoker.readState(session.streamHandle)
        }.getOrElse { error ->
            Log.w(TAG, "libVLC VMEM stream state read failed", error)
            return LibVlcVmemStreamState()
        }
        if (rawState.size < STATE_FIELD_COUNT) {
            return LibVlcVmemStreamState()
        }
        return LibVlcVmemStreamState(
            configured = rawState[INDEX_CONFIGURED] != 0L,
            frameVersion = rawState[INDEX_FRAME_VERSION],
            chroma = unpackFourcc(rawState[INDEX_CHROMA]),
            width = rawState[INDEX_WIDTH].toInt(),
            height = rawState[INDEX_HEIGHT].toInt(),
            visibleWidth = rawState[INDEX_VISIBLE_WIDTH].toInt(),
            visibleHeight = rawState[INDEX_VISIBLE_HEIGHT].toInt(),
            planeCount = rawState[INDEX_PLANE_COUNT].toInt(),
            pitch = rawState[INDEX_PITCH0].toInt(),
            pitch1 = rawState[INDEX_PITCH1].toInt(),
            pitch2 = rawState[INDEX_PITCH2].toInt(),
            pitch3 = rawState[INDEX_PITCH3].toInt(),
            line0 = rawState[INDEX_LINE0].toInt(),
            line1 = rawState[INDEX_LINE1].toInt(),
            line2 = rawState[INDEX_LINE2].toInt(),
            line3 = rawState[INDEX_LINE3].toInt(),
            totalBytes = rawState[INDEX_TOTAL_BYTES].toInt(),
        )
    }

    fun copyLatestFrame(
        session: LibVlcVmemStreamSession,
        target: ByteBuffer,
        lastFrameVersion: Long,
    ): Long {
        if (session.streamHandle == 0L) {
            return 0L
        }
        return runCatching {
            nativeInvoker.copyLatestFrame(
                streamHandle = session.streamHandle,
                target = target,
                targetCapacity = target.capacity(),
                lastFrameVersion = lastFrameVersion,
            )
        }.onFailure { error ->
            Log.w(TAG, "libVLC VMEM stream frame copy failed", error)
        }.getOrDefault(0L)
    }

    fun copyLatestFrameRgba(
        session: LibVlcVmemStreamSession,
        target: ByteBuffer,
        lastFrameVersion: Long,
    ): Long {
        if (session.streamHandle == 0L) {
            return 0L
        }
        return runCatching {
            nativeInvoker.copyLatestFrameRgba(
                streamHandle = session.streamHandle,
                target = target,
                targetCapacity = target.capacity(),
                lastFrameVersion = lastFrameVersion,
            )
        }.onFailure { error ->
            Log.w(TAG, "libVLC VMEM stream RGBA frame copy failed", error)
        }.getOrDefault(0L)
    }

    fun releaseStream(
        session: LibVlcVmemStreamSession?,
    ) {
        session ?: return
        if (session.streamHandle == 0L) {
            return
        }
        runCatching {
            nativeInvoker.releaseStream(
                playerInstance = session.playerInstance,
                streamHandle = session.streamHandle,
            )
        }.onFailure { error ->
            Log.w(TAG, "libVLC VMEM stream release failed", error)
        }
    }

    companion object {
        const val DEFAULT_VIDEO_OUTPUT_MODULE: String = "vmem"
        const val DEFAULT_WINDOW_MODULE: String = "wextern"
        const val DEFAULT_DECODER_DEVICE: String = "none"
        const val INVALID_PLAYER_INSTANCE: Int = -4001
        const val INVALID_STREAM_HANDLE: Int = -4002
        const val STREAM_CREATION_FAILED: Int = -4003
        const val NATIVE_CALL_FAILED: Int = -4004

        private const val INDEX_CONFIGURED = 0
        private const val INDEX_FRAME_VERSION = 1
        private const val INDEX_CHROMA = 2
        private const val INDEX_WIDTH = 3
        private const val INDEX_HEIGHT = 4
        private const val INDEX_VISIBLE_WIDTH = 5
        private const val INDEX_VISIBLE_HEIGHT = 6
        private const val INDEX_PLANE_COUNT = 7
        private const val INDEX_PITCH0 = 8
        private const val INDEX_PITCH1 = 9
        private const val INDEX_PITCH2 = 10
        private const val INDEX_PITCH3 = 11
        private const val INDEX_LINE0 = 12
        private const val INDEX_LINE1 = 13
        private const val INDEX_LINE2 = 14
        private const val INDEX_LINE3 = 15
        private const val INDEX_TOTAL_BYTES = 16
        private const val STATE_FIELD_COUNT = 17
        private const val TAG = "LibVlcVmemStream"
    }
}

data class LibVlcVmemStreamCreateResult(
    val success: Boolean,
    val resultCode: Int,
    val session: LibVlcVmemStreamSession? = null,
)

data class LibVlcVmemStreamAttachResult(
    val success: Boolean,
    val resultCode: Int,
    val session: LibVlcVmemStreamSession? = null,
)

data class LibVlcVmemStreamSession(
    val streamHandle: Long,
    val playerInstance: Long = 0L,
)

data class LibVlcVmemStreamState(
    val configured: Boolean = false,
    val frameVersion: Long = 0L,
    val chroma: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val visibleWidth: Int = 0,
    val visibleHeight: Int = 0,
    val planeCount: Int = 0,
    val pitch: Int = 0,
    val pitch1: Int = 0,
    val pitch2: Int = 0,
    val pitch3: Int = 0,
    val line0: Int = 0,
    val line1: Int = 0,
    val line2: Int = 0,
    val line3: Int = 0,
    val totalBytes: Int = 0,
)

internal interface LibVlcVmemStreamInvoker {
    fun createStream(preferredOutputChroma: String?): Long

    fun attachStream(
        playerInstance: Long,
        streamHandle: Long,
        windowWidth: Int,
        windowHeight: Int,
    ): Int

    fun readState(
        streamHandle: Long,
    ): LongArray

    fun copyLatestFrame(
        streamHandle: Long,
        target: ByteBuffer,
        targetCapacity: Int,
        lastFrameVersion: Long,
    ): Long

    fun copyLatestFrameRgba(
        streamHandle: Long,
        target: ByteBuffer,
        targetCapacity: Int,
        lastFrameVersion: Long,
    ): Long

    fun releaseStream(
        playerInstance: Long,
        streamHandle: Long,
    )
}

private object JniLibVlcVmemStreamInvoker : LibVlcVmemStreamInvoker {
    override fun createStream(preferredOutputChroma: String?): Long =
        LibVlcNativeVmemStreamBindings.createStream(preferredOutputChroma)

    override fun attachStream(
        playerInstance: Long,
        streamHandle: Long,
        windowWidth: Int,
        windowHeight: Int,
    ): Int = LibVlcNativeVmemStreamBindings.attachStream(
        playerInstance = playerInstance,
        streamHandle = streamHandle,
        windowWidth = windowWidth,
        windowHeight = windowHeight,
    )

    override fun readState(streamHandle: Long): LongArray =
        LibVlcNativeVmemStreamBindings.readState(streamHandle)

    override fun copyLatestFrame(
        streamHandle: Long,
        target: ByteBuffer,
        targetCapacity: Int,
        lastFrameVersion: Long,
    ): Long = LibVlcNativeVmemStreamBindings.copyLatestFrame(
        streamHandle = streamHandle,
        target = target,
        targetCapacity = targetCapacity,
        lastFrameVersion = lastFrameVersion,
    )

    override fun copyLatestFrameRgba(
        streamHandle: Long,
        target: ByteBuffer,
        targetCapacity: Int,
        lastFrameVersion: Long,
    ): Long = LibVlcNativeVmemStreamBindings.copyLatestFrameRgba(
        streamHandle = streamHandle,
        target = target,
        targetCapacity = targetCapacity,
        lastFrameVersion = lastFrameVersion,
    )

    override fun releaseStream(
        playerInstance: Long,
        streamHandle: Long,
    ) {
        LibVlcNativeVmemStreamBindings.releaseStream(
            playerInstance = playerInstance,
            streamHandle = streamHandle,
        )
    }
}

internal object LibVlcNativeVmemStreamBindings {
    init {
        System.loadLibrary("miruplay_vlcbridge")
    }

    external fun createStream(
        preferredOutputChroma: String?,
    ): Long

    external fun attachStream(
        playerInstance: Long,
        streamHandle: Long,
        windowWidth: Int,
        windowHeight: Int,
    ): Int

    external fun readState(
        streamHandle: Long,
    ): LongArray

    external fun copyLatestFrame(
        streamHandle: Long,
        target: ByteBuffer,
        targetCapacity: Int,
        lastFrameVersion: Long,
    ): Long

    external fun copyLatestFrameRgba(
        streamHandle: Long,
        target: ByteBuffer,
        targetCapacity: Int,
        lastFrameVersion: Long,
    ): Long

    external fun releaseStream(
        playerInstance: Long,
        streamHandle: Long,
    )
}

private fun normalizePreferredOutputChroma(
    preferredOutputChroma: String?,
): String? = preferredOutputChroma
    ?.trim()
    ?.uppercase()
    ?.takeIf { it.length == 4 }

private fun unpackFourcc(value: Long): String {
    if (value == 0L) return ""
    val bytes = CharArray(4) { index ->
        (((value shr (index * 8)) and 0xFFL).toInt()).toChar()
    }
    return bytes.concatToString().trimEnd('\u0000')
}
