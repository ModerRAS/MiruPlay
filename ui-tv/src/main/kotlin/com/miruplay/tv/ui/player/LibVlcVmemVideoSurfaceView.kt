package com.miruplay.tv.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.miruplay.tv.core.common.logging.MiruLog
import com.miruplay.tv.model.ToneMappingCurvePreset
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoSignalKind
import com.miruplay.tv.player.LibVlcVmemStreamBridge
import com.miruplay.tv.player.LibVlcVmemStreamSession
import com.miruplay.tv.player.LibVlcVmemStreamState
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.pow
import android.os.SystemClock
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class LibVlcVmemVideoSurfaceView(
    context: Context,
) : GLSurfaceView(context) {
    private val renderer = VmemRenderer()
    private var onFrameCaptured: ((String) -> Unit)? = null
    private var vmemRenderTickerRunning = false
    private var hasBoundStream = false
    private val vmemRenderTickRunnable = object : Runnable {
        override fun run() {
            if (!hasBoundStream) {
                vmemRenderTickerRunning = false
                return
            }
            requestRender()
            postDelayed(this, MIN_UPLOAD_INTERVAL_MS)
        }
    }

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
    }

    fun bindStream(
        bridge: LibVlcVmemStreamBridge?,
        session: LibVlcVmemStreamSession?,
    ) {
        hasBoundStream = bridge != null && session != null
        queueEvent {
            renderer.bindStream(bridge, session)
        }
        if (hasBoundStream) {
            restartVmemRenderTicker(immediate = true)
        } else {
            stopVmemRenderTicker()
        }
    }

    fun captureNextRenderedFrame(label: String) {
        queueEvent {
            renderer.requestFrameCapture(label)
        }
        requestRender()
    }

    fun setOnFrameCaptured(listener: ((String) -> Unit)?) {
        onFrameCaptured = listener
    }

    override fun onDetachedFromWindow() {
        stopVmemRenderTicker()
        queueEvent {
            renderer.releaseResources()
        }
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (hasBoundStream) {
            restartVmemRenderTicker(immediate = false)
        }
    }

    private fun restartVmemRenderTicker(
        immediate: Boolean,
    ) {
        removeCallbacks(vmemRenderTickRunnable)
        vmemRenderTickerRunning = true
        if (immediate) {
            post(vmemRenderTickRunnable)
        } else {
            postDelayed(vmemRenderTickRunnable, MIN_UPLOAD_INTERVAL_MS)
        }
    }

    private fun stopVmemRenderTicker() {
        if (!vmemRenderTickerRunning) {
            return
        }
        vmemRenderTickerRunning = false
        removeCallbacks(vmemRenderTickRunnable)
    }

    private inner class VmemRenderer : Renderer {
        private val vertexBuffer: FloatBuffer = ByteBuffer
            .allocateDirect(VERTEX_DATA.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(VERTEX_DATA)
                position(0)
            }
        private var programId: Int = 0
        private var rgbaTextureId: Int = 0
        private val planeTextureIds = IntArray(3)
        private val planeTextureWidths = IntArray(3)
        private val planeTextureHeights = IntArray(3)
        private var positionHandle: Int = 0
        private var texCoordHandle: Int = 0
        private var samplerHandle: Int = 0
        private var yPlaneSamplerHandle: Int = 0
        private var uPlaneSamplerHandle: Int = 0
        private var vPlaneSamplerHandle: Int = 0
        private var pipelineModeHandle: Int = 0
        private var enabledHandle: Int = 0
        private var exposureHandle: Int = 0
        private var contrastHandle: Int = 0
        private var saturationHandle: Int = 0
        private var highlightCompressionHandle: Int = 0
        private var shaderConfig: ShaderConfig = ShaderConfig()
        private var bridge: LibVlcVmemStreamBridge? = null
        private var session: LibVlcVmemStreamSession? = null
        private var frameBuffer: ByteBuffer? = null
        private var uploadBuffer: ByteBuffer? = null
        private var frameWidth: Int = 0
        private var frameHeight: Int = 0
        private var frameState: LibVlcVmemStreamState = LibVlcVmemStreamState()
        private var lastFrameVersion: Long = 0L
        private var hasUploadedFrame = false
        private var pendingCaptureLabel: String? = null
        private var waitingForFirstFrameCaptureLabel: String? = null
        private var captureInFlightLabel: String? = null
        private var hasLoggedFirstPixelProbe = false
        private var textureStorageInitialized = false
        private var lastUploadRealtimeMs: Long = 0L
        private var activeFramePipeline: VmemFramePipeline = VmemFramePipeline.RGBA_TEXTURE
        private var rawHdrPlaneSpecs: List<VmemPlaneUploadSpec> = emptyList()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            rgbaTextureId = createTexture2d()
            createTexture2dArray(planeTextureIds)
            positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(programId, "aTexCoord")
            samplerHandle = GLES20.glGetUniformLocation(programId, "uTexture")
            yPlaneSamplerHandle = GLES20.glGetUniformLocation(programId, "uPlaneY")
            uPlaneSamplerHandle = GLES20.glGetUniformLocation(programId, "uPlaneU")
            vPlaneSamplerHandle = GLES20.glGetUniformLocation(programId, "uPlaneV")
            pipelineModeHandle = GLES20.glGetUniformLocation(programId, "uPipelineMode")
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

            pollLatestFrame()
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
                STRIDE_BYTES,
                vertexBuffer,
            )
            GLES20.glEnableVertexAttribArray(positionHandle)

            vertexBuffer.position(2)
            GLES20.glVertexAttribPointer(
                texCoordHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                STRIDE_BYTES,
                vertexBuffer,
            )
            GLES20.glEnableVertexAttribArray(texCoordHandle)

            GLES20.glUniform1i(samplerHandle, 0)
            GLES20.glUniform1i(yPlaneSamplerHandle, 1)
            GLES20.glUniform1i(uPlaneSamplerHandle, 2)
            GLES20.glUniform1i(vPlaneSamplerHandle, 3)
            GLES20.glUniform1f(
                pipelineModeHandle,
                if (activeFramePipeline == VmemFramePipeline.RAW_YUV_SHADER) 1f else 0f,
            )
            GLES20.glUniform1f(enabledHandle, if (shaderConfig.enabled) 1f else 0f)
            GLES20.glUniform1f(exposureHandle, shaderConfig.exposure)
            GLES20.glUniform1f(contrastHandle, shaderConfig.contrast)
            GLES20.glUniform1f(saturationHandle, shaderConfig.saturation)
            GLES20.glUniform1f(highlightCompressionHandle, shaderConfig.highlightCompression)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, rgbaTextureId)
            if (activeFramePipeline == VmemFramePipeline.RAW_YUV_SHADER) {
                planeTextureIds.forEachIndexed { planeIndex, textureId ->
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE1 + planeIndex)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                }
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            captureFrameIfRequested()
        }

        fun updateConfig(
            ruleSet: ToneMappingRuleSet,
            signalDescriptor: VideoSignalDescriptor?,
        ) {
            shaderConfig = ShaderConfig.from(ruleSet, signalDescriptor)
        }

        fun bindStream(
            bridge: LibVlcVmemStreamBridge?,
            session: LibVlcVmemStreamSession?,
        ) {
            this.bridge = bridge
            this.session = session
            frameBuffer = null
            uploadBuffer = null
            frameWidth = 0
            frameHeight = 0
            frameState = LibVlcVmemStreamState()
            lastFrameVersion = 0L
            hasUploadedFrame = false
            hasLoggedFirstPixelProbe = false
            captureInFlightLabel = null
            textureStorageInitialized = false
            lastUploadRealtimeMs = 0L
            activeFramePipeline = VmemFramePipeline.RGBA_TEXTURE
            rawHdrPlaneSpecs = emptyList()
            planeTextureWidths.fill(0)
            planeTextureHeights.fill(0)
        }

        fun requestFrameCapture(label: String) {
            if (!shouldQueueVmemCaptureRequest(pendingCaptureLabel, captureInFlightLabel, label)) {
                return
            }
            pendingCaptureLabel = label
            Log.i(
                TAG,
                "Queued VMEM GL frame capture label=$label surface=${this@LibVlcVmemVideoSurfaceView.width}x${this@LibVlcVmemVideoSurfaceView.height} hasUploadedFrame=$hasUploadedFrame lastFrameVersion=$lastFrameVersion",
            )
        }

        fun releaseResources() {
            bridge = null
            session = null
            frameBuffer = null
            uploadBuffer = null
            hasUploadedFrame = false
            hasLoggedFirstPixelProbe = false
            captureInFlightLabel = null
            textureStorageInitialized = false
            lastUploadRealtimeMs = 0L
            if (rgbaTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(rgbaTextureId), 0)
                rgbaTextureId = 0
            }
            if (planeTextureIds.any { it != 0 }) {
                GLES20.glDeleteTextures(planeTextureIds.size, planeTextureIds, 0)
                planeTextureIds.fill(0)
            }
            planeTextureWidths.fill(0)
            planeTextureHeights.fill(0)
            if (programId != 0) {
                GLES20.glDeleteProgram(programId)
                programId = 0
            }
        }

        private fun pollLatestFrame() {
            val activeBridge = bridge ?: return
            val activeSession = session ?: return
            val nowMs = SystemClock.elapsedRealtime()
            if (!shouldPollLatestVmemFrame(hasUploadedFrame, pendingCaptureLabel, nowMs, lastUploadRealtimeMs, MIN_UPLOAD_INTERVAL_MS)) {
                return
            }
            val state = activeBridge.readState(activeSession)
            if (!state.configured || state.width <= 0 || state.height <= 0 || state.totalBytes <= 0) {
                return
            }
            val width = state.visibleWidth.coerceAtLeast(state.width)
            val height = state.visibleHeight.coerceAtLeast(state.height)
            val framePipeline = resolveVmemFramePipeline(state)
            if (framePipeline == VmemFramePipeline.RAW_YUV_SHADER) {
                ensureRawFrameBuffer(state.totalBytes)
                frameWidth = width
                frameHeight = height
                val rawFrame = frameBuffer ?: return
                val copiedVersion = activeBridge.copyLatestFrame(
                    session = activeSession,
                    target = rawFrame,
                    lastFrameVersion = lastFrameVersion,
                )
                if (copiedVersion <= lastFrameVersion) {
                    return
                }
                lastFrameVersion = copiedVersion
                uploadPlanarHdrFrame(state, rawFrame)
            } else {
                ensureRgbaBuffers(width, height, state.totalBytes)
                val rgbaBuffer = uploadBuffer ?: return
                var copiedVersion = activeBridge.copyLatestFrameRgba(
                    session = activeSession,
                    target = rgbaBuffer,
                    lastFrameVersion = lastFrameVersion,
                )
                var usedNativeRgba = copiedVersion > lastFrameVersion
                val workingBuffer = frameBuffer
                if (!usedNativeRgba && workingBuffer != null) {
                    copiedVersion = activeBridge.copyLatestFrame(
                        session = activeSession,
                        target = workingBuffer,
                        lastFrameVersion = lastFrameVersion,
                    )
                }
                if (copiedVersion <= lastFrameVersion) {
                    return
                }
                lastFrameVersion = copiedVersion
                if (usedNativeRgba) {
                    uploadRgbaFrame(state, rgbaBuffer, nativeConverted = true)
                } else {
                    val rawFrame = workingBuffer ?: return
                    uploadFrame(state, rawFrame)
                }
            }
            lastUploadRealtimeMs = nowMs
            hasUploadedFrame = true
            if (lastFrameVersion <= 3L || lastFrameVersion % 60L == 0L) {
                Log.i(
                    TAG,
                    "Uploaded VMEM frame version=$lastFrameVersion chroma=${state.chroma} size=${state.width}x${state.height} visible=${state.visibleWidth}x${state.visibleHeight} pitch=${state.pitch} path=${activeFramePipeline.logLabel}",
                )
            }
        }

        private fun ensureRawFrameBuffer(
            totalBytes: Int,
        ) {
            if (frameBuffer == null || frameBuffer?.capacity() != totalBytes) {
                frameBuffer = ByteBuffer.allocateDirect(totalBytes).order(ByteOrder.nativeOrder())
            }
        }

        private fun ensureRgbaBuffers(
            width: Int,
            height: Int,
            totalBytes: Int,
        ) {
            ensureRawFrameBuffer(totalBytes)
            val packedBytes = width * height * 4
            if (uploadBuffer == null || uploadBuffer?.capacity() != packedBytes) {
                uploadBuffer = ByteBuffer.allocateDirect(packedBytes).order(ByteOrder.nativeOrder())
            }
            frameWidth = width
            frameHeight = height
        }

        private fun uploadFrame(
            state: LibVlcVmemStreamState,
            source: ByteBuffer,
        ) {
            val target = uploadBuffer ?: return
            if (!hasLoggedFirstPixelProbe) {
                val rawProbe = describeRawVmemBytes(source, pixelCount = 8)
                Log.i(TAG, "VMEM source bytes before repack: $rawProbe")
                MiruLog.i(
                    TAG,
                    "VMEM source bytes before repack",
                    mapOf("pixels" to rawProbe),
                )
            }
            convertLibVlcFrameToRgba(
                source = source,
                target = target,
                state = state,
            )
            if (!hasLoggedFirstPixelProbe) {
                hasLoggedFirstPixelProbe = true
                val probe = describeVmemPixels(target, pixelCount = 4)
                Log.i(TAG, "VMEM first pixels after conversion: $probe")
                MiruLog.i(
                    TAG,
                    "VMEM first pixels after conversion",
                    mapOf("pixels" to probe),
                )
            }
            frameState = state
            uploadRgbaFrame(state, target, nativeConverted = false)
        }

        private fun uploadPlanarHdrFrame(
            state: LibVlcVmemStreamState,
            source: ByteBuffer,
        ) {
            val specs = resolvePlanarHdrPlaneUploadSpecs(state)
            if (specs.isEmpty()) {
                uploadFrame(state, source)
                return
            }
            frameState = state
            activeFramePipeline = VmemFramePipeline.RAW_YUV_SHADER
            rawHdrPlaneSpecs = specs
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            specs.forEach { spec ->
                val textureIndex = spec.planeIndex.coerceIn(0, planeTextureIds.lastIndex)
                val textureId = planeTextureIds[textureIndex]
                if (textureId == 0) {
                    return@forEach
                }
                val uploadBuffer = source.duplicate().order(ByteOrder.nativeOrder()).apply {
                    position(spec.bufferOffset)
                    limit(spec.bufferOffset + spec.byteCount)
                }.slice().order(ByteOrder.nativeOrder())
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1 + textureIndex)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                if (
                    planeTextureWidths[textureIndex] != spec.textureWidth ||
                    planeTextureHeights[textureIndex] != spec.textureHeight
                ) {
                    GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D,
                        0,
                        GLES20.GL_LUMINANCE_ALPHA,
                        spec.textureWidth,
                        spec.textureHeight,
                        0,
                        GLES20.GL_LUMINANCE_ALPHA,
                        GLES20.GL_UNSIGNED_BYTE,
                        null,
                    )
                    planeTextureWidths[textureIndex] = spec.textureWidth
                    planeTextureHeights[textureIndex] = spec.textureHeight
                }
                GLES20.glTexSubImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    spec.textureWidth,
                    spec.textureHeight,
                    GLES20.GL_LUMINANCE_ALPHA,
                    GLES20.GL_UNSIGNED_BYTE,
                    uploadBuffer,
                )
            }
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            if (!hasLoggedFirstPixelProbe) {
                hasLoggedFirstPixelProbe = true
                val probe = describeRawVmemBytes(source, pixelCount = 8)
                Log.i(TAG, "VMEM raw HDR plane bytes before shader upload: $probe")
                MiruLog.i(
                    TAG,
                    "VMEM raw HDR plane bytes before shader upload",
                    mapOf("pixels" to probe, "path" to activeFramePipeline.logLabel),
                )
            }
        }

        private fun uploadRgbaFrame(
            state: LibVlcVmemStreamState,
            rgbaBuffer: ByteBuffer,
            nativeConverted: Boolean,
        ) {
            frameState = state
            activeFramePipeline = VmemFramePipeline.RGBA_TEXTURE
            rawHdrPlaneSpecs = emptyList()
            rgbaBuffer.rewind()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, rgbaTextureId)
            if (!textureStorageInitialized) {
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    GLES20.GL_RGBA,
                    frameWidth,
                    frameHeight,
                    0,
                    GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE,
                    null,
                )
                textureStorageInitialized = true
            }
            GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                0,
                0,
                frameWidth,
                frameHeight,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                rgbaBuffer,
            )
            if (!hasLoggedFirstPixelProbe) {
                hasLoggedFirstPixelProbe = true
                val probe = describeVmemPixels(rgbaBuffer, pixelCount = 4)
                Log.i(
                    TAG,
                    "VMEM first pixels after ${if (nativeConverted) "native RGBA conversion" else "Kotlin conversion"}: $probe",
                )
                MiruLog.i(
                    TAG,
                    "VMEM first pixels after conversion",
                    mapOf(
                        "pixels" to probe,
                        "path" to if (nativeConverted) "native_rgba" else "kotlin_fallback",
                    ),
                )
            }
        }

        private fun captureFrameIfRequested() {
            val label = pendingCaptureLabel ?: return
            if (!hasUploadedFrame) {
                if (waitingForFirstFrameCaptureLabel != label) {
                    waitingForFirstFrameCaptureLabel = label
                    Log.i(TAG, "Waiting for first VMEM frame before capturing label=$label")
                }
                return
            }
            waitingForFirstFrameCaptureLabel = null
            val width = this@LibVlcVmemVideoSurfaceView.width
            val height = this@LibVlcVmemVideoSurfaceView.height
            if (width <= 0 || height <= 0) {
                Log.w(
                    TAG,
                    "Deferring VMEM GL frame capture label=$label because surface size is ${width}x${height}",
                )
                MiruLog.w(
                    TAG,
                    "Deferring VMEM GL frame capture because surface size is invalid",
                    attributes = mapOf(
                        "label" to label,
                        "width" to width.toString(),
                        "height" to height.toString(),
                    ),
                )
                return
            }
            pendingCaptureLabel = null
            captureInFlightLabel = label
            runCatching {
                Log.i(
                    TAG,
                    "Capturing VMEM GL frame label=$label surface=${width}x${height} frameVersion=$lastFrameVersion",
                )
                MiruLog.i(
                    TAG,
                    "Capturing VMEM GL frame",
                    mapOf(
                        "label" to label,
                        "stage" to "start",
                        "width" to width.toString(),
                        "height" to height.toString(),
                        "frameVersion" to lastFrameVersion.toString(),
                    ),
                )
                val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
                Log.i(TAG, "Capturing VMEM GL frame label=$label stage=glReadPixels.begin")
                GLES20.glReadPixels(
                    0,
                    0,
                    width,
                    height,
                    GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE,
                    pixelBuffer,
                )
                Log.i(TAG, "Capturing VMEM GL frame label=$label stage=glReadPixels.end")
                pixelBuffer.rewind()
                val rawPixels = ByteArray(width * height * 4)
                pixelBuffer.get(rawPixels)
                Log.i(TAG, "Capturing VMEM GL frame label=$label stage=copyPixels.end")
                val flippedPixels = ByteArray(rawPixels.size)
                flipRgbaRows(
                    source = rawPixels,
                    target = flippedPixels,
                    width = width,
                    height = height,
                )
                Log.i(TAG, "Capturing VMEM GL frame label=$label stage=rowFlip.end")
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(flippedPixels))
                Log.i(TAG, "Capturing VMEM GL frame label=$label stage=bitmap.end")
                val outputDir = File(context.filesDir, "MiruPlayGlCaptures").apply { mkdirs() }
                val outputFile = File(outputDir, "${sanitizeOutputFileName(label)}.png")
                FileOutputStream(outputFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                bitmap.recycle()
                Log.i(TAG, "Capturing VMEM GL frame label=$label stage=write.end path=${outputFile.absolutePath}")
                Log.i(TAG, "Captured VMEM GL frame label=$label path=${outputFile.absolutePath}")
                MiruLog.i(
                    TAG,
                    "Captured VMEM GL frame",
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
                captureInFlightLabel = null
            }.onFailure { error ->
                captureInFlightLabel = null
                pendingCaptureLabel = label
                MiruLog.e(
                    TAG,
                    "Failed to capture VMEM GL frame",
                    error,
                    mapOf(
                        "label" to label,
                        "width" to width.toString(),
                        "height" to height.toString(),
                    ),
                )
                Log.e(TAG, "Failed to capture VMEM GL frame label=$label", error)
            }
        }
    }

    private data class ShaderConfig(
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
            ): ShaderConfig {
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
                return ShaderConfig(
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
        private const val TAG = "LibVlcVmemVideoView"
        private const val STRIDE_BYTES = 4 * Float.SIZE_BYTES
        private const val MIN_UPLOAD_INTERVAL_MS = 33L
        private val VERTEX_DATA = floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f,
        )

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform sampler2D uPlaneY;
            uniform sampler2D uPlaneU;
            uniform sampler2D uPlaneV;
            uniform float uPipelineMode;
            uniform float uEnabled;
            uniform float uExposure;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uHighlightCompression;
            varying vec2 vTexCoord;

            float clamp01(float value) {
                return clamp(value, 0.0, 1.0);
            }

            float decodeLa16(vec4 sample) {
                float low = sample.r * 255.0;
                float high = sample.a * 255.0;
                return low + (high * 256.0);
            }

            float pqEotf(float value) {
                float m1 = 2610.0 / 16384.0;
                float m2 = 2523.0 / 32.0;
                float c1 = 3424.0 / 4096.0;
                float c2 = 2413.0 / 128.0;
                float c3 = 2392.0 / 128.0;
                float powerValue = pow(clamp01(value), 1.0 / m2);
                float numerator = max(powerValue - c1, 0.0);
                float denominator = c2 - c3 * powerValue;
                if (denominator <= 0.0) {
                    return 0.0;
                }
                return pow(numerator / denominator, 1.0 / m1);
            }

            vec3 bt2020LinearToBt709(vec3 linearBt2020) {
                return vec3(
                    1.6605 * linearBt2020.r - 0.5876 * linearBt2020.g - 0.0728 * linearBt2020.b,
                    -0.1246 * linearBt2020.r + 1.1329 * linearBt2020.g - 0.0083 * linearBt2020.b,
                    -0.0182 * linearBt2020.r - 0.1006 * linearBt2020.g + 1.1187 * linearBt2020.b
                );
            }

            float toneMapLinearToSdr(float linear) {
                float nits = max(linear, 0.0) * 10000.0 * 1.4;
                float mapped = nits / (1.0 + (nits / 120.0));
                return pow(clamp01(mapped / 120.0), 1.0 / 2.2);
            }

            vec3 sampleHdrPlanarColor() {
                float yCode = decodeLa16(texture2D(uPlaneY, vTexCoord));
                float uCode = decodeLa16(texture2D(uPlaneU, vTexCoord));
                float vCode = decodeLa16(texture2D(uPlaneV, vTexCoord));
                float yPrime = clamp01((yCode - 64.0) / 876.0);
                float u = (uCode - 512.0) / 896.0;
                float v = (vCode - 512.0) / 896.0;
                float rPrime = clamp01(yPrime + 1.4746 * v);
                float gPrime = clamp01(yPrime - 0.164553 * u - 0.571353 * v);
                float bPrime = clamp01(yPrime + 1.8814 * u);
                vec3 linearBt709 = bt2020LinearToBt709(vec3(
                    pqEotf(rPrime),
                    pqEotf(gPrime),
                    pqEotf(bPrime)
                ));
                return vec3(
                    toneMapLinearToSdr(linearBt709.r),
                    toneMapLinearToSdr(linearBt709.g),
                    toneMapLinearToSdr(linearBt709.b)
                );
            }

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
                vec3 color = uPipelineMode > 0.5
                    ? sampleHdrPlanarColor()
                    : texture2D(uTexture, vTexCoord).rgb;
                if (uEnabled > 0.5) {
                    color = applyToneMapping(color);
                }
                gl_FragColor = vec4(color, 1.0);
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

        private fun createTexture2dArray(target: IntArray) {
            GLES20.glGenTextures(target.size, target, 0)
            target.forEach { textureId ->
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
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
            }
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
                    error("Failed to link VMEM GL program: $error")
                }
                GLES20.glDeleteShader(vertexShader)
                GLES20.glDeleteShader(fragmentShader)
            }
        }

        private fun compileShader(type: Int, shaderSource: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderSource)
            GLES20.glCompileShader(shader)
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                error("Failed to compile VMEM GL shader: $error")
            }
            return shader
        }

        private fun sanitizeOutputFileName(label: String): String =
            label.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}

