package com.miruplay.tv.model

data class FilenameParseResult(
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val group: String? = null,
    val resolution: String? = null,
    val source: String? = null,
    val special: String? = null,
)

interface FilenameMetadataParser {
    fun parse(filename: String, maxLength: Int = 64): FilenameParseResult
}
