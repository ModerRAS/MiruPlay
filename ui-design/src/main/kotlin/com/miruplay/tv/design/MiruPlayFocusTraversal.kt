package com.miruplay.tv.design

enum class MiruPlayFocusAxis {
    Horizontal,
    Vertical,
    Linear,
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
