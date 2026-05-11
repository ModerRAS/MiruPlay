package com.miruplay.tv.clouddrive

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudDriveModule {
    @Binds
    @Singleton
    abstract fun bindCloudDriveClient(impl: GrpcCloudDriveClient): CloudDriveClient
}