internal fun shouldQueueVmemCaptureRequest(
    pendingCaptureLabel: String?,
    captureInFlightLabel: String?,
    requestedLabel: String,
): Boolean = pendingCaptureLabel != requestedLabel && captureInFlightLabel != requestedLabel

internal fun shouldPollLatestVmemFrame(
    hasUploadedFrame: Boolean,
    pendingCaptureLabel: String?,
    nowMs: Long,
    lastUploadMs: Long,
    minUploadIntervalMs: Long,
): Boolean {
    if (pendingCaptureLabel != null && hasUploadedFrame) {
        return false
    }
    if (!hasUploadedFrame) {
        return true
    }
    if (lastUploadMs <= 0L) {
        return true
    }
    return nowMs - lastUploadMs >= minUploadIntervalMs
}

internal enum class VmemFramePipeline(
    val logLabel: String,
) {
    RGBA_TEXTURE("native_rgba"),
    RAW_YUV_SHADER("raw_yuv_shader"),
}

internal data class VmemPlaneUploadSpec(
    val planeIndex: Int,
    val textureWidth: Int,
    val textureHeight: Int,
    val bufferOffset: Int,
    val byteCount: Int,
)

internal fun resolveVmemFramePipeline(
    state: LibVlcVmemStreamState,
): VmemFramePipeline = if (resolvePlanarHdrPlaneUploadSpecs(state).isNotEmpty()) {
    VmemFramePipeline.RAW_YUV_SHADER
} else {
    VmemFramePipeline.RGBA_TEXTURE
}

