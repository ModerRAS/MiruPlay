package com.miruplay.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDataSourceFactoryTest {
    @Test
    fun `headersFor applies auth when uri stays on same WebDAV origin`() {
        val config = PlaybackHttpRequestConfig(
            baseUrl = "http://10.137.32.158:19798/dav/115open/影音/电视剧",
            headers = mapOf("Authorization" to "Basic YW5vbnltb3VzOg=="),
        )

        val headers = config.headersFor(
            "http://10.137.32.158:19798/dav/115open/%E5%BD%B1%E9%9F%B3/%E7%94%B5%E8%A7%86%E5%89%A7/%E5%8C%BB%E9%A6%86%E7%AC%91%E4%BC%A0/%E5%8C%BB%E9%A6%86%E7%AC%91%E4%BC%A0.S01/%E5%8C%BB%E9%A6%86%E7%AC%91%E4%BC%A0.S01E01.mp4",
        )

        assertEquals("Basic YW5vbnltb3VzOg==", headers["Authorization"])
    }

    @Test
    fun `headersFor leaves unrelated origins unauthenticated`() {
        val config = PlaybackHttpRequestConfig(
            baseUrl = "http://10.137.32.158:19798/dav/115open/影音/电视剧",
            headers = mapOf("Authorization" to "Basic YW5vbnltb3VzOg=="),
        )

        val headers = config.headersFor("https://cdn.example.test/video.mp4")

        assertTrue(headers.isEmpty())
    }
}
