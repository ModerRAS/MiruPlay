package com.miruplay.tv.sync.rss

import android.content.Context
import com.miruplay.tv.clouddrive.CloudDriveClient
import com.miruplay.tv.model.FilenameMetadataParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncEngineModule {
    @Provides
    @Singleton
    fun provideRssFeedReader(): RssFeedReader = RssFeedFetcher()

    @Provides
    @Singleton
    fun provideRssTorrentDownloader(
        @ApplicationContext context: Context
    ): RssTorrentDownloader = TorrentFileDownloader(File(context.cacheDir, "rss-torrents"))

    @Provides
    @Singleton
    fun provideCloudDriveRssSubmissionPreparer(
        cloudDriveClient: CloudDriveClient,
        torrentDownloader: RssTorrentDownloader
    ): CloudDriveRssSubmissionPreparer =
        CloudDriveRssSubmissionPreparer(cloudDriveClient, torrentDownloader)

    @Provides
    @Singleton
    fun provideCloudDriveVideoClassifier(
        filenameMetadataParser: FilenameMetadataParser
    ): CloudDriveVideoClassifier =
        AndroidCloudDriveVideoClassifier(filenameMetadataParser)

    @Provides
    @Singleton
    fun provideCloudDriveLibraryOrganizer(
        cloudDriveClient: CloudDriveClient
    ): CloudDriveLibraryOrganizer =
        CloudDriveLibraryOrganizer(cloudDriveClient)
}

