package com.miruplay.tv.model

import org.w3c.dom.Element
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

object WebDavPropfindParser {
    fun parse(
        xml: String,
        baseUrl: String,
        requestedPath: String,
        includeRequestedPath: Boolean = false,
    ): List<FileEntry> {
        val factory = newXmlFactory()
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val responses = doc.getElementsByTagNameNS(NS_DAV, "response")

        val normalizedRequestedPath = MediaPathConventions.normalizeRemoteFilePath(requestedPath)
        val entries = mutableListOf<FileEntry>()
        for (i in 0 until responses.length) {
            val response = responses.item(i) as? Element ?: continue
            val href = response.childText("href") ?: continue
            val path = hrefToRemotePath(href, baseUrl)
            if (path.isBlank()) continue
            if (!includeRequestedPath && path == normalizedRequestedPath) continue

            val name = path.trimEnd('/').substringAfterLast('/')
            if (MediaFileConventions.isHiddenName(name)) continue

            val directory = response.isCollection()
            entries += FileEntry(
                name = name,
                path = "/${path.trim('/')}",
                isDirectory = directory,
                size = if (directory) 0L else response.childText("getcontentlength")?.toLongOrNull() ?: 0L,
                lastModified = parseDavDate(response.childText("getlastmodified").orEmpty()),
                mimeType = if (directory) null else response.childText("getcontenttype"),
            )
        }

        return MediaFileConventions.sortEntries(entries.filter { it.path != "/" })
    }

    private fun hrefToRemotePath(href: String, baseUrl: String): String {
        val decodedPath = MediaPathConventions.decodePath(pathFromUriOrManual(href))
        val basePath = extractBasePath(baseUrl)
        val withoutBase = when {
            basePath.isBlank() -> decodedPath
            decodedPath == basePath -> ""
            decodedPath.startsWith("$basePath/") -> decodedPath.removePrefix(basePath)
            else -> decodedPath
        }
        return MediaPathConventions.normalizeRemoteFilePath(withoutBase)
    }

    private fun extractBasePath(baseUrl: String): String {
        runCatching { URI(baseUrl).path?.trimEnd('/') }
            .getOrNull()
            ?.let { return it }

        val schemeEnd = baseUrl.indexOf("://")
        if (schemeEnd < 0) return ""
        val afterScheme = baseUrl.substring(schemeEnd + 3)
        val pathStart = afterScheme.indexOf('/')
        if (pathStart < 0) return ""
        return MediaPathConventions.decodePath(
            afterScheme.substring(pathStart)
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/'),
        )
    }

    private fun pathFromUriOrManual(value: String): String =
        runCatching { URI(value).path ?: value }.getOrElse { manualPath(value) }

    private fun manualPath(value: String): String {
        val withoutQuery = value.substringBefore('?').substringBefore('#')
        val schemeEnd = withoutQuery.indexOf("://")
        if (schemeEnd < 0) return withoutQuery
        val afterScheme = withoutQuery.substring(schemeEnd + 3)
        val pathStart = afterScheme.indexOf('/')
        return if (pathStart < 0) "" else afterScheme.substring(pathStart)
    }

    private fun Element.isCollection(): Boolean {
        val resTypes = getElementsByTagNameNS(NS_DAV, "resourcetype")
        for (i in 0 until resTypes.length) {
            val nodes = resTypes.item(i).childNodes
            for (j in 0 until nodes.length) {
                if (nodes.item(j).localName == "collection") return true
            }
        }
        return false
    }

    private fun Element.childText(tagName: String): String? {
        val nodes = getElementsByTagNameNS(NS_DAV, tagName)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
    }

    private fun parseDavDate(date: String): Long =
        runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).parse(date)?.time ?: 0L
        }.getOrDefault(0L)

    private fun newXmlFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setSafeFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setSafeFeature("http://xml.org/sax/features/external-general-entities", false)
            setSafeFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setSafeFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setXIncludeAwareSafely(false)
            setExpandEntityReferencesSafely(false)
        }

    private fun DocumentBuilderFactory.setSafeFeature(name: String, enabled: Boolean) {
        runCatching { setFeature(name, enabled) }
    }

    private fun DocumentBuilderFactory.setXIncludeAwareSafely(enabled: Boolean) {
        runCatching { isXIncludeAware = enabled }
    }

    private fun DocumentBuilderFactory.setExpandEntityReferencesSafely(enabled: Boolean) {
        runCatching { isExpandEntityReferences = enabled }
    }

    private const val NS_DAV = "DAV:"
}
