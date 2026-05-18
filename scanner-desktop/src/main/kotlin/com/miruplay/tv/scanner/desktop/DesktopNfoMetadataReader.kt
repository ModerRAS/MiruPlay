package com.miruplay.tv.scanner.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.mediasource.desktop.DesktopMediaSource
import com.miruplay.tv.model.MediaPathConventions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

data class DesktopEpisodeNfoMetadata(
    val title: String? = null,
    val showTitle: String? = null,
    val plot: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

data class DesktopTvShowNfoMetadata(
    val title: String? = null,
    val originalTitle: String? = null,
)

class DesktopNfoMetadataReader {
    suspend fun readEpisodeForVideo(
        source: DesktopMediaSource,
        videoPath: String,
    ): DesktopEpisodeNfoMetadata? {
        val nfoPath = siblingNfoPath(videoPath)
        return when (val stream = source.openStream(nfoPath)) {
            is Result.Success -> withContext(Dispatchers.IO) {
                stream.data.use { parseEpisode(it) }
            }
            is Result.Error -> null
        }
    }

    suspend fun readTvShowForDirectory(
        source: DesktopMediaSource,
        directoryPath: String,
    ): DesktopTvShowNfoMetadata? {
        val nfoPath = childPath(directoryPath, "tvshow.nfo")
        return when (val stream = source.openStream(nfoPath)) {
            is Result.Success -> withContext(Dispatchers.IO) {
                stream.data.use { parseTvShow(it) }
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

    private fun parseEpisode(input: InputStream): DesktopEpisodeNfoMetadata? =
        runCatching {
            val document = newXmlFactory().newDocumentBuilder().parse(input)
            val root = document.documentElement ?: return null
            if (!root.tagName.equals("episodedetails", ignoreCase = true)) return null
            DesktopEpisodeNfoMetadata(
                title = root.text("title"),
                showTitle = root.text("showtitle"),
                plot = root.text("plot"),
                seasonNumber = root.text("season")?.toIntOrNull(),
                episodeNumber = root.text("episode")?.toIntOrNull(),
            )
        }.getOrNull()

    private fun parseTvShow(input: InputStream): DesktopTvShowNfoMetadata? =
        runCatching {
            val document = newXmlFactory().newDocumentBuilder().parse(input)
            val root = document.documentElement ?: return null
            if (!root.tagName.equals("tvshow", ignoreCase = true)) return null
            DesktopTvShowNfoMetadata(
                title = root.text("title"),
                originalTitle = root.text("originaltitle"),
            )
        }.getOrNull()

    private fun Element.text(tagName: String): String? =
        getElementsByTagName(tagName)
            .item(0)
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun newXmlFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setSafeFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setSafeFeature("http://xml.org/sax/features/external-general-entities", false)
            setSafeFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setSafeFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isExpandEntityReferences = false
            isXIncludeAware = false
        }

    private fun DocumentBuilderFactory.setSafeFeature(name: String, enabled: Boolean) {
        runCatching { setFeature(name, enabled) }
    }
}
