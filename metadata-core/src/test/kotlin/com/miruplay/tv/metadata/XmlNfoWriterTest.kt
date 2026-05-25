package com.miruplay.tv.metadata

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class XmlNfoWriterTest {
    @Test
    fun `writeEpisodeNfo creates readable xml`() {
        val tempFile = File.createTempFile("miruplay-episode", ".nfo")
        try {
            val writer = XmlNfoWriter(NfoWriteOptions(createBackup = false))
            val metadata = com.miruplay.tv.model.NfoMetadata(
                title = "Episode Title",
                showTitle = "Show Title",
                season = 1,
                episode = 2,
            )

            val result = kotlinx.coroutines.runBlocking { writer.writeEpisodeNfo(tempFile.absolutePath, metadata) }

            assertTrue(result is com.miruplay.tv.core.common.Result.Success)
            assertTrue(tempFile.readText().contains("<episodedetails>"))
            assertTrue(tempFile.readText().contains("<title>Episode Title</title>"))
        } finally {
            tempFile.delete()
        }
    }
}