internal fun resolvePlanarHdrPlaneUploadSpecs(
    state: LibVlcVmemStreamState,
): List<VmemPlaneUploadSpec> {
    if (!isPlanarHdrVmemChroma(state.chroma) || state.planeCount < 3) {
        return emptyList()
    }
    val lumaWidth = state.pitch / 2
    val lumaHeight = state.line0
    val chromaWidth = state.pitch1 / 2
    val chromaHeightU = state.line1
    val chromaHeightV = state.line2
    if (
        lumaWidth <= 0 || lumaHeight <= 0 ||
        chromaWidth <= 0 || chromaHeightU <= 0 || chromaHeightV <= 0
    ) {
        return emptyList()
    }
    val plane0Bytes = state.pitch * lumaHeight
    val plane1Bytes = state.pitch1 * chromaHeightU
    val plane2Bytes = state.pitch2 * chromaHeightV
    return listOf(
        VmemPlaneUploadSpec(
            planeIndex = 0,
            textureWidth = lumaWidth,
            textureHeight = lumaHeight,
            bufferOffset = 0,
            byteCount = plane0Bytes,
        ),
        VmemPlaneUploadSpec(
            planeIndex = 1,
            textureWidth = chromaWidth,
            textureHeight = chromaHeightU,
            bufferOffset = plane0Bytes,
            byteCount = plane1Bytes,
        ),
        VmemPlaneUploadSpec(
            planeIndex = 2,
            textureWidth = (state.pitch2 / 2).coerceAtLeast(chromaWidth),
            textureHeight = chromaHeightV,
            bufferOffset = plane0Bytes + plane1Bytes,
            byteCount = plane2Bytes,
        ),
    )
}

