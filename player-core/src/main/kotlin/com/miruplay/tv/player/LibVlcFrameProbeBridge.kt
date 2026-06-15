package com.miruplay.tv.player

import android.util.Log
import com.miruplay.tv.model.VideoSignalDescriptor
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibVlcFrameProbeBridge @Inject constructor() {
    private var nativeInvoker: LibVlcFrameProbeInvoker? = null

    internal constructor(nativeInvoker: LibVlcFrameProbeInvoker) : this() {
        this.nativeInvoker = nativeInvoker
    }

    internal fun armFirstFrameProbe(
        playerInstance: Long,
        outputDir: File,
        label: String,
        preferredOutputChroma: String? = null,
        windowWidth: Int,
        windowHeight: Int,
    ): LibVlcFrameProbeArmResult {
        if (playerInstance == 0L) {
            return LibVlcFrameProbeArmResult(
                success = false,
                resultCode = INVALID_PLAYER_INSTANCE,
            )
        }
        outputDir.mkdirs()
        val session = LibVlcFrameProbeSession(
            playerInstance = playerInstance,
            probeHandle = 0L,
            captureLabel = sanitizeFrameProbeLabel(label),
            metadataFile = File(outputDir, "${sanitizeFrameProbeLabel(label)}_vmem.txt"),
            previewFile = File(outputDir, "${sanitizeFrameProbeLabel(label)}_vmem_preview.ppm"),
            lumaFile = File(outputDir, "${sanitizeFrameProbeLabel(label)}_vmem_luma.pgm"),
            rawFrameFile = File(outputDir, "${sanitizeFrameProbeLabel(label)}_vmem.raw"),
        )
        session.metadataFile.delete()
        session.previewFile.delete()
        session.lumaFile.delete()
        session.rawFrameFile.delete()
        val probeHandle = runCatching {
            invoker().createProbe(
                metadataFile = session.metadataFile,
                previewFile = session.previewFile,
                lumaFile = session.lumaFile,
                rawFrameFile = session.rawFrameFile,
                preferredOutputChroma = sanitizeProbeOutputChroma(preferredOutputChroma),
            )
        }.getOrElse { error ->
            Log.w(TAG, "libVLC frame probe creation failed", error)
            return LibVlcFrameProbeArmResult(
                success = false,
                resultCode = NATIVE_CALL_FAILED,
            )
        }
        if (probeHandle == 0L) {
            return LibVlcFrameProbeArmResult(
                success = false,
                resultCode = PROBE_CREATION_FAILED,
            )
        }
        val attachCode = runCatching {
            invoker().attachProbe(
                playerInstance = playerInstance,
                probeHandle = probeHandle,
                windowWidth = windowWidth.coerceAtLeast(1),
                windowHeight = windowHeight.coerceAtLeast(1),
            )
        }.getOrElse { error ->
            Log.w(TAG, "libVLC frame probe attach failed", error)
            runCatching { invoker().releaseProbe(playerInstance, probeHandle) }
            return LibVlcFrameProbeArmResult(
                success = false,
                resultCode = NATIVE_CALL_FAILED,
            )
        }
        if (attachCode != 0) {
            runCatching { invoker().releaseProbe(playerInstance, probeHandle) }
            return LibVlcFrameProbeArmResult(
                success = false,
                resultCode = attachCode,
            )
        }
        return LibVlcFrameProbeArmResult(
            success = true,
            resultCode = 0,
            session = session.copy(probeHandle = probeHandle),
        )
    }

    internal fun awaitFirstFrameProbe(
        session: LibVlcFrameProbeSession,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): LibVlcFrameProbeResult {
        val deadlineMs = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0L)
        while (System.currentTimeMillis() <= deadlineMs) {
            val hasMetadata = session.metadataFile.isFile && session.metadataFile.length() > 0L
            val hasPayload =
                (session.previewFile.isFile && session.previewFile.length() > 0L) ||
                    (session.lumaFile.isFile && session.lumaFile.length() > 0L) ||
                    (session.rawFrameFile.isFile && session.rawFrameFile.length() > 0L)
            if (hasMetadata && hasPayload) {
                return LibVlcFrameProbeResult(
                    success = true,
                    resultCode = 0,
                    metadataFile = session.metadataFile,
                    previewFile = session.previewFile,
                    lumaFile = session.lumaFile,
                    rawFrameFile = session.rawFrameFile,
                )
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return LibVlcFrameProbeResult(
            success = false,
            resultCode = WAIT_TIMEOUT,
            metadataFile = session.metadataFile,
            previewFile = session.previewFile,
            lumaFile = session.lumaFile,
            rawFrameFile = session.rawFrameFile,
        )
    }

    internal fun releaseProbe(session: LibVlcFrameProbeSession) {
        runCatching {
            invoker().releaseProbe(
                playerInstance = session.playerInstance,
                probeHandle = session.probeHandle,
            )
        }.onFailure { error ->
            Log.w(TAG, "libVLC frame probe release failed", error)
        }
    }

    companion object {
        const val INVALID_PLAYER_INSTANCE: Int = -2001
        const val PROBE_CREATION_FAILED: Int = -2002
        const val NATIVE_CALL_FAILED: Int = -2003
        const val WAIT_TIMEOUT: Int = -2004
        const val DEFAULT_VIDEO_OUTPUT_MODULE: String = LibVlcVmemStreamBridge.DEFAULT_VIDEO_OUTPUT_MODULE
        const val DEFAULT_WINDOW_MODULE: String = LibVlcVmemStreamBridge.DEFAULT_WINDOW_MODULE
        const val DEFAULT_DECODER_DEVICE: String = LibVlcVmemStreamBridge.DEFAULT_DECODER_DEVICE

        private const val DEFAULT_TIMEOUT_MS: Long = 10_000L
        private const val POLL_INTERVAL_MS: Long = 100L
        private const val TAG = "LibVlcFrameProbe"
    }

    private fun invoker(): LibVlcFrameProbeInvoker = nativeInvoker ?: JniLibVlcFrameProbeInvoker
}

internal data class LibVlcFrameProbeArmResult(
    val success: Boolean,
    val resultCode: Int,
    val session: LibVlcFrameProbeSession? = null,
)

internal data class LibVlcFrameProbeSession(
    val playerInstance: Long,
    val probeHandle: Long,
    val captureLabel: String,
    val metadataFile: File,
    val previewFile: File,
    val lumaFile: File,
    val rawFrameFile: File,
)

data class LibVlcFrameProbeResult(
    val success: Boolean,
    val resultCode: Int,
    val metadataFile: File,
    val previewFile: File,
    val lumaFile: File,
    val rawFrameFile: File,
)

internal interface LibVlcFrameProbeInvoker {
    fun createProbe(
        metadataFile: File,
        previewFile: File,
        lumaFile: File,
        rawFrameFile: File,
        preferredOutputChroma: String?,
    ): Long

    fun attachProbe(
        playerInstance: Long,
        probeHandle: Long,
        windowWidth: Int,
        windowHeight: Int,
    ): Int

    fun releaseProbe(
        playerInstance: Long,
        probeHandle: Long,
    )
}

private object JniLibVlcFrameProbeInvoker : LibVlcFrameProbeInvoker {
    override fun createProbe(
        metadataFile: File,
        previewFile: File,
        lumaFile: File,
        rawFrameFile: File,
        preferredOutputChroma: String?,
    ): Long = LibVlcNativeFrameProbeBindings.createProbe(
        metadataPath = metadataFile.absolutePath,
        previewPath = previewFile.absolutePath,
        lumaPath = lumaFile.absolutePath,
        rawFramePath = rawFrameFile.absolutePath,
        preferredOutputChroma = preferredOutputChroma,
    )

    override fun attachProbe(
        playerInstance: Long,
        probeHandle: Long,
        windowWidth: Int,
        windowHeight: Int,
    ): Int = LibVlcNativeFrameProbeBindings.attachProbe(
        playerInstance = playerInstance,
        probeHandle = probeHandle,
        windowWidth = windowWidth,
        windowHeight = windowHeight,
    )

    override fun releaseProbe(
        playerInstance: Long,
        probeHandle: Long,
    ) {
        LibVlcNativeFrameProbeBindings.releaseProbe(
            playerInstance = playerInstance,
            probeHandle = probeHandle,
        )
    }
}

internal object LibVlcNativeFrameProbeBindings {
    init {
        System.loadLibrary("miruplay_vlcbridge")
    }

    external fun createProbe(
        metadataPath: String,
        previewPath: String,
        lumaPath: String,
        rawFramePath: String,
        preferredOutputChroma: String?,
    ): Long

    external fun attachProbe(
        playerInstance: Long,
        probeHandle: Long,
        windowWidth: Int,
        windowHeight: Int,
    ): Int

    external fun releaseProbe(
        playerInstance: Long,
        probeHandle: Long,
    )
}

private fun sanitizeFrameProbeLabel(label: String): String =
    label.replace(Regex("[^A-Za-z0-9._-]"), "_")

internal fun preferredLibVlcProbeOutputChroma(
    signalDescriptor: VideoSignalDescriptor?,
): String? = when {
    // Let libVLC keep the decoder's native output chroma for VMEM capture.
    // Forcing a different HDR target format looked simpler for preview generation,
    // but on the target box it prevents the VMEM path from producing a stable first frame.
    (signalDescriptor?.bitDepth ?: 0) > 10 -> null
    else -> null
}

internal fun resolvePreferredLibVlcProbeOutputChroma(
    signalDescriptor: VideoSignalDescriptor?,
    debugConfig: LibVlcDebugConfig = LibVlcDebugConfig(),
): String? = debugConfig.displayChroma ?: preferredLibVlcProbeOutputChroma(signalDescriptor)

private fun sanitizeProbeOutputChroma(preferredOutputChroma: String?): String? =
    preferredOutputChroma
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.length == 4 }
