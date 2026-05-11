package com.miruplay.tv.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `should extract season from Chinese folder with trailing text`() {
        val result = splitSeriesAndSeason("歡迎來到實力至上主義的教室 第四季 2年級篇 第一學期")
        assertEquals("歡迎來到實力至上主義的教室", result.seriesName)
        assertEquals(4, result.seasonNumber)
    }

    @Test
    fun `should extract season from parenthesized Chinese folder`() {
        val result = splitSeriesAndSeason("一拳超人(第三季)")
        assertEquals("一拳超人", result.seriesName)
        assertEquals(3, result.seasonNumber)
    }

    @Test
    fun `should still match season at end`() {
        val result = splitSeriesAndSeason("Re：從零開始的異世界生活 第四季")
        assertEquals("Re：從零開始的異世界生活", result.seriesName)
        assertEquals(4, result.seasonNumber)
    }

    @Test
    fun `should not false-match 第二 that is not a season marker`() {
        val result = splitSeriesAndSeason("我和班上第二可愛的女生成為朋友")
        assertNull(result.seasonNumber)
    }
}