private fun isPlanarHdrVmemChroma(
    chroma: String,
): Boolean = when (chroma) {
    "I0AL",
    "I0CL",
    "I0FL",
    "I09L",
    "I09B",
    "I2AL",
    "I2CL",
    "I2FL",
    -> true
    else -> false
}

internal fun repackLibVlcRv32ToRgba(
    source: ByteBuffer,
    target: ByteBuffer,
    width: Int,
    height: Int,
    pitch: Int,
) {
    require(width >= 0) { "width must be non-negative" }
    require(height >= 0) { "height must be non-negative" }
    require(pitch >= width * 4) { "pitch must be at least width * 4" }

    source.rewind()
    target.clear()
    for (row in 0 until height) {
        val rowOffset = row * pitch
        for (column in 0 until width) {
            val pixelOffset = rowOffset + column * 4
            val r = source.get(pixelOffset)
            val g = source.get(pixelOffset + 1)
            target.put(r)
            target.put(g)
            target.put(source.get(pixelOffset + 2))
            target.put(0xFF.toByte())
        }
    }
    target.flip()
}

internal fun convertLibVlcFrameToRgba(
    source: ByteBuffer,
    target: ByteBuffer,
    state: LibVlcVmemStreamState,
) {
    when (state.chroma) {
        "RV32" -> repackLibVlcRv32ToRgba(
            source = source,
            target = target,
            width = state.visibleWidth.coerceAtLeast(state.width),
            height = state.visibleHeight.coerceAtLeast(state.height),
            pitch = state.pitch,
        )
        else -> convertPlanarOrSemiPlanarFrameToRgba(source, target, state)
    }
}

