package com.miruplay.tv.scraper.di

import android.content.Context
import com.miruplay.tv.scraper.BangumiAnimeMetadataSearchProvider
import com.miruplay.tv.scraper.BangumiScraper
import com.miruplay.tv.scraper.DefaultAnimeMetadataSearchAggregator
import com.miruplay.tv.scraper.DefaultDramaMetadataSearchAggregator
import com.miruplay.tv.scraper.MetadataScraper
import com.miruplay.tv.scraper.RoutingDramaMetadataRepository
import com.miruplay.tv.scraper.TmdbDramaMetadataRepository
import com.miruplay.tv.scraper.TmdbDramaMetadataSearchProvider
import com.miruplay.tv.scraper.TvMazeDramaMetadataSearchProvider
import com.miruplay.tv.scraper.filename.AnimeFilenameParser
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.repository.AnimeMetadataSearchAggregator
import com.miruplay.tv.repository.AnimeMetadataSearchProvider
import com.miruplay.tv.repository.BangumiCollectionService
import com.miruplay.tv.repository.BangumiEpisodeCommentsService
import com.miruplay.tv.repository.DramaMetadataRepository
import com.miruplay.tv.repository.DramaMetadataSearchAggregator
import com.miruplay.tv.repository.DramaMetadataSearchProvider
import com.miruplay.tv.scraper.core.BangumiArchiveStore
import com.miruplay.tv.scraper.core.BangumiArchiveSubjectSearch
import com.miruplay.tv.scraper.core.toSimplifiedChineseQuery
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScraperModule {

    @Provides
    @Singleton
    @IntoSet
    fun provideBangumiScraper(scraper: BangumiScraper): MetadataScraper = scraper

    @Provides
    @Singleton
    fun provideBangumiArchiveStore(
        @ApplicationContext context: Context
    ): BangumiArchiveStore = BangumiArchiveStore(File(context.filesDir, "bangumi-archive"))

    @Provides
    @Singleton
    fun provideBangumiArchiveSubjectSearch(
        store: BangumiArchiveStore
    ): BangumiArchiveSubjectSearch =
        BangumiArchiveSubjectSearch(
            subjectFileProvider = { store.subjectFile },
            normalizeQuery = { it.toSimplifiedChineseQuery() }
        )

    @Provides
    @Singleton
    fun provideBangumiCollectionService(scraper: BangumiScraper): BangumiCollectionService = scraper

    @Provides
    @Singleton
    fun provideBangumiEpisodeCommentsService(scraper: BangumiScraper): BangumiEpisodeCommentsService = scraper

    @Provides
    @Singleton
    fun provideDramaMetadataRepository(
        repository: RoutingDramaMetadataRepository,
    ): DramaMetadataRepository = repository

    @Provides
    @Singleton
    fun provideAnimeMetadataSearchAggregator(
        aggregator: DefaultAnimeMetadataSearchAggregator,
    ): AnimeMetadataSearchAggregator = aggregator

    @Provides
    @Singleton
    fun provideDramaMetadataSearchAggregator(
        aggregator: DefaultDramaMetadataSearchAggregator,
    ): DramaMetadataSearchAggregator = aggregator

    @Provides
    @Singleton
    @IntoSet
    fun provideBangumiAnimeMetadataSearchProvider(
        provider: BangumiAnimeMetadataSearchProvider,
    ): AnimeMetadataSearchProvider = provider

    @Provides
    @Singleton
    @IntoSet
    fun provideTmdbDramaMetadataSearchProvider(
        provider: TmdbDramaMetadataSearchProvider,
    ): DramaMetadataSearchProvider = provider

    @Provides
    @Singleton
    @IntoSet
    fun provideTvMazeDramaMetadataSearchProvider(
        provider: TvMazeDramaMetadataSearchProvider,
    ): DramaMetadataSearchProvider = provider

    @Provides
    @Singleton
    fun provideFilenameMetadataParser(parser: AnimeFilenameParser): FilenameMetadataParser = parser
}
