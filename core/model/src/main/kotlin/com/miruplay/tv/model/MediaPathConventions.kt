package com.miruplay.tv.model

import java.net.URLDecoder
import java.net.URLEncoder

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

    fun encodePathSegment(segment: String): String =
        URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

    fun decodePath(value: String): String =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)

    fun encodeRemotePath(path: String): String =
        normalizeRemotePath(path)
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { encodePathSegment(it) }

    private fun usesBackslashSeparator(path: String): Boolean =
        path.contains('\\') && !path.startsWith(SMB_SCHEME, ignoreCase = true)

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

    private const val SMB_SCHEME = "smb://"
}
