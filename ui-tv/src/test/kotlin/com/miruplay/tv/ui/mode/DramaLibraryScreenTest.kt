package com.miruplay.tv.ui.mode

import com.miruplay.tv.model.ProgressRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class DramaLibraryScreenTest {
    @Test
    fun `continue watching subtitle keeps current text when progress is missing`() {
        assertEquals("继续观看 01", dramaContinueWatchingSubtitle(1, null))
    }

    @Test
    fun `continue watching subtitle appends playback position when progress exists`() {
        val progress = ProgressRecord(
            episodeId = "episode-1",
            positionMs = 30_000L,
            lastWatched = 123L,
        )

        assertEquals("继续观看 01 · 看到 00:30", dramaContinueWatchingSubtitle(1, progress))
    }
}
