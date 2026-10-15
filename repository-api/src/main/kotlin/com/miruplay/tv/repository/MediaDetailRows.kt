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
import com.miruplay.tv.core.common.UiText
import com.miruplay.tv.core.common.rawUiText
import com.miruplay.tv.model.mediaDetailVideoValue

data class MediaDetailRow(
    val label: UiText,
    val value: UiText,
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
            addRow(mediaDetailIndexedTitleLabel(), rawUiText(entry.displayName()))
            addRow(mediaDetailIndexedTypeLabel(), if (entry.isDirectory) mediaDetailDirectoryValue() else mediaDetailVideoValue())
            addRow(mediaDetailAnimeLabel(), entry.animeName.orEmpty().takeIf { it.isNotBlank() }?.let { rawUiText(it) } ?: mediaDetailUnknownValue())
            entry.seasonNumber?.let { addRow(mediaDetailSeasonLabel(), rawUiText(it.toString())) }
            addRow(mediaDetailEpisodeLabel(), entry.episodeNumber?.toString()?.let { rawUiText(it) } ?: mediaDetailUnknownValue())
            entry.episodeTitle?.takeIf { it.isNotBlank() }?.let { addRow(mediaDetailEpisodeTitleLabel(), rawUiText(it)) }
            entry.metadataSource?.takeIf { it.isNotBlank() }?.let { addRow(mediaDetailMetadataSourceLabel(), rawUiText(it)) }
            entry.metadataId?.takeIf { it.isNotBlank() }?.let { addRow(mediaDetailMetadataIdLabel(), rawUiText(it)) }
            addRow(mediaDetailMetadataTitleLabel(), entry.metadataTitle?.takeIf { it.isNotBlank() }?.let { rawUiText(it) } ?: mediaDetailNotLinkedValue())
            entry.fileSize.takeIf { it > 0L }?.let { addRow(mediaDetailIndexedSizeLabel(), rawUiText(formatFileSize(it))) }
            formatLocalTimestamp(entry.lastModified)?.let { addRow(mediaDetailIndexedModifiedLabel(), rawUiText(it)) }
        }
        remoteEntry?.let { entry ->
            addRow(mediaDetailBrowserItemLabel(), rawUiText(entry.name))
            addRow(mediaDetailBrowserKindLabel(), if (entry.isDirectory) mediaDetailDirectoryValue() else mediaDetailFileValue())
            entry.mimeType?.takeIf { it.isNotBlank() }?.let { addRow(mediaDetailMimeLabel(), rawUiText(it)) }
            addRow(mediaDetailBrowserSizeLabel(), if (entry.size > 0L) rawUiText(formatFileSize(entry.size)) else mediaDetailUnknownValue())
            formatLocalTimestamp(entry.lastModified)?.let { addRow(mediaDetailBrowserModifiedLabel(), rawUiText(it)) }
        }
        recentRecord?.let { record ->
            addRow(mediaDetailResumeLabel(), rawUiText(formatPlaybackPosition(record.positionMs)))
            addRow(mediaDetailPlayCountLabel(), rawUiText(record.playCount.toString()))
            formatLocalTimestamp(record.lastWatched)?.let { addRow(mediaDetailLastWatchedLabel(), rawUiText(it)) }
        }
        indexEntry?.plot?.takeIf { it.isNotBlank() }?.let { addRow(mediaDetailPlotLabel(), rawUiText(it)) }
        addRow(mediaDetailPathLabel(), (indexEntry?.path ?: remoteEntry?.path ?: recentRecord?.episodeId)?.let { rawUiText(it) } ?: mediaDetailSourceEmptyValue())
    }

    private fun MutableList<MediaDetailRow>.addRow(label: UiText, value: UiText) {
        add(MediaDetailRow(label, value))
    }
}
