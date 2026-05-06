package com.miruplay.tv.mediasource.di

import com.miruplay.tv.mediasource.DefaultMediaSourceFactory
import com.miruplay.tv.mediasource.MediaSourceFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaSourceModule {

    @Binds
    @Singleton
    abstract fun bindMediaSourceFactory(impl: DefaultMediaSourceFactory): MediaSourceFactory
}
