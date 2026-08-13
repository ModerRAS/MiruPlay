package com.miruplay.tv.scraper.core

import com.miruplay.tv.repository.BangumiCommentContent
import com.miruplay.tv.repository.BangumiCommentTextStyle
import com.miruplay.tv.repository.BangumiCommentUser
import com.miruplay.tv.repository.BangumiEpisodeComment
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal object BangumiEpisodeCommentHtmlParser {
    fun parse(html: String, baseUrl: String): List<BangumiEpisodeComment> {
        val document = Jsoup.parse(html, baseUrl)
        val comments = document.select(
            "#comment_list > .row_reply, .singleCommentList .commentList > .row_reply",
        ).distinctBy(Element::id).mapNotNull(::parseTopLevelComment)
        val declaredCount = document.selectFirst(".singleCommentList h2.subtitle .tip")
            ?.text()
            ?.trim()
            ?.toIntOrNull()
        if (declaredCount != null && declaredCount > 0 && comments.isEmpty()) {
            throw IllegalStateException("Bangumi episode comment markup is unsupported")
        }
        return comments
    }

    private fun parseTopLevelComment(element: Element): BangumiEpisodeComment? {
        val comment = parseComment(element) ?: return null
        val replies = element.children().firstOrNull { it.hasClass("topic_sub_reply") }
            ?.children()
            ?.filter { it.hasClass("sub_reply_bg") }
            ?.mapNotNull(::parseComment)
            .orEmpty()
        return comment.copy(replies = replies)
    }

    private fun parseComment(element: Element): BangumiEpisodeComment? {
        val id = element.id().removePrefix("post_").toIntOrNull() ?: return null
        val inner = element.children().firstOrNull { it.hasClass("inner") }
        val author = inner?.selectFirst("strong > a") ?: inner?.selectFirst("a.l")
        val username = element.attr("data-item-user").ifBlank {
            author?.attr("href")?.substringAfterLast('/').orEmpty()
        }
        val name = author?.text()?.trim().orEmpty().ifBlank {
            username.ifBlank { "Bangumi 用户" }
        }
        val avatar = element.children().firstOrNull { it.tagName() == "a" && it.hasClass("avatar") }
            ?.selectFirst("[style*=background-image]")
            ?.attr("style")
            ?.substringAfter("url(", "")
            ?.substringBeforeLast(')')
            ?.trim(' ', '\'', '"')
            ?.let { resolveUrl(it, element.baseUri()) }
        val time = element.children().firstOrNull { it.hasClass("post_actions") }
            ?.selectFirst("small")
            ?.text()
            ?.substringAfter(" - ", "")
            ?.trim()
            ?.ifBlank { null }
        val body = inner?.children()?.firstOrNull { it.hasClass("reply_content") }
            ?.selectFirst(".message")
            ?: return null
        return BangumiEpisodeComment(
            id = id,
            user = BangumiCommentUser(name = name, avatarUrl = avatar),
            content = parseContent(body),
            createdAt = time,
        )
    }

    private fun parseContent(root: Element): List<BangumiCommentContent> =
        parseNodes(root.childNodes(), BangumiCommentTextStyle(), root.baseUri()).trimBoundaryWhitespace()

    private fun parseNodes(
        nodes: List<Node>,
        style: BangumiCommentTextStyle,
        baseUrl: String,
    ): List<BangumiCommentContent> {
        val result = mutableListOf<BangumiCommentContent>()
        nodes.forEach { node ->
            when (node) {
                is TextNode -> appendText(result, node.wholeText, style)
                is Element -> when {
                    node.hasClass("text_mask") -> result += BangumiCommentContent.Spoiler(
                        parseNodes(node.childNodes(), style, baseUrl),
                    )
                    node.tagName() == "img" -> {
                        val url = resolveUrl(node.attr("src"), baseUrl)
                        if (url != null) {
                            result += BangumiCommentContent.Image(
                                url = url,
                                description = node.attr("alt").ifBlank { null },
                                inline = node.hasClass("smile"),
                            )
                        } else {
                            appendText(result, node.attr("alt"), style)
                        }
                    }
                    node.tagName() == "br" -> appendText(result, "\n", style)
                    else -> result += parseNodes(
                        node.childNodes(),
                        styleForElement(style, node, baseUrl),
                        baseUrl,
                    )
                }
            }
        }
        return result
    }

    private fun styleForElement(
        style: BangumiCommentTextStyle,
        element: Element,
        baseUrl: String,
    ): BangumiCommentTextStyle = when (element.tagName()) {
        "b", "strong" -> style.copy(bold = true)
        "i", "em" -> style.copy(italic = true)
        "u" -> style.copy(underline = true)
        "s", "del", "strike" -> style.copy(strikethrough = true)
        "a" -> style.copy(linkUrl = resolveUrl(element.attr("href"), baseUrl))
        else -> style
    }

    private fun appendText(
        result: MutableList<BangumiCommentContent>,
        text: String,
        style: BangumiCommentTextStyle,
    ) {
        if (text.isEmpty()) return
        val previous = result.lastOrNull() as? BangumiCommentContent.Text
        if (previous?.style == style) {
            result[result.lastIndex] = previous.copy(value = previous.value + text)
        } else {
            result += BangumiCommentContent.Text(text, style)
        }
    }

    private fun List<BangumiCommentContent>.trimBoundaryWhitespace(): List<BangumiCommentContent> {
        val result = toMutableList()
        val first = result.firstOrNull() as? BangumiCommentContent.Text
        if (first != null) {
            val value = first.value.trimStart()
            if (value.isEmpty()) result.removeAt(0) else result[0] = first.copy(value = value)
        }
        val last = result.lastOrNull() as? BangumiCommentContent.Text
        if (last != null) {
            val value = last.value.trimEnd()
            if (value.isEmpty()) result.removeAt(result.lastIndex) else result[result.lastIndex] = last.copy(value = value)
        }
        return result
    }

    private fun resolveUrl(value: String, baseUrl: String): String? {
        val normalized = when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith('/') -> Jsoup.parse("<a href=\"$value\"></a>", baseUrl).selectFirst("a")?.absUrl("href")
            else -> null
        }
        return normalized?.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }
    }
}
