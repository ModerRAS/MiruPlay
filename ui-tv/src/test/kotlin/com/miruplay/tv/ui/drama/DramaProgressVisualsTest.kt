package com.miruplay.tv.ui.drama

import com.miruplay.tv.model.DramaEpisode
import com.miruplay.tv.model.ProgressRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class DramaProgressVisualsTest {
    @Test
    fun `returns exact fraction when duration-based progress is available`() {
        val progress = ProgressRecord(
            episodeId = "episode-1",
            positionMs = 25 * 60_000L,
            lastWatched = 123L,
        )

        assertEquals(0.42f, dramaProgressIndicatorFraction(0.42f, progress), 0.0001f)
    }

    @Test
    fun `returns coarse bucket when only watched position is available`() {
        val progress = ProgressRecord(
            episodeId = "episode-1",
            positionMs = 12 * 60_000L,
            lastWatched = 123L,
        )

        assertEquals(0.52f, dramaProgressIndicatorFraction(0f, progress), 0.0001f)
    }

    @Test
    fun `returns zero when there is no progress record`() {
        assertEquals(0f, dramaProgressIndicatorFraction(0f, null), 0.0001f)
    }

    @Test
    fun `drama episode helper falls back to watched-position bucket when duration is unavailable`() {
        val episode = DramaEpisode(
            id = "episode-1",
            seriesId = "series-1",
            episodeNumber = 1,
            filePath = "/drama/series-1/s01e01.mkv",
            fileName = "s01e01.mkv",
        )
        val progress = ProgressRecord(
            episodeId = "episode-1",
            positionMs = 4 * 60_000L,
            lastWatched = 123L,
        )

        assertEquals(0.34f, dramaEpisodeProgressIndicatorFraction(episode, progress), 0.0001f)
    }
}
