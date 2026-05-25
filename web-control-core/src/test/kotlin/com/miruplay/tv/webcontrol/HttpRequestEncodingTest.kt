package com.miruplay.tv.webcontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpRequestEncodingTest {
    @Test
    fun `queryParameter decodes Chinese path from raw query as UTF-8`() {
        val rawQuery = "path=%2Fdav%2F115open%2F%E7%95%AA%E5%89%A7%2F%E4%B8%AD%E6%96%87"
        val parsed = mapOf("path" to listOf("/dav/115open/������/������"))

        val path = HttpRequestEncoding.queryParameter(rawQuery, parsed, "path")

        assertEquals("/dav/115open/番剧/中文", path)
    }

    @Test
    fun `queryParameter falls back to parsed parameter when raw query is missing`() {
        val path = HttpRequestEncoding.queryParameter(null, mapOf("path" to listOf("/storage/emulated/0")), "path")

        assertEquals("/storage/emulated/0", path)
    }

    @Test
    fun `utf8BodyCandidates includes Latin-1 mojibake repair`() {
        val original = """{"location":"/dav/番剧"}"""
        val mojibake = original.toByteArray(Charsets.UTF_8).toString(Charsets.ISO_8859_1)

        val candidates = HttpRequestEncoding.utf8BodyCandidates(mojibake)

        assertEquals(original, candidates.first())
    }
}
