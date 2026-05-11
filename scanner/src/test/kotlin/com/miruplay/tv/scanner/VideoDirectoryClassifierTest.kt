package com.miruplay.tv.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDirectoryClassifierTest {
    private val classifier = VideoDirectoryClassifier(DefaultEpisodeDetector())

    @Test
    fun `classifies flat show season folder`() {
        val result = classifier.classifyVideo(
            path = "/media/异世界悠闲农家 第2季/01 [1080P].mp4",
            fileName = "01 [1080P].mp4"
        )

        assertEquals("异世界悠闲农家", result.animeName)
        assertEquals(2, result.seasonNumber)
        assertEquals(1, result.episodeNumber)
    }

    @Test
    fun `classifies nested season folder`() {
        val result = classifier.classifyVideo(
            path = "/media/异世界悠闲农家/Season 2/01 [1080P].mp4",
            fileName = "01 [1080P].mp4"
        )

        assertEquals("异世界悠闲农家", result.animeName)
        assertEquals(2, result.seasonNumber)
        assertEquals(1, result.episodeNumber)
    }

    @Test
    fun `classifies release filename with season suffix`() {
        val result = classifier.classifyVideo(
            path = "/downloads/[ANi] 异世界悠闲农家 第2季 - 07 [1080P].mp4",
            fileName = "[ANi] 异世界悠闲农家 第2季 - 07 [1080P].mp4"
        )

        assertEquals("异世界悠闲农家", result.animeName)
        assertEquals(2, result.seasonNumber)
        assertEquals(7, result.episodeNumber)
    }
}
