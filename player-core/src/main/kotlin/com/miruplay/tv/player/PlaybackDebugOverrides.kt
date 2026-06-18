package com.miruplay.tv.player

import android.util.Log
import com.miruplay.tv.model.VideoColorPrimaries
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.model.VideoTransferCharacteristic
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackDebugOverrides @Inject constructor() {
    @Volatile
    var forcedVideoSignalDescriptor: VideoSignalDescriptor? = null

    @Volatile
    var pendingGlFrameCaptureLabel: String? = null

    @Volatile
    var pendingLibVlcNativeSnapshotLabel: String? = null

    @Volatile
    var libVlcDebugConfig: LibVlcDebugConfig = LibVlcDebugConfig()

    @Volatile
    var skipLibVlcStartupProbe: Boolean = false

    @Volatile
    var skipLibVlcStartupOptions: Boolean = false

    @Synchronized
    fun consumePendingGlFrameCaptureLabel(): String? {
        val label = pendingGlFrameCaptureLabel
        logNativeSnapshotLabel("consumePendingGlFrameCaptureLabel", before = label, after = null)
        pendingGlFrameCaptureLabel = null
        return label
    }

    @Synchronized
    fun peekPendingGlFrameCaptureLabel(): String? = pendingGlFrameCaptureLabel

    @Synchronized
    fun clearPendingGlFrameCaptureLabel(label: String) {
        if (pendingGlFrameCaptureLabel == label) {
            logNativeSnapshotLabel(
                action = "clearPendingGlFrameCaptureLabel",
                before = pendingGlFrameCaptureLabel,
                after = null,
            )
            pendingGlFrameCaptureLabel = null
        }
    }

    @Synchronized
    fun consumePendingLibVlcNativeSnapshotLabel(): String? {
        val label = pendingLibVlcNativeSnapshotLabel
        logNativeSnapshotLabel(
            action = "consumePendingLibVlcNativeSnapshotLabel",
            before = label,
            after = null,
        )
        pendingLibVlcNativeSnapshotLabel = null
        return label
    }

    @Synchronized
    fun peekPendingLibVlcNativeSnapshotLabel(): String? {
        val label = pendingLibVlcNativeSnapshotLabel
        logNativeSnapshotLabel(
            action = "peekPendingLibVlcNativeSnapshotLabel",
            before = label,
            after = label,
        )
        return label
    }

    @Synchronized
    fun requestPendingLibVlcNativeSnapshotLabel(label: String) {
        logNativeSnapshotLabel(
            action = "requestPendingLibVlcNativeSnapshotLabel",
            before = pendingLibVlcNativeSnapshotLabel,
            after = label,
        )
        pendingLibVlcNativeSnapshotLabel = label
    }

    @Synchronized
    fun clearPendingLibVlcNativeSnapshotLabel(label: String) {
        if (pendingLibVlcNativeSnapshotLabel == label) {
            logNativeSnapshotLabel(
                action = "clearPendingLibVlcNativeSnapshotLabel",
                before = pendingLibVlcNativeSnapshotLabel,
                after = null,
            )
            pendingLibVlcNativeSnapshotLabel = null
        }
    }

    private fun logNativeSnapshotLabel(
        action: String,
        before: String?,
        after: String?,
    ) {
        runCatching {
            Log.i(
                "PlaybackDebugOverrides",
                "native_snapshot_label action=$action before=${before.orEmpty()} after=${after.orEmpty()}",
            )
        }
    }
}

data class LibVlcDebugConfig(
    val hwMode: LibVlcHardwareAccelerationMode = LibVlcHardwareAccelerationMode.FULL,
    val voutMode: LibVlcVoutMode = LibVlcVoutMode.DEFAULT,
    val displayChroma: String? = null,
)

enum class LibVlcHardwareAccelerationMode {
    FULL,
    DECODING_ONLY,
    DISABLED,
}

enum class LibVlcVoutMode {
    DEFAULT,
    DIRECT_TEXTURE,
    GL_SURFACE,
    OUTPUT_CALLBACKS,
    ANDROID_DISPLAY,
    VMEM_STREAM,
    VMEM_PROBE,
}

fun forcedVideoSignalDescriptorFor(
    signalKind: VideoSignalKind?,
): VideoSignalDescriptor? =
    when (signalKind) {
        VideoSignalKind.HDR10 -> VideoSignalDescriptor(
            signalKind = VideoSignalKind.HDR10,
            transfer = VideoTransferCharacteristic.PQ,
            colorPrimaries = VideoColorPrimaries.BT2020,
            bitDepth = 10,
            hasHdrStaticMetadata = true,
        )
        VideoSignalKind.HDR10_PLUS -> VideoSignalDescriptor(
            signalKind = VideoSignalKind.HDR10_PLUS,
            transfer = VideoTransferCharacteristic.PQ,
            colorPrimaries = VideoColorPrimaries.BT2020,
            bitDepth = 10,
            hasHdrStaticMetadata = true,
            hasHdr10PlusMetadata = true,
        )
        VideoSignalKind.DOLBY_VISION -> VideoSignalDescriptor(
            signalKind = VideoSignalKind.DOLBY_VISION,
            transfer = VideoTransferCharacteristic.PQ,
            colorPrimaries = VideoColorPrimaries.BT2020,
            bitDepth = 10,
        )
        VideoSignalKind.UNKNOWN_HDR -> VideoSignalDescriptor(
            signalKind = VideoSignalKind.UNKNOWN_HDR,
            transfer = VideoTransferCharacteristic.PQ,
            colorPrimaries = VideoColorPrimaries.BT2020,
            bitDepth = 10,
        )
        VideoSignalKind.SDR -> VideoSignalDescriptor(
            signalKind = VideoSignalKind.SDR,
            transfer = VideoTransferCharacteristic.SDR,
            colorPrimaries = VideoColorPrimaries.BT709,
            bitDepth = 8,
        )
        null -> null
    }
