package com.miruplay.tv.model

import com.miruplay.tv.core.common.UiText

fun pagedListPageStartForIndex(
    index: Int,
    itemCount: Int,
    pageSize: Int,
): Int {
    if (itemCount <= 0 || pageSize <= 0) return 0
    val safeIndex = index.coerceIn(0, itemCount - 1)
    return (safeIndex / pageSize) * pageSize
}

fun pagedListCoercedPageStart(
    pageStart: Int,
    itemCount: Int,
    pageSize: Int,
): Int {
    if (itemCount <= 0 || pageSize <= 0) return 0
    val maxPageStart = pagedListPageStartForIndex(
        index = itemCount - 1,
        itemCount = itemCount,
        pageSize = pageSize,
    )
    return (pageStart / pageSize)
        .coerceAtLeast(0)
        .times(pageSize)
        .coerceAtMost(maxPageStart)
}

fun pagedListPageSummary(
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
    pageSize: Int,
    unitLabel: UiText,
    prefix: UiText? = null,
): UiText? {
    if (itemCount <= 0 || visibleCount <= 0 || visibleCount >= itemCount) return null
    val safeStart = pagedListCoercedPageStart(
        pageStart = pageStart,
        itemCount = itemCount,
        pageSize = pageSize,
    )
    val end = (safeStart + visibleCount).coerceAtMost(itemCount)
    val start = safeStart + 1
    val range = UiText(
        "paged_list_range",
        listOf(start, end, itemCount, unitLabel),
        "显示 $start-$end / $itemCount ${unitLabel.fallback}，按上/下继续翻页。",
    )
    val title = prefix?.takeIf { it.fallback.isNotBlank() } ?: return range
    return UiText(
        "paged_list_range_prefixed",
        listOf(title, range),
        "${title.fallback}：${range.fallback}",
    )
}
