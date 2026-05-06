package com.miruplay.tv.metadata

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * XML-based NFO writer implementation
 */
class XmlNfoWriter(
    private val options: NfoWriteOptions = NfoWriteOptions()
) : NfoWriter {

    override suspend fun writeEpisodeNfo(nfoPath: String, metadata: NfoMetadata): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(nfoPath)

                // Backup existing file
                if (options.createBackup && file.exists()) {
                    file.copyTo(File("$nfoPath${options.backupSuffix}"), overwrite = true)
                }

                val xml = buildString {
                    appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    appendLine("<episodedetails>")
                    appendLine("  <title>${escapeXml(metadata.title)}</title>")
                    metadata.showTitle?.let { appendLine("  <showtitle>${escapeXml(it)}</showtitle>") }
                    appendLine("  <season>${metadata.season}</season>")
                    appendLine("  <episode>${metadata.episode}</episode>")
                    if (metadata.plot.isNotBlank()) {
                        appendLine("  <plot>${escapeXml(metadata.plot)}</plot>")
                    }
                    metadata.premiered?.let { appendLine("  <premiered>${escapeXml(it)}</premiered>") }
                    if (metadata.rating > 0) {
                        appendLine("  <rating>${metadata.rating}</rating>")
                    }
                    appendLine("  <playcount>${metadata.playcount}</playcount>")
                    if (metadata.lastplayed != null) {
                        appendLine("  <lastplayed>${escapeXml(metadata.lastplayed)}</lastplayed>")
                    }
                    if (metadata.resumePosition > 0) {
                        val minutes = metadata.resumePosition / 60.0 / 1000.0
                        appendLine("  <resume>${String.format("%.6f", minutes)}</resume>")
                    }
                    metadata.uniqueIds.forEach { id ->
                        appendLine("  <id type=\"${id.type}\" default=\"${id.isDefault}\">${escapeXml(id.value)}</id>")
                    }
                    appendLine("</episodedetails>")
                }

                file.writeText(xml, Charsets.UTF_8)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(AppError.SyncError.WriteFailed(nfoPath, e.message ?: "Unknown error"))
            }
        }

    override suspend fun writeTvShowNfo(nfoPath: String, metadata: TvShowNfoMetadata): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(nfoPath)

                if (options.createBackup && file.exists()) {
                    file.copyTo(File("$nfoPath${options.backupSuffix}"), overwrite = true)
                }

                val xml = buildString {
                    appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    appendLine("<tvshow>")
                    appendLine("  <title>${escapeXml(metadata.title)}</title>")
                    if (metadata.originalTitle.isNotBlank()) {
                        appendLine("  <originaltitle>${escapeXml(metadata.originalTitle)}</originaltitle>")
                    }
                    metadata.sortTitle?.let { appendLine("  <sorttitle>${escapeXml(it)}</sorttitle>") }
                    if (metadata.plot.isNotBlank()) {
                        appendLine("  <plot>${escapeXml(metadata.plot)}</plot>")
                    }
                    if (metadata.genre.isNotEmpty()) {
                        appendLine("  <genre>${metadata.genre.joinToString("/") { escapeXml(it) }}</genre>")
                    }
                    metadata.premiered?.let { appendLine("  <premiered>${escapeXml(it)}</premiered>") }
                    metadata.studio?.let { appendLine("  <studio>${escapeXml(it)}</studio>") }
                    if (metadata.rating > 0) {
                        appendLine("  <rating>${metadata.rating}</rating>")
                    }
                    metadata.uniqueIds.forEach { id ->
                        appendLine("  <id type=\"${id.type}\" default=\"${id.isDefault}\">${escapeXml(id.value)}</id>")
                    }
                    metadata.actors.forEach { actor ->
                        appendLine("  <actor>")
                        appendLine("    <name>${escapeXml(actor.name)}</name>")
                        if (actor.role.isNotBlank()) {
                            appendLine("    <role>${escapeXml(actor.role)}</role>")
                        }
                        appendLine("  </actor>")
                    }
                    appendLine("</tvshow>")
                }

                file.writeText(xml, Charsets.UTF_8)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(AppError.SyncError.WriteFailed(nfoPath, e.message ?: "Unknown error"))
            }
        }

    override suspend fun updateWatchProgress(nfoPath: String, position: Long, lastWatched: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(nfoPath)
                if (!file.exists()) {
                    return@withContext Result.failure(AppError.MediaSourceError.NotFound(nfoPath))
                }

                // Parse existing NFO
                val parser = XmlNfoParser()
                parser.parseEpisodeNfo(nfoPath).onSuccess { metadata ->
                    val updated = metadata.copy(
                        resumePosition = position,
                        lastplayed = formatTimestamp(lastWatched)
                    )
                    writeEpisodeNfo(nfoPath, updated)
                }.onError { error ->
                    return@withContext Result.failure(error)
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(AppError.SyncError.WriteFailed(nfoPath, e.message ?: "Unknown error"))
            }
        }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatTimestamp(timestamp: Long): String {
        val dateTime = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }
}