private fun convertPlanarOrSemiPlanarFrameToRgba(
    source: ByteBuffer,
    target: ByteBuffer,
    state: LibVlcVmemStreamState,
) {
    val width = state.visibleWidth.coerceAtLeast(state.width)
    val height = state.visibleHeight.coerceAtLeast(state.height)
    val bitDepth = when (state.chroma) {
        "P012" -> 12
        "P016" -> 16
        "P010",
        "I0AL",
        "I0CL",
        "I0FL",
        "I09L",
        "I09B",
        "I2AL",
        "I2CL",
        "I2FL",
        -> 10
        else -> 8
    }
    val bytesPerSample = if (bitDepth > 8) 2 else 1
    val plane0Offset = 0
    val plane1Offset = plane0Offset + state.pitch * max(state.line0, state.height)
    val plane2Offset = plane1Offset + state.pitch1 * max(state.line1, (state.height + 1) / 2)
    target.clear()
    for (row in 0 until height) {
        for (column in 0 until width) {
            val yCode = readSample(
                buffer = source,
                offset = plane0Offset + row * state.pitch + column * bytesPerSample,
                bitDepth = bitDepth,
            )
            val chromaColumn = column / 2
            val chromaRow = row / 2
            val uCode: Int
            val vCode: Int
            when {
                state.chroma == "NV12" || state.chroma == "NV21" || state.chroma == "P010" || state.chroma == "P012" || state.chroma == "P016" -> {
                    val uvOffset = plane1Offset + chromaRow * state.pitch1 + chromaColumn * bytesPerSample * 2
                    if (state.chroma == "NV21") {
                        vCode = readSample(source, uvOffset, bitDepth)
                        uCode = readSample(source, uvOffset + bytesPerSample, bitDepth)
                    } else {
                        uCode = readSample(source, uvOffset, bitDepth)
                        vCode = readSample(source, uvOffset + bytesPerSample, bitDepth)
                    }
                }
                else -> {
                    uCode = readSample(
                        buffer = source,
                        offset = plane1Offset + chromaRow * state.pitch1 + chromaColumn * bytesPerSample,
                        bitDepth = bitDepth,
                    )
                    vCode = readSample(
                        buffer = source,
                        offset = plane2Offset + chromaRow * state.pitch2 + chromaColumn * bytesPerSample,
                        bitDepth = bitDepth,
                    )
                }
            }
            val rgba = toneMapYuvToRgba(
                yCode = yCode,
                uCode = uCode,
                vCode = vCode,
                bitDepth = bitDepth,
                bt2020 = bitDepth > 8,
            )
            target.put(rgba[0])
            target.put(rgba[1])
            target.put(rgba[2])
            target.put(0xFF.toByte())
        }
    }
    target.flip()
}

