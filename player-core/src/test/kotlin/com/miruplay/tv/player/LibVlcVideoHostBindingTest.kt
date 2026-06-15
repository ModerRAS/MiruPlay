package com.miruplay.tv.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.model.defaultToneMappingRuleSet
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.util.VLCVideoLayout
import org.videolan.R as VlcR
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibVlcVideoHostBindingTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `binding a vlc host defers surface attach until host size is valid`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val host = mockk<VLCVideoLayout>(relaxed = true)
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides(),
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { player.getVLCVout() } returns vout
        every { host.width } returns 0
        every { host.height } returns 0
        every { host.isAttachedToWindow } returns false
        every { host.isLaidOut } returns false

        controller.setPrivateField("vlcMediaPlayer", player)

        controller.bindVlcVideoHost(host)

        verify(exactly = 0) {
            player.attachViews(any(), any(), any(), any())
        }
    }

    @Test
    fun `binding a vlc host uses surface attach with subtitle overlay by default`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val host = mockk<VLCVideoLayout>(relaxed = true)
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides(),
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { player.getVLCVout() } returns vout
        every { vout.areViewsAttached() } returns true
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true

        controller.setPrivateField("vlcMediaPlayer", player)

        controller.bindVlcVideoHost(host)

        verify(exactly = 1) {
            player.attachViews(host, null, true, false)
        }
    }

    @Test
    fun `binding a default vlc host does not wait on custom helper surfaces before attachViews`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val surfaceView = mockk<SurfaceView>(relaxed = true)
        val holder = mockk<SurfaceHolder>(relaxed = true)
        val invalidSurface = mockk<Surface>(relaxed = true)
        val host = mockk<VLCVideoLayout>(relaxed = true)
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides(),
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { player.getVLCVout() } returns vout
        every { vout.areViewsAttached() } returns true
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { host.childCount } returns 1
        every { host.getChildAt(0) } returns surfaceView
        every { surfaceView.width } returns 1920
        every { surfaceView.height } returns 1080
        every { surfaceView.holder } returns holder
        every { invalidSurface.isValid } returns false
        every { holder.surface } returns invalidSurface

        controller.setPrivateField("vlcMediaPlayer", player)

        controller.bindVlcVideoHost(host)

        verify(exactly = 1) {
            player.attachViews(host, null, true, false)
        }
    }

    @Test
    fun `android display mode defers stock attach until player surface frame has a real size`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val playerSurfaceFrame = mockk<FrameLayout>(relaxed = true)
        val host = mockk<VLCVideoLayout>(relaxed = true)
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.ANDROID_DISPLAY)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { player.getVLCVout() } returns vout
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { host.findViewById<FrameLayout>(VlcR.id.player_surface_frame) } returns playerSurfaceFrame
        every { playerSurfaceFrame.width } returns 0
        every { playerSurfaceFrame.height } returns 0

        controller.setPrivateField("vlcMediaPlayer", player)

        controller.bindVlcVideoHost(host)

        verify(exactly = 0) {
            player.attachViews(any(), any(), any(), any())
        }
    }

    @Test
    fun `android display mode attaches once player surface frame reports a real size`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val playerSurfaceFrame = mockk<FrameLayout>(relaxed = true)
        val host = mockk<VLCVideoLayout>(relaxed = true)
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.ANDROID_DISPLAY)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { player.getVLCVout() } returns vout
        every { vout.areViewsAttached() } returns true
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { host.findViewById<FrameLayout>(VlcR.id.player_surface_frame) } returns playerSurfaceFrame
        every { playerSurfaceFrame.width } returns 1920
        every { playerSurfaceFrame.height } returns 1080

        controller.setPrivateField("vlcMediaPlayer", player)

        controller.bindVlcVideoHost(host)

        verify(exactly = 1) {
            player.attachViews(host, null, true, false)
        }
    }

    @Test
    fun `binding a vlc host syncs host window size to libvlc`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val host = mockk<VLCVideoLayout>(relaxed = true)
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides(),
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { player.getVLCVout() } returns vout
        every { vout.areViewsAttached() } returns true
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true

        controller.setPrivateField("vlcMediaPlayer", player)

        controller.bindVlcVideoHost(host)

        verify(atLeast = 1) {
            vout.setWindowSize(1920, 1080)
        }
    }

    @Test
    fun `binding a direct texture capable vlc host uses stock layout attach path by default`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val textureView = mockk<TextureView>(relaxed = true)
        val host = mockkClass(
            VLCVideoLayout::class,
            relaxed = true,
            moreInterfaces = arrayOf(LibVlcDirectVideoHost::class),
        )
        val directHost = host as LibVlcDirectVideoHost
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides(),
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { player.getVLCVout() } returns vout
        every { vout.areViewsAttached() } returns true
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { textureView.width } returns 1920
        every { textureView.height } returns 1080
        every { textureView.isAvailable } returns true
        every { directHost.libVlcDirectVideoTextureView() } returns textureView

        controller.setPrivateField("vlcMediaPlayer", player)

        controller.bindVlcVideoHost(host)

        verify(exactly = 0) {
            vout.setVideoView(any<TextureView>())
        }
        verify(exactly = 0) {
            vout.attachViews()
        }
        verify(exactly = 1) {
            player.attachViews(host, null, true, false)
        }
    }

    @Test
    fun `android display mode avoids direct texture attach and matches stock surface host wiring`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val textureView = mockk<TextureView>(relaxed = true)
        val host = mockkClass(
            VLCVideoLayout::class,
            relaxed = true,
            moreInterfaces = arrayOf(LibVlcDirectVideoHost::class),
        )
        val directHost = host as LibVlcDirectVideoHost
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.ANDROID_DISPLAY)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { player.getVLCVout() } returns vout
        every { vout.areViewsAttached() } returns true
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { textureView.width } returns 1920
        every { textureView.height } returns 1080
        every { textureView.isAvailable } returns true
        every { directHost.libVlcDirectVideoTextureView() } returns textureView

        controller.setPrivateField("vlcMediaPlayer", player)

        controller.bindVlcVideoHost(host)

        verify(exactly = 0) {
            vout.setVideoView(any<TextureView>())
        }
        verify(exactly = 0) {
            vout.attachViews()
        }
        verify(exactly = 1) {
            player.attachViews(host, null, true, false)
        }
    }

    @Test
    fun `android display mode ignores hidden direct texture when deciding host readiness`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val textureView = mockk<TextureView>(relaxed = true)
        val host = mockkClass(
            VLCVideoLayout::class,
            relaxed = true,
            moreInterfaces = arrayOf(LibVlcDirectVideoHost::class),
        )
        val directHost = host as LibVlcDirectVideoHost
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.ANDROID_DISPLAY)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { player.getVLCVout() } returns vout
        every { vout.areViewsAttached() } returns true
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { textureView.width } returns 0
        every { textureView.height } returns 0
        every { textureView.isAvailable } returns false
        every { directHost.libVlcDirectVideoTextureView() } returns textureView

        controller.setPrivateField("vlcMediaPlayer", player)

        controller.bindVlcVideoHost(host)

        verify(exactly = 1) {
            player.attachViews(host, null, true, false)
        }
    }

    @Test
    fun `android display mode keeps the stock primary attach path even when an activity context is available`() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val host = mockk<VLCVideoLayout>(relaxed = true)
        val controller = HybridPlaybackController(
            context = activity,
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.ANDROID_DISPLAY)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { host.context } returns activity

        val displayManager = controller.invokePrivateMethod<Any?>("resolveVlcDisplayManager", host)

        assertNull(displayManager)
    }

    @Test
    fun `direct texture mode uses stock libvlc texture attach through player attachViews`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = RecordingVlcVout()
        val textureView = mockk<TextureView>(relaxed = true)
        val host = mockkClass(
            VLCVideoLayout::class,
            relaxed = true,
            moreInterfaces = arrayOf(LibVlcDirectVideoHost::class),
        )
        val directHost = host as LibVlcDirectVideoHost
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(
                    voutMode = enumValueOf<LibVlcVoutMode>("DIRECT_TEXTURE"),
                )
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { player.getVLCVout() } returns vout
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { textureView.width } returns 1920
        every { textureView.height } returns 1080
        every { textureView.isAvailable } returns true
        every { directHost.libVlcDirectVideoTextureView() } returns textureView

        controller.setPrivateField("vlcMediaPlayer", player)

        controller.bindVlcVideoHost(host)

        verify(exactly = 1) {
            directHost.setLibVlcDirectTextureEnabled(true)
        }
        assertNull(vout.videoTextureView)
        assertNull(vout.videoSurfaceTexture)
        assertEquals(0, vout.attachViewsWithListenerCount)
        verify(exactly = 1) {
            player.attachViews(host, null, false, true)
        }
    }

    @Test
    fun `gl surface mode binds dedicated surface host through libvlc surface texture attach`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = RecordingVlcVout()
        val dedicatedSurfaceTexture = mockk<SurfaceTexture>(relaxed = true)
        val host = mockkClass(
            VLCVideoLayout::class,
            relaxed = true,
            moreInterfaces = arrayOf(LibVlcSurfaceVideoHost::class),
        )
        val surfaceHost = host as LibVlcSurfaceVideoHost
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.GL_SURFACE)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { player.getVLCVout() } returns vout
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { surfaceHost.libVlcVideoSurface() } returns null
        every { surfaceHost.libVlcVideoSurfaceTexture() } returns dedicatedSurfaceTexture
        every { surfaceHost.libVlcVideoSurfaceWidth() } returns 1920
        every { surfaceHost.libVlcVideoSurfaceHeight() } returns 1080

        controller.setPrivateField("vlcMediaPlayer", player)

        controller.bindVlcVideoHost(host)

        verify(exactly = 1) {
            surfaceHost.setLibVlcVideoSurfaceEnabled(true)
        }
        assertTrue(vout.windowSizes.contains(1920 to 1080))
        assertSame(dedicatedSurfaceTexture, vout.videoSurfaceTexture)
        assertNull(vout.videoSurface)
        assertEquals(1, vout.attachViewsWithListenerCount)
        assertEquals(0, vout.attachViewsWithoutListenerCount)
        verify(exactly = 1) { player.setVideoTrackEnabled(true) }
        verify(exactly = 0) {
            player.attachViews(any(), any(), any(), any())
        }
    }

    @Test
    fun `output callbacks mode binds dedicated host through native output callback bridge`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val dedicatedSurface = mockk<Surface>(relaxed = true)
        val outputBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true)
        val bridgeSession = LibVlcOutputCallbacksSession(
            playerInstance = 55L,
            bridgeHandle = 91L,
        )
        val host = mockkClass(
            VLCVideoLayout::class,
            relaxed = true,
            moreInterfaces = arrayOf(LibVlcOutputCallbackVideoHost::class),
        )
        val outputHost = host as LibVlcOutputCallbackVideoHost
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.OUTPUT_CALLBACKS)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = outputBridge,
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        player.setPrivateField("mInstance", 55L)
        every { player.getVLCVout() } returns vout
        every { vout.areViewsAttached() } returns false
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { outputHost.libVlcOutputCallbackSurface() } returns dedicatedSurface
        every { outputHost.libVlcOutputCallbackWidth() } returns 1920
        every { outputHost.libVlcOutputCallbackHeight() } returns 1080
        every { dedicatedSurface.isValid } returns true
        every {
            outputBridge.attachOutput(
                playerInstance = 55L,
                surface = dedicatedSurface,
                width = 1920,
                height = 1080,
            )
        } returns LibVlcOutputCallbacksAttachResult(
            success = true,
            resultCode = 0,
            session = bridgeSession,
        )

        controller.setPrivateField("vlcMediaPlayer", player)
        assertTrue(controller.invokePrivateMethod<Boolean>("isVlcHostReadyForAttach", host))

        controller.bindVlcVideoHost(host)

        verify(exactly = 1) {
            outputHost.setLibVlcOutputCallbackEnabled(true)
        }
        assertEquals(true, controller.getPrivateField("usingOutputCallbackAttach"))
        assertEquals(bridgeSession, controller.getPrivateField("activeLibVlcOutputCallbackSession"))
        verify(exactly = 1) {
            outputBridge.attachOutput(
                playerInstance = 55L,
                surface = dedicatedSurface,
                width = 1920,
                height = 1080,
            )
        }
        verify(atLeast = 1) { outputBridge.updateOutputWindow(bridgeSession, 1920, 1080) }
        verify(atLeast = 1) {
            vout.setWindowSize(1920, 1080)
        }
        verify(exactly = 1) {
            player.setVideoTrackEnabled(true)
        }
        verify(exactly = 0) {
            player.attachViews(any(), any(), any(), any())
        }
        verify(exactly = 0) { vout.setVideoSurface(any<Surface>(), any()) }
        verify(exactly = 0) { vout.attachViews() }
    }

    @Test
    fun `output callbacks ready listener should bind dedicated surface with the active player even before the field is populated`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val dedicatedSurface = mockk<Surface>(relaxed = true)
        val outputBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true)
        var outputReady = false
        var readyListener: ((Boolean) -> Unit)? = null
        val host = mockkClass(
            VLCVideoLayout::class,
            relaxed = true,
            moreInterfaces = arrayOf(LibVlcOutputCallbackVideoHost::class),
        )
        val outputHost = host as LibVlcOutputCallbackVideoHost
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.OUTPUT_CALLBACKS)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = outputBridge,
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        player.setPrivateField("mInstance", 55L)
        every { player.getVLCVout() } returns vout
        every { vout.areViewsAttached() } returns false
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { dedicatedSurface.isValid } returns true
        every { outputHost.libVlcOutputCallbackSurface() } answers {
            if (outputReady) dedicatedSurface else null
        }
        every { outputHost.libVlcOutputCallbackWidth() } answers {
            if (outputReady) 1920 else 0
        }
        every { outputHost.libVlcOutputCallbackHeight() } answers {
            if (outputReady) 1080 else 0
        }
        every {
            outputHost.setOnLibVlcOutputCallbackReadyChanged(any())
        } answers {
            readyListener = firstArg()
        }

        controller.setPrivateField("vlcVideoHost", host)
        assertTrue(controller.invokePrivateMethod<Boolean>("isVlcHostReadyForAttach", host) == false)
        controller.invokePrivateMethod<Unit>("bindExistingVlcHost", player)

        verify(exactly = 0) { vout.setVideoSurface(any<Surface>(), any()) }

        outputReady = true
        assertTrue(controller.invokePrivateMethod<Boolean>("isVlcHostReadyForAttach", host))
        readyListener?.invoke(true)

        verify(exactly = 1) {
            outputBridge.attachOutput(
                playerInstance = 55L,
                surface = dedicatedSurface,
                width = 1920,
                height = 1080,
            )
        }
    }

    @Test
    fun `vmem probe mode skips view attachment entirely`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val host = mockk<VLCVideoLayout>(relaxed = true)
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.VMEM_PROBE)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        every { player.getVLCVout() } returns vout
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true

        controller.setPrivateField("vlcMediaPlayer", player)

        controller.bindVlcVideoHost(host)

        verify(exactly = 0) {
            player.attachViews(any(), any(), any(), any())
        }
        verify(exactly = 0) {
            vout.attachViews()
        }
    }

    @Test
    fun `vmem stream mode binds host and attaches a hidden libvlc window carrier`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val streamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true)
        val textureView = mockk<TextureView>(relaxed = true)
        val vmemView = mockk<android.view.View>(relaxed = true)
        val host = mockkClass(
            VLCVideoLayout::class,
            relaxed = true,
            moreInterfaces = arrayOf(LibVlcVmemVideoHost::class, LibVlcDirectVideoHost::class),
        )
        val vmemHost = host as LibVlcVmemVideoHost
        val directHost = host as LibVlcDirectVideoHost
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.VMEM_STREAM)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = streamBridge,
        )

        every { player.getVLCVout() } returns vout
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { vmemView.width } returns 1920
        every { vmemView.height } returns 1080
        every { vmemView.isAttachedToWindow } returns true
        every { vmemView.isLaidOut } returns true
        every { vmemHost.libVlcVmemVideoView() } returns vmemView
        every { textureView.width } returns 1920
        every { textureView.height } returns 1080
        every { textureView.isAvailable } returns true
        every { directHost.libVlcDirectVideoTextureView() } returns textureView

        controller.setPrivateField("vlcMediaPlayer", player)
        controller.setPrivateField(
            "activeLibVlcVmemStreamSession",
            LibVlcVmemStreamSession(streamHandle = 9L, playerInstance = 11L),
        )

        controller.bindVlcVideoHost(host)

        verify(exactly = 1) {
            vmemHost.setLibVlcVmemStreamEnabled(true)
        }
        verify(exactly = 1) {
            vmemHost.bindLibVlcVmemStream(streamBridge, any())
        }
        verify(exactly = 1) {
            player.setVideoTrackEnabled(true)
        }
        verify(exactly = 1) {
            directHost.setLibVlcDirectTextureEnabled(true)
        }
        verify(exactly = 0) {
            vout.setVideoSurface(any<SurfaceTexture>())
        }
        verify(exactly = 1) { player.attachViews(host, null, false, true) }
        assertSame(host, controller.getPrivateField("attachedVlcVideoHost"))
    }

    @Test
    fun `vmem stream mode ignores invalid stock surfaces when dedicated vmem view is ready`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val streamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true)
        val textureView = mockk<TextureView>(relaxed = true)
        val visibleSurfaceView = mockk<SurfaceView>(relaxed = true)
        val visibleHolder = mockk<SurfaceHolder>(relaxed = true)
        val invalidSurface = mockk<Surface>(relaxed = true)
        val vmemView = mockk<android.view.View>(relaxed = true)
        val host = mockkClass(
            VLCVideoLayout::class,
            relaxed = true,
            moreInterfaces = arrayOf(LibVlcVmemVideoHost::class, LibVlcDirectVideoHost::class),
        )
        val vmemHost = host as LibVlcVmemVideoHost
        val directHost = host as LibVlcDirectVideoHost
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.VMEM_STREAM)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = streamBridge,
        )

        every { player.getVLCVout() } returns vout
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { vmemView.width } returns 1920
        every { vmemView.height } returns 1080
        every { vmemView.isAttachedToWindow } returns true
        every { vmemView.isLaidOut } returns true
        every { vmemHost.libVlcVmemVideoView() } returns vmemView
        every { host.childCount } returns 1
        every { host.getChildAt(0) } returns visibleSurfaceView
        every { visibleSurfaceView.width } returns 1920
        every { visibleSurfaceView.height } returns 1080
        every { visibleSurfaceView.holder } returns visibleHolder
        every { visibleHolder.surface } returns invalidSurface
        every { invalidSurface.isValid } returns false
        every { textureView.width } returns 0
        every { textureView.height } returns 0
        every { textureView.isAvailable } returns false
        every { textureView.surfaceTexture } returns null
        every { directHost.libVlcDirectVideoTextureView() } returns textureView

        controller.setPrivateField("vlcMediaPlayer", player)
        controller.setPrivateField(
            "activeLibVlcVmemStreamSession",
            LibVlcVmemStreamSession(streamHandle = 9L, playerInstance = 11L),
        )

        controller.bindVlcVideoHost(host)

        verify(exactly = 1) {
            vmemHost.setLibVlcVmemStreamEnabled(true)
        }
        verify(exactly = 1) {
            vmemHost.bindLibVlcVmemStream(streamBridge, any())
        }
        verify(exactly = 1) {
            player.setVideoTrackEnabled(true)
        }
        verify(exactly = 0) {
            vout.setVideoView(any<TextureView>())
        }
        verify(exactly = 0) {
            vout.attachViews(any())
        }
        verify(exactly = 0) {
            vout.attachViews()
        }
        verify(exactly = 0) {
            vout.setVideoSurface(any<SurfaceTexture>())
        }
        verify(exactly = 1) { player.attachViews(host, null, false, true) }
        assertSame(host, controller.getPrivateField("attachedVlcVideoHost"))
    }

    @Test
    fun `vmem stream mode defers hidden carrier attach until dedicated vmem view reports a real size`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val streamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true)
        val vmemView = mockk<android.view.View>(relaxed = true)
        val host = mockkClass(
            VLCVideoLayout::class,
            relaxed = true,
            moreInterfaces = arrayOf(LibVlcVmemVideoHost::class, LibVlcDirectVideoHost::class),
        )
        val vmemHost = host as LibVlcVmemVideoHost
        val directHost = host as LibVlcDirectVideoHost
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.VMEM_STREAM)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = streamBridge,
        )

        every { player.getVLCVout() } returns vout
        every { player.videoScale } returns MediaPlayer.ScaleType.SURFACE_BEST_FIT
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { vmemView.width } returns 0
        every { vmemView.height } returns 0
        every { vmemView.isAttachedToWindow } returns true
        every { vmemView.isLaidOut } returns true
        every { vmemHost.libVlcVmemVideoView() } returns vmemView

        controller.setPrivateField("vlcMediaPlayer", player)
        controller.setPrivateField(
            "activeLibVlcVmemStreamSession",
            LibVlcVmemStreamSession(streamHandle = 9L, playerInstance = 11L),
        )

        controller.bindVlcVideoHost(host)

        verify(exactly = 1) {
            vmemHost.setLibVlcVmemStreamEnabled(true)
        }
        verify(exactly = 1) {
            vmemHost.bindLibVlcVmemStream(streamBridge, any())
        }
        verify(exactly = 1) {
            directHost.setLibVlcDirectTextureEnabled(true)
        }
        verify(exactly = 0) {
            player.setVideoTrackEnabled(any())
        }
        verify(exactly = 0) { player.attachViews(any(), any(), any(), any()) }
        assertNull(controller.getPrivateField("attachedVlcVideoHost"))
    }

    @Test
    fun `vmem stream mode defers playback start until host attachment completes`() {
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.VMEM_STREAM)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        val shouldDefer = controller.invokePrivateMethod<Boolean>(
            "shouldDeferLibVlcPlaybackStartUntilHostAttach",
        )

        assertTrue(shouldDefer)
    }

    @Test
    fun `vmem stream mode attaches stream after libvlc surfaces are created`() {
        val player = mockk<MediaPlayer>(relaxed = true)
        val vout = mockk<IVLCVout>(relaxed = true)
        val streamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true)
        val createdSession = LibVlcVmemStreamSession(streamHandle = 9L)
        val attachedSession = createdSession.copy(playerInstance = 55L)
        val vmemView = mockk<android.view.View>(relaxed = true)
        val host = mockkClass(
            VLCVideoLayout::class,
            relaxed = true,
            moreInterfaces = arrayOf(LibVlcVmemVideoHost::class),
        )
        val vmemHost = host as LibVlcVmemVideoHost
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.VMEM_STREAM)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = streamBridge,
        )

        player.setPrivateField("mInstance", 55L)
        every { player.getVLCVout() } returns vout
        every { vout.areViewsAttached() } returns true
        every { host.width } returns 1920
        every { host.height } returns 1080
        every { host.isAttachedToWindow } returns true
        every { host.isLaidOut } returns true
        every { vmemView.width } returns 1920
        every { vmemView.height } returns 1080
        every { vmemView.isAttachedToWindow } returns true
        every { vmemView.isLaidOut } returns true
        every { vmemHost.libVlcVmemVideoView() } returns vmemView
        every { streamBridge.createStream(null) } returns LibVlcVmemStreamCreateResult(
            success = true,
            resultCode = 0,
            session = createdSession,
        )
        every {
            streamBridge.attachStream(
                playerInstance = 55L,
                session = createdSession,
                windowWidth = 1920,
                windowHeight = 1080,
            )
        } returns LibVlcVmemStreamAttachResult(
            success = true,
            resultCode = 0,
            session = attachedSession,
        )

        controller.setPrivateField("vlcMediaPlayer", player)
        controller.setPrivateField("vlcVideoHost", host)
        controller.setPrivateField("attachedVlcVideoHost", host)
        controller.setPrivateField("usingHiddenCarrierAttach", true)

        val callback = controller.getPrivateField("vlcVoutCallback") as IVLCVout.Callback

        callback.onSurfacesCreated(vout)

        verify(exactly = 1) {
            streamBridge.createStream(null)
        }
        verify(exactly = 1) {
            streamBridge.attachStream(
                playerInstance = 55L,
                session = createdSession,
                windowWidth = 1920,
                windowHeight = 1080,
            )
        }
        verify(exactly = 1) {
            vmemHost.bindLibVlcVmemStream(streamBridge, attachedSession)
        }
        assertEquals(attachedSession, controller.getPrivateField("activeLibVlcVmemStreamSession"))
    }

    @Test
    fun `vmem stream mode keeps playback start deferred until stream session attaches`() {
        val controller = HybridPlaybackController(
            context = mockk<Context>(relaxed = true),
            exoController = mockExoController(),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides().apply {
                libVlcDebugConfig = LibVlcDebugConfig(voutMode = LibVlcVoutMode.VMEM_STREAM)
            },
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            libVlcSnapshotBridge = mockk<LibVlcSnapshotBridge>(relaxed = true),
            libVlcFrameProbeBridge = mockk<LibVlcFrameProbeBridge>(relaxed = true),
            libVlcOutputCallbacksBridge = mockk<LibVlcOutputCallbacksBridge>(relaxed = true),
            libVlcVmemStreamBridge = mockk<LibVlcVmemStreamBridge>(relaxed = true),
        )

        controller.setPrivateField("attachedVlcVideoHost", mockk<VLCVideoLayout>(relaxed = true))

        val shouldDefer = controller.invokePrivateMethod<Boolean>(
            "shouldDeferLibVlcPlaybackStartUntilHostAttach",
        )

        assertTrue(shouldDefer)
    }

    @Test
    fun `mocked libvlc media player still exposes a native instance through reflection helpers`() {
        val player = mockk<MediaPlayer>(relaxed = true)

        player.setPrivateField("mInstance", 55L)

        assertEquals(55L, resolveNativeVlcObjectInstance(0L, player))
    }
}

