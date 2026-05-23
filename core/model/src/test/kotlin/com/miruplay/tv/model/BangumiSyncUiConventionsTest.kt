package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BangumiSyncUiConventionsTest {
    @Test
    fun `Bangumi sync messages are shared`() {
        assertEquals("请先在设置里保存 Access Token", bangumiSyncMissingTokenMessage())
        assertEquals("当前番剧还没有 Bangumi 条目 ID，请先重新刮削", bangumiSyncMissingSubjectIdMessage())
        assertEquals("当前番剧没有可同步剧集", bangumiSyncNoEpisodesMessage())
        assertEquals("同步失败", bangumiSyncFailedMessage())
        assertEquals("同步剧集失败", bangumiSyncEpisodeFailedMessage())
    }
}
