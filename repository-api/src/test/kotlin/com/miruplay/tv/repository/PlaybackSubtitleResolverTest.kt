package com.miruplay.tv.repository

import com.miruplay.tv.core.common.AppError
import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.MediaSourceInfo
import com.miruplay.tv.model.MediaSourceInfoConventions
import com.miruplay.tv.model.PlaybackSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSubtitleResolverTest {
    @Test
    fun `resolver attaches indexed WebDAV subtitles and keeps explicit tracks`() = runBlocking {
        val mediaSource = MediaSourceInfoConventions.webDav(
            url = "https://dav.example/anime",
            name = "DAV",
        ).copy(id = 7L)
        val resolver = PlaybackSubtitleResolver(
            index = FakeIndexRepository(
                listOf(
                    MediaIndexEntry(
                        sourceId = 7L,
                        path = "/Show/Episode 01.mkv",
                        externalSubtitlePaths = listOf(
                            "/Show/Episode 01.ass",
                            "/Show/Episode 01.zh-CN.srt",
                        ),
                    ),
                ),
            ),
            mediaSources = FakeSourceRepository(mediaSource),
        )

        val resolved = resolver.resolve(
            PlaybackSource(
                uri = "https://dav.example/anime/Show/Episode%2001.mkv",
                mediaSourceId = "anime",
                episodeId = "7:/Show/Episode 01.mkv",
            ),
        )

        assertEquals(
            listOf(
                "https://dav.example/anime/Show/Episode%2001.ass",
                "https://dav.example/anime/Show/Episode%2001.zh-CN.srt",
            ),
            resolved.subtitleTracks.map { it.path },
        )
    }

    @Test
    fun `resolver leaves direct playback without an indexed episode unchanged`() = runBlocking {
        val source = PlaybackSource(uri = "https://example.test/video.mkv", mediaSourceId = "direct")
        val resolver = PlaybackSubtitleResolver(
            index = FakeIndexRepository(emptyList()),
            mediaSources = FakeSourceRepository(),
        )

        assertEquals(source, resolver.resolve(source))
    }
}

private class FakeIndexRepository(
    private val entries: List<MediaIndexEntry>,
) : MediaIndexRepository {
    override suspend fun rebuildIndex(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> = Result.success(Unit)
    override suspend fun upsertEntry(sourceId: Long, entry: MediaIndexEntry): Result<Unit> = Result.success(Unit)
    override suspend fun queryIndex(sourceId: Long, query: String): Result<List<MediaIndexEntry>> =
        Result.success(entries.filter { it.sourceId == sourceId })
    override suspend fun getAnimeInIndex(sourceId: Long): Result<List<String>> = Result.success(emptyList())
    override suspend fun clearIndex(sourceId: Long): Result<Unit> = Result.success(Unit)
    override suspend fun saveLastBatchUndo(sourceId: Long, entries: List<MediaIndexEntry>): Result<Unit> = Result.success(Unit)
    override suspend fun getLastBatchUndo(sourceId: Long): Result<List<MediaIndexEntry>> = Result.success(emptyList())
    override suspend fun clearLastBatchUndo(sourceId: Long): Result<Unit> = Result.success(Unit)
}

private class FakeSourceRepository(
    private vararg val sources: MediaSourceInfo,
) : MediaSourceRepository {
    override suspend fun addSource(source: MediaSourceInfo): Result<Long> = Result.success(source.id)
    override suspend fun removeSource(sourceId: Long): Result<Unit> = Result.success(Unit)
    override suspend fun getSources(): Result<List<MediaSourceInfo>> = Result.success(sources.toList())
    override suspend fun updateSource(source: MediaSourceInfo): Result<Unit> = Result.success(Unit)
    override suspend fun getSourceById(sourceId: Long): Result<MediaSourceInfo> =
        sources.firstOrNull { it.id == sourceId }
            ?.let { Result.success(it) }
            ?: Result.failure(AppError.MediaSourceError.NotFound("Source id: $sourceId"))
}
