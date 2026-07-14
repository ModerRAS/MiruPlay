package com.miruplay.tv.model

enum class SubtitleLanguagePreference(val storageValue: String) {
    AUTO("auto"),
    CHINESE_SIMPLIFIED("zh_hans"),
    CHINESE_TRADITIONAL("zh_hant"),
    CHINESE("zh"),
    ENGLISH("en"),
    JAPANESE("ja");

    companion object {
        fun fromStorageValue(value: String?): SubtitleLanguagePreference =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: AUTO
    }
}

fun prioritizeSubtitlePaths(
    paths: List<String>,
    preference: SubtitleLanguagePreference,
): List<String> {
    val tracks = buildExternalSubtitleTracks(paths)
    val preferredIndex = preferredSubtitleTrackIndex(tracks, preference) ?: return tracks.map(SubtitleTrack::path)
    return buildList {
        add(tracks[preferredIndex].path)
        tracks.forEachIndexed { index, track ->
            if (index != preferredIndex) add(track.path)
        }
    }
}

fun preferredSubtitleTrackIndex(
    tracks: List<SubtitleTrack>,
    preference: SubtitleLanguagePreference,
): Int? {
    if (preference == SubtitleLanguagePreference.AUTO) return null
    return tracks
        .mapIndexed { index, track -> index to subtitlePreferenceScore(track, preference) }
        .filter { (_, score) -> score > 0 }
        .maxWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenBy { -it.first })
        ?.first
}

private fun subtitlePreferenceScore(
    track: SubtitleTrack,
    preference: SubtitleLanguagePreference,
): Int {
    val detected = detectedSubtitleLanguage(track) ?: return 0
    return when (preference) {
        SubtitleLanguagePreference.AUTO -> 0
        SubtitleLanguagePreference.CHINESE_SIMPLIFIED -> when (detected) {
            DetectedSubtitleLanguage.CHINESE_SIMPLIFIED -> 3
            DetectedSubtitleLanguage.CHINESE -> 2
            DetectedSubtitleLanguage.CHINESE_TRADITIONAL -> 1
            else -> 0
        }
        SubtitleLanguagePreference.CHINESE_TRADITIONAL -> when (detected) {
            DetectedSubtitleLanguage.CHINESE_TRADITIONAL -> 3
            DetectedSubtitleLanguage.CHINESE -> 2
            DetectedSubtitleLanguage.CHINESE_SIMPLIFIED -> 1
            else -> 0
        }
        SubtitleLanguagePreference.CHINESE -> if (detected.isChinese) 3 else 0
        SubtitleLanguagePreference.ENGLISH -> if (detected == DetectedSubtitleLanguage.ENGLISH) 3 else 0
        SubtitleLanguagePreference.JAPANESE -> if (detected == DetectedSubtitleLanguage.JAPANESE) 3 else 0
    }
}

private fun detectedSubtitleLanguage(track: SubtitleTrack): DetectedSubtitleLanguage? {
    val declared = detectSubtitleLanguage(track.language)
    val descriptive = detectSubtitleLanguage(track.title)
    return when {
        declared == DetectedSubtitleLanguage.CHINESE && descriptive?.isChinese == true -> descriptive
        declared != null -> declared
        else -> descriptive
    }
}

private fun detectSubtitleLanguage(value: String): DetectedSubtitleLanguage? {
    if (value.isBlank() || value.equals("und", ignoreCase = true)) return null
    val normalized = value.lowercase().replace('_', '-')
    val tokens = normalized.split(Regex("[^a-z0-9]+"))
        .filter(String::isNotBlank)
    val pairs = tokens.zipWithNext { first, second -> "$first-$second" }
    val aliases = (pairs + tokens).toSet()
    return when {
        CHINESE_SIMPLIFIED_TEXT.any(normalized::contains) || aliases.any(CHINESE_SIMPLIFIED_ALIASES::contains) ->
            DetectedSubtitleLanguage.CHINESE_SIMPLIFIED
        CHINESE_TRADITIONAL_TEXT.any(normalized::contains) || aliases.any(CHINESE_TRADITIONAL_ALIASES::contains) ->
            DetectedSubtitleLanguage.CHINESE_TRADITIONAL
        CHINESE_TEXT.any(normalized::contains) || aliases.any(CHINESE_ALIASES::contains) ->
            DetectedSubtitleLanguage.CHINESE
        ENGLISH_TEXT.any(normalized::contains) || aliases.any(ENGLISH_ALIASES::contains) ->
            DetectedSubtitleLanguage.ENGLISH
        JAPANESE_TEXT.any(normalized::contains) || aliases.any(JAPANESE_ALIASES::contains) ->
            DetectedSubtitleLanguage.JAPANESE
        else -> null
    }
}

private enum class DetectedSubtitleLanguage(val isChinese: Boolean = false) {
    CHINESE_SIMPLIFIED(true),
    CHINESE_TRADITIONAL(true),
    CHINESE(true),
    ENGLISH,
    JAPANESE,
}

private val CHINESE_SIMPLIFIED_ALIASES = setOf("zh-hans", "zh-cn", "zh-sg", "chs", "sc", "gb", "gb2312")
private val CHINESE_TRADITIONAL_ALIASES = setOf("zh-hant", "zh-tw", "zh-hk", "zh-mo", "cht", "tc", "big5")
private val CHINESE_ALIASES = setOf("zh", "chi", "zho", "chinese")
private val ENGLISH_ALIASES = setOf("en", "eng", "english")
private val JAPANESE_ALIASES = setOf("ja", "jpn", "jp", "japanese")
private val CHINESE_SIMPLIFIED_TEXT = setOf("简中", "简体", "簡中")
private val CHINESE_TRADITIONAL_TEXT = setOf("繁中", "繁体", "繁體", "正體")
private val CHINESE_TEXT = setOf("中文", "汉语", "漢語")
private val ENGLISH_TEXT = setOf("英文", "英语", "英語")
private val JAPANESE_TEXT = setOf("日文", "日语", "日語")
