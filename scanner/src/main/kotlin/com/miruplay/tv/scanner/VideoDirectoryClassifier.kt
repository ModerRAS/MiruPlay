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
    private val filenameMetadataParser: FilenameMetadataParser? = null
) {
    fun classifyVideo(path: String, fileName: String): VideoClassification {
        val release = ReleaseFilenameParser.parse(fileName)
        val segments = pathSegments(path)
        val parentSegments = if (segments.lastOrNull() == fileName) {
            segments.dropLast(1)
        } else {
            segments.dropLast(1).ifEmpty { segments }
        }
        val seasonFolder = findSeasonFolder(parentSegments)
        val detectorMatch = episodeDetector.detectEpisode(fileName)
        var parsedFilename: FilenameParseResult? = null

        fun parsed(): FilenameParseResult? {
            val parser = filenameMetadataParser ?: return null
            if (parsedFilename == null) {
                parsedFilename = runCatching {
                    parser.parse(stripVideoExtension(fileName))
                }.getOrNull()
            }
            return parsedFilename
        }

        if (release != null) {
            val showContext = findShowContext(parentSegments, seasonFolder)
            val parsed = if (
                release.seriesName.isBlank() ||
                release.seasonNumber == null ||
                release.episodeNumber == null
            ) {
                parsed()
            } else {
                null
            }
            val season = release.seasonNumber ?: parsed?.season ?: seasonFolder?.seasonNumber ?: 1
            return VideoClassification(
                animeName = firstUsableName(
                    release.seriesName,
                    showContext?.seriesName,
                    parsed?.title,
                    detectorMatch?.animeName
                ),
                seasonNumber = season,
                episodeNumber = release.episodeNumber ?: parsed?.episode ?: detectorMatch?.episodeNumber
            )
        }

        if (seasonFolder != null) {
            val showContext = findShowContext(parentSegments, seasonFolder)
            val parsed = if (showContext == null || detectorMatch?.episodeNumber == null) parsed() else null
            return VideoClassification(
                animeName = firstUsableName(showContext?.seriesName, parsed?.title, detectorMatch?.animeName),
                seasonNumber = seasonFolder.seasonNumber,
                episodeNumber = detectorMatch?.episodeNumber ?: parsed?.episode
            )
        }

        val parsed = parsed()
        val showContext = findShowContext(parentSegments)
        val fallbackName = firstUsableName(parsed?.title, showContext?.seriesName, detectorMatch?.animeName)

        return VideoClassification(
            animeName = fallbackName,
            seasonNumber = parsed?.season
                ?: showContext?.seasonNumber
                ?: detectorMatch?.seasonNumber
                ?: 1,
            episodeNumber = parsed?.episode ?: detectorMatch?.episodeNumber ?: showContext?.episodeNumber
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

    private fun String?.usableName(): String? =
        this
            ?.replace(Regex("""[._]+"""), " ")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            ?.takeUnless { it.isGenericContextName() }

    private fun stripVideoExtension(fileName: String): String =
        fileName.replace(videoExtensionRegex, "")

    private data class SeasonFolder(val index: Int, val seasonNumber: Int)
    private data class ShowContext(
        val index: Int,
        val rawName: String,
        val seriesName: String,
        val seasonNumber: Int?,
        val episodeNumber: Int?
    )

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
        val normalized = lowercase()
            .replace(Regex("""[._\-\[\]【】()（）]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalized in genericContextNames) return true
        return genericContextPatterns.any { it.matches(normalized) }
    }

    companion object {
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
    }
}

data class VideoClassification(
    val animeName: String,
    val seasonNumber: Int,
    val episodeNumber: Int?
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
