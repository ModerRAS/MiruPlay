@file:Suppress("UnsafeOptInUsageError")

package com.miruplay.tv.player

import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.media3.common.Format
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import `is`.xyz.mpv.subtitle.NativeAssFont
import `is`.xyz.mpv.subtitle.NativeAssRenderer
import java.io.Closeable

class LibassSubtitleSession internal constructor(
    private val rendererFactory: LibassRendererFactory = NativeLibassRendererFactory,
    private val renderDispatcher: LibassRenderDispatcher = HandlerLibassRenderDispatcher(),
    private val monitorProvider: () -> LibassSubtitleMonitor? = { null },
) : VideoFrameMetadataListener, Closeable {
    private val lock = Any()
    private var monitor: LibassSubtitleMonitor? = null
    private var generation = 0L
    private var document: ByteArray? = null
    private val fonts = mutableListOf<NativeAssFont>()
    private val events = mutableListOf<String>()
    private var renderer: LibassRendererHandle? = null
    private var target: LibassRenderTarget? = null
    private var latestFrame: LibassVideoFrame? = null
    private var renderQueued = false
    private var rebuildPending = false
    private var active = false
    private var closed = false

    val isActive: Boolean
        get() = synchronized(lock) { active && !closed }

    fun currentGeneration(): Long = synchronized(lock) { generation }

    fun beginMedia(): Long = synchronized(lock) {
        if (closed) return@synchronized generation
        // The debug switch only takes effect at the next playback boundary, never by
        // dynamically mounting into a running session (approved condition #2).
        refreshMonitorLocked()
        monitor?.onBeginMedia()
        target?.clear(renderer)
        renderer?.close()
        renderer = null
        document = null
        fonts.clear()
        events.clear()
        latestFrame = null
        active = false
        generation += 1L
        generation
    }

    internal fun startTrack(mediaGeneration: Long, header: ByteArray?) = synchronized(lock) {
        if (!accepts(mediaGeneration)) return@synchronized
        monitor?.onStartTrack()
        target?.clear(renderer)
        renderer?.close()
        renderer = null
        document = header?.copyOf()
        events.clear()
        latestFrame = null
        active = true
        createRendererImmediatelyLocked()
    }

    internal fun acceptPayload(mediaGeneration: Long, payload: LibassPayload?) = synchronized(lock) {
        if (!accepts(mediaGeneration) || !active || payload == null) return@synchronized
        when (payload) {
            is LibassPayload.Document -> {
                document = payload.bytes.copyOf()
                events.clear()
                monitor?.onDocumentPayload(payload.bytes)
                createRendererImmediatelyLocked()
            }

            is LibassPayload.Event -> {
                events += payload.dialogueLine
                renderer?.addEvent(payload.dialogueLine)
                monitor?.onEventPayload(payload.dialogueLine)
            }
        }
    }

    fun addFont(mediaGeneration: Long, font: NativeAssFont) = synchronized(lock) {
        if (!accepts(mediaGeneration) || font.name.isBlank() || font.data.isEmpty()) return@synchronized
        val existingIndex = fonts.indexOfFirst { existing ->
            existing.name.equals(font.name, ignoreCase = true)
        }
        if (existingIndex >= 0 && fonts[existingIndex].data.contentEquals(font.data)) return@synchronized
        val copied = NativeAssFont(font.name, font.data.copyOf())
        if (existingIndex >= 0) fonts[existingIndex] = copied else fonts += copied
        scheduleRendererRebuildLocked()
    }

    internal fun onSeek(
        mediaGeneration: Long,
        flushEvents: Boolean = true,
    ) = synchronized(lock) {
        if (!accepts(mediaGeneration)) return@synchronized
        monitor?.onSeek(clearTimeline = flushEvents)
        latestFrame = null
        if (flushEvents) {
            events.clear()
            renderer?.flushEvents()
        }
    }

    internal fun onResume() = synchronized(lock) {
        if (!closed && active) monitor?.onResume()
    }

    internal fun deactivate(mediaGeneration: Long) = synchronized(lock) {
        if (!accepts(mediaGeneration)) return@synchronized
        monitor?.onDeactivate()
        target?.clear(renderer)
        renderer?.close()
        renderer = null
        document = null
        events.clear()
        latestFrame = null
        active = false
    }

    internal fun bindTarget(newTarget: LibassRenderTarget) = synchronized(lock) {
        if (closed) return@synchronized
        if (target === newTarget) return@synchronized
        target?.clear(renderer)
        target = newTarget
        scheduleRenderLocked()
    }

    fun bindSurface(surface: Surface, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        bindTarget(SurfaceLibassRenderTarget(surface, width, height))
    }

    fun unbindSurface(surface: Surface? = null) = synchronized(lock) {
        val current = target ?: return@synchronized
        if (surface != null && current is SurfaceLibassRenderTarget && current.surface !== surface) {
            return@synchronized
        }
        current.clear(renderer)
        target = null
        latestFrame = null
    }

    internal fun submitVideoFrame(
        presentationTimeUs: Long,
        storageWidth: Int,
        storageHeight: Int,
        anchorWallNs: Long? = null,
        mappingWallNs: Long? = anchorWallNs,
    ) = synchronized(lock) {
        if (closed || !active || renderer == null || target == null) return@synchronized
        latestFrame = LibassVideoFrame(
            presentationTimeUs = presentationTimeUs,
            storageWidth = storageWidth,
            storageHeight = storageHeight,
        )
        monitor?.onFrameSubmitted(
            mediaUs = presentationTimeUs,
            anchorWallNs = anchorWallNs,
            mappingWallNs = mappingWallNs,
            coalesced = renderQueued,
        )
        scheduleRenderLocked()
    }

    override fun onVideoFrameAboutToBeRendered(
        presentationTimeUs: Long,
        releaseTimeNs: Long,
        format: Format,
        mediaFormat: MediaFormat?,
    ) {
        submitVideoFrame(
            presentationTimeUs,
            format.width,
            format.height,
            anchorWallNs = releaseTimeNs,
        )
    }

    fun currentMonitorSnapshot(): LibassSubtitleMonitorSnapshot? =
        synchronized(lock) { monitor?.snapshot() }

    override fun close() {
        val shouldCloseDispatcher = synchronized(lock) {
            if (closed) return
            closed = true
            monitor?.close()
            target?.clear(renderer)
            target = null
            renderer?.close()
            renderer = null
            document = null
            fonts.clear()
            events.clear()
            latestFrame = null
            active = false
            true
        }
        if (shouldCloseDispatcher) renderDispatcher.close()
    }

    private fun accepts(mediaGeneration: Long): Boolean =
        !closed && mediaGeneration == generation

    private fun refreshMonitorLocked() {
        val next = monitorProvider()
        if (next !== monitor) {
            monitor?.close()
            monitor = next
        }
    }

    private fun createRendererImmediatelyLocked() {
        val currentDocument = document ?: return
        renderer?.close()
        renderer = rendererFactory.create(currentDocument.copyOf(), fonts.map { font ->
            NativeAssFont(font.name, font.data.copyOf())
        })
        val currentRenderer = renderer ?: return
        events.forEach(currentRenderer::addEvent)
        scheduleRenderLocked()
    }

    // Font-driven rebuilds run on the render dispatcher thread (lock-free native
    // create, atomic swap under the lock) so the extraction/playback threads never
    // block on libass renderer construction. Pending rebuilds coalesce into one.
    private fun scheduleRendererRebuildLocked() {
        if (rebuildPending || closed) return
        rebuildPending = true
        renderDispatcher.dispatch(::applyPendingRendererRebuild)
    }

    private fun applyPendingRendererRebuild() {
        val snapshot = synchronized(lock) {
            if (closed || !rebuildPending || !active) {
                rebuildPending = false
                return
            }
            rebuildPending = false
            val currentDocument = document?.copyOf() ?: return
            RebuildSnapshot(
                generation = generation,
                document = currentDocument,
                fonts = fonts.map { font -> NativeAssFont(font.name, font.data.copyOf()) },
            )
        }
        val newRenderer = rendererFactory.create(snapshot.document, snapshot.fonts)
        synchronized(lock) {
            if (closed || !active || generation != snapshot.generation) {
                newRenderer?.close()
                return
            }
            val currentRenderer = newRenderer
            if (currentRenderer != null) {
                // Replay the authoritative event list: events added while the
                // rebuild was in flight already hit the discarded renderer.
                events.forEach(currentRenderer::addEvent)
            }
            renderer?.close()
            renderer = currentRenderer
            scheduleRenderLocked()
        }
    }

    private data class RebuildSnapshot(
        val generation: Long,
        val document: ByteArray,
        val fonts: List<NativeAssFont>,
    )

    private fun scheduleRenderLocked() {
        if (renderQueued || latestFrame == null || renderer == null || target == null || !active || closed) return
        renderQueued = true
        renderDispatcher.dispatch(::drainLatestFrame)
    }

    private fun drainLatestFrame() {
        val request = synchronized(lock) {
            val frame = latestFrame
            latestFrame = null
            val currentRenderer = renderer
            val currentTarget = target
            if (closed || !active || frame == null || currentRenderer == null || currentTarget == null) {
                renderQueued = false
                return
            }
            RenderRequest(currentRenderer, currentTarget, frame)
        }

        monitor?.onRenderStarted()
        val renderResult = request.target.render(request.renderer, request.frame)
        monitor?.onRenderFinished(request.frame.presentationTimeUs, renderResult)

        synchronized(lock) {
            renderQueued = false
            scheduleRenderLocked()
        }
    }

    private data class RenderRequest(
        val renderer: LibassRendererHandle,
        val target: LibassRenderTarget,
        val frame: LibassVideoFrame,
    )
}

