package com.miruplay.tv.scanner

/**
 * Registry for custom episode naming patterns
 */
interface NamedPattern {
    val name: String
    val description: String
    val priority: Int = 0
    
    fun matches(fileName: String): Boolean
    fun extract(fileName: String): EpisodeMatch?
}

/**
 * Common episode naming patterns
 */
class StandardEpisodePattern : NamedPattern {
    override val name = "Standard"
    override val description = "S01E01 or Episode 01 or 01"
    override val priority = 100
    
    // Pattern: [Show] S01E01 [Title].mkv or [Show] 01 [Title].mkv
    private val standardRegex = Regex(
        """(?i)(?:S(\d{1,2})E?(\d{1,3})|E?p?(\d{1,3})(?:\s|[-_.]|$))""",
        RegexOption.IGNORE_CASE
    )
    
    override fun matches(fileName: String): Boolean = standardRegex.containsMatchIn(fileName)
    
    override fun extract(fileName: String): EpisodeMatch? {
        val result = standardRegex.find(fileName) ?: return null
        val season = result.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        val episode = result.groupValues.filter { it.isNotEmpty() }.lastOrNull()?.toIntOrNull() ?: return null
        
        return EpisodeMatch(
            animeName = extractAnimeName(fileName),
            seasonNumber = season,
            episodeNumber = episode,
            rawPattern = result.value
        )
    }
}

class MultiPartPattern : NamedPattern {
    override val name = "MultiPart"
    override val description = "03a.mkv, 03b.mkv"
    override val priority = 90
    
    private val multiPartRegex = Regex("""(\d{2})([ab])\.(?:mkv|mp4|avi)""", RegexOption.IGNORE_CASE)
    
    override fun matches(fileName: String): Boolean = multiPartRegex.containsMatchIn(fileName)
    
    override fun extract(fileName: String): EpisodeMatch? {
        val result = multiPartRegex.find(fileName) ?: return null
        return EpisodeMatch(
            seasonNumber = 1,
            episodeNumber = result.groupValues[1].toInt(),
            isMultiPart = true,
            partLabel = result.groupValues[2],
            rawPattern = result.value
        )
    }
}

class TitlePattern : NamedPattern {
    override val name = "Title"
    override val description = "Show Title - 01.mkv"
    override val priority = 80
    
    private val titleRegex = Regex("""[-_.]\s*(\d{2,3})\s*\.(?:mkv|mp4|avi)""", RegexOption.IGNORE_CASE)
    
    override fun matches(fileName: String): Boolean = titleRegex.containsMatchIn(fileName)
    
    override fun extract(fileName: String): EpisodeMatch? {
        val result = titleRegex.find(fileName) ?: return null
        return EpisodeMatch(
            animeName = extractAnimeName(fileName),
            seasonNumber = 1,
            episodeNumber = result.groupValues[1].toInt(),
            rawPattern = result.value
        )
    }
}

/**
 * Default episode detector with standard patterns
 */
class DefaultEpisodeDetector : EpisodeDetector {
    private val patterns = listOf(
        StandardEpisodePattern(),
        MultiPartPattern(),
        TitlePattern()
    ).sortedBy { it.priority }
    
    override fun detectEpisode(fileName: String): EpisodeMatch? {
        return patterns.firstNotNullOfOrNull { it.extract(fileName) }
    }
    
    override fun detectSeason(fileName: String): Int? {
        // Try S01 pattern first
        Regex("""S(\d{1,2})""", RegexOption.IGNORE_CASE)
            .find(fileName)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }
        
        // Try folder pattern
        Regex("""Season\s*(\d{1,2})""", RegexOption.IGNORE_CASE)
            .find(fileName)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }
        
        return 1
    }
    
    override fun extractAnimeName(fileName: String): String? {
        // Remove episode numbers and extensions
        val cleaned = fileName
            .replace(Regex("""S?\d{1,2}E?\d{1,3}""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[-_.]\d{2,3}[-_.]"""), " ")
            .replace(Regex("""\.(?:mkv|mp4|avi|srt|ass)""", RegexOption.IGNORE_CASE), "")
            .trim()
            .replace(Regex("""\s+"""), " ")
        
        return cleaned.takeIf { it.isNotBlank() && it.length > 1 }
    }
}
