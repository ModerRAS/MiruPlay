package com.miruplay.tv.scanner

import com.miruplay.tv.model.MediaPathConventions

data class AudioClassification(
    val albumName: String,
    val artistName: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null
)

class AudioDirectoryClassifier {

    fun classifyAudio(path: String, fileName: String, rootContext: String? = null): AudioClassification {
        val classificationPath = path.withRootContext(rootContext)
        val segments = pathSegments(classificationPath)
        val fileIdx = segments.indexOfFirst { it.equals(fileName, ignoreCase = true) }
        val parentSegments = if (fileIdx >= 0) segments.take(fileIdx) else segments.dropLast(1)
        val album = parentSegments.lastOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Album"
        val artist = parentSegments.dropLast(1).lastOrNull()?.trim()?.takeIf { it.isNotBlank() && !it.isGenericName() }
        val fileStem = fileName.substringBeforeLast('.', fileName)
        val trackNumber = parseTrackNumber(fileStem)
        val discNumber = parseDiscNumber(fileStem) ?: parseDiscFromFolder(parentSegments)
        return AudioClassification(albumName = album, artistName = artist, trackNumber = trackNumber, discNumber = discNumber)
    }

    fun isCueName(name: String): Boolean = name.substringAfterLast('.', "").equals("cue", ignoreCase = true)

    private fun pathSegments(path: String): List<String> =
        path.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotBlank() }

    private fun String.withRootContext(rootContext: String?): String {
        val ctx = rootContext?.trim()?.takeIf { it.isNotBlank() } ?: return this
        val segs = pathSegments(this)
        if (segs.any { it.equals(ctx, ignoreCase = true) }) return this
        return "${ctx.trimEnd('/')}/${this.trimStart('/')}"
    }

    private fun String.isGenericName(): Boolean {
        val n = lowercase().trim()
        return n in setOf("music", "audio", "flac", "mp3", "library", "media", "downloads")
    }

    private fun parseTrackNumber(stem: String): Int? {
        // 01 - Title, 01.Title, Track 01, 1-01
        val m = Regex("""^\s*0?(\d{1,3})\s*[-_.\s]+""").find(stem)
        if (m != null) return m.groupValues[1].toIntOrNull()
        val m2 = Regex("""\b0?(\d{1,3})\b""").find(stem)
        // ponytail: only take leading number as track, avoid false positives from year
        return null
    }

    private fun parseDiscNumber(stem: String): Int? {
        val m = Regex("""(?i)\b(?:disc|cd|disk)\s*0?(\d)\b""").find(stem) ?: return null
        return m.groupValues[1].toIntOrNull()
    }

    private fun parseDiscFromFolder(segments: List<String>): Int? {
        for (seg in segments.reversed()) {
            val m = Regex("""(?i)\b(?:disc|cd|disk)\s*0?(\d)\b""").find(seg) ?: continue
            return m.groupValues[1].toIntOrNull()
        }
        return null
    }
}
