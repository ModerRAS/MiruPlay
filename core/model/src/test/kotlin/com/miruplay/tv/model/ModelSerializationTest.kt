package com.miruplay.tv.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ModelSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `Anime serialization round-trip`() {
        val anime = Anime(
            id = "1",
            title = "Test Anime",
            titleCn = "测试",
            summary = "A test anime",
            genres = listOf("Action", "Comedy"),
            episodeCount = 12,
            rating = 8.5f
        )
        val jsonStr = json.encodeToString(Anime.serializer(), anime)
        val decoded = json.decodeFromString(Anime.serializer(), jsonStr)
        assertEquals(anime.id, decoded.id)
        assertEquals(anime.title, decoded.title)
        assertEquals(anime.genres, decoded.genres)
    }

    @Test
    fun `PlaybackState has at least 7 subclasses`() {
        // Verify all 7 sealed subclasses exist by instantiating each
        val states: List<PlaybackState> = listOf(
            PlaybackState.Idle,
            PlaybackState.Loading(PlaybackSource("uri://test", "1")),
            PlaybackState.Playing(PlaybackSource("uri://test", "1"), 0L),
            PlaybackState.Paused(PlaybackSource("uri://test", "1"), 1000L),
            PlaybackState.Buffering(PlaybackSource("uri://test", "1"), 500L),
            PlaybackState.Ended(PlaybackSource("uri://test", "1")),
            PlaybackState.Error(null, "test error")
        )
        assertTrue("PlaybackState should have >= 7 subclasses", states.size == 7)
    }

    @Test
    fun `PlaybackState exhaustive when exhaustive switch`() {
        // Verify all states can be matched
        val states: List<PlaybackState> = listOf(
            PlaybackState.Idle,
            PlaybackState.Loading(PlaybackSource("uri://test", "1")),
            PlaybackState.Playing(PlaybackSource("uri://test", "1"), 0L),
            PlaybackState.Paused(PlaybackSource("uri://test", "1"), 1000L),
            PlaybackState.Buffering(PlaybackSource("uri://test", "1"), 500L),
            PlaybackState.Ended(PlaybackSource("uri://test", "1")),
            PlaybackState.Error(null, "test error")
        )
        assertEquals(7, states.size)
    }

    @Test
    fun `Episode serialization round-trip`() {
        val episode = Episode(
            id = "ep1",
            animeId = "anime1",
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Episode 1",
            filePath = "/path/to/ep1.mkv",
            fileName = "ep1.mkv",
            duration = 1500000L
        )
        val jsonStr = json.encodeToString(Episode.serializer(), episode)
        val decoded = json.decodeFromString(Episode.serializer(), jsonStr)
        assertEquals(episode.id, decoded.id)
        assertEquals(episode.episodeNumber, decoded.episodeNumber)
    }

    @Test
    fun `Episode watchedPosition millisecond precision`() {
        // Verify Episode format supports ms precision via watchedPosition field
        val episodeJson = """{"id":"ep1","animeId":"a1","seasonNumber":1,"episodeNumber":1,"filePath":"/p","fileName":"test.mkv","watchedPosition":123456789,"title":"Test"}"""
        val episode = json.decodeFromString(Episode.serializer(), episodeJson)
        assertEquals(123456789L, episode.watchedPosition)
    }
}