private fun readSample(
    buffer: ByteBuffer,
    offset: Int,
    bitDepth: Int,
): Int {
    if (bitDepth <= 8) {
        return buffer.get(offset).toInt() and 0xFF
    }
    val low = buffer.get(offset).toInt() and 0xFF
    val high = buffer.get(offset + 1).toInt() and 0xFF
    val raw = low or (high shl 8)
    val maxCode = when {
        bitDepth >= 16 -> 0xFFFF
        else -> (1 shl bitDepth) - 1
    }
    if (raw > maxCode && bitDepth < 16) {
        return (raw ushr (16 - bitDepth)).coerceAtMost(maxCode)
    }
    return raw.coerceAtMost(maxCode)
}

private fun toneMapYuvToRgba(
    yCode: Int,
    uCode: Int,
    vCode: Int,
    bitDepth: Int,
    bt2020: Boolean,
): ByteArray {
    val yOffset = if (bitDepth > 8) 64.0 else 16.0
    val yRange = if (bitDepth > 8) 876.0 else 219.0
    val chromaCenter = if (bitDepth > 8) 512.0 else 128.0
    val chromaRange = if (bitDepth > 8) 896.0 else 224.0
    val yPrime = clamp01((yCode - yOffset) / yRange)
    val u = (uCode - chromaCenter) / chromaRange
    val v = (vCode - chromaCenter) / chromaRange
    var rPrime: Double
    var gPrime: Double
    var bPrime: Double
    if (bt2020) {
        rPrime = yPrime + 1.4746 * v
        gPrime = yPrime - 0.164553 * u - 0.571353 * v
        bPrime = yPrime + 1.8814 * u
    } else {
        rPrime = yPrime + 1.5748 * v
        gPrime = yPrime - 0.187324 * u - 0.468124 * v
        bPrime = yPrime + 1.8556 * u
    }
    rPrime = clamp01(rPrime)
    gPrime = clamp01(gPrime)
    bPrime = clamp01(bPrime)
    if (bt2020) {
        val linearBt709 = bt2020LinearToBt709(
            rPrime = rPrime,
            gPrime = gPrime,
            bPrime = bPrime,
        )
        rPrime = toneMapLinearToSdrGamma(linearBt709[0])
        gPrime = toneMapLinearToSdrGamma(linearBt709[1])
        bPrime = toneMapLinearToSdrGamma(linearBt709[2])
    }
    return byteArrayOf(
        toByte(rPrime),
        toByte(gPrime),
        toByte(bPrime),
    )
}

