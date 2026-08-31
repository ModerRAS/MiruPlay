package com.miruplay.tv.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object NativeDspBridge {
    private var loadAttempted = false
    private var loaded = false

    private fun ensureLoaded(): Boolean {
        if (loadAttempted) return loaded
        loadAttempted = true
        loaded = try {
            System.loadLibrary("miruplay_dsp")
            // Verify JNI
            nativeIsAvailable()
            true
        } catch (e: Throwable) {
            // UnsatisfiedLinkError on JVM tests or missing ABI
            false
        }
        return loaded
    }

    fun isAvailable(): Boolean = ensureLoaded()

    fun isNeonAvailable(): Boolean = if (!ensureLoaded()) false else try {
        nativeIsNeonAvailable()
    } catch (_: Throwable) { false }

    /**
     * One alloc per plan. Caller must call [release] when done (e.g., onReset).
     * [tapsByChannel] size = channels, each FloatArray size = tapsLen. Pre-reversed inside native.
     * Returns handle (0 on failure, fallback to Kotlin).
     */
    fun create(channels: Int, tapsLen: Int, tapsByChannel: Array<FloatArray>, preamp: Float, channelGain: FloatArray): Long {
        if (!ensureLoaded()) return 0
        if (channels <= 0 || tapsLen <= 0) return 0
        return try {
            nativeCreate(channels, tapsLen, tapsByChannel, preamp, channelGain)
        } catch (_: Throwable) { 0 }
    }

    fun release(handle: Long) {
        if (handle == 0L || !ensureLoaded()) return
        try { nativeRelease(handle) } catch (_: Throwable) {}
    }

    fun reset(handle: Long) {
        if (handle == 0L || !ensureLoaded()) return
        try { nativeReset(handle) } catch (_: Throwable) {}
    }

    /**
     * Zero-copy batch via direct buffers. Caller must have pre-allocated direct FloatBuffers of
     * capacity >= frames*channels, reused across calls (zero allocation).
     */
    fun processDirect(handle: Long, inBuf: FloatBuffer, outBuf: FloatBuffer, frames: Int) {
        if (handle == 0L || !ensureLoaded()) return
        nativeProcessDirect(handle, inBuf, outBuf, frames)
    }

    // Fallback array path (copies but no direct buffer needed, for unit tests)
    fun processArray(handle: Long, inArray: FloatArray, inOffset: Int, outArray: FloatArray, outOffset: Int, frames: Int) {
        if (handle == 0L || !ensureLoaded()) return
        nativeProcessArray(handle, inArray, inOffset, outArray, outOffset, frames)
    }

    fun updateTaps(handle: Long, tapsByChannel: Array<FloatArray>, preamp: Float, channelGain: FloatArray): Long {
        if (handle == 0L || !ensureLoaded()) return 0
        return try { nativeUpdateTaps(handle, tapsByChannel, preamp, channelGain) } catch (_: Throwable) { handle }
    }

    fun designFir(targetDb: FloatArray, taps: Int): FloatArray? {
        if (!ensureLoaded()) return null
        return try { nativeDesignFir(targetDb, taps) } catch (_: Throwable) { null }
    }

    fun designFirScalar(targetDb: FloatArray, taps: Int): FloatArray? {
        if (!ensureLoaded()) return null
        return try { nativeDesignFirScalar(targetDb, taps) } catch (_: Throwable) { null }
    }

    // --- pool helpers for Kotlin side zero-allocation ---
    fun allocateDirectFloatBuffer(floatCapacity: Int): FloatBuffer {
        // 16-byte aligned via allocateDirect? ByteBuffer.allocateDirect is aligned.
        return ByteBuffer.allocateDirect(floatCapacity * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }

    @JvmStatic private external fun nativeIsAvailable(): Boolean
    @JvmStatic private external fun nativeIsNeonAvailable(): Boolean
    @JvmStatic private external fun nativeCreate(channels: Int, tapsLen: Int, tapsByChannel: Array<FloatArray>, preamp: Float, channelGain: FloatArray): Long
    @JvmStatic private external fun nativeRelease(handle: Long)
    @JvmStatic private external fun nativeReset(handle: Long)
    @JvmStatic private external fun nativeProcessDirect(handle: Long, inBuffer: FloatBuffer, outBuffer: FloatBuffer, frames: Int)
    @JvmStatic private external fun nativeProcessArray(handle: Long, inArray: FloatArray, inOffset: Int, outArray: FloatArray, outOffset: Int, frames: Int)
    @JvmStatic private external fun nativeUpdateTaps(handle: Long, tapsByChannel: Array<FloatArray>, preamp: Float, channelGain: FloatArray): Long
    @JvmStatic private external fun nativeDesignFir(targetDb: FloatArray, taps: Int): FloatArray
    @JvmStatic private external fun nativeDesignFirScalar(targetDb: FloatArray, taps: Int): FloatArray
}
