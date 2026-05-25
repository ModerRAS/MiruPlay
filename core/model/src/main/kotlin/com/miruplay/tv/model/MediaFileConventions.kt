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

    fun hasExtension(name: String, extension: String): Boolean =
        extensionOf(name).equals(extension.trimStart('.'), ignoreCase = true)

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

    fun <T> fileEntryComparator(
        isDirectory: (T) -> Boolean,
        name: (T) -> String,
    ): Comparator<T> =
        compareBy<T> { !isDirectory(it) }.thenBy { name(it).lowercase() }

    fun sortEntries(entries: Iterable<FileEntry>): List<FileEntry> =
        entries.sortedWith(fileEntryComparator(FileEntry::isDirectory, FileEntry::name))

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
            .stripRemoteSuffixAfterExtension()
            .substringAfterLast('.', "")
            .lowercase()

    private fun String.stripRemoteSuffixAfterExtension(): String {
        val dotIndex = lastIndexOf('.')
        if (dotIndex < 0) return this

        val suffixIndex = listOf(
            indexOf('?', startIndex = dotIndex + 1),
            indexOf('#', startIndex = dotIndex + 1),
        ).filter { it >= 0 }.minOrNull() ?: return this

        return substring(0, suffixIndex)
    }
}
