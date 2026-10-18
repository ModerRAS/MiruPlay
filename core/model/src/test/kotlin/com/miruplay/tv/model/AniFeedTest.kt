package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AniFeedTest {
    @Test
    fun `parse reads guid link category pubDate and size`() {
        val items = AniRssParser.parse(SAMPLE_XML)

        assertEquals(2, items.size)
        val first = items[0]
        assertEquals("https://resources.ani.rip/2026-10/ep01.mp4?d=mp4", first.url)
        assertEquals("2835ee8b-7d70-5ab7-8097-e2cb6ef58fd9", first.category)
        assertTrue(first.pubDateMs > 0L)
        assertEquals(479408947L, first.sizeBytes)
        assertTrue(first.title.contains("野生的大魔王出現了"))
    }

    @Test
    fun `parse falls back to link when guid is missing`() {
        val items = AniRssParser.parse(SAMPLE_XML)
        assertEquals("https://resources.ani.rip/2026-10/ep02.mp4?d=mp4", items[1].guid)
        assertEquals("", items[1].category)
    }

    @Test
    fun `parse returns empty list for blank input`() {
        assertTrue(AniRssParser.parse("").isEmpty())
        assertTrue(AniRssParser.parse("<rss></rss>").isEmpty())
    }

    @Test
    fun `parseSizeBytes handles common units`() {
        assertEquals(479408947L, AniRssParser.parseSizeBytes("457.2 MB"))
        assertEquals(1024L * 1024 * 1024, AniRssParser.parseSizeBytes("1.0 GB"))
        assertEquals(900L * 1024, AniRssParser.parseSizeBytes("900 KB"))
        assertEquals(0L, AniRssParser.parseSizeBytes(""))
        assertEquals(0L, AniRssParser.parseSizeBytes("unknown"))
    }

    @Test
    fun `title parser splits series season and episode`() {
        val plain = AniTitleParser.parse("[ANi] 野生的大魔王出現了！ - 16 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4")!!
        assertEquals("野生的大魔王出現了！", plain.seriesTitle)
        assertEquals(1, plain.seasonNumber)
        assertEquals(16, plain.episodeNumber)

        val season = AniTitleParser.parse("[ANi] 佐佐木與文鳥小嗶 Season 2 - 02 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4")!!
        assertEquals("佐佐木與文鳥小嗶", season.seriesTitle)
        assertEquals(2, season.seasonNumber)
        assertEquals(2, season.episodeNumber)

        val chinese = AniTitleParser.parse("[ANi] 夜櫻家大作戰 第二季 - 41 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4")!!
        assertEquals("夜櫻家大作戰", chinese.seriesTitle)
        assertEquals(2, chinese.seasonNumber)
        assertEquals(41, chinese.episodeNumber)

        val absolute = AniTitleParser.parse("[ANi] 黑色五葉草 Season 2 - 173 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4")!!
        assertEquals(173, absolute.episodeNumber)
        assertEquals(2, absolute.seasonNumber)
    }

    @Test
    fun `title parser returns null for non matching names`() {
        assertNull(AniTitleParser.parse("Episode 01.mkv"))
        assertNull(AniTitleParser.parse("[ANi] no episode here.mp4"))
    }

    @Test
    fun `parseSeasonNumber handles arabic and chinese numerals`() {
        assertEquals(2, AniTitleParser.parseSeasonNumber("2"))
        assertEquals(2, AniTitleParser.parseSeasonNumber("二"))
        assertEquals(10, AniTitleParser.parseSeasonNumber("十"))
        assertEquals(12, AniTitleParser.parseSeasonNumber("十二"))
        assertEquals(20, AniTitleParser.parseSeasonNumber("二十"))
        assertEquals(23, AniTitleParser.parseSeasonNumber("二十三"))
        assertNull(AniTitleParser.parseSeasonNumber(""))
        assertNull(AniTitleParser.parseSeasonNumber("abc"))
    }

    companion object {
        private const val SAMPLE_XML = """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<rss xmlns:anime="https://resources.ani.rip" version="2.0"><channel>""" +
            """<item><title><![CDATA[[ANi] 野生的大魔王出現了！ - 16 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4]]></title>""" +
            """<link>https://resources.ani.rip/2026-10/ep01.mp4?d=mp4</link>""" +
            """<guid isPermaLink="false">https://resources.ani.rip/2026-10/ep01.mp4?d=mp4</guid>""" +
            """<category><![CDATA[2835ee8b-7d70-5ab7-8097-e2cb6ef58fd9]]></category>""" +
            """<pubDate>Sat, 17 Oct 2026 13:31:10 GMT</pubDate>""" +
            """<anime:size>457.2 MB</anime:size></item>""" +
            """<item><title><![CDATA[[ANi] 小狸貓和小狐狸 - 03 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4]]></title>""" +
            """<link>https://resources.ani.rip/2026-10/ep02.mp4?d=mp4</link>""" +
            """<pubDate>Sat, 17 Oct 2026 22:30:37 GMT</pubDate>""" +
            """<anime:size>14.0 MB</anime:size></item>""" +
            """</channel></rss>"""
    }
}
