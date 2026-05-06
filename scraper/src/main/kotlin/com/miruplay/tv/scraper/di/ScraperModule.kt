package com.miruplay.tv.scraper.di

import com.miruplay.tv.scraper.MetadataScraper
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
    fun provideAniListScraper(): MetadataScraper {
        // Placeholder - will be implemented in T35
        throw NotImplementedError("AniList scraper not yet implemented")
    }
}