private fun bt2020LinearToBt709(
    rPrime: Double,
    gPrime: Double,
    bPrime: Double,
): DoubleArray {
    val linearR = pqEotf(rPrime)
    val linearG = pqEotf(gPrime)
    val linearB = pqEotf(bPrime)
    return doubleArrayOf(
        1.6605 * linearR - 0.5876 * linearG - 0.0728 * linearB,
        -0.1246 * linearR + 1.1329 * linearG - 0.0083 * linearB,
        -0.0182 * linearR - 0.1006 * linearG + 1.1187 * linearB,
    )
}

private fun toneMapLinearToSdrGamma(linear: Double): Double {
    val nits = linear.coerceAtLeast(0.0) * 10000.0 * 1.4
    val mapped = nits / (1.0 + nits / 120.0)
    return clamp01(mapped / 120.0).pow(1.0 / 2.2)
}

private fun pqEotf(value: Double): Double {
    val safe = clamp01(value)
    val m1 = 2610.0 / 16384.0
    val m2 = 2523.0 / 32.0
    val c1 = 3424.0 / 4096.0
    val c2 = 2413.0 / 128.0
    val c3 = 2392.0 / 128.0
    val power = safe.pow(1.0 / m2)
    val numerator = max(power - c1, 0.0)
    val denominator = c2 - c3 * power
    if (denominator <= 0.0) return 0.0
    return (numerator / denominator).pow(1.0 / m1)
}

