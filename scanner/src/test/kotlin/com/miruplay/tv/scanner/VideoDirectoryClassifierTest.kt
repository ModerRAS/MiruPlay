package com.miruplay.tv.scanner

import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.model.FilenameParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `should extract season from dotted s folder suffix`() {
        val result = splitSeriesAndSeason("医馆笑传.S01")
        assertEquals("医馆笑传", result.seriesName)
        assertEquals(1, result.seasonNumber)
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
        val parser = StaticFilenameParser(
            FilenameParseResult(
                title = "葬送的芙莉莲",
                season = 2,
                episode = 3
            )
        )
        val classifier = VideoDirectoryClassifier(
            episodeDetector = DefaultEpisodeDetector(),
            filenameMetadataParser = parser
        )

        val result = classifier.classifyVideo(
            path = "/downloads/raw/weird-upload-name.mkv",
            fileName = "weird-upload-name.mkv"
        )

        assertEquals("葬送的芙莉莲", result.animeName)
        assertEquals(2, result.seasonNumber)
        assertEquals(3, result.episodeNumber)
        assertEquals(listOf(128, 128), parser.maxLengths)
    }

    @Test
    fun `passes full video path to parser so folder title can classify numbered file`() {
        val parser = MappingFilenameParser(
            mapOf(
                "/downloads/raw/葬送的芙莉莲 第2季/03.mkv" to FilenameParseResult(
                    title = "葬送的芙莉莲",
                    season = 2,
                    episode = 3
                )
            )
        )
        val classifier = VideoDirectoryClassifier(
            episodeDetector = DefaultEpisodeDetector(),
            filenameMetadataParser = parser
        )

        val result = classifier.classifyVideo(
            path = "/downloads/raw/葬送的芙莉莲 第2季/03.mkv",
            fileName = "03.mkv"
        )

        assertEquals("葬送的芙莉莲", result.animeName)
        assertEquals(2, result.seasonNumber)
        assertEquals(3, result.episodeNumber)
        assertTrue(parser.requests.contains("/downloads/raw/葬送的芙莉莲 第2季/03.mkv"))
        assertEquals("/downloads/raw/葬送的芙莉莲 第2季/03.mkv", result.diagnostics.pathModelText)
        assertEquals("葬送的芙莉莲", result.diagnostics.pathParsed?.title)
        assertTrue(result.diagnostics.evidence.any { it.source == "PATH_BERT" })
    }

    @Test
    fun `ignores zero episode from path parser and keeps numbered filename episode`() {
        val parser = MappingFilenameParser(
            mapOf(
                "/sdcard/Movies/MiruPlayPathParser-20260526-090430/葬送的芙莉莲 第2季/03.mp4" to FilenameParseResult(
                    title = "/sdcard/ /MiruPlayPathParser",
                    episode = 0
                ),
                "葬送的芙莉莲 第2季" to FilenameParseResult(
                    title = "葬送的芙莉莲",
                    season = 2
                )
            )
        )
        val classifier = VideoDirectoryClassifier(
            episodeDetector = DefaultEpisodeDetector(),
            filenameMetadataParser = parser
        )

        val result = classifier.classifyVideo(
            path = "/sdcard/Movies/MiruPlayPathParser-20260526-090430/葬送的芙莉莲 第2季/03.mp4",
            fileName = "03.mp4"
        )

        assertEquals("葬送的芙莉莲", result.animeName)
        assertEquals(2, result.seasonNumber)
        assertEquals(3, result.episodeNumber)
        assertNull(result.diagnostics.pathParsed?.title)
        assertTrue(result.titleCandidates.none { it.startsWith("/") || it.contains("sdcard") })
    }

    @Test
    fun `keeps release episode when path parser mistakes dotted season folder for episode one`() {
        val classifier = VideoDirectoryClassifier(
            episodeDetector = DefaultEpisodeDetector(),
            filenameMetadataParser = MappingFilenameParser(
                mapOf(
                    "/医馆笑传/医馆笑传.S01/医馆笑传.S01E02.mp4" to FilenameParseResult(
                        title = "医馆笑传.S01",
                        episode = 1
                    )
                )
            )
        )

        val result = classifier.classifyVideo(
            path = "/医馆笑传/医馆笑传.S01/医馆笑传.S01E02.mp4",
            fileName = "医馆笑传.S01E02.mp4",
            rootContext = "医馆笑传"
        )

        assertEquals("医馆笑传", result.animeName)
        assertEquals(1, result.seasonNumber)
        assertEquals(2, result.episodeNumber)
        assertEquals("医馆笑传.S01", result.diagnostics.pathParsed?.title)
    }

    @Test
    fun `combines folder parser title and filename parser episode`() {
        val classifier = VideoDirectoryClassifier(
            episodeDetector = DefaultEpisodeDetector(),
            filenameMetadataParser = MappingFilenameParser(
                mapOf(
                    "03 [1080P]" to FilenameParseResult(episode = 3),
                    "葬送的芙莉莲 第2季" to FilenameParseResult(
                        title = "葬送的芙莉莲",
                        season = 2
                    )
                )
            )
        )

        val result = classifier.classifyVideo(
            path = "/downloads/葬送的芙莉莲 第2季/03 [1080P].mkv",
            fileName = "03 [1080P].mkv"
        )

        assertEquals("葬送的芙莉莲", result.animeName)
        assertEquals(2, result.seasonNumber)
        assertEquals(3, result.episodeNumber)
        assertTrue(result.titleCandidates.contains("葬送的芙莉莲"))
    }

    @Test
    fun `uses episode folder parser when video filename is generic`() {
        val classifier = VideoDirectoryClassifier(
            episodeDetector = DefaultEpisodeDetector(),
            filenameMetadataParser = MappingFilenameParser(
                mapOf(
                    "葬送的芙莉莲" to FilenameParseResult(title = "葬送的芙莉莲"),
                    "Episode 03" to FilenameParseResult(episode = 3)
                )
            )
        )

        val result = classifier.classifyVideo(
            path = "/media/葬送的芙莉莲/Season 2/Episode 03/video.mkv",
            fileName = "video.mkv"
        )

        assertEquals("葬送的芙莉莲", result.animeName)
        assertEquals(2, result.seasonNumber)
        assertEquals(3, result.episodeNumber)
    }

    @Test
    fun `keeps alternate parser title candidates for metadata search`() {
        val classifier = VideoDirectoryClassifier(
            episodeDetector = DefaultEpisodeDetector(),
            filenameMetadataParser = MappingFilenameParser(
                mapOf(
                    "Frieren S2E03" to FilenameParseResult(
                        title = "Frieren",
                        season = 2,
                        episode = 3
                    ),
                    "葬送的芙莉莲 第2季" to FilenameParseResult(
                        title = "葬送的芙莉莲",
                        season = 2
                    )
                )
            )
        )

        val result = classifier.classifyVideo(
            path = "/downloads/葬送的芙莉莲 第2季/Frieren S2E03.mkv",
            fileName = "Frieren S2E03.mkv"
        )

        assertEquals("葬送的芙莉莲", result.animeName)
        assertEquals(2, result.seasonNumber)
        assertEquals(3, result.episodeNumber)
        assertTrue(result.titleCandidates.contains("Frieren"))
    }

    @Test
    fun `ignores drama root and promo folders when path parser leaks full remote path`() {
        val classifier = VideoDirectoryClassifier(
            episodeDetector = DefaultEpisodeDetector(),
            filenameMetadataParser = StaticFilenameParser(
                FilenameParseResult(
                    title = "/dav/115open/影音/电视剧/白日提灯/[片头尾]/片头《初醒》",
                )
            )
        )

        val result = classifier.classifyVideo(
            path = "/白日提灯/[片头尾]/片头《初醒》.mp4",
            fileName = "片头《初醒》.mp4",
            rootContext = "电视剧"
        )

        assertEquals("白日提灯", result.animeName)
        assertEquals("白日提灯", result.diagnostics.pathParsed?.title)
    }

    @Test
    fun `collapses simple drama root prefix from path parser before final title selection`() {
        val classifier = VideoDirectoryClassifier(
            episodeDetector = DefaultEpisodeDetector(),
            filenameMetadataParser = MappingFilenameParser(
                mapOf(
                    "电视剧/逐玉/06.mkv" to FilenameParseResult(
                        title = "电视剧/逐玉",
                        episode = 6,
                    )
                )
            )
        )

        val result = classifier.classifyVideo(
            path = "/逐玉/06.mkv",
            fileName = "06.mkv",
            rootContext = "电视剧",
        )

        assertEquals("逐玉", result.animeName)
        assertTrue(result.titleCandidates.contains("逐玉"))
        assertTrue(result.titleCandidates.none { it.contains("电视剧/逐玉") })
    }

    private class StaticFilenameParser(
        private val result: FilenameParseResult
    ) : FilenameMetadataParser {
        val maxLengths = mutableListOf<Int>()

        override fun parse(filename: String, maxLength: Int): FilenameParseResult {
            maxLengths += maxLength
            return result
        }
    }

    private class MappingFilenameParser(
        private val results: Map<String, FilenameParseResult>
    ) : FilenameMetadataParser {
        val requests = mutableListOf<String>()

        override fun parse(filename: String, maxLength: Int): FilenameParseResult {
            requests += filename
            return results[filename] ?: FilenameParseResult()
        }
    }
}
