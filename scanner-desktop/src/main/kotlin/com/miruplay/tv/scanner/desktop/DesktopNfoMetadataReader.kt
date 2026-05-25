package com.miruplay.tv.scanner.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.metadata.XmlNfoParser
import com.miruplay.tv.model.MediaPathConventions

class DesktopNfoMetadataReader {
    private val parser = XmlNfoParser()

    suspend fun readEpisodeForVideo(
        source: MediaSource,
        videoPath: String,
    ): com.miruplay.tv.model.NfoMetadata? {
        val nfoPath = siblingNfoPath(videoPath)
        return when (val stream = source.openStream(nfoPath)) {
            is Result.Success -> {
                val xml = stream.data.bufferedReader().use { it.readText() }
                parser.parseEpisodeNfoFromContent(xml).getOrNull()
            }
            is Result.Error -> null
        }
    }

    suspend fun readTvShowForDirectory(
        source: MediaSource,
        directoryPath: String,
    ): com.miruplay.tv.model.TvShowNfoMetadata? {
        val nfoPath = childPath(directoryPath, "tvshow.nfo")
        return when (val stream = source.openStream(nfoPath)) {
            is Result.Success -> {
                val xml = stream.data.bufferedReader().use { it.readText() }
                parser.parseTvShowNfoFromContent(xml).getOrNull()
            }
            is Result.Error -> null
        }
    }

    internal fun siblingNfoPath(videoPath: String): String {
        return MediaPathConventions.siblingWithExtension(videoPath, "nfo")
    }

    internal fun childPath(directoryPath: String, childName: String): String {
        return MediaPathConventions.childPath(directoryPath, childName)
    }
}
