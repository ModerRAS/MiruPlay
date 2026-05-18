package com.miruplay.tv.clouddrive

object CloudDrivePaths {
    fun normalize(path: String): String {
        val normalized = path.trim().replace('\\', '/').trimEnd('/')
        return when {
            normalized.isBlank() -> "/"
            normalized.startsWith('/') -> normalized
            else -> "/$normalized"
        }
    }

    fun join(parentPath: String, fileName: String): String =
        "${normalize(parentPath).trimEnd('/')}/$fileName"
}
