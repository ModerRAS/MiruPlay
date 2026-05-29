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
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.scraper.core.BangumiApiClient
import com.miruplay.tv.scraper.core.BangumiArchiveSubjectSearch
import com.miruplay.tv.scraper.core.searchByAlias
import com.miruplay.tv.scraper.core.toBangumiHttpProxyConfig
import com.miruplay.tv.scraper.core.toSimplifiedChineseQuery
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BangumiScraper @Inject constructor(
    private val credentials: AppCredentialStore,
    private val cloudDriveRepository: CloudDriveAutomationRepository,
    archiveSearch: BangumiArchiveSubjectSearch,
) : MetadataScraper, MetadataImageBackfillScraper, BangumiCollectionService {

    override val sourceName: String = "Bangumi"

    private val api = BangumiApiClient(
        tokenProvider = { credentials.bangumiAccessToken },
        userAgent = USER_AGENT,
        normalizeQuery = { it.toSimplifiedChineseQuery() },
        archiveSearch = archiveSearch,
    )

    companion object {
        private const val USER_AGENT = "ModerRAS/MiruPlay/0.1.0 (Android TV) (https://github.com/ModerRAS/MiruPlay)"
    }

    override val hasToken: Boolean
        get() = api.hasToken

    override suspend fun searchAnime(query: String): Result<List<ScraperResult>> =
        withConfiguredProxy { api.searchAnime(query) }

    override suspend fun getAnimeDetails(animeId: String): Result<Anime> =
        withConfiguredProxy { api.getAnimeDetails(animeId) }

    override suspend fun getImageDetails(animeId: String): Result<Anime> =
        withConfiguredProxy { api.getOnlineAnimeDetails(animeId) }

    override suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>> =
        withConfiguredProxy { api.getEpisodes(animeId).map { episodes -> episodes.map { it.toEpisodeMetadata() } } }

    override suspend fun searchByAlias(normalizedName: String, candidates: List<String>): Result<ScraperResult?> =
        withConfiguredProxy { api.searchByAlias(normalizedName, candidates) }

    override suspend fun getCurrentUser(): Result<BangumiUser> =
        withConfiguredProxy { api.getCurrentUser() }

    override suspend fun getSubjectCollection(subjectId: Int): Result<BangumiSubjectCollection?> =
        withConfiguredProxy { api.getSubjectCollection(subjectId) }

    override suspend fun upsertSubjectCollection(
        subjectId: Int,
        type: BangumiSubjectCollectionType
    ): Result<Unit> =
        withConfiguredProxy { api.upsertSubjectCollection(subjectId, type) }

    override suspend fun getEpisodeCollections(subjectId: Int): Result<List<BangumiEpisodeCollection>> =
        withConfiguredProxy { api.getEpisodeCollections(subjectId) }

    override suspend fun updateEpisodeCollections(
        subjectId: Int,
        episodeIds: List<Int>,
        type: BangumiEpisodeCollectionType
    ): Result<Unit> =
        withConfiguredProxy { api.updateEpisodeCollections(subjectId, episodeIds, type) }

    override suspend fun updateEpisodeCollection(
        episodeId: Int,
        type: BangumiEpisodeCollectionType
    ): Result<Unit> =
        withConfiguredProxy { api.updateEpisodeCollection(episodeId, type) }

    private suspend fun <T> withConfiguredProxy(block: suspend () -> Result<T>): Result<T> {
        cloudDriveRepository.getConfig().getOrNull()?.let { config ->
            api.configureProxy(config.toBangumiHttpProxyConfig())
        }
        return block()
    }
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
