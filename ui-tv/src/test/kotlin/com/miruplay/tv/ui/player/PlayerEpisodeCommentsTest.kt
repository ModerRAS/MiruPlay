package com.miruplay.tv.ui.player

import com.miruplay.tv.core.common.Result
import com.miruplay.tv.model.Episode
import com.miruplay.tv.model.PlaybackSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlayerEpisodeCommentsTest {
    @Test
    fun `playback source bangumi id takes precedence over database fallback`() = runBlocking {
        var databaseLoaded = false
        val episodeId = resolveBangumiEpisodeId(
            source = PlaybackSource(
                uri = "episode.mkv",
                mediaSourceId = "anime",
                episodeId = "episode-1",
                bangumiEpisodeId = 101,
            ),
            loadCachedEpisode = {
                databaseLoaded = true
                episode(bangumiEpisodeId = 202)
            },
        )

        assertEquals(101, episodeId)
        assertFalse(databaseLoaded)
    }

    @Test
    fun `database fallback id reaches comments API and final label`() = runBlocking {
        val source = PlaybackSource(
            uri = "episode.mkv",
            mediaSourceId = "anime",
            episodeId = "episode-1",
        )
        val episodeId = resolveBangumiEpisodeId(source) {
            assertEquals("episode-1", it)
            episode(bangumiEpisodeId = 202)
        }
        var requestedEpisodeId: Int? = null

        val (loadedEpisodeId, result) = loadBangumiEpisodeComments(requireNotNull(episodeId)) {
            requestedEpisodeId = it
            Result.success(emptyList())
        }

        assertEquals(202, requestedEpisodeId)
        assertEquals(202, loadedEpisodeId)
        assertEquals(Result.success(emptyList<Nothing>()), result)
        assertEquals("Bangumi Ep. 202", bangumiEpisodeLabel(loadedEpisodeId))
        assertEquals("当前剧集", bangumiEpisodeLabel(null))
    }

    private fun episode(bangumiEpisodeId: Int): Episode =
        Episode(
            id = "episode-1",
            animeId = "anime",
            episodeNumber = 1,
            filePath = "episode.mkv",
            fileName = "episode.mkv",
            bangumiEpisodeId = bangumiEpisodeId,
        )
}
