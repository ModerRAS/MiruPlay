package com.miruplay.tv.ui.player

import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDebugCapturePolicyTest {
    private val source = PlaybackSource(
        uri = "/sdcard/test.mp4",
        mediaSourceId = "test",
        startPosition = 0L,
        subtitleTracks = emptyList(),
    )

    @Test
    fun `standard debug capture waits until playback has progressed`() {
        val shouldCapture = shouldScheduleStandardDebugCapture(
            pendingLabel = "hdr-proof",
            playbackState = PlaybackState.Playing(source, 1_500L),
            currentPosition = 1_500L,
        )

        assertFalse(shouldCapture)
    }

    @Test
    fun `standard debug capture starts once playback is playing past the minimum gate`() {
        val shouldCapture = shouldScheduleStandardDebugCapture(
            pendingLabel = "hdr-proof",
            playbackState = PlaybackState.Playing(source, 5_500L),
            currentPosition = 5_500L,
        )

        assertTrue(shouldCapture)
    }

    @Test
    fun `standard debug capture ignores empty labels`() {
        val shouldCapture = shouldScheduleStandardDebugCapture(
            pendingLabel = null,
            playbackState = PlaybackState.Playing(source, 8_000L),
            currentPosition = 8_000L,
        )

        assertFalse(shouldCapture)
    }
}
