package com.miruplay.tv.ui.library

import com.miruplay.tv.model.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryScanNoticeTest {
    @Test
    fun `mlip scan notice reports database counts and generation time`() {
        val notice = libraryScanNotice(
            results = listOf(
                ScanResult(
                    animeName = "动漫",
                    episodesFound = 4177,
                    newEpisodes = 12,
                    summary = "MLIP：259 部动漫，2672 集，4177 个文件；library.db 生成于 2026-07-22T07:55:30Z",
                ),
            ),
            sourceFailures = emptyList(),
        )

        assertEquals(
            "MLIP：259 部动漫，2672 集，4177 个文件；library.db 生成于 2026-07-22T07:55:30Z",
            notice,
        )
    }

    @Test
    fun `mixed scan notice includes summarized and ordinary sources`() {
        val notice = libraryScanNotice(
            results = listOf(
                ScanResult(
                    animeName = "MLIP",
                    episodesFound = 4177,
                    newEpisodes = 12,
                    summary = "MLIP：259 部动漫，2672 集，4177 个文件",
                ),
                ScanResult(animeName = "Local", episodesFound = 8, newEpisodes = 2),
            ),
            sourceFailures = emptyList(),
        )

        assertEquals("MLIP：259 部动漫，2672 集，4177 个文件；扫描完成：8 个文件", notice)
    }

    @Test
    fun `scan notice keeps failure visible when old content remains`() {
        val notice = libraryScanNotice(
            results = emptyList(),
            sourceFailures = listOf("未找到媒体库索引：library.db"),
        )

        assertEquals("扫描失败：未找到媒体库索引：library.db", notice)
    }
}
