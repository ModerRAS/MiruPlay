package com.miruplay.tv.scanner

import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.model.FilenameParseResult
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

    @Test
    fun `uses nearest real show folder instead of generic episode container`() {
        val result = classifier.classifyVideo(
            path = "/media/我的英雄學院 FINAL SEASON/单集/Episode 01/[ANi] 我的英雄學院 FINAL SEASON - 01 [1080P].mp4",
            fileName = "[ANi] 我的英雄學院 FINAL SEASON - 01 [1080P].mp4"
        )

        assertEquals("我的英雄學院 FINAL SEASON", result.animeName)
        assertEquals(1, result.seasonNumber)
        assertEquals(1, result.episodeNumber)
    }

    @Test
    fun `classifies bracket title episode release folder`() {
        val result = classifier.classifyVideo(
            path = "/downloads/Ani/[Moezakura][Youkoso Jitsuryoku Shijou Shugi no Kyoushitsu e S4][01][HEVC][x265 10bit][1080p][JPSC]/video.mkv",
            fileName = "video.mkv"
        )

        assertEquals("Youkoso Jitsuryoku Shijou Shugi no Kyoushitsu e", result.animeName)
        assertEquals(4, result.seasonNumber)
        assertEquals(1, result.episodeNumber)
    }

    @Test
    fun `classifies bracket title with sxe release folder`() {
        val result = classifier.classifyVideo(
            path = "/downloads/Ani/[Comicat&kisssub][Sousou no Frieren S2][1080P][BIG5][MP4]/Sousou no Frieren S2E03.mp4",
            fileName = "Sousou no Frieren S2E03.mp4"
        )

        assertEquals("Sousou no Frieren", result.animeName)
        assertEquals(2, result.seasonNumber)
        assertEquals(3, result.episodeNumber)
    }

    @Test
    fun `does not treat library root as show root when writing nfo`() {
        val root = classifier.showRootForVideo("/media/动漫/单集/Episode 01/video.mkv")
        assertNull(root)
    }

    @Test
    fun `uses filename parser when release heuristics cannot identify show`() {
        val classifier = VideoDirectoryClassifier(
            episodeDetector = DefaultEpisodeDetector(),
            filenameMetadataParser = StaticFilenameParser(
                FilenameParseResult(
                    title = "葬送的芙莉莲",
                    season = 2,
                    episode = 3
                )
            )
        )

        val result = classifier.classifyVideo(
            path = "/downloads/raw/weird-upload-name.mkv",
            fileName = "weird-upload-name.mkv"
        )

        assertEquals("葬送的芙莉莲", result.animeName)
        assertEquals(2, result.seasonNumber)
        assertEquals(3, result.episodeNumber)
    }

    private class StaticFilenameParser(
        private val result: FilenameParseResult
    ) : FilenameMetadataParser {
        override fun parse(filename: String, maxLength: Int): FilenameParseResult = result
    }
}
