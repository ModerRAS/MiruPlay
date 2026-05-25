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
    fun `play request clamps explicit position to episode duration`() {
        val episode = episode(duration = 120_000L)

        assertEquals(
            0L,
            PlayEpisodeRequest(episodeId = episode.id, startPositionMs = -1L)
                .startPositionFor(episode, progress = null),
        )
        assertEquals(
            120_000L,
            PlayEpisodeRequest(episodeId = episode.id, startPositionMs = 150_000L)
                .startPositionFor(episode, progress = null),
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

    @Test
    fun `play request builds shared playback source for WebUI launch`() {
        val episode = episode(duration = 120_000L)
        val progress = progress(episode.id, positionMs = 45_000L)

        val source = PlayEpisodeRequest(episodeId = episode.id)
            .toWebControlPlaybackSource(episode, progress)

        assertEquals("/anime/episode-1.mkv", source.uri)
        assertEquals("anime-1", source.mediaSourceId)
        assertEquals(45_000L, source.startPosition)
        assertEquals(0, source.subtitleTracks.size)
        assertEquals("episode-1", source.episodeId)
    }

    @Test
    fun `play request can use platform playable uri override`() {
        val episode = episode(duration = 120_000L)

        val source = PlayEpisodeRequest(episodeId = episode.id, startPositionMs = 12_000L)
            .toWebControlPlaybackSource(
                episode = episode,
                progress = null,
                playableUri = "http://127.0.0.1/media/episode-1.mkv",
            )

        assertEquals("http://127.0.0.1/media/episode-1.mkv", source.uri)
        assertEquals("anime-1", source.mediaSourceId)
        assertEquals(12_000L, source.startPosition)
        assertEquals("episode-1", source.episodeId)
    }

    @Test
    fun `playback source maps to WebUI navigation payload`() {
        val source = PlayEpisodeRequest(episodeId = "episode-1", startPositionMs = 12_000L)
            .toWebControlPlaybackSource(episode(duration = 120_000L), progress = null)

        val payload = source.toWebPlaybackSource()

        assertEquals(source.uri, payload.uri)
        assertEquals(source.mediaSourceId, payload.mediaSourceId)
        assertEquals(source.startPosition, payload.startPositionMs)
        assertEquals(source.episodeId, payload.episodeId)
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
