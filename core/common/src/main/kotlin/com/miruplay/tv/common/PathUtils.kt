package com.miruplay.tv.common

object PathUtils {
    fun normalizePath(path: String): String {
        return path
            .replace("\\", "/")
            .replace("/+".toRegex(), "/")
            .trimEnd('/')
    }

    fun extractFileName(path: String): String {
        val normalized = normalizePath(path)
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(lastSlash + 1) else normalized
    }

    fun extractDirectory(path: String): String {
        val normalized = normalizePath(path)
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(0, lastSlash) else ""
    }

    fun extractExtension(path: String): String {
        val fileName = extractFileName(path)
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot + 1).lowercase()
        } else ""
    }

    fun isHiddenFile(path: String): Boolean {
        val name = extractFileName(path)
        return name.startsWith(".") ||
               name == "Thumbs.db" ||
               name == "@eaDir" ||
               name == "desktop.ini" ||
               name.endsWith(".part")
    }

    fun joinPath(vararg parts: String): String {
        return parts.joinToString("/") { it.trimEnd('/') }
    }
}