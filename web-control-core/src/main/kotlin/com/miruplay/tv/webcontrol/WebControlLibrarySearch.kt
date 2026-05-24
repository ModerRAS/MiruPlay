package com.miruplay.tv.webcontrol

private const val WEB_CONTROL_RECENTLY_ADDED_LIMIT = 24

fun LibraryDto.filteredByQuery(query: String): LibraryDto {
    val normalized = query.trim()
    if (normalized.isBlank()) return this

    val filtered = allAnime.filter { item ->
        item.id.contains(normalized, ignoreCase = true) ||
            item.title.contains(normalized, ignoreCase = true) ||
            item.titleCn?.contains(normalized, ignoreCase = true) == true
    }
    return copy(
        recentlyAdded = filtered.take(WEB_CONTROL_RECENTLY_ADDED_LIMIT),
        allAnime = filtered,
    )
}
