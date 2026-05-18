package com.miruplay.tv.clouddrive

object CloudDrivePaths {
    fun normalize(path: String): String {
        return normalize(path, blankValue = "/")
    }

    fun normalizeScoped(path: String): String {
        return normalize(path, blankValue = "")
    }

    fun join(parentPath: String, fileName: String): String =
        "${normalize(parentPath).trimEnd('/')}/$fileName"

    fun parentPath(path: String): String {
        val normalized = normalize(path)
        return normalized.substringBeforeLast('/', "").ifBlank { "/" }
    }

    fun isScopedDirectory(path: String): Boolean {
        val normalized = normalizeScoped(path)
        return normalized.isNotBlank() && normalized != "/"
    }

    fun isSameOrChild(path: String, basePath: String): Boolean {
        val normalizedPath = normalizeScoped(path)
        val normalizedBase = normalizeScoped(basePath)
        return normalizedBase.isNotBlank() &&
            (normalizedPath == normalizedBase || normalizedPath.startsWith("$normalizedBase/"))
    }

    fun isChild(path: String, basePath: String): Boolean {
        val normalizedPath = normalizeScoped(path)
        val normalizedBase = normalizeScoped(basePath)
        return normalizedBase.isNotBlank() && normalizedPath.startsWith("$normalizedBase/")
    }

    private fun normalize(path: String, blankValue: String): String {
        val normalized = path.trim().replace('\\', '/').trimEnd('/')
        return when {
            normalized.isBlank() -> blankValue
            normalized.startsWith('/') -> normalized
            else -> "/$normalized"
        }
    }
}
