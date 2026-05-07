package com.miruplay.tv.data.di

import com.miruplay.tv.data.dao.AnimeDao
import com.miruplay.tv.data.dao.EpisodeDao
import com.miruplay.tv.data.dao.IndexDao
import com.miruplay.tv.data.dao.MediaSourceDao
import com.miruplay.tv.data.dao.ProgressDao
import com.miruplay.tv.data.db.MiruPlayDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAnimeDao(database: MiruPlayDatabase): AnimeDao = database.animeDao()

    @Provides
    @Singleton
    fun provideEpisodeDao(database: MiruPlayDatabase): EpisodeDao = database.episodeDao()

    @Provides
    @Singleton
    fun provideMediaSourceDao(database: MiruPlayDatabase): MediaSourceDao = database.mediaSourceDao()

    @Provides
    @Singleton
    fun provideProgressDao(database: MiruPlayDatabase): ProgressDao = database.progressDao()

    @Provides
    @Singleton
    fun provideIndexDao(database: MiruPlayDatabase): IndexDao = database.indexDao()
}