private fun Any.setPrivateField(fieldName: String, value: Any?) {
    var currentClass: Class<*>? = javaClass
    while (currentClass != null) {
        runCatching {
            val field = currentClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(this, value)
            return
        }
        currentClass = currentClass.superclass
    }
    error("Field $fieldName was not found on ${javaClass.name}")
}

private fun Any.getPrivateField(fieldName: String): Any? {
    var currentClass: Class<*>? = javaClass
    while (currentClass != null) {
        runCatching {
            val field = currentClass.getDeclaredField(fieldName)
            field.isAccessible = true
            return field.get(this)
        }
        currentClass = currentClass.superclass
    }
    error("Field $fieldName was not found on ${javaClass.name}")
}

@Suppress("UNCHECKED_CAST")
private fun <T> Any.invokePrivateMethod(methodName: String, vararg args: Any?): T {
    var currentClass: Class<*>? = javaClass
    while (currentClass != null) {
        val method = currentClass.declaredMethods.firstOrNull { candidate ->
            candidate.name == methodName && candidate.parameterCount == args.size
        }
        if (method != null) {
            method.isAccessible = true
            return method.invoke(this, *args) as T
        }
        currentClass = currentClass.superclass
    }
    error("Method $methodName was not found on ${javaClass.name}")
}

