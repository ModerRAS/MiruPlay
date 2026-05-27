package com.miruplay.tv.scanner

import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.model.FilenameParseResult

/**
 * Classifies video paths into a stable show id/title, season number, and episode number.
 *
 * The scanner uses the returned show name as the library/cache key. Season suffixes such as
 * "Season 2", "S02", "II", and "第2季" are folded into seasonNumber so multi-season shows
 * stay under one library entry.
 */
class VideoDirectoryClassifier(
    private val episodeDetector: EpisodeDetector,
    private val filenameMetadataParser: FilenameMetadataParser? = null,
    private val filenameOnly: Boolean = false,
) {
    private val parsedTextCache = mutableMapOf<String, FilenameParseResult?>()

    fun classifyVideo(path: String, fileName: String): VideoClassification {
        val release = ReleaseFilenameParser.parse(fileName)
        val segments = pathSegments(path)
        val parentSegments = if (segments.lastOrNull() == fileName) {
            segments.dropLast(1)
        } else {
            segments.dropLast(1).ifEmpty { segments }
        }
        val detectorMatch = episodeDetector.detectEpisode(fileName)
        val showContext = findShowContext(parentSegments, seasonFolder)
        val fileParsed = parseMetadata(stripVideoExtension(fileName))
        val folderContexts = parentSegments
            .takeLast(maxParsedContextSegments)
            .asReversed()
            .filter { it.shouldParseContextSegment() }
            .mapIndexedNotNull { distance, rawName ->
                parseMetadata(rawName)?.let { parsed ->
                    evidenceFromParsed(
                        parsed = parsed,
                        source = EvidenceSource.FOLDER_BERT,
                        distance = distance
                    )
                }
            }
        val evidence = buildList {
            release?.let { add(evidenceFromRelease(it)) }
            fileParsed?.let { add(evidenceFromParsed(it, EvidenceSource.FILE_BERT, 0)) }
            folderContexts.forEach(::add)
            seasonFolder?.let {
                add(
                    ClassificationEvidence(
                        title = null,
                        normalizedTitle = null,
                        seasonNumber = it.seasonNumber,
                        episodeNumber = null,
                        score = seasonFolderScore
                    )
                )
            }
            showContext?.let { add(evidenceFromShowContext(it)) }
            detectorMatch?.let { add(evidenceFromDetector(it)) }
        }

        val titleSelection = chooseTitleSelection(evidence)
        val season = chooseNumber(evidence, titleSelection.normalizedTitle) { it.seasonNumber } ?: 1
        val episode = chooseNumber(evidence, titleSelection.normalizedTitle) { it.episodeNumber }

        return VideoClassification(
            animeName = titleSelection.title,
            seasonNumber = season,
            episodeNumber = episode,
            titleCandidates = titleSelection.candidates
        )
    }

    fun classifyNfo(path: String): NfoClassification {
        val segments = pathSegments(path)
        val parentSegments = segments.dropLast(1)
        val seasonFolder = findSeasonFolder(parentSegments)
        val showContext = findShowContext(parentSegments, seasonFolder)
        val parentName = if (seasonFolder != null) {
            parentSegments.getOrNull(seasonFolder.index - 1)
        } else {
            parentSegments.lastOrNull()
        }.orEmpty()

        val split = splitSeriesAndSeason(showContext?.rawName ?: parentName)
        return NfoClassification(
            animeName = firstUsableName(showContext?.seriesName, split.seriesName, parentName),
            seasonNumber = seasonFolder?.seasonNumber ?: split.seasonNumber ?: 1
        )
    }

    fun showRootForVideo(path: String): String? {
        val normalized = path.replace('\\', '/')
        val segments = normalized.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null

        val parentSegments = segments.dropLast(1)
        val seasonFolder = findSeasonFolder(parentSegments)
        val candidateSegments = if (seasonFolder != null) {
            parentSegments.take(seasonFolder.index)
        } else {
            parentSegments
        }
        val showContext = findShowContext(candidateSegments) ?: return null
        val showSegments = candidateSegments.take(showContext.index + 1)
        if (showSegments.isEmpty()) return null

        val prefix = when {
            normalized.startsWith("/") -> "/"
            normalized.length > 2 && normalized[1] == ':' -> ""
            else -> ""
        }
        return prefix + showSegments.joinToString("/")
    }

    private fun findSeasonFolder(segments: List<String>): SeasonFolder? {
        for (index in segments.indices.reversed()) {
            val season = parseSeasonFolder(segments[index])
            if (season != null) return SeasonFolder(index, season)
        }
        return null
    }

    private fun parseSeasonFolder(name: String): Int? {
        val trimmed = name.trim()
        seasonFolderPatterns.forEach { regex ->
            val match = regex.matchEntire(trimmed) ?: return@forEach
            return parseSeasonNumber(match.groupValues[1])
        }
        return null
    }

    private fun pathSegments(path: String): List<String> =
        path.replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun firstUsableName(vararg candidates: String?): String =
        candidates.firstNotNullOfOrNull { it.usableName() } ?: "Unknown"

    private fun parseMetadata(text: String): FilenameParseResult? {
        val parser = filenameMetadataParser ?: return null
        val normalized = text.trim()
        if (normalized.isBlank()) return null
        return parsedTextCache.getOrPut(normalized) {
            runCatching { parser.parse(normalized, maxParsedTextLength) }.getOrNull()
        }
    }

    private fun chooseTitleSelection(evidence: List<ClassificationEvidence>): TitleSelection {
        val titleScores = linkedMapOf<String, Int>()
        evidence.forEach { candidate ->
            val normalized = candidate.normalizedTitle ?: return@forEach
            titleScores[normalized] = (titleScores[normalized] ?: 0) + candidate.score
        }

        if (titleScores.isEmpty()) {
            return TitleSelection(
                title = "Unknown",
                normalizedTitle = null,
                candidates = emptyList()
            )
        }

        val ordered = titleScores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        val selectedKey = ordered.first()
        return TitleSelection(
            title = selectedKey,
            normalizedTitle = selectedKey,
            candidates = ordered.distinct()
        )
    }

    private fun chooseNumber(
        evidence: List<ClassificationEvidence>,
        normalizedTitle: String?,
        selector: (ClassificationEvidence) -> Int?
    ): Int? {
        fun bestFrom(candidates: List<ClassificationEvidence>): Int? =
            candidates
                .mapNotNull { candidate -> selector(candidate)?.let { value -> candidate to value } }
                .maxByOrNull { it.first.score }
                ?.second

        val preferred = normalizedTitle?.let { title ->
            evidence.filter { it.normalizedTitle == title }
        }.orEmpty()
        return bestFrom(preferred) ?: bestFrom(evidence)
    }

    private fun String.normalizedTitle(): String? =
        replace(Regex("""[._]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            ?.let(::splitSeriesAndSeason)
            ?.seriesName
            ?.replace(Regex("""[._]+"""), " ")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && !it.isGenericContextName() }

    private fun String?.usableName(): String? =
        this
            ?.replace(Regex("""[._]+"""), " ")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            ?.takeUnless { it.isGenericContextName() }

    private fun stripVideoExtension(fileName: String): String =
        fileName.replace(videoExtensionRegex, "")

    private fun modelPathText(path: String, fileName: String): String {
        val normalized = path.replace('\\', '/').trim()
        if (normalized.isBlank()) return fileName
        if (normalized.length <= maxParsedTextLength) return normalized

        val segments = pathSegments(normalized)
        val tail = segments.takeLast(maxParsedContextSegments + 1).joinToString("/")
        return tail
            .takeIf { it.isNotBlank() }
            ?.takeLast(maxParsedTextLength)
            ?: normalized.takeLast(maxParsedTextLength)
    }

    private data class SeasonFolder(val index: Int, val seasonNumber: Int)
    private data class ShowContext(
        val index: Int,
        val rawName: String,
        val seriesName: String,
        val seasonNumber: Int?,
        val episodeNumber: Int?
    )

    private fun evidenceFromRelease(release: ReleaseFileMatch): ClassificationEvidence =
        ClassificationEvidence(
            title = firstUsableName(release.seriesName, release.animeName).takeIf { it != "Unknown" },
            normalizedTitle = firstUsableName(release.seriesName, release.animeName).normalizedTitle(),
            seasonNumber = release.seasonNumber,
            episodeNumber = release.episodeNumber,
            score = releaseScore + release.seasonNumber.scoreBonus() + release.episodeNumber.scoreBonus()
        )

    private fun evidenceFromParsed(
        parsed: FilenameParseResult,
        source: EvidenceSource,
        distance: Int,
    ): ClassificationEvidence {
        val title = parsed.title?.takeIf { it.isNotBlank() }
        return ClassificationEvidence(
            title = title,
            normalizedTitle = title?.normalizedTitle(),
            seasonNumber = parsed.season,
            episodeNumber = parsed.episode,
            score = source.baseScore - distance * source.distancePenalty +
                parsed.season.scoreBonus() + parsed.episode.scoreBonus()
        )
    }

    private fun evidenceFromShowContext(showContext: ShowContext): ClassificationEvidence =
        ClassificationEvidence(
            title = showContext.seriesName,
            normalizedTitle = showContext.seriesName.normalizedTitle(),
            seasonNumber = showContext.seasonNumber,
            episodeNumber = showContext.episodeNumber,
            score = showContextScore +
                showContext.seasonNumber.scoreBonus() +
                showContext.episodeNumber.scoreBonus()
        )

    private fun evidenceFromDetector(match: EpisodeMatch): ClassificationEvidence =
        ClassificationEvidence(
            title = match.animeName,
            normalizedTitle = match.animeName?.normalizedTitle(),
            seasonNumber = match.seasonNumber.takeIf { it > 0 },
            episodeNumber = match.episodeNumber,
            score = detectorScore +
                match.seasonNumber.scoreBonus() +
                match.episodeNumber.scoreBonus()
        )

    private data class ClassificationEvidence(
        val title: String?,
        val normalizedTitle: String?,
        val seasonNumber: Int?,
        val episodeNumber: Int?,
        val score: Int,
    )

    private data class TitleSelection(
        val title: String,
        val normalizedTitle: String?,
        val candidates: List<String>
    )

    private enum class EvidenceSource(
        val baseScore: Int,
        val distancePenalty: Int
    ) {
        FILE_BERT(510, 0),
        FOLDER_BERT(640, 20)
    }

    private fun Int?.scoreBonus(): Int =
        if (this == null) 0 else 20

    private fun String.shouldParseContextSegment(): Boolean =
        usableName() != null || numericContextPatterns.any { it.matches(normalizedContextName()) }

    private fun findShowContext(
        segments: List<String>,
        seasonFolder: SeasonFolder? = null
    ): ShowContext? {
        val candidateSegments = if (seasonFolder != null) {
            segments.take(seasonFolder.index)
        } else {
            segments
        }
        for (index in candidateSegments.indices.reversed()) {
            val rawName = candidateSegments[index]
            val release = ReleaseFilenameParser.parse(rawName)
            if (release != null) {
                val seriesName = release.seriesName.usableName()
                if (seriesName != null) {
                    return ShowContext(index, rawName, seriesName, release.seasonNumber, release.episodeNumber)
                }
            }
            val split = splitSeriesAndSeason(rawName)
            val seriesName = split.seriesName.usableName() ?: continue
            return ShowContext(index, rawName, seriesName, split.seasonNumber, null)
        }
        return null
    }

    private fun String.isGenericContextName(): Boolean {
        val normalized = normalizedContextName()
        if (normalized in genericContextNames) return true
        return genericContextPatterns.any { it.matches(normalized) }
    }

    private fun String.normalizedContextName(): String =
        lowercase()
            .replace(Regex("""[._\-\[\]【】()（）]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    companion object {
        private const val maxParsedTextLength = 128
        private const val maxParsedContextSegments = 6
        private const val releaseScore = 520
        private const val showContextScore = 620
        private const val detectorScore = 120
        private const val seasonFolderScore = 360
        private val videoExtensionRegex = Regex("""(?i)\.(mkv|mp4|avi|mov|wmv|flv|webm|m4v|mpg|mpeg|ts|m2ts)$""")
        private val seasonFolderPatterns = listOf(
            Regex("""(?i)season\s*(\d{1,2})"""),
            Regex("""(?i)s(\d{1,2})"""),
            Regex("""第\s*([0-9一二三四五六七八九十]+)\s*[季期]""")
        )
        private val genericContextNames = setOf(
            "115open",
            "ani",
            "anime",
            "anime library",
            "animation",
            "download",
            "downloads",
            "downloads ani",
            "raw",
            "raws",
            "library",
            "media",
            "media library",
            "video",
            "videos",
            "video library",
            "影视",
            "影音",
            "动漫",
            "動畫",
            "下载",
            "下載",
            "单集",
            "單集",
            "合集",
            "全集",
            "特典",
            "番外",
            "正片",
            "season",
            "seasons",
            "series",
            "episode",
            "episodes",
            "ep",
            "ova",
            "oad",
            "sp",
            "sps",
            "special",
            "specials",
            "extra",
            "extras",
            "movie",
            "movies",
            "film",
            "films",
            "tvsp"
        )
        private val genericContextPatterns = listOf(
            Regex("""(?i)^(?:season|series|s)\s*\d{1,2}$"""),
            Regex("""(?i)^(?:final|last)\s+season$"""),
            Regex("""(?i)^(?:ep|episode|part)\s*[\d一二三四五六七八九十]+(?:[a-z])?$""")
        )
        private val numericContextPatterns = listOf(
            Regex("""(?i)^(?:season|series|s)\s*\d{1,2}$"""),
            Regex("""(?i)^(?:ep|episode|part)\s*[\d一二三四五六七八九十]+(?:[a-z])?$""")
        )
    }
}

data class VideoClassification(
    val animeName: String,
    val seasonNumber: Int,
    val episodeNumber: Int?,
    val titleCandidates: List<String> = emptyList()
)

data class NfoClassification(
    val animeName: String,
    val seasonNumber: Int
)

private data class ReleaseFileMatch(
    val animeName: String,
    val seriesName: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?
)

private object ReleaseFilenameParser {
    private val leadingGroupRegex = Regex("""^(?:\[[^\]]+]|【[^】]+】|\([^)]+\))\s*""")
    private val extensionRegex = Regex("""(?i)\.(mkv|mp4|avi|mov|wmv|flv|webm|m4v|mpg|mpeg|ts|m2ts)$""")
    private val sxeRegex = Regex("""(?i)\bS(\d{1,2})E(\d{1,4})\b""")
    private val bracketTitleSxeRegex = Regex("""^(?:\[[^\]]+]|【[^】]+】|\([^)]+\))\s*(?:\[(?<title>[^\]]+?)]|【(?<titleCjk>[^】]+?)】|\((?<titleParen>[^)]+?)\))(?<tail>.*)$""")
    private val bracketTitleEpisodeRegex = Regex("""^(?:\[[^\]]+]|【[^】]+】|\([^)]+\))\s*(?:\[(?<title>[^\]]+?)]|【(?<titleCjk>[^】]+?)】|\((?<titleParen>[^)]+?)\))\s*(?:\[(?<ep>\d{1,4}(?:-\d{1,4})?)]|【(?<epCjk>\d{1,4}(?:-\d{1,4})?)】|\((?<epParen>\d{1,4}(?:-\d{1,4})?)\))(?<tail>.*)$""")
    private val compactBracketEpisodeRegex = Regex("""^(?:\[[^\]]+]|【[^】]+】|\([^)]+\))\s*(?<title>.+?)\s*(?:\[(?<ep>\d{1,4}(?:-\d{1,4})?)]|【(?<epCjk>\d{1,4}(?:-\d{1,4})?)】|\((?<epParen>\d{1,4}(?:-\d{1,4})?)\))(?<tail>.*)$""")
    private val dashEpisodeRegex = Regex("""(?i)\s+-\s+(?:ep?\s*)?(\d{1,4})(?:\.\d+)?(?:\s|$|\[|【|\()""")
    private val epEpisodeRegex = Regex("""(?i)(?:^|[\s._\-\[])(?:ep|episode)\.?\s*(\d{1,4})(?:\.\d+)?(?:\s|$|[\]【】\(\)])""")
    private val bracketEpisodeRegex = Regex("""\[(\d{1,4})]""")
    private val trailingTagRegex = Regex("""\s*(?:\[[^\]]*]|【[^】]*】|\([^)]*\)|（[^）]*）)+\s*$""")
    private val trailingBracketTagsRegex = Regex("""^\s*(?:\[[^\]]*]|【[^】]*】|\([^)]*\)|（[^）]*）)*\s*$""")

    fun parse(fileName: String): ReleaseFileMatch? {
        val withoutExtension = fileName.replace(extensionRegex, "")
        val withoutGroup = withoutExtension.replace(leadingGroupRegex, "").trim()
        if (withoutGroup.isBlank()) return null

        sxeRegex.find(withoutGroup)?.let { match ->
            val title = withoutGroup.substring(0, match.range.first)
                .trimReleaseTitle()
            if (title.isNotBlank()) {
                val split = splitSeriesAndSeason(title)
                return ReleaseFileMatch(
                    animeName = title,
                    seriesName = split.seriesName,
                    seasonNumber = split.seasonNumber ?: match.groupValues[1].toIntOrNull(),
                    episodeNumber = match.groupValues[2].toIntOrNull()
                )
            }
        }

        bracketTitleSxeRegex.matchEntire(withoutExtension)?.let { match ->
            val title = match.bracketTitle()
            val tail = match.groups["tail"]?.value.orEmpty()
            sxeRegex.find(tail)?.let { sxe ->
                val split = splitSeriesAndSeason(title.trimReleaseTitle())
                return ReleaseFileMatch(
                    animeName = title.trimReleaseTitle(),
                    seriesName = split.seriesName,
                    seasonNumber = split.seasonNumber ?: sxe.groupValues[1].toIntOrNull(),
                    episodeNumber = sxe.groupValues[2].toIntOrNull()
                )
            }
        }

        bracketTitleEpisodeRegex.matchEntire(withoutExtension)?.let { match ->
            val title = match.bracketTitle().trimReleaseTitle()
            val episode = match.bracketEpisodeNumber()
            if (title.isNotBlank() && match.trailingTagsAreSafe()) {
                val split = splitSeriesAndSeason(title)
                return ReleaseFileMatch(
                    animeName = title,
                    seriesName = split.seriesName,
                    seasonNumber = split.seasonNumber,
                    episodeNumber = episode
                )
            }
        }

        compactBracketEpisodeRegex.matchEntire(withoutExtension)?.let { match ->
            val title = match.groups["title"]?.value.orEmpty().trimReleaseTitle()
            val episode = match.bracketEpisodeNumber()
            if (title.isNotBlank() && match.trailingTagsAreSafe()) {
                val split = splitSeriesAndSeason(title)
                return ReleaseFileMatch(
                    animeName = title,
                    seriesName = split.seriesName,
                    seasonNumber = split.seasonNumber,
                    episodeNumber = episode
                )
            }
        }

        dashEpisodeRegex.findAll(withoutGroup).lastOrNull()?.let { match ->
            val title = withoutGroup.substring(0, match.range.first)
                .trimReleaseTitle()
            if (title.isNotBlank()) {
                val split = splitSeriesAndSeason(title)
                return ReleaseFileMatch(
                    animeName = title,
                    seriesName = split.seriesName,
                    seasonNumber = split.seasonNumber,
                    episodeNumber = match.groupValues[1].toIntOrNull()
                )
            }
        }

        epEpisodeRegex.findAll(withoutGroup).lastOrNull()?.let { match ->
            val title = withoutGroup.substring(0, match.range.first)
                .trimReleaseTitle()
            if (title.isNotBlank()) {
                val split = splitSeriesAndSeason(title)
                return ReleaseFileMatch(
                    animeName = title,
                    seriesName = split.seriesName,
                    seasonNumber = split.seasonNumber,
                    episodeNumber = match.groupValues[1].toIntOrNull()
                )
            }
        }

        bracketEpisodeRegex.findAll(withoutGroup).lastOrNull()?.let { match ->
            val title = withoutGroup.substring(0, match.range.first)
                .trimReleaseTitle()
            if (title.isNotBlank()) {
                val split = splitSeriesAndSeason(title)
                return ReleaseFileMatch(
                    animeName = title,
                    seriesName = split.seriesName,
                    seasonNumber = split.seasonNumber,
                    episodeNumber = match.groupValues[1].toIntOrNull()
                )
            }
        }

        return null
    }

    private fun String.trimReleaseTitle(): String =
        replace(trailingTagRegex, "")
            .replace(Regex("""[._]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '_', '.')

    private fun MatchResult.bracketTitle(): String =
        groups["title"]?.value
            ?: groups["titleCjk"]?.value
            ?: groups["titleParen"]?.value
            ?: ""

    private fun MatchResult.bracketEpisodeNumber(): Int? =
        (groups["ep"]?.value ?: groups["epCjk"]?.value ?: groups["epParen"]?.value)
            ?.takeUnless { it.contains('-') }
            ?.substringBefore('-')
            ?.toIntOrNull()

    private fun MatchResult.trailingTagsAreSafe(): Boolean {
        val tail = groups["tail"]?.value.orEmpty()
        return tail.isBlank() || tail.matches(trailingBracketTagsRegex)
    }
}

data class SeriesSeason(
    val seriesName: String,
    val seasonNumber: Int?
)

fun splitSeriesAndSeason(name: String): SeriesSeason {
    val trimmed = name.trim()
    for (pattern in seasonSuffixPatterns) {
        val match = pattern.matchEntire(trimmed) ?: continue
        val title = match.groups["title"]?.value?.trim().orEmpty()
        val season = match.groups["num"]?.value?.let { parseSeasonNumber(it) }
        if (title.isNotBlank() && season != null) {
            return SeriesSeason(title, season)
        }
    }
    return SeriesSeason(trimmed, null)
}

private val seasonSuffixPatterns = listOf(
    Regex("""(?i)^(?<title>.+?)\s+season\s*(?<num>\d{1,2})$"""),
    Regex("""(?i)^(?<title>.+?)\s+s(?<num>\d{1,2})$"""),
    Regex("""(?i)^(?<title>.+?)\s+(?<num>\d{1,2})(?:st|nd|rd|th)\s+season$"""),
    // NEW: Parenthesized Chinese season, e.g., "一拳超人(第三季)", "某某番（第二季）"
    Regex("""^(?<title>.+?)\s*[（(]\s*第(?<num>\d{1,2}|[一二三四五六七八九十]+)\s*[季期]\s*[)）]"""),
    // MODIFIED: Chinese season suffix — no longer requires end anchor, allows trailing text
    // e.g., "歡迎來到實力至上主義的教室 第四季 2年級篇 第一學期"
    // IMPORTANT: .*$ is REQUIRED at the end because splitSeriesAndSeason uses matchEntire()
    // which must consume ALL characters in the string
    Regex("""^(?<title>.+?)\s*第(?<num>\d{1,2}|[一二三四五六七八九十]+)[季期].*$"""),
    Regex("""^(?<title>.+?)\s+(?<num>II|III|IV|V|VI|VII|VIII|IX|X|貳|贰|弐|二期|三期|四期)$"""),
    Regex("""^(?<title>.+?)\s+(?<num>[2-9]\d*)$""")
)

private fun parseSeasonNumber(raw: String): Int? {
    val normalized = raw.trim()
    normalized.toIntOrNull()?.let { return it }
    return when (normalized.uppercase()) {
        "II" -> 2
        "III" -> 3
        "IV" -> 4
        "V" -> 5
        "VI" -> 6
        "VII" -> 7
        "VIII" -> 8
        "IX" -> 9
        "X" -> 10
        else -> when (normalized) {
            "貳", "贰", "弐", "二期" -> 2
            "三期" -> 3
            "四期" -> 4
            else -> parseCjkNumber(normalized)
        }
    }
}

private fun parseCjkNumber(raw: String): Int? {
    var total = 0
    var current = 0
    for (char in raw) {
        when (char) {
            '一' -> current += 1
            '二' -> current += 2
            '三' -> current += 3
            '四' -> current += 4
            '五' -> current += 5
            '六' -> current += 6
            '七' -> current += 7
            '八' -> current += 8
            '九' -> current += 9
            '十' -> {
                total += if (current == 0) 10 else current * 10
                current = 0
            }
            else -> return null
        }
    }
    return total + current
}
