package `is`.xyz.mpv.subtitle

import android.view.Surface
import java.io.Closeable

class NativeAssRenderer private constructor(
    private var handle: Long,
    private val calls: NativeAssCalls,
) : Closeable {

    @Synchronized
    fun addEvent(dialogueLine: String): Boolean =
        handle.takeIf { it != 0L }?.let { calls.addEvent(it, dialogueLine) } ?: false

    @Synchronized
    fun flushEvents(): Boolean =
        handle.takeIf { it != 0L }?.let(calls::flushEvents) ?: false

    @Synchronized
    fun render(
        surface: Surface,
        timeMs: Long,
        frameWidth: Int,
        frameHeight: Int,
        storageWidth: Int,
        storageHeight: Int,
    ): Int = handle.takeIf { it != 0L }?.let {
        calls.render(
            handle = it,
            surface = surface,
            timeMs = timeMs,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            storageWidth = storageWidth,
            storageHeight = storageHeight,
        )
    } ?: RENDER_ERROR

    @Synchronized
    fun clearSurface(surface: Surface, width: Int, height: Int): Boolean {
        val activeHandle = handle
        return activeHandle != 0L && calls.clearSurface(activeHandle, surface, width, height)
    }

    @Synchronized
    override fun close() {
        val activeHandle = handle
        if (activeHandle == 0L) return
        handle = 0L
        calls.release(activeHandle)
    }

    companion object {
        const val RENDER_ERROR = -1
        const val RENDER_UNCHANGED = 0
        const val RENDER_UPDATED = 1

        fun isAvailable(): Boolean = JniNativeAssCalls.isAvailable()

        fun create(
            document: ByteArray,
            fonts: List<NativeAssFont>,
        ): NativeAssRenderer? = create(document, fonts, JniNativeAssCalls)

        internal fun create(
            document: ByteArray,
            fonts: List<NativeAssFont>,
            calls: NativeAssCalls,
        ): NativeAssRenderer? {
            if (document.isEmpty() || !calls.isAvailable()) return null
            val handle = calls.create(
                document = document.copyOf(),
                fonts = fonts.map { font ->
                    NativeAssFont(font.name, font.data.copyOf())
                },
            )
            return handle.takeIf { it != 0L }?.let { NativeAssRenderer(it, calls) }
        }
    }
}

internal interface NativeAssCalls {
    fun isAvailable(): Boolean

    fun create(document: ByteArray, fonts: List<NativeAssFont>): Long

    fun addEvent(handle: Long, dialogueLine: String): Boolean

    fun flushEvents(handle: Long): Boolean

    fun render(
        handle: Long,
        surface: Surface,
        timeMs: Long,
        frameWidth: Int,
        frameHeight: Int,
        storageWidth: Int,
        storageHeight: Int,
    ): Int

    fun clearSurface(handle: Long, surface: Surface, width: Int, height: Int): Boolean

    fun release(handle: Long)
}

private object JniNativeAssCalls : NativeAssCalls {
    private val libraryLoaded = runCatching {
        System.loadLibrary("miruplay_libass")
        true
    }.getOrDefault(false)

    override fun isAvailable(): Boolean =
        libraryLoaded && runCatching(::nativeIsAvailable).getOrDefault(false)

    override fun create(document: ByteArray, fonts: List<NativeAssFont>): Long =
        nativeCreate(
            document = document,
            fontNames = fonts.map(NativeAssFont::name).toTypedArray(),
            fontData = fonts.map(NativeAssFont::data).toTypedArray(),
        )

    override fun addEvent(handle: Long, dialogueLine: String): Boolean =
        nativeAddEvent(handle, dialogueLine.toByteArray(Charsets.UTF_8))

    override fun flushEvents(handle: Long): Boolean = nativeFlushEvents(handle)

    override fun render(
        handle: Long,
        surface: Surface,
        timeMs: Long,
        frameWidth: Int,
        frameHeight: Int,
        storageWidth: Int,
        storageHeight: Int,
    ): Int = nativeRender(
        handle,
        surface,
        timeMs,
        frameWidth,
        frameHeight,
        storageWidth,
        storageHeight,
    )

    override fun clearSurface(handle: Long, surface: Surface, width: Int, height: Int): Boolean =
        nativeClearSurface(handle, surface, width, height)

    override fun release(handle: Long) = nativeRelease(handle)

    private external fun nativeIsAvailable(): Boolean

    private external fun nativeCreate(
        document: ByteArray,
        fontNames: Array<String>,
        fontData: Array<ByteArray>,
    ): Long

    private external fun nativeAddEvent(handle: Long, dialogueLine: ByteArray): Boolean

    private external fun nativeFlushEvents(handle: Long): Boolean

    private external fun nativeRender(
        handle: Long,
        surface: Surface,
        timeMs: Long,
        frameWidth: Int,
        frameHeight: Int,
        storageWidth: Int,
        storageHeight: Int,
    ): Int

    private external fun nativeClearSurface(handle: Long, surface: Surface, width: Int, height: Int): Boolean

    private external fun nativeRelease(handle: Long)
}
