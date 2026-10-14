package com.miruplay.tv.ui.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import com.miruplay.tv.player.PlaybackHealthSnapshot
import com.miruplay.tv.player.UiJankSnapshot
import com.miruplay.tv.player.VideoHealthSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackHealthHudTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `hud hides without snapshots`() {
        compose.setContent {
            PlaybackHealthHudContent(health = null, libass = null)
        }

        assertTrue(
            compose.onAllNodesWithText("drop=", substring = true).fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            compose.onAllNodesWithText("SUB upd=", substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `hud shows video ui and subtitle lines`() {
        val health = PlaybackHealthSnapshot(
            backend = "STANDARD_EXO",
            video = VideoHealthSnapshot(
                decoderName = "c2.test.hevc.decoder",
                hardwareAccelerated = true,
                width = 1920,
                height = 1080,
                droppedFramesTotal = 5,
                renderedFrames = 900,
                audioUnderruns = 1,
            ),
            ui = UiJankSnapshot(running = true, totalFrames = 300, jankFrames = 3, maxDeltaMs = 120),
        )
        val libass = LibassHudNumbers(updated = 42, skipped = 2, slow = 7, commitP95Ms = 35)

        compose.setContent {
            PlaybackHealthHudContent(health = health, libass = libass)
        }

        compose.onNodeWithText("c2.test.hevc.decoder", substring = true).assertExists()
        compose.onNodeWithText("drop=5/900", substring = true).assertExists()
        compose.onNodeWithText("jank=3/300", substring = true).assertExists()
        compose.onNodeWithText("SUB upd=42 skip=2 slow=7 p95=35ms", substring = true).assertExists()
    }

    @Test
    fun `hud shows subtitle line alone`() {
        compose.setContent {
            PlaybackHealthHudContent(
                health = null,
                libass = LibassHudNumbers(updated = 9, skipped = 0, slow = 0, commitP95Ms = 2),
            )
        }

        compose.onNodeWithText("SUB upd=9", substring = true).assertExists()
        assertTrue(
            compose.onAllNodesWithText("drop=", substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }
}
