package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result

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

interface BangumiCollectionService {
    val hasToken: Boolean

    suspend fun getCurrentUser(): Result<BangumiUser>
    suspend fun getSubjectCollection(subjectId: Int): Result<BangumiSubjectCollection?>
    suspend fun upsertSubjectCollection(subjectId: Int, type: BangumiSubjectCollectionType): Result<Unit>
    suspend fun getEpisodeCollections(subjectId: Int): Result<List<BangumiEpisodeCollection>>
    suspend fun updateEpisodeCollections(
        subjectId: Int,
        episodeIds: List<Int>,
        type: BangumiEpisodeCollectionType
    ): Result<Unit>
    suspend fun updateEpisodeCollection(episodeId: Int, type: BangumiEpisodeCollectionType): Result<Unit>
}