internal data class LibassVideoFrame(
    val presentationTimeUs: Long,
    val storageWidth: Int,
    val storageHeight: Int,
)

internal interface LibassRendererFactory {
    fun create(document: ByteArray, fonts: List<NativeAssFont>): LibassRendererHandle?
}

internal interface LibassRendererHandle : Closeable {
    fun addEvent(dialogueLine: String): Boolean
    fun flushEvents(): Boolean

    fun render(
        surface: Surface,
        frame: LibassVideoFrame,
        width: Int,
        height: Int,
    ): Int = NativeAssRenderer.RENDER_UNCHANGED

    fun clearSurface(surface: Surface, width: Int, height: Int): Boolean = false
}

internal interface LibassRenderTarget {
    fun render(renderer: LibassRendererHandle, frame: LibassVideoFrame): Int =
        NativeAssRenderer.RENDER_UNCHANGED

    fun clear(renderer: LibassRendererHandle?)
}

internal interface LibassRenderDispatcher : Closeable {
    fun dispatch(block: () -> Unit)
}

private object NativeLibassRendererFactory : LibassRendererFactory {
    override fun create(document: ByteArray, fonts: List<NativeAssFont>): LibassRendererHandle? =
        NativeAssRenderer.create(document, fonts)?.let(::NativeLibassRendererHandle)
}

