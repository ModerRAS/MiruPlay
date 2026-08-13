package com.miruplay.tv.repository

import com.miruplay.tv.core.common.Result

sealed interface BangumiCommentContent {
    data class Text(
        val value: String,
        val style: BangumiCommentTextStyle = BangumiCommentTextStyle(),
    ) : BangumiCommentContent
    data class Image(
        val url: String,
        val description: String? = null,
        val inline: Boolean = false,
    ) : BangumiCommentContent
    data class Spoiler(val children: List<BangumiCommentContent>) : BangumiCommentContent
}

data class BangumiCommentTextStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val linkUrl: String? = null,
)

data class BangumiCommentUser(
    val id: Int? = null,
    val name: String,
    val avatarUrl: String? = null,
)

data class BangumiEpisodeComment(
    val id: Int,
    val user: BangumiCommentUser,
    val content: List<BangumiCommentContent>,
    val createdAt: String? = null,
    val replies: List<BangumiEpisodeComment> = emptyList(),
)

interface BangumiEpisodeCommentsService {
    suspend fun getEpisodeComments(episodeId: Int): Result<List<BangumiEpisodeComment>>
}
