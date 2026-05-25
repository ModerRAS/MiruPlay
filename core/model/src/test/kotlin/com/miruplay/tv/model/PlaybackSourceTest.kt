package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceTest {
    @Test
    fun `playbackSourceFromInputs trims media path parses seconds and subtitles`() {
        val source = playbackSourceFromInputs(
            mediaPath = " D:/Anime/Episode 01.mkv ",
            subtitlePath = "D:/Anime/Episode 01.ass",
            startSeconds = "12.345",
            mediaSourceId = "library",
            episodeId = "episode-1",
        )

        assertEquals("D:/Anime/Episode 01.mkv", source.uri)
        assertEquals("library", source.mediaSourceId)
        assertEquals("episode-1", source.episodeId)
        assertEquals(12_345L, source.startPosition)
        assertEquals(listOf("D:/Anime/Episode 01.ass"), source.subtitleTracks.map { it.path })
    }

    @Test
    fun `playbackSourceFromInputs defaults episode id to media uri`() {
        val source = playbackSourceFromInputs(
            mediaPath = "video.mkv",
            subtitlePath = "",
            startSeconds = "",
            mediaSourceId = "desktop",
        )

        assertEquals("video.mkv", source.episodeId)
    }

    @Test
    fun `playbackSourceFromInputs clamps invalid or negative starts to zero`() {
        assertEquals(
            0L,
            playbackSourceFromInputs("video.mkv", "", "abc", mediaSourceId = "desktop").startPosition,
        )
        assertEquals(
            0L,
            playbackSourceFromInputs("video.mkv", "", "-3", mediaSourceId = "desktop").startPosition,
        )
    }

    @Test
    fun `playbackSourceFromInputs requires media path`() {
        val error = runCatching {
            playbackSourceFromInputs(
                mediaPath = "   ",
                subtitlePath = "",
                startSeconds = "",
                mediaSourceId = "desktop",
                blankMediaMessage = "Choose media first.",
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Choose media first.", error?.message)
    }

    @Test
    fun `episode playback queue sorts by season episode and path`() {
        val episodes = listOf(
            episode(id = "s2e1", season = 2, number = 1, path = "S02E01.mkv"),
            episode(id = "s1e2b", season = 1, number = 2, path = "S01E02-B.mkv"),
            episode(id = "s1e1", season = 1, number = 1, path = "S01E01.mkv"),
            episode(id = "s1e2a", season = 1, number = 2, path = "S01E02-A.mkv"),
        )

        assertEquals(
            listOf("s1e1", "s1e2a", "s1e2b", "s2e1"),
            episodes.sortedForPlaybackQueue().map { it.id },
        )
        assertEquals("s1e2a", episodes.nextEpisodeAfter("s1e1")?.id)
        assertEquals(null, episodes.nextEpisodeAfter("s2e1"))
        assertEquals(null, episodes.nextEpisodeAfter("missing"))
    }

    @Test
    fun `episode playback source resumes from progress unless already completed`() {
        val source = episode(id = "ep1", duration = 100_000L).toPlaybackSource(
            playableUri = "https://media.example.test/ep1.mkv",
            progress = ProgressRecord(
                episodeId = "ep1",
                positionMs = 30_000L,
                lastWatched = 123L,
            ),
        )
        val completed = episode(id = "ep1", duration = 100_000L).toPlaybackSource(
            playableUri = "https://media.example.test/ep1.mkv",
            progress = ProgressRecord(
                episodeId = "ep1",
                positionMs = 95_000L,
                lastWatched = 456L,
            ),
        )

        assertEquals("https://media.example.test/ep1.mkv", source.uri)
        assertEquals("anime", source.mediaSourceId)
        assertEquals("ep1", source.episodeId)
        assertEquals(30_000L, source.startPosition)
        assertEquals(0L, completed.startPosition)
    }

    private fun episode(
        id: String,
        season: Int = 1,
        number: Int = 1,
        path: String = "$id.mkv",
        duration: Long = 0L,
    ): Episode =
        Episode(
            id = id,
            animeId = "anime",
            seasonNumber = season,
            episodeNumber = number,
            filePath = path,
            fileName = path.substringAfterLast('/'),
            duration = duration,
        )
}
