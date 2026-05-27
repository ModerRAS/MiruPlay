package com.miruplay.tv.data.di

import com.miruplay.tv.data.preferences.WebControlPreferencesManager
import com.miruplay.tv.data.preferences.PlaybackPreferencesManager
import com.miruplay.tv.data.preferences.ScanPreferencesManager
import com.miruplay.tv.data.logging.LogUploadRepositoryImpl
import com.miruplay.tv.data.repository.CloudDriveAutomationRepositoryImpl
import com.miruplay.tv.data.repository.IndexRepositoryImpl
import com.miruplay.tv.data.repository.MediaRepositoryImpl
import com.miruplay.tv.data.repository.MetadataRepositoryImpl
import com.miruplay.tv.data.repository.ProgressRepositoryImpl
import com.miruplay.tv.data.secure.MediaSourceSecretStore
import com.miruplay.tv.data.secure.SecurePreferencesManager
import com.miruplay.tv.repository.AppCredentialStore
import com.miruplay.tv.repository.AppUpdateRepository
import com.miruplay.tv.repository.CloudDriveAutomationRepository
import com.miruplay.tv.repository.CloudDriveCredentialStore
import com.miruplay.tv.repository.LogUploadRepository
import com.miruplay.tv.repository.MediaIndexRepository
import com.miruplay.tv.repository.MediaSourceRepository
import com.miruplay.tv.repository.MetadataRepository
import com.miruplay.tv.repository.PlaybackProgressRepository
import com.miruplay.tv.repository.PlaybackPreferencesRepository
import com.miruplay.tv.repository.ScanPreferencesRepository
import com.miruplay.tv.repository.WebControlAccessManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaSourceRepository

    @Binds
    @Singleton
    abstract fun bindMediaSourceSecretStore(
        impl: SecurePreferencesManager
    ): MediaSourceSecretStore

    @Binds
    @Singleton
    abstract fun bindAppCredentialStore(impl: SecurePreferencesManager): AppCredentialStore

    @Binds
    @Singleton
    abstract fun bindCloudDriveCredentialStore(impl: SecurePreferencesManager): CloudDriveCredentialStore

    @Binds
    @Singleton
    abstract fun bindMetadataRepository(impl: MetadataRepositoryImpl): MetadataRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): PlaybackProgressRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackPreferencesRepository(
        impl: PlaybackPreferencesManager
    ): PlaybackPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindScanPreferencesRepository(
        impl: ScanPreferencesManager
    ): ScanPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindIndexRepository(impl: IndexRepositoryImpl): MediaIndexRepository

    @Binds
    @Singleton
    abstract fun bindCloudDriveAutomationRepository(
        impl: CloudDriveAutomationRepositoryImpl
    ): CloudDriveAutomationRepository

    @Binds
    @Singleton
    abstract fun bindLogUploadRepository(
        impl: LogUploadRepositoryImpl
    ): LogUploadRepository

    @Binds
    @Singleton
    abstract fun bindWebControlAccessManager(
        impl: WebControlPreferencesManager
    ): WebControlAccessManager
}
