package com.miruplay.tv.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.ToneMappingCurvePreset
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoSignalKind
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class LibVlcImageReaderOutputView(
    context: Context,
) : GLSurfaceView(context) {
    private val renderer = ImageReaderToneMappingRenderer()
    private val imageReaderThread = HandlerThread("MiruPlayVlcImageReader").apply { start() }
    private val imageReaderHandler = Handler(imageReaderThread.looper)
    private var imageReader: ImageReader? = null
    private var imageReaderSurface: Surface? = null
    private var imageReaderWidth: Int = 0
    private var imageReaderHeight: Int = 0
    private var imageReaderSurfaceConfig = resolveLibVlcImageReaderSurfaceConfig(Build.VERSION.SDK_INT)
    private var outputBound = false
    private var onOutputSurfaceReadyChanged: ((Boolean) -> Unit)? = null
    private var onFrameCaptured: ((String) -> Unit)? = null
    private var hasLoggedFirstImageAvailable = false
    private var hasLoggedFirstQueuedFrame = false
    private var hasLoggedCopyFailure = false
    private var requestedCaptureLabelForTest: String? = null

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun bind(
        ruleSet: ToneMappingRuleSet,
        signalDescriptor: VideoSignalDescriptor?,
    ) {
        queueEvent {
            renderer.updateConfig(ruleSet, signalDescriptor)
        }
        requestRender()
    }

    fun outputSurface(): Surface? = imageReaderSurface

    fun outputSurfaceWidth(): Int = imageReaderWidth

    fun outputSurfaceHeight(): Int = imageReaderHeight

    fun setOutputBound(enabled: Boolean) {
        outputBound = enabled
        MiruLog.i(
            TAG,
            "Updated libVLC ImageReader output bound state",
            mapOf(
                "enabled" to enabled.toString(),
                "surface_present" to (imageReaderSurface != null).toString(),
                "width" to imageReaderWidth.toString(),
                "height" to imageReaderHeight.toString(),
            ),
        )
    }

    fun setOnOutputSurfaceReadyChanged(listener: ((Boolean) -> Unit)?) {
        onOutputSurfaceReadyChanged = listener
        listener?.invoke(isOutputSurfaceReady())
    }

    fun setOnFrameCaptured(listener: ((String) -> Unit)?) {
        onFrameCaptured = listener
    }

    fun prepareOutputSurface(
        hostWidth: Int,
        hostHeight: Int,
    ) {
        if (hostWidth < MIN_DECODER_SURFACE_SIZE || hostHeight < MIN_DECODER_SURFACE_SIZE) {
            return
        }
        val expectedSize = resolveLibVlcImageReaderSurfaceSize(
            requestedWidth = hostWidth,
            requestedHeight = hostHeight,
            config = imageReaderSurfaceConfig,
        )
        if (
            imageReaderSurface != null &&
            imageReaderWidth == expectedSize.first &&
            imageReaderHeight == expectedSize.second
        ) {
            onOutputSurfaceReadyChanged?.invoke(isOutputSurfaceReady())
            return
        }
        createOutputSurface(hostWidth, hostHeight)
    }

    internal fun pendingCaptureLabelForTest(): String? =
        renderer.pendingCaptureLabelForTest() ?: requestedCaptureLabelForTest

    fun captureNextRenderedFrame(label: String) {
        requestedCaptureLabelForTest = label
        queueEvent {
            renderer.requestFrameCapture(label)
        }
        requestRender()
    }

    override fun onDetachedFromWindow() {
        releaseOutputSurface()
        queueEvent {
            renderer.releaseResources()
        }
        imageReaderThread.quitSafely()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        prepareOutputSurface(width, height)
    }

    private fun createOutputSurface(width: Int, height: Int) {
        releaseOutputSurface()
        val requestedWidth = width.coerceAtLeast(MIN_DECODER_SURFACE_SIZE)
        val requestedHeight = height.coerceAtLeast(MIN_DECODER_SURFACE_SIZE)
        val preferredConfig = resolveLibVlcImageReaderSurfaceConfig(Build.VERSION.SDK_INT)
        val resolvedConfig = createImageReaderWithFallback(
            requestedWidth = requestedWidth,
            requestedHeight = requestedHeight,
            preferredConfig = preferredConfig,
        ) ?: return
        val (newReader, activeConfig, surfaceWidth, surfaceHeight) = resolvedConfig
        newReader.setOnImageAvailableListener(
            { reader ->
                handleImageAvailable(reader)
            },
            imageReaderHandler,
        )
        imageReaderSurfaceConfig = activeConfig
        imageReader = newReader
        imageReaderSurface = newReader.surface
        imageReaderWidth = surfaceWidth
        imageReaderHeight = surfaceHeight
        hasLoggedFirstImageAvailable = false
        hasLoggedFirstQueuedFrame = false
        hasLoggedCopyFailure = false
        renderer.resetFrameState()
        Log.i(
            TAG,
            "Created libVLC ImageReader output surface " +
                "requestedHost=${requestedWidth}x${requestedHeight} " +
                "surface=${surfaceWidth}x${surfaceHeight} " +
                "format=${activeConfig.imageFormat} usage=${activeConfig.usage} " +
                "copyStrategy=${activeConfig.copyStrategy}",
        )
        MiruLog.i(
            TAG,
            "Created libVLC ImageReader output surface",
            mapOf(
                "requested_host_width" to requestedWidth.toString(),
                "requested_host_height" to requestedHeight.toString(),
                "surface_width" to surfaceWidth.toString(),
                "surface_height" to surfaceHeight.toString(),
                "image_format" to activeConfig.imageFormat.toString(),
                "usage" to activeConfig.usage.toString(),
                "copy_strategy" to activeConfig.copyStrategy.name,
            ),
        )
        onOutputSurfaceReadyChanged?.invoke(isOutputSurfaceReady())
    }

    private fun releaseOutputSurface() {
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        imageReaderSurface?.release()
        imageReaderSurface = null
        imageReaderWidth = 0
        imageReaderHeight = 0
        hasLoggedFirstImageAvailable = false
        hasLoggedFirstQueuedFrame = false
        hasLoggedCopyFailure = false
        renderer.resetFrameState()
        onOutputSurfaceReadyChanged?.invoke(false)
    }

    private fun handleImageAvailable(reader: ImageReader) {
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        image.use { latestImage ->
            if (!hasLoggedFirstImageAvailable) {
                hasLoggedFirstImageAvailable = true
                logImageAvailableDiagnostics(latestImage, reason = "first_image_available")
            }
            val frame = copyFrame(latestImage) ?: return
            if (!hasLoggedFirstQueuedFrame) {
                hasLoggedFirstQueuedFrame = true
                MiruLog.i(
                    TAG,
                    "Queued first libVLC ImageReader frame for GL upload",
                    mapOf(
                        "width" to frame.width.toString(),
                        "height" to frame.height.toString(),
                        "copy_strategy" to imageReaderSurfaceConfig.copyStrategy.name,
                    ),
                )
            }
            renderer.offerFrame(frame)
        }
        requestRender()
    }

    private fun copyFrame(image: Image): FrameBuffer? {
        return when (imageReaderSurfaceConfig.copyStrategy) {
            LibVlcImageReaderCopyStrategy.RGBA_PLANES -> copyRgbaPlaneFrame(image)
            LibVlcImageReaderCopyStrategy.HARDWARE_BITMAP -> copyHardwareBitmapFrame(image)
        }
    }

    private fun copyRgbaPlaneFrame(image: Image): FrameBuffer? {
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width.coerceAtLeast(1)
        val height = image.height.coerceAtLeast(1)
        val rowBytes = width * 4
        val sourceBuffer = plane.buffer.duplicate().order(ByteOrder.nativeOrder())
        sourceBuffer.rewind()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        if (pixelStride != 4 || rowStride < rowBytes) {
            Log.w(
                TAG,
                "Unsupported ImageReader plane layout width=$width height=$height " +
                    "pixelStride=$pixelStride rowStride=$rowStride",
            )
            logFrameCopyFailure(
                image = image,
                reason = "unsupported_rgba_plane_layout",
                extraAttributes = mapOf(
                    "pixel_stride" to pixelStride.toString(),
                    "row_stride" to rowStride.toString(),
                    "expected_row_bytes" to rowBytes.toString(),
                ),
            )
            return null
        }
        val packed = ByteBuffer.allocateDirect(rowBytes * height).order(ByteOrder.nativeOrder())
        val rowBuffer = ByteArray(rowStride)
        var rowOffset = 0
        repeat(height) {
            sourceBuffer.position(rowOffset)
            sourceBuffer.get(rowBuffer, 0, rowStride)
            packed.put(rowBuffer, 0, rowBytes)
            rowOffset += rowStride
        }
        packed.rewind()
        return FrameBuffer(width = width, height = height, pixels = packed)
    }

    private fun copyHardwareBitmapFrame(image: Image): FrameBuffer? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "Hardware-buffer copy requested below API 29")
            logFrameCopyFailure(image = image, reason = "hardware_buffer_copy_below_api_29")
            return null
        }
        val hardwareBuffer = image.hardwareBuffer ?: return null
        hardwareBuffer.use { buffer ->
            val colorSpace = ColorSpace.getFromDataSpace(image.dataSpace)
                ?: ColorSpace.get(ColorSpace.Named.SRGB)
            val wrappedBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
            if (wrappedBitmap == null) {
                Log.w(TAG, "Bitmap.wrapHardwareBuffer returned null for libVLC output frame")
                logFrameCopyFailure(
                    image = image,
                    reason = "wrap_hardware_buffer_returned_null",
                    extraAttributes = hardwareBufferAttributes(buffer),
                )
                return null
            }
            return try {
                val softwareBitmap = wrappedBitmap.copy(Bitmap.Config.ARGB_8888, false)
                if (softwareBitmap == null) {
                    Log.w(TAG, "Failed to copy libVLC hardware bitmap into ARGB_8888")
                    logFrameCopyFailure(
                        image = image,
                        reason = "hardware_bitmap_copy_returned_null",
                        extraAttributes = hardwareBufferAttributes(buffer),
                    )
                    null
                } else {
                    val rowBytes = softwareBitmap.width * 4
                    val packed = ByteBuffer
                        .allocateDirect(rowBytes * softwareBitmap.height)
                        .order(ByteOrder.nativeOrder())
                    softwareBitmap.copyPixelsToBuffer(packed)
                    packed.rewind()
                    FrameBuffer(
                        width = softwareBitmap.width,
                        height = softwareBitmap.height,
                        pixels = packed,
                    ).also {
                        softwareBitmap.recycle()
                    }
                }
            } finally {
                wrappedBitmap.recycle()
            }
        }
    }

    private fun createImageReaderWithFallback(
        requestedWidth: Int,
        requestedHeight: Int,
        preferredConfig: LibVlcImageReaderSurfaceConfig,
    ): LibVlcImageReaderCreationResult? {
        val preferredWidth = resolveLibVlcImageReaderSurfaceSize(
            requestedWidth = requestedWidth,
            requestedHeight = requestedHeight,
            config = preferredConfig,
        ).first
        val preferredHeight = resolveLibVlcImageReaderSurfaceSize(
            requestedWidth = requestedWidth,
            requestedHeight = requestedHeight,
            config = preferredConfig,
        ).second
        val preferredResult = runCatching {
            createImageReader(preferredWidth, preferredHeight, preferredConfig)
        }
        if (preferredResult.isSuccess) {
            return LibVlcImageReaderCreationResult(
                reader = preferredResult.getOrThrow(),
                config = preferredConfig,
                width = preferredWidth,
                height = preferredHeight,
            )
        }
        val preferredError = preferredResult.exceptionOrNull()
        Log.w(
            TAG,
            "Failed to create preferred libVLC ImageReader config " +
                "format=${preferredConfig.imageFormat} usage=${preferredConfig.usage} " +
                "copyStrategy=${preferredConfig.copyStrategy}",
            preferredError,
        )
        MiruLog.w(
            TAG,
            "Failed to create preferred libVLC ImageReader config",
            preferredError,
            mapOf(
                "requested_width" to requestedWidth.toString(),
                "requested_height" to requestedHeight.toString(),
                "surface_width" to preferredWidth.toString(),
                "surface_height" to preferredHeight.toString(),
                "image_format" to preferredConfig.imageFormat.toString(),
                "usage" to preferredConfig.usage.toString(),
                "copy_strategy" to preferredConfig.copyStrategy.name,
            ),
        )
        val fallbackConfig = resolveLibVlcImageReaderSurfaceConfig(Build.VERSION_CODES.P)
        if (fallbackConfig == preferredConfig) {
            return null
        }
        val fallbackWidth = resolveLibVlcImageReaderSurfaceSize(
            requestedWidth = requestedWidth,
            requestedHeight = requestedHeight,
            config = fallbackConfig,
        ).first
        val fallbackHeight = resolveLibVlcImageReaderSurfaceSize(
            requestedWidth = requestedWidth,
            requestedHeight = requestedHeight,
            config = fallbackConfig,
        ).second
        return runCatching {
            LibVlcImageReaderCreationResult(
                reader = createImageReader(fallbackWidth, fallbackHeight, fallbackConfig),
                config = fallbackConfig,
                width = fallbackWidth,
                height = fallbackHeight,
            )
        }.onFailure { fallbackError ->
            Log.e(
                TAG,
                "Failed to create fallback libVLC ImageReader config " +
                    "format=${fallbackConfig.imageFormat} usage=${fallbackConfig.usage} " +
                    "copyStrategy=${fallbackConfig.copyStrategy}",
                fallbackError,
            )
            MiruLog.e(
                TAG,
                "Failed to create fallback libVLC ImageReader config",
                fallbackError,
                mapOf(
                    "requested_width" to requestedWidth.toString(),
                    "requested_height" to requestedHeight.toString(),
                    "surface_width" to fallbackWidth.toString(),
                    "surface_height" to fallbackHeight.toString(),
                    "image_format" to fallbackConfig.imageFormat.toString(),
                    "usage" to fallbackConfig.usage.toString(),
                    "copy_strategy" to fallbackConfig.copyStrategy.name,
                ),
            )
        }.getOrNull()
    }

    private fun createImageReader(
        width: Int,
        height: Int,
        config: LibVlcImageReaderSurfaceConfig,
    ): ImageReader =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && config.usage != 0L) {
            ImageReader.newInstance(
                width,
                height,
                config.imageFormat,
                MAX_IMAGES,
                config.usage,
            )
        } else {
            ImageReader.newInstance(
                width,
                height,
                config.imageFormat,
                MAX_IMAGES,
            )
        }

    private fun isOutputSurfaceReady(): Boolean =
        isLibVlcOutputCallbackAttachReady(
            surfacePresent = imageReaderSurface != null,
            surfaceValid = imageReaderSurface?.isValid == true,
            hostWidth = width,
            hostHeight = height,
        )

    private inner class ImageReaderToneMappingRenderer : Renderer {
        private val vertexBuffer: FloatBuffer = ByteBuffer
            .allocateDirect(IMAGE_VERTEX_DATA.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(IMAGE_VERTEX_DATA)
                position(0)
            }
        private val latestFrame = AtomicReference<FrameBuffer?>(null)
        private var programId: Int = 0
        private var textureId: Int = 0
        private var positionHandle: Int = 0
        private var texCoordHandle: Int = 0
        private var samplerHandle: Int = 0
        private var enabledHandle: Int = 0
        private var exposureHandle: Int = 0
        private var contrastHandle: Int = 0
        private var saturationHandle: Int = 0
        private var highlightCompressionHandle: Int = 0
        private var shaderConfig: ImageShaderConfig = ImageShaderConfig()
        private var hasUploadedFrame = false
        private var hasLoggedFirstTextureUpload = false
        private var pendingCaptureLabel: String? = null
        private var waitingForFirstFrameCaptureLabel: String? = null

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            programId = createProgram(IMAGE_VERTEX_SHADER, IMAGE_FRAGMENT_SHADER)
            textureId = createTexture2d()
            positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(programId, "aTexCoord")
            samplerHandle = GLES20.glGetUniformLocation(programId, "uTexture")
            enabledHandle = GLES20.glGetUniformLocation(programId, "uEnabled")
            exposureHandle = GLES20.glGetUniformLocation(programId, "uExposure")
            contrastHandle = GLES20.glGetUniformLocation(programId, "uContrast")
            saturationHandle = GLES20.glGetUniformLocation(programId, "uSaturation")
            highlightCompressionHandle = GLES20.glGetUniformLocation(programId, "uHighlightCompression")
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            latestFrame.getAndSet(null)?.let { frame ->
                uploadFrame(frame)
                hasUploadedFrame = true
            }
            if (!hasUploadedFrame) {
                return
            }

            GLES20.glUseProgram(programId)
            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(
                positionHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                IMAGE_STRIDE_BYTES,
                vertexBuffer,
            )
            GLES20.glEnableVertexAttribArray(positionHandle)

            vertexBuffer.position(2)
            GLES20.glVertexAttribPointer(
                texCoordHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                IMAGE_STRIDE_BYTES,
                vertexBuffer,
            )
            GLES20.glEnableVertexAttribArray(texCoordHandle)

            GLES20.glUniform1i(samplerHandle, 0)
            GLES20.glUniform1f(enabledHandle, if (shaderConfig.enabled) 1f else 0f)
            GLES20.glUniform1f(exposureHandle, shaderConfig.exposure)
            GLES20.glUniform1f(contrastHandle, shaderConfig.contrast)
            GLES20.glUniform1f(saturationHandle, shaderConfig.saturation)
            GLES20.glUniform1f(highlightCompressionHandle, shaderConfig.highlightCompression)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            captureFrameIfRequested()
        }

        fun updateConfig(
            ruleSet: ToneMappingRuleSet,
            signalDescriptor: VideoSignalDescriptor?,
        ) {
            shaderConfig = ImageShaderConfig.from(ruleSet, signalDescriptor)
        }

        fun offerFrame(frame: FrameBuffer) {
            latestFrame.getAndSet(frame)
        }

        fun requestFrameCapture(label: String) {
            pendingCaptureLabel = label
        }

        fun pendingCaptureLabelForTest(): String? = pendingCaptureLabel

        fun resetFrameState() {
            latestFrame.set(null)
            hasUploadedFrame = false
            hasLoggedFirstTextureUpload = false
        }

        fun releaseResources() {
            latestFrame.set(null)
            if (textureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                textureId = 0
            }
            if (programId != 0) {
                GLES20.glDeleteProgram(programId)
                programId = 0
            }
        }

        private fun uploadFrame(frame: FrameBuffer) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                frame.width,
                frame.height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                frame.pixels.rewind(),
            )
            if (!hasLoggedFirstTextureUpload) {
                hasLoggedFirstTextureUpload = true
                MiruLog.i(
                    TAG,
                    "Uploaded first libVLC ImageReader frame into GL texture",
                    mapOf(
                        "width" to frame.width.toString(),
                        "height" to frame.height.toString(),
                    ),
                )
            }
        }

        private fun captureFrameIfRequested() {
            val label = pendingCaptureLabel ?: return
            if (!hasUploadedFrame) {
                if (waitingForFirstFrameCaptureLabel != label) {
                    waitingForFirstFrameCaptureLabel = label
                    Log.i(TAG, "Waiting for first ImageReader frame before capturing label=$label")
                }
                return
            }
            waitingForFirstFrameCaptureLabel = null
            pendingCaptureLabel = null
            val width = width
            val height = height
            if (width <= 0 || height <= 0) {
                return
            }
            runCatching {
                val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
                GLES20.glReadPixels(
                    0,
                    0,
                    width,
                    height,
                    GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE,
                    pixelBuffer,
                )
                pixelBuffer.rewind()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(pixelBuffer)
                val flipped = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        flipped.setPixel(x, height - y - 1, bitmap.getPixel(x, y))
                    }
                }
                bitmap.recycle()
                val outputDir = File(context.filesDir, "MiruPlayGlCaptures").apply { mkdirs() }
                val outputFile = File(outputDir, "${sanitizeOutputFileName(label)}.png")
                FileOutputStream(outputFile).use { stream ->
                    flipped.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                flipped.recycle()
                Log.i(TAG, "Captured ImageReader GL frame label=$label path=${outputFile.absolutePath}")
                MiruLog.i(
                    TAG,
                    "Captured ImageReader GL frame",
                    mapOf(
                        "label" to label,
                        "path" to outputFile.absolutePath,
                        "width" to width.toString(),
                        "height" to height.toString(),
                    ),
                )
                post {
                    onFrameCaptured?.invoke(label)
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to capture ImageReader GL frame label=$label", error)
                MiruLog.e(
                    TAG,
                    "Failed to capture ImageReader GL frame",
                    error,
                    mapOf("label" to label),
                )
            }
        }
    }

    private data class FrameBuffer(
        val width: Int,
        val height: Int,
        val pixels: ByteBuffer,
    )

    private data class ImageShaderConfig(
        val enabled: Boolean = false,
        val exposure: Float = 1f,
        val contrast: Float = 1f,
        val saturation: Float = 1f,
        val highlightCompression: Float = 0f,
    ) {
        companion object {
            fun from(
                ruleSet: ToneMappingRuleSet,
                signalDescriptor: VideoSignalDescriptor?,
            ): ImageShaderConfig {
                val hdrFactor = when (signalDescriptor?.signalKind) {
                    VideoSignalKind.HDR10_PLUS -> 1.08f
                    VideoSignalKind.HDR10 -> 1.03f
                    VideoSignalKind.DOLBY_VISION -> 1.02f
                    VideoSignalKind.UNKNOWN_HDR -> 1.01f
                    else -> 1f
                }
                val curveBias = when (ruleSet.curvePreset) {
                    ToneMappingCurvePreset.PASSTHROUGH -> 1f
                    ToneMappingCurvePreset.MOBIUS -> 1.02f
                    ToneMappingCurvePreset.REINHARD -> 0.98f
                }
                return ImageShaderConfig(
                    enabled = ruleSet.enabled,
                    exposure = (ruleSet.targetSdrNits / 120f).coerceIn(0.75f, 1.35f) * hdrFactor * curveBias,
                    contrast = (1f + ruleSet.contrastRecovery / 40f).coerceIn(0.8f, 1.8f),
                    saturation = (1f + ruleSet.saturationRecovery / 45f).coerceIn(0.75f, 1.8f),
                    highlightCompression = (ruleSet.highlightCompression / 100f).coerceIn(0f, 0.6f),
                )
            }
        }
    }

    companion object {
        private const val TAG = "LibVlcImageReaderOutputView"
        private const val MAX_IMAGES = 3
        private const val IMAGE_STRIDE_BYTES = 4 * Float.SIZE_BYTES
        private val IMAGE_VERTEX_DATA = floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f,
        )

        private const val IMAGE_VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val IMAGE_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform float uEnabled;
            uniform float uExposure;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uHighlightCompression;
            varying vec2 vTexCoord;

            vec3 applyToneMapping(vec3 color) {
                color = max(color * uExposure, vec3(0.0));
                float luma = dot(color, vec3(0.2627, 0.6780, 0.0593));
                float shoulderStart = 0.60;
                float shoulder = max(luma - shoulderStart, 0.0);
                float compressedLuma = luma / (1.0 + shoulder * uHighlightCompression * 2.0);
                color *= (compressedLuma + 0.0001) / max(luma, 0.0001);
                color = (color - 0.5) * uContrast + 0.5;
                float gray = dot(color, vec3(0.299, 0.587, 0.114));
                color = mix(vec3(gray), color, uSaturation);
                return clamp(color, 0.0, 1.0);
            }

            void main() {
                vec4 sampled = texture2D(uTexture, vTexCoord);
                vec3 color = sampled.rgb;
                if (uEnabled > 0.5) {
                    color = applyToneMapping(color);
                }
                gl_FragColor = vec4(color, sampled.a);
            }
        """

        private fun createTexture2d(): Int {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
            GLES20.glTexParameterf(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR.toFloat(),
            )
            GLES20.glTexParameterf(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR.toFloat(),
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE,
            )
            return textures[0]
        }

        private fun createProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
            return GLES20.glCreateProgram().also { program ->
                GLES20.glAttachShader(program, vertexShader)
                GLES20.glAttachShader(program, fragmentShader)
                GLES20.glLinkProgram(program)
                val linkStatus = IntArray(1)
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
                if (linkStatus[0] == 0) {
                    val error = GLES20.glGetProgramInfoLog(program)
                    GLES20.glDeleteProgram(program)
                    error("Failed to link ImageReader GL program: $error")
                }
                GLES20.glDeleteShader(vertexShader)
                GLES20.glDeleteShader(fragmentShader)
            }
        }

        private fun compileShader(type: Int, source: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val compileStatus = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
                if (compileStatus[0] == 0) {
                    val error = GLES20.glGetShaderInfoLog(shader)
                    GLES20.glDeleteShader(shader)
                    error("Failed to compile ImageReader GL shader: $error")
                }
            }
        }

        private fun sanitizeOutputFileName(value: String): String =
            value.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
    }

    private fun logImageAvailableDiagnostics(
        image: Image,
        reason: String,
    ) {
        val attributes = mutableMapOf(
            "reason" to reason,
            "image_width" to image.width.toString(),
            "image_height" to image.height.toString(),
            "image_format" to image.format.toString(),
            "data_space" to image.dataSpace.toString(),
            "plane_count" to image.planes.size.toString(),
            "copy_strategy" to imageReaderSurfaceConfig.copyStrategy.name,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            image.hardwareBuffer?.let { buffer ->
                attributes += hardwareBufferAttributes(buffer)
            }
        }
        MiruLog.i(TAG, "Received libVLC ImageReader frame", attributes)
    }

    private fun logFrameCopyFailure(
        image: Image,
        reason: String,
        extraAttributes: Map<String, String> = emptyMap(),
    ) {
        if (hasLoggedCopyFailure) {
            return
        }
        hasLoggedCopyFailure = true
        val attributes = mutableMapOf(
            "reason" to reason,
            "image_width" to image.width.toString(),
            "image_height" to image.height.toString(),
            "image_format" to image.format.toString(),
            "data_space" to image.dataSpace.toString(),
            "plane_count" to image.planes.size.toString(),
            "copy_strategy" to imageReaderSurfaceConfig.copyStrategy.name,
        )
        attributes += extraAttributes
        MiruLog.w(
            TAG,
            "Failed to copy libVLC ImageReader frame into a CPU or GL-friendly buffer",
            attributes = attributes,
        )
    }

    private fun hardwareBufferAttributes(
        buffer: android.hardware.HardwareBuffer,
    ): Map<String, String> = mapOf(
        "hardware_buffer_width" to buffer.width.toString(),
        "hardware_buffer_height" to buffer.height.toString(),
        "hardware_buffer_format" to buffer.format.toString(),
        "hardware_buffer_usage" to buffer.usage.toString(),
    )
}

internal enum class LibVlcImageReaderCopyStrategy {
    RGBA_PLANES,
    HARDWARE_BITMAP,
}

internal data class LibVlcImageReaderSurfaceConfig(
    val imageFormat: Int,
    val usage: Long,
    val copyStrategy: LibVlcImageReaderCopyStrategy,
)

internal data class LibVlcImageReaderCreationResult(
    val reader: ImageReader,
    val config: LibVlcImageReaderSurfaceConfig,
    val width: Int,
    val height: Int,
)

internal fun resolveLibVlcImageReaderSurfaceConfig(
    sdkInt: Int,
): LibVlcImageReaderSurfaceConfig =
    if (sdkInt >= Build.VERSION_CODES.Q) {
        LibVlcImageReaderSurfaceConfig(
            imageFormat = ImageFormat.PRIVATE,
            usage = android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
            copyStrategy = LibVlcImageReaderCopyStrategy.HARDWARE_BITMAP,
        )
    } else {
        LibVlcImageReaderSurfaceConfig(
            imageFormat = PixelFormat.RGBA_8888,
            usage = 0L,
            copyStrategy = LibVlcImageReaderCopyStrategy.RGBA_PLANES,
        )
    }

internal fun resolveLibVlcImageReaderSurfaceSize(
    requestedWidth: Int,
    requestedHeight: Int,
    config: LibVlcImageReaderSurfaceConfig,
): Pair<Int, Int> =
    requestedWidth.coerceAtLeast(MIN_DECODER_SURFACE_SIZE) to
        requestedHeight.coerceAtLeast(MIN_DECODER_SURFACE_SIZE)

internal fun isLibVlcOutputCallbackAttachReady(
    surfacePresent: Boolean,
    surfaceValid: Boolean,
    hostWidth: Int,
    hostHeight: Int,
): Boolean =
    surfacePresent &&
        surfaceValid &&
        hostWidth >= MIN_DECODER_SURFACE_SIZE &&
        hostHeight >= MIN_DECODER_SURFACE_SIZE

private inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R {
    return try {
        block(this)
    } finally {
        this?.close()
    }
}
