package com.miruplay.tv.scraper

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.core.common.logging.PerformanceLog
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.BangumiCollectionService
import com.miruplay.tv.repository.BangumiEpisodeMetadata
import com.miruplay.tv.repository.BangumiEpisodeCollection
import com.miruplay.tv.repository.BangumiEpisodeComment
import com.miruplay.tv.repository.BangumiEpisodeCommentsService
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
) : MetadataScraper, MetadataImageBackfillScraper, ManualMetadataSearchScraper, BangumiCollectionService, BangumiEpisodeCommentsService {

    override val sourceName: String = "Bangumi"

    private val api = BangumiApiClient(
        tokenProvider = { credentials.bangumiAccessToken },
        userAgent = USER_AGENT,
        normalizeQuery = { it.toSimplifiedChineseQuery() },
        archiveSearch = archiveSearch,
    )

    companion object {
        private const val USER_AGENT = "ModerRAS/MiruPlay/0.1.0 (Android TV) (https://github.com/ModerRAS/MiruPlay)"
        private const val PERFORMANCE_TAG = "BangumiPerformance"
    }

    override val hasToken: Boolean
        get() = api.hasToken

    override suspend fun searchAnime(query: String): Result<List<ScraperResult>> =
        withConfiguredProxy("bangumi.scraper.search", query.performanceQueryAttributes()) { api.searchAnime(query) }

    override suspend fun searchManualAnime(query: String): Result<List<ScraperResult>> =
        withConfiguredProxy("bangumi.scraper.manual_search", query.performanceQueryAttributes()) {
            api.searchAnimeForManualMatch(query)
        }

    override suspend fun getAnimeDetails(animeId: String): Result<Anime> =
        withConfiguredProxy("bangumi.scraper.details", mapOf("anime_id" to animeId)) { api.getAnimeDetails(animeId) }

    override suspend fun getImageDetails(animeId: String): Result<Anime> =
        withConfiguredProxy("bangumi.scraper.image_details", mapOf("anime_id" to animeId)) {
            api.getOnlineAnimeDetails(animeId)
        }

    override suspend fun getEpisodes(animeId: String): Result<List<EpisodeMetadata>> =
        withConfiguredProxy("bangumi.scraper.episodes", mapOf("anime_id" to animeId)) {
            api.getEpisodes(animeId).map { episodes -> episodes.map { it.toEpisodeMetadata() } }
        }

    override suspend fun searchByAlias(normalizedName: String, candidates: List<String>): Result<ScraperResult?> =
        withConfiguredProxy(
            operation = "bangumi.scraper.alias_search",
            attributes = mapOf(
                "normalized_query_length" to normalizedName.length.toString(),
                "candidate_count" to candidates.size.toString(),
            ),
        ) { api.searchByAlias(normalizedName, candidates) }

    override suspend fun getEpisodeComments(episodeId: Int): Result<List<BangumiEpisodeComment>> =
        withConfiguredProxy(
            "bangumi.scraper.episode_comments",
            mapOf("episode_id" to episodeId.toString()),
        ) { api.getEpisodeComments(episodeId) }

    override suspend fun getCurrentUser(): Result<BangumiUser> =
        withConfiguredProxy("bangumi.scraper.current_user") { api.getCurrentUser() }

    override suspend fun getSubjectCollection(subjectId: Int): Result<BangumiSubjectCollection?> =
        withConfiguredProxy("bangumi.scraper.subject_collection", mapOf("subject_id" to subjectId.toString())) {
            api.getSubjectCollection(subjectId)
        }

    override suspend fun upsertSubjectCollection(
        subjectId: Int,
        type: BangumiSubjectCollectionType
    ): Result<Unit> =
        withConfiguredProxy(
            operation = "bangumi.scraper.upsert_subject_collection",
            attributes = mapOf("subject_id" to subjectId.toString(), "collection_type" to type.name),
        ) { api.upsertSubjectCollection(subjectId, type) }

    override suspend fun getEpisodeCollections(subjectId: Int): Result<List<BangumiEpisodeCollection>> =
        withConfiguredProxy("bangumi.scraper.episode_collections", mapOf("subject_id" to subjectId.toString())) {
            api.getEpisodeCollections(subjectId)
        }

    override suspend fun updateEpisodeCollections(
        subjectId: Int,
        episodeIds: List<Int>,
        type: BangumiEpisodeCollectionType
    ): Result<Unit> =
        withConfiguredProxy(
            operation = "bangumi.scraper.update_episode_collections",
            attributes = mapOf(
                "subject_id" to subjectId.toString(),
                "episode_count" to episodeIds.size.toString(),
                "collection_type" to type.name,
            ),
        ) { api.updateEpisodeCollections(subjectId, episodeIds, type) }

    override suspend fun updateEpisodeCollection(
        episodeId: Int,
        type: BangumiEpisodeCollectionType
    ): Result<Unit> =
        withConfiguredProxy(
            operation = "bangumi.scraper.update_episode_collection",
            attributes = mapOf("episode_id" to episodeId.toString(), "collection_type" to type.name),
        ) { api.updateEpisodeCollection(episodeId, type) }

    private suspend fun <T> withConfiguredProxy(
        operation: String,
        attributes: Map<String, String> = emptyMap(),
        block: suspend () -> Result<T>,
    ): Result<T> =
        PerformanceLog.measureSuspendResult(
            tag = PERFORMANCE_TAG,
            operation = operation,
            attributes = attributes,
        ) {
            PerformanceLog.measureSuspendResult(
                tag = PERFORMANCE_TAG,
                operation = "bangumi.scraper.proxy_config",
            ) {
                cloudDriveRepository.getConfig()
            }.getOrNull()?.let { config ->
                api.configureProxy(config.toBangumiHttpProxyConfig())
            }
            block()
        }

    private fun String.performanceQueryAttributes(): Map<String, String> =
        mapOf(
            "query_length" to length.toString(),
            "query_hash" to Integer.toHexString(hashCode()),
        )
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
