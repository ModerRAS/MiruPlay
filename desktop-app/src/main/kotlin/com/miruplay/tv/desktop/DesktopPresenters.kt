package com.miruplay.tv.desktop

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.SubtitleFormat
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.model.formatFileSize
import com.miruplay.tv.model.formatPlaybackPosition
import com.miruplay.tv.repository.MetadataBatchConflict
import com.miruplay.tv.repository.MetadataBatchMatch
import com.miruplay.tv.repository.MetadataBatchPlan
import com.miruplay.tv.repository.MetadataBatchPlanner
import com.miruplay.tv.repository.MetadataBatchUpdate
import com.miruplay.tv.repository.MediaIndexEntry
import java.nio.file.Paths
import kotlin.math.roundToLong

internal object DesktopRuntimeDefaults {
    fun mpvPath(): String = Paths.get("runtime", "mpv", "mpv.exe").toString()

    fun configDirectory(): String = Paths.get("runtime", "mpv", "portable_config").toString()
}

internal data class DesktopSourceListItem(
    val source: MediaSourceInfo,
) {
    override fun toString(): String {
        val location = source.connectionInfo["path"]
            ?: source.connectionInfo["url"]
            ?: source.connectionInfo.values.firstOrNull()
            ?: ""
        return "${source.type.name}: ${source.name}  $location"
    }
}

internal object DesktopPlaybackSourceFactory {
    fun buildSubtitleTracks(value: String): List<SubtitleTrack> =
        value
            .split(';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map(::subtitleTrackFromPath)

    fun subtitleTrackFromPath(path: String): SubtitleTrack {
        val normalized = path.trim()
        return SubtitleTrack(
            title = normalized.substringAfterLast('/').substringAfterLast('\\'),
            isExternal = true,
            path = normalized,
            format = subtitleFormat(normalized),
        )
    }

    private fun subtitleFormat(path: String): SubtitleFormat =
        when (path.substringAfterLast('.', "").lowercase()) {
            "ass" -> SubtitleFormat.ASS
            "ssa" -> SubtitleFormat.SSA
            "vtt" -> SubtitleFormat.VTT
            else -> SubtitleFormat.SRT
        }
}

internal object DesktopIndexSearchPresenter {
    fun displayName(entry: MediaIndexEntry): String {
        val title = entry.animeName?.takeIf { it.isNotBlank() }
            ?: entry.metadataTitle?.takeIf { it.isNotBlank() }
            ?: entry.path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val episode = entry.episodeNumber?.let { " EP$it" }.orEmpty()
        val episodeTitle = entry.episodeTitle?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
        return "$title$episode$episodeTitle"
    }

    fun displayLine(entry: MediaIndexEntry): String {
        val kind = if (entry.isDirectory) "DIR" else "VID"
        return "[$kind] ${displayName(entry)}  ${entry.path}"
    }

    fun toBrowserEntry(entry: MediaIndexEntry): FileEntry =
        FileEntry(
            name = displayName(entry),
            path = entry.path,
            isDirectory = entry.isDirectory,
            size = entry.fileSize,
            lastModified = entry.lastModified,
        )
}

internal object DesktopMediaDetailsPresenter {
    fun details(file: FileEntry, indexEntry: MediaIndexEntry?): String = buildString {
        val displayName = indexEntry?.let(DesktopIndexSearchPresenter::displayName) ?: file.name
        appendLine("Name: $displayName")
        indexEntry?.animeName?.takeIf { it.isNotBlank() }?.let { appendLine("Anime: $it") }
        indexEntry?.seasonNumber?.let { appendLine("Season: $it") }
        indexEntry?.episodeNumber?.let { appendLine("Episode: $it") }
        indexEntry?.episodeTitle?.takeIf { it.isNotBlank() }?.let { appendLine("Episode title: $it") }
        indexEntry?.metadataSource?.takeIf { it.isNotBlank() }?.let { appendLine("Metadata source: $it") }
        indexEntry?.metadataId?.takeIf { it.isNotBlank() }?.let { appendLine("Metadata ID: $it") }
        indexEntry?.metadataTitle?.takeIf { it.isNotBlank() }?.let { appendLine("Metadata title: $it") }
        val size = indexEntry?.fileSize?.takeIf { it > 0L } ?: file.size
        if (size > 0L) appendLine("Size: ${formatFileSize(size)}")
        appendLine("Path: ${file.path}")
        indexEntry?.plot?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine(it)
        }
    }

    fun recentDetails(record: ProgressRecord): String = buildString {
        appendLine("Name: ${record.episodeId.substringAfterLast('/').substringAfterLast('\\')}")
        appendLine("Resume: ${formatPlaybackPosition(record.positionMs)}")
        appendLine("Play count: ${record.playCount}")
        appendLine("Path: ${record.episodeId}")
    }
}

internal object DesktopBangumiSearchPresenter {
    fun displayResults(query: String, results: List<ScraperResult>): String = buildString {
        appendLine("Bangumi matches for \"$query\"")
        results.forEachIndexed { index, result ->
            appendLine(
                "${index + 1}. ${result.title}${result.titleCn?.let { " / $it" }.orEmpty()} " +
                    "id=${result.animeId} confidence=${(result.confidence * 100).roundToLong()}%"
            )
        }
    }

    fun details(result: ScraperResult): String = buildString {
        appendLine("ID: ${result.animeId}")
        appendLine("Title: ${result.title}")
        result.titleCn?.takeIf { it.isNotBlank() }?.let { appendLine("Chinese title: $it") }
        appendLine("Matched title: ${result.matchedTitle}")
        appendLine("Confidence: ${(result.confidence * 100).roundToLong()}%")
        appendLine("Source: ${result.source.name}")
    }
}

internal typealias DesktopBangumiBatchMatch = MetadataBatchMatch
internal typealias DesktopBangumiBatchUpdate = MetadataBatchUpdate
internal typealias DesktopBangumiBatchConflict = MetadataBatchConflict
internal typealias DesktopBangumiBatchPlan = MetadataBatchPlan

internal object DesktopBangumiBatchPresenter {
    fun queriesFor(entries: List<MediaIndexEntry>): List<String> =
        MetadataBatchPlanner.queriesFor(entries)

    fun acceptedMatches(matches: List<DesktopBangumiBatchMatch>): List<DesktopBangumiBatchMatch> =
        MetadataBatchPlanner.acceptedMatches(matches)

    fun planFor(
        entries: List<MediaIndexEntry>,
        matches: List<DesktopBangumiBatchMatch>,
    ): DesktopBangumiBatchPlan {
        return MetadataBatchPlanner.planFor(entries, matches)
    }

    fun displayPreview(matches: List<DesktopBangumiBatchMatch>): String =
        MetadataBatchPlanner.displayPreview(matches)

    fun displayPlanSummary(plan: DesktopBangumiBatchPlan): String =
        MetadataBatchPlanner.displayPlanSummary(plan)
}
