package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class WebControlPlaybackTest {
    @Test
    fun `play request explicit position wins over saved progress`() {
        val episode = episode(duration = 120_000L)
        val progress = progress(episode.id, positionMs = 45_000L)

        assertEquals(
            12_000L,
            PlayEpisodeRequest(episodeId = episode.id, startPositionMs = 12_000L)
                .startPositionFor(episode, progress),
        )
    }

    @Test
    fun `play request resumes saved partial progress`() {
        val episode = episode(duration = 120_000L)
        val progress = progress(episode.id, positionMs = 45_000L)

        assertEquals(
            45_000L,
            PlayEpisodeRequest(episodeId = episode.id).startPositionFor(episode, progress),
        )
    }

    @Test
    fun `play request resets completed saved progress`() {
        val episode = episode(duration = 100_000L)
        val progress = progress(episode.id, positionMs = 95_000L)

        assertEquals(
            0L,
            PlayEpisodeRequest(episodeId = episode.id).startPositionFor(episode, progress),
        )
    }

    private fun episode(duration: Long): Episode =
        Episode(
            id = "episode-1",
            animeId = "anime-1",
            episodeNumber = 1,
            filePath = "/anime/episode-1.mkv",
            fileName = "episode-1.mkv",
            duration = duration,
        )

    private fun progress(
        episodeId: String,
        positionMs: Long,
    ): ProgressRecord =
        ProgressRecord(
            episodeId = episodeId,
            positionMs = positionMs,
            lastWatched = 1L,
        )
}
