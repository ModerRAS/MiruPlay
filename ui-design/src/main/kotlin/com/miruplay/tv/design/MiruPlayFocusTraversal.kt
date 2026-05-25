package com.miruplay.tv.design

enum class MiruPlayFocusAxis {
    Horizontal,
    Vertical,
    Linear,
}

enum class MiruPlayFocusEdge {
    Before,
    After,
}

sealed interface MiruPlayFocusMove {
    data class Index(val index: Int) : MiruPlayFocusMove
    data class Edge(val edge: MiruPlayFocusEdge) : MiruPlayFocusMove
}

fun MiruPlayInputIntent.navigationDelta(axis: MiruPlayFocusAxis): Int? =
    when (axis) {
        MiruPlayFocusAxis.Horizontal -> horizontalNavigationDelta()
        MiruPlayFocusAxis.Vertical -> verticalNavigationDelta()
        MiruPlayFocusAxis.Linear -> linearNavigationDelta()
    }

fun <T> List<T>.focusTargetAfter(
    current: T,
    delta: Int,
): T? {
    val currentIndex = indexOf(current)
    if (currentIndex < 0) return null
    return getOrNull(currentIndex + delta)
}

fun <T> List<T>.focusTargetAfter(
    current: T,
    intent: MiruPlayInputIntent,
    axis: MiruPlayFocusAxis,
): T? =
    intent.navigationDelta(axis)?.let { delta ->
        focusTargetAfter(current = current, delta = delta)
    }

fun focusIndexAfter(
    currentIndex: Int,
    delta: Int,
    itemCount: Int,
): Int? {
    if (itemCount <= 0 || currentIndex !in 0 until itemCount) return null
    return (currentIndex + delta).takeIf { it in 0 until itemCount }
}

fun focusIndexAfter(
    currentIndex: Int,
    intent: MiruPlayInputIntent,
    axis: MiruPlayFocusAxis,
    itemCount: Int,
): Int? =
    intent.navigationDelta(axis)?.let { delta ->
        focusIndexAfter(
            currentIndex = currentIndex,
            delta = delta,
            itemCount = itemCount,
        )
    }

fun focusMoveAfter(
    currentIndex: Int,
    delta: Int,
    itemCount: Int,
): MiruPlayFocusMove? {
    if (itemCount <= 0 || delta == 0 || currentIndex !in 0 until itemCount) return null
    val targetIndex = currentIndex + delta
    return when {
        targetIndex < 0 -> MiruPlayFocusMove.Edge(MiruPlayFocusEdge.Before)
        targetIndex >= itemCount -> MiruPlayFocusMove.Edge(MiruPlayFocusEdge.After)
        else -> MiruPlayFocusMove.Index(targetIndex)
    }
}

fun focusMoveAfter(
    currentIndex: Int,
    intent: MiruPlayInputIntent,
    axis: MiruPlayFocusAxis,
    itemCount: Int,
): MiruPlayFocusMove? =
    intent.navigationDelta(axis)?.let { delta ->
        focusMoveAfter(
            currentIndex = currentIndex,
            delta = delta,
            itemCount = itemCount,
        )
    }

fun gridFocusIndexAfter(
    currentIndex: Int,
    intent: MiruPlayInputIntent,
    columns: Int,
    itemCount: Int,
): Int? {
    if (itemCount <= 0 || currentIndex !in 0 until itemCount) return null
    val safeColumns = columns.coerceAtLeast(1)
    val currentColumn = currentIndex % safeColumns
    return when (intent.horizontalNavigationDelta()) {
        1 -> if (currentColumn == safeColumns - 1) {
            null
        } else {
            focusIndexAfter(currentIndex = currentIndex, delta = 1, itemCount = itemCount)
        }
        -1 -> if (currentColumn == 0) {
            null
        } else {
            focusIndexAfter(currentIndex = currentIndex, delta = -1, itemCount = itemCount)
        }
        else -> when (intent.verticalNavigationDelta()) {
            -1 -> focusIndexAfter(currentIndex = currentIndex, delta = -safeColumns, itemCount = itemCount)
            1 -> {
                val nextRowStart = ((currentIndex / safeColumns) + 1) * safeColumns
                if (nextRowStart in 0 until itemCount) {
                    minOf(nextRowStart + currentColumn, itemCount - 1)
                } else {
                    null
                }
            }
            else -> null
        }
    }
}

fun splitColumnFocusIndexAfter(
    currentIndex: Int,
    intent: MiruPlayInputIntent,
    pageStart: Int,
    visibleCount: Int,
    itemCount: Int,
): Int? {
    if (itemCount <= 0 || currentIndex !in 0 until itemCount) return null
    val safePageStart = pageStart.coerceIn(0, itemCount - 1)
    val safeVisibleCount = visibleCount.coerceIn(1, itemCount - safePageStart)
    val splitIndex = splitColumnSecondColumnStart(
        pageStart = safePageStart,
        visibleCount = safeVisibleCount,
    )
    val pageEnd = safePageStart + safeVisibleCount
    return when (intent.verticalNavigationDelta()) {
        -1 -> focusIndexAfter(currentIndex = currentIndex, delta = -1, itemCount = itemCount)
        1 -> focusIndexAfter(currentIndex = currentIndex, delta = 1, itemCount = itemCount)
        else -> when (intent.horizontalNavigationDelta()) {
            -1 -> if (currentIndex >= splitIndex && currentIndex < pageEnd) {
                val leftIndex = safePageStart + currentIndex - splitIndex
                leftIndex.coerceAtMost(splitIndex - 1)
            } else {
                null
            }
            1 -> if (currentIndex in safePageStart until splitIndex && splitIndex < pageEnd) {
                (currentIndex + splitIndex - safePageStart).takeIf { it < pageEnd } ?: pageEnd - 1
            } else {
                null
            }
            else -> null
        }
    }
}

fun splitColumnSecondColumnStart(pageStart: Int, visibleCount: Int): Int {
    val safeVisibleCount = visibleCount.coerceAtLeast(0)
    return pageStart + (safeVisibleCount + 1) / 2
}

fun nextEnabledFocusIndex(
    currentIndex: Int,
    delta: Int,
    itemCount: Int,
    enabledItems: List<Boolean> = emptyList(),
): Int? {
    if (itemCount <= 0 || delta == 0 || currentIndex !in 0 until itemCount) return null
    var targetIndex = currentIndex + delta
    while (targetIndex in 0 until itemCount) {
        if (enabledItems.getOrElse(targetIndex) { true }) return targetIndex
        targetIndex += delta
    }
    return null
}

fun nextEnabledFocusIndex(
    currentIndex: Int,
    intent: MiruPlayInputIntent,
    axis: MiruPlayFocusAxis,
    itemCount: Int,
    enabledItems: List<Boolean> = emptyList(),
): Int? =
    intent.navigationDelta(axis)?.let { delta ->
        nextEnabledFocusIndex(
            currentIndex = currentIndex,
            delta = delta,
            itemCount = itemCount,
            enabledItems = enabledItems,
        )
    }

fun firstEnabledFocusIndex(
    itemCount: Int,
    enabledItems: List<Boolean> = emptyList(),
): Int? {
    if (itemCount <= 0) return null
    return (0 until itemCount).firstOrNull { index ->
        enabledItems.getOrElse(index) { true }
    }
}
