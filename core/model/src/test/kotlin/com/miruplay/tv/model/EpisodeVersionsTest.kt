package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeVersionsTest {
    @Test
    fun `duplicate season episode entries become one logical episode`() {
        val grouped = listOf(
            episode(id = "1:/Anime/WEB/01.mkv", path = "/Anime/WEB/01.mkv"),
            episode(id = "1:/Anime/BD/01.mkv", path = "/Anime/BD/01.mkv"),
            episode(id = "1:/Anime/WEB/02.mkv", path = "/Anime/WEB/02.mkv", number = 2),
        ).groupEpisodeVersions()

        assertEquals(2, grouped.size)
        assertEquals(2, grouped.first().versions.size)
        assertEquals("anime#S1E1", grouped.first().progressId)
    }

    @Test
    fun `nearest version keeps the current directory`() {
        val versions = listOf(
            EpisodeVersion("1:/Anime/BD/02.mkv", "/Anime/BD/02.mkv", "02.mkv"),
            EpisodeVersion("1:/Anime/WEB/02.mkv", "/Anime/WEB/02.mkv", "02.mkv"),
        )

        assertEquals(
            "/Anime/WEB/02.mkv",
            versions.nearestTo("/Anime/WEB/01.mkv")?.filePath,
        )
    }

    private fun episode(
        id: String,
        path: String,
        number: Int = 1,
    ) = Episode(
        id = id,
        animeId = "anime",
        episodeNumber = number,
        filePath = path,
        fileName = path.substringAfterLast('/'),
    )
}
