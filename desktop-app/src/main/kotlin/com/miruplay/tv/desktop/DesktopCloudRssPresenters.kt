package com.miruplay.tv.desktop

import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.sync.rss.DesktopCloudDriveRssSchedulerState
import com.miruplay.tv.sync.rss.linkedCloudDriveSourceLabel
import com.miruplay.tv.sync.rss.schedulerStatus as sharedSchedulerStatus

internal fun schedulerStatus(state: DesktopCloudDriveRssSchedulerState): String =
    state.sharedSchedulerStatus()

internal fun linkedSourceLabel(
    sources: List<MediaSourceInfo>,
    sourceId: Long?,
): String =
    linkedCloudDriveSourceLabel(sources, sourceId)
