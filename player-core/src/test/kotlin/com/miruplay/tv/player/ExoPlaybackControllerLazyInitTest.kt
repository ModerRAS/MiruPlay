package com.miruplay.tv.player

import android.app.ActivityManager
import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
