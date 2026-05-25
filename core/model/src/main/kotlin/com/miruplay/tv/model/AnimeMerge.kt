package com.miruplay.tv.model

/**
 * Identity keys used to collapse duplicate library entries that point to the same show.
 */
fun Anime.sameAnimeIdentityKeys(): Set<String> = buildSet {
    bangumiId?.takeIf { it > 0 }?.let { add("bangumi:$it") }
    anilistId?.takeIf { it > 0 }?.let { add("anilist:$it") }
    tmdbId?.takeIf { it > 0 }?.let { add("tmdb:$it") }

    titleCn.normalizedMergeTitle()?.let { add("title:$it") }
    title.normalizedMergeTitle()?.let { add("title:$it") }
}

fun Anime.hasSameAnimeIdentityAs(other: Anime): Boolean {
    if (hasConflictingExternalId(other)) return false
    val mine = sameAnimeIdentityKeys()
    if (mine.isEmpty()) return false
    return mine.any { it in other.sameAnimeIdentityKeys() }
}

fun List<Anime>.mergeSameAnimeForDisplay(): List<Anime> =
    sameAnimeGroups().map { it.mergeAnimeGroupForDisplay() }

fun List<Anime>.sameAnimeGroupFor(anchor: Anime): List<Anime> =
    (listOf(anchor) + this)
        .sameAnimeGroups()
        .firstOrNull { group -> group.any { it.id == anchor.id } }
        ?: listOf(anchor)

private fun List<Anime>.sameAnimeGroups(): List<List<Anime>> {
    val groups = mutableListOf<MutableList<Anime>>()

    for (anime in distinctBy { it.id }) {
        val matchedGroups = groups.filter { group ->
            group.canMergeWith(anime)
        }

        if (matchedGroups.isEmpty()) {
            groups += mutableListOf(anime)
            continue
        }

        val target = matchedGroups.first()
        target += anime

        matchedGroups.drop(1)
            .filter { duplicateGroup -> target.canMergeGroup(duplicateGroup) }
            .forEach { duplicateGroup ->
                target += duplicateGroup
                groups -= duplicateGroup
            }
    }

    return groups
}

fun List<Anime>.mergeAnimeGroupForDisplay(): Anime {
    val unique = distinctBy { it.id }
    val primary = unique.firstOrNull() ?: return Anime(id = "", title = "")
    val best = unique.maxByOrNull { it.metadataScore() } ?: primary

    return best.copy(
        id = primary.id,
        episodeCount = unique.maxOfOrNull { it.episodeCount } ?: best.episodeCount
    )
}

private fun String?.normalizedMergeTitle(): String? =
    this
        ?.replace(Regex("[._]+"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }

private fun Anime.metadataScore(): Int =
    listOf(
        if (!posterUrl.isNullOrBlank()) 8 else 0,
        if (!posterLocalPath.isNullOrBlank()) 8 else 0,
        if (!fanartUrl.isNullOrBlank()) 4 else 0,
        if (summary.isNotBlank()) 2 else 0,
        if (bangumiId != null || anilistId != null || tmdbId != null) 2 else 0,
        if (rating > 0f) 1 else 0,
        episodeCount.coerceAtLeast(0).coerceAtMost(100)
    ).sum()

private fun Anime.hasConflictingExternalId(other: Anime): Boolean =
    hasConflictingId(bangumiId, other.bangumiId) ||
        hasConflictingId(anilistId, other.anilistId) ||
        hasConflictingId(tmdbId, other.tmdbId)

private fun hasConflictingId(left: Int?, right: Int?): Boolean =
    left != null && right != null && left != right

private fun List<Anime>.canMergeWith(anime: Anime): Boolean =
    none { it.hasConflictingExternalId(anime) } &&
        any { existing -> existing.hasSameAnimeIdentityAs(anime) }

private fun List<Anime>.canMergeGroup(other: List<Anime>): Boolean =
    none { left -> other.any { right -> left.hasConflictingExternalId(right) } } &&
        any { left -> other.any { right -> left.hasSameAnimeIdentityAs(right) } }
