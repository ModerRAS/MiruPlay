package com.miruplay.tv.scanner

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.data.repository.IndexRepository
import com.miruplay.tv.data.repository.IndexRepositoryEntity
import com.miruplay.tv.data.repository.MediaRepository
import com.miruplay.tv.data.repository.MetadataRepository
import com.miruplay.tv.mediasource.FileEntry
import com.miruplay.tv.mediasource.FileMetadata
import com.miruplay.tv.mediasource.MediaSource
import com.miruplay.tv.mediasource.MediaSourceFactory
import com.miruplay.tv.model.Anime
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.MediaCapabilities
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class ScanCoordinatorTest {

    @Test
    fun `scanSource starts WebDAV traversal at empty path and indexes remote files`() = runBlocking {
        val sourceInfo = MediaSourceInfo(
            id = 7L,
            name = "WebDAV",
            type = MediaSourceType.WEBDAV,
            connectionInfo = mapOf("url" to "http://example.test/dav")
        )
        val mediaSource = FakeMediaSource(
            listings = mapOf(
                "" to listOf(
                    FileEntry(name = "番剧", path = "/番剧", isDirectory = true)
                ),
                "/番剧" to listOf(
                    FileEntry(name = "01 [1080P].mp4", path = "/番剧/01 [1080P].mp4", isDirectory = false, size = 1234),
                    FileEntry(name = "01.trickplay", path = "/番剧/01.trickplay", isDirectory = true)
                )
            )
        )
        val indexRepository = RecordingIndexRepository()
        val metadataRepository = RecordingMetadataRepository()
        val coordinator = ScanCoordinator(
            mediaRepository = SingleSourceRepository(sourceInfo),
            mediaSourceFactory = SingleMediaSourceFactory(mediaSource),
            indexRepository = indexRepository,
            metadataRepository = metadataRepository
        )

        val result = coordinator.scanSource(sourceInfo.id)

        assertTrue("Scan should succeed", result.isSuccess())
        assertEquals(listOf("", "/番剧"), mediaSource.listedPaths)
        assertEquals(1, result.getOrNull()?.episodesFound)
        assertEquals(listOf("/番剧/01 [1080P].mp4"), indexRepository.entries.map { it.path })
        assertEquals("番剧", indexRepository.entries.single().animeName)
        assertEquals("7:/番剧/01 [1080P].mp4", metadataRepository.episodes.single().id)
        assertEquals(
            "http://example.test/dav/%E7%95%AA%E5%89%A7/01%20%5B1080P%5D.mp4",
            metadataRepository.episodes.single().filePath
        )
    }

    private class SingleSourceRepository(
        private val source: MediaSourceInfo
    ) : MediaRepository {
        override suspend fun addSource(source: MediaSourceInfo): Result<Long> = Result.success(source.id)
        override suspend fun removeSource(sourceId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun getSources(): Result<List<MediaSourceInfo>> = Result.success(listOf(source))
        override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> = Result.success(Unit)
        override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> =
            if (sourceId == source.id) Result.success(source) else Result.failure(AppError.MediaSourceError.NotFound(sourceId.toString()))
    }

    private class SingleMediaSourceFactory(
        private val source: MediaSource
    ) : MediaSourceFactory {
        override fun create(info: MediaSourceInfo): Result<MediaSource> = Result.success(source)
        override fun supports(type: MediaSourceType): Boolean = true
    }

    private class RecordingIndexRepository : IndexRepository {
        val entries = mutableListOf<IndexRepositoryEntity>()

        override suspend fun rebuildIndex(sourceId: Long, entries: List<IndexRepositoryEntity>): Result<Unit> {
            this.entries.clear()
            this.entries.addAll(entries)
            return Result.success(Unit)
        }

        override suspend fun queryIndex(sourceId: Long, query: String): Result<List<IndexRepositoryEntity>> = Result.success(entries)
        override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> = Result.success(entries.mapNotNull { it.animeName }.distinct())
        override suspend fun clearIndex(sourceId: Long): Result<Unit> {
            entries.clear()
            return Result.success(Unit)
        }
    }

    private class RecordingMetadataRepository : MetadataRepository {
        val episodes = mutableListOf<Episode>()

        override suspend fun cacheMetadata(anime: Anime): Result<Unit> = Result.success(Unit)
        override suspend fun getCachedMetadata(animeId: String): Result<Anime?> = Result.success(null)
        override suspend fun getCachedEpisode(episodeId: String): Result<Episode?> = Result.success(episodes.firstOrNull { it.id == episodeId })
        override suspend fun getCachedEpisodes(animeId: String): Result<List<Episode>> = Result.success(episodes)
        override suspend fun cacheEpisodes(animeId: String, episodes: List<Episode>): Result<Unit> {
            this.episodes.clear()
            this.episodes.addAll(episodes)
            return Result.success(Unit)
        }
        override suspend fun invalidateCache(animeId: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeMediaSource(
        private val listings: Map<String, List<FileEntry>>
    ) : MediaSource {
        override val id: String = "fake"
        override lateinit var info: MediaSourceInfo
        override val capabilities: MediaCapabilities = MediaCapabilities()
        val listedPaths = mutableListOf<String>()

        override suspend fun listFiles(path: String): Result<List<FileEntry>> {
            listedPaths.add(path)
            return Result.success(listings[path].orEmpty())
        }

        override suspend fun openStream(path: String): Result<InputStream> =
            Result.success(ByteArrayInputStream(ByteArray(0)))

        override suspend fun getMetadata(path: String): Result<FileMetadata> =
            Result.failure(AppError.MediaSourceError.NotFound(path))

        override suspend fun testConnection(): Result<Boolean> = Result.success(true)
        override suspend fun close() = Unit
    }
}