internal fun flipRgbaRows(
    source: ByteArray,
    target: ByteArray,
    width: Int,
    height: Int,
) {
    require(width >= 0) { "width must be non-negative" }
    require(height >= 0) { "height must be non-negative" }
    val rowBytes = width * 4
    require(source.size >= rowBytes * height) { "source must contain width * height * 4 bytes" }
    require(target.size >= rowBytes * height) { "target must contain width * height * 4 bytes" }
    for (row in 0 until height) {
        val sourceOffset = row * rowBytes
        val targetOffset = (height - row - 1) * rowBytes
        source.copyInto(
            destination = target,
            destinationOffset = targetOffset,
            startIndex = sourceOffset,
            endIndex = sourceOffset + rowBytes,
        )
    }
}

private fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

private fun toByte(value: Double): Byte =
    ((clamp01(value) * 255.0) + 0.5).toInt().coerceIn(0, 255).toByte()

internal fun describeVmemPixels(
    buffer: ByteBuffer,
    pixelCount: Int,
): String {
    val duplicate = buffer.duplicate()
    duplicate.rewind()
    val bytes = ByteArray((pixelCount.coerceAtLeast(0) * 4).coerceAtMost(duplicate.remaining()))
    duplicate.get(bytes)
    return bytes
        .toList()
        .chunked(4)
        .mapIndexed { index, pixel ->
            val channels = pixel.joinToString(separator = ",") { channel ->
                (channel.toInt() and 0xFF).toString()
            }
            "p$index=[$channels]"
        }
        .joinToString(separator = " ")
}

internal fun describeRawVmemBytes(
    buffer: ByteBuffer,
    pixelCount: Int,
): String {
    val duplicate = buffer.duplicate()
    duplicate.rewind()
    val bytes = ByteArray((pixelCount.coerceAtLeast(0) * 4).coerceAtMost(duplicate.remaining()))
    duplicate.get(bytes)
    return bytes
        .toList()
        .chunked(4)
        .mapIndexed { index, pixel ->
            val channels = pixel.joinToString(separator = ",") { channel ->
                (channel.toInt() and 0xFF).toString()
            }
            val r = pixel.getOrNull(0)?.toInt()?.and(0xFF) ?: 0
            val g = pixel.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
            val b = pixel.getOrNull(2)?.toInt()?.and(0xFF) ?: 0
            val x = pixel.getOrNull(3)?.toInt()?.and(0xFF) ?: 0
            "p$index=[$channels] asXrgb->rgba=[$r,$g,$b,255] x=$x"
        }
        .joinToString(separator = " ")
}
