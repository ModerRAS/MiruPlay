package com.miruplay.tv.player

import android.app.ActivityManager
import android.content.Context
import android.view.ViewGroup
import androidx.media3.exoplayer.ExoPlayer
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.player.ijk.android.MiruIjkSurfaceView
import `is`.xyz.mpv.MiruMpvSurfaceView
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExoPlaybackControllerLazyInitTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `constructing controller does not initialize exo player provider`() {
        val standardProvider = CountingProvider { mockk<ExoPlayer>(relaxed = true) }

        createController(standardProvider)

        assertEquals(0, standardProvider.getCallCount)
    }

    @Test
    fun `getPlayer initializes the standard exo player`() {
        val standardPlayer = mockk<ExoPlayer>(relaxed = true)
        val standardProvider = CountingProvider { standardPlayer }
        val controller = createController(standardProvider)

        val player = controller.getPlayer()

        assertSame(standardPlayer, player)
        assertEquals(1, standardProvider.getCallCount)
    }

    @Test
    fun `read only queries do not initialize the standard exo player`() = runBlocking {
        val standardProvider = CountingProvider { mockk<ExoPlayer>(relaxed = true) }
        val controller = createController(standardProvider)

        assertEquals(0L, controller.getCurrentPosition())
        assertEquals(0L, controller.getDuration())
        assertFalse(controller.isPlaying())
        assertEquals(0, standardProvider.getCallCount)
    }

    @Test
    fun `ijk backend does not initialize exo player`() = runBlocking {
        val standardProvider = CountingProvider { mockk<ExoPlayer>(relaxed = true) }
        val controller = createController(standardProvider)

        controller.setRequestedRenderBackend(PlaybackRenderBackend.EXPERIMENTAL_IJKPLAYER)

        assertNull(controller.getPlayer())
        assertEquals(0, standardProvider.getCallCount)
    }

    @Test
    fun `refreshing runtime config for standard backend does not initialize exo player`() = runBlocking {
        val standardProvider = CountingProvider { mockk<ExoPlayer>(relaxed = true) }
        val controller = createController(standardProvider)

        controller.setRequestedRenderBackend(PlaybackRenderBackend.STANDARD_EXO)
        controller.setSessionRuleOverride(
            ruleKey = com.miruplay.tv.model.VideoRenderRuleKey.HDR10,
            ruleSet = com.miruplay.tv.model.defaultToneMappingRuleSet(com.miruplay.tv.model.VideoRenderRuleKey.HDR10),
        )

        assertEquals(0, standardProvider.getCallCount)
    }

    @Test
    fun `embedded unbind releases before removal and later stop does not release twice`() = runBlocking {
        val controller = createController(CountingProvider { mockk<ExoPlayer>(relaxed = true) })
        val host = mockk<ViewGroup>(relaxed = true)
        val view = mockk<MiruMpvSurfaceView>(relaxed = true)
        val events = mutableListOf<String>()
        every { view.parent } returns host
        every { view.releaseMpv() } answers { events += "release" }
        every { host.removeView(view) } answers { events += "remove" }
        controller.setPrivateField("embeddedMpvView", view)
        controller.setPrivateField("embeddedMpvHostView", host)
        controller.setPrivateField("embeddedMpvSource", PlaybackSource("test.mp4", "test"))

        controller.unbindVlcVideoHost()
        controller.unbindVlcVideoHost()

        assertEquals(listOf("release", "remove"), events)
        assertNull(controller.privateField("embeddedMpvView"))
        assertNull(controller.privateField("embeddedMpvHostView"))
        assertTrue(controller.privateField("embeddedMpvPendingLoad"))

        controller.stop()

        verify(exactly = 1) { view.releaseMpv() }
        verify(exactly = 1) { host.removeView(view) }
    }

    @Test
    fun `embedded unbind preserves reentrant replacement references`() {
        val controller = createController(CountingProvider { mockk<ExoPlayer>(relaxed = true) })
        val originalHost = mockk<ViewGroup>(relaxed = true)
        val replacementHost = mockk<ViewGroup>(relaxed = true)
        val originalView = mockk<MiruMpvSurfaceView>(relaxed = true)
        val replacementView = mockk<MiruMpvSurfaceView>(relaxed = true)
        every { originalView.parent } returns originalHost
        every { originalView.releaseMpv() } answers {
            controller.setPrivateField("embeddedMpvView", replacementView)
            controller.setPrivateField("embeddedMpvHostView", replacementHost)
            controller.setPrivateField("embeddedMpvPendingLoad", false)
        }
        controller.setPrivateField("embeddedMpvView", originalView)
        controller.setPrivateField("embeddedMpvHostView", originalHost)

        controller.unbindVlcVideoHost()

        assertSame(replacementView, controller.privateField("embeddedMpvView"))
        assertSame(replacementHost, controller.privateField("embeddedMpvHostView"))
        assertFalse(controller.privateField("embeddedMpvPendingLoad"))
        verify(exactly = 1) { originalView.releaseMpv() }
        verify(exactly = 1) { originalHost.removeView(originalView) }
    }

    @Test
    fun `ijk unbind still removes without release and stop releases once`() = runBlocking {
        val controller = createController(CountingProvider { mockk<ExoPlayer>(relaxed = true) })
        val host = mockk<ViewGroup>(relaxed = true)
        val view = mockk<MiruIjkSurfaceView>(relaxed = true)
        every { view.parent } returns host
        controller.setPrivateField("ijkView", view)
        controller.setPrivateField("ijkHostView", host)
        controller.setPrivateField("ijkSource", PlaybackSource("test.mp4", "test"))

        controller.unbindVlcVideoHost()

        verify(exactly = 1) { host.removeView(view) }
        verify(exactly = 0) { view.releasePlayer() }
        assertSame(view, controller.privateField("ijkView"))
        assertNull(controller.privateField("ijkHostView"))
        assertFalse(controller.privateField("ijkPendingLoad"))

        controller.stop()

        verify(exactly = 1) { view.releasePlayer() }
    }

    private fun createController(standardProvider: Provider<ExoPlayer>): ExoPlaybackController {
        val context = mockk<Context>(relaxed = true).apply {
            every { getSystemService(ActivityManager::class.java) } returns null
        }
        return ExoPlaybackController(
            context = context,
            standardExoPlayerProvider = standardProvider,
            dataSourceFactory = mockk<PlaybackDataSourceFactory>(relaxed = true),
            httpRequestResolver = mockk<PlaybackHttpRequestResolver>(relaxed = true),
            playbackPreferencesRepository = mockk<PlaybackPreferencesRepository>(relaxed = true),
            playbackDebugOverrides = PlaybackDebugOverrides(),
            externalMpvLauncher = mockk<AndroidExternalMpvLauncher>(relaxed = true),
            config = PlaybackConfig(),
        )
    }

    private fun ExoPlaybackController.setPrivateField(name: String, value: Any?) {
        javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(this@setPrivateField, value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> ExoPlaybackController.privateField(name: String): T =
        javaClass.getDeclaredField(name).let { field ->
            field.isAccessible = true
            field.get(this) as T
        }
}

private class CountingProvider<T>(
    private val factory: () -> T,
) : Provider<T> {
    var getCallCount: Int = 0
        private set

    override fun get(): T {
        getCallCount += 1
        return factory()
    }
}
