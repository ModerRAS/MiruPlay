package com.miruplay.tv.ui.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.miruplay.tv.model.playbackAudioTrackCountLabel
import com.miruplay.tv.player.AudioTrack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class PlayerTimelineFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `controls focus timeline and down follows the nearest enabled action`() {
        var controlsVisible by mutableStateOf(false)

        compose.setContent {
            val timelineFocusRequester = remember { FocusRequester() }
            val transportFocusRequester = remember { FocusRequester() }
            val pictureFocusRequester = remember { FocusRequester() }
            val speedFocusRequester = remember { FocusRequester() }
            val subtitlesFocusRequester = remember { FocusRequester() }
            val audioFocusRequester = remember { FocusRequester() }

            LaunchedEffect(controlsVisible) {
                if (controlsVisible) {
                    timelineFocusRequester.requestFocus()
                }
            }

            Box(
                modifier = Modifier
                    .size(width = 960.dp, height = 540.dp)
                    .focusable(),
            ) {
                if (controlsVisible) {
                    PlayerBottomBar(
                        currentPosition = 12_000L,
                        duration = 60_000L,
                        subtitles = emptyList(),
                        audioTracks = listOf(AudioTrack(index = 0, language = "jpn")),
                        selectedSubtitleTrackIndex = null,
                        selectedAudioTrackIndex = null,
                        playbackSpeed = 1.0f,
                        signalFormatLabel = "",
                        openMenu = null,
                        timelineFocusRequester = timelineFocusRequester,
                        transportFocusRequester = transportFocusRequester,
                        pictureFocusRequester = pictureFocusRequester,
                        speedFocusRequester = speedFocusRequester,
                        subtitlesFocusRequester = subtitlesFocusRequester,
                        audioFocusRequester = audioFocusRequester,
                        onSkipBackward = { error("Timeline seek was not expected") },
                        onSkipForward = { error("Timeline seek was not expected") },
                        onOpenMenu = {},
                    )
                }
            }
        }

        compose.runOnIdle {
            controlsVisible = true
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithTag(PLAYER_TIMELINE_TEST_TAG).assertIsFocused()
                true
            }.getOrDefault(false)
        }
        compose.onNodeWithTag(PLAYER_TIMELINE_TEST_TAG).assertIsFocused()
        compose.onNodeWithTag(PLAYER_TIMELINE_TEST_TAG).performKeyInput {
            pressKey(Key.DirectionDown)
        }
        compose.onNodeWithText(playbackAudioTrackCountLabel(1)).assertIsFocused()
        compose.onNodeWithText(playbackAudioTrackCountLabel(1)).performKeyInput {
            pressKey(Key.DirectionUp)
        }
        compose.onNodeWithTag(PLAYER_TIMELINE_TEST_TAG).assertIsFocused()
    }

}
