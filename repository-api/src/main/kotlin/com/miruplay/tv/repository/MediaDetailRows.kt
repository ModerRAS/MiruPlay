package com.miruplay.tv.repository

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.formatFileSize
import com.miruplay.tv.model.formatLocalTimestamp
import com.miruplay.tv.model.formatPlaybackPosition
import com.miruplay.tv.model.mediaDetailAnimeLabel
import com.miruplay.tv.model.mediaDetailBrowserItemLabel
import com.miruplay.tv.model.mediaDetailBrowserKindLabel
import com.miruplay.tv.model.mediaDetailBrowserModifiedLabel
import com.miruplay.tv.model.mediaDetailBrowserSizeLabel
import com.miruplay.tv.model.mediaDetailDirectoryValue
import com.miruplay.tv.model.mediaDetailEpisodeLabel
import com.miruplay.tv.model.mediaDetailEpisodeTitleLabel
import com.miruplay.tv.model.mediaDetailFileValue
import com.miruplay.tv.model.mediaDetailIndexedModifiedLabel
import com.miruplay.tv.model.mediaDetailIndexedSizeLabel
import com.miruplay.tv.model.mediaDetailIndexedTitleLabel
import com.miruplay.tv.model.mediaDetailIndexedTypeLabel
import com.miruplay.tv.model.mediaDetailLastWatchedLabel
import com.miruplay.tv.model.mediaDetailMetadataIdLabel
import com.miruplay.tv.model.mediaDetailMetadataSourceLabel
import com.miruplay.tv.model.mediaDetailMetadataTitleLabel
import com.miruplay.tv.model.mediaDetailMimeLabel
import com.miruplay.tv.model.mediaDetailNotLinkedValue
import com.miruplay.tv.model.mediaDetailPathLabel
import com.miruplay.tv.model.mediaDetailPlayCountLabel
import com.miruplay.tv.model.mediaDetailPlotLabel
import com.miruplay.tv.model.mediaDetailResumeLabel
import com.miruplay.tv.model.mediaDetailSeasonLabel
import com.miruplay.tv.model.mediaDetailSourceEmptyValue
import com.miruplay.tv.model.mediaDetailSourceLabel
import com.miruplay.tv.model.mediaDetailUnknownValue
import com.miruplay.tv.model.mediaDetailVideoValue

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
        addRow(mediaDetailSourceLabel(), source?.displayLabel() ?: mediaDetailSourceEmptyValue())
        indexEntry?.let { entry ->
            addRow(mediaDetailIndexedTitleLabel(), entry.displayName())
            addRow(mediaDetailIndexedTypeLabel(), if (entry.isDirectory) mediaDetailDirectoryValue() else mediaDetailVideoValue())
            addRow(mediaDetailAnimeLabel(), entry.animeName.orEmpty().ifBlank { mediaDetailUnknownValue() })
            entry.seasonNumber?.let { addRow(mediaDetailSeasonLabel(), it.toString()) }
            addRow(mediaDetailEpisodeLabel(), entry.episodeNumber?.toString() ?: mediaDetailUnknownValue())
            entry.episodeTitle?.takeIf { it.isNotBlank() }?.let { addRow(mediaDetailEpisodeTitleLabel(), it) }
            entry.metadataSource?.takeIf { it.isNotBlank() }?.let { addRow(mediaDetailMetadataSourceLabel(), it) }
            entry.metadataId?.takeIf { it.isNotBlank() }?.let { addRow(mediaDetailMetadataIdLabel(), it) }
            addRow(mediaDetailMetadataTitleLabel(), entry.metadataTitle?.takeIf { it.isNotBlank() } ?: mediaDetailNotLinkedValue())
            entry.fileSize.takeIf { it > 0L }?.let { addRow(mediaDetailIndexedSizeLabel(), formatFileSize(it)) }
            formatLocalTimestamp(entry.lastModified)?.let { addRow(mediaDetailIndexedModifiedLabel(), it) }
        }
        remoteEntry?.let { entry ->
            addRow(mediaDetailBrowserItemLabel(), entry.name)
            addRow(mediaDetailBrowserKindLabel(), if (entry.isDirectory) mediaDetailDirectoryValue() else mediaDetailFileValue())
            entry.mimeType?.takeIf { it.isNotBlank() }?.let { addRow(mediaDetailMimeLabel(), it) }
            addRow(mediaDetailBrowserSizeLabel(), if (entry.size > 0L) formatFileSize(entry.size) else mediaDetailUnknownValue())
            formatLocalTimestamp(entry.lastModified)?.let { addRow(mediaDetailBrowserModifiedLabel(), it) }
        }
        recentRecord?.let { record ->
            addRow(mediaDetailResumeLabel(), formatPlaybackPosition(record.positionMs))
            addRow(mediaDetailPlayCountLabel(), record.playCount.toString())
            formatLocalTimestamp(record.lastWatched)?.let { addRow(mediaDetailLastWatchedLabel(), it) }
        }
        indexEntry?.plot?.takeIf { it.isNotBlank() }?.let { addRow(mediaDetailPlotLabel(), it) }
        addRow(mediaDetailPathLabel(), indexEntry?.path ?: remoteEntry?.path ?: recentRecord?.episodeId ?: mediaDetailSourceEmptyValue())
    }

    private fun MutableList<MediaDetailRow>.addRow(label: String, value: String) {
        add(MediaDetailRow(label, value))
    }
}