private class NativeLibassRendererHandle(
    private val delegate: NativeAssRenderer,
) : LibassRendererHandle {
    override fun addEvent(dialogueLine: String): Boolean = delegate.addEvent(dialogueLine)
    override fun flushEvents(): Boolean = delegate.flushEvents()

    override fun render(
        surface: Surface,
        frame: LibassVideoFrame,
        width: Int,
        height: Int,
    ): Int = delegate.render(
        surface = surface,
        timeMs = frame.presentationTimeUs / 1_000L,
        frameWidth = width,
        frameHeight = height,
        storageWidth = frame.storageWidth,
        storageHeight = frame.storageHeight,
    )

    override fun clearSurface(surface: Surface, width: Int, height: Int): Boolean =
        delegate.clearSurface(surface, width, height)

    override fun close() = delegate.close()
}

private class SurfaceLibassRenderTarget(
    val surface: Surface,
    private val width: Int,
    private val height: Int,
) : LibassRenderTarget {
    override fun render(renderer: LibassRendererHandle, frame: LibassVideoFrame): Int =
        renderer.render(surface, frame, width, height)

    override fun clear(renderer: LibassRendererHandle?) {
        renderer?.clearSurface(surface, width, height)
    }
}

private class HandlerLibassRenderDispatcher : LibassRenderDispatcher {
    private val lock = Any()
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var closed = false

    override fun dispatch(block: () -> Unit) = synchronized(lock) {
        if (closed) return@synchronized
        val targetHandler = handler ?: HandlerThread("MiruLibassRender").let { newThread ->
            newThread.start()
            thread = newThread
            Handler(newThread.looper).also { handler = it }
        }
        targetHandler.post(block)
        Unit
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        handler = null
        thread = null
    }
}
