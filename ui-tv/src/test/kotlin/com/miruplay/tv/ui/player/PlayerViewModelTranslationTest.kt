package com.miruplay.tv.ui.player

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import com.miruplay.tv.model.SubtitleFormat
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoRenderRuleKey
import com.miruplay.tv.player.PlaybackController
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.translation.SubtitleTranslationService
import com.miruplay.tv.translation.TranslationPreferencesRepository
import com.miruplay.tv.translation.TranslationProvider
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlayerViewModelTranslationTest {

    @Test
    fun `successful translation injects track replays at position and selects it`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val original = subtitleTrack("original.srt")
            val translated = subtitleTrack("translated.srt")
            val tracks = mutableListOf(original)
            var replayedSource: PlaybackSource? = null
            var selectedTrack: Int? = null
            val controller = mockk<PlaybackController>(relaxed = true)
            every { controller.state } returns MutableStateFlow(PlaybackState.Idle)
            every { controller.currentVideoSignalDescriptor } returns relaxedStateFlow()
            every { controller.currentRenderRuleKey } returns relaxedStateFlow()
            every { controller.currentToneMappingRuleSet } returns relaxedStateFlow()
            every { controller.requestedRenderBackend } returns relaxedStateFlow()
            every { controller.activeRenderBackend } returns relaxedStateFlow()
            every { controller.fallbackReason } returns relaxedStateFlow()
            every { controller.sessionRuleOverrides } returns relaxedStateFlow()
            every { controller.getAvailableSubtitles() } answers { tracks.toList() }
            every { controller.getSelectedSubtitleTrackIndex() } returns 0
            coEvery { controller.getCurrentPosition() } returns 9_123L
            coEvery { controller.getDuration() } returns 60_000L
            coEvery { controller.play(any()) } coAnswers {
                replayedSource = firstArg()
                tracks.clear()
                tracks += replayedSource!!.subtitleTracks
            }
            coEvery { controller.setSubtitleTrack(any()) } coAnswers {
                selectedTrack = firstArg()
            }

            val translationService = mockk<SubtitleTranslationService>()
            coEvery {
                translationService.translateTrack(original, "zh-Hans", TranslationProvider.GOOGLE)
            } returns Result.success(translated)
            val viewModel = PlayerViewModel(
                playbackController = controller,
                progressRepository = mockk(relaxed = true),
                metadataRepository = daggerLazy(mockk(relaxed = true)),
                mediaRepository = daggerLazy(mockk(relaxed = true)),
                mediaSourceFactory = daggerLazy(mockk(relaxed = true)),
                mediaIndexRepository = daggerLazy(mockk(relaxed = true)),
                bangumiSyncEngine = daggerLazy(mockk(relaxed = true)),
                bangumiEpisodeCommentsService = daggerLazy(mockk(relaxed = true)),
                playbackPreferences = mockk<PlaybackPreferencesRepository>(relaxed = true),
                scanPreferences = mockk<ScanPreferencesRepository>(relaxed = true),
                subtitleTranslationService = translationService,
                translationPreferences = TestTranslationPreferences(),
            )
            val source = PlaybackSource(
                uri = "file:///video.mkv",
                mediaSourceId = "source",
                subtitleTracks = listOf(original),
            )
            setPrivateField(viewModel, "activeSource", source)
            setStateFlowValue(viewModel, "_availableSubtitles", listOf(original))
            setStateFlowValue(viewModel, "_selectedSubtitleTrackIndex", 0)
            setStateFlowValue(viewModel, "_currentPosition", 9_123L)

            viewModel.translateSelectedSubtitle(TranslationProvider.GOOGLE, "zh-Hans")
            runCurrent()

            val replay = requireNotNull(replayedSource)
            assertEquals(9_123L, replay.startPosition)
            assertEquals(listOf(original, translated), replay.subtitleTracks)
            assertSame(replay, viewModel.activePlaybackSource.value)
            assertEquals(1, selectedTrack)
            assertEquals(9_123L, viewModel.currentPosition.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun subtitleTrack(path: String) = SubtitleTrack(
        language = "en",
        title = path,
        isExternal = true,
        path = path,
        format = SubtitleFormat.SRT,
    )

    private fun <T> relaxedStateFlow(): StateFlow<T> = mockk(relaxed = true)

    private fun <T> daggerLazy(value: T): Lazy<T> = Lazy { value }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    private fun setStateFlowValue(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name).apply { isAccessible = true }
        val flow = field.get(target) as MutableStateFlow<Any?>
        flow.value = value
    }

    private class TestTranslationPreferences : TranslationPreferencesRepository {
        override var deepSeekApiKey: String = ""
        override var defaultTargetLanguage: String = "zh-Hans"
    }
}
