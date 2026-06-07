package com.miruplay.tv.ui.di

import com.miruplay.tv.ui.library.LibraryScanController
import com.miruplay.tv.ui.library.LibraryScanTask
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UiTvBindingModule {
    @Binds
    @Singleton
    abstract fun bindLibraryScanController(
        impl: LibraryScanTask,
    ): LibraryScanController
}
