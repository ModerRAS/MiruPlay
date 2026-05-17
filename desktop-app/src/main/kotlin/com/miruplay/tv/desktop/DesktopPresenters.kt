package com.miruplay.tv.desktop

import com.miruplay.tv.model.FileEntry
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.ProgressRecord
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.model.SubtitleFormat
import com.miruplay.tv.model.SubtitleTrack
import com.miruplay.tv.model.displayTitle
import com.miruplay.tv.model.formatFileSize
import com.miruplay.tv.model.formatPlaybackPosition
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

internal data class DesktopBangumiBatchMatch(
    val query: String,
    val result: ScraperResult? = null,
    val candidates: List<ScraperResult> = result?.let { listOf(it) }.orEmpty(),
)

internal data class DesktopBangumiBatchUpdate(
    val query: String,
    val original: MediaIndexEntry,
    val updated: MediaIndexEntry,
    val result: ScraperResult,
)

internal data class DesktopBangumiBatchConflict(
    val query: String,
    val entry: MediaIndexEntry,
)

internal data class DesktopBangumiBatchPlan(
    val readyUpdates: List<DesktopBangumiBatchUpdate>,
    val reviewMatches: List<DesktopBangumiBatchMatch>,
    val conflicts: List<DesktopBangumiBatchConflict>,
)

internal object DesktopBangumiBatchPresenter {
    private const val READY_CONFIDENCE = 0.85f

    fun queriesFor(entries: List<MediaIndexEntry>): List<String> =
        entries
            .mapNotNull(::queryFor)
            .distinct()

    fun acceptedMatches(matches: List<DesktopBangumiBatchMatch>): List<DesktopBangumiBatchMatch> =
        matches.filter { (it.result?.confidence ?: 0f) >= READY_CONFIDENCE }

    fun planFor(
        entries: List<MediaIndexEntry>,
        matches: List<DesktopBangumiBatchMatch>,
    ): DesktopBangumiBatchPlan {
        val readyUpdates = mutableListOf<DesktopBangumiBatchUpdate>()
        val reviewMatches = mutableListOf<DesktopBangumiBatchMatch>()
        val conflicts = mutableListOf<DesktopBangumiBatchConflict>()
        matches.forEach { match ->
            val result = match.result
            if (result == null || result.confidence < READY_CONFIDENCE) {
                reviewMatches += match
                return@forEach
            }
            val matchingEntries = entries.filter { queryFor(it) == match.query }
            if (matchingEntries.any(::hasExternalMetadata)) {
                conflicts += matchingEntries.map { DesktopBangumiBatchConflict(match.query, it) }
                return@forEach
            }
            readyUpdates += matchingEntries.map { entry ->
                DesktopBangumiBatchUpdate(
                    query = match.query,
                    original = entry,
                    updated = entry.copy(
                        animeName = result.displayTitle(),
                        metadataSource = result.source.name,
                        metadataId = result.animeId,
                        metadataTitle = result.displayTitle(),
                    ),
                    result = result,
                )
            }
        }
        return DesktopBangumiBatchPlan(
            readyUpdates = readyUpdates,
            reviewMatches = reviewMatches,
            conflicts = conflicts,
        )
    }

    fun displayPreview(matches: List<DesktopBangumiBatchMatch>): String = buildString {
        matches.forEach { match ->
            val result = match.result
            val status = if ((result?.confidence ?: 0f) >= READY_CONFIDENCE) "ready" else "review"
            append(match.query)
            append(": ")
            append(result?.let(::displayCandidate) ?: "No match")
            append(" [$status]")
            if (match.candidates.size > 1) append(" candidates=${match.candidates.size}")
            appendLine()
        }
    }

    fun displayPlanSummary(plan: DesktopBangumiBatchPlan): String =
        "${plan.readyUpdates.size} ready, ${plan.reviewMatches.size} review, ${plan.conflicts.size} conflicts"

    private fun queryFor(entry: MediaIndexEntry): String? =
        entry.animeName?.takeIf { it.isNotBlank() }
            ?: entry.metadataTitle?.takeIf { it.isNotBlank() }
            ?: entry.path.let { path ->
                val fileName = path.substringAfterLast('/').substringAfterLast('\\')
                fileName.substringBeforeLast('.', fileName)
            }
                .takeIf { it.isNotBlank() }

    private fun displayCandidate(result: ScraperResult): String =
        result.title + result.titleCn?.takeIf { it.isNotBlank() }?.let { " / $it" }.orEmpty()

    private fun hasExternalMetadata(entry: MediaIndexEntry): Boolean =
        !entry.metadataSource.isNullOrBlank() ||
            !entry.metadataId.isNullOrBlank() ||
            !entry.metadataTitle.isNullOrBlank()
}
