package com.miruplay.tv.player

internal sealed interface LibVlcMediaTarget {
    data class LocalPath(val path: String) : LibVlcMediaTarget
    data class Location(val uri: String) : LibVlcMediaTarget
}

internal fun resolveLibVlcMediaTarget(sourceUri: String): LibVlcMediaTarget {
    val trimmed = sourceUri.trim()
    if (trimmed.startsWith("file://", ignoreCase = true)) {
        return trimmed.substringAfter("file://")
            .takeIf { it.isNotBlank() }
            ?.let(LibVlcMediaTarget::LocalPath)
            ?: LibVlcMediaTarget.Location(trimmed)
    }
    if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("content://", ignoreCase = true)
    ) {
        return LibVlcMediaTarget.Location(trimmed)
    }
    if (trimmed.startsWith("/")) {
        return LibVlcMediaTarget.LocalPath(trimmed)
    }
    return LibVlcMediaTarget.Location(trimmed)
}
