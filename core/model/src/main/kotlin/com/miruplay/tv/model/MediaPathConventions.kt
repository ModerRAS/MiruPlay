package com.miruplay.tv.model

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Paths

object MediaPathConventions {
    fun fileName(path: String): String =
        path.trimEnd('/', '\\')
            .substringAfterLast('/')
            .substringAfterLast('\\')

    fun stem(path: String): String =
        fileName(path).substringBeforeLast('.', fileName(path))

    fun parentName(path: String): String =
        parentPath(path)
            ?.let(::fileName)
            .orEmpty()

    fun animeNameFromEpisodePath(path: String): String? {
        val segments = pathSegments(path)
        if (segments.isEmpty()) return null

        val mediaRootIndex = segments.indexOfLast { it.isMediaRootSegment() }
        if (mediaRootIndex >= 0) {
            val segmentsAfterRoot = segments.drop(mediaRootIndex + 1)
            if (segmentsAfterRoot.size >= 2) return segmentsAfterRoot.first()
            return segments[mediaRootIndex]
        }

        val parent = segments.dropLast(1).lastOrNull { !it.isPathRootSegment() }
        if (!parent.isNullOrBlank()) return parent

        return segments.firstOrNull { !it.isPathRootSegment() }
    }

    fun parentPath(path: String): String? {
        val trimmed = path.trimEnd('/', '\\')
        if (trimmed.isBlank()) return null
        val separatorIndex = maxOf(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'))
        if (separatorIndex < 0) return null
        return trimmed.substring(0, separatorIndex)
    }

    fun siblingPath(path: String, siblingFileName: String): String =
        parentPath(path)
            ?.let { childPath(it, siblingFileName) }
            ?: siblingFileName

    fun siblingWithExtension(path: String, extension: String): String {
        val siblingName = "${stem(path)}.${extension.trimStart('.')}"
        return siblingPath(path, siblingName)
    }

    fun childPath(directoryPath: String, childName: String): String {
        if (directoryPath.isBlank()) return childName
        val separator = if (usesBackslashSeparator(directoryPath)) "\\" else "/"
        return "${directoryPath.trimEnd('/', '\\')}$separator$childName"
    }

    fun remoteParent(path: String): String? {
        val clean = path.trimEnd('/')
        if (clean.isBlank() || clean == "/") return null
        if (clean.startsWith(SMB_SCHEME, ignoreCase = true)) {
            val segments = clean.removePrefixIgnoreCase(SMB_SCHEME)
                .split('/')
                .filter { it.isNotBlank() }
            if (segments.size <= 2) return null
            return "$SMB_SCHEME${segments.dropLast(1).joinToString("/")}"
        }

        val parent = clean.trim('/').substringBeforeLast('/', "")
        return if (parent.isBlank()) "" else "/$parent"
    }

    fun normalizeRemotePath(path: String): String =
        path.substringBefore('?')
            .replace('\\', '/')
            .trim('/')

    fun normalizeRemoteFilePath(path: String): String =
        path.replace('\\', '/')
            .trim('/')

    fun encodePathSegment(segment: String): String =
        URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

    fun decodePath(value: String): String =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)

    fun encodeRemotePath(path: String): String =
        normalizeRemoteFilePath(path)
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { encodePathSegment(it) }

    fun canonicalizeRemoteUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank() || "://" !in trimmed) return trimmed
        return runCatching {
            canonicalizeRemoteUri(URI(trimmed))
        }.getOrElse {
            REMOTE_URL_REGEX.matchEntire(trimmed)
                ?.let { match ->
                    val encodedPath = match.groupValues[3]
                        .split('/')
                        .joinToString("/") { segment ->
                            val decoded = runCatching {
                                URLDecoder.decode(
                                    segment.replace("+", "%2B"),
                                    Charsets.UTF_8.name(),
                                )
                            }.getOrDefault(segment)
                            encodePathSegment(decoded)
                        }
                    "${match.groupValues[1]}://${match.groupValues[2]}$encodedPath" +
                        match.groupValues[4] + match.groupValues[5]
                }
                ?: trimmed
        }
    }

    fun joinRemoteUrl(baseUrl: String, path: String): String {
        val trimmedPath = path.trim()
        if (trimmedPath.startsWith(HTTP_SCHEME, ignoreCase = true) ||
            trimmedPath.startsWith(HTTPS_SCHEME, ignoreCase = true)
        ) {
            return canonicalizeRemoteUrl(trimmedPath)
        }

        val base = canonicalizeRemoteUrl(baseUrl).trimEnd('/')
        if (base.isBlank()) return path
        if (path.startsWith(base)) return path
        val encodedPath = encodeRemotePath(path)
        return if (encodedPath.isBlank()) "$base/" else "$base/$encodedPath"
    }

    fun canonicalMediaKey(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return ""
        if ("://" in trimmed) return normalizeRemotePath(trimmed)
        return runCatching {
            Paths.get(trimmed).toAbsolutePath().normalize().toString()
        }.getOrElse {
            normalizeRemotePath(trimmed)
        }
    }

    private fun usesBackslashSeparator(path: String): Boolean =
        path.contains('\\') && !path.startsWith(SMB_SCHEME, ignoreCase = true)

    private fun pathSegments(path: String): List<String> =
        path.substringBefore('?')
            .split('/', '\\')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun String.isPathRootSegment(): Boolean =
        matches(WINDOWS_DRIVE_SEGMENT_REGEX) ||
            equals("smb:", ignoreCase = true) ||
            equals(SMB_SCHEME.removeSuffix("://"), ignoreCase = true) ||
            equals("content:", ignoreCase = true)

    private fun String.isMediaRootSegment(): Boolean =
        lowercase()
            .replace(Regex("""[._\-\[\]【】()（）]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim() in MEDIA_ROOT_SEGMENTS

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

    private fun canonicalizeRemoteUri(uri: URI): String {
        if (uri.scheme.isNullOrBlank() || uri.authority.isNullOrBlank()) {
            return uri.toString()
        }
        return URI(
            uri.scheme,
            uri.authority,
            uri.path,
            uri.query,
            uri.fragment,
        ).toASCIIString()
    }

    private val REMOTE_URL_REGEX = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*):\/\/([^\/?#]+)([^?#]*)(\?[^#]*)?(#.*)?$""")
    private val WINDOWS_DRIVE_SEGMENT_REGEX = Regex("""^[A-Za-z]:$""")
    private val MEDIA_ROOT_SEGMENTS = setOf(
        "115open",
        "ani",
        "anime",
        "anime library",
        "download",
        "downloads",
        "library",
        "media",
        "movies",
        "video",
        "videos",
        "动漫",
        "下载",
        "下載",
    )
    private const val SMB_SCHEME = "smb://"
    private const val HTTP_SCHEME = "http://"
    private const val HTTPS_SCHEME = "https://"
}
