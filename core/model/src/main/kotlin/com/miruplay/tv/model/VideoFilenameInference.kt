package com.miruplay.tv.model

data class VideoFilenameMetadata(
    val title: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
)

object VideoFilenameInference {
    fun infer(
        fileName: String,
        parentName: String? = null,
        ignoreGenericParent: Boolean = true,
    ): VideoFilenameMetadata {
        val stem = fileName.substringBeforeLast('.', fileName)
        val withoutGroup = stem.replace(leadingReleaseGroupRegex, "")
        val withoutTags = tagRegex.replace(withoutGroup, " ")
        val episodeMatch = seasonEpisodeRegex.find(withoutTags) ?: episodeNumberRegex.findAll(withoutTags).lastOrNull()
        val titlePart = episodeMatch
            ?.let { withoutTags.substring(0, it.range.first) }
            ?: withoutTags
        val parentTitle = parentName
            ?.let(::cleanupTitle)
            ?.takeUnless { ignoreGenericParent && it.isGenericContextName() }
            .orEmpty()
        val title = cleanupTitle(titlePart)
            .ifBlank { parentTitle }
            .ifBlank { "Unknown" }

        return VideoFilenameMetadata(
            title = title,
            seasonNumber = inferSeason(fileName),
            episodeNumber = inferEpisode(fileName),
        )
    }

    private fun inferSeason(fileName: String): Int? {
        val stem = fileName.substringBeforeLast('.', fileName)
        return seasonEpisodeRegex.find(stem)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun inferEpisode(fileName: String): Int? {
        val stem = fileName.substringBeforeLast('.', fileName)
        seasonEpisodeRegex.find(stem)?.groupValues?.getOrNull(2)?.toIntOrNull()?.let { return it }
        return episodeNumberRegex.findAll(stem).lastOrNull()?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun cleanupTitle(value: String): String =
        value.replace(Regex("""[_・]+"""), " ")
            .replace(Regex("""\s*[-–—]\s*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun String.isGenericContextName(): Boolean =
        lowercase()
            .replace(Regex("""[._\-\[\]【】()（）]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim() in GENERIC_CONTEXT_NAMES

    private val GENERIC_CONTEXT_NAMES = setOf(
        "115open",
        "ani",
        "anime",
        "anime library",
        "download",
        "downloads",
        "library",
        "media",
        "video",
        "videos",
        "动漫",
        "下载",
        "下載",
    )
    private val leadingReleaseGroupRegex = Regex("""^\s*(?:\[[^\]]+]|【[^】]+】|\([^)]+\))\s*""")
    private val tagRegex = Regex("""[\[\(【][^\]\)】]{1,64}[\]\)】]""")
    private val seasonEpisodeRegex = Regex("""(?i)(?:^|[\s._-])S(\d{1,2})E(\d{1,3})(?:[\s._-]|$)""")
    private val episodeNumberRegex = Regex("""(?i)(?:^|[\s._-])(?:EP?)?(\d{1,4})(?:v\d+)?(?:[\s._-]|$)""")
}
