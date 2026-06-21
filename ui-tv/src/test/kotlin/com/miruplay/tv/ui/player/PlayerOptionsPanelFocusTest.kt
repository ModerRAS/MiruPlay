package com.miruplay.tv.ui.player

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.miruplay.tv.model.PlaybackRenderBackend
import com.miruplay.tv.model.ToneMappingProfilePreset
import com.miruplay.tv.model.defaultToneMappingRuleSet
import com.miruplay.tv.model.VideoRenderRuleKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerOptionsPanelFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `picture menu assigns initial focus to first preset chip`() {
        compose.setContent {
            PlayerOptionsPanel(
                menu = PlayerMenu.Picture,
                subtitles = emptyList(),
                audioTracks = emptyList(),
                playbackSpeed = 1.0f,
                signalFormatLabel = "HDR10",
                activeBackend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
                requestedBackend = PlaybackRenderBackend.EXPERIMENTAL_LIBVLC,
                fallbackReason = null,
                currentPicturePreset = ToneMappingProfilePreset.BALANCED,
                currentToneMappingRuleSet = defaultToneMappingRuleSet(VideoRenderRuleKey.HDR10),
                onSelectSubtitle = {},
                onSelectAudioTrack = {},
                onSelectSpeed = {},
                onSelectPicturePreset = {},
                onSavePictureDefault = {},
                onResetPictureSession = {},
                onAdjustTargetSdrNits = {},
                onAdjustContrastRecovery = {},
                onAdjustSaturationRecovery = {},
                onAdjustHighlightCompression = {},
                onSelectBackend = {},
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithText("仅本次播放 直通").assertIsFocused()
                true
            }.getOrDefault(false)
        }

        compose.onNodeWithText("仅本次播放 直通").assertIsFocused()
    }
}
