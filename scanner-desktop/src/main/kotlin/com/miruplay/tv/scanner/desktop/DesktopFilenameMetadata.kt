package com.miruplay.tv.scanner.desktop

internal object DesktopFilenameMetadata {
    private val bracketToken = Regex("""[\[\(【][^\]\)】]{1,64}[\]\)】]""")
    private val seasonEpisode = Regex("""(?i)(?:^|[\s._-])S(\d{1,2})E(\d{1,3})(?:[\s._-]|$)""")
    private val episodeNumber = Regex("""(?i)(?:^|[\s._-])(?:EP?)?(\d{1,4})(?:v\d+)?(?:[\s._-]|$)""")

    fun inferAnimeName(fileName: String, parentName: String): String {
        val stem = fileName.substringBeforeLast('.', fileName)
        val withoutGroup = stem.replace(Regex("""^\[[^\]]+]"""), "")
        val withoutTags = bracketToken.replace(withoutGroup, " ")
        val episodeMatch = seasonEpisode.find(withoutTags) ?: episodeNumber.findAll(withoutTags).lastOrNull()
        val titlePart = episodeMatch
            ?.let { withoutTags.substring(0, it.range.first) }
            ?: withoutTags
        return cleanupTitle(titlePart).ifBlank { cleanupTitle(parentName).ifBlank { parentName } }
    }

    fun inferSeason(fileName: String): Int? {
        val stem = fileName.substringBeforeLast('.', fileName)
        return seasonEpisode.find(stem)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun inferEpisode(fileName: String): Int? {
        val stem = fileName.substringBeforeLast('.', fileName)
        seasonEpisode.find(stem)?.groupValues?.getOrNull(2)?.toIntOrNull()?.let { return it }
        return episodeNumber.findAll(stem).lastOrNull()?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun cleanupTitle(value: String): String =
        value.replace(Regex("""[_・]+"""), " ")
            .replace(Regex("""\s*[-–—]\s*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
