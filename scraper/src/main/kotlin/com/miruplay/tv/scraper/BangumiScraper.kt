package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.BangumiCollectionService
import com.miruplay.tv.repository.BangumiEpisodeMetadata
import com.miruplay.tv.repository.BangumiEpisodeCollection
import com.miruplay.tv.repository.BangumiEpisodeCollectionType
import com.miruplay.tv.repository.BangumiSubjectCollection
import com.miruplay.tv.repository.BangumiSubjectCollectionType
import com.miruplay.tv.repository.BangumiUser
import com.miruplay.tv.scraper.core.BangumiApiClient
import com.miruplay.tv.scraper.core.BangumiArchiveSubjectSearch
import com.miruplay.tv.scraper.core.searchByAlias
import com.miruplay.tv.scraper.core.toSimplifiedChineseQuery
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BangumiScraper @Inject constructor(
    private val credentials: AppCredentialStore,
    archiveSearch: BangumiArchiveSubjectSearch,
) : MetadataScraper, BangumiCollectionService {

    override val sourceName: String = "Bangumi"

    private val api = BangumiApiClient(
        tokenProvider = { credentials.bangumiAccessToken },
        userAgent = USER_AGENT,
        normalizeQuery = { it.toSimplifiedChineseQuery() },
        archiveSearch = archiveSearch,
    )

    companion object {
        private const val USER_AGENT = "MiruPlay/1.0 (Android TV; https://github.com/open-ani/animeko-inspired-local-client)"
    }

    override val hasToken: Boolean
        get() = api.hasToken

    override suspend fun searchAnime(query: String): Result<List<ScraperResult>> =
        api.searchAnime(query)

    override suspend fun getAnimeDetails(animeId: String): Result<Anime> =
        api.getAnimeDetails(animeId)

    override suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>> =
        api.getEpisodes(animeId).map { episodes -> episodes.map { it.toEpisodeMetadata() } }

    override suspend fun searchByAlias(normalizedName: String, candidates: List<String>): Result<ScraperResult?> =
        api.searchByAlias(normalizedName, candidates)

    override suspend fun getCurrentUser(): Result<BangumiUser> =
        api.getCurrentUser()

    override suspend fun getSubjectCollection(subjectId: Int): Result<BangumiSubjectCollection?> =
        api.getSubjectCollection(subjectId)

    override suspend fun upsertSubjectCollection(
        subjectId: Int,
        type: BangumiSubjectCollectionType
    ): Result<Unit> =
        api.upsertSubjectCollection(subjectId, type)

    override suspend fun getEpisodeCollections(subjectId: Int): Result<List<BangumiEpisodeCollection>> =
        api.getEpisodeCollections(subjectId)

    override suspend fun updateEpisodeCollections(
        subjectId: Int,
        episodeIds: List<Int>,
        type: BangumiEpisodeCollectionType
    ): Result<Unit> =
        api.updateEpisodeCollections(subjectId, episodeIds, type)

    override suspend fun updateEpisodeCollection(
        episodeId: Int,
        type: BangumiEpisodeCollectionType
    ): Result<Unit> =
        api.updateEpisodeCollection(episodeId, type)
}

private fun BangumiEpisodeMetadata.toEpisodeMetadata(): EpisodeMetadata =
    EpisodeMetadata(
        episodeNumber = episodeNumber,
        title = title,
        airDate = airDate,
        summary = summary,
        thumbnailUrl = thumbnailUrl,
        isSpecial = isSpecial,
        bangumiEpisodeId = bangumiEpisodeId,
        durationMs = durationMs,
        collectionType = collectionType,
    )
