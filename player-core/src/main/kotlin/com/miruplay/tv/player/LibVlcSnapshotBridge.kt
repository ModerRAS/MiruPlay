package com.miruplay.tv.player

import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibVlcSnapshotBridge @Inject constructor() {
    private var nativeInvoker: LibVlcSnapshotInvoker = JniLibVlcSnapshotInvoker

    internal constructor(nativeInvoker: LibVlcSnapshotInvoker) : this() {
        this.nativeInvoker = nativeInvoker
    }

    fun takeSnapshot(
        playerInstance: Long,
        outputFile: File,
        width: Int = 0,
        height: Int = 0,
    ): LibVlcSnapshotResult {
        if (playerInstance == 0L) {
            return LibVlcSnapshotResult(
                success = false,
                resultCode = INVALID_PLAYER_INSTANCE,
                outputFile = outputFile,
            )
        }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }
        val resultCode = runCatching {
            nativeInvoker.takeSnapshot(
                playerInstance = playerInstance,
                outputPath = outputFile.absolutePath,
                width = width.coerceAtLeast(0),
                height = height.coerceAtLeast(0),
            )
        }.getOrElse { error ->
            Log.w(
                TAG,
                "libVLC native snapshot invocation failed for ${outputFile.absolutePath}",
                error,
            )
            return LibVlcSnapshotResult(
                success = false,
                resultCode = NATIVE_CALL_FAILED,
                outputFile = outputFile,
            )
        }
        if (resultCode != 0) {
            return LibVlcSnapshotResult(
                success = false,
                resultCode = resultCode,
                outputFile = outputFile,
            )
        }
        repeat(20) {
            if (outputFile.isFile && outputFile.length() > 0L) {
                return LibVlcSnapshotResult(
                    success = true,
                    resultCode = resultCode,
                    outputFile = outputFile,
                )
            }
            Thread.sleep(50L)
        }
        return LibVlcSnapshotResult(
            success = false,
            resultCode = OUTPUT_FILE_MISSING,
            outputFile = outputFile,
        )
    }

    companion object {
        const val INVALID_PLAYER_INSTANCE: Int = -1001
        const val OUTPUT_FILE_MISSING: Int = -1002
        const val NATIVE_CALL_FAILED: Int = -1003

        private const val TAG = "LibVlcSnapshotBridge"
    }
}

data class LibVlcSnapshotResult(
    val success: Boolean,
    val resultCode: Int,
    val outputFile: File,
)

internal interface LibVlcSnapshotInvoker {
    fun takeSnapshot(
        playerInstance: Long,
        outputPath: String,
        width: Int,
        height: Int,
    ): Int
}

private object JniLibVlcSnapshotInvoker : LibVlcSnapshotInvoker {
    override fun takeSnapshot(
        playerInstance: Long,
        outputPath: String,
        width: Int,
        height: Int,
    ): Int = LibVlcNativeSnapshotBindings.takeSnapshot(
        playerInstance = playerInstance,
        outputPath = outputPath,
        width = width,
        height = height,
    )
}

internal object LibVlcNativeSnapshotBindings {
    init {
        System.loadLibrary("miruplay_vlcbridge")
    }

    external fun takeSnapshot(
        playerInstance: Long,
        outputPath: String,
        width: Int,
        height: Int,
    ): Int
}
