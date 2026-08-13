package com.miruplay.tv.scraper.core

import com.miruplay.tv.repository.BangumiCommentContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BangumiEpisodeCommentHtmlParserTest {
    @Test
    fun `parser preserves comments replies rich content images and masks`() {
        val comments = BangumiEpisodeCommentHtmlParser.parse(
            html = """
                <div id="comment_list" class="commentList">
                  <div id="post_205402" class="light_odd row row_reply clearit"
                       name="floor-1" data-item-user="chaucerling">
                    <div class="post_actions re_info">
                      <div class="action"><small><a class="floor-anchor">#1</a> - 2013-12-03 21:45</small></div>
                    </div>
                    <a href="/user/chaucerling" class="avatar">
                      <span style="background-image:url('//lain.bgm.tv/pic/user/l/1.jpg')"></span>
                    </a>
                    <div class="inner">
                      <strong><a href="/user/chaucerling" class="l">chaucer</a></strong>
                      <div class="reply_content"><div class="message clearit">
                        父评论<strong>粗体</strong><span class="text_mask">剧透<img src="/img/secret.jpg"></span>
                        <img src="/img/smiles/tv/15.gif" class="smile" alt="(bgm38)">
                        <a href="https://example.com">链接</a>
                      </div></div>
                    </div>
                    <div class="topic_sub_reply" id="topic_reply_205402">
                      <div id="post_242518" class="sub_reply_bg clearit"
                           name="floor-1-1" data-item-user="laoism">
                        <div class="post_actions re_info"><div class="action"><small>#1-1 - 2014-10-19 18:10</small></div></div>
                        <a class="avatar"><span style="background-image:url('https://example.com/bob.jpg')"></span></a>
                        <div class="inner">
                          <strong><a href="/user/laoism" class="l">老白</a></strong>
                          <div class="reply_content"><div class="message clearit">子回复</div></div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div id="post_300000" class="row row_reply" data-item-user="anonymous">
                    <div class="inner">
                      <strong><a class="l">匿名昵称</a></strong>
                      <div class="reply_content"><div class="message clearit">第二条</div></div>
                    </div>
                  </div>
                </div>
            """.trimIndent(),
            baseUrl = "https://bgm.tv/ep/1027",
        )

        assertEquals(listOf(205402, 300000), comments.map { it.id })
        val first = comments.first()
        assertEquals("chaucer", first.user.name)
        assertEquals("https://lain.bgm.tv/pic/user/l/1.jpg", first.user.avatarUrl)
        assertEquals("2013-12-03 21:45", first.createdAt)
        assertEquals(242518, first.replies.single().id)
        assertEquals("老白", first.replies.single().user.name)
        assertEquals("子回复", first.replies.single().content.text())
        assertFalse(first.content.text().contains("子回复"))

        val texts = first.content.filterIsInstance<BangumiCommentContent.Text>()
        assertTrue(texts.any { it.value == "粗体" && it.style.bold })
        assertTrue(texts.any { it.value == "链接" && it.style.linkUrl == "https://example.com" })
        val spoiler = first.content.filterIsInstance<BangumiCommentContent.Spoiler>().single()
        assertEquals("剧透", spoiler.children.text())
        assertEquals(
            "https://bgm.tv/img/secret.jpg",
            spoiler.children.filterIsInstance<BangumiCommentContent.Image>().single().url,
        )
        assertTrue(
            first.content.filterIsInstance<BangumiCommentContent.Image>()
                .any {
                    it.url == "https://bgm.tv/img/smiles/tv/15.gif" &&
                        it.inline &&
                        it.description == "(bgm38)"
                },
        )
    }

    @Test
    fun `parser rejects changed markup when page declares comments`() {
        assertThrows(IllegalStateException::class.java) {
            BangumiEpisodeCommentHtmlParser.parse(
                "<div class=\"singleCommentList\"><h2 class=\"subtitle\">吐槽箱 <span class=\"tip\">3</span></h2></div>",
                "https://bgm.tv/ep/1027",
            )
        }
    }

    @Test
    fun `parser returns empty list when comment container is absent`() {
        assertTrue(BangumiEpisodeCommentHtmlParser.parse("<html></html>", "https://bgm.tv").isEmpty())
    }
}

private fun List<BangumiCommentContent>.text(): String = joinToString("") { node ->
    when (node) {
        is BangumiCommentContent.Text -> node.value
        is BangumiCommentContent.Image -> ""
        is BangumiCommentContent.Spoiler -> node.children.text()
    }
}
