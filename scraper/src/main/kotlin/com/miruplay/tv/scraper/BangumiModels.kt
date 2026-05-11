package com.miruplay.tv.scraper

data class BangumiUser(
    val id: Int,
    val username: String,
    val nickname: String
)

data class BangumiSubjectCollection(
    val subjectId: Int,
    val type: Int,
    val rate: Int,
    val epStatus: Int,
    val updatedAt: String? = null
)

data class BangumiEpisodeCollection(
    val episodeId: Int,
    val episodeNumber: Int,
    val type: Int,
    val updatedAt: Long = 0L
)

enum class BangumiSubjectCollectionType(val value: Int) {
    WISH(1),
    DONE(2),
    DOING(3),
    ON_HOLD(4),
    DROPPED(5)
}

enum class BangumiEpisodeCollectionType(val value: Int) {
    NONE(0),
    WISH(1),
    DONE(2),
    DROPPED(3)
}
