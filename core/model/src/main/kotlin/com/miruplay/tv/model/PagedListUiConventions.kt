package com.miruplay.tv.model

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
    unitLabel: String,
    prefix: String? = null,
): String? {
    if (itemCount <= 0 || visibleCount <= 0 || visibleCount >= itemCount) return null
    val safeStart = pagedListCoercedPageStart(
        pageStart = pageStart,
        itemCount = itemCount,
        pageSize = pageSize,
    )
    val end = (safeStart + visibleCount).coerceAtMost(itemCount)
    val range = "显示 ${safeStart + 1}-$end / $itemCount $unitLabel，按上/下继续翻页。"
    return prefix
        ?.takeIf { it.isNotBlank() }
        ?.let { "$it：$range" }
        ?: range
}
