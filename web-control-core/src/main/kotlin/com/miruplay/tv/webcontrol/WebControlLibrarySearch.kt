package com.miruplay.tv.webcontrol

import com.miruplay.tv.model.Anime

private const val WEB_CONTROL_LIBRARY_WINDOW_LIMIT = 24

fun List<Anime>.toWebControlLibrary(
    continueWatching: List<ContinueWatchingDto> = emptyList(),
): LibraryDto {
    val anime = distinctBy { it.id }
        .sortedBy { it.title.ifBlank { it.id } }
    return LibraryDto(
        continueWatching = continueWatching,
        recentlyAdded = anime.takeLast(WEB_CONTROL_LIBRARY_WINDOW_LIMIT),
        allAnime = anime,
    )
}

fun LibraryDto.filteredByQuery(query: String): LibraryDto {
    val normalized = query.trim()
    if (normalized.isBlank()) return this

    val filtered = allAnime.filter { item ->
        item.id.contains(normalized, ignoreCase = true) ||
            item.title.contains(normalized, ignoreCase = true) ||
            item.titleCn?.contains(normalized, ignoreCase = true) == true
    }
    return copy(
        recentlyAdded = filtered.take(WEB_CONTROL_LIBRARY_WINDOW_LIMIT),
        allAnime = filtered,
    )
}