private fun mockExoController(): ExoPlaybackController =
    mockk<ExoPlaybackController>(relaxed = true).also { controller ->
        every { controller.state } returns MutableStateFlow(PlaybackState.Idle)
        every { controller.requestedRenderBackend } returns MutableStateFlow(PlaybackRenderBackend.STANDARD_EXO)
        every { controller.activeRenderBackend } returns MutableStateFlow(PlaybackRenderBackend.STANDARD_EXO)
        every { controller.currentVideoSignalDescriptor } returns MutableStateFlow(null)
        every { controller.currentRenderRuleKey } returns MutableStateFlow(VideoRenderRuleKey.SDR)
        every { controller.currentToneMappingRuleSet } returns
            MutableStateFlow(defaultToneMappingRuleSet(VideoRenderRuleKey.SDR))
        every { controller.sessionRuleOverrides } returns
            MutableStateFlow(emptyMap())
        every { controller.fallbackReason } returns MutableStateFlow(null)
    }

private class RecordingVlcVout : IVLCVout {
    var videoSurfaceView: SurfaceView? = null
    var videoTextureView: TextureView? = null
    var videoSurface: Surface? = null
    var videoSurfaceTexture: SurfaceTexture? = null
    var attachViewsWithListenerCount: Int = 0
    var attachViewsWithoutListenerCount: Int = 0
    var detachViewsCount: Int = 0
    val windowSizes = mutableListOf<Pair<Int, Int>>()
    private var viewsAttached = false

