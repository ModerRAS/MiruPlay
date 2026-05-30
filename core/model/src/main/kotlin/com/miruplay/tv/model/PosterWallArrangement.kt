package com.miruplay.tv.model

import kotlinx.serialization.Serializable

@Serializable
enum class PosterWallArrangement {
    TITLE,
    RELEASE_SEASON,
}

data class PosterWallAnimeSection(
    val title: String?,
    val anime: List<Anime>,
)

data class AnimeReleaseSeason(
    val year: Int,
    val startMonth: Int,
) {
    val title: String = "${year}年${startMonth}月新番"
}

fun Anime.releaseSeason(): AnimeReleaseSeason? =
    airDate?.toAnimeReleaseSeason()

fun Anime.posterWallReleaseSeasonTitle(): String? =
    releaseSeason()?.title

fun List<Anime>.sortedForPosterWall(
    arrangement: PosterWallArrangement = PosterWallArrangement.TITLE,
): List<Anime> =
    when (arrangement) {
        PosterWallArrangement.TITLE -> sortedByDisplayTitle()
        PosterWallArrangement.RELEASE_SEASON -> sortedWith(
            compareBy<Anime> { if (it.releaseSeason() == null) 1 else 0 }
                .thenByDescending { it.releaseSeason()?.year ?: Int.MIN_VALUE }
                .thenByDescending { it.releaseSeason()?.startMonth ?: Int.MIN_VALUE }
                .thenBy { it.displayTitle().lowercase() },
        )
    }

fun List<Anime>.posterWallSections(
    arrangement: PosterWallArrangement = PosterWallArrangement.TITLE,
): List<PosterWallAnimeSection> {
    val sorted = sortedForPosterWall(arrangement)
    if (sorted.isEmpty()) return emptyList()
    if (arrangement == PosterWallArrangement.TITLE) {
        return listOf(PosterWallAnimeSection(title = null, anime = sorted))
    }

    return sorted
        .groupBy { it.posterWallReleaseSeasonTitle() ?: posterWallUnknownReleaseSeasonTitle() }
        .map { (title, anime) -> PosterWallAnimeSection(title = title, anime = anime) }
}

fun posterWallArrangementLabel(arrangement: PosterWallArrangement): String =
    when (arrangement) {
        PosterWallArrangement.TITLE -> "按标题"
        PosterWallArrangement.RELEASE_SEASON -> "按新番季"
    }

fun posterWallArrangementStatus(arrangement: PosterWallArrangement): String =
    when (arrangement) {
        PosterWallArrangement.TITLE -> "海报墙当前按标题排序：中文名优先，其次使用原名。"
        PosterWallArrangement.RELEASE_SEASON -> "海报墙会按播出日期归入 1/4/7/9 月新番；没有播出日期的条目排在最后。"
    }

fun posterWallUnknownReleaseSeasonTitle(): String =
    "未识别播出日期"

private fun List<Anime>.sortedByDisplayTitle(): List<Anime> =
    sortedBy { it.displayTitle().lowercase() }

fun String.toAnimeReleaseSeason(): AnimeReleaseSeason? {
    val match = airDateRegex.find(trim()) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    if (month !in 1..12) return null
    return AnimeReleaseSeason(
        year = year,
        startMonth = releaseSeasonStartMonth(month),
    )
}

private fun releaseSeasonStartMonth(month: Int): Int =
    when {
        month >= 9 -> 9
        month >= 7 -> 7
        month >= 4 -> 4
        else -> 1
    }

private val airDateRegex = Regex("""(\d{4})\D+(\d{1,2})""")
