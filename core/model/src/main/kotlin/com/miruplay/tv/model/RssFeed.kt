package com.miruplay.tv.model

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

const val RSS_DEFAULT_FEED_URL = "https://api.ani.rip/ani-download.xml"

/**
 * Generic RSS 2.0 media feed: each `<item>` carries one directly playable file.
 *
 * Supported URL locations, in priority order: `<enclosure url>` (when it is a
 * plain http(s) file link), then `<link>`. Items with no direct http(s) URL
 * (e.g. magnet-only entries) are skipped — this source plays streams, it does
 * not download torrents. `<category>` is used as the series key when present;
 * otherwise items are grouped by parsed series title.
 *
 * The default feed is ANi (`RSS_DEFAULT_FEED_URL`); any feed with the same
 * shape (Mikan-style subject feeds, direct-link shares) works by just
 * changing the source URL.
 */
data class RssMediaItem(
    val guid: String,
    val url: String,
    val title: String,
    /** Opaque per-series key from <category>; blank when the feed omits it. */
    val category: String = "",
    val pubDateMs: Long = 0L,
    val sizeBytes: Long = 0L,
)

data class RssEpisodeRef(
    val seriesTitle: String,
    val seasonNumber: Int = 1,
    val episodeNumber: Int,
)

object RssFeedParser {
    fun parse(xml: String): List<RssMediaItem> {
        if (xml.isBlank()) return emptyList()
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        val nodes = doc.getElementsByTagName("item")
        return (0 until nodes.length).mapNotNull { index ->
            val element = nodes.item(index) as? Element ?: return@mapNotNull null
            val url = element.directFileUrl() ?: return@mapNotNull null
            RssMediaItem(
                guid = element.text("guid").takeIf { it.isNotBlank() } ?: url,
                url = url,
                title = element.text("title").ifBlank { fileNameOf(url) },
                category = element.text("category"),
                pubDateMs = parsePubDateMs(element.text("pubDate")),
                sizeBytes = parseSizeBytes(element.text("size"))
                    .takeIf { it > 0L }
                    ?: element.enclosureLength(),
            )
        }
    }

    private fun Element.directFileUrl(): String? {
        enclosureUrl()?.takeIf { it.isHttpUrl() }?.let { return it }
        return text("link").takeIf { it.isNotBlank() && it.isHttpUrl() }
    }

    private fun Element.enclosureUrl(): String? {
        val nodes = getElementsByTagName("enclosure")
        for (index in 0 until nodes.length) {
            val url = (nodes.item(index) as? Element)?.getAttribute("url")
            if (!url.isNullOrBlank()) return url.trim()
        }
        return null
    }

    private fun Element.enclosureLength(): Long {
        val nodes = getElementsByTagName("enclosure")
        for (index in 0 until nodes.length) {
            val length = (nodes.item(index) as? Element)
                ?.getAttribute("length")?.toLongOrNull()
            if (length != null && length > 0L) return length
        }
        return 0L
    }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://", ignoreCase = true) ||
            startsWith("https://", ignoreCase = true)

    fun parseSizeBytes(raw: String): Long {
        val match = SIZE_REGEX.find(raw.trim().uppercase(Locale.US)) ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val multiplier = when (match.groupValues[2]) {
            "B" -> 1L
            "KB" -> 1024L
            "MB" -> 1024L * 1024L
            "GB" -> 1024L * 1024L * 1024L
            else -> 0L
        }
        return (value * multiplier).toLong().coerceAtLeast(0L)
    }

    fun parsePubDateMs(raw: String): Long {
        if (raw.isBlank()) return 0L
        return runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).parse(raw)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun Element.text(tagName: String): String {
        // Handles both plain tags (<category>) and namespaced ones (<anime:size>).
        val nodes = getElementsByTagName(tagName)
        if (nodes.length > 0) return nodes.item(0)?.textContent?.trim().orEmpty()
        val all = getElementsByTagName("*")
        for (index in 0 until all.length) {
            val node = all.item(index) as? Element ?: continue
            if (node.tagName.substringAfter(':') == tagName.substringAfter(':')) {
                return node.textContent?.trim().orEmpty()
            }
        }
        return ""
    }

    private fun fileNameOf(url: String): String =
        url.substringBefore('?').substringAfterLast('/').ifBlank { url }

    private val SIZE_REGEX = Regex("""([\d.]+)\s*([KMG]?B)""")
}

object RssTitleParser {
    private val episodeSuffix = Regex("""^(.*?)\s+-\s+(\d{1,4})(?:\s*\[.*)?\.[A-Za-z0-9]+$""")
    private val groupPrefix = Regex("""^(?:\[[^\]]+\]\s*)+""")
    private val seasonSuffix = Regex("""\s*(?:[Ss]eason\s*\d+|第[0-9一二三四五六七八九十百]+\s*季)\s*$""")
    private val seasonEnglish = Regex("""[Ss]eason\s*(\d+)""")
    private val seasonTagged = Regex("""[Ss](\d+)\s*E""")
    private val seasonChinese = Regex("""第\s*([0-9一二三四五六七八九十百]+)\s*季""")

    fun parse(fileName: String): RssEpisodeRef? {
        val match = episodeSuffix.find(fileName.trim()) ?: return null
        val rawSeries = match.groupValues[1].trim().replace(groupPrefix, "").trim()
        if (rawSeries.isEmpty()) return null
        val episode = match.groupValues[2].toIntOrNull() ?: return null
        return RssEpisodeRef(
            seriesTitle = rawSeries.replace(seasonSuffix, "").trim().ifBlank { rawSeries },
            seasonNumber = seasonOf(rawSeries),
            episodeNumber = episode,
        )
    }

    fun seasonOf(seriesTitle: String): Int =
        seasonEnglish.find(seriesTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: seasonTagged.find(seriesTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: seasonChinese.find(seriesTitle)?.groupValues?.getOrNull(1)?.let(::parseSeasonNumber)
            ?: 1

    fun parseSeasonNumber(raw: String): Int? {
        raw.trim().toIntOrNull()?.let { return it }
        val text = raw.trim()
        if (text.isEmpty()) return null
        var total = 0
        var current = 0
        for (char in text) {
            when (char) {
                '百' -> {
                    total += (if (current == 0) 1 else current) * 100
                    current = 0
                }
                '十' -> {
                    total += (if (current == 0) 1 else current) * 10
                    current = 0
                }
                else -> {
                    val digit = CN_DIGITS[char] ?: return null
                    current = current * 10 + digit
                }
            }
        }
        return (total + current).takeIf { it > 0 }
    }

    private val CN_DIGITS = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
        '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )
}
