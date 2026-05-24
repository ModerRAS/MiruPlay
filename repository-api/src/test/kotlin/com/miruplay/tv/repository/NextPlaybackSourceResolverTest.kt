package com.miruplay.tv.repository

import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.PlaybackSource
import com.miruplay.tv.model.ProgressRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextPlaybackSourceResolverTest {
    @Test
    fun `buildNextPlaybackSource uses shared ordering resume and playable uri`() = runBlocking {
        val current = episode(id = "ep1", number = 1)
        val next = episode(id = "ep2", number = 2)
        val episodes = listOf(
            episode(id = "ep3", number = 3),
            next,
            current,
        )

        val source = buildNextPlaybackSource(
            currentEpisodeId = current.id,
            loadCurrentEpisode = { id -> current.takeIf { it.id == id } },
            loadEpisodes = { animeId -> episodes.filter { it.animeId == animeId } },
            loadProgress = { id ->
                ProgressRecord(
                    episodeId = id,
                    positionMs = 30_000L,
                    lastWatched = 1L,
                )
            },
            resolvePlayableUri = { episode -> "https://play.example/${episode.id}.mkv" },
        )

        assertEquals(
            PlaybackSource(
                uri = "https://play.example/ep2.mkv",
                mediaSourceId = "anime-1",
                startPosition = 30_000L,
                episodeId = "ep2",
            ),
            source,
        )
    }

    @Test
    fun `buildNextPlaybackSource resets completed progress`() = runBlocking {
        val current = episode(id = "ep1", number = 1)
        val next = episode(id = "ep2", number = 2, duration = 100_000L)

        val source = buildNextPlaybackSource(
            currentEpisodeId = current.id,
            loadCurrentEpisode = { current },
            loadEpisodes = { listOf(current, next) },
            loadProgress = { id ->
                ProgressRecord(
                    episodeId = id,
                    positionMs = 95_000L,
                    lastWatched = 1L,
                    playCount = 1,
                )
            },
        )

        assertEquals(
            PlaybackSource(
                uri = "D:/Anime/ep2.mkv",
                mediaSourceId = "anime-1",
                startPosition = 0L,
                episodeId = "ep2",
            ),
            source,
        )
    }

    @Test
    fun `buildNextPlaybackSource accepts playback source input`() = runBlocking {
        val current = episode(id = "ep1", number = 1)
        val next = episode(id = "ep2", number = 2)

        val source = buildNextPlaybackSource(
            currentSource = PlaybackSource(
                uri = "D:/Anime/ep1.mkv",
                mediaSourceId = "anime-1",
                episodeId = current.id,
            ),
            loadCurrentEpisode = { current },
            loadEpisodes = { listOf(current, next) },
            loadProgress = { null },
        )

        assertEquals(
            PlaybackSource(
                uri = "D:/Anime/ep2.mkv",
                mediaSourceId = "anime-1",
                startPosition = 0L,
                episodeId = "ep2",
            ),
            source,
        )
    }

    @Test
    fun `buildNextPlaybackSource returns null without episode id`() = runBlocking {
        val source = buildNextPlaybackSource(
            currentEpisodeId = null,
            loadCurrentEpisode = { error("Current episode should not load without an id") },
            loadEpisodes = { emptyList() },
            loadProgress = { null },
        )

        assertNull(source)
    }

    @Test
    fun `buildNextPlaybackSource returns null when current episode or next episode is missing`() = runBlocking {
        assertNull(
            buildNextPlaybackSource(
                currentEpisodeId = "missing",
                loadCurrentEpisode = { null },
                loadEpisodes = { error("Episode list should not load without current episode") },
                loadProgress = { null },
            )
        )

        val current = episode(id = "ep1", number = 1)

        assertNull(
            buildNextPlaybackSource(
                currentEpisodeId = current.id,
                loadCurrentEpisode = { current },
                loadEpisodes = { listOf(current) },
                loadProgress = { error("Progress should not load without a next episode") },
            )
        )
    }

    private fun episode(
        id: String,
        number: Int,
        duration: Long = 0L,
    ): Episode =
        Episode(
            id = id,
            animeId = "anime-1",
            episodeNumber = number,
            filePath = "D:/Anime/$id.mkv",
            fileName = "$id.mkv",
            duration = duration,
        )
}
