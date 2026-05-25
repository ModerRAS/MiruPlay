package com.miruplay.tv.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiruPlayInputIntentTest {
    @Test
    fun `activation intent is shared by Android TV and desktop`() {
        assertTrue(MiruPlayInputIntent.Activate.isActivationIntent())
        assertFalse(MiruPlayInputIntent.DirectionLeft.isActivationIntent())
        assertFalse(MiruPlayInputIntent.Back.isActivationIntent())
    }

    @Test
    fun `back intents exclude text editing and directional navigation`() {
        assertTrue(MiruPlayInputIntent.Back.isBackIntent())
        assertTrue(MiruPlayInputIntent.NavigatePrevious.isBackIntent())
        assertTrue(MiruPlayInputIntent.NavigateOut.isBackIntent())
        assertFalse(MiruPlayInputIntent.DirectionLeft.isBackIntent())
        assertFalse(MiruPlayInputIntent.Activate.isBackIntent())
    }

    @Test
    fun `playback toggle accepts activation and media play pause`() {
        assertTrue(MiruPlayInputIntent.Activate.isPlaybackToggleIntent())
        assertTrue(MiruPlayInputIntent.MediaPlayPause.isPlaybackToggleIntent())
        assertFalse(MiruPlayInputIntent.MediaPlay.isPlaybackToggleIntent())
        assertFalse(MiruPlayInputIntent.MediaPause.isPlaybackToggleIntent())
    }

    @Test
    fun `direction intents expose shared navigation deltas`() {
        assertEquals(-1, MiruPlayInputIntent.DirectionLeft.horizontalNavigationDelta())
        assertEquals(1, MiruPlayInputIntent.DirectionRight.horizontalNavigationDelta())
        assertNull(MiruPlayInputIntent.DirectionUp.horizontalNavigationDelta())

        assertEquals(-1, MiruPlayInputIntent.DirectionUp.verticalNavigationDelta())
        assertEquals(1, MiruPlayInputIntent.DirectionDown.verticalNavigationDelta())
        assertNull(MiruPlayInputIntent.DirectionRight.verticalNavigationDelta())

        assertEquals(-1, MiruPlayInputIntent.DirectionLeft.linearNavigationDelta())
        assertEquals(1, MiruPlayInputIntent.DirectionRight.linearNavigationDelta())
        assertEquals(-1, MiruPlayInputIntent.DirectionUp.linearNavigationDelta())
        assertEquals(1, MiruPlayInputIntent.DirectionDown.linearNavigationDelta())
        assertNull(MiruPlayInputIntent.Activate.linearNavigationDelta())
    }

    @Test
    fun `desktop playback stage action maps shared playback intents`() {
        assertEquals(
            MiruPlayPlaybackInputAction.Launch,
            MiruPlayInputIntent.Activate.desktopPlaybackStageAction(isPlayerActive = false),
        )
        assertEquals(
            MiruPlayPlaybackInputAction.TogglePause,
            MiruPlayInputIntent.Activate.desktopPlaybackStageAction(isPlayerActive = true),
        )
        assertEquals(
            MiruPlayPlaybackInputAction.SeekBack,
            MiruPlayInputIntent.DirectionLeft.desktopPlaybackStageAction(isPlayerActive = true),
        )
        assertEquals(
            MiruPlayPlaybackInputAction.SeekForward,
            MiruPlayInputIntent.DirectionRight.desktopPlaybackStageAction(isPlayerActive = true),
        )
        assertEquals(
            MiruPlayPlaybackInputAction.Resume,
            MiruPlayInputIntent.MediaPlay.desktopPlaybackStageAction(isPlayerActive = true),
        )
        assertEquals(
            MiruPlayPlaybackInputAction.Pause,
            MiruPlayInputIntent.MediaPause.desktopPlaybackStageAction(isPlayerActive = true),
        )
        assertEquals(
            MiruPlayPlaybackInputAction.Stop,
            MiruPlayInputIntent.MediaStop.desktopPlaybackStageAction(isPlayerActive = true),
        )
        assertNull(MiruPlayInputIntent.MediaPause.desktopPlaybackStageAction(isPlayerActive = false))
        assertNull(MiruPlayInputIntent.DirectionUp.desktopPlaybackStageAction(isPlayerActive = true))
    }

    @Test
    fun `desktop global playback action keeps text keys available`() {
        assertEquals(
            MiruPlayPlaybackInputAction.Launch,
            MiruPlayInputIntent.MediaPlay.desktopPlaybackGlobalMediaAction(isPlayerActive = false),
        )
        assertEquals(
            MiruPlayPlaybackInputAction.TogglePause,
            MiruPlayInputIntent.MediaPlayPause.desktopPlaybackGlobalMediaAction(isPlayerActive = true),
        )
        assertEquals(
            MiruPlayPlaybackInputAction.Stop,
            MiruPlayInputIntent.MediaStop.desktopPlaybackGlobalMediaAction(isPlayerActive = true),
        )
        assertNull(MiruPlayInputIntent.Activate.desktopPlaybackGlobalMediaAction(isPlayerActive = true))
        assertNull(MiruPlayInputIntent.DirectionLeft.desktopPlaybackGlobalMediaAction(isPlayerActive = true))
    }

    @Test
    fun `TV playback overlay action follows controls visibility`() {
        assertEquals(
            MiruPlayPlaybackInputAction.HideControls,
            MiruPlayInputIntent.Back.tvPlaybackOverlayAction(controlsVisible = true, hasOpenMenu = false),
        )
        assertEquals(
            MiruPlayPlaybackInputAction.CloseMenu,
            MiruPlayInputIntent.Back.tvPlaybackOverlayAction(controlsVisible = true, hasOpenMenu = true),
        )
        assertEquals(
            MiruPlayPlaybackInputAction.NavigateBack,
            MiruPlayInputIntent.Back.tvPlaybackOverlayAction(controlsVisible = false, hasOpenMenu = false),
        )
        assertEquals(
            MiruPlayPlaybackInputAction.ShowControls,
            MiruPlayInputIntent.DirectionUp.tvPlaybackOverlayAction(controlsVisible = false, hasOpenMenu = false),
        )
        assertEquals(
            MiruPlayPlaybackInputAction.TogglePause,
            MiruPlayInputIntent.Activate.tvPlaybackOverlayAction(controlsVisible = false, hasOpenMenu = false),
        )
        assertNull(MiruPlayInputIntent.Activate.tvPlaybackOverlayAction(controlsVisible = true, hasOpenMenu = false))
    }

    @Test
    fun `TV playback actions identify when controls should be refreshed`() {
        assertTrue(MiruPlayPlaybackInputAction.SeekBack.shouldRefreshTvPlaybackControls(controlsVisible = true))
        assertTrue(MiruPlayPlaybackInputAction.SeekForward.shouldRefreshTvPlaybackControls(controlsVisible = false))
        assertTrue(MiruPlayPlaybackInputAction.TogglePause.shouldRefreshTvPlaybackControls(controlsVisible = false))
        assertFalse(MiruPlayPlaybackInputAction.TogglePause.shouldRefreshTvPlaybackControls(controlsVisible = true))
        assertFalse(MiruPlayPlaybackInputAction.NavigateBack.shouldRefreshTvPlaybackControls(controlsVisible = false))
    }
}
