package com.miruplay.tv.repository

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaPathConventions
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.mediaDetailIndexedKindValue
import com.miruplay.tv.model.displayTitle

fun MediaIndexEntry.displayName(): String {
    val title = animeName?.takeIf { it.isNotBlank() }
        ?: metadataTitle?.takeIf { it.isNotBlank() }
        ?: path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
    val episode = episodeNumber?.let { " EP$it" }.orEmpty()
    val episodeTitle = episodeTitle?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
    return "$title$episode$episodeTitle"
}

fun MediaIndexEntry.displayLine(): String {
    val kind = mediaDetailIndexedKindValue(isDirectory)
    return "[$kind] ${displayName()}  $path"
}

fun MediaIndexEntry.toBrowserEntry(): FileEntry =
    FileEntry(
        name = displayName(),
        path = path,
        isDirectory = isDirectory,
        size = fileSize,
        lastModified = lastModified,
    )

fun MediaIndexEntry.metadataQuery(): String? =
    animeName?.takeIf { it.isNotBlank() }
        ?: metadataTitle?.takeIf { it.isNotBlank() }
        ?: MediaPathConventions.stem(path).takeIf { it.isNotBlank() }

fun MediaIndexEntry.withExternalMetadata(result: ScraperResult, sourceId: Long = this.sourceId): MediaIndexEntry {
    val title = result.displayTitle()
    return copy(
        sourceId = sourceId,
        animeName = title,
        metadataSource = result.source.name,
        metadataId = result.animeId,
        metadataTitle = title,
        scrapeStatus = MediaScrapeStatus.SCRAPED,
        scrapeMessage = localMetadataOverrideMessage(),
        scrapedAt = System.currentTimeMillis(),
    )
}

fun MediaIndexEntry.clearExternalMetadata(sourceId: Long = this.sourceId): MediaIndexEntry =
    copy(
        sourceId = sourceId,
        metadataSource = null,
        metadataId = null,
        metadataTitle = null,
        scrapeStatus = null,
        scrapeMessage = null,
        scrapedAt = 0L,
    )

fun MediaIndexEntry.localMetadataOverrideKey(): String? =
    scrapeMessage
        ?.takeIf { it.startsWith(LOCAL_METADATA_OVERRIDE_PREFIX) }
        ?.removePrefix(LOCAL_METADATA_OVERRIDE_PREFIX)
        ?.takeIf { it.isNotBlank() }

private fun MediaIndexEntry.localMetadataOverrideMessage(): String? {
    localMetadataOverrideKey()?.let { return localMetadataOverrideMessage(it) }
    val mlipId = metadataId?.takeIf { id ->
        metadataSource.equals("MLIP", ignoreCase = true) && id.startsWith("mlip:")
    } ?: return null
    return localMetadataOverrideMessage(mlipId)
}

fun localMetadataOverrideMessage(key: String): String =
    LOCAL_METADATA_OVERRIDE_PREFIX + key

fun MediaIndexEntry.hasSameMediaKeyAs(other: MediaIndexEntry): Boolean =
    sourceId == other.sourceId && path == other.path

private const val LOCAL_METADATA_OVERRIDE_PREFIX = "Local metadata override for "

fun List<MediaIndexEntry>.mediaFilesOnly(): List<MediaIndexEntry> =
    filterNot { it.isDirectory }

fun List<MediaIndexEntry>.replaceByMediaKey(updated: MediaIndexEntry): List<MediaIndexEntry> =
    map { entry -> if (entry.hasSameMediaKeyAs(updated)) updated else entry }

fun List<MediaIndexEntry>.replaceByMediaKeys(updatedEntries: List<MediaIndexEntry>): List<MediaIndexEntry> {
    if (updatedEntries.isEmpty()) return this
    val byKey = updatedEntries.associateBy { it.sourceId to it.path }
    return map { entry -> byKey[entry.sourceId to entry.path] ?: entry }
}

fun MediaIndexEntry?.updatedSelectionAfterReplacingByMediaKeys(
    updatedEntries: List<MediaIndexEntry>,
): MediaIndexEntry? =
    this?.let { selected ->
        updatedEntries.firstOrNull { it.hasSameMediaKeyAs(selected) } ?: selected
    }

fun MediaIndexEntry?.retainedSelectionInMediaIndex(
    entries: List<MediaIndexEntry>,
): MediaIndexEntry? =
    this?.let { selected ->
        entries.firstOrNull { it.hasSameMediaKeyAs(selected) }
    }
