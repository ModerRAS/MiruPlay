package com.miruplay.tv.model

object MediaFileConventions {
    val defaultVideoExtensions: Set<String> = setOf(
        "mkv",
        "mp4",
        "avi",
        "mov",
        "webm",
        "wmv",
        "flv",
        "m4v",
    )

    private val hiddenNames: Set<String> = setOf(
        ".DS_Store",
        "Thumbs.db",
        "@eaDir",
        ".Trash",
        "\$RECYCLE.BIN",
        "System Volume Information",
    )

    fun isHiddenName(name: String): Boolean =
        name.trimEnd('/') in hiddenNames

    fun isVideoName(name: String, extensions: Set<String> = defaultVideoExtensions): Boolean {
        val extension = extensionOf(name)
        return extensions.any { it.equals(extension, ignoreCase = true) }
    }

    fun mimeTypeForName(name: String): String? =
        when (extensionOf(name)) {
            "mkv" -> "video/x-matroska"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "m4v" -> "video/x-m4v"
            "ass", "ssa" -> "text/x-ass"
            "srt" -> "application/x-subrip"
            "vtt" -> "text/vtt"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> null
        }

    fun sortEntries(entries: Iterable<FileEntry>): List<FileEntry> =
        entries.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })

    fun metadataFor(entry: FileEntry): FileMetadata =
        FileMetadata(
            name = entry.name,
            path = entry.path,
            isDirectory = entry.isDirectory,
            size = entry.size,
            lastModified = entry.lastModified,
            mimeType = entry.mimeType,
        )

    private fun extensionOf(name: String): String =
        name.substringAfterLast('/', name)
            .substringAfterLast('\\')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('.', "")
            .lowercase()
}
