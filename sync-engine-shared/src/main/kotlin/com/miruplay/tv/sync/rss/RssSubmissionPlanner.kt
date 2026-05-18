package com.miruplay.tv.sync.rss

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import java.security.MessageDigest

data class RssSubmissionDecision(
    val item: RssFeedItem,
    val submissionUrl: String?,
    val itemKey: String?,
    val status: RssSubmissionDecisionStatus,
    val submissionType: RssSubmissionUrlType,
)

enum class RssSubmissionDecisionStatus {
    WOULD_SUBMIT,
    SKIPPED_FILTER,
    MISSING_SUBMISSION,
}

enum class RssSubmissionUrlType {
    MAGNET,
    TORRENT,
    OTHER,
    NONE,
}

object RssSubmissionPlanner {
    fun plan(feedItems: List<RssFeedItem>, filterRegex: String?): Result<List<RssSubmissionDecision>> {
        val filter = filterRegex
            ?.takeIf { it.isNotBlank() }
            ?.let {
                runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrElse { error ->
                    return Result.failure(AppError.SyncError.WriteFailed("RSS", "过滤正则无效：${error.message}"))
                }
            }

        return Result.success(
            feedItems.map { item ->
                val matchesFilter = filter?.containsMatchIn(item.title) ?: true
                val submissionUrl = item.submissionUrl
                val status = when {
                    !matchesFilter -> RssSubmissionDecisionStatus.SKIPPED_FILTER
                    submissionUrl.isNullOrBlank() -> RssSubmissionDecisionStatus.MISSING_SUBMISSION
                    else -> RssSubmissionDecisionStatus.WOULD_SUBMIT
                }
                RssSubmissionDecision(
                    item = item,
                    submissionUrl = submissionUrl,
                    itemKey = submissionUrl?.takeIf(String::isNotBlank)?.let { stableItemKey(item, it) },
                    status = status,
                    submissionType = RssSubmissionUrls.typeOf(submissionUrl),
                )
            }
        )
    }

    fun stableItemKey(item: RssFeedItem, submissionUrl: String): String =
        item.guid?.takeIf { it.isNotBlank() } ?: stableHash("${item.title}|$submissionUrl")

    fun stableHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

}