    override fun setVideoView(videoSurfaceView: SurfaceView) {
        this.videoSurfaceView = videoSurfaceView
    }

    override fun setVideoView(videoTextureView: TextureView) {
        this.videoTextureView = videoTextureView
    }

    override fun setVideoSurface(videoSurface: Surface, surfaceHolder: SurfaceHolder?) {
        this.videoSurface = videoSurface
    }

    override fun setVideoSurface(videoSurfaceTexture: SurfaceTexture) {
        this.videoSurfaceTexture = videoSurfaceTexture
    }

    override fun setSubtitlesView(subtitlesSurfaceView: SurfaceView) = Unit

    override fun setSubtitlesView(subtitlesTextureView: TextureView) = Unit

    override fun setSubtitlesSurface(subtitlesSurface: Surface, surfaceHolder: SurfaceHolder?) = Unit

    override fun setSubtitlesSurface(subtitlesSurfaceTexture: SurfaceTexture) = Unit

    override fun attachViews(onNewVideoLayoutListener: IVLCVout.OnNewVideoLayoutListener) {
        attachViewsWithListenerCount += 1
        viewsAttached = true
    }

    override fun attachViews() {
        attachViewsWithoutListenerCount += 1
        viewsAttached = true
    }

    override fun detachViews() {
        detachViewsCount += 1
        viewsAttached = false
    }

    override fun areViewsAttached(): Boolean = viewsAttached

    override fun addCallback(callback: IVLCVout.Callback) = Unit

    override fun removeCallback(callback: IVLCVout.Callback) = Unit

    override fun sendMouseEvent(action: Int, button: Int, x: Int, y: Int) = Unit

    override fun setWindowSize(width: Int, height: Int) {
        windowSizes += width to height
    }
}
