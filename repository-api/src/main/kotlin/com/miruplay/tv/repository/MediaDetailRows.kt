package com.miruplay.tv.repository

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.formatFileSize
import com.miruplay.tv.model.formatLocalTimestamp
import com.miruplay.tv.model.formatPlaybackPosition

data class MediaDetailRow(
    val label: String,
    val value: String,
)

object MediaDetailRows {
    fun build(
        source: MediaSourceInfo?,
        indexEntry: MediaIndexEntry?,
        remoteEntry: FileEntry?,
        recentRecord: ProgressRecord?,
    ): List<MediaDetailRow> = buildList {
        addRow("Source", source?.displayLabel() ?: "None")
        indexEntry?.let { entry ->
            addRow("Indexed title", entry.displayName())
            addRow("Indexed type", if (entry.isDirectory) "Directory" else "Video")
            addRow("Anime", entry.animeName.orEmpty().ifBlank { "Unknown" })
            entry.seasonNumber?.let { addRow("Season", it.toString()) }
            addRow("Episode", entry.episodeNumber?.toString() ?: "Unknown")
            entry.episodeTitle?.takeIf { it.isNotBlank() }?.let { addRow("Episode title", it) }
            entry.metadataSource?.takeIf { it.isNotBlank() }?.let { addRow("Metadata source", it) }
            entry.metadataId?.takeIf { it.isNotBlank() }?.let { addRow("Metadata ID", it) }
            addRow("Metadata title", entry.metadataTitle?.takeIf { it.isNotBlank() } ?: "Not linked")
            entry.fileSize.takeIf { it > 0L }?.let { addRow("Indexed size", formatFileSize(it)) }
            formatLocalTimestamp(entry.lastModified)?.let { addRow("Indexed modified", it) }
        }
        remoteEntry?.let { entry ->
            addRow("Browser item", entry.name)
            addRow("Browser kind", if (entry.isDirectory) "Directory" else "File")
            entry.mimeType?.takeIf { it.isNotBlank() }?.let { addRow("MIME", it) }
            addRow("Browser size", if (entry.size > 0L) formatFileSize(entry.size) else "Unknown")
            formatLocalTimestamp(entry.lastModified)?.let { addRow("Browser modified", it) }
        }
        recentRecord?.let { record ->
            addRow("Resume", formatPlaybackPosition(record.positionMs))
            addRow("Play count", record.playCount.toString())
            formatLocalTimestamp(record.lastWatched)?.let { addRow("Last watched", it) }
        }
        indexEntry?.plot?.takeIf { it.isNotBlank() }?.let { addRow("Plot", it) }
        addRow("Path", indexEntry?.path ?: remoteEntry?.path ?: recentRecord?.episodeId ?: "None")
    }

    private fun MutableList<MediaDetailRow>.addRow(label: String, value: String) {
        add(MediaDetailRow(label, value))
    }
}
