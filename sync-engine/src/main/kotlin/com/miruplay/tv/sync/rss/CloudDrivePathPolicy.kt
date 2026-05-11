package com.miruplay.tv.sync.rss

internal object CloudDrivePathPolicy {
    fun normalize(path: String): String {
        val trimmed = path.trim().replace('\\', '/').trimEnd('/')
        return when {
            trimmed.isBlank() -> ""
            trimmed.startsWith('/') -> trimmed.ifBlank { "/" }
            else -> "/$trimmed"
        }
    }

    fun isScopedDirectory(path: String): Boolean {
        val normalized = normalize(path)
        return normalized.isNotBlank() && normalized != "/"
    }

    fun isSameOrChild(path: String, basePath: String): Boolean {
        val normalizedPath = normalize(path)
        val normalizedBase = normalize(basePath)
        return normalizedBase.isNotBlank() &&
            (normalizedPath == normalizedBase || normalizedPath.startsWith("$normalizedBase/"))
    }

    fun isChild(path: String, basePath: String): Boolean {
        val normalizedPath = normalize(path)
        val normalizedBase = normalize(basePath)
        return normalizedBase.isNotBlank() && normalizedPath.startsWith("$normalizedBase/")
    }
}
