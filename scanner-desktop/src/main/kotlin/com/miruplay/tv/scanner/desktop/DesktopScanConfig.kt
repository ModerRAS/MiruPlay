package com.miruplay.tv.scanner.desktop

import com.miruplay.tv.model.MediaFileConventions

data class DesktopScanConfig(
    val maxDepth: Int = 8,
    val includeDirectories: Boolean = true,
    val videoExtensions: Set<String> = defaultVideoExtensions,
) {
    init {
        require(maxDepth >= 0) { "maxDepth must not be negative" }
    }

    companion object {
        val defaultVideoExtensions = MediaFileConventions.defaultVideoExtensions
    }
}

data class DesktopScanReport(
    val sourceId: Long,
    val rootPath: String,
    val entries: List<com.miruplay.tv.repository.MediaIndexEntry>,
    val filesIndexed: Int,
    val directoriesVisited: Int,
)
