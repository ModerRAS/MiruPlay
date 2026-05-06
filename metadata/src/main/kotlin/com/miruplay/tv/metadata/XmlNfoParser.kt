package com.miruplay.tv.metadata

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * XML-based NFO parser implementation
 */
class XmlNfoParser : NfoParser {

    override suspend fun parseEpisodeNfo(nfoPath: String): Result<NfoMetadata> = withContext(Dispatchers.IO) {
        try {
            val file = File(nfoPath)
            if (!file.exists()) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(nfoPath))
            }

            val doc = parseXml(file)
            val root = doc.documentElement

            if (root.tagName != "episodedetails") {
                return@withContext Result.failure(AppError.ParseError.NfoMalformed(1, "Expected episodedetails root"))
            }

            val metadata = NfoMetadata(
                title = getText(root, "title"),
                showTitle = getText(root, "showtitle"),
                season = getText(root, "season")?.toIntOrNull() ?: 1,
                episode = getText(root, "episode")?.toIntOrNull() ?: 1,
                plot = getText(root, "plot"),
                premiered = getText(root, "premiered"),
                rating = getText(root, "rating")?.toFloatOrNull() ?: 0f,
                playcount = getText(root, "playcount")?.toIntOrNull() ?: 0,
                lastplayed = getText(root, "lastplayed"),
                resumePosition = parseResumePosition(getText(root, "resume")),
                uniqueIds = parseUniqueIds(root)
            )

            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(AppError.ParseError.XmlParseError(e.message ?: "Unknown error"))
        }
    }

    override suspend fun parseTvShowNfo(nfoPath: String): Result<TvShowNfoMetadata> = withContext(Dispatchers.IO) {
        try {
            val file = File(nfoPath)
            if (!file.exists()) {
                return@withContext Result.failure(AppError.MediaSourceError.NotFound(nfoPath))
            }

            val doc = parseXml(file)
            val root = doc.documentElement

            if (root.tagName != "tvshow") {
                return@withContext Result.failure(AppError.ParseError.NfoMalformed(1, "Expected tvshow root"))
            }

            val metadata = TvShowNfoMetadata(
                title = getText(root, "title"),
                originalTitle = getText(root, "originaltitle") ?: getText(root, "title"),
                sortTitle = getText(root, "sorttitle"),
                plot = getText(root, "plot"),
                genre = parseGenres(root),
                premiered = getText(root, "premiered"),
                studio = getText(root, "studio"),
                rating = getText(root, "rating")?.toFloatOrNull() ?: 0f,
                uniqueIds = parseUniqueIds(root),
                actors = parseActors(root)
            )

            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(AppError.ParseError.XmlParseError(e.message ?: "Unknown error"))
        }
    }

    override suspend fun detectNfoType(nfoContent: String): NfoType {
        return when {
            nfoContent.contains("<episodedetails") -> NfoType.EPISODE
            nfoContent.contains("<tvshow") -> NfoType.TVSHOW
            nfoContent.contains("<movie") -> NfoType.MOVIE
            nfoContent.contains("<musicvideo") -> NfoType.MUSICVIDEO
            else -> NfoType.UNKNOWN
        }
    }

    private fun parseXml(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(file)
    }

    private fun getText(element: Element, tagName: String): String? {
        return element.getElementsByTagName(tagName).item(0)?.textContent?.trim()
    }

    private fun parseResumePosition(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        return text.toDoubleOrNull()?.times(60)?.toLong() ?: 0L
    }

    private fun parseUniqueIds(element: Element): List<UniqueId> {
        val ids = mutableListOf<UniqueId>()
        val idElements = element.getElementsByTagName("id")
        for (i in 0 until idElements.length) {
            val idElement = idElements.item(i) as? Element ?: continue
            val type = idElement.getAttribute("type")
            val value = idElement.textContent?.trim() ?: continue
            val isDefault = idElement.getAttribute("default") == "true"
            ids.add(UniqueId(type = type, value = value, isDefault = isDefault))
        }
        return ids
    }

    private fun parseGenres(element: Element): List<String> {
        val genres = mutableListOf<String>()
        val genreElements = element.getElementsByTagName("genre")
        for (i in 0 until genreElements.length) {
            genreElements.item(i)?.textContent?.trim()?.let { genre ->
                genres.addAll(genre.split("/").map { it.trim() })
            }
        }
        return genres.distinct()
    }

    private fun parseActors(element: Element): List<Actor> {
        val actors = mutableListOf<Actor>()
        val actorElements = element.getElementsByTagName("actor")
        for (i in 0 until actorElements.length) {
            val actorElement = actorElements.item(i) as? Element ?: continue
            val name = getText(actorElement, "name") ?: continue
            val role = getText(actorElement, "role")
            actors.add(Actor(name = name, role = role ?: ""))
        }
        return actors
    }
}
