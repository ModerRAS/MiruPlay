package com.miruplay.tv.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.player.LibVlcDirectVideoHost
import com.miruplay.tv.player.LibVlcOutputCallbackVideoHost
import com.miruplay.tv.player.LibVlcSurfaceVideoHost
import com.miruplay.tv.player.LibVlcVmemStreamBridge
import com.miruplay.tv.player.LibVlcVmemStreamSession
import com.miruplay.tv.player.LibVlcVmemVideoHost
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.FileOutputStream

class LibVlcTextureVideoHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : VLCVideoLayout(context, attrs, defStyleAttr), LibVlcDirectVideoHost, LibVlcSurfaceVideoHost,
    LibVlcOutputCallbackVideoHost, LibVlcVmemVideoHost {
    private companion object {
        const val HIDDEN_CARRIER_SIZE_PX = 1
        const val HIDDEN_CARRIER_OFFSET_PX = -10_000f
    }

    private var onFrameCaptured: ((String) -> Unit)? = null
    private var surfaceVideoHostEnabled = false
    private var directTextureEnabled = false
    private var outputCallbackEnabled = false
    private var vmemStreamEnabled = false
    private var onSurfaceVideoHostReadyChanged: ((Boolean) -> Unit)? = null
    private var onOutputCallbackReadyChanged: ((Boolean) -> Unit)? = null
    private val debugOverlayTextureView = TextureView(context).also { texture ->
        addView(
            texture,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }
    private val glVideoSurfaceView = GLVideoSurfaceView(context).also { glView ->
        addView(
            glView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        glView.visibility = View.INVISIBLE
        glView.setOnFrameCaptured { label ->
            onFrameCaptured?.invoke(label)
        }
        glView.setOnVideoSurfaceReadyChanged { ready ->
            onSurfaceVideoHostReadyChanged?.invoke(ready)
        }
    }
    private val outputCallbackSurfaceView = LibVlcImageReaderOutputView(context).also { outputView ->
        addView(
            outputView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        outputView.visibility = View.GONE
        outputView.setOnFrameCaptured { label ->
            onFrameCaptured?.invoke(label)
        }
        outputView.setOnOutputSurfaceReadyChanged { ready ->
            onOutputCallbackReadyChanged?.invoke(ready)
        }
    }
    private val vmemVideoSurfaceView = LibVlcVmemVideoSurfaceView(context).also { vmemView ->
        addView(
            vmemView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        vmemView.visibility = View.INVISIBLE
        vmemView.setOnFrameCaptured { label ->
            onFrameCaptured?.invoke(label)
        }
    }

    init {
        debugOverlayTextureView.isOpaque = false
        debugOverlayTextureView.visibility = View.VISIBLE
        debugOverlayTextureView.alpha = 0f
        debugOverlayTextureView.isClickable = false
        debugOverlayTextureView.isFocusable = false
        debugOverlayTextureView.isFocusableInTouchMode = false
    }

    fun setOnFrameCaptured(listener: ((String) -> Unit)?) {
        onFrameCaptured = listener
        glVideoSurfaceView.setOnFrameCaptured(listener)
        outputCallbackSurfaceView.setOnFrameCaptured(listener)
        vmemVideoSurfaceView.setOnFrameCaptured(listener)
    }

    override fun libVlcDirectVideoTextureView(): TextureView? = findManagedLibVlcTextureView()

    override fun setLibVlcDirectTextureEnabled(enabled: Boolean) {
        directTextureEnabled = enabled
        updateDirectVideoTextureVisibility()
        updateManagedTextureCarrierVisibility()
    }

    override fun libVlcOutputCallbackSurface(): Surface? = outputCallbackSurfaceView.outputSurface()

    override fun libVlcOutputCallbackView(): View = outputCallbackSurfaceView

    override fun libVlcOutputCallbackWidth(): Int =
        width.takeIf { it > 0 } ?: outputCallbackSurfaceView.width

    override fun libVlcOutputCallbackHeight(): Int =
        height.takeIf { it > 0 } ?: outputCallbackSurfaceView.height

    override fun setOnLibVlcOutputCallbackReadyChanged(listener: ((Boolean) -> Unit)?) {
        onOutputCallbackReadyChanged = listener
        listener?.invoke(
            isLibVlcOutputCallbackAttachReady(
                surfacePresent = outputCallbackSurfaceView.outputSurface() != null,
                hostWidth = libVlcOutputCallbackWidth(),
                hostHeight = libVlcOutputCallbackHeight(),
            ),
        )
    }

    override fun setLibVlcOutputCallbackEnabled(enabled: Boolean) {
        outputCallbackEnabled = enabled
        outputCallbackSurfaceView.setOutputBound(false)
        if (enabled) {
            outputCallbackSurfaceView.prepareOutputSurface(
                hostWidth = libVlcOutputCallbackWidth().coerceAtLeast(width),
                hostHeight = libVlcOutputCallbackHeight().coerceAtLeast(height),
            )
        }
        updateGlVideoSurfaceVisibility()
        updateOutputCallbackSurfaceVisibility()
        updateVmemVideoSurfaceVisibility()
        updateDirectVideoTextureVisibility()
        updateManagedTextureCarrierVisibility()
    }

    override fun libVlcVmemVideoView(): View = vmemVideoSurfaceView

    override fun setLibVlcVmemStreamEnabled(enabled: Boolean) {
        vmemStreamEnabled = enabled
        updateVmemVideoSurfaceVisibility()
        updateDirectVideoTextureVisibility()
        updateManagedTextureCarrierVisibility()
    }

    override fun bindLibVlcVmemStream(
        bridge: LibVlcVmemStreamBridge?,
        session: LibVlcVmemStreamSession?,
    ) {
        vmemVideoSurfaceView.bindStream(bridge, session)
    }

    override fun libVlcVideoSurface(): Surface? = glVideoSurfaceView.decoderOutputSurface()

    override fun libVlcVideoSurfaceTexture(): SurfaceTexture? =
        glVideoSurfaceView.decoderOutputSurfaceTexture()

    override fun libVlcVideoSurfaceView(): View = glVideoSurfaceView

    override fun libVlcVideoSurfaceWidth(): Int = glVideoSurfaceView.decoderOutputSurfaceWidth()

    override fun libVlcVideoSurfaceHeight(): Int = glVideoSurfaceView.decoderOutputSurfaceHeight()

    override fun setOnLibVlcVideoSurfaceReadyChanged(listener: ((Boolean) -> Unit)?) {
        onSurfaceVideoHostReadyChanged = listener
        listener?.invoke(
            isDecoderVideoSurfaceReady(
                glVideoSurfaceView.decoderOutputSurface(),
                glVideoSurfaceView.decoderOutputSurfaceWidth(),
                glVideoSurfaceView.decoderOutputSurfaceHeight(),
            ),
        )
    }

    override fun setLibVlcVideoSurfaceEnabled(enabled: Boolean) {
        surfaceVideoHostEnabled = enabled
        updateGlVideoSurfaceVisibility()
        updateManagedTextureCarrierVisibility()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (outputCallbackEnabled) {
            outputCallbackSurfaceView.prepareOutputSurface(w, h)
        }
    }

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        if (child is TextureView && child.id == org.videolan.R.id.texture_video) {
            updateManagedTextureCarrierVisibility()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateManagedTextureCarrierVisibility()
        if (vmemStreamEnabled) {
            vmemVideoSurfaceView.bringToFront()
        }
    }

    fun bindToneMappingState(
        ruleSet: ToneMappingRuleSet,
        signalDescriptor: VideoSignalDescriptor?,
    ) {
        glVideoSurfaceView.bind(
            player = null,
            ruleSet = ruleSet,
            signalDescriptor = signalDescriptor,
        )
        outputCallbackSurfaceView.bind(
            ruleSet = ruleSet,
            signalDescriptor = signalDescriptor,
        )
        vmemVideoSurfaceView.bind(
            ruleSet = ruleSet,
            signalDescriptor = signalDescriptor,
        )
    }

    private fun updateGlVideoSurfaceVisibility() {
        val shouldExposeGlSurface = surfaceVideoHostEnabled
        glVideoSurfaceView.visibility = if (shouldExposeGlSurface) View.VISIBLE else View.INVISIBLE
        Log.i(
            "LibVlcTextureVideoHostView",
            "Updated GL surface host visibility visible=${glVideoSurfaceView.visibility} " +
                "surfaceReady=${glVideoSurfaceView.decoderOutputSurface() != null} " +
                "size=${glVideoSurfaceView.decoderOutputSurfaceWidth()}x${glVideoSurfaceView.decoderOutputSurfaceHeight()} " +
                "surfaceHost=$surfaceVideoHostEnabled outputCallbacks=$outputCallbackEnabled",
        )
        glVideoSurfaceView.requestLayout()
        glVideoSurfaceView.invalidate()
    }

    private fun updateOutputCallbackSurfaceVisibility() {
        outputCallbackSurfaceView.visibility = if (outputCallbackEnabled) View.VISIBLE else View.GONE
        Log.i(
            "LibVlcTextureVideoHostView",
            "Updated output callback surface visibility visible=${outputCallbackSurfaceView.visibility} " +
                "surfaceReady=${outputCallbackSurfaceView.outputSurface() != null} " +
                "surfaceSize=${outputCallbackSurfaceView.outputSurfaceWidth()}x${outputCallbackSurfaceView.outputSurfaceHeight()} " +
                "hostSize=${libVlcOutputCallbackWidth()}x${libVlcOutputCallbackHeight()} " +
                "outputCallbacks=$outputCallbackEnabled",
        )
        outputCallbackSurfaceView.requestLayout()
        outputCallbackSurfaceView.invalidate()
    }

    private fun updateVmemVideoSurfaceVisibility() {
        vmemVideoSurfaceView.visibility = if (vmemStreamEnabled) View.VISIBLE else View.INVISIBLE
        Log.i(
            "LibVlcTextureVideoHostView",
            "Updated VMEM surface visibility visible=${vmemVideoSurfaceView.visibility} vmem=$vmemStreamEnabled",
        )
        vmemVideoSurfaceView.requestLayout()
        vmemVideoSurfaceView.invalidate()
    }

    private fun updateManagedTextureCarrierVisibility() {
        val carrier = findManagedLibVlcTextureView() ?: return
        val wantsVisibleDirectTexture = directTextureEnabled && !vmemStreamEnabled
        val targetVisibility = when {
            wantsVisibleDirectTexture -> View.VISIBLE
            vmemStreamEnabled -> View.VISIBLE
            else -> View.GONE
        }
        val targetAlpha = if (wantsVisibleDirectTexture) 1f else 0f
        val targetWidth = if (vmemStreamEnabled) HIDDEN_CARRIER_SIZE_PX else FrameLayout.LayoutParams.MATCH_PARENT
        val targetHeight = if (vmemStreamEnabled) HIDDEN_CARRIER_SIZE_PX else FrameLayout.LayoutParams.MATCH_PARENT
        val targetTranslationX = if (vmemStreamEnabled) HIDDEN_CARRIER_OFFSET_PX else 0f
        val targetTranslationY = if (vmemStreamEnabled) HIDDEN_CARRIER_OFFSET_PX else 0f
        val currentLp = carrier.layoutParams
        val updatedLp = when (currentLp) {
            is FrameLayout.LayoutParams -> currentLp
            null -> FrameLayout.LayoutParams(targetWidth, targetHeight)
            else -> FrameLayout.LayoutParams(currentLp)
        }
        var layoutChanged = false
        if (updatedLp.width != targetWidth) {
            updatedLp.width = targetWidth
            layoutChanged = true
        }
        if (updatedLp.height != targetHeight) {
            updatedLp.height = targetHeight
            layoutChanged = true
        }
        if (layoutChanged || carrier.layoutParams !== updatedLp) {
            carrier.layoutParams = updatedLp
        }
        if (carrier.visibility != targetVisibility) {
            carrier.visibility = targetVisibility
        }
        if (carrier.alpha != targetAlpha) {
            carrier.alpha = targetAlpha
        }
        if (carrier.translationX != targetTranslationX) {
            carrier.translationX = targetTranslationX
        }
        if (carrier.translationY != targetTranslationY) {
            carrier.translationY = targetTranslationY
        }
        carrier.requestLayout()
        carrier.invalidate()
        Log.i(
            "LibVlcTextureVideoHostView",
            "Updated managed texture carrier visibility visible=${carrier.visibility} " +
                "alpha=${carrier.alpha} direct=$directTextureEnabled vmem=$vmemStreamEnabled " +
                "translation=${carrier.translationX},${carrier.translationY} " +
                "width=${carrier.width} height=${carrier.height}",
        )
    }

    fun debugOverlayTextureViewForTest(): TextureView = debugOverlayTextureView

    fun managedVideoTextureViewForTest(): TextureView? = findManagedLibVlcTextureView()

    fun glVideoSurfaceViewForTest(): GLVideoSurfaceView = glVideoSurfaceView

    fun outputCallbackSurfaceViewForTest(): LibVlcImageReaderOutputView = outputCallbackSurfaceView

    fun vmemVideoSurfaceViewForTest(): LibVlcVmemVideoSurfaceView = vmemVideoSurfaceView

    private fun updateDirectVideoTextureVisibility() {
        debugOverlayTextureView.visibility = if (vmemStreamEnabled) View.GONE else View.VISIBLE
        debugOverlayTextureView.alpha = 0f
        debugOverlayTextureView.translationX = 0f
        debugOverlayTextureView.translationY = 0f
        debugOverlayTextureView.requestLayout()
        debugOverlayTextureView.invalidate()
        Log.i(
            "LibVlcTextureVideoHostView",
            "Updated direct/output callback texture visibility " +
                "visible=${debugOverlayTextureView.visibility} " +
                "alpha=${debugOverlayTextureView.alpha} " +
                "direct=$directTextureEnabled output=$outputCallbackEnabled " +
                "available=${debugOverlayTextureView.isAvailable} " +
                "width=${debugOverlayTextureView.width} height=${debugOverlayTextureView.height}",
        )
    }

    fun captureCurrentFrame(label: String): Boolean {
        if (vmemStreamEnabled) {
            vmemVideoSurfaceView.captureNextRenderedFrame(label)
            return true
        }
        if (outputCallbackEnabled) {
            outputCallbackSurfaceView.captureNextRenderedFrame(label)
            return true
        }
        if (surfaceVideoHostEnabled) {
            glVideoSurfaceView.captureNextRenderedFrame(label)
            return true
        }
        val directTextureCapture = if (directTextureEnabled || outputCallbackEnabled) {
            captureFromTextureViewIfAvailable(
                textureView = findManagedLibVlcTextureView(),
                label = label,
                source = "direct_texture",
            )
        } else {
            null
        }
        if (directTextureCapture == true) {
            return true
        }
        val surfaceViews = findCapturableSurfaceViews()
        if (surfaceViews.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (captureFromSurfaceViews(surfaceViews, label)) {
                return true
            }
        }
        val textureView = findNestedTextureView()
        if (textureView != null) {
            val nestedTextureCapture = captureFromTextureViewIfAvailable(
                textureView = textureView,
                label = label,
                source = "nested_texture",
            )
            if (nestedTextureCapture == true) {
                return true
            }
        }
        if (captureFromWindowBounds(label = label, source = "window")) {
            return true
        }
        MiruLog.w(
            "LibVlcTextureVideoHostView",
            "Skipping libVLC frame capture because no capturable video output was available",
            attributes = mapOf("label" to label),
        )
        return false
    }

    private fun captureFromSurfaceViews(
        surfaceViews: List<SurfaceView>,
        label: String,
        startIndex: Int = 0,
    ): Boolean {
        val surfaceView = surfaceViews.getOrNull(startIndex) ?: return false
        return captureFromSurfaceView(
            surfaceViews = surfaceViews,
            surfaceView = surfaceView,
            label = label,
            surfaceIndex = startIndex,
        )
    }

    private fun captureFromTextureViewIfAvailable(
        textureView: TextureView?,
        label: String,
        source: String,
    ): Boolean? {
        textureView ?: return null
        if (textureView.visibility != View.VISIBLE ||
            !textureView.isAvailable ||
            textureView.width <= 0 ||
            textureView.height <= 0
        ) {
            MiruLog.w(
                "LibVlcTextureVideoHostView",
                "Skipping libVLC texture capture because texture view is unavailable",
                attributes = mapOf(
                    "label" to label,
                    "source" to source,
                    "visibility" to textureView.visibility.toString(),
                    "available" to textureView.isAvailable.toString(),
                    "width" to textureView.width.toString(),
                    "height" to textureView.height.toString(),
                ),
            )
            Log.w(
                "LibVlcTextureVideoHostView",
                "Texture capture unavailable label=$label source=$source visibility=${textureView.visibility} available=${textureView.isAvailable} width=${textureView.width} height=${textureView.height}",
            )
            return null
        }
        return runCatching {
            val bitmap = textureView.bitmap ?: return@runCatching false
            val stats = analyzeDebugCaptureBitmap(bitmap)
            if (stats.isAllBlack) {
                bitmap.recycle()
                MiruLog.w(
                    "LibVlcTextureVideoHostView",
                    "Dropping libVLC texture capture because sampled bitmap is fully black",
                    attributes = mapOf(
                        "label" to label,
                        "source" to source,
                        "sample_count" to stats.sampleCount.toString(),
                        "non_black_samples" to stats.nonBlackSamples.toString(),
                        "max_channel_value" to stats.maxChannelValue.toString(),
                    ),
                )
                Log.w(
                    "LibVlcTextureVideoHostView",
                    "Texture capture was fully black label=$label source=$source maxChannel=${stats.maxChannelValue}",
                )
                return@runCatching captureFromWindowBounds(
                    label = label,
                    source = "${source}_window_fallback",
                )
            }
            saveTextureBitmap(
                bitmap = bitmap,
                label = label,
                width = textureView.width,
                height = textureView.height,
                source = source,
                stats = stats,
            )
            true
        }.getOrElse { error ->
            Log.e("LibVlcTextureVideoHostView", "Failed to capture frame for $label", error)
            MiruLog.e(
                "LibVlcTextureVideoHostView",
                "Failed to capture libVLC texture frame",
                error,
                mapOf(
                    "label" to label,
                    "source" to source,
                ),
            )
            false
        }
    }

    private fun captureFromSurfaceView(
        surfaceViews: List<SurfaceView>,
        surfaceView: SurfaceView,
        label: String,
        surfaceIndex: Int,
    ): Boolean {
        val source = buildSurfaceCaptureSource(surfaceView)
        if (surfaceView.width <= 0 || surfaceView.height <= 0) {
            MiruLog.w(
                "LibVlcTextureVideoHostView",
                "Skipping PixelCopy frame capture because surface size is invalid",
                attributes = mapOf(
                    "label" to label,
                    "source" to source,
                    "surface_index" to surfaceIndex.toString(),
                    "surface_count" to surfaceViews.size.toString(),
                    "width" to surfaceView.width.toString(),
                    "height" to surfaceView.height.toString(),
                ),
            )
            return captureNextSurfaceOrWindow(
                surfaceViews = surfaceViews,
                label = label,
                nextSurfaceIndex = surfaceIndex + 1,
                fallbackSource = "${source}_window_fallback_invalid_size",
            )
        }
        val surface = surfaceView.holder?.surface
        if (surface == null || !surface.isValid) {
            MiruLog.w(
                "LibVlcTextureVideoHostView",
                "Skipping PixelCopy frame capture because surface is invalid",
                attributes = mapOf(
                    "label" to label,
                    "source" to source,
                    "surface_index" to surfaceIndex.toString(),
                    "surface_count" to surfaceViews.size.toString(),
                    "width" to surfaceView.width.toString(),
                    "height" to surfaceView.height.toString(),
                    "surface_present" to (surface != null).toString(),
                ),
            )
            return captureNextSurfaceOrWindow(
                surfaceViews = surfaceViews,
                label = label,
                nextSurfaceIndex = surfaceIndex + 1,
                fallbackSource = "${source}_window_fallback_invalid_surface",
            )
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
                            "LibVlcTextureVideoHostView",
                            "PixelCopy frame capture failed",
                            attributes = mapOf(
                                "label" to label,
                                "source" to source,
                                "surface_index" to surfaceIndex.toString(),
                                "surface_count" to surfaceViews.size.toString(),
                                "result" to result.toString(),
                            ),
                        )
                        captureNextSurfaceOrWindow(
                            surfaceViews = surfaceViews,
                            label = label,
                            nextSurfaceIndex = surfaceIndex + 1,
                            fallbackSource = "${source}_window_fallback_result_$result",
                        )
                        return@request
                    }
                    val stats = analyzeDebugCaptureBitmap(bitmap)
                    if (stats.isAllBlack) {
                        bitmap.recycle()
                        MiruLog.w(
                            "LibVlcTextureVideoHostView",
                            "Dropping libVLC surface capture because sampled bitmap is fully black",
                            attributes = mapOf(
                                "label" to label,
                                "source" to source,
                                "surface_index" to surfaceIndex.toString(),
                                "surface_count" to surfaceViews.size.toString(),
                                "sample_count" to stats.sampleCount.toString(),
                                "non_black_samples" to stats.nonBlackSamples.toString(),
                                "max_channel_value" to stats.maxChannelValue.toString(),
                            ),
                        )
                        captureNextSurfaceOrWindow(
                            surfaceViews = surfaceViews,
                            label = label,
                            nextSurfaceIndex = surfaceIndex + 1,
                            fallbackSource = "${source}_window_fallback_black",
                        )
                        return@request
                    }
                    saveTextureBitmap(
                        bitmap = bitmap,
                        label = label,
                        width = surfaceView.width,
                        height = surfaceView.height,
                        source = source,
                        stats = stats,
                    )
                },
                Handler(Looper.getMainLooper()),
            )
            true
        }.getOrElse { error ->
            bitmap.recycle()
            MiruLog.w(
                "LibVlcTextureVideoHostView",
                "PixelCopy frame capture threw before request completion",
                error,
                mapOf(
                    "label" to label,
                    "source" to source,
                    "surface_index" to surfaceIndex.toString(),
                    "surface_count" to surfaceViews.size.toString(),
                ),
            )
            captureNextSurfaceOrWindow(
                surfaceViews = surfaceViews,
                label = label,
                nextSurfaceIndex = surfaceIndex + 1,
                fallbackSource = "${source}_window_fallback_exception",
            )
        }
    }

    private fun captureNextSurfaceOrWindow(
        surfaceViews: List<SurfaceView>,
        label: String,
        nextSurfaceIndex: Int,
        fallbackSource: String,
    ): Boolean {
        return if (nextSurfaceIndex < surfaceViews.size) {
            captureFromSurfaceViews(
                surfaceViews = surfaceViews,
                label = label,
                startIndex = nextSurfaceIndex,
            )
        } else {
            captureFromWindowBounds(
                label = label,
                source = fallbackSource,
            )
        }
    }

    private fun captureFromWindowBounds(
        label: String,
        source: String,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        if (!isAttachedToWindow) {
            return false
        }
        val activity = context.findActivity() ?: return false
        val bounds = resolveWindowCaptureBounds(this) ?: return false
        val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        return runCatching {
            PixelCopy.request(
                activity.window,
                bounds,
                bitmap,
                { result ->
                    if (result != PixelCopy.SUCCESS) {
                        bitmap.recycle()
                        MiruLog.w(
                            "LibVlcTextureVideoHostView",
                            "Window PixelCopy frame capture failed",
                            attributes = mapOf(
                                "label" to label,
                                "source" to source,
                                "result" to result.toString(),
                            ),
                        )
                        return@request
                    }
                    val stats = analyzeDebugCaptureBitmap(bitmap)
                    if (stats.isAllBlack) {
                        bitmap.recycle()
                        MiruLog.w(
                            "LibVlcTextureVideoHostView",
                            "Dropping libVLC window capture because sampled bitmap is fully black",
                            attributes = mapOf(
                                "label" to label,
                                "source" to source,
                                "sample_count" to stats.sampleCount.toString(),
                                "non_black_samples" to stats.nonBlackSamples.toString(),
                                "max_channel_value" to stats.maxChannelValue.toString(),
                            ),
                        )
                        return@request
                    }
                    saveTextureBitmap(
                        bitmap = bitmap,
                        label = label,
                        width = bounds.width(),
                        height = bounds.height(),
                        source = source,
                        stats = stats,
                    )
                },
                Handler(Looper.getMainLooper()),
            )
            true
        }.getOrElse { error ->
            bitmap.recycle()
            MiruLog.w(
                "LibVlcTextureVideoHostView",
                "Window PixelCopy frame capture threw before request completion",
                error,
                mapOf(
                    "label" to label,
                    "source" to source,
                ),
            )
            false
        }
    }

    private fun saveTextureBitmap(
        bitmap: Bitmap,
        label: String,
        width: Int,
        height: Int,
        source: String,
        stats: DebugCaptureBitmapStats,
    ) {
        runCatching {
            val outputDir = File(context.filesDir, "MiruPlayLibVlcCaptures").apply { mkdirs() }
            val outputFile = File(outputDir, "${sanitizeLabel(label)}.png")
            FileOutputStream(outputFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            bitmap.recycle()
            Log.i(
                "LibVlcTextureVideoHostView",
                "Captured libVLC texture frame label=$label source=$source path=${outputFile.absolutePath}",
            )
            MiruLog.i(
                "LibVlcTextureVideoHostView",
                "Captured libVLC texture frame",
                mapOf(
                    "label" to label,
                    "source" to source,
                    "path" to outputFile.absolutePath,
                    "width" to width.toString(),
                    "height" to height.toString(),
                    "sample_count" to stats.sampleCount.toString(),
                    "non_black_samples" to stats.nonBlackSamples.toString(),
                    "max_channel_value" to stats.maxChannelValue.toString(),
                ),
            )
            if (shouldTreatCaptureSourceAsVerifiedVideoFrame(source)) {
                onFrameCaptured?.invoke(label)
            } else {
                MiruLog.w(
                    "LibVlcTextureVideoHostView",
                    "Ignoring libVLC debug capture as verification because it came from a fallback/window source",
                    attributes = mapOf(
                        "label" to label,
                        "source" to source,
                    ),
                )
            }
        }.getOrElse { error ->
            bitmap.recycle()
            Log.e("LibVlcTextureVideoHostView", "Failed to persist texture frame for $label", error)
            MiruLog.e(
                "LibVlcTextureVideoHostView",
                "Failed to persist libVLC texture frame",
                error,
                mapOf(
                    "label" to label,
                    "source" to source,
                ),
            )
        }
    }

    private fun findNestedTextureView(): TextureView? =
        collectTextureViews(this)
            .filterNot { it === debugOverlayTextureView }
            .maxByOrNull { it.width * it.height }

    private fun findManagedLibVlcTextureView(): TextureView? =
        runCatching { findViewById<TextureView>(org.videolan.R.id.texture_video) }.getOrNull()

    private fun findCapturableSurfaceViews(): List<SurfaceView> =
        sortSurfaceViewsForCapture(
            collectSurfaceViews(this).filter { surfaceView ->
                surfaceView.visibility == View.VISIBLE &&
                    surfaceView !== glVideoSurfaceView &&
                    surfaceView !== outputCallbackSurfaceView
            },
        )

    private fun collectTextureViews(view: View): List<TextureView> {
        val matches = mutableListOf<TextureView>()
        return when (view) {
            is TextureView -> {
                matches += view
                matches
            }
            is FrameLayout -> {
                for (index in 0 until view.childCount) {
                    matches += collectTextureViews(view.getChildAt(index))
                }
                matches
            }
            else -> emptyList()
        }
    }

    private fun collectSurfaceViews(view: View): List<SurfaceView> {
        val matches = mutableListOf<SurfaceView>()
        return when (view) {
            is SurfaceView -> {
                matches += view
                matches
            }
            is FrameLayout -> {
                for (index in 0 until view.childCount) {
                    matches += collectSurfaceViews(view.getChildAt(index))
                }
                matches
            }
            else -> emptyList()
        }
    }

    private fun sanitizeLabel(label: String): String =
        label.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

internal fun sortSurfaceViewsForCapture(surfaceViews: List<SurfaceView>): List<SurfaceView> =
    surfaceViews.sortedWith(
        compareByDescending<SurfaceView> { surfaceCapturePriorityByEntryName(it.safeResourceEntryName()) }
            .thenByDescending { it.width * it.height }
            .thenBy { it.left }
            .thenBy { it.top },
    )

internal fun surfaceCapturePriorityByEntryName(entryName: String?): Int =
    when (entryName) {
        "surface_video" -> 3
        "surface_subtitles" -> 0
        else -> 2
    }

internal fun shouldTreatCaptureSourceAsVerifiedVideoFrame(source: String): Boolean =
    !source.contains("window_fallback", ignoreCase = true) &&
        !source.equals("window", ignoreCase = true)

private fun buildSurfaceCaptureSource(surfaceView: SurfaceView): String =
    surfaceView.safeResourceEntryName() ?: "surface"

private fun View.safeResourceEntryName(): String? =
    if (id == View.NO_ID) {
        null
    } else {
        runCatching { resources.getResourceEntryName(id) }.getOrNull()
    }

internal fun resolveWindowCaptureBounds(view: View): Rect? {
    if (view.width <= 0 || view.height <= 0) {
        return null
    }
    val location = IntArray(2)
    view.getLocationInWindow(location)
    return Rect(
        location[0],
        location[1],
        location[0] + view.width,
        location[1] + view.height,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal data class DebugCaptureBitmapStats(
    val sampleCount: Int,
    val nonBlackSamples: Int,
    val maxChannelValue: Int,
) {
    val isAllBlack: Boolean
        get() = nonBlackSamples == 0 && maxChannelValue == 0
}

internal fun analyzeDebugCaptureBitmap(bitmap: Bitmap): DebugCaptureBitmapStats {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    val stepX = maxOf(1, width / 32)
    val stepY = maxOf(1, height / 32)
    var sampleCount = 0
    var nonBlackSamples = 0
    var maxChannelValue = 0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = bitmap.getPixel(x, y)
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            val pixelMax = maxOf(red, green, blue)
            if (pixelMax > 0) {
                nonBlackSamples += 1
            }
            if (pixelMax > maxChannelValue) {
                maxChannelValue = pixelMax
            }
            sampleCount += 1
            x += stepX
        }
        y += stepY
    }
    return DebugCaptureBitmapStats(
        sampleCount = sampleCount,
        nonBlackSamples = nonBlackSamples,
        maxChannelValue = maxChannelValue,
    )
}
