package com.miruplay.tv.repository

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object BangumiApiPayloads {
    fun searchSubjects(
        keyword: String,
        subjectType: Int = SUBJECT_TYPE_ANIME,
    ): JsonObject =
        buildJsonObject {
            put("keyword", keyword)
            put("sort", "match")
            put(
                "filter",
                buildJsonObject {
                    put("type", buildJsonArray { add(subjectType) })
                }
            )
        }

    fun subjectCollection(type: BangumiSubjectCollectionType): JsonObject =
        buildJsonObject {
            put("type", type.value)
        }

    fun episodeCollections(
        episodeIds: List<Int>,
        type: BangumiEpisodeCollectionType,
    ): JsonObject =
        buildJsonObject {
            put("episode_id", buildJsonArray { episodeIds.distinct().forEach { add(it) } })
            put("type", type.value)
        }

    fun episodeCollection(type: BangumiEpisodeCollectionType): JsonObject =
        buildJsonObject {
            put("type", type.value)
        }

    private const val SUBJECT_TYPE_ANIME = 2
}
