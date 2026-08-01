package com.miruplay.tv.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.miruplay.tv.model.EpisodeVersionSelectionPolicy
import com.miruplay.tv.model.FallbackBackendPolicy
import com.miruplay.tv.model.FormatAwareToneMappingPreferences
import com.miruplay.tv.model.PeakDetectionStrategy
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.SubtitleLanguagePreference
import com.miruplay.tv.model.ToneMappingCurvePreset
import com.miruplay.tv.model.ToneMappingRuleSet
import com.miruplay.tv.model.VideoRenderRuleKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackPreferencesManagerTest {
    private lateinit var manager: PlaybackPreferencesManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("miruplay_playback_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        manager = PlaybackPreferencesManager(context)
    }

    @Test
    fun `manager returns default format-aware tone mapping preferences when unset`() = runBlocking {
        val preferences = manager.getFormatAwareToneMappingPreferences()

        assertEquals(PlaybackRenderBackend.STANDARD_EXO, preferences.defaultBackend)
        assertTrue(preferences.rules.containsKey(VideoRenderRuleKey.SDR))
        assertTrue(preferences.rules.containsKey(VideoRenderRuleKey.HDR10))
        assertTrue(preferences.rules.containsKey(VideoRenderRuleKey.HDR10_PLUS))
        assertTrue(preferences.rules.containsKey(VideoRenderRuleKey.DOLBY_VISION))
    }

    @Test
    fun `manager persists episode version selection policy`() = runBlocking {
        assertEquals(EpisodeVersionSelectionPolicy.AUTO_NEAREST, manager.getEpisodeVersionSelectionPolicy())

        manager.setEpisodeVersionSelectionPolicy(EpisodeVersionSelectionPolicy.MANUAL)

        assertEquals(EpisodeVersionSelectionPolicy.MANUAL, manager.getEpisodeVersionSelectionPolicy())
    }

    @Test
    fun `manager persists preferred subtitle language`() = runBlocking {
        assertEquals(SubtitleLanguagePreference.AUTO, manager.getPreferredSubtitleLanguage())

        manager.setPreferredSubtitleLanguage(SubtitleLanguagePreference.CHINESE_SIMPLIFIED)

        assertEquals(
            SubtitleLanguagePreference.CHINESE_SIMPLIFIED,
            manager.getPreferredSubtitleLanguage(),
        )
    }

    @Test
    fun `manager persists subtitle background transparency`() = runBlocking {
        assertEquals(false, manager.getSubtitleBackgroundTransparent())

        manager.setSubtitleBackgroundTransparent(true)

        assertEquals(true, manager.getSubtitleBackgroundTransparent())
    }

    @Test
    fun `manager persists and reloads customized format-aware tone mapping preferences`() = runBlocking {
        val updated = FormatAwareToneMappingPreferences(
            defaultBackend = PlaybackRenderBackend.EXPERIMENTAL_GL,
            rules = FormatAwareToneMappingPreferences().rules + (
                VideoRenderRuleKey.HDR10 to ToneMappingRuleSet(
                    ruleKey = VideoRenderRuleKey.HDR10,
                    enabled = true,
                    curvePreset = ToneMappingCurvePreset.REINHARD,
                    targetSdrNits = 140,
                    peakDetectionStrategy = PeakDetectionStrategy.STATIC_METADATA,
                    saturationRecovery = 12,
                    contrastRecovery = 9,
                    highlightCompression = 22,
                    fallbackBackendPolicy = FallbackBackendPolicy.KEEP_CURRENT,
                )
            )
        )

        manager.setFormatAwareToneMappingPreferences(updated)

        val restored = manager.getFormatAwareToneMappingPreferences()

        assertEquals(PlaybackRenderBackend.EXPERIMENTAL_GL, restored.defaultBackend)
        assertEquals(
            ToneMappingCurvePreset.REINHARD,
            restored.rules.getValue(VideoRenderRuleKey.HDR10).curvePreset,
        )
        assertEquals(
            PeakDetectionStrategy.STATIC_METADATA,
            restored.rules.getValue(VideoRenderRuleKey.HDR10).peakDetectionStrategy,
        )
        assertEquals(140, restored.rules.getValue(VideoRenderRuleKey.HDR10).targetSdrNits)
    }
}
