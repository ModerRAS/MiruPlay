package com.miruplay.tv.desktop

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.repository.MediaDetailRow
import com.miruplay.tv.repository.MediaDetailRows
import com.miruplay.tv.repository.MediaIndexEntry

internal typealias DesktopMediaDetailRow = MediaDetailRow

internal object DesktopMediaDetailRows {
    fun build(
        source: MediaSourceInfo?,
        indexEntry: MediaIndexEntry?,
        remoteEntry: FileEntry?,
        recentRecord: ProgressRecord?,
    ): List<DesktopMediaDetailRow> =
        MediaDetailRows.build(source, indexEntry, remoteEntry, recentRecord)
}
