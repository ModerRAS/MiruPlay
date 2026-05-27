package com.miruplay.tv.scraper.desktop

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.ScraperResult
import com.miruplay.tv.repository.BangumiCollectionService
import com.miruplay.tv.repository.BangumiEpisodeCollection
import com.miruplay.tv.repository.BangumiEpisodeCollectionType
import com.miruplay.tv.repository.BangumiEpisodeMetadata
import com.miruplay.tv.repository.BangumiSubjectCollection
import com.miruplay.tv.repository.BangumiSubjectCollectionType
import com.miruplay.tv.repository.BangumiUser
import com.miruplay.tv.scraper.MetadataScraper
import com.miruplay.tv.scraper.core.BangumiApiClient
import com.miruplay.tv.scraper.core.searchByAlias
import com.ibm.icu.text.Transliterator
import okhttp3.HttpUrl

class DesktopBangumiScraper internal constructor(
    baseUrl: String,
    tokenProvider: () -> String? = { null },
) : MetadataScraper, BangumiCollectionService {
    constructor(tokenProvider: () -> String? = { null }) : this(
        BangumiApiClient.DEFAULT_BASE_URL,
        tokenProvider
    )

    internal constructor(baseUrl: HttpUrl, tokenProvider: () -> String? = { null }) : this(
        baseUrl.toString(),
        tokenProvider
    )

    private val api = BangumiApiClient(
        baseUrl = baseUrl,
        tokenProvider = tokenProvider,
        userAgent = USER_AGENT,
        normalizeQuery = { it.toSimplifiedChinese() },
    )

    override val hasToken: Boolean
        get() = api.hasToken

    override val sourceName: String = "Bangumi"

    override suspend fun searchAnime(query: String): Result<List<ScraperResult>> =
        api.searchAnime(query)

    override suspend fun searchByAlias(normalizedName: String, candidates: List<String>): Result<ScraperResult?> =
        api.searchByAlias(normalizedName, candidates)

    override suspend fun getAnimeDetails(animeId: String): Result<Anime> =
        api.getAnimeDetails(animeId)

    override suspend fun getEpisodes(animeId: String): Result<List<DesktopEpisodeMetadata>> =
        api.getEpisodes(animeId)

    override suspend fun getCurrentUser(): Result<BangumiUser> =
        api.getCurrentUser()

    override suspend fun getSubjectCollection(subjectId: Int): Result<BangumiSubjectCollection?> =
        api.getSubjectCollection(subjectId)

    override suspend fun upsertSubjectCollection(
        subjectId: Int,
        type: BangumiSubjectCollectionType,
    ): Result<Unit> =
        api.upsertSubjectCollection(subjectId, type)

    override suspend fun getEpisodeCollections(subjectId: Int): Result<List<BangumiEpisodeCollection>> =
        api.getEpisodeCollections(subjectId)

    override suspend fun updateEpisodeCollections(
        subjectId: Int,
        episodeIds: List<Int>,
        type: BangumiEpisodeCollectionType,
    ): Result<Unit> =
        api.updateEpisodeCollections(subjectId, episodeIds, type)

    override suspend fun updateEpisodeCollection(
        episodeId: Int,
        type: BangumiEpisodeCollectionType,
    ): Result<Unit> =
        api.updateEpisodeCollection(episodeId, type)

    private companion object {
        const val USER_AGENT = "MiruPlay/1.0 (Windows Desktop; https://github.com/hooke007/mpv_PlayKit)"
    }
}

typealias DesktopEpisodeMetadata = BangumiEpisodeMetadata

private fun String.toSimplifiedChinese(): String =
    DesktopChineseTransliterator.toSimplified(this)

private object DesktopChineseTransliterator {
    private val traditionalToSimplified = runCatching {
        Transliterator.getInstance("Traditional-Simplified")
    }.getOrNull()

    fun toSimplified(text: String): String =
        traditionalToSimplified?.transliterate(text) ?: text
}
