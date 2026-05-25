package com.miruplay.tv.scraper.di

import com.miruplay.tv.scraper.AniListScraper
import com.miruplay.tv.scraper.BangumiScraper
import com.miruplay.tv.scraper.MetadataScraper
import com.miruplay.tv.scraper.filename.AnimeFilenameParser
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.repository.BangumiCollectionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
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
    fun provideBangumiCollectionService(scraper: BangumiScraper): BangumiCollectionService = scraper

    @Provides
    @Singleton
    @IntoSet
    fun provideAniListScraper(scraper: AniListScraper): MetadataScraper = scraper

    @Provides
    @Singleton
    fun provideFilenameMetadataParser(parser: AnimeFilenameParser): FilenameMetadataParser = parser
}
