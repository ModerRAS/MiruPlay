package com.miruplay.tv.scraper.di

import android.content.Context
import com.miruplay.tv.scraper.AniListScraper
import com.miruplay.tv.scraper.BangumiScraper
import com.miruplay.tv.scraper.MetadataScraper
import com.miruplay.tv.scraper.filename.AnimeFilenameParser
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.repository.BangumiCollectionService
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
    @IntoSet
    fun provideAniListScraper(scraper: AniListScraper): MetadataScraper = scraper

    @Provides
    @Singleton
    fun provideFilenameMetadataParser(parser: AnimeFilenameParser): FilenameMetadataParser = parser
}
