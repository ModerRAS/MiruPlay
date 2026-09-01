package com.miruplay.tv.scanner

import android.media.MediaMetadataRetriever
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaPathConventions
import java.io.File

data class AudioTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: String? = null,
    val durationMs: Long? = null,
    val coverPath: String? = null
)

class AudioTagReader(
    private val coverCacheDir: File? = null
) {
    suspend fun readTags(source: MediaSource, entry: FileEntry): AudioTags {
        // ponytail: 64KB header Vorbis/ID3 parsing for WEBDAV/SMB left as fallback to filename; MediaMetadataRetriever covers LOCAL 95%
        val localPath = entry.path
        if (isLocalFile(localPath)) {
            readViaRetriever(localPath)?.let { return it }
        }
        // For remote or retriever failure, try stream header peek (light) then fallback
        readViaStreamHeader(source, entry)?.let { return it }
        return fallbackFromFileName(entry)
    }

    private fun isLocalFile(path: String): Boolean {
        if (path.startsWith("content://", ignoreCase = true)) return false
        return try {
            val f = File(path)
            f.exists() && f.isFile
        } catch (_: Exception) {
            false
        }
    }

    private fun readViaRetriever(path: String): AudioTags? = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotBlank() }
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotBlank() }
        val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotBlank() }
        val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()?.takeIf { it.isNotBlank() }
        val trackStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
        val discStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
        val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.trim()?.takeIf { it.isNotBlank() }
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        val trackNumber = trackStr?.substringBefore('/')?.trim()?.toIntOrNull()
        val discNumber = discStr?.substringBefore('/')?.trim()?.toIntOrNull()
        val coverPath = extractCover(retriever, path)
        retriever.release()
        AudioTags(title = title, artist = artist, album = album, albumArtist = albumArtist, trackNumber = trackNumber, discNumber = discNumber, year = year, durationMs = duration, coverPath = coverPath)
    } catch (_: Exception) {
        null
    }

    private fun extractCover(retriever: MediaMetadataRetriever, path: String): String? {
        if (coverCacheDir == null) return null
        return try {
            val bytes = retriever.embeddedPicture ?: return null
            if (bytes.isEmpty()) return null
            // ponytail: global cache dir, per-track hash; per-album dedup if needed later
            val hash = path.hashCode().toString(16)
            val out = File(coverCacheDir, "music_cover_$hash.jpg").apply { parentFile?.mkdirs() }
            if (!out.exists()) out.writeBytes(bytes)
            out.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun readViaStreamHeader(source: MediaSource, entry: FileEntry): AudioTags? {
        // ponytail: 64KB FLAC Vorbis/ID3 header parsing left as TODO; return null to fallback now, upgrade when remote tag miss rate >5%
        return null
    }

    private fun fallbackFromFileName(entry: FileEntry): AudioTags {
        val fileName = MediaPathConventions.fileName(entry.path)
        val stem = fileName.substringBeforeLast('.', fileName)
        // Try to infer track number prefix like "01 - Title" or "01 Title"
        val trackNumber = Regex("""^\s*0?(\d{1,3})\s*[-_.]\s*""").find(stem)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val title = if (trackNumber != null) stem.replaceFirst(Regex("""^\s*0?\d{1,3}\s*[-_.]\s*"""), "").trim().takeIf { it.isNotBlank() } else stem.trim().takeIf { it.isNotBlank() }
        // Album/artist from directory via classifier will be filled later; keep null here
        return AudioTags(title = title, trackNumber = trackNumber, durationMs = entry.let { null })
    }
}
