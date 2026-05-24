package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.ProgressRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WebControlProgressTest {
    @Test
    fun `episode with progress maps progress fields`() {
        val episode = episode("episode-1")
        val dto = episode.toWebControlEpisodeWithProgress(
            ProgressRecord(
                episodeId = "episode-1",
                positionMs = 12_000L,
                lastWatched = 34L,
                playCount = 2,
            ),
        )

        assertSame(episode, dto.episode)
        assertEquals(12_000L, dto.progressMs)
        assertEquals(34L, dto.lastWatched)
        assertEquals(2, dto.playCount)
    }

    @Test
    fun `episode without progress maps zero progress fields`() {
        val episode = episode("episode-1")
        val dto = episode.toWebControlEpisodeWithProgress(null)

        assertSame(episode, dto.episode)
        assertEquals(0L, dto.progressMs)
        assertEquals(0L, dto.lastWatched)
        assertEquals(0, dto.playCount)
    }

    @Test
    fun `continue watching keeps progress episode id and optional metadata`() {
        val episode = episode("episode-1")
        val anime = Anime(id = "anime-1", title = "Frieren")
        val dto = ProgressRecord(
            episodeId = "episode-1",
            positionMs = 45_000L,
            lastWatched = 67L,
            playCount = 3,
        ).toWebControlContinueWatching(episode, anime)

        assertEquals("episode-1", dto.progressEpisodeId)
        assertEquals(45_000L, dto.positionMs)
        assertEquals(67L, dto.lastWatched)
        assertEquals(3, dto.playCount)
        assertSame(episode, dto.episode)
        assertSame(anime, dto.anime)
    }

    private fun episode(id: String): Episode =
        Episode(
            id = id,
            animeId = "anime-1",
            episodeNumber = 1,
            filePath = "D:/Anime/$id.mkv",
            fileName = "$id.mkv",
        )
}
