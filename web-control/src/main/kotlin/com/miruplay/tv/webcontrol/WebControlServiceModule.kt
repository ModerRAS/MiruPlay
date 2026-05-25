package com.miruplay.tv.webcontrol

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WebControlServiceModule {
    @Binds
    @Singleton
    abstract fun bindWebControlEndpointService(
        impl: WebControlService,
    ): WebControlEndpointService
}
