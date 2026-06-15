package com.miruplay.tv.ui.player

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import com.miruplay.tv.core.common.logging.MiruLog
import java.io.File
import java.io.FileOutputStream

internal sealed interface CapturableVideoView {
    val view: View

    data class Texture(override val view: TextureView) : CapturableVideoView

    data class Surface(override val view: SurfaceView) : CapturableVideoView
}

internal fun findCapturableVideoView(root: View): CapturableVideoView? =
    findTextureVideoView(root)?.let(CapturableVideoView::Texture)
        ?: findSurfaceVideoView(root)?.let(CapturableVideoView::Surface)

internal fun PlayerView.captureCurrentFrame(
    label: String,
    onFrameCaptured: ((String) -> Unit)? = null,
): Boolean {
    val capturableVideoView = findCapturableVideoView(this)
    if (capturableVideoView == null) {
        MiruLog.w(
            "PlayerViewFrameCapture",
            "Skipping player frame capture because no capturable video view was found",
            attributes = mapOf("label" to label),
        )
        return false
    }
    return when (capturableVideoView) {
        is CapturableVideoView.Texture -> captureFromTextureView(
            textureView = capturableVideoView.view,
            label = label,
            onFrameCaptured = onFrameCaptured,
        )

        is CapturableVideoView.Surface -> captureFromSurfaceView(
            surfaceView = capturableVideoView.view,
            label = label,
            onFrameCaptured = onFrameCaptured,
        )
    }
}

private fun findTextureVideoView(root: View): TextureView? {
    return when (root) {
        is TextureView -> root
        is ViewGroup -> {
            for (index in 0 until root.childCount) {
                val match = findTextureVideoView(root.getChildAt(index))
                if (match != null) {
                    return match
                }
            }
            null
        }

        else -> null
    }
}

private fun findSurfaceVideoView(root: View): SurfaceView? {
    return when (root) {
        is SurfaceView -> root
        is ViewGroup -> {
            for (index in 0 until root.childCount) {
                val match = findSurfaceVideoView(root.getChildAt(index))
                if (match != null) {
                    return match
                }
            }
            null
        }

        else -> null
    }
}

private fun captureFromTextureView(
    textureView: TextureView,
    label: String,
    onFrameCaptured: ((String) -> Unit)?,
): Boolean {
    if (!textureView.isAvailable || textureView.width <= 0 || textureView.height <= 0) {
        MiruLog.w(
            "PlayerViewFrameCapture",
            "Skipping player texture frame capture because texture view is unavailable",
            attributes = mapOf(
                "label" to label,
                "available" to textureView.isAvailable.toString(),
                "width" to textureView.width.toString(),
                "height" to textureView.height.toString(),
            ),
        )
        return false
    }
    return runCatching {
        val bitmap = textureView.bitmap ?: return@runCatching false
        saveBitmap(
            bitmap = bitmap,
            label = label,
            outputDirName = "MiruPlayPlayerViewCaptures",
            viewType = "texture",
            width = textureView.width,
            height = textureView.height,
            baseDir = textureView.context.filesDir,
            onFrameCaptured = onFrameCaptured,
        )
        true
    }.getOrElse { error ->
        Log.e("PlayerViewFrameCapture", "Failed to capture texture frame for $label", error)
        MiruLog.e(
            "PlayerViewFrameCapture",
            "Failed to capture player texture frame",
            error,
            mapOf("label" to label),
        )
        false
    }
}

private fun captureFromSurfaceView(
    surfaceView: SurfaceView,
    label: String,
    onFrameCaptured: ((String) -> Unit)?,
): Boolean {
    if (surfaceView.width <= 0 || surfaceView.height <= 0) {
        MiruLog.w(
            "PlayerViewFrameCapture",
            "Skipping player surface frame capture because size is invalid",
            attributes = mapOf(
                "label" to label,
                "width" to surfaceView.width.toString(),
                "height" to surfaceView.height.toString(),
            ),
        )
        return false
    }
    val surface = surfaceView.holder?.surface
    if (surface == null || !surface.isValid) {
        MiruLog.w(
            "PlayerViewFrameCapture",
            "Skipping player surface frame capture because surface is invalid",
            attributes = mapOf(
                "label" to label,
                "surface_present" to (surface != null).toString(),
                "width" to surfaceView.width.toString(),
                "height" to surfaceView.height.toString(),
            ),
        )
        return false
    }
    val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
    return runCatching {
        PixelCopy.request(
            surfaceView,
            bitmap,
            { result ->
                if (result != PixelCopy.SUCCESS) {
                    bitmap.recycle()
                    MiruLog.w(
                        "PlayerViewFrameCapture",
                        "Player surface PixelCopy failed",
                        attributes = mapOf(
                            "label" to label,
                            "result" to result.toString(),
                        ),
                    )
                    return@request
                }
                saveBitmap(
                    bitmap = bitmap,
                    label = label,
                    outputDirName = "MiruPlayPlayerViewCaptures",
                    viewType = "surface",
                    width = surfaceView.width,
                    height = surfaceView.height,
                    baseDir = surfaceView.context.filesDir,
                    onFrameCaptured = onFrameCaptured,
                )
            },
            Handler(Looper.getMainLooper()),
        )
        true
    }.getOrElse { error ->
        bitmap.recycle()
        Log.e("PlayerViewFrameCapture", "PixelCopy request failed for $label", error)
        MiruLog.e(
            "PlayerViewFrameCapture",
            "Player surface PixelCopy threw before completion",
            error,
            mapOf("label" to label),
        )
        false
    }
}

private fun saveBitmap(
    bitmap: Bitmap,
    label: String,
    outputDirName: String,
    viewType: String,
    width: Int,
    height: Int,
    baseDir: File,
    onFrameCaptured: ((String) -> Unit)?,
) {
    runCatching {
        val outputDir = File(baseDir, outputDirName).apply { mkdirs() }
        val outputFile = File(outputDir, "${sanitizeCaptureLabel(label)}.png")
        FileOutputStream(outputFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
        Log.i(
            "PlayerViewFrameCapture",
            "Captured player frame label=$label type=$viewType path=${outputFile.absolutePath}",
        )
        MiruLog.i(
            "PlayerViewFrameCapture",
            "Captured player frame",
            mapOf(
                "label" to label,
                "path" to outputFile.absolutePath,
                "view_type" to viewType,
                "width" to width.toString(),
                "height" to height.toString(),
            ),
        )
        onFrameCaptured?.invoke(label)
    }.getOrElse { error ->
        bitmap.recycle()
        Log.e("PlayerViewFrameCapture", "Failed to persist player frame for $label", error)
        MiruLog.e(
            "PlayerViewFrameCapture",
            "Failed to persist player frame",
            error,
            mapOf("label" to label),
        )
    }
}

private fun sanitizeCaptureLabel(label: String): String =
    label.replace(Regex("[^A-Za-z0-9._-]"), "_")
