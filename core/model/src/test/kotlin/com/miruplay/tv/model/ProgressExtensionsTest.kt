package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressExtensionsTest {

    @Test
    fun `partial progress is resumable but not completed`() {
        val episode = episode(duration = 100_000L)
        val progress = progress(positionMs = 30_000L)

        assertFalse(episode.isCompleted(progress))
        assertTrue(episode.continueEpisodeProgress(progress))
        assertEquals(30_000L, episode.resumePosition(progress))
        assertEquals("看到 00:30", episode.progressLabel(progress))
    }

    @Test
    fun `completion threshold marks watched and clears resume position`() {
        val episode = episode(duration = 100_000L)
        val progress = progress(positionMs = 90_000L)

        assertTrue(episode.isCompleted(progress))
        assertFalse(episode.continueEpisodeProgress(progress))
        assertEquals(0L, episode.resumePosition(progress))
        assertEquals("已看", episode.progressLabel(progress))
    }

    @Test
    fun `playback position coercion clamps to episode duration`() {
        val episode = episode(duration = 100_000L)

        assertEquals(0L, episode.coercePlaybackPosition(-1L))
        assertEquals(45_000L, episode.coercePlaybackPosition(45_000L))
        assertEquals(100_000L, episode.coercePlaybackPosition(120_000L))
    }

    @Test
    fun `progress fraction uses duration-aware position coercion`() {
        val episode = episode(duration = 100_000L)

        assertEquals(0f, episode.progressFraction(progress(positionMs = -1L)), 0.0001f)
        assertEquals(0.45f, episode.progressFraction(progress(positionMs = 45_000L)), 0.0001f)
        assertEquals(1f, episode.progressFraction(progress(positionMs = 120_000L)), 0.0001f)
    }

    @Test
    fun `unknown duration only completes after a finished playback`() {
        val episode = episode(duration = 0L)
        val partial = progress(positionMs = 30_000L)
        val completed = progress(positionMs = 0L, playCount = 1)

        assertFalse(episode.isCompleted(partial))
        assertTrue(episode.continueEpisodeProgress(partial))
        assertTrue(episode.isCompleted(completed))
        assertEquals("已看", episode.progressLabel(completed))
    }

    @Test
    fun `bangumi done collection is treated as completed`() {
        val episode = episode(duration = 100_000L, bangumiCollectionType = 2)
        val progress = progress(positionMs = 10_000L)

        assertTrue(episode.isCompleted(progress))
        assertEquals(0L, episode.resumePosition(progress))
        assertEquals("已看", episode.progressLabel(progress))
    }

    @Test
    fun `recent playback status helpers share TV wording`() {
        val record = progress(positionMs = 123_456L)

        assertEquals("尚未载入最近播放。", recentPlaybackInitialStatus())
        assertEquals("还没有最近播放记录。", recentPlaybackLoadedStatus(emptyList()))
        assertEquals("已载入 1 条最近播放。", recentPlaybackLoadedStatus(listOf(record)))
        assertEquals("还没有最近播放记录。", recentPlaybackShowingStatus(emptyList()))
        assertEquals("正在显示 1 条最近播放。", recentPlaybackShowingStatus(listOf(record)))
        assertEquals("请先选择一条最近播放记录。", recentPlaybackRequiredStatus())
        assertEquals("未看", playbackProgressRecordLabel(null))
        assertEquals("未看", playbackProgressRecordLabel(progress(positionMs = 0L)))
        assertEquals("看到 02:03", playbackProgressRecordLabel(record))
        assertEquals(record.copy(positionMs = 456_000L), record.retainedSelectionInProgressRecords(listOf(record.copy(positionMs = 456_000L))))
        assertEquals(null, record.retainedSelectionInProgressRecords(listOf(progress(episodeId = "ep2", positionMs = 0L))))
        assertEquals(null, null.retainedSelectionInProgressRecords(listOf(record)))
        assertEquals("123", record.resumeStartSecondsText())
        assertEquals("已载入最近播放：Episode 1。", record.loadedPlaybackStatus("Episode 1"))
    }

    @Test
    fun `continue episode picks most recently watched partial episode`() {
        val first = episode(episodeId = "ep1", episodeNumber = 1, duration = 100_000L)
        val second = episode(episodeId = "ep2", episodeNumber = 2, duration = 100_000L)
        val third = episode(episodeId = "ep3", episodeNumber = 3, duration = 100_000L)
        val episodes = listOf(
            first to progress(episodeId = "ep1", positionMs = 30_000L, lastWatched = 100L),
            second to progress(episodeId = "ep2", positionMs = 40_000L, lastWatched = 300L),
            third to progress(episodeId = "ep3", positionMs = 0L, lastWatched = 0L),
        )

        assertEquals(second, episodes.continueEpisode())
        assertEquals("继续观看 2", episodes.continueActionLabel())
    }

    @Test
    fun `continue episode falls back to first unfinished then first episode`() {
        val first = episode(episodeId = "ep1", episodeNumber = 1, duration = 100_000L)
        val second = episode(episodeId = "ep2", episodeNumber = 2, duration = 100_000L)
        val watched = progress(episodeId = "ep1", positionMs = 90_000L)

        assertEquals(second, listOf(first to watched, second to null).continueEpisode())
        assertEquals(first, listOf(first to watched).continueEpisode())
        assertEquals(null, emptyList<Pair<Episode, ProgressRecord?>>().continueEpisode())
        assertEquals("播放", emptyList<Pair<Episode, ProgressRecord?>>().continueActionLabel())
    }

    private fun episode(
        episodeId: String = "ep1",
        episodeNumber: Int = 1,
        duration: Long,
        bangumiCollectionType: Int? = null
    ): Episode = Episode(
        id = episodeId,
        animeId = "anime1",
        episodeNumber = episodeNumber,
        filePath = "/anime/$episodeId.mkv",
        fileName = "$episodeId.mkv",
        duration = duration,
        bangumiCollectionType = bangumiCollectionType
    )

    private fun progress(
        episodeId: String = "ep1",
        positionMs: Long,
        playCount: Int = 0,
        lastWatched: Long = 123L
    ): ProgressRecord = ProgressRecord(
        episodeId = episodeId,
        positionMs = positionMs,
        lastWatched = lastWatched,
        playCount = playCount
    )
}
