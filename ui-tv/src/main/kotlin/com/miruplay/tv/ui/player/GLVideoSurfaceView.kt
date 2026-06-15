package com.miruplay.tv.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLES30
import android.util.Log
import android.view.Surface
import com.miruplay.tv.core.common.logging.MiruLog
import androidx.media3.common.Player
import com.miruplay.tv.model.ToneMappingCurvePreset
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoSignalDescriptor
import com.miruplay.tv.model.VideoSignalKind
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal const val MIN_DECODER_SURFACE_SIZE = 4

internal fun isDecoderVideoSurfaceReady(
    surface: Surface?,
    width: Int,
    height: Int,
): Boolean = surface != null && width >= MIN_DECODER_SURFACE_SIZE && height >= MIN_DECODER_SURFACE_SIZE

class GLVideoSurfaceView(
    context: Context,
) : GLSurfaceView(context) {
    private val renderer = ToneMappingRenderer()
    private var currentPlayer: Player? = null
    private var attachedPlayer: Player? = null
    private var attachedSurface: Surface? = null
    private var onVideoSurfaceReadyChanged: ((Boolean) -> Unit)? = null
    private var onFrameCaptured: ((String) -> Unit)? = null
    private var outputSurfaceWidth: Int = 0
    private var outputSurfaceHeight: Int = 0
    private var requestedCaptureLabelForTest: String? = null

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun bind(
        player: Player?,
        ruleSet: ToneMappingRuleSet,
        signalDescriptor: VideoSignalDescriptor?,
    ) {
        queueEvent {
            renderer.updateConfig(ruleSet, signalDescriptor)
        }
        if (currentPlayer !== player) {
            detachPlayerSurface()
            currentPlayer = player
            attachPlayerSurfaceIfReady()
        }
        requestRender()
    }

    fun setOnVideoSurfaceReadyChanged(listener: ((Boolean) -> Unit)?) {
        onVideoSurfaceReadyChanged = listener
        listener?.invoke(
            isDecoderVideoSurfaceReady(renderer.outputSurface, outputSurfaceWidth, outputSurfaceHeight),
        )
    }

    fun decoderOutputSurface(): Surface? = renderer.outputSurface

    fun decoderOutputSurfaceTexture(): SurfaceTexture? = renderer.outputSurfaceTexture()

    fun decoderOutputSurfaceWidth(): Int = outputSurfaceWidth

    fun decoderOutputSurfaceHeight(): Int = outputSurfaceHeight

    fun captureNextRenderedFrame(label: String) {
        requestedCaptureLabelForTest = label
        Log.i("GLVideoSurfaceView", "Queued GL frame capture for label=$label")
        queueEvent {
            renderer.requestFrameCapture(label)
        }
        requestRender()
    }

    fun setOnFrameCaptured(listener: ((String) -> Unit)?) {
        onFrameCaptured = listener
    }

    internal fun pendingCaptureLabelForTest(): String? =
        renderer.pendingCaptureLabelForTest() ?: requestedCaptureLabelForTest

    override fun onDetachedFromWindow() {
        detachPlayerSurface()
        queueEvent {
            renderer.releaseResources()
        }
        super.onDetachedFromWindow()
    }

    private fun attachPlayerSurfaceIfReady() {
        val player = currentPlayer ?: return
        val surface = renderer.outputSurface ?: return
        if (!isDecoderVideoSurfaceReady(surface, outputSurfaceWidth, outputSurfaceHeight)) {
            Log.i(
                "GLVideoSurfaceView",
                "Deferring player surface attach until decoder surface has a valid size " +
                    "surface=${surface.hashCode()} size=${outputSurfaceWidth}x${outputSurfaceHeight}",
            )
            return
        }
        if (attachedPlayer === player && attachedSurface === surface) {
            return
        }
        detachPlayerSurface()
        attachedPlayer = player
        attachedSurface = surface
        Log.i(
            "GLVideoSurfaceView",
            "Attaching player surface surface=${surface.hashCode()} size=${outputSurfaceWidth}x${outputSurfaceHeight}",
        )
        player.setVideoSurface(surface)
    }

    private fun detachPlayerSurface() {
        val player = attachedPlayer
        val surface = attachedSurface
        if (player != null && surface != null) {
            player.clearVideoSurface(surface)
        }
        attachedPlayer = null
        attachedSurface = null
    }

    private inner class ToneMappingRenderer :
        Renderer,
        SurfaceTexture.OnFrameAvailableListener {
        private val frameAvailable = AtomicBoolean(false)
        private val textureTransform = FloatArray(16)
        private val vertexBuffer: FloatBuffer = ByteBuffer
            .allocateDirect(VERTEX_DATA.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(VERTEX_DATA)
                position(0)
            }

        var outputSurface: Surface? = null
            private set

        private var surfaceTexture: SurfaceTexture? = null
        private var programId: Int = 0
        private var textureId: Int = 0
        private var positionHandle: Int = 0
        private var texCoordHandle: Int = 0
        private var textureMatrixHandle: Int = 0
        private var samplerHandle: Int = 0
        private var enabledHandle: Int = 0
        private var exposureHandle: Int = 0
        private var contrastHandle: Int = 0
        private var saturationHandle: Int = 0
        private var highlightCompressionHandle: Int = 0
        private var shaderConfig: ShaderConfig = ShaderConfig()
        private var pendingCaptureLabel: String? = null
        private var hasRenderedVideoFrame: Boolean = false
        private var waitingForFirstFrameCaptureLabel: String? = null

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            val glVersion = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
            val glVendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty()
            val glRenderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
            val glExtensions = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty()
            Log.i(
                "GLVideoSurfaceView",
                "GL context created version=$glVersion vendor=$glVendor renderer=$glRenderer " +
                    "extYuvTarget=${glExtensions.contains("GL_EXT_YUV_target")} " +
                    "ext10Bit=${glExtensions.contains("GL_EXT_texture_norm16")} " +
                    "extBT2020=${glExtensions.contains("GL_EXT_gl_colorspace_bt2020_pq")}",
            )
            programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            textureId = createExternalTexture()
            positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
            texCoordHandle = GLES20.glGetAttribLocation(programId, "aTexCoord")
            textureMatrixHandle = GLES20.glGetUniformLocation(programId, "uTexMatrix")
            samplerHandle = GLES20.glGetUniformLocation(programId, "uTexture")
            enabledHandle = GLES20.glGetUniformLocation(programId, "uEnabled")
            exposureHandle = GLES20.glGetUniformLocation(programId, "uExposure")
            contrastHandle = GLES20.glGetUniformLocation(programId, "uContrast")
            saturationHandle = GLES20.glGetUniformLocation(programId, "uSaturation")
            highlightCompressionHandle = GLES20.glGetUniformLocation(programId, "uHighlightCompression")
            surfaceTexture = SurfaceTexture(textureId).also {
                it.setOnFrameAvailableListener(this)
            }
            outputSurface = Surface(surfaceTexture)
            outputSurfaceWidth = 0
            outputSurfaceHeight = 0
            Log.i("GLVideoSurfaceView", "Surface created for experimental GL output")
            post {
                onVideoSurfaceReadyChanged?.invoke(
                    isDecoderVideoSurfaceReady(outputSurface, outputSurfaceWidth, outputSurfaceHeight),
                )
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            outputSurfaceWidth = width
            outputSurfaceHeight = height
            surfaceTexture?.setDefaultBufferSize(width, height)
            Log.i(
                "GLVideoSurfaceView",
                "Surface changed for experimental GL output size=${width}x${height}",
            )
            GLES20.glViewport(0, 0, width, height)
            post {
                attachPlayerSurfaceIfReady()
                onVideoSurfaceReadyChanged?.invoke(
                    isDecoderVideoSurfaceReady(outputSurface, outputSurfaceWidth, outputSurfaceHeight),
                )
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            val localSurfaceTexture = surfaceTexture ?: return
            if (frameAvailable.compareAndSet(true, false)) {
                localSurfaceTexture.updateTexImage()
                localSurfaceTexture.getTransformMatrix(textureTransform)
                hasRenderedVideoFrame = true
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

            GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureTransform, 0)
            GLES20.glUniform1i(samplerHandle, 0)
            GLES20.glUniform1f(enabledHandle, if (shaderConfig.enabled) 1f else 0f)
            GLES20.glUniform1f(exposureHandle, shaderConfig.exposure)
            GLES20.glUniform1f(contrastHandle, shaderConfig.contrast)
            GLES20.glUniform1f(saturationHandle, shaderConfig.saturation)
            GLES20.glUniform1f(highlightCompressionHandle, shaderConfig.highlightCompression)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            captureFrameIfRequested()
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
            frameAvailable.set(true)
            if (!hasRenderedVideoFrame) {
                Log.i("GLVideoSurfaceView", "Received first video frame from SurfaceTexture")
            }
            requestRender()
        }

        fun updateConfig(
            ruleSet: ToneMappingRuleSet,
            signalDescriptor: VideoSignalDescriptor?,
        ) {
            shaderConfig = ShaderConfig.from(ruleSet, signalDescriptor)
        }

        fun requestFrameCapture(label: String) {
            pendingCaptureLabel = label
        }

        fun pendingCaptureLabelForTest(): String? = pendingCaptureLabel

        fun outputSurfaceTexture(): SurfaceTexture? = surfaceTexture

        fun releaseResources() {
            outputSurface?.let { surface ->
                post {
                    if (attachedSurface === surface) {
                        detachPlayerSurface()
                    }
                    onVideoSurfaceReadyChanged?.invoke(false)
                }
                surface.release()
            }
            outputSurface = null
            outputSurfaceWidth = 0
            outputSurfaceHeight = 0
            surfaceTexture?.release()
            surfaceTexture = null
            if (textureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                textureId = 0
            }
            if (programId != 0) {
                GLES20.glDeleteProgram(programId)
                programId = 0
            }
        }

        private fun captureFrameIfRequested() {
            val label = pendingCaptureLabel ?: return
            if (!hasRenderedVideoFrame) {
                if (waitingForFirstFrameCaptureLabel != label) {
                    waitingForFirstFrameCaptureLabel = label
                    Log.i("GLVideoSurfaceView", "Waiting for first video frame before capturing label=$label")
                }
                return
            }
            waitingForFirstFrameCaptureLabel = null
            pendingCaptureLabel = null
            try {
                Log.i("GLVideoSurfaceView", "Starting GL frame capture for label=$label")
                val width = width
                val height = height
                if (width <= 0 || height <= 0) {
                    Log.w("GLVideoSurfaceView", "Skipping GL frame capture for $label because size is ${width}x${height}")
                    MiruLog.w(
                        "GLVideoSurfaceView",
                        "Skipping GL frame capture because surface size is invalid",
                        attributes = mapOf(
                            "label" to label,
                            "width" to width.toString(),
                            "height" to height.toString(),
                        ),
                    )
                    return
                }
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
                val outputDir = File(
                    this@GLVideoSurfaceView.context.filesDir,
                    "MiruPlayGlCaptures",
                )
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }
                val outputFile = File(outputDir, "${sanitizeFileName(label)}.png")
                FileOutputStream(outputFile).use { stream ->
                    flipped.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                flipped.recycle()
                Log.i(
                    "GLVideoSurfaceView",
                    "Captured GL frame label=$label path=${outputFile.absolutePath} size=${width}x${height}",
                )
                post {
                    onFrameCaptured?.invoke(label)
                }
                MiruLog.i(
                    "GLVideoSurfaceView",
                    "Captured GL frame",
                    mapOf(
                        "label" to label,
                        "path" to outputFile.absolutePath,
                        "width" to width.toString(),
                        "height" to height.toString(),
                    ),
                )
            } catch (error: Throwable) {
                Log.e("GLVideoSurfaceView", "Failed to capture GL frame for $label", error)
                MiruLog.e(
                    "GLVideoSurfaceView",
                    "Failed to capture GL frame",
                    error,
                    mapOf("label" to label),
                )
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
        private const val STRIDE_BYTES = 4 * Float.SIZE_BYTES
        private val VERTEX_DATA = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vec4 transformed = uTexMatrix * vec4(aTexCoord, 0.0, 1.0);
                vTexCoord = transformed.xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
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

        private fun createExternalTexture(): Int {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR.toFloat(),
            )
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR.toFloat(),
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE,
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
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
                    error("Failed to link GL program: $error")
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
                    error("Failed to compile shader: $error")
                }
            }
        }

        private fun sanitizeFileName(value: String): String =
            value.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
    }
}
