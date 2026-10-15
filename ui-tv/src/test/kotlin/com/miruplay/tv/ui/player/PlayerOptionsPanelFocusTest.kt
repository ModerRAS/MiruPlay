package com.miruplay.tv.ui.player

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.model.ToneMappingProfilePreset
import com.miruplay.tv.model.pictureSessionOverrideLabel
import com.miruplay.tv.model.playbackSubtitleOptionLabel
import com.miruplay.tv.model.toneMappingPresetLabel
import com.miruplay.tv.ui.common.resolve
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

    private val appContext: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `subtitle menu scrolls to and focuses an offscreen selected track`() {
        val selectedIndex = 12
        val subtitles = List(18) { index ->
            SubtitleTrack(
                language = "en",
                title = "Subtitle $index",
                path = "/subtitle-$index.srt",
            )
        }
        val selectedLabel = playbackSubtitleOptionLabel(subtitles[selectedIndex], selectedIndex).resolve(appContext)
        compose.setContent {
            PlayerOptionsPanel(
                menu = PlayerMenu.Subtitles,
                subtitles = subtitles,
                audioTracks = emptyList(),
                selectedSubtitleTrackIndex = selectedIndex,
                selectedAudioTrackIndex = null,
                playbackSpeed = 1.0f,
                currentPicturePreset = ToneMappingProfilePreset.BALANCED,
                onSelectSubtitle = {},
                onSelectAudioTrack = {},
                onSelectSpeed = {},
                onSelectPicturePreset = {},
                onSavePictureDefault = {},
                onResetPictureSession = {},
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithText(selectedLabel).assertIsFocused()
                true
            }.getOrDefault(false)
        }

        compose.onNodeWithText(selectedLabel).assertIsFocused()
    }

    @Test
    fun `picture menu assigns initial focus to selected preset`() {
        val selectedLabel =
            "${pictureSessionOverrideLabel().resolve(appContext)} ${toneMappingPresetLabel(ToneMappingProfilePreset.BALANCED).resolve(appContext)}"
        compose.setContent {
            PlayerOptionsPanel(
                menu = PlayerMenu.Picture,
                subtitles = emptyList(),
                audioTracks = emptyList(),
                selectedSubtitleTrackIndex = null,
                selectedAudioTrackIndex = null,
                playbackSpeed = 1.0f,
                currentPicturePreset = ToneMappingProfilePreset.BALANCED,
                onSelectSubtitle = {},
                onSelectAudioTrack = {},
                onSelectSpeed = {},
                onSelectPicturePreset = {},
                onSavePictureDefault = {},
                onResetPictureSession = {},
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithText(selectedLabel).assertIsFocused()
                true
            }.getOrDefault(false)
        }

        compose.onNodeWithText(selectedLabel).assertIsFocused()
    }
}
