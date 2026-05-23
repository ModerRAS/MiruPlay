package com.miruplay.tv.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlNfoParserTest {
    private val parser = XmlNfoParser()

    @Test
    fun `detectNfoType distinguishes episode and tv show content`() = kotlinx.coroutines.runBlocking {
        assertEquals(NfoType.EPISODE, parser.detectNfoType("<episodedetails/>"))
        assertEquals(NfoType.TVSHOW, parser.detectNfoType("<tvshow/>"))
        assertEquals(NfoType.UNKNOWN, parser.detectNfoType("<root/>"))
    }

    @Test
    fun `parseEpisodeNfoFromContent extracts episode metadata`() = kotlinx.coroutines.runBlocking {
        val result = parser.parseEpisodeNfoFromContent(
            """
            <episodedetails>
                <title>Episode Title</title>
                <showtitle>Show Title</showtitle>
                <season>2</season>
                <episode>7</episode>
                <plot>Plot text.</plot>
                <playcount>3</playcount>
                <resume>12.5</resume>
                <id type="bangumi" default="true">123</id>
            </episodedetails>
            """.trimIndent()
        )

        assertTrue(result is com.miruplay.tv.core.common.Result.Success)
        val metadata = (result as com.miruplay.tv.core.common.Result.Success).data
        assertEquals("Episode Title", metadata.title)
        assertEquals("Show Title", metadata.showTitle)
        assertEquals(2, metadata.season)
        assertEquals(7, metadata.episode)
        assertEquals("Plot text.", metadata.plot)
        assertEquals(3, metadata.playcount)
        assertEquals(750000L, metadata.resumePosition)
    }

    @Test
    fun `parseTvShowNfoFromContent extracts tv show metadata`() = kotlinx.coroutines.runBlocking {
        val result = parser.parseTvShowNfoFromContent(
            """
            <tvshow>
                <title>Show Title</title>
                <originaltitle>Original</originaltitle>
                <sorttitle>Sort Title</sorttitle>
                <plot>Plot text.</plot>
                <genre>Action/Comedy</genre>
                <studio>Studio</studio>
                <rating>8.5</rating>
                <actor>
                    <name>Actor One</name>
                    <role>Lead</role>
                </actor>
            </tvshow>
            """.trimIndent()
        )

        assertTrue(result is com.miruplay.tv.core.common.Result.Success)
        val metadata = (result as com.miruplay.tv.core.common.Result.Success).data
        assertEquals("Show Title", metadata.title)
        assertEquals("Original", metadata.originalTitle)
        assertEquals("Sort Title", metadata.sortTitle)
        assertEquals(listOf("Action", "Comedy"), metadata.genre)
        assertEquals("Studio", metadata.studio)
        assertEquals(8.5f, metadata.rating)
        assertEquals(1, metadata.actors.size)
        assertEquals("Actor One", metadata.actors.single().name)
    }
}
